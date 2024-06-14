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


import org.apache.catalina.ha.ClusterMessageBase;

/**
 * Session cluster message
 *
 * @author Peter Rossbach
 */
public class SessionMessageImpl extends ClusterMessageBase implements SessionMessage {

    private static final long serialVersionUID = 2L;


    /*
     * Private serializable variables to keep the messages state
     */
    private final int mEvtType;
    private final byte[] mSession;
    private final String mSessionID;

    private final String mContextName;
    private long serializationTimestamp;
    private boolean timestampSet = false;
    private String uniqueId;


    private SessionMessageImpl(String contextName, int eventtype, byte[] session, String sessionID) {
        mEvtType = eventtype;
        mSession = session;
        mSessionID = sessionID;
        mContextName = contextName;
        uniqueId = sessionID;
    }

    /**
     * Creates a session message. Depending on what event type you want this message to represent, you populate the
     * different parameters in the constructor<BR>
     * The following rules apply dependent on what event type argument you use:<BR>
     * <B>EVT_SESSION_CREATED</B><BR>
     * The parameters: session, sessionID must be set.<BR>
     * <B>EVT_SESSION_EXPIRED</B><BR>
     * The parameters: sessionID must be set.<BR>
     * <B>EVT_SESSION_ACCESSED</B><BR>
     * The parameters: sessionID must be set.<BR>
     * <B>EVT_GET_ALL_SESSIONS</B><BR>
     * get all sessions from from one of the nodes.<BR>
     * <B>EVT_SESSION_DELTA</B><BR>
     * Send attribute delta (add,update,remove attribute or principal, ...).<BR>
     * <B>EVT_ALL_SESSION_DATA</B><BR>
     * Send complete serializes session list<BR>
     * <B>EVT_ALL_SESSION_TRANSFERCOMPLETE</B><BR>
     * send that all session state information are transferred after GET_ALL_SESSION received from this sender.<BR>
     * <B>EVT_CHANGE_SESSION_ID</B><BR>
     * send original sessionID and new sessionID.<BR>
     * <B>EVT_ALL_SESSION_NOCONTEXTMANAGER</B><BR>
     * send that context manager does not exist after GET_ALL_SESSION received from this sender.<BR>
     *
     * @param contextName - the name of the context (application
     * @param eventtype   - one of the 8 event type defined in this class
     * @param session     - the serialized byte array of the session itself
     * @param sessionID   - the id that identifies this session
     * @param uniqueID    - the id that identifies this message
     */
    public SessionMessageImpl(String contextName, int eventtype, byte[] session, String sessionID, String uniqueID) {
        this(contextName, eventtype, session, sessionID);
        uniqueId = uniqueID;
    }

    @Override
    public int getEventType() {
        return mEvtType;
    }

    @Override
    public byte[] getSession() {
        return mSession;
    }

    @Override
    public String getSessionID() {
        return mSessionID;
    }

    /**
     * Set message send time but only the first setting works (one shot)
     *
     * @param time the timestamp
     */
    @Override
    public void setTimestamp(long time) {
        if (!timestampSet) {
            serializationTimestamp = time;
            timestampSet = true;
        }
    }

    @Override
    public long getTimestamp() {
        return serializationTimestamp;
    }

    @Override
    public String getEventTypeString() {
        switch (mEvtType) {
            case EVT_SESSION_CREATED:
                return "SESSION-MODIFIED";
            case EVT_SESSION_EXPIRED:
                return "SESSION-EXPIRED";
            case EVT_SESSION_ACCESSED:
                return "SESSION-ACCESSED";
            case EVT_GET_ALL_SESSIONS:
                return "SESSION-GET-ALL";
            case EVT_SESSION_DELTA:
                return "SESSION-DELTA";
            case EVT_ALL_SESSION_DATA:
                return "ALL-SESSION-DATA";
            case EVT_ALL_SESSION_TRANSFERCOMPLETE:
                return "SESSION-STATE-TRANSFERRED";
            case EVT_CHANGE_SESSION_ID:
                return "SESSION-ID-CHANGED";
            case EVT_ALL_SESSION_NOCONTEXTMANAGER:
                return "NO-CONTEXT-MANAGER";
            default:
                return "UNKNOWN-EVENT-TYPE";
        }
    }

    @Override
    public String getContextName() {
        return mContextName;
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public String toString() {
        return getEventTypeString() + "#" + getContextName() + "#" + getSessionID();
    }
}
