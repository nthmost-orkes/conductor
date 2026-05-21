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
package org.conductoross.conductor.webhook.config;

import org.conductoross.conductor.dao.memory.webhook.InMemoryWebhookDAO;
import org.conductoross.conductor.dao.memory.webhook.InMemoryWebhookTaskService;
import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.service.webhook.WebhookTaskService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the webhook module.
 *
 * <p>This configuration provides default in-memory implementations for webhook storage. Production
 * deployments should provide their own implementations backed by persistent storage (e.g., Redis,
 * PostgreSQL).
 *
 * <p>The webhook feature can be disabled by setting {@code conductor.webhooks.enabled=false}.
 */
@Configuration
@ConditionalOnProperty(
        value = "conductor.webhooks.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WebhookConfiguration {

    /**
     * Default in-memory implementation of WebhookDAO.
     *
     * <p>This bean is only created if no other WebhookDAO bean is present. Production deployments
     * should provide a persistent implementation.
     */
    @Bean
    @ConditionalOnMissingBean
    public WebhookDAO webhookDAO() {
        return new InMemoryWebhookDAO();
    }

    /**
     * Default in-memory implementation of WebhookTaskService.
     *
     * <p>This bean is only created if no other WebhookTaskService bean is present. Production
     * deployments should provide a persistent implementation.
     */
    @Bean
    @ConditionalOnMissingBean
    public WebhookTaskService webhookTaskService() {
        return new InMemoryWebhookTaskService();
    }
}
