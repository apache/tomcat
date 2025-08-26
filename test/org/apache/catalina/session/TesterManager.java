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
package org.apache.catalina.session;

import java.beans.PropertyChangeListener;
import java.io.IOException;

import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.SessionIdGenerator;

public class TesterManager implements Manager {

    private Context context;

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void setContext(Context context) {
        this.context = context;
    }

    @Override
    public SessionIdGenerator getSessionIdGenerator() {
        return null;
    }

    @Override
    public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
        // NO-OP
    }

    @Override
    public long getSessionCounter() {
        return 0;
    }

    @Override
    public int getMaxActive() {
        return 0;
    }

    @Override
    public void setMaxActive(int maxActive) {
        // NO-OP
    }

    @Override
    public int getActiveSessions() {
        return 0;
    }

    @Override
    public long getExpiredSessions() {
        return 0;
    }

    @Override
    public void setExpiredSessions(long expiredSessions) {
        // NO-OP
    }

    @Override
    public int getRejectedSessions() {
        return 0;
    }

    @Override
    public int getSessionMaxAliveTime() {
        return 0;
    }

    @Override
    public void setSessionMaxAliveTime(int sessionMaxAliveTime) {
        // NO-OP
    }

    @Override
    public int getSessionAverageAliveTime() {
        return 0;
    }

    @Override
    public int getSessionCreateRate() {
        return 0;
    }

    @Override
    public int getSessionExpireRate() {
        return 0;
    }

    @Override
    public void add(Session session) {
        // NO-OP
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        // NO-OP
    }

    @Override
    public void changeSessionId(Session session, String newId) {
        // NO-OP
    }

    @Override
    public Session createEmptySession() {
        return null;
    }

    @Override
    public Session createSession(String sessionId) {
        return null;
    }

    @Override
    public Session findSession(String id) throws IOException {
        return null;
    }

    @Override
    public Session[] findSessions() {
        return null;
    }

    @Override
    public void load() throws ClassNotFoundException, IOException {
        // NO-OP
    }

    @Override
    public void remove(Session session) {
        // NO-OP
    }

    @Override
    public void remove(Session session, boolean update) {
        // NO-OP
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        // NO-OP
    }

    @Override
    public void unload() throws IOException {
        // NO-OP
    }

    @Override
    public void backgroundProcess() {
        // NO-OP
    }

    @Override
    public boolean willAttributeDistribute(String name, Object value) {
        return false;
    }

    @Override
    public void setNotifyBindingListenerOnUnchangedValue(boolean notifyBindingListenerOnUnchangedValue) {
        // NO-OP
    }

    @Override
    public void setNotifyAttributeListenerOnUnchangedValue(boolean notifyAttributeListenerOnUnchangedValue) {
        // NO-OP
    }

    @Override
    public void setSessionActivityCheck(boolean sessionActivityCheck) {
        // NO-OP
    }

    @Override
    public void setSessionLastAccessAtStart(boolean sessionLastAccessAtStart) {
        // NO-OP
    }

    @Override
    public void setSessionCounter(long sessionCounter) {
        // NO-OP
    }
}
