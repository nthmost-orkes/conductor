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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persistent telemetry bookkeeping stored as a single JSON file in the configured state directory.
 * Holds the random, stable {@code instanceId} (so restarts are not counted as new installs) and the
 * set of Conductor versions already reported (so each version sends exactly once).
 *
 * <p>The {@code instanceId} is a random UUID: it is not derived from hostname, IP, or hardware, and
 * is not linkable to any identity.
 */
public class TelemetryState {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String instanceId;
    private Set<String> sentVersions = new LinkedHashSet<>();

    public TelemetryState() {}

    @JsonProperty("instanceId")
    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    @JsonProperty("sentVersions")
    public Set<String> getSentVersions() {
        return sentVersions;
    }

    public void setSentVersions(Set<String> sentVersions) {
        this.sentVersions = sentVersions == null ? new LinkedHashSet<>() : sentVersions;
    }

    public boolean hasSent(String version) {
        return sentVersions.contains(version);
    }

    public void markSent(String version) {
        sentVersions.add(version);
    }

    /**
     * Loads existing state from {@code <stateDir>/telemetry-state.json}, creating a fresh state
     * with a new instance id if none exists (or if the file is unreadable). Never throws on a
     * missing or corrupt file — telemetry must not disrupt the server.
     */
    public static TelemetryState loadOrCreate(String stateDir) {
        Path file = Path.of(stateDir, "telemetry-state.json");
        TelemetryState state = null;
        try {
            if (Files.exists(file)) {
                state = MAPPER.readValue(Files.readAllBytes(file), TelemetryState.class);
            }
        } catch (IOException | RuntimeException e) {
            state = null; // fall through to a fresh state
        }
        if (state == null) {
            state = new TelemetryState();
        }
        if (state.instanceId == null || state.instanceId.isBlank()) {
            state.instanceId = UUID.randomUUID().toString();
        }
        return state;
    }

    /** Persists this state to {@code <stateDir>/telemetry-state.json}, creating dirs as needed. */
    public void save(String stateDir) {
        Path dir = Path.of(stateDir);
        Path file = dir.resolve("telemetry-state.json");
        try {
            Files.createDirectories(dir);
            Files.write(file, MAPPER.writeValueAsBytes(this));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
