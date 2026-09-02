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

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TelemetryPayloadTest {

    @Test
    public void serializesSnakeCaseWithExplicitNulls() {
        TelemetryPayload payload =
                new TelemetryPayload(
                        "uuid-1",
                        null,
                        "3.21.5",
                        "2026-08-31T00:00:00Z",
                        null,
                        new TelemetryPayload.Runtime("21.0.2", "Linux", "aarch64", 8, 4096),
                        new TelemetryPayload.Modules(
                                "postgres", "postgres", "elasticsearch", "sqs", "s3", "none"),
                        new TelemetryPayload.Scale(
                                "2-5", "10-99", "100-999", "1k-9999/day", "10-99"));

        JsonNode json = TelemetryPayload.mapper().valueToTree(payload);

        assertEquals(1, json.get("schema_version").asInt());
        assertEquals("uuid-1", json.get("instance_id").asText());
        assertEquals("3.21.5", json.get("conductor_version").asText());

        // cluster_id and email must be present and explicitly null (matches TELEMETRY.md)
        assertTrue(json.has("cluster_id"));
        assertTrue(json.get("cluster_id").isNull());
        assertTrue(json.has("email"));
        assertTrue(json.get("email").isNull());

        assertEquals("aarch64", json.get("runtime").get("os_arch").asText());
        assertEquals(4096, json.get("runtime").get("max_heap_mb").asLong());
        assertEquals("s3", json.get("modules").get("external_payload_storage").asText());
        assertEquals("none", json.get("modules").get("file_storage").asText());
        assertEquals("1k-9999/day", json.get("scale").get("workflows_started_bucket").asText());
    }
}
