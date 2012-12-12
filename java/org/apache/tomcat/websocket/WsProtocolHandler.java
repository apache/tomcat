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
package org.apache.tomcat.websocket;

import java.io.IOException;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.ProtocolHandler;
import javax.servlet.http.WebConnection;
import javax.websocket.Endpoint;

/**
 * Servlet 3.1 HTTP upgrade handler for WebSocket connections.
 */
public class WsProtocolHandler implements ProtocolHandler {

    private final Endpoint ep;
    private final ClassLoader applicationClassLoader;
    private final WsSession wsSession;


    public WsProtocolHandler(Endpoint ep) {
        this.ep = ep;
        applicationClassLoader = Thread.currentThread().getContextClassLoader();
        wsSession = new WsSession(ep);
    }


    @Override
    public void init(WebConnection connection) {
        // Need to call onOpen using the web application's class loader
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            ep.onOpen(wsSession);
        } finally {
            t.setContextClassLoader(cl);
        }
        ServletInputStream sis;
        ServletOutputStream sos;
        try {
            sis = connection.getInputStream();
            sos = connection.getOutputStream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        WsFrame wsFrame = new WsFrame(sis, wsSession);
        sis.setReadListener(new WsReadListener(this, wsFrame));
        sos.setWriteListener(new WsWriteListener(this));
    }


    private void onError(Throwable throwable) {
        // Need to call onError using the web application's class loader
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            ep.onError(throwable);
        } finally {
            t.setContextClassLoader(cl);
        }
    }

    private static class WsReadListener implements ReadListener {

        private final WsProtocolHandler wsProtocolHandler;
        private final WsFrame wsFrame;


        private WsReadListener(WsProtocolHandler wsProtocolHandler,
                WsFrame wsFrame) {
            this.wsProtocolHandler = wsProtocolHandler;
            this.wsFrame = wsFrame;
        }


        @Override
        public void onDataAvailable() {
            try {
                wsFrame.onDataAvailable();
            } catch (IOException e) {
                onError(e);
            }
        }


        @Override
        public void onAllDataRead() {
            // Will never happen with WebSocket
            throw new IllegalStateException();
        }


        @Override
        public void onError(Throwable throwable) {
            wsProtocolHandler.onError(throwable);
        }
    }

    private static class WsWriteListener implements WriteListener {

        private final WsProtocolHandler wsProtocolHandler;


        private WsWriteListener(WsProtocolHandler wsProtocolHandler) {
            this.wsProtocolHandler = wsProtocolHandler;
        }


        @Override
        public void onWritePossible() {
            // TODO Auto-generated method stub
        }


        @Override
        public void onError(Throwable throwable) {
            wsProtocolHandler.onError(throwable);
        }
    }
}
