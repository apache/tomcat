/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.websocket.ClientContainer;
import javax.websocket.CloseReason;
import javax.websocket.Encoder;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

public class WsSession implements Session {

    @Override
    public ClientContainer getContainer() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setEncoders(List<Encoder> encoders) {
        // TODO Auto-generated method stub

    }

    @Override
    public void addMessageHandler(MessageHandler listener) {
        // TODO Auto-generated method stub

    }

    @Override
    public Set<MessageHandler> getMessageHandlers() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void removeMessageHandler(MessageHandler listener) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getProtocolVersion() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getNegotiatedSubprotocol() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<String> getNegotiatedExtensions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isSecure() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public long getInactiveTime() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isActive() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public long getTimeout() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setTimeout(long seconds) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setMaximumMessageSize(long length) {
        // TODO Auto-generated method stub

    }

    @Override
    public long getMaximumMessageSize() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public RemoteEndpoint getRemote() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void close(CloseReason closeStatus) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public URI getRequestURI() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, String[]> getRequestParameterMap() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getQueryString() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, String> getPathParameters() {
        // TODO Auto-generated method stub
        return null;
    }

}
