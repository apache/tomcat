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
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.websocket.ClientContainer;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Endpoint;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

public class WsSession implements Session {

    private MessageHandler textMessageHandler = null;
    private MessageHandler binaryMessageHandler = null;
    private MessageHandler.Basic<PongMessage> pongMessageHandler = new DefaultPingMessageHandler(
            this);
    private final Endpoint localEndpoint;


    public WsSession(Endpoint localEndpoint) {
        this.localEndpoint = localEndpoint;
    }


    @Override
    public ClientContainer getContainer() {
        // TODO Auto-generated method stub
        return null;
    }


    @SuppressWarnings("unchecked")
    @Override
    public void addMessageHandler(MessageHandler listener) {
        Type[] types = listener.getClass().getGenericInterfaces();
        if (types.length != 1) {
            // TODO i18n
            throw new IllegalArgumentException();
        }
        if (types[0].getClass().equals(String.class)) {
            if (textMessageHandler != null) {
                // TODO i18n
                throw new IllegalStateException();
            }
            textMessageHandler = listener;
        } else if (types[0].getClass().equals(ByteBuffer.class)) {
            if (binaryMessageHandler != null) {
                // TODO i18n
                throw new IllegalStateException();
            }
            binaryMessageHandler = listener;
        } else if (types[0].getClass().equals(PongMessage.class)) {
            if (pongMessageHandler != null) {
                // TODO i18n
                throw new IllegalStateException();
            }
            if (listener instanceof MessageHandler.Basic<?>) {
                pongMessageHandler = (MessageHandler.Basic<PongMessage>) listener;
            } else {
                // TODO i18n
                throw new IllegalArgumentException();
            }
        } else {
            // TODO i18n
            throw new IllegalArgumentException();
        }
    }


    @Override
    public Set<MessageHandler> getMessageHandlers() {
        Set<MessageHandler> result = new HashSet<>();
        if (binaryMessageHandler != null) {
            result.add(binaryMessageHandler);
        }
        if (textMessageHandler != null) {
            result.add(textMessageHandler);
        }
        if (pongMessageHandler != null) {
            result.add(pongMessageHandler);
        }
        return result;
    }


    @Override
    public void removeMessageHandler(MessageHandler listener) {
        if (listener == null) {
            return;
        }
        if (listener.equals(textMessageHandler)) {
            textMessageHandler = null;
        } else if (listener.equals(binaryMessageHandler)) {
            binaryMessageHandler = null;
        } else if (listener.equals(pongMessageHandler)) {
            pongMessageHandler = null;
        }
        // TODO Ignore? ISE?
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
    public boolean isOpen() {
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
        close(new CloseReason(CloseCodes.GOING_AWAY, ""));
    }


    @Override
    public void close(CloseReason closeStatus) throws IOException {
        // TODO Send the close message to the remote endpoint
        localEndpoint.onClose(closeStatus);
    }


    @Override
    public URI getRequestURI() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Map<String,String[]> getRequestParameterMap() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public String getQueryString() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Map<String,String> getPathParameters() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Map<String,Object> getUserProperties() {
        // TODO Auto-generated method stub
        return null;
    }


    public MessageHandler getTextMessageHandler() {
        return textMessageHandler;
    }


    public MessageHandler getBinaryMessageHandler() {
        return binaryMessageHandler;
    }


    public MessageHandler.Basic<PongMessage> getPongMessageHandler() {
        return pongMessageHandler;
    }

    private static class DefaultPingMessageHandler implements
            MessageHandler.Basic<PongMessage> {

        private final WsSession wsSession;


        private DefaultPingMessageHandler(WsSession wsSession) {
            this.wsSession = wsSession;
        }


        @Override
        public void onMessage(PongMessage message) {
            RemoteEndpoint remoteEndpoint = wsSession.getRemote();
            if (remoteEndpoint != null) {
                remoteEndpoint.sendPong(message.getApplicationData());
            }
        }
    }
}
