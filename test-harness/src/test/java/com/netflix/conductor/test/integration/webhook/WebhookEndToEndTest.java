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
package com.netflix.conductor.test.integration.webhook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.webhook.rest.IncomingWebhookResource;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end integration test for the webhook feature.
 *
 * <p>This test verifies the complete webhook flow:
 *
 * <ol>
 *   <li>Create a webhook configuration via REST API
 *   <li>Register and start a workflow with a WAIT_FOR_WEBHOOK task
 *   <li>Fire an incoming webhook event via REST API
 *   <li>Verify the WAIT_FOR_WEBHOOK task is completed with the webhook payload
 *   <li>Verify the workflow completes successfully
 * </ol>
 *
 * <p>The webhook runtime components (system task, mapper, worker, service) are working correctly.
 * See also WebhookHashingTest in the webhooks module for unit tests.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = ConductorTestApp.class)
@TestPropertySource(
        properties = {
            "conductor.db.type=memory",
            "conductor.indexing.enabled=false",
            "conductor.webhooks.enabled=true",
            "conductor.webhook.worker.threadCount=1",
            "conductor.webhook.worker.pollingInterval=100"
        })
public class WebhookEndToEndTest {

    @LocalServerPort private int port;

    @Autowired private ApplicationContext applicationContext;

    private String apiRoot;
    private WorkflowClient workflowClient;
    private MetadataClient metadataClient;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    private static final String DEFAULT_EMAIL = "test@conductor.oss";

    @BeforeClass
    public static void setupClass() {
        // Any one-time setup
    }

    @Before
    public void setup() {
        apiRoot = String.format("http://localhost:%d/api/", port);

        workflowClient = new WorkflowClient();
        workflowClient.setRootURI(apiRoot);

        metadataClient = new MetadataClient();
        metadataClient.setRootURI(apiRoot);

        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testWebhookCompletesWaitingTask() throws Exception {
        // 1. Create a webhook configuration
        String webhookId = createWebhookConfig("test-webhook-" + UUID.randomUUID());

        // 2. Register and start a workflow with WAIT_FOR_WEBHOOK task
        String workflowName = "webhook_test_workflow_" + UUID.randomUUID();
        WorkflowDef workflowDef = createWebhookWorkflowDef(workflowName);
        metadataClient.registerWorkflowDef(workflowDef);

        // Start the workflow with the webhook ID in input
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("webhookId", webhookId);

        StartWorkflowRequest startRequest =
                new StartWorkflowRequest()
                        .withName(workflowName)
                        .withVersion(1)
                        .withInput(workflowInput);

        String workflowId = workflowClient.startWorkflow(startRequest);
        assertNotNull("Workflow should have started", workflowId);

        // 3. Verify workflow is running with WAIT_FOR_WEBHOOK task in progress
        await().atMost(5, TimeUnit.SECONDS)
                .until(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            return wf.getStatus() == WorkflowStatus.RUNNING
                                    && !wf.getTasks().isEmpty()
                                    && wf.getTasks().get(0).getStatus() == Task.Status.IN_PROGRESS;
                        });

        Workflow runningWorkflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(WorkflowStatus.RUNNING, runningWorkflow.getStatus());
        assertEquals(1, runningWorkflow.getTasks().size());
        assertEquals(
                "wait_for_webhook_ref", runningWorkflow.getTasks().get(0).getReferenceTaskName());

        // 4. Fire an incoming webhook event
        Map<String, Object> webhookPayload = new HashMap<>();
        webhookPayload.put("customerId", "cust-123");
        webhookPayload.put("eventType", "payment_received");
        webhookPayload.put("amount", 99.99);

        fireWebhook(webhookId, webhookPayload);

        // 5. Verify workflow completes with webhook data in task output
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            return wf.getStatus() == WorkflowStatus.COMPLETED;
                        });

        Workflow completedWorkflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(WorkflowStatus.COMPLETED, completedWorkflow.getStatus());

        // Verify the webhook task has the payload in its output
        var webhookTask =
                completedWorkflow.getTasks().stream()
                        .filter(t -> t.getReferenceTaskName().equals("wait_for_webhook_ref"))
                        .findFirst()
                        .orElseThrow();

        assertEquals(Task.Status.COMPLETED, webhookTask.getStatus());
        assertNotNull(webhookTask.getOutputData());
        assertEquals("cust-123", webhookTask.getOutputData().get("customerId"));
        assertEquals("payment_received", webhookTask.getOutputData().get("eventType"));
    }

    @Test
    public void testIncomingWebhookResourceBeanExists() {
        // Verify IncomingWebhookResource is registered as a Spring bean
        IncomingWebhookResource resource =
                applicationContext.getBean(IncomingWebhookResource.class);
        assertNotNull("IncomingWebhookResource should be registered", resource);
    }

    @Test
    public void testWebhookWithHeaderVerification() throws Exception {
        // Create webhook with header-based verification
        String webhookId =
                createWebhookConfigWithHeaders(
                        "header-webhook-" + UUID.randomUUID(),
                        Map.of("X-Api-Key", "secret-key-123"));

        // Register and start workflow
        String workflowName = "header_webhook_workflow_" + UUID.randomUUID();
        WorkflowDef workflowDef = createWebhookWorkflowDef(workflowName);
        metadataClient.registerWorkflowDef(workflowDef);

        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("webhookId", webhookId);

        String workflowId =
                workflowClient.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(workflowName)
                                .withVersion(1)
                                .withInput(workflowInput));

        // Wait for task to be in progress
        await().atMost(5, TimeUnit.SECONDS)
                .until(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            return !wf.getTasks().isEmpty()
                                    && wf.getTasks().get(0).getStatus() == Task.Status.IN_PROGRESS;
                        });

        // Fire webhook with correct header
        Map<String, Object> payload = Map.of("data", "test-data");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Api-Key", "secret-key-123");

        fireWebhookWithHeaders(webhookId, payload, headers);

        // Verify workflow completes
        await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            return wf.getStatus() == WorkflowStatus.COMPLETED;
                        });

        Workflow completedWorkflow = workflowClient.getWorkflow(workflowId, true);
        assertEquals(WorkflowStatus.COMPLETED, completedWorkflow.getStatus());
    }

    @Test
    public void testMultipleTasksWaitingForSameWebhook() throws Exception {
        // Create a single webhook config
        String webhookId = createWebhookConfig("multi-task-webhook-" + UUID.randomUUID());

        // Start multiple workflows all waiting on the same webhook
        String workflowName = "multi_wait_workflow_" + UUID.randomUUID();
        WorkflowDef workflowDef = createWebhookWorkflowDef(workflowName);
        metadataClient.registerWorkflowDef(workflowDef);

        Map<String, Object> workflowInput = Map.of("webhookId", webhookId);

        String workflowId1 =
                workflowClient.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(workflowName)
                                .withVersion(1)
                                .withInput(workflowInput));
        String workflowId2 =
                workflowClient.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(workflowName)
                                .withVersion(1)
                                .withInput(workflowInput));
        String workflowId3 =
                workflowClient.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(workflowName)
                                .withVersion(1)
                                .withInput(workflowInput));

        // Wait for all tasks to be in progress
        await().atMost(5, TimeUnit.SECONDS)
                .until(
                        () -> {
                            return isTaskInProgress(workflowId1)
                                    && isTaskInProgress(workflowId2)
                                    && isTaskInProgress(workflowId3);
                        });

        // Fire one webhook event
        fireWebhook(webhookId, Map.of("broadcast", "message"));

        // All three workflows should complete
        await().atMost(15, TimeUnit.SECONDS)
                .until(
                        () -> {
                            return workflowClient.getWorkflow(workflowId1, false).getStatus()
                                            == WorkflowStatus.COMPLETED
                                    && workflowClient.getWorkflow(workflowId2, false).getStatus()
                                            == WorkflowStatus.COMPLETED
                                    && workflowClient.getWorkflow(workflowId3, false).getStatus()
                                            == WorkflowStatus.COMPLETED;
                        });

        // Verify all completed with the webhook data
        for (String wfId : List.of(workflowId1, workflowId2, workflowId3)) {
            Workflow wf = workflowClient.getWorkflow(wfId, true);
            assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());
            assertEquals("message", wf.getTasks().get(0).getOutputData().get("broadcast"));
        }
    }

    // --- Helper methods ---

    private String createWebhookConfig(String name) throws Exception {
        String webhookId = UUID.randomUUID().toString();

        Map<String, Object> webhookConfig = new HashMap<>();
        webhookConfig.put("id", webhookId);
        webhookConfig.put("name", name);
        webhookConfig.put("verifier", "HEADER_BASED");
        webhookConfig.put("headers", Map.of("X-Test-Header", "test-value"));

        String url = String.format("http://localhost:%d/api/metadata/webhook", port);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request =
                new HttpEntity<>(objectMapper.writeValueAsString(webhookConfig), headers);
        ResponseEntity<String> response =
                restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        assertTrue("Webhook creation should succeed", response.getStatusCode().is2xxSuccessful());

        return webhookId;
    }

    private String createWebhookConfigWithHeaders(
            String name, Map<String, String> verificationHeaders) throws Exception {
        String webhookId = UUID.randomUUID().toString();

        Map<String, Object> webhookConfig = new HashMap<>();
        webhookConfig.put("id", webhookId);
        webhookConfig.put("name", name);
        webhookConfig.put("verifier", "HEADER_BASED");
        webhookConfig.put("headers", verificationHeaders);

        String url = String.format("http://localhost:%d/api/metadata/webhook", port);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request =
                new HttpEntity<>(objectMapper.writeValueAsString(webhookConfig), headers);
        ResponseEntity<String> response =
                restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        assertTrue("Webhook creation should succeed", response.getStatusCode().is2xxSuccessful());

        return webhookId;
    }

    private WorkflowDef createWebhookWorkflowDef(String name) {
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail(DEFAULT_EMAIL);
        def.setDescription("Test workflow with WAIT_FOR_WEBHOOK task");

        WorkflowTask webhookTask = new WorkflowTask();
        webhookTask.setName("WAIT_FOR_WEBHOOK");
        webhookTask.setTaskReferenceName("wait_for_webhook_ref");
        webhookTask.setType(TaskType.WAIT_FOR_WEBHOOK.name());

        // Input mapping - the matches field is used for hash computation
        Map<String, Object> inputParameters = new HashMap<>();
        inputParameters.put("matches", Map.of("webhookId", "${workflow.input.webhookId}"));
        webhookTask.setInputParameters(inputParameters);

        def.getTasks().add(webhookTask);

        return def;
    }

    private void fireWebhook(String webhookId, Map<String, Object> payload) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Test-Header", "test-value"); // Match the default verification header

        fireWebhookWithHeaders(webhookId, payload, headers);
    }

    private void fireWebhookWithHeaders(
            String webhookId, Map<String, Object> payload, HttpHeaders headers) throws Exception {
        String url = String.format("http://localhost:%d/webhook/%s", port, webhookId);

        HttpEntity<String> request =
                new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
        ResponseEntity<String> response =
                restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        assertTrue(
                "Webhook POST should succeed: " + response.getBody(),
                response.getStatusCode().is2xxSuccessful());
    }

    private boolean isTaskInProgress(String workflowId) {
        try {
            Workflow wf = workflowClient.getWorkflow(workflowId, true);
            return !wf.getTasks().isEmpty()
                    && wf.getTasks().get(0).getStatus() == Task.Status.IN_PROGRESS;
        } catch (Exception e) {
            return false;
        }
    }
}
