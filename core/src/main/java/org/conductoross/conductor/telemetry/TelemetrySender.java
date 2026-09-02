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
package org.conductoross.conductor.telemetry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Ships the telemetry payload with a single HTTP POST using the JDK's built-in {@link HttpClient}
 * (no new dependencies). Sending is strictly best-effort: any failure is logged and swallowed so
 * that telemetry can never disrupt or slow the server.
 */
@Component
@ConditionalOnProperty(name = "conductor.telemetry.enabled", havingValue = "true")
public class TelemetrySender {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetrySender.class);

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /**
     * POSTs the JSON body to the endpoint. Returns true only on a 2xx response. Never throws.
     *
     * @param endpoint destination URL (assumed non-blank)
     * @param json compact JSON payload
     */
    public boolean send(String endpoint, String json) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(endpoint))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();
            HttpResponse<Void> response =
                    client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return true;
            }
            LOGGER.warn("Telemetry send returned HTTP {}", status);
            return false;
        } catch (Exception e) {
            LOGGER.debug("Telemetry send failed (ignored): {}", e.toString());
            return false;
        }
    }
}
