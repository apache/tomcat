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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.tomcat.dbcp.pool2.TrackedUse;

/**
 * Tracks db connection usage for recovering and reporting
 * abandoned db connections.
 *
 * The JDBC Connection, Statement, and ResultSet classes
 * extend this class.
 *
 * @author Glenn L. Nielsen
 * @since 2.0
 */
public class AbandonedTrace implements TrackedUse {

    /** A list of objects created by children of this object */
    private final List<WeakReference<AbandonedTrace>> traceList = new ArrayList<>();
    /** Last time this connection was used */
    private volatile long lastUsed = 0;

    /**
     * Create a new AbandonedTrace without config and
     * without doing abandoned tracing.
     */
    public AbandonedTrace() {
        init(null);
    }

    /**
     * Construct a new AbandonedTrace with a parent object.
     *
     * @param parent AbandonedTrace parent object
     */
    public AbandonedTrace(AbandonedTrace parent) {
        init(parent);
    }

    /**
     * Initialize abandoned tracing for this object.
     *
     * @param parent AbandonedTrace parent object
     */
    private void init(AbandonedTrace parent) {
        if (parent != null) {
            parent.addTrace(this);
        }
    }

    /**
     * Get the last time this object was used in ms.
     *
     * @return long time in ms
     */
    @Override
    public long getLastUsed() {
        return lastUsed;
    }

    /**
     * Set the time this object was last used to the
     * current time in ms.
     */
    protected void setLastUsed() {
        lastUsed = System.currentTimeMillis();
    }

    /**
     * Set the time in ms this object was last used.
     *
     * @param time time in ms
     */
    protected void setLastUsed(long time) {
        lastUsed = time;
    }

    /**
     * Add an object to the list of objects being
     * traced.
     *
     * @param trace AbandonedTrace object to add
     */
    protected void addTrace(AbandonedTrace trace) {
        synchronized (this.traceList) {
            this.traceList.add(new WeakReference<>(trace));
        }
        setLastUsed();
    }

    /**
     * Clear the list of objects being traced by this
     * object.
     */
    protected void clearTrace() {
        synchronized(this.traceList) {
            this.traceList.clear();
        }
    }

    /**
     * Get a list of objects being traced by this object.
     *
     * @return List of objects
     */
    protected List<AbandonedTrace> getTrace() {
        int size = traceList.size();
        if (size == 0) {
            return Collections.emptyList();
        }
        ArrayList<AbandonedTrace> result = new ArrayList<>(size);
        synchronized (this.traceList) {
            Iterator<WeakReference<AbandonedTrace>> iter = traceList.iterator();
            while (iter.hasNext()) {
                WeakReference<AbandonedTrace> ref = iter.next();
                if (ref.get() == null) {
                    // Clean-up since we are here anyway
                    iter.remove();
                } else {
                    result.add(ref.get());
                }
            }
        }
        return result;
    }

    /**
     * Remove a child object this object is tracing.
     *
     * @param trace AbandonedTrace object to remove
     */
    protected void removeTrace(AbandonedTrace trace) {
        synchronized(this.traceList) {
            Iterator<WeakReference<AbandonedTrace>> iter = traceList.iterator();
            while (iter.hasNext()) {
                WeakReference<AbandonedTrace> ref = iter.next();
                if (trace.equals(ref.get())) {
                    iter.remove();
                    break;
                } else if (ref.get() == null) {
                    // Clean-up since we are here anyway
                    iter.remove();
                }
            }
        }
    }
}
