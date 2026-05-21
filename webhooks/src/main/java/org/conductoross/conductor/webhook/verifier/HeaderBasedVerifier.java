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
package org.conductoross.conductor.webhook.verifier;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.util.ErrorList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifies incoming webhook events by checking HTTP headers.
 *
 * <p>This verifier compares the headers in the incoming event against the expected headers
 * configured in the webhook. All configured headers must be present and have matching values.
 */
@Component
public class HeaderBasedVerifier implements WebhookVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeaderBasedVerifier.class);

    @Override
    public ErrorList verify(
            WebhookConfig webhookConfig, IncomingWebhookEvent incomingWebhookEvent) {
        var errors = new ErrorList();

        Map<String, String> expectedHeaders = webhookConfig.getHeaders();
        if (expectedHeaders == null || expectedHeaders.isEmpty()) {
            // No headers configured means verification passes (no constraints)
            return errors;
        }

        for (Map.Entry<String, String> entry : expectedHeaders.entrySet()) {
            String headerName = entry.getKey();
            String expectedValue = entry.getValue();

            if (incomingWebhookEvent.getHeaders() == null) {
                errors.add(
                        "Header " + headerName + " is not present in the incoming webhook event");
                continue;
            }

            List<String> headerValues = incomingWebhookEvent.getHeaders().get(headerName);

            if (headerValues == null || headerValues.isEmpty()) {
                errors.add(
                        "Header " + headerName + " is not present in the incoming webhook event");
                continue;
            }

            // Check the first header value (standard HTTP behavior)
            if (!expectedValue.equals(headerValues.get(0))) {
                errors.add(
                        "Header "
                                + headerName
                                + " value does not match the configured webhook value");
            }

            if (headerValues.size() > 1) {
                LOGGER.warn(
                        "Multiple values present for header {} in webhook {}",
                        headerName,
                        webhookConfig.getId());
            }
        }

        return errors;
    }

    @Override
    public String getType() {
        return WebhookConfig.Verifier.HEADER_BASED.toString();
    }
}
