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

/**
 * CallStack strategy that uses the stack trace from a {@link Throwable}.
 *
 * @see Throwable#fillInStackTrace()
 * @since 2.4.3
 */
public class ThrowableCallStack implements CallStack {

    private final String messageFormat;
    //@GuardedBy("dateFormat")
    private final DateFormat dateFormat;

    private volatile Snapshot snapshot;

    public ThrowableCallStack(final String messageFormat, final boolean useTimestamp) {
        this.messageFormat = messageFormat;
        this.dateFormat = useTimestamp ? new SimpleDateFormat(messageFormat) : null;
    }

    @Override
    public synchronized boolean printStackTrace(PrintWriter writer) {
        Snapshot snapshot = this.snapshot;
        if (snapshot == null) {
            return false;
        }
        final String message;
        if (dateFormat == null) {
            message = messageFormat;
        } else {
            synchronized (dateFormat) {
                message = dateFormat.format(Long.valueOf(snapshot.timestamp));
            }
        }
        writer.println(message);
        snapshot.printStackTrace(writer);
        return true;
    }

    @Override
    public void fillInStackTrace() {
        snapshot = new Snapshot();
    }

    @Override
    public void clear() {
        snapshot = null;
    }

    private static class Snapshot extends Throwable {
        private static final long serialVersionUID = -7871548158947014789L;
        private final long timestamp = System.currentTimeMillis();
    }
}
