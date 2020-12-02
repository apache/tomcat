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

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.collections.CaseInsensitiveKeyMap;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.KeyStoreUtil;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;

public class WsWebSocketContainer implements WebSocketContainer, BackgroundProcess {

    private static final StringManager sm = StringManager.getManager(WsWebSocketContainer.class);
    private static final Random RANDOM = new Random();
    private static final byte[] CRLF = new byte[] { 13, 10 };

    private static final byte[] GET_BYTES = "GET ".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ROOT_URI_BYTES = "/".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] HTTP_VERSION_BYTES =
            " HTTP/1.1\r\n".getBytes(StandardCharsets.ISO_8859_1);

    private volatile AsynchronousChannelGroup asynchronousChannelGroup = null;
    private final Object asynchronousChannelGroupLock = new Object();

    private final Log log = LogFactory.getLog(WsWebSocketContainer.class); // must not be static
    // Server side uses the endpoint path as the key
    // Client side uses the client endpoint instance
    private final Map<Object, Set<WsSession>> endpointSessionMap = new HashMap<>();
    private final Map<WsSession,WsSession> sessions = new ConcurrentHashMap<>();
    private final Object endPointSessionMapLock = new Object();

    private long defaultAsyncTimeout = -1;
    private int maxBinaryMessageBufferSize = Constants.DEFAULT_BUFFER_SIZE;
    private int maxTextMessageBufferSize = Constants.DEFAULT_BUFFER_SIZE;
    private volatile long defaultMaxSessionIdleTimeout = 0;
    private int backgroundProcessCount = 0;
    private int processPeriod = Constants.DEFAULT_PROCESS_PERIOD;

    private InstanceManager instanceManager;

    InstanceManager getInstanceManager() {
        return instanceManager;
    }

    protected void setInstanceManager(InstanceManager instanceManager) {
        this.instanceManager = instanceManager;
    }

    @Override
    public Session connectToServer(Object pojo, URI path)
            throws DeploymentException {

        ClientEndpoint annotation =
                pojo.getClass().getAnnotation(ClientEndpoint.class);
        if (annotation == null) {
            throw new DeploymentException(
                    sm.getString("wsWebSocketContainer.missingAnnotation",
                            pojo.getClass().getName()));
        }

        Endpoint ep = new PojoEndpointClient(pojo, Arrays.asList(annotation.decoders()));

        Class<? extends ClientEndpointConfig.Configurator> configuratorClazz =
                annotation.configurator();

        ClientEndpointConfig.Configurator configurator = null;
        if (!ClientEndpointConfig.Configurator.class.equals(
                configuratorClazz)) {
            try {
                configurator = configuratorClazz.getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new DeploymentException(sm.getString(
                        "wsWebSocketContainer.defaultConfiguratorFail"), e);
            }
        }

        ClientEndpointConfig.Builder builder = ClientEndpointConfig.Builder.create();
        // Avoid NPE when using RI API JAR - see BZ 56343
        if (configurator != null) {
            builder.configurator(configurator);
        }
        ClientEndpointConfig config = builder.
                decoders(Arrays.asList(annotation.decoders())).
                encoders(Arrays.asList(annotation.encoders())).
                preferredSubprotocols(Arrays.asList(annotation.subprotocols())).
                build();
        return connectToServer(ep, config, path);
    }


    @Override
    public Session connectToServer(Class<?> annotatedEndpointClass, URI path)
            throws DeploymentException {

        Object pojo;
        try {
            pojo = annotatedEndpointClass.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new DeploymentException(sm.getString(
                    "wsWebSocketContainer.endpointCreateFail",
                    annotatedEndpointClass.getName()), e);
        }

        return connectToServer(pojo, path);
    }


    @Override
    public Session connectToServer(Class<? extends Endpoint> clazz,
            ClientEndpointConfig clientEndpointConfiguration, URI path)
            throws DeploymentException {

        Endpoint endpoint;
        try {
            endpoint = clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new DeploymentException(sm.getString(
                    "wsWebSocketContainer.endpointCreateFail", clazz.getName()),
                    e);
        }

        return connectToServer(endpoint, clientEndpointConfiguration, path);
    }


    @Override
    public Session connectToServer(Endpoint endpoint,
            ClientEndpointConfig clientEndpointConfiguration, URI path)
            throws DeploymentException {
        return connectToServerRecursive(endpoint, clientEndpointConfiguration, path, new HashSet<URI>());
    }

    private Session connectToServerRecursive(Endpoint endpoint,
            ClientEndpointConfig clientEndpointConfiguration, URI path,
            Set<URI> redirectSet)
            throws DeploymentException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("wsWebSocketContainer.connect.entry", endpoint.getClass().getName(), path));
        }

        boolean secure = false;
        ByteBuffer proxyConnect = null;
        URI proxyPath;

        // Validate scheme (and build proxyPath)
        String scheme = path.getScheme();
        if ("ws".equalsIgnoreCase(scheme)) {
            proxyPath = URI.create("http" + path.toString().substring(2));
        } else if ("wss".equalsIgnoreCase(scheme)) {
            proxyPath = URI.create("https" + path.toString().substring(3));
            secure = true;
        } else {
            throw new DeploymentException(sm.getString(
                    "wsWebSocketContainer.pathWrongScheme", scheme));
        }

        // Validate host
        String host = path.getHost();
        if (host == null) {
            throw new DeploymentException(
                    sm.getString("wsWebSocketContainer.pathNoHost"));
        }
        int port = path.getPort();

        SocketAddress sa = null;

        // Check to see if a proxy is configured. Javadoc indicates return value
        // will never be null
        List<Proxy> proxies = ProxySelector.getDefault().select(proxyPath);
        Proxy selectedProxy = null;
        for (Proxy proxy : proxies) {
            if (proxy.type().equals(Proxy.Type.HTTP)) {
                sa = proxy.address();
                if (sa instanceof InetSocketAddress) {
                    InetSocketAddress inet = (InetSocketAddress) sa;
                    if (inet.isUnresolved()) {
                        sa = new InetSocketAddress(inet.getHostName(), inet.getPort());
                    }
                }
                selectedProxy = proxy;
                break;
            }
        }

        // If the port is not explicitly specified, compute it based on the
        // scheme
        if (port == -1) {
            if ("ws".equalsIgnoreCase(scheme)) {
                port = 80;
            } else {
                // Must be wss due to scheme validation above
                port = 443;
            }
        }

        // If sa is null, no proxy is configured so need to create sa
        if (sa == null) {
            sa = new InetSocketAddress(host, port);
        } else {
            proxyConnect = createProxyRequest(host, port);
        }

        // Create the initial HTTP request to open the WebSocket connection
        Map<String, List<String>> reqHeaders = createRequestHeaders(host, port, secure,
                clientEndpointConfiguration);
        clientEndpointConfiguration.getConfigurator().beforeRequest(reqHeaders);
        if (Constants.DEFAULT_ORIGIN_HEADER_VALUE != null
                && !reqHeaders.containsKey(Constants.ORIGIN_HEADER_NAME)) {
            List<String> originValues = new ArrayList<>(1);
            originValues.add(Constants.DEFAULT_ORIGIN_HEADER_VALUE);
            reqHeaders.put(Constants.ORIGIN_HEADER_NAME, originValues);
        }
        ByteBuffer request = createRequest(path, reqHeaders);

        AsynchronousSocketChannel socketChannel;
        try {
            socketChannel = AsynchronousSocketChannel.open(getAsynchronousChannelGroup());
        } catch (IOException ioe) {
            throw new DeploymentException(sm.getString(
                    "wsWebSocketContainer.asynchronousSocketChannelFail"), ioe);
        }

        Map<String,Object> userProperties = clientEndpointConfiguration.getUserProperties();

        // Get the connection timeout
        long timeout = Constants.IO_TIMEOUT_MS_DEFAULT;
        String timeoutValue = (String) userProperties.get(Constants.IO_TIMEOUT_MS_PROPERTY);
        if (timeoutValue != null) {
            timeout = Long.valueOf(timeoutValue).intValue();
        }

        // Set-up
        // Same size as the WsFrame input buffer
        ByteBuffer response = ByteBuffer.allocate(getDefaultMaxBinaryMessageBufferSize());
        String subProtocol;
        boolean success = false;
        List<Extension> extensionsAgreed = new ArrayList<>();
        Transformation transformation = null;
        AsyncChannelWrapper channel = null;

        try {
            // Open the connection
            Future<Void> fConnect = socketChannel.connect(sa);

            if (proxyConnect != null) {
                fConnect.get(timeout, TimeUnit.MILLISECONDS);
                // Proxy CONNECT is clear text
                channel = new AsyncChannelWrapperNonSecure(socketChannel);
                writeRequest(channel, proxyConnect, timeout);
                HttpResponse httpResponse = processResponse(response, channel, timeout);
                if (httpResponse.getStatus() != 200) {
                    throw new DeploymentException(sm.getString(
                            "wsWebSocketContainer.proxyConnectFail", selectedProxy,
                            Integer.toString(httpResponse.getStatus())));
                }
            }

            if (secure) {
                // Regardless of whether a non-secure wrapper was created for a
                // proxy CONNECT, need to use TLS from this point on so wrap the
                // original AsynchronousSocketChannel
                SSLEngine sslEngine = createSSLEngine(userProperties, host, port);
                channel = new AsyncChannelWrapperSecure(socketChannel, sslEngine);
            } else if (channel == null) {
                // Only need to wrap as this point if it wasn't wrapped to process a
                // proxy CONNECT
                channel = new AsyncChannelWrapperNonSecure(socketChannel);
            }

            fConnect.get(timeout, TimeUnit.MILLISECONDS);

            Future<Void> fHandshake = channel.handshake();
            fHandshake.get(timeout, TimeUnit.MILLISECONDS);

            if (log.isDebugEnabled()) {
                SocketAddress localAddress = null;
                try {
                    localAddress = channel.getLocalAddress();
                } catch (IOException ioe) {
                    // Ignore
                }
                log.debug(sm.getString("wsWebSocketContainer.connect.write",
                        Integer.valueOf(request.position()), Integer.valueOf(request.limit()), localAddress));
            }
            writeRequest(channel, request, timeout);

            HttpResponse httpResponse = processResponse(response, channel, timeout);

            // Check maximum permitted redirects
            int maxRedirects = Constants.MAX_REDIRECTIONS_DEFAULT;
            String maxRedirectsValue =
                    (String) userProperties.get(Constants.MAX_REDIRECTIONS_PROPERTY);
            if (maxRedirectsValue != null) {
                maxRedirects = Integer.parseInt(maxRedirectsValue);
            }

            if (httpResponse.status != 101) {
                if(isRedirectStatus(httpResponse.status)){
                    List<String> locationHeader =
                            httpResponse.getHandshakeResponse().getHeaders().get(
                                    Constants.LOCATION_HEADER_NAME);

                    if (locationHeader == null || locationHeader.isEmpty() ||
                            locationHeader.get(0) == null || locationHeader.get(0).isEmpty()) {
                        throw new DeploymentException(sm.getString(
                                "wsWebSocketContainer.missingLocationHeader",
                                Integer.toString(httpResponse.status)));
                    }

                    URI redirectLocation = URI.create(locationHeader.get(0)).normalize();

                    if (!redirectLocation.isAbsolute()) {
                        redirectLocation = path.resolve(redirectLocation);
                    }

                    String redirectScheme = redirectLocation.getScheme().toLowerCase();

                    if (redirectScheme.startsWith("http")) {
                        redirectLocation = new URI(redirectScheme.replace("http", "ws"),
                                redirectLocation.getUserInfo(), redirectLocation.getHost(),
                                redirectLocation.getPort(), redirectLocation.getPath(),
                                redirectLocation.getQuery(), redirectLocation.getFragment());
                    }

                    if (!redirectSet.add(redirectLocation) || redirectSet.size() > maxRedirects) {
                        throw new DeploymentException(sm.getString(
                                "wsWebSocketContainer.redirectThreshold", redirectLocation,
                                Integer.toString(redirectSet.size()),
                                Integer.toString(maxRedirects)));
                    }

                    return connectToServerRecursive(endpoint, clientEndpointConfiguration, redirectLocation, redirectSet);

                }

                else if (httpResponse.status == 401) {

                    if (userProperties.get(Constants.AUTHORIZATION_HEADER_NAME) != null) {
                        throw new DeploymentException(sm.getString(
                                "wsWebSocketContainer.failedAuthentication",
                                Integer.valueOf(httpResponse.status)));
                    }

                    List<String> wwwAuthenticateHeaders = httpResponse.getHandshakeResponse()
                            .getHeaders().get(Constants.WWW_AUTHENTICATE_HEADER_NAME);

                    if (wwwAuthenticateHeaders == null || wwwAuthenticateHeaders.isEmpty() ||
                            wwwAuthenticateHeaders.get(0) == null || wwwAuthenticateHeaders.get(0).isEmpty()) {
                        throw new DeploymentException(sm.getString(
                                "wsWebSocketContainer.missingWWWAuthenticateHeader",
                                Integer.toString(httpResponse.status)));
                    }

                    String authScheme = wwwAuthenticateHeaders.get(0).split("\\s+", 2)[0];
                    String requestUri = new String(request.array(), StandardCharsets.ISO_8859_1)
                            .split("\\s", 3)[1];

                    Authenticator auth = AuthenticatorFactory.getAuthenticator(authScheme);

                    if (auth == null) {
                        throw new DeploymentException(
                                sm.getString("wsWebSocketContainer.unsupportedAuthScheme",
                                        Integer.valueOf(httpResponse.status), authScheme));
                    }

                    userProperties.put(Constants.AUTHORIZATION_HEADER_NAME, auth.getAuthorization(
                            requestUri, wwwAuthenticateHeaders.get(0), userProperties));

                    return connectToServerRecursive(endpoint, clientEndpointConfiguration, path, redirectSet);

                } else {
                    throw new DeploymentException(sm.getString("wsWebSocketContainer.invalidStatus",
                            Integer.toString(httpResponse.status)));
                }
            }
            HandshakeResponse handshakeResponse = httpResponse.getHandshakeResponse();
            clientEndpointConfiguration.getConfigurator().afterResponse(handshakeResponse);

            // Sub-protocol
            List<String> protocolHeaders = handshakeResponse.getHeaders().get(
                    Constants.WS_PROTOCOL_HEADER_NAME);
            if (protocolHeaders == null || protocolHeaders.size() == 0) {
                subProtocol = null;
            } else if (protocolHeaders.size() == 1) {
                subProtocol = protocolHeaders.get(0);
            } else {
                throw new DeploymentException(
                        sm.getString("wsWebSocketContainer.invalidSubProtocol"));
            }

            // Extensions
            // Should normally only be one header but handle the case of
            // multiple headers
            List<String> extHeaders = handshakeResponse.getHeaders().get(
                    Constants.WS_EXTENSIONS_HEADER_NAME);
            if (extHeaders != null) {
                for (String extHeader : extHeaders) {
                    Util.parseExtensionHeader(extensionsAgreed, extHeader);
                }
            }

            // Build the transformations
            TransformationFactory factory = TransformationFactory.getInstance();
            for (Extension extension : extensionsAgreed) {
                List<List<Extension.Parameter>> wrapper = new ArrayList<>(1);
                wrapper.add(extension.getParameters());
                Transformation t = factory.create(extension.getName(), wrapper, false);
                if (t == null) {
                    throw new DeploymentException(sm.getString(
                            "wsWebSocketContainer.invalidExtensionParameters"));
                }
                if (transformation == null) {
                    transformation = t;
                } else {
                    transformation.setNext(t);
                }
            }

            success = true;
        } catch (ExecutionException | InterruptedException | SSLException |
                EOFException | TimeoutException | URISyntaxException | AuthenticationException e) {
            throw new DeploymentException(sm.getString("wsWebSocketContainer.httpRequestFailed", path), e);
        } finally {
            if (!success) {
                if (channel != null) {
                    channel.close();
                } else {
                    try {
                        socketChannel.close();
                    } catch (IOException ioe) {
                        // Ignore
                    }
                }
            }
        }

        // Switch to WebSocket
        WsRemoteEndpointImplClient wsRemoteEndpointClient = new WsRemoteEndpointImplClient(channel);

        WsSession wsSession = new WsSession(endpoint, wsRemoteEndpointClient,
                this, null, null, null, null, null, extensionsAgreed,
                subProtocol, Collections.<String,String>emptyMap(), secure,
                clientEndpointConfiguration);

        WsFrameClient wsFrameClient = new WsFrameClient(response, channel,
                wsSession, transformation);
        // WsFrame adds the necessary final transformations. Copy the
        // completed transformation chain to the remote end point.
        wsRemoteEndpointClient.setTransformation(wsFrameClient.getTransformation());

        endpoint.onOpen(wsSession, clientEndpointConfiguration);
        registerSession(endpoint, wsSession);

        /* It is possible that the server sent one or more messages as soon as
         * the WebSocket connection was established. Depending on the exact
         * timing of when those messages were sent they could be sat in the
         * input buffer waiting to be read and will not trigger a "data
         * available to read" event. Therefore, it is necessary to process the
         * input buffer here. Note that this happens on the current thread which
         * means that this thread will be used for any onMessage notifications.
         * This is a special case. Subsequent "data available to read" events
         * will be handled by threads from the AsyncChannelGroup's executor.
         */
        wsFrameClient.startInputProcessing();

        return wsSession;
    }


    private static void writeRequest(AsyncChannelWrapper channel, ByteBuffer request,
            long timeout) throws TimeoutException, InterruptedException, ExecutionException {
        int toWrite = request.limit();

        Future<Integer> fWrite = channel.write(request);
        Integer thisWrite = fWrite.get(timeout, TimeUnit.MILLISECONDS);
        toWrite -= thisWrite.intValue();

        while (toWrite > 0) {
            fWrite = channel.write(request);
            thisWrite = fWrite.get(timeout, TimeUnit.MILLISECONDS);
            toWrite -= thisWrite.intValue();
        }
    }


    private static boolean isRedirectStatus(int httpResponseCode) {

        boolean isRedirect = false;

        switch (httpResponseCode) {
        case Constants.MULTIPLE_CHOICES:
        case Constants.MOVED_PERMANENTLY:
        case Constants.FOUND:
        case Constants.SEE_OTHER:
        case Constants.USE_PROXY:
        case Constants.TEMPORARY_REDIRECT:
            isRedirect = true;
            break;
        default:
            break;
        }

        return isRedirect;
    }


    private static ByteBuffer createProxyRequest(String host, int port) {
        StringBuilder request = new StringBuilder();
        request.append("CONNECT ");
        request.append(host);
        request.append(':');
        request.append(port);

        request.append(" HTTP/1.1\r\nProxy-Connection: keep-alive\r\nConnection: keepalive\r\nHost: ");
        request.append(host);
        request.append(':');
        request.append(port);

        request.append("\r\n\r\n");

        byte[] bytes = request.toString().getBytes(StandardCharsets.ISO_8859_1);
        return ByteBuffer.wrap(bytes);
    }

    protected void registerSession(Object key, WsSession wsSession) {

        if (!wsSession.isOpen()) {
            // The session was closed during onOpen. No need to register it.
            return;
        }
        synchronized (endPointSessionMapLock) {
            if (endpointSessionMap.size() == 0) {
                BackgroundProcessManager.getInstance().register(this);
            }
            Set<WsSession> wsSessions = endpointSessionMap.get(key);
            if (wsSessions == null) {
                wsSessions = new HashSet<>();
                endpointSessionMap.put(key, wsSessions);
            }
            wsSessions.add(wsSession);
        }
        sessions.put(wsSession, wsSession);
    }


    protected void unregisterSession(Object key, WsSession wsSession) {

        synchronized (endPointSessionMapLock) {
            Set<WsSession> wsSessions = endpointSessionMap.get(key);
            if (wsSessions != null) {
                wsSessions.remove(wsSession);
                if (wsSessions.size() == 0) {
                    endpointSessionMap.remove(key);
                }
            }
            if (endpointSessionMap.size() == 0) {
                BackgroundProcessManager.getInstance().unregister(this);
            }
        }
        sessions.remove(wsSession);
    }


    Set<Session> getOpenSessions(Object key) {
        HashSet<Session> result = new HashSet<>();
        synchronized (endPointSessionMapLock) {
            Set<WsSession> sessions = endpointSessionMap.get(key);
            if (sessions != null) {
                result.addAll(sessions);
            }
        }
        return result;
    }

    private static Map<String, List<String>> createRequestHeaders(String host, int port,
            boolean secure, ClientEndpointConfig clientEndpointConfiguration) {

        Map<String, List<String>> headers = new HashMap<>();
        List<Extension> extensions = clientEndpointConfiguration.getExtensions();
        List<String> subProtocols = clientEndpointConfiguration.getPreferredSubprotocols();
        Map<String, Object> userProperties = clientEndpointConfiguration.getUserProperties();

        if (userProperties.get(Constants.AUTHORIZATION_HEADER_NAME) != null) {
            List<String> authValues = new ArrayList<>(1);
            authValues.add((String) userProperties.get(Constants.AUTHORIZATION_HEADER_NAME));
            headers.put(Constants.AUTHORIZATION_HEADER_NAME, authValues);
        }

        // Host header
        List<String> hostValues = new ArrayList<>(1);
        if (port == 80 && !secure || port == 443 && secure) {
            // Default ports. Do not include port in host header
            hostValues.add(host);
        } else {
            hostValues.add(host + ':' + port);
        }

        headers.put(Constants.HOST_HEADER_NAME, hostValues);

        // Upgrade header
        List<String> upgradeValues = new ArrayList<>(1);
        upgradeValues.add(Constants.UPGRADE_HEADER_VALUE);
        headers.put(Constants.UPGRADE_HEADER_NAME, upgradeValues);

        // Connection header
        List<String> connectionValues = new ArrayList<>(1);
        connectionValues.add(Constants.CONNECTION_HEADER_VALUE);
        headers.put(Constants.CONNECTION_HEADER_NAME, connectionValues);

        // WebSocket version header
        List<String> wsVersionValues = new ArrayList<>(1);
        wsVersionValues.add(Constants.WS_VERSION_HEADER_VALUE);
        headers.put(Constants.WS_VERSION_HEADER_NAME, wsVersionValues);

        // WebSocket key
        List<String> wsKeyValues = new ArrayList<>(1);
        wsKeyValues.add(generateWsKeyValue());
        headers.put(Constants.WS_KEY_HEADER_NAME, wsKeyValues);

        // WebSocket sub-protocols
        if (subProtocols != null && subProtocols.size() > 0) {
            headers.put(Constants.WS_PROTOCOL_HEADER_NAME, subProtocols);
        }

        // WebSocket extensions
        if (extensions != null && extensions.size() > 0) {
            headers.put(Constants.WS_EXTENSIONS_HEADER_NAME,
                    generateExtensionHeaders(extensions));
        }

        return headers;
    }


    private static List<String> generateExtensionHeaders(List<Extension> extensions) {
        List<String> result = new ArrayList<>(extensions.size());
        for (Extension extension : extensions) {
            StringBuilder header = new StringBuilder();
            header.append(extension.getName());
            for (Extension.Parameter param : extension.getParameters()) {
                header.append(';');
                header.append(param.getName());
                String value = param.getValue();
                if (value != null && value.length() > 0) {
                    header.append('=');
                    header.append(value);
                }
            }
            result.add(header.toString());
        }
        return result;
    }


    private static String generateWsKeyValue() {
        byte[] keyBytes = new byte[16];
        RANDOM.nextBytes(keyBytes);
        return Base64.encodeBase64String(keyBytes);
    }


    private static ByteBuffer createRequest(URI uri, Map<String,List<String>> reqHeaders) {
        ByteBuffer result = ByteBuffer.allocate(4 * 1024);

        // Request line
        result.put(GET_BYTES);
        final String path = uri.getPath();
        if (null == path || path.isEmpty()) {
            result.put(ROOT_URI_BYTES);
        } else {
            result.put(uri.getRawPath().getBytes(StandardCharsets.ISO_8859_1));
        }
        String query = uri.getRawQuery();
        if (query != null) {
            result.put((byte) '?');
            result.put(query.getBytes(StandardCharsets.ISO_8859_1));
        }
        result.put(HTTP_VERSION_BYTES);

        // Headers
        for (Entry<String, List<String>> entry : reqHeaders.entrySet()) {
            result = addHeader(result, entry.getKey(), entry.getValue());
        }

        // Terminating CRLF
        result.put(CRLF);

        result.flip();

        return result;
    }


    private static ByteBuffer addHeader(ByteBuffer result, String key, List<String> values) {
        if (values.isEmpty()) {
            return result;
        }

        result = putWithExpand(result, key.getBytes(StandardCharsets.ISO_8859_1));
        result = putWithExpand(result, ": ".getBytes(StandardCharsets.ISO_8859_1));
        result = putWithExpand(result, StringUtils.join(values).getBytes(StandardCharsets.ISO_8859_1));
        result = putWithExpand(result, CRLF);

        return result;
    }


    private static ByteBuffer putWithExpand(ByteBuffer input, byte[] bytes) {
        if (bytes.length > input.remaining()) {
            int newSize;
            if (bytes.length > input.capacity()) {
                newSize = 2 * bytes.length;
            } else {
                newSize = input.capacity() * 2;
            }
            ByteBuffer expanded = ByteBuffer.allocate(newSize);
            input.flip();
            expanded.put(input);
            input = expanded;
        }
        return input.put(bytes);
    }


    /**
     * Process response, blocking until HTTP response has been fully received.
     * @throws ExecutionException
     * @throws InterruptedException
     * @throws DeploymentException
     * @throws TimeoutException
     */
    private HttpResponse processResponse(ByteBuffer response,
            AsyncChannelWrapper channel, long timeout) throws InterruptedException,
            ExecutionException, DeploymentException, EOFException,
            TimeoutException {

        Map<String,List<String>> headers = new CaseInsensitiveKeyMap<>();

        int status = 0;
        boolean readStatus = false;
        boolean readHeaders = false;
        String line = null;
        while (!readHeaders) {
            // On entering loop buffer will be empty and at the start of a new
            // loop the buffer will have been fully read.
            response.clear();
            // Blocking read
            Future<Integer> read = channel.read(response);
            Integer bytesRead;
            try {
                bytesRead = read.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                TimeoutException te = new TimeoutException(
                        sm.getString("wsWebSocketContainer.responseFail", Integer.toString(status), headers));
                te.initCause(e);
                throw te;
            }
            if (bytesRead.intValue() == -1) {
                throw new EOFException(sm.getString("wsWebSocketContainer.responseFail", Integer.toString(status), headers));
            }
            response.flip();
            while (response.hasRemaining() && !readHeaders) {
                if (line == null) {
                    line = readLine(response);
                } else {
                    line += readLine(response);
                }
                if ("\r\n".equals(line)) {
                    readHeaders = true;
                } else if (line.endsWith("\r\n")) {
                    if (readStatus) {
                        parseHeaders(line, headers);
                    } else {
                        status = parseStatus(line);
                        readStatus = true;
                    }
                    line = null;
                }
            }
        }

        return new HttpResponse(status, new WsHandshakeResponse(headers));
    }


    private int parseStatus(String line) throws DeploymentException {
        // This client only understands HTTP 1.
        // RFC2616 is case specific
        String[] parts = line.trim().split(" ");
        // CONNECT for proxy may return a 1.0 response
        if (parts.length < 2 || !("HTTP/1.0".equals(parts[0]) || "HTTP/1.1".equals(parts[0]))) {
            throw new DeploymentException(sm.getString(
                    "wsWebSocketContainer.invalidStatus", line));
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException nfe) {
            throw new DeploymentException(sm.getString(
                    "wsWebSocketContainer.invalidStatus", line));
        }
    }


    private void parseHeaders(String line, Map<String,List<String>> headers) {
        // Treat headers as single values by default.

        int index = line.indexOf(':');
        if (index == -1) {
            log.warn(sm.getString("wsWebSocketContainer.invalidHeader", line));
            return;
        }
        // Header names are case insensitive so always use lower case
        String headerName = line.substring(0, index).trim().toLowerCase(Locale.ENGLISH);
        // Multi-value headers are stored as a single header and the client is
        // expected to handle splitting into individual values
        String headerValue = line.substring(index + 1).trim();

        List<String> values = headers.get(headerName);
        if (values == null) {
            values = new ArrayList<>(1);
            headers.put(headerName, values);
        }
        values.add(headerValue);
    }

    private String readLine(ByteBuffer response) {
        // All ISO-8859-1
        StringBuilder sb = new StringBuilder();

        char c = 0;
        while (response.hasRemaining()) {
            c = (char) response.get();
            sb.append(c);
            if (c == 10) {
                break;
            }
        }

        return sb.toString();
    }


    private SSLEngine createSSLEngine(Map<String,Object> userProperties, String host, int port)
            throws DeploymentException {

        try {
            // See if a custom SSLContext has been provided
            SSLContext sslContext =
                    (SSLContext) userProperties.get(Constants.SSL_CONTEXT_PROPERTY);

            if (sslContext == null) {
                // Create the SSL Context
                sslContext = SSLContext.getInstance("TLS");

                // Trust store
                String sslTrustStoreValue =
                        (String) userProperties.get(Constants.SSL_TRUSTSTORE_PROPERTY);
                if (sslTrustStoreValue != null) {
                    String sslTrustStorePwdValue = (String) userProperties.get(
                            Constants.SSL_TRUSTSTORE_PWD_PROPERTY);
                    if (sslTrustStorePwdValue == null) {
                        sslTrustStorePwdValue = Constants.SSL_TRUSTSTORE_PWD_DEFAULT;
                    }

                    File keyStoreFile = new File(sslTrustStoreValue);
                    KeyStore ks = KeyStore.getInstance("JKS");
                    try (InputStream is = new FileInputStream(keyStoreFile)) {
                        KeyStoreUtil.load(ks, is, sslTrustStorePwdValue.toCharArray());
                    }

                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(ks);

                    sslContext.init(null, tmf.getTrustManagers(), null);
                } else {
                    sslContext.init(null, null, null);
                }
            }

            SSLEngine engine = sslContext.createSSLEngine(host, port);

            String sslProtocolsValue =
                    (String) userProperties.get(Constants.SSL_PROTOCOLS_PROPERTY);
            if (sslProtocolsValue != null) {
                engine.setEnabledProtocols(sslProtocolsValue.split(","));
            }

            engine.setUseClientMode(true);

            // Enable host verification
            // Start with current settings (returns a copy)
            SSLParameters sslParams = engine.getSSLParameters();
            // Use HTTPS since WebSocket starts over HTTP(S)
            sslParams.setEndpointIdentificationAlgorithm("HTTPS");
            // Write the parameters back
            engine.setSSLParameters(sslParams);

            return engine;
        } catch (Exception e) {
            throw new DeploymentException(sm.getString(
                    "wsWebSocketContainer.sslEngineFail"), e);
        }
    }


    @Override
    public long getDefaultMaxSessionIdleTimeout() {
        return defaultMaxSessionIdleTimeout;
    }


    @Override
    public void setDefaultMaxSessionIdleTimeout(long timeout) {
        this.defaultMaxSessionIdleTimeout = timeout;
    }


    @Override
    public int getDefaultMaxBinaryMessageBufferSize() {
        return maxBinaryMessageBufferSize;
    }


    @Override
    public void setDefaultMaxBinaryMessageBufferSize(int max) {
        maxBinaryMessageBufferSize = max;
    }


    @Override
    public int getDefaultMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }


    @Override
    public void setDefaultMaxTextMessageBufferSize(int max) {
        maxTextMessageBufferSize = max;
    }


    /**
     * {@inheritDoc}
     *
     * Currently, this implementation does not support any extensions.
     */
    @Override
    public Set<Extension> getInstalledExtensions() {
        return Collections.emptySet();
    }


    /**
     * {@inheritDoc}
     *
     * The default value for this implementation is -1.
     */
    @Override
    public long getDefaultAsyncSendTimeout() {
        return defaultAsyncTimeout;
    }


    /**
     * {@inheritDoc}
     *
     * The default value for this implementation is -1.
     */
    @Override
    public void setAsyncSendTimeout(long timeout) {
        this.defaultAsyncTimeout = timeout;
    }


    /**
     * Cleans up the resources still in use by WebSocket sessions created from
     * this container. This includes closing sessions and cancelling
     * {@link Future}s associated with blocking read/writes.
     */
    public void destroy() {
        CloseReason cr = new CloseReason(
                CloseCodes.GOING_AWAY, sm.getString("wsWebSocketContainer.shutdown"));

        for (WsSession session : sessions.keySet()) {
            try {
                session.close(cr);
            } catch (IOException ioe) {
                log.debug(sm.getString(
                        "wsWebSocketContainer.sessionCloseFail", session.getId()), ioe);
            }
        }

        // Only unregister with AsyncChannelGroupUtil if this instance
        // registered with it
        if (asynchronousChannelGroup != null) {
            synchronized (asynchronousChannelGroupLock) {
                if (asynchronousChannelGroup != null) {
                    AsyncChannelGroupUtil.unregister();
                    asynchronousChannelGroup = null;
                }
            }
        }
    }


    private AsynchronousChannelGroup getAsynchronousChannelGroup() {
        // Use AsyncChannelGroupUtil to share a common group amongst all
        // WebSocket clients
        AsynchronousChannelGroup result = asynchronousChannelGroup;
        if (result == null) {
            synchronized (asynchronousChannelGroupLock) {
                if (asynchronousChannelGroup == null) {
                    asynchronousChannelGroup = AsyncChannelGroupUtil.register();
                }
                result = asynchronousChannelGroup;
            }
        }
        return result;
    }


    // ----------------------------------------------- BackgroundProcess methods

    @Override
    public void backgroundProcess() {
        // This method gets called once a second.
        backgroundProcessCount ++;
        if (backgroundProcessCount >= processPeriod) {
            backgroundProcessCount = 0;

            for (WsSession wsSession : sessions.keySet()) {
                wsSession.checkExpiration();
            }
        }

    }


    @Override
    public void setProcessPeriod(int period) {
        this.processPeriod = period;
    }


    /**
     * {@inheritDoc}
     *
     * The default value is 10 which means session expirations are processed
     * every 10 seconds.
     */
    @Override
    public int getProcessPeriod() {
        return processPeriod;
    }


    private static class HttpResponse {
        private final int status;
        private final HandshakeResponse handshakeResponse;

        public HttpResponse(int status, HandshakeResponse handshakeResponse) {
            this.status = status;
            this.handshakeResponse = handshakeResponse;
        }


        public int getStatus() {
            return status;
        }


        public HandshakeResponse getHandshakeResponse() {
            return handshakeResponse;
        }
    }
}
