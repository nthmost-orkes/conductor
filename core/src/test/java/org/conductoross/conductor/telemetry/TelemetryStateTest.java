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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TelemetryStateTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void createsAndPersistsStableInstanceId() {
        String dir = tmp.getRoot().getAbsolutePath();
        TelemetryState first = TelemetryState.loadOrCreate(dir);
        assertNotNull(first.getInstanceId());
        assertFalse(first.getInstanceId().isBlank());
        first.save(dir);
        assertTrue(Files.exists(Path.of(dir, "telemetry-state.json")));

        TelemetryState second = TelemetryState.loadOrCreate(dir);
        assertEquals(first.getInstanceId(), second.getInstanceId());
    }

    @Test
    public void recordsSentVersions() {
        String dir = tmp.getRoot().getAbsolutePath();
        TelemetryState state = TelemetryState.loadOrCreate(dir);
        assertFalse(state.hasSent("3.21.5"));
        state.markSent("3.21.5");
        state.save(dir);

        TelemetryState reloaded = TelemetryState.loadOrCreate(dir);
        assertTrue(reloaded.hasSent("3.21.5"));
        assertFalse(reloaded.hasSent("3.22.0"));
    }

    @Test
    public void corruptFileYieldsFreshState() throws Exception {
        String dir = tmp.getRoot().getAbsolutePath();
        Files.writeString(Path.of(dir, "telemetry-state.json"), "{not valid json");
        TelemetryState state = TelemetryState.loadOrCreate(dir);
        assertNotNull(state.getInstanceId());
    }
}
