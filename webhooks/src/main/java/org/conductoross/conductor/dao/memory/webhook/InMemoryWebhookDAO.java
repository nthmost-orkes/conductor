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
package org.conductoross.conductor.dao.memory.webhook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Default single-node implementation of {@link WebhookDAO}.
 *
 * <p>Backed by in-process maps; suitable for single-server deployments and tests. Multi-node
 * deployments should bind a persistent implementation (lands in a later PR).
 */
@Slf4j
public class InMemoryWebhookDAO implements WebhookDAO {

    private final ConcurrentHashMap<String, WebhookConfig> configs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IncomingWebhookEvent> events =
            new ConcurrentHashMap<>();
    // Maps webhookId -> (matcherKey -> matchCriteria)
    private final ConcurrentHashMap<String, Map<String, Map<String, Object>>> matchers =
            new ConcurrentHashMap<>();

    @Override
    public void createWebhook(String id, WebhookConfig config) {
        checkNotNull(id, "Webhook id cannot be null");
        checkNotNull(config, "WebhookConfig cannot be null");
        configs.put(id, config);
        log.debug("Created webhook: id={}, name={}", id, config.getName());
    }

    @Override
    public WebhookConfig getWebhook(String id) {
        return configs.get(id);
    }

    @Override
    public List<WebhookConfig> getAllWebhooks() {
        return new ArrayList<>(configs.values());
    }

    @Override
    public void removeWebhook(String id) {
        checkNotNull(id, "Webhook id cannot be null");
        WebhookConfig removed = configs.remove(id);
        if (removed != null) {
            log.debug("Removed webhook: id={}", id);
        } else {
            log.debug("Webhook not found for removal: id={}", id);
        }
    }

    @Override
    public void createIncomingWebhookEvent(String id, IncomingWebhookEvent event) {
        checkNotNull(id, "Event id cannot be null");
        checkNotNull(event, "IncomingWebhookEvent cannot be null");
        events.put(id, event);
        log.debug("Stored incoming webhook event: id={}, webhookId={}", id, event.getWebhookId());
    }

    @Override
    public IncomingWebhookEvent getWebhookEvent(String id) {
        return events.get(id);
    }

    @Override
    public void removeWebhookEvent(String id) {
        checkNotNull(id, "Event id cannot be null");
        IncomingWebhookEvent removed = events.remove(id);
        if (removed != null) {
            log.debug("Removed webhook event: id={}", id);
        } else {
            log.debug("Webhook event not found for removal: id={}", id);
        }
    }

    @Override
    public Map<String, Map<String, Object>> getMatchers(String webhookId) {
        checkNotNull(webhookId, "Webhook id cannot be null");
        return matchers.getOrDefault(webhookId, Collections.emptyMap());
    }

    @Override
    public void removeMatchers(String id) {
        checkNotNull(id, "Webhook id cannot be null");
        Map<String, Map<String, Object>> removed = matchers.remove(id);
        if (removed != null) {
            log.debug("Removed {} matchers for webhook: id={}", removed.size(), id);
        }
    }

    @Override
    public void createMatchers(
            WebhookConfig webhookConfig,
            Map<String, Integer> receiverWorkflowNamesToVersionsOverride) {
        checkNotNull(webhookConfig, "WebhookConfig cannot be null");
        checkNotNull(webhookConfig.getId(), "WebhookConfig id cannot be null");

        Map<String, Integer> workflowVersions =
                receiverWorkflowNamesToVersionsOverride != null
                        ? receiverWorkflowNamesToVersionsOverride
                        : webhookConfig.getReceiverWorkflowNamesToVersions();

        if (workflowVersions == null || workflowVersions.isEmpty()) {
            log.debug("No receiver workflows configured for webhook: id={}", webhookConfig.getId());
            return;
        }

        // For the in-memory implementation, we store a simple mapping.
        // Real implementations (Redis/Postgres) would compute and store hash prefixes.
        Map<String, Map<String, Object>> matcherMap = new HashMap<>();

        for (Map.Entry<String, Integer> entry : workflowVersions.entrySet()) {
            String workflowName = entry.getKey();
            Integer version = entry.getValue();
            // Matcher key format: webhookId;workflowName;version
            String matcherKey = webhookConfig.getId() + ";" + workflowName + ";" + version;
            // For simplicity, store an empty match criteria map (no JSONPath filters)
            // Real implementations would parse the webhook config's expression field
            matcherMap.put(matcherKey, Collections.emptyMap());
        }

        matchers.put(webhookConfig.getId(), matcherMap);
        log.debug(
                "Created {} matchers for webhook: id={}", matcherMap.size(), webhookConfig.getId());
    }
}
