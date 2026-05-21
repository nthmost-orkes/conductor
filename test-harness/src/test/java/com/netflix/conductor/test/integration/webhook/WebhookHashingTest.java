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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.conductoross.conductor.dao.memory.webhook.InMemoryWebhookTaskService;
import org.conductoross.conductor.service.webhook.WebhookTaskService;
import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.model.TaskModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for webhook hash routing behavior.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>Multiple tasks with the same matches land in the same hash bucket</li>
 *   <li>Different workflow versions produce different hashes</li>
 *   <li>Task reference name iteration suffixes are stripped</li>
 *   <li>Hash computation is stable across map key ordering</li>
 *   <li>Concurrent registrations don't lose tasks</li>
 * </ul>
 */
public class WebhookHashingTest {

    private WebhookTaskService taskService;

    @Before
    public void setUp() {
        taskService = new InMemoryWebhookTaskService();
    }

    @Test
    public void sameMatchesProduceSameHash_multipleTasks() {
        // Two tasks with identical matches should land in same bucket
        Map<String, Object> matches = Map.of("customerId", "cust-123", "eventType", "payment");

        TaskModel task1 = createTask("task-1", "payment-workflow", "wait_for_webhook", matches);
        TaskModel task2 = createTask("task-2", "payment-workflow", "wait_for_webhook", matches);

        taskService.put(task1, 1);
        taskService.put(task2, 1);

        // Both should be retrievable via the same hash
        String hash = computeExpectedHash("payment-workflow", 1, "wait_for_webhook", matches);
        Set<String> taskIds = taskService.get(hash);

        assertEquals(2, taskIds.size());
        assertTrue(taskIds.contains("task-1"));
        assertTrue(taskIds.contains("task-2"));
    }

    @Test
    public void differentVersionsProduceDifferentHashes() {
        Map<String, Object> matches = Map.of("orderId", "order-456");

        TaskModel taskV1 = createTask("task-v1", "order-workflow", "webhook_ref", matches);
        TaskModel taskV2 = createTask("task-v2", "order-workflow", "webhook_ref", matches);

        taskService.put(taskV1, 1);
        taskService.put(taskV2, 2);

        // Each version should be in its own bucket
        String hashV1 = computeExpectedHash("order-workflow", 1, "webhook_ref", matches);
        String hashV2 = computeExpectedHash("order-workflow", 2, "webhook_ref", matches);

        assertEquals(Set.of("task-v1"), taskService.get(hashV1));
        assertEquals(Set.of("task-v2"), taskService.get(hashV2));
    }

    @Test
    public void iterationSuffixStrippedFromTaskRefName() {
        // Tasks in DO_WHILE loops have iteration suffixes like "task__1", "task__2"
        // The hash should be computed without the suffix
        Map<String, Object> matches = Map.of("batchId", "batch-789");

        TaskModel iteration1 = createTask("task-iter-1", "batch-workflow", "process_item__1", matches);
        TaskModel iteration2 = createTask("task-iter-2", "batch-workflow", "process_item__2", matches);
        TaskModel noSuffix = createTask("task-no-suffix", "batch-workflow", "process_item", matches);

        taskService.put(iteration1, 1);
        taskService.put(iteration2, 1);
        taskService.put(noSuffix, 1);

        // All three should be in the same bucket (suffix stripped)
        String hash = computeExpectedHash("batch-workflow", 1, "process_item", matches);
        Set<String> taskIds = taskService.get(hash);

        assertEquals(3, taskIds.size());
        assertTrue(taskIds.contains("task-iter-1"));
        assertTrue(taskIds.contains("task-iter-2"));
        assertTrue(taskIds.contains("task-no-suffix"));
    }

    @Test
    public void hashStableRegardlessOfMapKeyOrder() {
        // LinkedHashMap preserves insertion order, but hash should be same regardless
        LinkedHashMap<String, Object> orderA = new LinkedHashMap<>();
        orderA.put("alpha", "1");
        orderA.put("beta", "2");
        orderA.put("gamma", "3");

        LinkedHashMap<String, Object> orderB = new LinkedHashMap<>();
        orderB.put("gamma", "3");
        orderB.put("alpha", "1");
        orderB.put("beta", "2");

        TaskModel taskA = createTask("task-a", "test-workflow", "ref", orderA);
        TaskModel taskB = createTask("task-b", "test-workflow", "ref", orderB);

        taskService.put(taskA, 1);
        taskService.put(taskB, 1);

        // Both should be in same bucket
        String hash = computeExpectedHash("test-workflow", 1, "ref", orderA);
        Set<String> taskIds = taskService.get(hash);

        assertEquals(2, taskIds.size());
    }

    @Test
    public void emptyMatchesProducesValidHash() {
        Map<String, Object> emptyMatches = Map.of();

        TaskModel task = createTask("task-empty", "empty-workflow", "ref", emptyMatches);
        taskService.put(task, 1);

        String hash = computeExpectedHash("empty-workflow", 1, "ref", emptyMatches);
        assertEquals(Set.of("task-empty"), taskService.get(hash));
    }

    @Test
    public void concurrentRegistrationsAllSucceed() throws InterruptedException {
        int numThreads = 20;
        int tasksPerThread = 100;
        Map<String, Object> matches = Map.of("key", "value");

        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < tasksPerThread; i++) {
                        TaskModel task = createTask(
                                "task-" + threadId + "-" + i,
                                "concurrent-workflow",
                                "ref",
                                matches);
                        taskService.put(task, 1);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        // All tasks should have been registered
        assertEquals(numThreads * tasksPerThread, successCount.get());

        // All should be retrievable
        String hash = computeExpectedHash("concurrent-workflow", 1, "ref", matches);
        Set<String> taskIds = taskService.get(hash);
        assertEquals(numThreads * tasksPerThread, taskIds.size());
    }

    @Test
    public void removeOnlyAffectsTargetTask() {
        Map<String, Object> matches = Map.of("x", "y");

        TaskModel task1 = createTask("task-1", "wf", "ref", matches);
        TaskModel task2 = createTask("task-2", "wf", "ref", matches);
        TaskModel task3 = createTask("task-3", "wf", "ref", matches);

        taskService.put(task1, 1);
        taskService.put(task2, 1);
        taskService.put(task3, 1);

        String hash = computeExpectedHash("wf", 1, "ref", matches);

        // Remove task-2
        taskService.remove(hash, "task-2");

        Set<String> remaining = taskService.get(hash);
        assertEquals(2, remaining.size());
        assertTrue(remaining.contains("task-1"));
        assertTrue(remaining.contains("task-3"));
    }

    @Test
    public void removeLastTaskClearsBucket() {
        Map<String, Object> matches = Map.of("solo", "task");

        TaskModel task = createTask("only-task", "wf", "ref", matches);
        taskService.put(task, 1);

        String hash = computeExpectedHash("wf", 1, "ref", matches);
        assertEquals(1, taskService.get(hash).size());

        taskService.remove(hash, "only-task");

        assertTrue(taskService.get(hash).isEmpty());
    }

    // --- Helper methods ---

    private TaskModel createTask(
            String taskId,
            String workflowType,
            String refName,
            Map<String, Object> matches) {
        TaskModel task = new TaskModel();
        task.setTaskId(taskId);
        task.setWorkflowType(workflowType);
        task.setReferenceTaskName(refName);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("matches", matches);
        task.setInputData(inputData);

        return task;
    }

    /**
     * Compute expected hash using the same algorithm as InMemoryWebhookTaskService.
     * This is intentionally duplicated to catch any drift between test and impl.
     */
    private String computeExpectedHash(
            String workflowName,
            int version,
            String refName,
            Map<String, Object> matches) {
        // Strip iteration suffix
        String[] parts = refName.split("__");
        String baseRefName = parts.length > 0 ? parts[0] : refName;

        StringBuilder hash = new StringBuilder(workflowName + ";" + version + ";" + baseRefName);
        // Sort keys
        matches.keySet().stream().sorted().forEach(k ->
                hash.append(";").append(matches.get(k)));
        return hash.toString();
    }
}
