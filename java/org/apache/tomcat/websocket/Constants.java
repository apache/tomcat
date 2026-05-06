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
package org.apache.tomcat.websocket;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Extension;

/**
 * Internal implementation constants.
 */
public class Constants {

    // OP Codes
    /**
     * Continuation frame opcode.
     */
    public static final byte OPCODE_CONTINUATION = 0x00;
    /**
     * Text frame opcode.
     */
    public static final byte OPCODE_TEXT = 0x01;
    /**
     * Binary frame opcode.
     */
    public static final byte OPCODE_BINARY = 0x02;
    /**
     * Close frame opcode.
     */
    public static final byte OPCODE_CLOSE = 0x08;
    /**
     * Ping frame opcode.
     */
    public static final byte OPCODE_PING = 0x09;
    /**
     * Pong frame opcode.
     */
    public static final byte OPCODE_PONG = 0x0A;

    // Internal OP Codes
    // RFC 6455 limits OP Codes to 4 bits so these should never clash
    // Always set bit 4 so these will be treated as control codes
    static final byte INTERNAL_OPCODE_FLUSH = 0x18;

    // Buffers
    static final int DEFAULT_BUFFER_SIZE =
            Integer.getInteger("org.apache.tomcat.websocket.DEFAULT_BUFFER_SIZE", 8 * 1024).intValue();

    // Client connection
    /**
     * Property name to set to configure the value that is passed to
     * {@link javax.net.ssl.SSLEngine#setEnabledProtocols(String[])}. The value should be a comma separated string.
     *
     * @deprecated This will be removed in Tomcat 11. Use {@link ClientEndpointConfig#getSSLContext()}
     */
    @Deprecated(forRemoval = true, since = "Tomcat 10.1.x")
    public static final String SSL_PROTOCOLS_PROPERTY = "org.apache.tomcat.websocket.SSL_PROTOCOLS";
    @Deprecated(forRemoval = true, since = "Tomcat 10.1.x")
    public static final String SSL_TRUSTSTORE_PROPERTY = "org.apache.tomcat.websocket.SSL_TRUSTSTORE";
    @Deprecated(forRemoval = true, since = "Tomcat 10.1.x")
    public static final String SSL_TRUSTSTORE_PWD_PROPERTY = "org.apache.tomcat.websocket.SSL_TRUSTSTORE_PWD";
    @Deprecated(forRemoval = true, since = "Tomcat 10.1.x")
    public static final String SSL_TRUSTSTORE_PWD_DEFAULT = "changeit";
    /**
     * Property name to set to configure used SSLContext. The value should be an instance of SSLContext. If this
     * property is present, the SSL_TRUSTSTORE* properties are ignored.
     *
     * @deprecated This will be removed in Tomcat 11. Use {@link ClientEndpointConfig#getSSLContext()}
     */
    @Deprecated(forRemoval = true, since = "Tomcat 10.1.x")
    public static final String SSL_CONTEXT_PROPERTY = "org.apache.tomcat.websocket.SSL_CONTEXT";
    /**
     * Property name to set to configure the timeout (in milliseconds) when establishing a WebSocket connection to
     * server. The default is {@link #IO_TIMEOUT_MS_DEFAULT}.
     */
    public static final String IO_TIMEOUT_MS_PROPERTY = "org.apache.tomcat.websocket.IO_TIMEOUT_MS";
    /**
     * Default I/O timeout in milliseconds for WebSocket client connections.
     */
    public static final long IO_TIMEOUT_MS_DEFAULT = 5000;

    // RFC 2068 recommended a limit of 5
    // Most browsers have a default limit of 20
    /**
     * Property name for maximum redirect count.
     */
    public static final String MAX_REDIRECTIONS_PROPERTY = "org.apache.tomcat.websocket.MAX_REDIRECTIONS";
    /**
     * Default maximum number of redirects.
     */
    public static final int MAX_REDIRECTIONS_DEFAULT = 20;

    // HTTP upgrade header names and values
    /**
     * Host HTTP header name.
     */
    public static final String HOST_HEADER_NAME = "Host";
    /**
     * Upgrade HTTP header name.
     */
    public static final String UPGRADE_HEADER_NAME = "Upgrade";
    /**
     * Upgrade header value for WebSocket.
     */
    public static final String UPGRADE_HEADER_VALUE = "websocket";
    /**
     * Origin HTTP header name.
     */
    public static final String ORIGIN_HEADER_NAME = "Origin";
    /**
     * Connection HTTP header name.
     */
    public static final String CONNECTION_HEADER_NAME = "Connection";
    /**
     * Connection header upgrade value.
     */
    public static final String CONNECTION_HEADER_VALUE = "upgrade";
    /**
     * Location HTTP header name.
     */
    public static final String LOCATION_HEADER_NAME = "Location";
    /**
     * Authorization HTTP header name.
     */
    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    /**
     * WWW-Authenticate HTTP header name.
     */
    public static final String WWW_AUTHENTICATE_HEADER_NAME = "WWW-Authenticate";
    /**
     * Proxy-Authorization HTTP header name.
     */
    public static final String PROXY_AUTHORIZATION_HEADER_NAME = "Proxy-Authorization";
    /**
     * Proxy-Authenticate HTTP header name.
     */
    public static final String PROXY_AUTHENTICATE_HEADER_NAME = "Proxy-Authenticate";
    /**
     * Sec-WebSocket-Version HTTP header name.
     */
    public static final String WS_VERSION_HEADER_NAME = "Sec-WebSocket-Version";
    /**
     * Sec-WebSocket-Version header value.
     */
    public static final String WS_VERSION_HEADER_VALUE = "13";
    /**
     * Sec-WebSocket-Key HTTP header name.
     */
    public static final String WS_KEY_HEADER_NAME = "Sec-WebSocket-Key";
    /**
     * Sec-WebSocket-Protocol HTTP header name.
     */
    public static final String WS_PROTOCOL_HEADER_NAME = "Sec-WebSocket-Protocol";
    /**
     * Sec-WebSocket-Extensions HTTP header name.
     */
    public static final String WS_EXTENSIONS_HEADER_NAME = "Sec-WebSocket-Extensions";

    // HTTP status codes
    /**
     * HTTP 300 Multiple Choices.
     */
    public static final int MULTIPLE_CHOICES = 300;
    /**
     * HTTP 301 Moved Permanently.
     */
    public static final int MOVED_PERMANENTLY = 301;
    /**
     * HTTP 302 Found.
     */
    public static final int FOUND = 302;
    /**
     * HTTP 303 See Other.
     */
    public static final int SEE_OTHER = 303;
    /**
     * HTTP 305 Use Proxy.
     */
    public static final int USE_PROXY = 305;
    /**
     * HTTP 307 Temporary Redirect.
     */
    public static final int TEMPORARY_REDIRECT = 307;
    /**
     * HTTP 401 Unauthorized.
     */
    public static final int UNAUTHORIZED = 401;
    /**
     * HTTP 407 Proxy Authentication Required.
     */
    public static final int PROXY_AUTHENTICATION_REQUIRED = 407;

    // Configuration for Origin header in client
    static final String DEFAULT_ORIGIN_HEADER_VALUE =
            System.getProperty("org.apache.tomcat.websocket.DEFAULT_ORIGIN_HEADER_VALUE");

    /**
     * Property name for blocking send timeout configuration.
     */
    public static final String BLOCKING_SEND_TIMEOUT_PROPERTY = "org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT";
    /**
     * Default blocking send timeout in milliseconds (20 seconds).
     */
    public static final long DEFAULT_BLOCKING_SEND_TIMEOUT = 20 * 1000;

    /**
     * Property name for session close timeout configuration.
     */
    public static final String SESSION_CLOSE_TIMEOUT_PROPERTY = "org.apache.tomcat.websocket.SESSION_CLOSE_TIMEOUT";
    /**
     * Default session close timeout in milliseconds (30 seconds).
     */
    public static final long DEFAULT_SESSION_CLOSE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    /**
     * Property name for abnormal session close send timeout configuration.
     */
    public static final String ABNORMAL_SESSION_CLOSE_SEND_TIMEOUT_PROPERTY =
            "org.apache.tomcat.websocket.ABNORMAL_SESSION_CLOSE_SEND_TIMEOUT";
    /**
     * Default abnormal session close send timeout in milliseconds (50ms).
     */
    public static final long DEFAULT_ABNORMAL_SESSION_CLOSE_SEND_TIMEOUT = 50;

    /**
     * Property name for read idle timeout in milliseconds on WebSocket sessions.
     */
    public static final String READ_IDLE_TIMEOUT_MS = "org.apache.tomcat.websocket.READ_IDLE_TIMEOUT_MS";

    /**
     * Property name for write idle timeout in milliseconds on WebSocket sessions.
     */
    public static final String WRITE_IDLE_TIMEOUT_MS = "org.apache.tomcat.websocket.WRITE_IDLE_TIMEOUT_MS";

    // Configuration for background processing checks intervals
    static final int DEFAULT_PROCESS_PERIOD =
            Integer.getInteger("org.apache.tomcat.websocket.DEFAULT_PROCESS_PERIOD", 10).intValue();

    /**
     * Property name for WebSocket authentication username.
     */
    public static final String WS_AUTHENTICATION_USER_NAME = "org.apache.tomcat.websocket.WS_AUTHENTICATION_USER_NAME";
    /**
     * Property name for WebSocket authentication password.
     */
    public static final String WS_AUTHENTICATION_PASSWORD = "org.apache.tomcat.websocket.WS_AUTHENTICATION_PASSWORD";
    /**
     * Property name for WebSocket authentication realm.
     */
    public static final String WS_AUTHENTICATION_REALM = "org.apache.tomcat.websocket.WS_AUTHENTICATION_REALM";

    /**
     * Property name for WebSocket proxy authentication username.
     */
    public static final String WS_AUTHENTICATION_PROXY_USER_NAME =
            "org.apache.tomcat.websocket.WS_AUTHENTICATION_PROXY_USER_NAME";
    /**
     * Property name for WebSocket proxy authentication password.
     */
    public static final String WS_AUTHENTICATION_PROXY_PASSWORD =
            "org.apache.tomcat.websocket.WS_AUTHENTICATION_PROXY_PASSWORD";
    /**
     * Property name for WebSocket proxy authentication realm.
     */
    public static final String WS_AUTHENTICATION_PROXY_REALM =
            "org.apache.tomcat.websocket.WS_AUTHENTICATION_PROXY_REALM";

    /**
     * List of installed WebSocket extensions.
     */
    public static final List<Extension> INSTALLED_EXTENSIONS = List.of(new WsExtension("permessage-deflate"));

    private Constants() {
        // Hide default constructor
    }
}
