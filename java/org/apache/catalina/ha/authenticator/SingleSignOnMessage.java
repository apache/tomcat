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

package org.apache.catalina.ha.authenticator;

import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.ha.session.SerializablePrincipal;
import org.apache.catalina.tribes.Member;

/**
 * Contains the SingleSignOn data, read and written by the ClusterSingleSignOn
 * @author Fabien Carrion
 */

public class SingleSignOnMessage implements ClusterMessage {

    private static final long serialVersionUID = 1L;

    public static final int ADD_SESSION = 1;
    public static final int DEREGISTER_SESSION = 2;
    public static final int LOGOUT_SESSION = 3;
    public static final int REGISTER_SESSION = 4;
    public static final int UPDATE_SESSION = 5;
    public static final int REMOVE_SESSION = 6;

    private int action = -1;
    private String ssoId = null;
    private String ctxname = null;
    private String sessionId = null;
    private String authType = null;
    private String password = null;
    private String username = null;
    private SerializablePrincipal principal = null;

    private Member address = null;
    private long timestamp = 0;
    private String uniqueId = null;

    public SingleSignOnMessage(Member source,
                               String ssoId,
                               String sessionId) {
        this.address = source;
        this.ssoId = ssoId;
        this.sessionId = sessionId;
    }
    
    /**
     * Get the address that this message originated from.  This would be set
     * if the message was being relayed from a host other than the one
     * that originally sent it.
     */
    @Override
    public Member getAddress() {
        return address;
    }

    /**
     * Called by the cluster before sending it to the other
     * nodes.
     *
     * @param member Member
     */
    @Override
    public void setAddress(Member member) {
        this.address = member;
    }

    /**
     * Timestamp message.
     *
     * @return long
     */
    @Override
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Called by the cluster before sending out
     * the message.
     *
     * @param timestamp The timestamp
     */
    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Each message must have a unique ID, in case of using async replication,
     * and a smart queue, this id is used to replace messages not yet sent.
     *
     * @return String
     */
    @Override
    public String getUniqueId() {
        if (this.uniqueId != null)
            return this.uniqueId;
        StringBuilder result = new StringBuilder(getSsoId());
        result.append("#-#");
        result.append(System.currentTimeMillis());
        return result.toString();
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public int getAction() {
        return action;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public String getSsoId() {
        return ssoId;
    }

    public void setSsoId(String ssoId) {
        this.ssoId = ssoId;
    }

    public String getContextName() {
        return ctxname;
    }

    public void setContextName(String ctxname) {
        this.ctxname = ctxname;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public SerializablePrincipal getPrincipal() {
        return principal;
    }

    public void setPrincipal(SerializablePrincipal principal) {
        this.principal = principal;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Return a String rendering of this object.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("SingleSignOnMessage[action=");
        sb.append(getAction()).append(", ssoId=").append(getSsoId());
        sb.append(", sessionId=").append(getSessionId()).append(", username=");
        sb.append(getUsername()).append("]");
        return (sb.toString());

    }

}
