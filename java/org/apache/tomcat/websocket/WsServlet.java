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
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.ProtocolHandler;
import javax.websocket.Endpoint;
import javax.websocket.ServerEndpointConfiguration;
import javax.xml.bind.DatatypeConverter;

/**
 * Handles the initial HTTP connection for WebSocket connections.
 */
public class WsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Charset ISO_8859_1;
    static {
        ISO_8859_1 = Charset.forName("ISO-8859-1");
    }
    private static final byte[] WS_ACCEPT = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(ISO_8859_1);
    private final Queue<MessageDigest> sha1Helpers = new ConcurrentLinkedQueue<>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // Information required to send the server handshake message
        String key;
        String subProtocol = null;
        List<String> extensions = Collections.emptyList();
        if (!headerContainsToken(req, "upgrade", "websocket")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!headerContainsToken(req, "connection", "upgrade")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!headerContainsToken(req, "sec-websocket-version", "13")) {
            resp.setStatus(426);
            resp.setHeader("Sec-WebSocket-Version", "13");
            return;
        }
        key = req.getHeader("Sec-WebSocket-Key");
        if (key == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        // Need an Endpoint instance to progress this further
        ServerContainerImpl cp = ServerContainerImpl.getServerContainer();
        ServerEndpointConfiguration<?> sec = cp.getServerEndpointConfiguration(
                req.getServletPath(), req.getPathInfo());
        // Origin check
        String origin = req.getHeader("Origin");
        if (!sec.checkOrigin(origin)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        // Sub-protocols
        List<String> subProtocols = getTokensFromHeader(req,
                "Sec-WebSocket-Protocol");
        if (!subProtocols.isEmpty()) {
            subProtocol = sec.getNegotiatedSubprotocol(subProtocols);
        }
        // Extensions
        List<String> requestedExtensions = getTokensFromHeader(req,
                "Sec-WebSocket-Extensions");
        if (!extensions.isEmpty()) {
            extensions = sec.getNegotiatedExtensions(requestedExtensions);
        }
        // If we got this far, all is good. Accept the connection.
        resp.setHeader("Upgrade", "websocket");
        resp.setHeader("Connection", "upgrade");
        resp.setHeader("Sec-WebSocket-Accept", getWebSocketAccept(key));
        if (subProtocol != null) {
            resp.setHeader("Sec-WebSocket-Protocol", subProtocol);
        }
        if (!extensions.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            Iterator<String> iter = extensions.iterator();
            // There must be at least one
            sb.append(iter.next());
            while (iter.hasNext()) {
                sb.append(',');
                sb.append(iter.next());
            }
            resp.setHeader("Sec-WebSocket-Extensions", sb.toString());
        }
        Endpoint ep = (Endpoint) sec.getEndpointFactory().createEndpoint();
        ProtocolHandler wsHandler = new WsProtocolHandler(ep);
        req.upgrade(wsHandler);
    }


    /*
     * This only works for tokens. Quoted strings need more sophisticated
     * parsing.
     */
    private boolean headerContainsToken(HttpServletRequest req,
            String headerName, String target) {
        Enumeration<String> headers = req.getHeaders(headerName);
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            String[] tokens = header.split(",");
            for (String token : tokens) {
                if (target.equalsIgnoreCase(token.trim())) {
                    return true;
                }
            }
        }
        return false;
    }


    /*
     * This only works for tokens. Quoted strings need more sophisticated
     * parsing.
     */
    private List<String> getTokensFromHeader(HttpServletRequest req,
            String headerName) {
        List<String> result = new ArrayList<>();
        Enumeration<String> headers = req.getHeaders(headerName);
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            String[] tokens = header.split(",");
            for (String token : tokens) {
                result.add(token.trim());
            }
        }
        return result;
    }


    private String getWebSocketAccept(String key) throws ServletException {
        MessageDigest sha1Helper = sha1Helpers.poll();
        if (sha1Helper == null) {
            try {
                sha1Helper = MessageDigest.getInstance("SHA1");
            } catch (NoSuchAlgorithmException e) {
                throw new ServletException(e);
            }
        }
        sha1Helper.reset();
        sha1Helper.update(key.getBytes(ISO_8859_1));
        String result = DatatypeConverter.printBase64Binary(sha1Helper.digest(WS_ACCEPT));
        sha1Helpers.add(sha1Helper);
        return result;
    }
}
