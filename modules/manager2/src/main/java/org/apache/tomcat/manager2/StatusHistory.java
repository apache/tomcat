/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.manager2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * A rolling, in-memory history of status samples, collected at a fixed tick interval by a background task and served by
 * {@code GET /api/status/history}. The history holds the samples of the last {@code windowMs} milliseconds (the default
 * of 10 minutes at a 2 second tick is 300 samples) and drives the charts of the Dashboard, which therefore always show
 * the last {@code windowMs} of server activity regardless of when the page was opened.
 * <p>
 * Each sample aggregates the values the Dashboard displays: JVM heap usage, busy threads and cumulative
 * request/error/byte counters summed over all connectors, plus the active session count of all deployed applications.
 * The per-second rates (requests/s, errors/s, bytes/s) are derived from the difference to the previous sample at
 * collection time, so consumers do not need to keep their own baseline. The rates of the very first sample are
 * {@code null} because there is no baseline yet.
 * <p>
 * Samples are immutable once recorded. The recording thread is the scheduled collection task; readers are the API
 * servlet threads. Synchronizing the two operations is sufficient for safe publication.
 */
public final class StatusHistory {


    /**
     * The default collection period in milliseconds (2 seconds).
     */
    public static final long DEFAULT_TICK_MS = 2000;


    /**
     * The default collection window in milliseconds (10 minutes).
     */
    public static final long DEFAULT_WINDOW_MS = 10 * 60 * 1000;


    private final long tickMs;

    private final long windowMs;

    private final Deque<Sample> samples = new ArrayDeque<>();

    private List<Map<String, Object>> latestApps = List.of();

    private long previousTs = -1;

    private long previousCount;

    private long previousErrors;

    private long previousBytesSent;

    private long previousBytesReceived;


    /**
     * Create a new history.
     *
     * @param tickMs   the collection period in milliseconds
     * @param windowMs the window of history to keep in milliseconds (must be at least one tick)
     */
    public StatusHistory(long tickMs, long windowMs) {
        this.tickMs = tickMs;
        this.windowMs = windowMs;
    }


    /**
     * Record a new sample from the given snapshot (the result of {@link StatusSnapshot#snapshot}). This method is
     * called by the scheduled collection task and must not be called concurrently with itself.
     *
     * @param snap the snapshot of the current runtime state
     */
    public synchronized void record(Map<String, Object> snap) {

        long ts = asLong(snap.get("ts"));

        long heapUsed = 0;
        long heapCommitted = 0;
        long heapMax = 0;
        if (snap.get("jvm") instanceof Map<?, ?> jvm && jvm.get("memory") instanceof Map<?, ?> memory) {
            heapUsed = asLong(memory.get("used"));
            heapCommitted = asLong(memory.get("committed"));
            heapMax = asLong(memory.get("max"));
        }

        long threadsBusy = 0;
        long threadsMax = 0;
        long count = 0;
        long errors = 0;
        long bytesSent = 0;
        long bytesReceived = 0;
        if (snap.get("connectors") instanceof List<?> connectors) {
            for (Object connectorObj : connectors) {
                Map<String, Object> connector = asMap(connectorObj);
                if (connector == null) {
                    continue;
                }
                if (connector.get("threads") instanceof Map<?, ?> threads) {
                    threadsMax += asLong(threads.get("max"));
                    threadsBusy += asLong(threads.get("busy"));
                }
                if (connector.get("requests") instanceof Map<?, ?> requests) {
                    count += asLong(requests.get("count"));
                    errors += asLong(requests.get("errors"));
                    bytesSent += asLong(requests.get("bytesSent"));
                    bytesReceived += asLong(requests.get("bytesReceived"));
                }
            }
        }

        long sessions = 0;
        List<Map<String, Object>> apps = new ArrayList<>();
        if (snap.get("apps") instanceof List<?> appList) {
            for (Object app : appList) {
                Map<String, Object> appMap = asMap(app);
                if (appMap != null) {
                    sessions += asLong(appMap.get("activeSessions"));
                    apps.add(appMap);
                }
            }
        }

        Double rps = null;
        Double eps = null;
        Double bpsSent = null;
        Double bpsRecv = null;
        if (previousTs > 0) {
            double dt = (ts - previousTs) / 1000.0;
            if (dt > 0 && dt < 30) {
                rps = Math.max(0, (count - previousCount) / dt);
                eps = Math.max(0, (errors - previousErrors) / dt);
                bpsSent = Math.max(0, (bytesSent - previousBytesSent) / dt);
                bpsRecv = Math.max(0, (bytesReceived - previousBytesReceived) / dt);
            }
        }

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("ts", Long.valueOf(ts));
        json.put("heapUsed", Long.valueOf(heapUsed));
        json.put("heapCommitted", Long.valueOf(heapCommitted));
        json.put("heapMax", Long.valueOf(heapMax));
        json.put("threadsBusy", Long.valueOf(threadsBusy));
        json.put("threadsMax", Long.valueOf(threadsMax));
        json.put("sessions", Long.valueOf(sessions));
        json.put("rps", rps);
        json.put("eps", eps);
        json.put("bpsSent", bpsSent);
        json.put("bpsRecv", bpsRecv);

        samples.addLast(new Sample(ts, json));
        long cutoff = ts - windowMs;
        while (!samples.isEmpty() && samples.peekFirst().ts < cutoff) {
            samples.removeFirst();
        }

        previousTs = ts;
        previousCount = count;
        previousErrors = errors;
        previousBytesSent = bytesSent;
        previousBytesReceived = bytesReceived;
        latestApps = apps;
    }


    /**
     * Build the JSON payload served by {@code GET /api/status/history}: the configuration ({@code windowMs},
     * {@code tickMs}), the samples of the current window (oldest first) and the application list of the latest sample.
     *
     * @return the payload
     */
    public synchronized Map<String, Object> payload() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowMs", Long.valueOf(windowMs));
        result.put("tickMs", Long.valueOf(tickMs));
        List<Map<String, Object>> json = new ArrayList<>(samples.size());
        for (Sample sample : samples) {
            json.add(sample.json);
        }
        result.put("samples", json);
        result.put("apps", latestApps);
        return result;
    }


    /**
     * A single recorded sample.
     */
    private static final class Sample {

        private final long ts;

        private final Map<String, Object> json;

        private Sample(long ts, Map<String, Object> json) {
            this.ts = ts;
            this.json = json;
        }
    }


    private static long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0;
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}
