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

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.VersionService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Assembles a {@link TelemetryPayload} from live server state. All transformation logic lives in
 * pure, dependency-free static helpers ({@link #readModules}, {@link #buildScale}, {@link
 * #readRuntime}) so it can be unit tested without Spring; this bean is thin glue that reads the
 * numbers from the injected services and hands them to those helpers.
 *
 * <p>Only created when {@code conductor.telemetry.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "conductor.telemetry.enabled", havingValue = "true")
public class TelemetryCollector {

    private static final String WORKFLOW_START_COUNTER = "workflow_start_success";

    private final Environment environment;
    private final MetadataService metadataService;
    private final QueueDAO queueDAO;
    private final MeterRegistry meterRegistry;
    private final VersionService versionService;
    private final TelemetryProperties properties;

    public TelemetryCollector(
            Environment environment,
            MetadataService metadataService,
            QueueDAO queueDAO,
            MeterRegistry meterRegistry,
            VersionService versionService,
            TelemetryProperties properties) {
        this.environment = environment;
        this.metadataService = metadataService;
        this.queueDAO = queueDAO;
        this.meterRegistry = meterRegistry;
        this.versionService = versionService;
        this.properties = properties;
    }

    /**
     * Builds the full payload for the given persistent state (source of the stable instance id).
     */
    public TelemetryPayload collect(TelemetryState state) {
        long workflowDefs = safeCount(() -> metadataService.getWorkflowDefs().size());
        long taskDefs = safeCount(() -> metadataService.getTaskDefs().size());
        long serverNodes = 1; // TODO: multi-node cluster sizing — see cluster_id open question
        return new TelemetryPayload(
                state.getInstanceId(),
                null, // cluster_id reserved for a future release
                versionService.getVersion(),
                Instant.now().toString(),
                blankToNull(properties.getEmail()),
                readRuntime(),
                readModules(environment::getProperty),
                buildScale(
                        workflowDefs,
                        taskDefs,
                        serverNodes,
                        averageQueueDepth(),
                        workflowsPerDay()));
    }

    // ---- pure helpers (unit tested) ------------------------------------------------------------

    /** Reads the active module types from a property lookup. Pure: no Spring, no I/O. */
    static TelemetryPayload.Modules readModules(Function<String, String> get) {
        String db = orUnknown(get.apply("conductor.db.type"));
        String queue = orUnknown(get.apply("conductor.queue.type"));
        boolean indexingEnabled =
                !"false"
                        .equalsIgnoreCase(
                                orDefault(get.apply("conductor.indexing.enabled"), "true"));
        String indexing =
                indexingEnabled ? orUnknown(get.apply("conductor.indexing.type")) : "none";
        String eventQueue = orDefault(get.apply("conductor.default-event-queue.type"), "none");
        String externalPayload =
                orDefault(get.apply("conductor.external-payload-storage.type"), "none");
        boolean fileStorageEnabled =
                "true"
                        .equalsIgnoreCase(
                                orDefault(get.apply("conductor.file-storage.enabled"), "false"));
        String fileStorage =
                fileStorageEnabled
                        ? orDefault(get.apply("conductor.file-storage.type"), "conductor")
                        : "none";
        return new TelemetryPayload.Modules(
                db, queue, indexing, eventQueue, externalPayload, fileStorage);
    }

    /** Buckets raw scale numbers. Pure. */
    static TelemetryPayload.Scale buildScale(
            long workflowDefs,
            long taskDefs,
            long serverNodes,
            double avgQueueDepth,
            double workflowsPerDay) {
        return new TelemetryPayload.Scale(
                Buckets.serverNodes(serverNodes),
                Buckets.count(workflowDefs),
                Buckets.count(taskDefs),
                Buckets.perDay(workflowsPerDay),
                Buckets.count(Math.round(avgQueueDepth)));
    }

    /** Coarse JVM / OS / hardware facts. Pure (reads only process-global state). */
    static TelemetryPayload.Runtime readRuntime() {
        java.lang.Runtime rt = java.lang.Runtime.getRuntime();
        return new TelemetryPayload.Runtime(
                System.getProperty("java.version", "unknown"),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"),
                rt.availableProcessors(),
                Buckets.heapMb(rt.maxMemory()));
    }

    // ---- live sources (guarded so telemetry never disrupts the server) --------------------------

    private double averageQueueDepth() {
        try {
            Map<String, Long> detail = queueDAO.queuesDetail();
            if (detail == null || detail.isEmpty()) {
                return 0;
            }
            return detail.values().stream().mapToLong(Long::longValue).average().orElse(0);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Rough average workflows started per day since boot: cumulative {@code workflow_start_success}
     * count scaled by uptime. TODO: replace with a proper windowed per-day rate.
     */
    private double workflowsPerDay() {
        try {
            double total =
                    meterRegistry.find(WORKFLOW_START_COUNTER).counters().stream()
                            .mapToDouble(Counter::count)
                            .sum();
            long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
            if (uptimeMillis <= 0) {
                return 0;
            }
            return total * 86_400_000.0 / uptimeMillis;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long safeCount(java.util.function.LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String orUnknown(String value) {
        return orDefault(value, "unknown");
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
