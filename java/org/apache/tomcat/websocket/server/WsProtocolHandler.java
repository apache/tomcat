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
package org.apache.tomcat.websocket.server;

import java.io.EOFException;
import java.io.IOException;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.WebConnection;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfiguration;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.WsIOException;
import org.apache.tomcat.websocket.WsRequest;
import org.apache.tomcat.websocket.WsSession;

/**
 * Servlet 3.1 HTTP upgrade handler for WebSocket connections.
 */
public class WsProtocolHandler implements HttpUpgradeHandler {

    private static StringManager sm =
            StringManager.getManager(Constants.PACKAGE_NAME);
    private static final Log log =
            LogFactory.getLog(WsProtocolHandler.class);

    private final Endpoint ep;
    private final EndpointConfiguration endpointConfig;
    private final ClassLoader applicationClassLoader;
    private final ServerContainerImpl webSocketContainer;
    private final WsRequest request;
    private final String subProtocol;

    private WsSession wsSession;


    public WsProtocolHandler(Endpoint ep, EndpointConfiguration endpointConfig,
            ServerContainerImpl wsc, WsRequest request, String subProtocol) {
        this.ep = ep;
        this.endpointConfig = endpointConfig;
        this.webSocketContainer = wsc;
        this.request = request;
        this.subProtocol = subProtocol;
        applicationClassLoader = Thread.currentThread().getContextClassLoader();
    }


    @Override
    public void init(WebConnection connection) {
        ServletInputStream sis;
        ServletOutputStream sos;
        try {
            sis = connection.getInputStream();
            sos = connection.getOutputStream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        // Need to call onOpen using the web application's class loader
        // Create the frame using the application's class loader so it can pick
        // up application specific config from the ServerContainerImpl
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            WsRemoteEndpointServer wsRemoteEndpointServer =
                    new WsRemoteEndpointServer(sos, webSocketContainer);
            // TODO Replace null with path parameter map
            wsSession = new WsSession(ep, wsRemoteEndpointServer,
                    webSocketContainer, request, subProtocol, null);
            WsFrameServer wsFrame = new WsFrameServer(
                    sis,
                    wsSession);
            sis.setReadListener(new WsReadListener(this, wsFrame));
            sos.setWriteListener(
                    new WsWriteListener(this, wsRemoteEndpointServer));
            ep.onOpen(wsSession, endpointConfig);
            webSocketContainer.registerSession(ep.getClass(), wsSession);
        } finally {
            t.setContextClassLoader(cl);
        }
    }


    private void onError(Throwable throwable) {
        // Need to call onError using the web application's class loader
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            ep.onError(wsSession, throwable);
        } finally {
            t.setContextClassLoader(cl);
        }
    }


    private void close(CloseReason cr) {
        try {
            wsSession.close(cr);
        } catch (IOException e) {
            if (log.isInfoEnabled()) {
                log.info(sm.getString("wsProtocolHandler.closeFailed"), e);
            }
        }
    }


    private static class WsReadListener implements ReadListener {

        private final WsProtocolHandler wsProtocolHandler;
        private final WsFrameServer wsFrame;


        private WsReadListener(WsProtocolHandler wsProtocolHandler,
                WsFrameServer wsFrame) {
            this.wsProtocolHandler = wsProtocolHandler;
            this.wsFrame = wsFrame;
        }


        @Override
        public void onDataAvailable() {
            try {
                wsFrame.onDataAvailable();
            } catch (WsIOException ws) {
                wsProtocolHandler.close(ws.getCloseReason());
            } catch (EOFException eof) {
                CloseReason cr = new CloseReason(
                        CloseCodes.CLOSED_ABNORMALLY, eof.getMessage());
                wsProtocolHandler.close(cr);
            } catch (IOException ioe) {
                onError(ioe);
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
        private final WsRemoteEndpointServer wsRemoteEndpointServer;

        private WsWriteListener(WsProtocolHandler wsProtocolHandler,
                WsRemoteEndpointServer wsRemoteEndpointServer) {
            this.wsProtocolHandler = wsProtocolHandler;
            this.wsRemoteEndpointServer = wsRemoteEndpointServer;
        }


        @Override
        public void onWritePossible() {
            wsRemoteEndpointServer.onWritePossible();
        }


        @Override
        public void onError(Throwable throwable) {
            wsProtocolHandler.onError(throwable);
        }
    }
}
