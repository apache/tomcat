/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.test.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.juli.logging.LogFactory;

/**
 * Shared capture of JULI/JUL records for the diagnostic tests.
 * <p>The handler is attached to the named loggers, the logger levels are
 * raised to {@link Level#ALL}, and a canary record is written through the
 * same JULI logger the production code logs through. The constructor fails
 * if the capture does not observe the canary, so a test cannot silently pass
 * against a handler that is wired to the wrong logger.</p>
 * <p>The capture restores the original levels and removes its handler on
 * {@link #close()}; tests use it in try-with-resources.</p>
 */
public class LogCapture implements AutoCloseable {

    protected final List<LogRecord> records =
            Collections.synchronizedList(new ArrayList<>());

    private final List<Logger> loggers = new ArrayList<>();
    private final List<Level> originalLevels = new ArrayList<>();

    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            // no buffering
        }

        @Override
        public void close() {
            // nothing to release
        }
    };

    /**
     * @param canary        marker message written through JULI
     * @param canaryOwner   class whose logger must carry the canary
     * @param loggerOwners  loggers whose records the test wants to capture
     */
    public LogCapture(String canary, Class<?> canaryOwner, Class<?>... loggerOwners) {
        java.util.Set<String> added = new java.util.HashSet<>();
        for (Class<?> owner : loggerOwners) {
            if (!added.add(owner.getName())) {
                continue;
            }
            Logger logger = Logger.getLogger(owner.getName());
            originalLevels.add(logger.getLevel());
            logger.setLevel(Level.ALL);
            logger.addHandler(handler);
            loggers.add(logger);
        }
        LogFactory.getLog(canaryOwner).error(canary);
        if (!hasMessage(canary)) {
            close();
            throw new AssertionError(
                    "the test handler must observe what this class logs");
        }
    }

    /**
     * @param message the exact message to look for
     * @return {@code true} when a captured record carries the message
     */
    public boolean hasMessage(String message) {
        synchronized (records) {
            return records.stream().anyMatch(r -> message.equals(r.getMessage()));
        }
    }

    /**
     * @return a snapshot of the captured records, safe for concurrent reporting
     */
    protected LogRecord[] snapshot() {
        synchronized (records) {
            return records.toArray(new LogRecord[0]);
        }
    }

    /**
     * @return a mutable snapshot list, for tests that walk it
     */
    protected List<LogRecord> snapshotList() {
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }

    /** Discard the canary/startup records once the fixture is armed. */
    public void clear() {
        synchronized (records) {
            records.clear();
        }
    }

    @Override
    public void close() {
        for (int i = 0; i < loggers.size(); i++) {
            loggers.get(i).removeHandler(handler);
            loggers.get(i).setLevel(originalLevels.get(i));
        }
        records.clear();
    }
}
