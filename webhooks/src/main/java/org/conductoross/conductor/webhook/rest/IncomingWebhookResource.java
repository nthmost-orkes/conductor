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

import java.util.Map;

import org.conductoross.conductor.webhook.service.IncomingWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;

/**
 * REST endpoint for receiving incoming webhook events.
 *
 * <p>External systems call these endpoints to deliver webhook events to Conductor. The events are
 * verified and queued for processing by the WebhookWorker.
 */
@RestController
@RequestMapping(value = "/webhook")
@ConditionalOnProperty(value = "conductor.webhooks.enabled", havingValue = "true", matchIfMissing = true)
public class IncomingWebhookResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncomingWebhookResource.class);

    private final IncomingWebhookService incomingWebhookService;

    public IncomingWebhookResource(IncomingWebhookService incomingWebhookService) {
        this.incomingWebhookService = incomingWebhookService;
    }

    @PostMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Receive an incoming webhook event")
    public String handleWebhook(
            @PathVariable String id,
            @RequestBody String bodyStr,
            @RequestParam(required = false) Map<String, Object> requestParams,
            @RequestHeader HttpHeaders headers) {

        LOGGER.debug("Received webhook event for id={}", id);
        LOGGER.trace("Webhook payload: {}", bodyStr);
        LOGGER.trace("Webhook params: {}", requestParams);
        LOGGER.trace("Webhook headers: {}", headers);

        return incomingWebhookService.handleWebhook(id, bodyStr, requestParams, headers);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Handle webhook ping/verification request")
    public String handlePing(
            @PathVariable String id,
            @RequestParam(required = false) Map<String, Object> requestParams) {

        LOGGER.debug("Received webhook ping for id={}", id);
        return incomingWebhookService.handlePing(id, requestParams);
    }
}
