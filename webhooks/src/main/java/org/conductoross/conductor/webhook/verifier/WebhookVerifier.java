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

import java.util.Map;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.conductoross.conductor.webhook.util.ErrorList;

/**
 * Strategy interface for verifying incoming webhook events.
 *
 * <p>Implementations validate that an incoming event is authentic based on the webhook's
 * configuration. Different verifier types (HEADER_BASED, HMAC, SLACK, etc.) implement different
 * verification strategies.
 */
public interface WebhookVerifier {

    /**
     * Verifies an incoming webhook event against the webhook configuration.
     *
     * @param webhookConfig the webhook configuration containing verification parameters
     * @param incomingWebhookEvent the incoming event to verify
     * @return an ErrorList containing error messages if verification fails; empty if successful
     */
    ErrorList verify(WebhookConfig webhookConfig, IncomingWebhookEvent incomingWebhookEvent);

    /**
     * Returns the type of this verifier (e.g., "HEADER_BASED", "SLACK", "HMAC").
     *
     * @return the verifier type, matching {@link WebhookConfig.Verifier#toString()}
     */
    String getType();

    /**
     * Extracts a challenge response from the incoming event, if applicable.
     *
     * <p>Some webhook providers (e.g., Slack) send a challenge during URL verification that must be
     * echoed back. Most verifiers return null.
     *
     * @param incomingWebhookEvent the incoming event
     * @param webhookConfig the webhook configuration
     * @return the challenge value to echo back, or null if not applicable
     */
    default String extractChallenge(
            IncomingWebhookEvent incomingWebhookEvent, WebhookConfig webhookConfig) {
        return null;
    }

    /**
     * Handles a ping/verification request (typically via HTTP GET).
     *
     * <p>Some webhook providers send a GET request to verify the endpoint before sending events.
     * Most verifiers return null.
     *
     * @param webhookConfig the webhook configuration
     * @param requestParams the request parameters from the ping
     * @return the response to send back, or null if not applicable
     */
    default String handlePing(WebhookConfig webhookConfig, Map<String, Object> requestParams) {
        return null;
    }
}
