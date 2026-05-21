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
package org.conductoross.conductor.dao.webhook;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;

/**
 * Persists webhook configurations and inbound event audit records.
 *
 * <p>Implementations: {@code InMemoryWebhookDAO} (single-node, default), {@code RedisWebhookDAO} /
 * {@code PostgresWebhookDAO} (multi-node, land in later PRs).
 *
 * <p><b>Mutation contract:</b> objects returned from {@code get*} methods must not be mutated by
 * callers. Implementations may return live references to stored state; deserializing impls
 * (Redis/Postgres) return fresh objects, in-memory impls do not. To modify a stored value,
 * construct a new instance and write it back via the corresponding {@code create*} method.
 */
public interface WebhookDAO {

    /**
     * Returns the webhook event with the given ID, or {@code null} if not found.
     *
     * @param messageId the event ID to look up (must not be null)
     * @return the event, or {@code null} if no event exists with this ID
     * @see WebhookDAO — the returned event must not be mutated by the caller.
     */
    IncomingWebhookEvent getWebhookEvent(String messageId);

    /**
     * Returns the webhook configuration with the given ID, or {@code null} if not found.
     *
     * @param webhookId the webhook ID to look up (must not be null)
     * @return the config, or {@code null} if no webhook exists with this ID
     * @see WebhookDAO — returned config must not be mutated by the caller.
     */
    WebhookConfig getWebhook(String webhookId);

    /**
     * Returns all matchers for the given webhook ID.
     *
     * <p>Matchers map hash-prefix keys (e.g., "workflowName;version;taskRef") to their match
     * criteria (JSONPath -> expected value).
     *
     * @param webhookId the webhook ID (must not be null)
     * @return a map of matcher key to match criteria, or empty map if none
     */
    Map<String, Map<String, Object>> getMatchers(String webhookId);

    /**
     * Creates or updates a webhook configuration. If a webhook with the given ID already exists, it
     * is replaced.
     *
     * @param id the webhook ID (must not be null)
     * @param webhookConfig the configuration to store (must not be null)
     */
    void createWebhook(String id, WebhookConfig webhookConfig);

    /**
     * Removes the webhook event with the given ID. No-op if the event does not exist.
     *
     * @param id the event ID to remove (must not be null)
     */
    void removeWebhookEvent(String id);

    /**
     * Removes the webhook configuration with the given ID. No-op if the webhook does not exist.
     *
     * @param id the webhook ID to remove (must not be null)
     */
    void removeWebhook(String id);

    /**
     * Removes all matchers associated with the given webhook ID.
     *
     * @param id the webhook ID (must not be null)
     */
    void removeMatchers(String id);

    /**
     * Returns all registered webhook configurations. The returned list is a fresh collection; the
     * caller may modify the list itself, but must not mutate its elements.
     *
     * @return all webhooks, or an empty list if none are registered (never {@code null})
     */
    List<WebhookConfig> getAllWebhooks();

    /**
     * Alias for {@link #getAllWebhooks()} to match Orkes naming.
     *
     * @return all webhooks, or an empty list if none are registered
     */
    default List<WebhookConfig> getWebhooks() {
        return getAllWebhooks();
    }

    /**
     * Creates matchers for the given webhook configuration.
     *
     * <p>Matchers are computed from the webhook's receiverWorkflowNamesToVersions and stored for
     * fast lookup when incoming events arrive.
     *
     * @param webhookConfig the webhook configuration
     * @param receiverWorkflowNamesToVersionsOverride optional override for workflow versions; if
     *     null, uses webhookConfig.getReceiverWorkflowNamesToVersions()
     */
    void createMatchers(
            WebhookConfig webhookConfig,
            Map<String, Integer> receiverWorkflowNamesToVersionsOverride);

    /**
     * Creates or updates an incoming webhook event record. If an event with the given ID already
     * exists, it is replaced.
     *
     * @param id the event ID (must not be null)
     * @param incomingWebhookEvent the event to store (must not be null)
     */
    void createIncomingWebhookEvent(String id, IncomingWebhookEvent incomingWebhookEvent);
}
