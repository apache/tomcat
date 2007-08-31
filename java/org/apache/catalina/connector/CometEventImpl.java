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
import java.util.Arrays;
import java.util.HashSet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.CometEvent;
import org.apache.catalina.util.StringManager;
import org.apache.coyote.ActionCode;
import org.apache.tomcat.util.net.PollerInterest;
import org.apache.tomcat.util.MutableBoolean;

public class CometEventImpl implements CometEvent {

    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


    public CometEventImpl(Request request, Response response) {
        this.request = request;
        this.response = response;
        //default behavior is to only listen for read events
        register(CometOperation.OP_READ);
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
    protected int cometOperations = 0;
    
    /**
     * Blocking or not blocking
     */
    protected boolean blocking = true;

    // --------------------------------------------------------- Public Methods

    /**
     * Clear the event.
     */
    public void clear() {
        request = null;
        response = null;
        blocking = true;
        cometOperations = 0;
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

    public void setTimeout(int timeout) throws ServletException,UnsupportedOperationException {
        //this check should get removed as soon as connection timeout is implemented everywhere.
        if (request.getAttribute("org.apache.tomcat.comet.timeout.support") == Boolean.TRUE) {
            request.action(ActionCode.ACTION_COMET_TIMEOUT,new Integer(timeout));
        } else {
            throw new UnsupportedOperationException();
        }
    }
    
    public boolean isReadable() {
        return request.isAvailable() || request.isReadable();
    }    
    public boolean isWriteable() {
        return response.isWriteable();
    }
    
    public boolean hasOp(int op) {
        return (cometOperations & op ) == op;
    }
    
    public void configureBlocking(boolean blocking) throws IllegalStateException {
        if ( getEventType() != EventType.BEGIN ) throw new IllegalStateException("Can only be configured during the BEGIN event.");
        MutableBoolean bool = new MutableBoolean(blocking);
        request.action(ActionCode.ACTION_COMET_CONFIGURE_BLOCKING,bool);
        this.blocking = bool.get();
    }

    public void register(int operations) throws IllegalStateException {
        //add it to the registered set
        cometOperations = cometOperations | operations;
        request.action(ActionCode.ACTION_COMET_REGISTER, translate(cometOperations));
    }

    public void unregister(int operations) throws IllegalStateException {
        //remove from the registered set
        cometOperations = cometOperations & (~operations);
        request.action(ActionCode.ACTION_COMET_REGISTER, translate(cometOperations));
    }
    
    public boolean isBlocking() {
        return blocking;
    }
    
    public int getRegisteredOps() {
        return cometOperations;
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

    protected Integer translate(int op) {
        return new Integer(op);
    }
    

}
