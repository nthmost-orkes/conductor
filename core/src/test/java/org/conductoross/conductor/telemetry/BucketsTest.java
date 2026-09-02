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

import static org.junit.Assert.assertEquals;

public class BucketsTest {

    @Test
    public void countBoundaries() {
        assertEquals("0", Buckets.count(0));
        assertEquals("0", Buckets.count(-5));
        assertEquals("1-9", Buckets.count(1));
        assertEquals("1-9", Buckets.count(9));
        assertEquals("10-99", Buckets.count(10));
        assertEquals("10-99", Buckets.count(99));
        assertEquals("100-999", Buckets.count(100));
        assertEquals("100-999", Buckets.count(999));
        assertEquals("1000-9999", Buckets.count(1000));
        assertEquals("1000-9999", Buckets.count(9999));
        assertEquals("10000+", Buckets.count(10000));
        assertEquals("10000+", Buckets.count(5_000_000));
    }

    @Test
    public void serverNodesBoundaries() {
        assertEquals("1", Buckets.serverNodes(0));
        assertEquals("1", Buckets.serverNodes(1));
        assertEquals("2-5", Buckets.serverNodes(2));
        assertEquals("2-5", Buckets.serverNodes(5));
        assertEquals("6-20", Buckets.serverNodes(6));
        assertEquals("6-20", Buckets.serverNodes(20));
        assertEquals("21-100", Buckets.serverNodes(21));
        assertEquals("21-100", Buckets.serverNodes(100));
        assertEquals("100+", Buckets.serverNodes(101));
    }

    @Test
    public void perDayBoundaries() {
        assertEquals("0", Buckets.perDay(0));
        assertEquals("0", Buckets.perDay(0.4));
        assertEquals("1-99/day", Buckets.perDay(1));
        assertEquals("1-99/day", Buckets.perDay(99.9));
        assertEquals("100-999/day", Buckets.perDay(100));
        assertEquals("100-999/day", Buckets.perDay(999.9));
        assertEquals("1k-9999/day", Buckets.perDay(1_000));
        assertEquals("10k-99k/day", Buckets.perDay(10_000));
        assertEquals("100k-999k/day", Buckets.perDay(100_000));
        assertEquals("1M+/day", Buckets.perDay(1_000_000));
        assertEquals("1M+/day", Buckets.perDay(5_000_000));
    }

    @Test
    public void heapMbRoundsToNearest1024() {
        assertEquals(0L, Buckets.heapMb(0));
        assertEquals(4096L, Buckets.heapMb(4096L * 1024 * 1024));
        assertEquals(4096L, Buckets.heapMb((long) (3.9 * 1024 * 1024 * 1024)));
        assertEquals(1024L, Buckets.heapMb(1500L * 1024 * 1024));
    }
}
