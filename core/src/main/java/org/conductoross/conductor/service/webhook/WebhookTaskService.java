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
package org.conductoross.conductor.service.webhook;

import java.util.Set;

import com.netflix.conductor.model.TaskModel;

/**
 * Routes inbound webhook events to parked WAIT_FOR_WEBHOOK tasks by hash.
 *
 * <p>Implementations: {@code InMemoryWebhookTaskService} (single-node, default), {@code
 * RedisWebhookTaskService} (multi-node, lands in a later PR).
 */
public interface WebhookTaskService {

    /**
     * Removes a task registration from the given hash bucket.
     *
     * @param hash the hash key (must not be null)
     * @param taskId the task ID to remove (must not be null)
     */
    void remove(String hash, String taskId);

    /**
     * Returns all task IDs registered under the given hash.
     *
     * @param hash the hash key to look up (must not be null)
     * @return set of task IDs, or an empty set if none found (never {@code null})
     */
    Set<String> get(String hash);

    /**
     * Returns all task IDs waiting for the given webhook ID.
     *
     * <p>This provides a reverse lookup by webhookId, enabling webhook events to find all waiting
     * tasks regardless of their workflow/version/taskRef combination.
     *
     * @param webhookId the webhook ID to look up (must not be null)
     * @return set of task IDs, or an empty set if none found (never {@code null})
     */
    Set<String> getByWebhookId(String webhookId);

    /**
     * Registers a WAIT_FOR_WEBHOOK task so it can be matched by incoming webhook events. The task's
     * {@code inputData.matches} field is used to compute the hash key.
     *
     * @param taskModel the task to register (must not be null, must have matches in inputData)
     * @param workflowVersion the workflow version (used in hash computation)
     * @throws com.netflix.conductor.core.exception.NonTransientException if matches field is
     *     missing
     */
    void put(TaskModel taskModel, int workflowVersion);

    final class Constants {

        private Constants() {}

        public static final String WAIT_FOR_WEBHOOK = "WAIT_FOR_WEBHOOK";
        public static final String WEBHOOK_DELIMITER = ";";
    }
}
