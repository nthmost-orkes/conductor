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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * The complete telemetry payload, mirroring {@code telemetry-payload.schema.json} field-for-field.
 * Serialized with a snake_case {@link ObjectMapper}, so Java camelCase fields map to the schema's
 * snake_case keys (e.g. {@code schemaVersion} -> {@code schema_version}).
 *
 * <p>Nulls are intentionally serialized (not omitted) so that {@code email} and {@code cluster_id}
 * appear explicitly as {@code null} — matching the documented "exact payload".
 */
public class TelemetryPayload {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    /** A snake_case mapper shared by the collector, preview logging, and the sender. */
    static ObjectMapper mapper() {
        return MAPPER;
    }

    private final int schemaVersion = 1;
    private final String instanceId;
    private final String clusterId;
    private final String conductorVersion;
    private final String sentAt;
    private final String email;
    private final Runtime runtime;
    private final Modules modules;
    private final Scale scale;

    public TelemetryPayload(
            String instanceId,
            String clusterId,
            String conductorVersion,
            String sentAt,
            String email,
            Runtime runtime,
            Modules modules,
            Scale scale) {
        this.instanceId = instanceId;
        this.clusterId = clusterId;
        this.conductorVersion = conductorVersion;
        this.sentAt = sentAt;
        this.email = email;
        this.runtime = runtime;
        this.modules = modules;
        this.scale = scale;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getClusterId() {
        return clusterId;
    }

    public String getConductorVersion() {
        return conductorVersion;
    }

    public String getSentAt() {
        return sentAt;
    }

    public String getEmail() {
        return email;
    }

    public Runtime getRuntime() {
        return runtime;
    }

    public Modules getModules() {
        return modules;
    }

    public Scale getScale() {
        return scale;
    }

    /** Coarse JVM / OS / hardware facts. */
    public static class Runtime {
        private final String jvmVersion;
        private final String osName;
        private final String osArch;
        private final int cpuCount;
        private final long maxHeapMb;

        public Runtime(
                String jvmVersion, String osName, String osArch, int cpuCount, long maxHeapMb) {
            this.jvmVersion = jvmVersion;
            this.osName = osName;
            this.osArch = osArch;
            this.cpuCount = cpuCount;
            this.maxHeapMb = maxHeapMb;
        }

        public String getJvmVersion() {
            return jvmVersion;
        }

        public String getOsName() {
            return osName;
        }

        public String getOsArch() {
            return osArch;
        }

        public int getCpuCount() {
            return cpuCount;
        }

        public long getMaxHeapMb() {
            return maxHeapMb;
        }
    }

    /** Which backend modules are active. Values are the raw configured type strings. */
    public static class Modules {
        private final String dbType;
        private final String queueType;
        private final String indexingType;
        private final String eventQueueType;
        private final String externalPayloadStorage;
        private final String fileStorage;

        public Modules(
                String dbType,
                String queueType,
                String indexingType,
                String eventQueueType,
                String externalPayloadStorage,
                String fileStorage) {
            this.dbType = dbType;
            this.queueType = queueType;
            this.indexingType = indexingType;
            this.eventQueueType = eventQueueType;
            this.externalPayloadStorage = externalPayloadStorage;
            this.fileStorage = fileStorage;
        }

        public String getDbType() {
            return dbType;
        }

        public String getQueueType() {
            return queueType;
        }

        public String getIndexingType() {
            return indexingType;
        }

        public String getEventQueueType() {
            return eventQueueType;
        }

        public String getExternalPayloadStorage() {
            return externalPayloadStorage;
        }

        public String getFileStorage() {
            return fileStorage;
        }
    }

    /** Bucketed scale/load signals. Exact counts and rates are never sent. */
    public static class Scale {
        private final String serverNodes;
        private final String workflowDefs;
        private final String taskDefs;
        private final String workflowsStartedBucket;
        private final String avgQueueDepthBucket;

        public Scale(
                String serverNodes,
                String workflowDefs,
                String taskDefs,
                String workflowsStartedBucket,
                String avgQueueDepthBucket) {
            this.serverNodes = serverNodes;
            this.workflowDefs = workflowDefs;
            this.taskDefs = taskDefs;
            this.workflowsStartedBucket = workflowsStartedBucket;
            this.avgQueueDepthBucket = avgQueueDepthBucket;
        }

        public String getServerNodes() {
            return serverNodes;
        }

        public String getWorkflowDefs() {
            return workflowDefs;
        }

        public String getTaskDefs() {
            return taskDefs;
        }

        public String getWorkflowsStartedBucket() {
            return workflowsStartedBucket;
        }

        public String getAvgQueueDepthBucket() {
            return avgQueueDepthBucket;
        }
    }
}
