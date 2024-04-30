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
package org.apache.catalina.ha.session;

import org.apache.catalina.ha.ClusterMessage;

/**
 * The SessionMessage interface is used when a session has been created, modified, expired in a Tomcat cluster node.
 * <p>
 * The following events are currently available:
 * <ul>
 * <li>{@code public static final int EVT_SESSION_CREATED}</li>
 * <li>{@code public static final int EVT_SESSION_EXPIRED}</li>
 * <li>{@code public static final int EVT_SESSION_ACCESSED}</li>
 * <li>{@code public static final int EVT_GET_ALL_SESSIONS}</li>
 * <li>{@code public static final int EVT_SESSION_DELTA}</li>
 * <li>{@code public static final int EVT_ALL_SESSION_DATA}</li>
 * <li>{@code public static final int EVT_ALL_SESSION_TRANSFERCOMPLETE}</li>
 * <li>{@code public static final int EVT_CHANGE_SESSION_ID}</li>
 * <li>{@code public static final int EVT_ALL_SESSION_NOCONTEXTMANAGER}</li>
 * </ul>
 */
public interface SessionMessage extends ClusterMessage {

    /**
     * Event type used when a session has been created on a node
     */
    int EVT_SESSION_CREATED = 1;

    /**
     * Event type used when a session has expired
     */
    int EVT_SESSION_EXPIRED = 2;

    /**
     * Event type used when a session has been accessed (ie, last access time has been updated. This is used so that the
     * replicated sessions will not expire on the network
     */
    int EVT_SESSION_ACCESSED = 3;

    /**
     * Event type used when a server comes online for the first time. The first thing the newly started server wants to
     * do is to grab the all the sessions from one of the nodes and keep the same state in there
     */
    int EVT_GET_ALL_SESSIONS = 4;

    /**
     * Event type used when an attribute has been added to a session, the attribute will be sent to all the other nodes
     * in the cluster
     */
    int EVT_SESSION_DELTA = 13;

    /**
     * When a session state is transferred, this is the event.
     */
    int EVT_ALL_SESSION_DATA = 12;

    /**
     * When a session state is complete transferred, this is the event.
     */
    int EVT_ALL_SESSION_TRANSFERCOMPLETE = 14;

    /**
     * Event type used when a sessionID has been changed.
     */
    int EVT_CHANGE_SESSION_ID = 15;

    /**
     * Event type used when context manager doesn't exist. This is used when the manager which send a session state does
     * not exist.
     */
    int EVT_ALL_SESSION_NOCONTEXTMANAGER = 16;

    /**
     * @return the context name associated with this message
     */
    String getContextName();

    /**
     * Clear text event type name (for logging purpose only).
     *
     * @return the event type in a string representation, useful for debugging
     */
    String getEventTypeString();

    /**
     * returns the event type
     *
     * @return one of the event types EVT_XXXX
     */
    int getEventType();

    /**
     * @return the serialized data for the session
     */
    byte[] getSession();

    /**
     * @return the session ID for the session
     */
    String getSessionID();


}// SessionMessage
