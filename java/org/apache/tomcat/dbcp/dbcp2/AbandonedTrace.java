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
package org.apache.tomcat.dbcp.dbcp2;

import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.apache.tomcat.dbcp.pool2.TrackedUse;

/**
 * Tracks connection usage for recovering and reporting abandoned connections.
 * <p>
 * The JDBC Connection, Statement, and ResultSet classes extend this class.
 * </p>
 *
 * @since 2.0
 */
public class AbandonedTrace implements TrackedUse, AutoCloseable {

    static void add(final AbandonedTrace receiver, final AbandonedTrace trace) {
        if (receiver != null) {
            receiver.addTrace(trace);
        }
    }

    /** A list of objects created by children of this object. */
    private final List<WeakReference<AbandonedTrace>> traceList = new ArrayList<>();

    /** Last time this connection was used. */
    private volatile Instant lastUsedInstant = Instant.EPOCH;

    /**
     * Creates a new AbandonedTrace without config and without doing abandoned tracing.
     */
    public AbandonedTrace() {
        init(null);
    }

    /**
     * Constructs a new AbandonedTrace with a parent object.
     *
     * @param parent
     *            AbandonedTrace parent object.
     */
    public AbandonedTrace(final AbandonedTrace parent) {
        init(parent);
    }

    /**
     * Adds an object to the list of objects being traced.
     *
     * @param trace
     *            AbandonedTrace object to add.
     */
    protected void addTrace(final AbandonedTrace trace) {
        synchronized (this.traceList) {
            this.traceList.add(new WeakReference<>(trace));
        }
        setLastUsed();
    }

    /**
     * Clears the list of objects being traced by this object.
     */
    protected void clearTrace() {
        synchronized (this.traceList) {
            this.traceList.clear();
        }
    }

    /**
     * Subclasses can implement this nop.
     *
     * @throws SQLException Ignored here, for subclasses.
     * @since 2.10.0
     */
    @Override
    public void close() throws SQLException {
        // nop
    }

    /**
     * Closes this resource and if an exception is caught, then calls {@code exceptionHandler}.
     *
     * @param exceptionHandler Consumes exception thrown closing this resource.
     * @since 2.10.0
     */
    protected void close(final Consumer<Exception> exceptionHandler) {
        Utils.close(this, exceptionHandler);
    }

    /**
     * Gets the last time this object was used in milliseconds.
     *
     * @return long time in milliseconds.
     */
    @Override
    @Deprecated
    public long getLastUsed() {
        return lastUsedInstant.toEpochMilli();
    }

    @Override
    public Instant getLastUsedInstant() {
        return lastUsedInstant;
    }

    /**
     * Gets a list of objects being traced by this object.
     *
     * @return List of objects.
     */
    protected List<AbandonedTrace> getTrace() {
        final int size = traceList.size();
        if (size == 0) {
            return Collections.emptyList();
        }
        final ArrayList<AbandonedTrace> result = new ArrayList<>(size);
        synchronized (this.traceList) {
            final Iterator<WeakReference<AbandonedTrace>> iter = traceList.iterator();
            while (iter.hasNext()) {
                final AbandonedTrace trace = iter.next().get();
                if (trace == null) {
                    // Clean-up since we are here anyway
                    iter.remove();
                } else {
                    result.add(trace);
                }
            }
        }
        return result;
    }

    /**
     * Initializes abandoned tracing for this object.
     *
     * @param parent
     *            AbandonedTrace parent object.
     */
    private void init(final AbandonedTrace parent) {
        AbandonedTrace.add(parent, this);
    }

    /**
     * Removes this object the source object is tracing.
     *
     * @param source The object tracing
     * @since 2.7.0
     */
    protected void removeThisTrace(final Object source) {
        if (source instanceof AbandonedTrace) {
            AbandonedTrace.class.cast(source).removeTrace(this);
        }
    }

    /**
     * Removes a child object this object is tracing.
     *
     * @param trace
     *            AbandonedTrace object to remove.
     */
    protected void removeTrace(final AbandonedTrace trace) {
        synchronized (this.traceList) {
            final Iterator<WeakReference<AbandonedTrace>> iter = traceList.iterator();
            while (iter.hasNext()) {
                final AbandonedTrace traceInList = iter.next().get();
                if (trace != null && trace.equals(traceInList)) {
                    iter.remove();
                    break;
                }
                if (traceInList == null) {
                    // Clean-up since we are here anyway
                    iter.remove();
                }
            }
        }
    }

    /**
     * Sets the time this object was last used to the current time in milliseconds.
     */
    protected void setLastUsed() {
        lastUsedInstant = Instant.now();
    }

    /**
     * Sets the instant this object was last used.
     *
     * @param lastUsedInstant
     *            instant.
     * @since 2.10.0
     */
    protected void setLastUsed(final Instant lastUsedInstant) {
        this.lastUsedInstant = lastUsedInstant;
    }

    /**
     * Sets the time in milliseconds this object was last used.
     *
     * @param lastUsedMillis
     *            time in milliseconds.
     * @deprecated Use {@link #setLastUsed(Instant)}
     */
    @Deprecated
    protected void setLastUsed(final long lastUsedMillis) {
        this.lastUsedInstant = Instant.ofEpochMilli(lastUsedMillis);
    }
}
