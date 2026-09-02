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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Orchestrates opt-in telemetry once the application is ready: builds the payload, optionally logs
 * it (the server-side "preview"), and sends it at most once per Conductor version. Runs on a daemon
 * thread so it never blocks or slows startup, and only exists when {@code
 * conductor.telemetry.enabled=true}.
 */
@Service
@ConditionalOnProperty(name = "conductor.telemetry.enabled", havingValue = "true")
public class TelemetryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryService.class);

    private final TelemetryCollector collector;
    private final TelemetrySender sender;
    private final TelemetryProperties properties;

    public TelemetryService(
            TelemetryCollector collector, TelemetrySender sender, TelemetryProperties properties) {
        this.collector = collector;
        this.sender = sender;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread worker = new Thread(this::runOnce, "conductor-telemetry");
        worker.setDaemon(true);
        worker.start();
    }

    void runOnce() {
        try {
            TelemetryState state = TelemetryState.loadOrCreate(properties.getStateDir());
            TelemetryPayload payload = collector.collect(state);
            String version = payload.getConductorVersion();

            if (properties.isLogPayloadOnStartup()) {
                LOGGER.info(
                        "Conductor telemetry (opt-in) — exact payload that will be sent:\n{}",
                        TelemetryPayload.mapper()
                                .writerWithDefaultPrettyPrinter()
                                .writeValueAsString(payload));
            }

            String endpoint = properties.getEndpoint();
            if (endpoint == null || endpoint.isBlank()) {
                LOGGER.info("Telemetry endpoint not configured; payload collected but not sent.");
                return;
            }
            if (state.hasSent(version)) {
                LOGGER.debug("Telemetry already sent for version {}; skipping.", version);
                return;
            }

            String json = TelemetryPayload.mapper().writeValueAsString(payload);
            if (sender.send(endpoint, json)) {
                state.markSent(version);
                state.save(properties.getStateDir());
                LOGGER.info("Telemetry sent for version {}.", version);
            }
        } catch (Exception e) {
            LOGGER.debug("Telemetry run failed (ignored): {}", e.toString());
        }
    }
}
