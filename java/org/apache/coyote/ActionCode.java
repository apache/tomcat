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
 * ActionCodes represent callbacks from the servlet container to the coyote connector. Actions are implemented by
 * ProtocolHandler, using the ActionHook interface.
 *
 * @see ProtocolHandler
 * @see ActionHook
 *
 * @author Remy Maucherat
 */
public enum ActionCode {

    /**
     * Acknowledge request, most often used for HTTP expectations.
     */
    ACK,

    /**
     * Regular close.
     */
    CLOSE,

    /**
     * Response commit, which means any initial bytes part of the response are going to be sent.
     */
    COMMIT,

    /**
     * A serious error occurred from which it is not possible to recover safely. Further attempts to write to the
     * response should be ignored and the connection needs to be closed as soon as possible. This can also be used to
     * forcibly close a connection if an error occurs after the response has been committed.
     */
    CLOSE_NOW,

    /**
     * A flush() operation originated by the client ( i.e. a flush() on the servlet output stream or writer, called by a
     * servlet ). Argument is the Response.
     */
    CLIENT_FLUSH,

    /**
     * Has the processor been placed into the error state? Note that the response may not have an appropriate error code
     * set.
     */
    IS_ERROR,

    /**
     * The processor may have been placed into an error state and some error states do not permit any further I/O. Is
     * I/O currently allowed?
     */
    IS_IO_ALLOWED,

    /**
     * Hook called if swallowing request input should be disabled. Example: Cancel a large file upload.
     */
    DISABLE_SWALLOW_INPUT,

    /**
     * Callback for lazy evaluation - extract the remote host name and address.
     */
    REQ_HOST_ATTRIBUTE,

    /**
     * Callback for lazy evaluation - extract the remote host address.
     */
    REQ_HOST_ADDR_ATTRIBUTE,

    /**
     * Callback for lazy evaluation - extract the connection peer address.
     */
    REQ_PEER_ADDR_ATTRIBUTE,

    /**
     * Callback for lazy evaluation - extract the SSL-related attributes including the client certificate if present.
     */
    REQ_SSL_ATTRIBUTE,

    /**
     * Force a TLS re-handshake and make the resulting client certificate (if any) available as a request attribute.
     */
    REQ_SSL_CERTIFICATE,

    /**
     * Callback for lazy evaluation - socket remote port.
     */
    REQ_REMOTEPORT_ATTRIBUTE,

    /**
     * Callback for lazy evaluation - socket local port.
     */
    REQ_LOCALPORT_ATTRIBUTE,

    /**
     * Callback for lazy evaluation - local address.
     */
    REQ_LOCAL_ADDR_ATTRIBUTE,

    /**
     * Callback for lazy evaluation - local address.
     */
    REQ_LOCAL_NAME_ATTRIBUTE,

    /**
     * Callback for setting FORM auth body replay
     */
    REQ_SET_BODY_REPLAY,

    /**
     * Callback for getting the amount of available bytes.
     */
    AVAILABLE,

    /**
     * Callback for an async request.
     */
    ASYNC_START,

    /**
     * Callback for an async call to {@link jakarta.servlet.AsyncContext#dispatch()}.
     */
    ASYNC_DISPATCH,

    /**
     * Callback to indicate the the actual dispatch has started and that the async state needs change.
     */
    ASYNC_DISPATCHED,

    /**
     * Callback for an async call to {@link jakarta.servlet.AsyncContext#start(Runnable)}.
     */
    ASYNC_RUN,

    /**
     * Callback for an async call to {@link jakarta.servlet.AsyncContext#complete()}.
     */
    ASYNC_COMPLETE,

    /**
     * Callback to trigger the processing of an async timeout.
     */
    ASYNC_TIMEOUT,

    /**
     * Callback to trigger the error processing.
     */
    ASYNC_ERROR,

    /**
     * Callback for an async call to {@link jakarta.servlet.AsyncContext#setTimeout(long)}
     */
    ASYNC_SETTIMEOUT,

    /**
     * Callback to determine if async processing is in progress.
     */
    ASYNC_IS_ASYNC,

    /**
     * Callback to determine if async dispatch is in progress.
     */
    ASYNC_IS_STARTED,

    /**
     * Call back to determine if async complete is in progress.
     */
    ASYNC_IS_COMPLETING,

    /**
     * Callback to determine if async dispatch is in progress.
     */
    ASYNC_IS_DISPATCHING,

    /**
     * Callback to determine if async is timing out.
     */
    ASYNC_IS_TIMINGOUT,

    /**
     * Callback to determine if async is in error.
     */
    ASYNC_IS_ERROR,

    /**
     * Callback to trigger post processing. Typically only used during error handling to trigger essential processing
     * that otherwise would be skipped.
     */
    ASYNC_POST_PROCESS,

    /**
     * Callback to trigger the HTTP upgrade process.
     */
    UPGRADE,

    /**
     * Indicator that Servlet is interested in being notified when data is available to be read.
     */
    NB_READ_INTEREST,

    /**
     * Used with non-blocking writes to determine if a write is currently allowed (sets passed parameter to
     * <code>true</code>) or not (sets passed parameter to <code>false</code>). If a write is not allowed then callback
     * will be triggered at some future point when write becomes possible again.
     */
    NB_WRITE_INTEREST,

    /**
     * Indicates if the request body has been fully read.
     */
    REQUEST_BODY_FULLY_READ,

    /**
     * Indicates that the container needs to trigger a call to onDataAvailable() for the registered non-blocking read
     * listener.
     */
    DISPATCH_READ,

    /**
     * Indicates that the container needs to trigger a call to onWritePossible() for the registered non-blocking write
     * listener.
     */
    DISPATCH_WRITE,

    /**
     * Execute any non-blocking dispatches that have been registered via {@link #DISPATCH_READ} or
     * {@link #DISPATCH_WRITE}. Typically required when the non-blocking listeners are configured on a thread where the
     * processing wasn't triggered by a read or write event on the socket.
     */
    DISPATCH_EXECUTE,

    /**
     * Are the request trailer fields ready to be read? Note that this returns true if it is known that request trailer
     * fields are not supported so an empty collection of trailers can then be read.
     */
    IS_TRAILER_FIELDS_READY,

    /**
     * Are HTTP trailer fields supported for the current response? Note that once an HTTP/1.1 response has been
     * committed, it will no longer support trailer fields.
     */
    IS_TRAILER_FIELDS_SUPPORTED,

    /**
     * Obtain the request identifier for this request as defined by the protocol in use. Note that some protocols do not
     * define such an identifier. E.g. this will be Stream ID for HTTP/2.
     */
    PROTOCOL_REQUEST_ID,

    /**
     * Obtain the servlet connection instance for the network connection supporting the current request.
     */
    SERVLET_CONNECTION
}
