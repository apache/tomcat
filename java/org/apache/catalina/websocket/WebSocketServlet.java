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
package org.apache.catalina.websocket;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.util.Base64;
import org.apache.tomcat.util.buf.B2CConverter;

/**
 * Provides the base implementation of a Servlet for processing WebSocket
 * connections as per RFC6455. It is expected that applications will extend this
 * implementation and provide application specific functionality.
 */
public abstract class WebSocketServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final byte[] WS_ACCEPT =
            "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(
                    B2CConverter.ISO_8859_1);

    private MessageDigest sha1Helper;


    @Override
    public void init() throws ServletException {
        super.init();

        try {
            sha1Helper = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new ServletException(e);
        }
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Information required to send the server handshake message
        String key;
        String subProtocol = null;
        List<String> extensions = Collections.emptyList();

        if (!headerContains(req, "upgrade", "websocket")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (!headerContains(req, "connection", "upgrade")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (!headerContains(req, "sec-websocket-version", "13")) {
            resp.setStatus(426);
            resp.setHeader("Sec-WebSocket-Version", "13");
            return;
        }

        key = req.getHeader("Sec-WebSocket-Key");
        if (key == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // TODO Read client handshake - Origin
        //                              Sec-WebSocket-Protocol
        //                              Sec-WebSocket-Extensions

        // TODO Extensions require the ability to specify something (API TBD)
        //      that can be passed to the Tomcat internals and process extension
        //      data present when the frame is fragmented.

        // If we got this far, all is good. Accept the connection.
        resp.setHeader("upgrade", "websocket");
        resp.setHeader("connection", "upgrade");
        resp.setHeader("Sec-WebSocket-Accept", getWebSocketAccept(key));
        if (subProtocol != null) {
            // TODO
        }
        if (!extensions.isEmpty()) {
            // TODO
        }

        // Small hack until the Servlet API provides a way to do this.
        StreamInbound inbound = createWebSocketInbound();
        ((RequestFacade) req).doUpgrade(inbound);
    }


    private boolean headerContains(HttpServletRequest req, String headerName,
            String target) {
        Enumeration<String> headers = req.getHeaders(headerName);
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            // TODO Splitting headers into tokens isn't quite this simple but
            //      this should be OK in this case. It is tempting to change the
            //      header parsing code so there is a one to one mapping between
            //      token and enumeration entry.
            String[] tokens = header.split(",");
            for (String token : tokens) {
                if (target.equalsIgnoreCase(token.trim())) {
                    return true;
                }
            }
        }
        return true;
    }


    private String getWebSocketAccept(String key) {
        synchronized (sha1Helper) {
            sha1Helper.reset();
            sha1Helper.update(key.getBytes(B2CConverter.ISO_8859_1));
            return Base64.encode(sha1Helper.digest(WS_ACCEPT));
        }
    }

    protected abstract StreamInbound createWebSocketInbound();
}
