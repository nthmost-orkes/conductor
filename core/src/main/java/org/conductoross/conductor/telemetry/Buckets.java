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

/**
 * Pure bucketing helpers. Counts and rates are reported as coarse, non-overlapping ranges so that
 * no deployment can be fingerprinted by its exact scale. The returned strings are the controlled
 * vocabulary defined in {@code telemetry-payload.schema.json}.
 */
final class Buckets {

    private Buckets() {}

    /** Order-of-magnitude bucket for a non-negative count. */
    static String count(long n) {
        if (n <= 0) return "0";
        if (n < 10) return "1-9";
        if (n < 100) return "10-99";
        if (n < 1_000) return "100-999";
        if (n < 10_000) return "1000-9999";
        return "10000+";
    }

    /** Bucket for a number of server nodes. */
    static String serverNodes(long n) {
        if (n <= 1) return "1";
        if (n <= 5) return "2-5";
        if (n <= 20) return "6-20";
        if (n <= 100) return "21-100";
        return "100+";
    }

    /** Order-of-magnitude bucket for a per-day rate. */
    static String perDay(double ratePerDay) {
        if (ratePerDay < 1) return "0";
        if (ratePerDay < 100) return "1-99/day";
        if (ratePerDay < 1_000) return "100-999/day";
        if (ratePerDay < 10_000) return "1k-9999/day";
        if (ratePerDay < 100_000) return "10k-99k/day";
        if (ratePerDay < 1_000_000) return "100k-999k/day";
        return "1M+/day";
    }

    /** Max heap in MB, rounded to the nearest 1024 MB to avoid fingerprinting. */
    static long heapMb(long maxBytes) {
        if (maxBytes <= 0) return 0;
        double mb = maxBytes / (1024.0 * 1024.0);
        return Math.round(mb / 1024.0) * 1024L;
    }
}
