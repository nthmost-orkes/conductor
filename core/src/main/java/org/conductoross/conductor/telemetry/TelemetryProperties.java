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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the strictly opt-in telemetry feature. Everything defaults to off; the feature
 * never turns itself on. See {@code TELEMETRY.md} for the full trust contract and the exact
 * payload.
 */
@ConfigurationProperties("conductor.telemetry")
public class TelemetryProperties {

    /** Master switch. When false (the default) nothing is ever collected or sent. */
    private boolean enabled = false;

    /**
     * Destination the one-per-version payload is POSTed to. When blank, the payload is still
     * collected and (optionally) logged, but never transmitted — useful for previewing locally.
     */
    private String endpoint = "";

    /**
     * Optional contact email. Present in the payload ONLY if explicitly set here. This is a
     * separate, independent consent from {@link #enabled}: sharing anonymous stats never implies an
     * email.
     */
    private String email = null;

    /**
     * Directory where the persistent instance id and the set of already-reported versions are kept,
     * so restarts and per-version sends are not miscounted as new installs.
     */
    private String stateDir = System.getProperty("user.home", ".") + "/.conductor";

    /** When true, the exact payload is logged once at startup (the server-side "preview"). */
    private boolean logPayloadOnStartup = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStateDir() {
        return stateDir;
    }

    public void setStateDir(String stateDir) {
        this.stateDir = stateDir;
    }

    public boolean isLogPayloadOnStartup() {
        return logPayloadOnStartup;
    }

    public void setLogPayloadOnStartup(boolean logPayloadOnStartup) {
        this.logPayloadOnStartup = logPayloadOnStartup;
    }
}
