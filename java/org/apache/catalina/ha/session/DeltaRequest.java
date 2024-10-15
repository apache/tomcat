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

import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.Principal;
import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.catalina.SessionListener;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * This class is used to track the series of actions that happens when a request is executed. These actions will then
 * translate into invocations of methods on the actual session.
 * <p>
 * This class is NOT thread safe. One DeltaRequest per session.
 */
public class DeltaRequest implements Externalizable {

    public static final Log log = LogFactory.getLog(DeltaRequest.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(DeltaRequest.class);

    public static final int TYPE_ATTRIBUTE = 0;
    public static final int TYPE_PRINCIPAL = 1;
    public static final int TYPE_ISNEW = 2;
    public static final int TYPE_MAXINTERVAL = 3;
    public static final int TYPE_AUTHTYPE = 4;
    public static final int TYPE_LISTENER = 5;
    public static final int TYPE_NOTE = 6;

    public static final int ACTION_SET = 0;
    public static final int ACTION_REMOVE = 1;

    public static final String NAME_PRINCIPAL = "__SET__PRINCIPAL__";
    public static final String NAME_MAXINTERVAL = "__SET__MAXINTERVAL__";
    public static final String NAME_ISNEW = "__SET__ISNEW__";
    public static final String NAME_AUTHTYPE = "__SET__AUTHTYPE__";
    public static final String NAME_LISTENER = "__SET__LISTENER__";

    private String sessionId;
    private final Deque<AttributeInfo> actions = new ArrayDeque<>();
    private final Deque<AttributeInfo> actionPool = new ArrayDeque<>();

    private boolean recordAllActions = false;

    public DeltaRequest() {

    }

    public DeltaRequest(String sessionId, boolean recordAllActions) {
        this.recordAllActions = recordAllActions;
        if (sessionId != null) {
            setSessionId(sessionId);
        }
    }


    public void setAttribute(String name, Object value) {
        int action = (value == null) ? ACTION_REMOVE : ACTION_SET;
        addAction(TYPE_ATTRIBUTE, action, name, value);
    }

    public void removeAttribute(String name) {
        addAction(TYPE_ATTRIBUTE, ACTION_REMOVE, name, null);
    }

    public void setNote(String name, Object value) {
        int action = (value == null) ? ACTION_REMOVE : ACTION_SET;
        addAction(TYPE_NOTE, action, name, value);
    }

    public void removeNote(String name) {
        addAction(TYPE_NOTE, ACTION_REMOVE, name, null);
    }

    public void setMaxInactiveInterval(int interval) {
        addAction(TYPE_MAXINTERVAL, ACTION_SET, NAME_MAXINTERVAL, Integer.valueOf(interval));
    }

    /**
     * Only support principals from type {@link GenericPrincipal GenericPrincipal}
     *
     * @param p Session principal
     *
     * @see GenericPrincipal
     */
    public void setPrincipal(Principal p) {
        int action = (p == null) ? ACTION_REMOVE : ACTION_SET;
        GenericPrincipal gp = null;
        if (p != null) {
            if (p instanceof GenericPrincipal) {
                gp = (GenericPrincipal) p;
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("deltaRequest.showPrincipal", p.getName(), getSessionId()));
                }
            } else {
                log.error(sm.getString("deltaRequest.wrongPrincipalClass", p.getClass().getName()));
            }
        }
        addAction(TYPE_PRINCIPAL, action, NAME_PRINCIPAL, gp);
    }

    public void setNew(boolean n) {
        int action = ACTION_SET;
        addAction(TYPE_ISNEW, action, NAME_ISNEW, Boolean.valueOf(n));
    }

    public void setAuthType(String authType) {
        int action = (authType == null) ? ACTION_REMOVE : ACTION_SET;
        addAction(TYPE_AUTHTYPE, action, NAME_AUTHTYPE, authType);
    }

    public void addSessionListener(SessionListener listener) {
        addAction(TYPE_LISTENER, ACTION_SET, NAME_LISTENER, listener);
    }

    public void removeSessionListener(SessionListener listener) {
        addAction(TYPE_LISTENER, ACTION_REMOVE, NAME_LISTENER, listener);
    }

    protected void addAction(int type, int action, String name, Object value) {
        AttributeInfo info = null;
        if (this.actionPool.size() > 0) {
            try {
                info = actionPool.removeFirst();
            } catch (Exception x) {
                log.error(sm.getString("deltaRequest.removeUnable"), x);
                info = new AttributeInfo(type, action, name, value);
            }
            info.init(type, action, name, value);
        } else {
            info = new AttributeInfo(type, action, name, value);
        }
        // if we have already done something to this attribute, make sure
        // we don't send multiple actions across the wire
        if (!recordAllActions) {
            try {
                actions.remove(info);
            } catch (java.util.NoSuchElementException x) {
                // do nothing, we wanted to remove it anyway
            }
        }
        // add the action
        actions.addLast(info);
    }

    public void execute(DeltaSession session, boolean notifyListeners) {
        if (!this.sessionId.equals(session.getId())) {
            throw new IllegalArgumentException(sm.getString("deltaRequest.ssid.mismatch"));
        }
        session.access();
        for (AttributeInfo info : actions) {
            switch (info.getType()) {
                case TYPE_ATTRIBUTE:
                    if (info.getAction() == ACTION_SET) {
                        if (log.isTraceEnabled()) {
                            log.trace("Session.setAttribute('" + info.getName() + "', '" + info.getValue() + "')");
                        }
                        session.setAttribute(info.getName(), info.getValue(), notifyListeners, false);
                    } else {
                        if (log.isTraceEnabled()) {
                            log.trace("Session.removeAttribute('" + info.getName() + "')");
                        }
                        session.removeAttribute(info.getName(), notifyListeners, false);
                    }

                    break;
                case TYPE_ISNEW:
                    if (log.isTraceEnabled()) {
                        log.trace("Session.setNew('" + info.getValue() + "')");
                    }
                    session.setNew(((Boolean) info.getValue()).booleanValue(), false);
                    break;
                case TYPE_MAXINTERVAL:
                    if (log.isTraceEnabled()) {
                        log.trace("Session.setMaxInactiveInterval('" + info.getValue() + "')");
                    }
                    session.setMaxInactiveInterval(((Integer) info.getValue()).intValue(), false);
                    break;
                case TYPE_PRINCIPAL:
                    Principal p = null;
                    if (info.getAction() == ACTION_SET) {
                        p = (Principal) info.getValue();
                    }
                    session.setPrincipal(p, false);
                    break;
                case TYPE_AUTHTYPE:
                    String authType = null;
                    if (info.getAction() == ACTION_SET) {
                        authType = (String) info.getValue();
                    }
                    session.setAuthType(authType, false);
                    break;
                case TYPE_LISTENER:
                    SessionListener listener = (SessionListener) info.getValue();
                    if (info.getAction() == ACTION_SET) {
                        session.addSessionListener(listener, false);
                    } else {
                        session.removeSessionListener(listener, false);
                    }
                    break;
                case TYPE_NOTE:
                    if (info.getAction() == ACTION_SET) {
                        if (log.isTraceEnabled()) {
                            log.trace("Session.setNote('" + info.getName() + "', '" + info.getValue() + "')");
                        }
                        session.setNote(info.getName(), info.getValue(), false);
                    } else {
                        if (log.isTraceEnabled()) {
                            log.trace("Session.removeNote('" + info.getName() + "')");
                        }
                        session.removeNote(info.getName(), false);
                    }

                    break;
                default:
                    log.warn(sm.getString("deltaRequest.invalidAttributeInfoType", info));
            }// switch
        } // for
        session.endAccess();
        reset();
    }

    public void reset() {
        while (actions.size() > 0) {
            try {
                AttributeInfo info = actions.removeFirst();
                info.recycle();
                actionPool.addLast(info);
            } catch (Exception x) {
                log.error(sm.getString("deltaRequest.removeUnable"), x);
            }
        }
        actions.clear();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
        if (sessionId == null) {
            Exception e = new Exception(sm.getString("deltaRequest.ssid.null"));
            log.error(sm.getString("deltaRequest.ssid.null"), e.fillInStackTrace());
        }
    }

    public int getSize() {
        return actions.size();
    }

    public void clear() {
        actions.clear();
        actionPool.clear();
    }

    @Override
    public void readExternal(java.io.ObjectInput in) throws IOException, ClassNotFoundException {
        // sessionId - String
        // recordAll - boolean
        // size - int
        // AttributeInfo - in an array
        reset();
        sessionId = in.readUTF();
        recordAllActions = in.readBoolean();
        int cnt = in.readInt();
        for (int i = 0; i < cnt; i++) {
            AttributeInfo info = null;
            if (this.actionPool.size() > 0) {
                try {
                    info = actionPool.removeFirst();
                } catch (Exception x) {
                    log.error(sm.getString("deltaRequest.removeUnable"), x);
                    info = new AttributeInfo();
                }
            } else {
                info = new AttributeInfo();
            }
            info.readExternal(in);
            actions.addLast(info);
        } // for
    }


    @Override
    public void writeExternal(java.io.ObjectOutput out) throws IOException {
        // sessionId - String
        // recordAll - boolean
        // size - int
        // AttributeInfo - in an array
        out.writeUTF(getSessionId());
        out.writeBoolean(recordAllActions);
        out.writeInt(getSize());
        for (AttributeInfo info : actions) {
            info.writeExternal(out);
        }
    }

    /**
     * serialize DeltaRequest
     *
     * @see DeltaRequest#writeExternal(java.io.ObjectOutput)
     *
     * @return serialized delta request
     *
     * @throws IOException IO error serializing
     */
    protected byte[] serialize() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        writeExternal(oos);
        oos.flush();
        oos.close();
        return bos.toByteArray();
    }

    private static class AttributeInfo implements Externalizable {
        private String name = null;
        private Object value = null;
        private int action;
        private int type;

        AttributeInfo() {
            this(-1, -1, null, null);
        }

        AttributeInfo(int type, int action, String name, Object value) {
            super();
            init(type, action, name, value);
        }

        public void init(int type, int action, String name, Object value) {
            this.name = name;
            this.value = value;
            this.action = action;
            this.type = type;
        }

        public int getType() {
            return type;
        }

        public int getAction() {
            return action;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        public String getName() {
            return name;
        }

        public void recycle() {
            name = null;
            value = null;
            type = -1;
            action = -1;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof AttributeInfo)) {
                return false;
            }
            AttributeInfo other = (AttributeInfo) o;
            return other.getName().equals(this.getName());
        }

        @Override
        public void readExternal(java.io.ObjectInput in) throws IOException, ClassNotFoundException {
            // type - int
            // action - int
            // name - String
            // hasvalue - boolean
            // value - object
            type = in.readInt();
            action = in.readInt();
            name = in.readUTF();
            boolean hasValue = in.readBoolean();
            if (hasValue) {
                value = in.readObject();
            }
        }

        @Override
        public void writeExternal(java.io.ObjectOutput out) throws IOException {
            // type - int
            // action - int
            // name - String
            // hasvalue - boolean
            // value - object
            out.writeInt(getType());
            out.writeInt(getAction());
            out.writeUTF(getName());
            out.writeBoolean(getValue() != null);
            if (getValue() != null) {
                out.writeObject(getValue());
            }
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder("AttributeInfo[type=");
            buf.append(getType()).append(", action=").append(getAction());
            buf.append(", name=").append(getName()).append(", value=").append(getValue());
            buf.append(", addr=").append(super.toString()).append(']');
            return buf.toString();
        }

    }

}
