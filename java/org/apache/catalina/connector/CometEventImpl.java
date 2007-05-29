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


package org.apache.catalina.connector;

import java.io.IOException;
import java.util.HashSet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.CometEvent;
import org.apache.catalina.util.StringManager;
import org.apache.coyote.ActionCode;
import org.apache.tomcat.util.net.PollerInterest;

public class CometEventImpl implements CometEvent {

    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


    public CometEventImpl(Request request, Response response) {
        this.request = request;
        this.response = response;
    }


    // ----------------------------------------------------- Instance Variables

    
    /**
     * Associated request.
     */
    protected Request request = null;


    /**
     * Associated response.
     */
    protected Response response = null;

    
    /**
     * Event type.
     */
    protected EventType eventType = EventType.BEGIN;
    

    /**
     * Event sub type.
     */
    protected EventSubType eventSubType = null;
    
    /**
     * Current set of operations
     */
    protected HashSet<CometOperation> cometOperations = new HashSet<CometOperation>(3);
    
    /**
     * Current set of configurations
     */
    protected HashSet<CometConfiguration> cometConfigurations = new HashSet<CometConfiguration>(3);

    protected WorkerThreadCheck threadCheck = new WorkerThreadCheck();

    private static final Object threadCheckHolder = new Object();
    
    // --------------------------------------------------------- Public Methods

    /**
     * Clear the event.
     */
    public void clear() {
        request = null;
        response = null;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }
    
    public void setEventSubType(EventSubType eventSubType) {
        this.eventSubType = eventSubType;
    }
    
    public void close() throws IOException {
        if (request == null) {
            throw new IllegalStateException(sm.getString("cometEvent.nullRequest"));
        }
        request.setComet(false);
        response.finishResponse();
    }

    public EventSubType getEventSubType() {
        return eventSubType;
    }

    public EventType getEventType() {
        return eventType;
    }

    public HttpServletRequest getHttpServletRequest() {
        return request.getRequest();
    }

    public HttpServletResponse getHttpServletResponse() {
        return response.getResponse();
    }

    public void setTimeout(int timeout) throws IOException, ServletException,UnsupportedOperationException {
        //this check should get removed as soon as connection timeout is implemented everywhere.
        if (request.getAttribute("org.apache.tomcat.comet.timeout.support") == Boolean.TRUE) {
            request.action(ActionCode.ACTION_COMET_TIMEOUT,new Integer(timeout));
        } else {
            throw new UnsupportedOperationException();
        }
    }
    
    public boolean isReadable() {
        return request.isReadable();
    }    
    public boolean isWriteable() {
        return response.isWriteable();
    }
    
    public void configure(CometEvent.CometConfiguration... options)
        throws IOException, IllegalStateException {
        checkWorkerThread();
        synchronized (cometConfigurations) {
            cometConfigurations.clear();
            for (CometEvent.CometConfiguration cc : options) {
                cometConfigurations.add(cc);
            }
            request.action(ActionCode.ACTION_COMET_CONFIGURE,options);
        }
    }

    public void register(CometEvent.CometOperation... operations)
        throws IOException, IllegalStateException {
        synchronized (cometOperations) {
            //add it to the registered set
            for (CometEvent.CometOperation co : operations) {
                if (!cometOperations.contains(co)) {
                    cometOperations.add(co);
                    request.action(ActionCode.ACTION_COMET_REGISTER, translate(co));
                }
            }
        }
    }

    public void unregister(CometOperation... operations)
        throws IOException, IllegalStateException {
        synchronized (cometOperations) {
            //remove from the registered set
            for (CometEvent.CometOperation co : operations) {
                if (cometOperations.contains(co)) {
                    cometOperations.remove(co);
                    request.action(ActionCode.ACTION_COMET_UNREGISTER, translate(co));
                }
            }
        }
    }
    
    public CometConfiguration[] getConfiguration() {
        return (CometConfiguration[])cometConfigurations.toArray(new CometConfiguration[0]);
    }
    
    public CometOperation[] getRegisteredOps() {
        return (CometOperation[])cometOperations.toArray(new CometOperation[0]);
    }
    
    public String toString() {
        StringBuffer buf = new StringBuffer("CometEventImpl[");
        buf.append(super.toString());
        buf.append("] Event:");
        buf.append(getEventType());
        buf.append(" SubType:");
        buf.append(getEventSubType());
        return buf.toString();
    }

    protected void setWorkerThread() {
        threadCheck.set(threadCheckHolder);
    }
    
    protected void unsetWorkerThread() {
        threadCheck.set(null);
    }

    protected void checkWorkerThread() throws IllegalStateException {
        //throw exception if not on worker thread
        if ( !(threadCheck.get() == threadCheckHolder) ) 
            throw new IllegalStateException("The operation can only be performed when invoked by a Tomcat worker thread.");
    }
    
    protected PollerInterest translate(CometOperation op) {
        if ( op == CometEvent.CometOperation.OP_READ )
            return PollerInterest.READ;
        else if ( op == CometEvent.CometOperation.OP_WRITE )
            return PollerInterest.WRITE;
        else if ( op == CometEvent.CometOperation.OP_CALLBACK )
            return PollerInterest.CALLBACK;
        else 
            throw new IllegalArgumentException(op!=null?op.toString():"null");
    }
    
    //inner class used to keep track if the current thread is a worker thread.
    private static class WorkerThreadCheck extends ThreadLocal {
        
    }
    
    

}
