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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TelemetryCollectorTest {

    private static Function<String, String> props(Map<String, String> m) {
        return m::get;
    }

    @Test
    public void readModulesDefaultsWhenAbsent() {
        TelemetryPayload.Modules mod = TelemetryCollector.readModules(props(new HashMap<>()));
        assertEquals("unknown", mod.getDbType());
        assertEquals("unknown", mod.getQueueType());
        // indexing defaults to enabled, but the type is absent
        assertEquals("unknown", mod.getIndexingType());
        assertEquals("none", mod.getEventQueueType());
        assertEquals("none", mod.getExternalPayloadStorage());
        assertEquals("none", mod.getFileStorage());
    }

    @Test
    public void readModulesReadsConfiguredValues() {
        Map<String, String> m = new HashMap<>();
        m.put("conductor.db.type", "postgres");
        m.put("conductor.queue.type", "postgres");
        m.put("conductor.indexing.type", "elasticsearch");
        m.put("conductor.default-event-queue.type", "sqs");
        m.put("conductor.external-payload-storage.type", "s3");
        m.put("conductor.file-storage.enabled", "true");
        m.put("conductor.file-storage.type", "gcs");

        TelemetryPayload.Modules mod = TelemetryCollector.readModules(props(m));
        assertEquals("postgres", mod.getDbType());
        assertEquals("postgres", mod.getQueueType());
        assertEquals("elasticsearch", mod.getIndexingType());
        assertEquals("sqs", mod.getEventQueueType());
        assertEquals("s3", mod.getExternalPayloadStorage());
        assertEquals("gcs", mod.getFileStorage());
    }

    @Test
    public void readModulesIndexingDisabledReportsNone() {
        Map<String, String> m = new HashMap<>();
        m.put("conductor.indexing.enabled", "false");
        m.put("conductor.indexing.type", "elasticsearch");
        assertEquals("none", TelemetryCollector.readModules(props(m)).getIndexingType());
    }

    @Test
    public void readModulesFileStorageDisabledReportsNoneEvenWithType() {
        Map<String, String> m = new HashMap<>();
        m.put("conductor.file-storage.enabled", "false");
        m.put("conductor.file-storage.type", "gcs");
        assertEquals("none", TelemetryCollector.readModules(props(m)).getFileStorage());
    }

    @Test
    public void buildScaleBucketsAllFields() {
        TelemetryPayload.Scale s = TelemetryCollector.buildScale(75, 300, 3, 42.0, 5000.0);
        assertEquals("2-5", s.getServerNodes());
        assertEquals("10-99", s.getWorkflowDefs());
        assertEquals("100-999", s.getTaskDefs());
        assertEquals("1k-9999/day", s.getWorkflowsStartedBucket());
        assertEquals("10-99", s.getAvgQueueDepthBucket());
    }

    @Test
    public void readRuntimePopulated() {
        TelemetryPayload.Runtime r = TelemetryCollector.readRuntime();
        assertNotNull(r.getJvmVersion());
        assertNotNull(r.getOsName());
        assertNotNull(r.getOsArch());
        assertTrue(r.getCpuCount() >= 1);
        assertTrue(r.getMaxHeapMb() >= 0);
    }
}
