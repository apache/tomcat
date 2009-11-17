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
 
package org.apache.coyote;


/**
 * Enumerated class containing the adapter event codes.
 * Actions represent callbacks from the servlet container to the coyote
 * connector.
 *
 * Actions are implemented by ProtocolHandler, using the ActionHook interface.
 *
 * @see ProtocolHandler
 * @see ActionHook
 * @author Remy Maucherat
 */
public final class ActionCode {


    // -------------------------------------------------------------- Constants


    public static final ActionCode ACTION_ACK = new ActionCode(1);


    public static final ActionCode ACTION_CLOSE = new ActionCode(2);


    public static final ActionCode ACTION_COMMIT = new ActionCode(3);


    /**
     * A flush() operation originated by the client ( i.e. a flush() on
     * the servlet output stream or writer, called by a servlet ).
     * 
     * Argument is the Response.
     */
    public static final ActionCode ACTION_CLIENT_FLUSH = new ActionCode(4);

    
    public static final ActionCode ACTION_CUSTOM = new ActionCode(5);


    public static final ActionCode ACTION_RESET = new ActionCode(6);


    public static final ActionCode ACTION_START = new ActionCode(7);


    public static final ActionCode ACTION_STOP = new ActionCode(8);


    public static final ActionCode ACTION_WEBAPP = new ActionCode(9);

    /** Hook called after request, but before recycling. Can be used
        for logging, to update counters, custom cleanup - the request
        is still visible
    */
    public static final ActionCode ACTION_POST_REQUEST = new ActionCode(10);

    /**
     * Callback for lazy evaluation - extract the remote host address.
     */
    public static final ActionCode ACTION_REQ_HOST_ATTRIBUTE = 
        new ActionCode(11);


    /**
     * Callback for lazy evaluation - extract the remote host infos (address, name, port) and local address.
     */
    public static final ActionCode ACTION_REQ_HOST_ADDR_ATTRIBUTE = new ActionCode(12);

    /**
     * Callback for lazy evaluation - extract the SSL-related attributes.
     */
    public static final ActionCode ACTION_REQ_SSL_ATTRIBUTE = new ActionCode(13);


    /** Chain for request creation. Called each time a new request is created
        ( requests are recycled ).
     */
    public static final ActionCode ACTION_NEW_REQUEST = new ActionCode(14);


    /**
     * Callback for lazy evaluation - extract the SSL-certificate 
     * (including forcing a re-handshake if necessary)
     */
    public static final ActionCode ACTION_REQ_SSL_CERTIFICATE = new ActionCode(15);
    
    
    /**
     * Callback for lazy evaluation - socket remote port.
     **/
    public static final ActionCode ACTION_REQ_REMOTEPORT_ATTRIBUTE = new ActionCode(16);

    
    /**
     * Callback for lazy evaluation - socket local port.
     **/
    public static final ActionCode ACTION_REQ_LOCALPORT_ATTRIBUTE = new ActionCode(17);
    
    
    /**
     * Callback for lazy evaluation - local address.
     **/
    public static final ActionCode ACTION_REQ_LOCAL_ADDR_ATTRIBUTE = new ActionCode(18);
    
    
    /**
     * Callback for lazy evaluation - local address.
     **/
    public static final ActionCode ACTION_REQ_LOCAL_NAME_ATTRIBUTE = new ActionCode(19);


    /**
     * Callback for setting FORM auth body replay
     */
    public static final ActionCode ACTION_REQ_SET_BODY_REPLAY = new ActionCode(20);


    /**
     * Callback for begin Comet processing
     */
    public static final ActionCode ACTION_COMET_BEGIN = new ActionCode(21);


    /**
     * Callback for end Comet processing
     */
    public static final ActionCode ACTION_COMET_END = new ActionCode(22);


    /**
     * Callback for getting the amount of available bytes
     */
    public static final ActionCode ACTION_AVAILABLE = new ActionCode(23);

    /**
     * Callback for an asynchronous close of the Comet event
     */
    public static final ActionCode ACTION_COMET_CLOSE = new ActionCode(24);

    /**
     * Callback for setting the timeout asynchronously
     */
    public static final ActionCode ACTION_COMET_SETTIMEOUT = new ActionCode(25);
    
    /**
     * Callback for an async request
     */
    public static final ActionCode ACTION_ASYNC_START = new ActionCode(26);
    
    /**
     * Callback for an async call to {@link javax.servlet.AsyncContext#complete()}
     */
    public static final ActionCode ACTION_ASYNC_COMPLETE = new ActionCode(27);
    /**
     * Callback for an async call to {@link javax.servlet.ServletRequest#setAsyncTimeout(long)}
     */
    public static final ActionCode ACTION_ASYNC_SETTIMEOUT = new ActionCode(28);
    
    /**
     * Callback for an async call to {@link javax.servlet.AsyncContext#dispatch()}
     */
    public static final ActionCode ACTION_ASYNC_DISPATCH = new ActionCode(29);
    
    
    // ----------------------------------------------------------- Constructors
    int code;

    /**
     * Private constructor.
     */
    private ActionCode(int code) {
        this.code=code;
    }

    /** Action id, usable in switches and table indexes
     */
    public int getCode() {
        return code;
    }


}
