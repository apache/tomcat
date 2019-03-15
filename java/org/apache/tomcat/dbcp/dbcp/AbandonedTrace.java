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

package org.apache.tomcat.dbcp.dbcp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks db connection usage for recovering and reporting
 * abandoned db connections.
 *
 * The JDBC Connection, Statement, and ResultSet classes
 * extend this class.
 * 
 * @author Glenn L. Nielsen
 * @version $Revision: 899987 $ $Date: 2010-01-16 11:51:16 -0500 (Sat, 16 Jan 2010) $
 */
public class AbandonedTrace {

    /** DBCP AbandonedConfig */
    private final AbandonedConfig config;
    /** A stack trace of the code that created me (if in debug mode) */
    private volatile Exception createdBy;
    /** A list of objects created by children of this object */
    private final List traceList = new ArrayList();
    /** Last time this connection was used */
    private volatile long lastUsed = 0;

    /**
     * Create a new AbandonedTrace without config and
     * without doing abandoned tracing.
     */
    public AbandonedTrace() {
        this.config = null;
        init(null);
    }

    /**
     * Construct a new AbandonedTrace with no parent object.
     *
     * @param config AbandonedConfig
     */
    public AbandonedTrace(AbandonedConfig config) {
        this.config = config;
        init(null);
    }

    /**
     * Construct a new AbandonedTrace with a parent object.
     *
     * @param parent AbandonedTrace parent object
     */
    public AbandonedTrace(AbandonedTrace parent) {
        this.config = parent.getConfig();
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

        if (config == null) {
            return;
        }
        if (config.getLogAbandoned()) {
            createdBy = new AbandonedObjectException();
        }
    }

    /**
     * Get the abandoned config for this object.
     *
     * @return AbandonedConfig for this object
     */
    protected AbandonedConfig getConfig() {
        return config;
    }

    /**
     * Get the last time this object was used in ms.
     *
     * @return long time in ms
     */
    protected long getLastUsed() {
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
     * If logAbandoned=true generate a stack trace
     * for this object then add this object to the parent
     * object trace list.
     */
    protected void setStackTrace() {
        if (config == null) {                 
            return;                           
        }                    
        if (config.getLogAbandoned()) {
            createdBy = new AbandonedObjectException();
        }
    }

    /**
     * Add an object to the list of objects being
     * traced.
     *
     * @param trace AbandonedTrace object to add
     */
    protected void addTrace(AbandonedTrace trace) {
        synchronized (this.traceList) {
            this.traceList.add(trace);
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
    protected List getTrace() {
        synchronized (this.traceList) {
            return new ArrayList(traceList);
        }
    }

    /**
     * Prints a stack trace of the code that
     * created this object.
     */
    public void printStackTrace() {
        if (createdBy != null && config != null) {
            createdBy.printStackTrace(config.getLogWriter());
        }
        synchronized(this.traceList) {
            Iterator it = this.traceList.iterator();
            while (it.hasNext()) {
                AbandonedTrace at = (AbandonedTrace)it.next();
                at.printStackTrace();
            }
        }
    }

    /**
     * Remove a child object this object is tracing.
     *
     * @param trace AbandonedTrace object to remove
     */
    protected void removeTrace(AbandonedTrace trace) {
        synchronized(this.traceList) {
            this.traceList.remove(trace);
        }
    }

    static class AbandonedObjectException extends Exception {

        private static final long serialVersionUID = 7398692158058772916L;

        /** Date format */
        //@GuardedBy("this")
        private static final SimpleDateFormat format = new SimpleDateFormat
            ("'DBCP object created' yyyy-MM-dd HH:mm:ss " +
             "'by the following code was never closed:'");

        private final long _createdTime;

        public AbandonedObjectException() {
            _createdTime = System.currentTimeMillis();
        }

        // Override getMessage to avoid creating objects and formatting
        // dates unless the log message will actually be used.
        public String getMessage() {
            String msg;
            synchronized(format) {
                msg = format.format(new Date(_createdTime));
            }
            return msg;
        }
    }
}
