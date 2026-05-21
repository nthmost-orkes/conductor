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
package org.conductoross.conductor.webhook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the webhook worker.
 *
 * <p>These properties control the behavior of the {@link
 * org.conductoross.conductor.webhook.WebhookWorker} that polls the webhook queue and processes
 * incoming events.
 */
@Configuration
@ConfigurationProperties("conductor.webhook.worker")
public class WebhookWorkerProperties {

    /** Queue name shared by the producer (IncomingWebhookService) and consumer (WebhookWorker). */
    public static final String WEBHOOK_QUEUE = "_webhook_queue";

    /** Number of worker threads polling the webhook queue. Set to 0 to disable the worker. */
    private int threadCount = 1;

    /** Polling interval in milliseconds between queue polls. */
    private int pollingInterval = 1000;

    /** Number of messages to fetch per poll operation. */
    private int pollBatchSize = 10;

    /** Maximum number of recent execution histories to retain per webhook. */
    private int lastRunWorkflowIdSize = 10;

    public int getLastRunWorkflowIdSize() {
        return lastRunWorkflowIdSize;
    }

    public void setLastRunWorkflowIdSize(int lastRunWorkflowIdSize) {
        this.lastRunWorkflowIdSize = lastRunWorkflowIdSize;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(int pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

    public int getPollBatchSize() {
        return pollBatchSize;
    }

    public void setPollBatchSize(int pollBatchSize) {
        this.pollBatchSize = pollBatchSize;
    }
}
