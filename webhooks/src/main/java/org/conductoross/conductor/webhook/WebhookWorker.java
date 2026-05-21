/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.webhook;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.service.webhook.WebhookTaskService;
import org.conductoross.conductor.webhook.config.WebhookWorkerProperties;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.model.WebhookExecutionHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.LifecycleAwareComponent;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import static org.conductoross.conductor.webhook.config.WebhookWorkerProperties.WEBHOOK_QUEUE;

/**
 * Background worker that processes queued webhook events.
 *
 * <p>This worker polls the webhook queue, matches incoming events against waiting WAIT_FOR_WEBHOOK
 * tasks, and completes those tasks with the webhook payload as output data.
 *
 * <p>OSS-side simplification: This worker does not support multi-tenancy, event message recording,
 * or workflow-start-on-webhook features present in Orkes. It focuses on the core task-completion
 * flow.
 */
@Component
public class WebhookWorker extends LifecycleAwareComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookWorker.class);

    private final ScheduledExecutorService executorService;
    private final QueueDAO queueDAO;
    private final ObjectMapper objectMapper;
    private final WebhookHashingService webhookHashingService;
    private final WebhookTaskService webhookTaskService;
    private final WorkflowExecutor workflowExecutor;
    private final WebhookDAO webhookDAO;
    private final ExecutionDAO executionDAO;
    private final int pollBatchSize;
    private final int lastRunWorkflowIdSize;

    public WebhookWorker(
            ObjectMapper objectMapper,
            QueueDAO queueDAO,
            WebhookDAO webhookDAO,
            ExecutionDAO executionDAO,
            WebhookWorkerProperties properties,
            WebhookHashingService webhookHashingService,
            WorkflowExecutor workflowExecutor,
            WebhookTaskService webhookTaskService) {

        this.objectMapper = objectMapper;
        this.webhookDAO = webhookDAO;
        this.queueDAO = queueDAO;
        this.executionDAO = executionDAO;
        this.webhookHashingService = webhookHashingService;
        this.webhookTaskService = webhookTaskService;
        this.pollBatchSize = properties.getPollBatchSize();
        this.workflowExecutor = workflowExecutor;
        this.lastRunWorkflowIdSize = properties.getLastRunWorkflowIdSize();

        int threadCount = properties.getThreadCount();
        int pollingInterval = properties.getPollingInterval();

        if (threadCount > 0) {
            this.executorService =
                    Executors.newScheduledThreadPool(
                            threadCount,
                            new ThreadFactoryBuilder()
                                    .setNameFormat("webhookWorker-thread-%d")
                                    .build());

            for (int i = 0; i < threadCount; i++) {
                this.executorService.scheduleWithFixedDelay(
                        () -> {
                            try {
                                pollAndExecute();
                            } catch (Throwable t) {
                                LOGGER.error(
                                        "Error in webhook worker poll loop: {}", t.getMessage(), t);
                            }
                        },
                        10,
                        pollingInterval,
                        TimeUnit.MILLISECONDS);
            }
            LOGGER.info(
                    "WebhookWorker started with threadCount={}, pollingInterval={}ms, pollBatchSize={}",
                    threadCount,
                    pollingInterval,
                    pollBatchSize);
        } else {
            this.executorService = null;
            LOGGER.info("WebhookWorker disabled (threadCount=0)");
        }
    }

    private void pollAndExecute() {
        List<String> messages = queueDAO.pop(WEBHOOK_QUEUE, pollBatchSize, 1000);
        for (String id : messages) {
            try {
                handleMessage(id);
            } catch (Throwable t) {
                LOGGER.error("Error processing webhook message {}: {}", id, t.getMessage(), t);
            }
            // Ack the message even if webhook processing fails to avoid infinite reprocessing
            // Future enhancement: implement retry logic with dead-letter queue
            queueDAO.ack(WEBHOOK_QUEUE, id);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPayload(IncomingWebhookEvent event)
            throws JsonProcessingException {
        Object obj = objectMapper.readValue(event.getBody(), Object.class);
        Map<String, Object> payload;
        if (obj instanceof Map) {
            payload = new HashMap<>((Map<String, Object>) obj);
        } else {
            payload = new HashMap<>();
            payload.put("request", obj);
        }
        if (event.getRequestParams() != null) {
            payload.putAll(event.getRequestParams());
        }
        return payload;
    }

    private void handleMessage(String messageId) {
        LOGGER.debug("Processing webhook message: {}", messageId);

        IncomingWebhookEvent event =
                objectMapper.convertValue(
                        webhookDAO.getWebhookEvent(messageId), IncomingWebhookEvent.class);

        if (event == null) {
            LOGGER.warn("Webhook event {} not found in storage", messageId);
            return;
        }

        WebhookConfig webhookConfig = webhookDAO.getWebhook(event.getWebhookId());

        if (webhookConfig == null) {
            LOGGER.warn(
                    "Webhook config {} not found for event {}", event.getWebhookId(), messageId);
            return;
        }

        Set<String> matchedWorkflowIds = new HashSet<>();

        // Get all matchers for this webhook and try to match the event
        Map<String, Map<String, Object>> matchers = webhookDAO.getMatchers(event.getWebhookId());

        // If no matchers are configured, try to find tasks directly by webhookId
        // This handles the case where tasks register with matches: {webhookId: "xxx"}
        if (matchers.isEmpty()) {
            LOGGER.debug(
                    "No matchers configured for webhook {}, using direct task lookup",
                    event.getWebhookId());
            try {
                Map<String, Object> payload = getPayload(event);
                completeTasksByWebhookId(event.getWebhookId(), payload, matchedWorkflowIds);
            } catch (JsonProcessingException e) {
                LOGGER.error(
                        "Failed to parse webhook body for event {}: {}", messageId, e.getMessage());
            }
        } else {
            // Use configured matchers for hash-based matching
            for (Map.Entry<String, Map<String, Object>> entry : matchers.entrySet()) {
                String key = entry.getKey();
                Map<String, Object> value = entry.getValue();

                if (value == null) {
                    LOGGER.debug(
                            "Skipping misconfigured matcher entry for webhook {}: {}",
                            event.getWebhookId(),
                            key);
                    continue;
                }

                String hash =
                        webhookHashingService.computeJsonHash(
                                new StringBuilder(key),
                                value,
                                event.getBody(),
                                event.getRequestParams());

                if (hash == null) {
                    LOGGER.debug(
                            "No matching hash for webhook {} with matcher key {}",
                            event.getWebhookId(),
                            key);
                    continue;
                }

                try {
                    Map<String, Object> payload = getPayload(event);
                    LOGGER.debug(
                            "Completing webhook tasks with hash: {} and payload: {}",
                            hash,
                            payload);
                    completeTask(hash, payload, matchedWorkflowIds);
                } catch (JsonProcessingException e) {
                    LOGGER.error(
                            "Failed to parse webhook body for event {}: {}",
                            messageId,
                            e.getMessage());
                }
            }
        }

        // Store execution history
        storeWebhookEventExecutionHistory(matchedWorkflowIds, webhookConfig, event);

        // Clean up the event from storage
        webhookDAO.removeWebhookEvent(event.getId());
    }

    private void storeWebhookEventExecutionHistory(
            Set<String> matchedWorkflowIds,
            WebhookConfig webhookConfig,
            IncomingWebhookEvent incomingWebhookEvent) {

        try {
            String payload = objectMapper.writeValueAsString(incomingWebhookEvent);
            boolean matched = !matchedWorkflowIds.isEmpty();

            WebhookExecutionHistory history =
                    new WebhookExecutionHistory(
                            incomingWebhookEvent.getId(),
                            matched,
                            matchedWorkflowIds,
                            payload,
                            incomingWebhookEvent.getTimeStamp());

            // Add to the history with size limit
            List<WebhookExecutionHistory> existingHistory =
                    webhookConfig.getWebhookExecutionHistory();
            if (existingHistory == null) {
                webhookConfig.setWebhookExecutionHistory(List.of(history));
            } else if (existingHistory.size() >= lastRunWorkflowIdSize) {
                // Remove oldest entry and add new one at front
                existingHistory.remove(existingHistory.size() - 1);
                existingHistory.add(0, history);
            } else {
                existingHistory.add(0, history);
            }

            if (matched) {
                webhookConfig.setUrlVerified(true);
            }

            webhookDAO.createWebhook(webhookConfig.getId(), webhookConfig);
        } catch (JsonProcessingException e) {
            LOGGER.error(
                    "Failed to serialize webhook event {} for history: {}",
                    incomingWebhookEvent.getId(),
                    e.getMessage());
        }
    }

    private void completeTask(
            String hash, Map<String, Object> payload, Set<String> matchedWorkflowIds) {
        Set<String> taskIds = webhookTaskService.get(hash);

        for (String taskId : taskIds) {
            completeTask(taskId, hash, payload, matchedWorkflowIds);
        }
    }

    private void completeTasksByWebhookId(
            String webhookId, Map<String, Object> payload, Set<String> matchedWorkflowIds) {
        Set<String> taskIds = webhookTaskService.getByWebhookId(webhookId);
        LOGGER.debug("Found {} tasks waiting for webhookId: {}", taskIds.size(), webhookId);

        for (String taskId : taskIds) {
            // Use webhookId as the hash for removal (simplified, since we don't have the full hash)
            completeTaskByWebhookId(taskId, webhookId, payload, matchedWorkflowIds);
        }
    }

    private void completeTaskByWebhookId(
            String taskId,
            String webhookId,
            Map<String, Object> payload,
            Set<String> matchedWorkflowIds) {

        TaskModel taskModel = executionDAO.getTask(taskId);
        if (taskModel == null) {
            LOGGER.debug("Task {} not found, may have been cleaned up", taskId);
            return;
        }

        if (taskModel.getStatus().isTerminal()) {
            LOGGER.debug("Task {} is already in terminal state: {}", taskId, taskModel.getStatus());
            return;
        }

        TaskResult taskResult = new TaskResult(taskModel.toTask());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.getOutputData().putAll(payload);

        workflowExecutor.updateTask(taskResult);
        matchedWorkflowIds.add(taskModel.getWorkflowInstanceId());

        LOGGER.info(
                "Completed webhook task {} in workflow {} via webhookId {}",
                taskId,
                taskModel.getWorkflowInstanceId(),
                webhookId);
    }

    private void completeTask(
            String taskId,
            String hash,
            Map<String, Object> payload,
            Set<String> matchedWorkflowIds) {

        TaskModel taskModel = executionDAO.getTask(taskId);
        if (taskModel == null) {
            LOGGER.debug("Task {} not found, may have been cleaned up", taskId);
            webhookTaskService.remove(hash, taskId);
            return;
        }

        if (taskModel.getStatus().isTerminal()) {
            LOGGER.debug("Task {} is already in terminal state: {}", taskId, taskModel.getStatus());
            webhookTaskService.remove(hash, taskId);
            return;
        }

        TaskResult taskResult = new TaskResult(taskModel.toTask());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        taskResult.getOutputData().putAll(payload);

        workflowExecutor.updateTask(taskResult);
        matchedWorkflowIds.add(taskModel.getWorkflowInstanceId());
        webhookTaskService.remove(hash, taskId);

        LOGGER.info(
                "Completed webhook task {} in workflow {} with hash {}",
                taskId,
                taskModel.getWorkflowInstanceId(),
                hash);
    }

    @Override
    public void doStop() {
        if (this.executorService != null) {
            this.executorService.shutdown();
            LOGGER.info("WebhookWorker stopped");
        }
    }
}
