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
package org.apache.tomcat.dbcp.pool2.impl;

import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;

/**
 * CallStack strategy that uses the stack trace from a {@link Throwable}. This
 * strategy provides call stack method names and other metadata in addition to
 * the call stack of classes.
 *
 * @see Throwable#fillInStackTrace()
 * @since 2.4.3
 */
public class ThrowableCallStack implements CallStack {

    /**
     * A snapshot of a throwable.
     */
    private static final class Snapshot extends Throwable {

        private static final long serialVersionUID = 1L;
        private final Instant timestamp;

        /**
         * Constructs a new instance with its message set to the now instant.
         */
        Snapshot() {
            this(Instant.now());
        }

        /**
         * Constructs a new instance and use the timestamp as the message with using {@link DateTimeFormatter#ISO_INSTANT} for more precision.
         *
         * @param timestamp normally the now instant.
         */
        private Snapshot(final Instant timestamp) {
            super(timestamp.toString());
            this.timestamp = timestamp;
        }
    }

    private final String messageFormat;

    // We keep the SimpleDateFormat for backward compatibility instead of a DateTimeFormatter.
    //@GuardedBy("dateFormat")
    private final DateFormat dateFormat;

    private volatile Snapshot snapshot;

    /**
     * Creates a new instance.
     *
     * @param messageFormat message format
     * @param useTimestamp whether to format the dates in the output message or not
     */
    public ThrowableCallStack(final String messageFormat, final boolean useTimestamp) {
        this.messageFormat = messageFormat;
        this.dateFormat = useTimestamp ? new SimpleDateFormat(messageFormat) : null;
    }

    @Override
    public void clear() {
        snapshot = null;
    }

    @Override
    public void fillInStackTrace() {
        snapshot = new Snapshot();
    }

    @Override
    public synchronized boolean printStackTrace(final PrintWriter writer) {
        final Snapshot snapshotRef = this.snapshot;
        if (snapshotRef == null) {
            return false;
        }
        final String message;
        if (dateFormat == null) {
            message = messageFormat;
        } else {
            synchronized (dateFormat) {
                // The throwable message is in {@link DateTimeFormatter#ISO_INSTANT} format for more precision.
                message = dateFormat.format(Long.valueOf(snapshotRef.timestamp.toEpochMilli()));
            }
        }
        writer.println(message);
        snapshotRef.printStackTrace(writer);
        return true;
    }
}
