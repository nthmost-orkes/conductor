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
package org.conductoross.conductor.webhook.rest;

import java.util.List;
import java.util.UUID;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.NotFoundException;

import io.swagger.v3.oas.annotations.Operation;

/**
 * REST endpoint for managing webhook configurations.
 *
 * <p>This resource provides CRUD operations for webhook configurations. Webhooks define how
 * external events are received, verified, and matched to waiting WAIT_FOR_WEBHOOK tasks.
 */
@RestController
@RequestMapping(
        value = "/api/metadata/webhook",
        produces = {MediaType.APPLICATION_JSON_VALUE})
@ConditionalOnProperty(
        value = "conductor.webhooks.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WebhookConfigResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookConfigResource.class);

    /** Placeholder value returned for secret fields to avoid exposing secrets. */
    private static final String SECRET_PLACEHOLDER = "******";

    private final WebhookDAO webhookDAO;

    public WebhookConfigResource(WebhookDAO webhookDAO) {
        this.webhookDAO = webhookDAO;
    }

    @PostMapping
    @Operation(summary = "Create a new webhook configuration")
    public WebhookConfig createWebhook(@RequestBody WebhookConfig webhookConfig) {
        validateWebhookConfig(webhookConfig);

        // Generate ID if not provided
        if (webhookConfig.getId() == null || webhookConfig.getId().isBlank()) {
            webhookConfig.setId(UUID.randomUUID().toString());
        }

        webhookDAO.createWebhook(webhookConfig.getId(), webhookConfig);
        LOGGER.info("Created webhook configuration: {}", webhookConfig.getId());

        return webhookConfig;
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing webhook configuration")
    public WebhookConfig updateWebhook(
            @PathVariable String id, @RequestBody WebhookConfig webhookConfig) {

        WebhookConfig existing = webhookDAO.getWebhook(id);
        if (existing == null) {
            throw new NotFoundException("Webhook with id " + id + " does not exist");
        }

        validateWebhookConfig(webhookConfig);
        webhookConfig.setId(id);

        webhookDAO.createWebhook(id, webhookConfig);
        LOGGER.info("Updated webhook configuration: {}", id);

        return webhookConfig;
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a webhook configuration")
    public void deleteWebhook(@PathVariable String id) {
        WebhookConfig existing = webhookDAO.getWebhook(id);
        if (existing == null) {
            throw new NotFoundException("Webhook with id " + id + " does not exist");
        }

        webhookDAO.removeWebhook(id);
        LOGGER.info("Deleted webhook configuration: {}", id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a webhook configuration by ID")
    public WebhookConfig getWebhook(@PathVariable String id) {
        WebhookConfig webhookConfig = webhookDAO.getWebhook(id);
        if (webhookConfig == null) {
            throw new NotFoundException("Webhook with id " + id + " does not exist");
        }

        // Mask the secret value for security
        webhookConfig.setSecretValue(SECRET_PLACEHOLDER);
        return webhookConfig;
    }

    @GetMapping
    @Operation(summary = "Get all webhook configurations")
    public List<WebhookConfig> getAllWebhooks() {
        List<WebhookConfig> webhooks = webhookDAO.getAllWebhooks();

        // Mask secret values for security
        for (WebhookConfig webhook : webhooks) {
            webhook.setSecretValue(SECRET_PLACEHOLDER);
        }

        return webhooks;
    }

    /** Validates webhook configuration before create/update. */
    private void validateWebhookConfig(WebhookConfig webhookConfig) {
        if (webhookConfig.getName() == null || webhookConfig.getName().isBlank()) {
            throw new NonTransientException("Webhook name is required");
        }

        if (webhookConfig.getVerifier() == null) {
            throw new NonTransientException("Webhook verifier is required");
        }

        // For HEADER_BASED verifier, at least one header must be configured
        if (webhookConfig.getVerifier() == WebhookConfig.Verifier.HEADER_BASED
                && CollectionUtils.isEmpty(webhookConfig.getHeaders())) {
            throw new NonTransientException(
                    "At least one header must be configured for HEADER_BASED verifier");
        }
    }
}
