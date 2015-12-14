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
package org.apache.tomcat.util.net;

/**
 * Defines events that occur per socket that require further processing by the
 * container. Usually these events are triggered by the socket implementation
 * but they may be triggered by the container.
 */
public enum SocketEvent {

    /**
     * Data is available to be read.
     */
    OPEN_READ,

    /**
     * The socket is ready to be written to.
     */
    OPEN_WRITE,

    /**
     * The associated Connector/Endpoint is stopping and the connection/socket
     * needs to be closed cleanly.
     */
    STOP,

    /**
     * A timeout has occurred and the connection needs to be closed cleanly.
     * Currently this is only used by the Servlet 3.0 async processing.
     */
    TIMEOUT,

    /**
     * The client has disconnected.
     */
    DISCONNECT,

    /**
     * An error has occurred that does not had a dedicated event type. Currently
     * this is only used by NIO2 to signal the failure of a completion handler.
     */
    ERROR,

    /**
     * Used internally by the container to signal that an I/O occurred during an
     * asynchronous read.
     *
     * TODO: Given how this is used, it is possible to refactor the processing
     *       so this enum value is not required?
     */
    ASYNC_WRITE_ERROR,

    /**
     * Initiated by the container when an I/O error is detected on a
     * non-container thread.
     *
     * TODO: Can this be combined with / replaced by ERROR?
     */
    CLOSE_NOW
}
