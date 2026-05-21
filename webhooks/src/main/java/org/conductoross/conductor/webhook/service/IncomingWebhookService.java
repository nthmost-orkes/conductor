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
package org.conductoross.conductor.webhook.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.verifier.WebhookVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.dao.QueueDAO;

import static org.conductoross.conductor.webhook.config.WebhookWorkerProperties.WEBHOOK_QUEUE;

/**
 * Service for handling incoming webhook events.
 *
 * <p>This service receives webhook events from the REST API, verifies them using the configured
 * verifier, and queues them for processing by the {@link
 * org.conductoross.conductor.webhook.WebhookWorker}.
 */
@Service
public class IncomingWebhookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncomingWebhookService.class);

    private final WebhookDAO webhookDAO;
    private final QueueDAO queueDAO;
    private final Map<String, WebhookVerifier> webhookVerifiers;

    public IncomingWebhookService(
            WebhookDAO webhookDAO, Set<WebhookVerifier> webhookVerifiersSet, QueueDAO queueDAO) {
        this.webhookDAO = webhookDAO;
        this.queueDAO = queueDAO;
        this.webhookVerifiers =
                webhookVerifiersSet.stream()
                        .collect(Collectors.toMap(WebhookVerifier::getType, Function.identity()));
    }

    /**
     * Handles an incoming webhook POST request.
     *
     * <p>This method:
     *
     * <ol>
     *   <li>Looks up the webhook configuration by ID
     *   <li>Verifies the request using the configured verifier
     *   <li>Queues the event for processing
     *   <li>Returns any challenge response (for URL verification flows)
     * </ol>
     *
     * @param id the webhook ID
     * @param bodyStr the raw request body
     * @param requestParams the request query parameters
     * @param headers the HTTP headers
     * @return the challenge response if applicable, otherwise null
     * @throws NonTransientException if the webhook doesn't exist or verification fails
     */
    public String handleWebhook(
            String id, String bodyStr, Map<String, Object> requestParams, HttpHeaders headers) {

        String eventId = UUID.randomUUID().toString();

        IncomingWebhookEvent incomingWebhookEvent =
                IncomingWebhookEvent.builder()
                        .body(bodyStr)
                        .timeStamp(System.currentTimeMillis())
                        .headers(headers)
                        .requestParams(requestParams)
                        .webhookId(id)
                        .id(eventId)
                        .build();

        WebhookConfig webhookConfig = webhookDAO.getWebhook(id);
        if (webhookConfig == null) {
            String description = "Webhook " + id + " does not exist";
            LOGGER.warn(description);
            throw new NonTransientException(description);
        }

        String verifierName = webhookConfig.getVerifier().toString();
        WebhookVerifier verifier = this.webhookVerifiers.get(verifierName);

        if (verifier == null) {
            String description =
                    "No verifier found for type " + verifierName + " configured on webhook " + id;
            LOGGER.error(description);
            throw new NonTransientException(description);
        }

        var verificationErrors = verifier.verify(webhookConfig, incomingWebhookEvent);

        if (verificationErrors.arePresent()) {
            String verificationFailure =
                    "Request verification failed for webhook '"
                            + id
                            + "': "
                            + verificationErrors.getMessage();
            LOGGER.error(verificationFailure);
            throw new NonTransientException(verificationFailure);
        }

        boolean update = false;

        if (!webhookConfig.isUrlVerified()) {
            webhookConfig.setUrlVerified(true);
            update = true;
        }

        String webhookChallenge = verifier.extractChallenge(incomingWebhookEvent, webhookConfig);
        if (webhookChallenge == null) {
            // This message should be processed and added to the queue
            storeWebhook(incomingWebhookEvent);
        }

        // Update the webhook config if URL was just verified
        if (update) {
            webhookDAO.createWebhook(webhookConfig.getId(), webhookConfig);
        }

        return webhookChallenge;
    }

    private void storeWebhook(IncomingWebhookEvent incomingWebhookEvent) {
        webhookDAO.createIncomingWebhookEvent(incomingWebhookEvent.getId(), incomingWebhookEvent);
        queueDAO.push(WEBHOOK_QUEUE, incomingWebhookEvent.getId(), 0);
        LOGGER.debug("Queued webhook event {} for processing", incomingWebhookEvent.getId());
    }

    /**
     * Handles a webhook ping/verification request (HTTP GET).
     *
     * @param id the webhook ID
     * @param requestParams the request query parameters
     * @return the ping response if applicable, otherwise null
     */
    public String handlePing(String id, Map<String, Object> requestParams) {
        WebhookConfig webhookConfig = webhookDAO.getWebhook(id);
        if (webhookConfig == null) {
            LOGGER.warn("Webhook with id {} does not exist", id);
            return null;
        }

        String verifierName = webhookConfig.getVerifier().toString();
        WebhookVerifier verifier = this.webhookVerifiers.get(verifierName);

        if (verifier == null) {
            LOGGER.warn("No verifier found for type {} on webhook {}", verifierName, id);
            return null;
        }

        String response = verifier.handlePing(webhookConfig, requestParams);
        if (response != null) {
            // Ping event - mark URL as verified
            webhookConfig.setUrlVerified(true);
            webhookDAO.createWebhook(id, webhookConfig);
        } else {
            // GET request that's not a ping - treat as a webhook event with empty body
            String eventId = UUID.randomUUID().toString();
            IncomingWebhookEvent incomingWebhookEvent =
                    IncomingWebhookEvent.builder()
                            .body("{}")
                            .timeStamp(System.currentTimeMillis())
                            .webhookId(webhookConfig.getId())
                            .requestParams(requestParams)
                            .id(eventId)
                            .build();
            storeWebhook(incomingWebhookEvent);
        }
        return response;
    }
}
