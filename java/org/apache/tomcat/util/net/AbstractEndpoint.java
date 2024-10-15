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
package org.apache.tomcat.util.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.Acceptor.AcceptorState;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.StoreType;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.LimitLatch;
import org.apache.tomcat.util.threads.ResizableExecutor;
import org.apache.tomcat.util.threads.TaskQueue;
import org.apache.tomcat.util.threads.TaskThreadFactory;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.apache.tomcat.util.threads.VirtualThreadExecutor;

/**
 * @param <S> The type used by the socket wrapper associated with this endpoint.
 *            May be the same as U.
 * @param <U> The type of the underlying socket used by this endpoint. May be
 *            the same as S.
 *
 * @author Mladen Turk
 * @author Remy Maucherat
 */
public abstract class AbstractEndpoint<S,U> {

    // -------------------------------------------------------------- Constants

    protected static final StringManager sm = StringManager.getManager(AbstractEndpoint.class);

    public interface Handler<S> {

        /**
         * Different types of socket states to react upon.
         */
        enum SocketState {
            // TODO Add a new state to the AsyncStateMachine and remove
            //      ASYNC_END (if possible)
            OPEN, CLOSED, LONG, ASYNC_END, SENDFILE, UPGRADING, UPGRADED, ASYNC_IO, SUSPENDED
        }


        /**
         * Process the provided socket with the given current status.
         *
         * @param socket The socket to process
         * @param status The current socket status
         *
         * @return The state of the socket after processing
         */
        SocketState process(SocketWrapperBase<S> socket,
                SocketEvent status);


        /**
         * Obtain the GlobalRequestProcessor associated with the handler.
         *
         * @return the GlobalRequestProcessor
         */
        Object getGlobal();


        /**
         * Release any resources associated with the given SocketWrapper.
         *
         * @param socketWrapper The socketWrapper to release resources for
         */
        void release(SocketWrapperBase<S> socketWrapper);


        /**
         * Inform the handler that the endpoint has stopped accepting any new
         * connections. Typically, the endpoint will be stopped shortly
         * afterwards but it is possible that the endpoint will be resumed so
         * the handler should not assume that a stop will follow.
         */
        void pause();


        /**
         * Recycle resources associated with the handler.
         */
        void recycle();
    }

    protected enum BindState {
        UNBOUND(false, false),
        BOUND_ON_INIT(true, true),
        BOUND_ON_START(true, true),
        SOCKET_CLOSED_ON_STOP(false, true);

        private final boolean bound;
        private final boolean wasBound;

        BindState(boolean bound, boolean wasBound) {
            this.bound = bound;
            this.wasBound = wasBound;
        }

        public boolean isBound() {
            return bound;
        }

        public boolean wasBound() {
            return wasBound;
        }
    }


    public static long toTimeout(long timeout) {
        // Many calls can't do infinite timeout so use Long.MAX_VALUE if timeout is <= 0
        return (timeout > 0) ? timeout : Long.MAX_VALUE;
    }

    // ----------------------------------------------------------------- Fields

    /**
     * Running state of the endpoint.
     */
    protected volatile boolean running = false;


    /**
     * Will be set to true whenever the endpoint is paused.
     */
    protected volatile boolean paused = false;

    /**
     * Are we using an internal executor
     */
    protected volatile boolean internalExecutor = true;


    /**
     * counter for nr of connections handled by an endpoint
     */
    private volatile LimitLatch connectionLimitLatch = null;

    /**
     * Socket properties
     */
    protected final SocketProperties socketProperties = new SocketProperties();
    public SocketProperties getSocketProperties() {
        return socketProperties;
    }

    /**
     * Thread used to accept new connections and pass them to worker threads.
     */
    protected Acceptor<U> acceptor;

    /**
     * Cache for SocketProcessor objects
     */
    protected SynchronizedStack<SocketProcessorBase<S>> processorCache;

    private ObjectName oname = null;

    /**
     * Map holding all current connections keyed with the sockets.
     */
    protected Map<U, SocketWrapperBase<S>> connections = new ConcurrentHashMap<>();

    /**
     * Get a set with the current open connections.
     * @return A set with the open socket wrappers
     */
    public Set<SocketWrapperBase<S>> getConnections() {
        return new HashSet<>(connections.values());
    }

    private SSLImplementation sslImplementation = null;
    public SSLImplementation getSslImplementation() {
        return sslImplementation;
    }


    // ----------------------------------------------------------------- Properties

    private String sslImplementationName = null;
    public String getSslImplementationName() {
        return sslImplementationName;
    }
    public void setSslImplementationName(String s) {
        this.sslImplementationName = s;
    }


    private int sniParseLimit = 64 * 1024;
    public int getSniParseLimit() {
        return sniParseLimit;
    }
    public void setSniParseLimit(int sniParseLimit) {
        this.sniParseLimit = sniParseLimit;
    }


    private String defaultSSLHostConfigName = SSLHostConfig.DEFAULT_SSL_HOST_NAME;
    /**
     * @return The host name for the default SSL configuration for this endpoint
     *         - always in lower case.
     */
    public String getDefaultSSLHostConfigName() {
        return defaultSSLHostConfigName;
    }
    public void setDefaultSSLHostConfigName(String defaultSSLHostConfigName) {
        this.defaultSSLHostConfigName = defaultSSLHostConfigName.toLowerCase(Locale.ENGLISH);
    }


    protected ConcurrentMap<String,SSLHostConfig> sslHostConfigs = new ConcurrentHashMap<>();
    /**
     * Add the given SSL Host configuration.
     *
     * @param sslHostConfig The configuration to add
     *
     * @throws IllegalArgumentException If the host name is not valid or if a
     *                                  configuration has already been provided
     *                                  for that host
     */
    public void addSslHostConfig(SSLHostConfig sslHostConfig) throws IllegalArgumentException {
        addSslHostConfig(sslHostConfig, false);
    }
    /**
     * Add the given SSL Host configuration, optionally replacing the existing
     * configuration for the given host.
     *
     * @param sslHostConfig The configuration to add
     * @param replace       If {@code true} replacement of an existing
     *                      configuration is permitted, otherwise any such
     *                      attempted replacement will trigger an exception
     *
     * @throws IllegalArgumentException If the host name is not valid or if a
     *                                  configuration has already been provided
     *                                  for that host and replacement is not
     *                                  allowed
     */
    public void addSslHostConfig(SSLHostConfig sslHostConfig, boolean replace) throws IllegalArgumentException {
        String key = sslHostConfig.getHostName();
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException(sm.getString("endpoint.noSslHostName"));
        }
        if (bindState != BindState.UNBOUND && bindState != BindState.SOCKET_CLOSED_ON_STOP &&
                isSSLEnabled()) {
            try {
                createSSLContext(sslHostConfig);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        if (replace) {
            SSLHostConfig previous = sslHostConfigs.put(key, sslHostConfig);
            if (previous != null) {
                unregisterJmx(sslHostConfig);
            }
            registerJmx(sslHostConfig);

            // Do not release any SSLContexts associated with a replaced
            // SSLHostConfig. They may still be in used by existing connections
            // and releasing them would break the connection at best. Let GC
            // handle the clean up.
        } else {
            SSLHostConfig duplicate = sslHostConfigs.putIfAbsent(key, sslHostConfig);
            if (duplicate != null) {
                releaseSSLContext(sslHostConfig);
                throw new IllegalArgumentException(sm.getString("endpoint.duplicateSslHostName", key));
            }
            registerJmx(sslHostConfig);
        }
    }
    /**
     * Removes the SSL host configuration for the given host name, if such a
     * configuration exists.
     *
     * @param hostName  The host name associated with the SSL host configuration
     *                  to remove
     *
     * @return  The SSL host configuration that was removed, if any
     */
    public SSLHostConfig removeSslHostConfig(String hostName) {
        if (hostName == null) {
            return null;
        }
        // Host names are case insensitive but stored/processed in lower case
        // internally because they are used as keys in a ConcurrentMap where
        // keys are compared in a case sensitive manner.
        String hostNameLower = hostName.toLowerCase(Locale.ENGLISH);
        if (hostNameLower.equals(getDefaultSSLHostConfigName())) {
            throw new IllegalArgumentException(
                    sm.getString("endpoint.removeDefaultSslHostConfig", hostName));
        }
        SSLHostConfig sslHostConfig = sslHostConfigs.remove(hostNameLower);
        unregisterJmx(sslHostConfig);
        return sslHostConfig;
    }
    /**
     * Re-read the configuration files for the SSL host and replace the existing
     * SSL configuration with the updated settings. Note this replacement will
     * happen even if the settings remain unchanged.
     *
     * @param hostName The SSL host for which the configuration should be
     *                 reloaded. This must match a current SSL host
     */
    public void reloadSslHostConfig(String hostName) {
        // Host names are case insensitive but stored/processed in lower case
        // internally because they are used as keys in a ConcurrentMap where
        // keys are compared in a case sensitive manner.
        // This method can be called via various paths so convert the supplied
        // host name to lower case here to ensure the conversion occurs whatever
        // the call path.
        SSLHostConfig sslHostConfig = sslHostConfigs.get(hostName.toLowerCase(Locale.ENGLISH));
        if (sslHostConfig == null) {
            throw new IllegalArgumentException(
                    sm.getString("endpoint.unknownSslHostName", hostName));
        }
        addSslHostConfig(sslHostConfig, true);
    }
    /**
     * Re-read the configuration files for all SSL hosts and replace the
     * existing SSL configuration with the updated settings. Note this
     * replacement will happen even if the settings remain unchanged.
     */
    public void reloadSslHostConfigs() {
        for (String hostName : sslHostConfigs.keySet()) {
            reloadSslHostConfig(hostName);
        }
    }
    public SSLHostConfig[] findSslHostConfigs() {
        return sslHostConfigs.values().toArray(new SSLHostConfig[0]);
    }

    /**
     * Create the SSLContext for the given SSLHostConfig.
     *
     * @param sslHostConfig The SSLHostConfig for which the SSLContext should be
     *                      created
     * @throws IllegalArgumentException If the SSLContext cannot be created for
     *                      the given SSLHostConfig
     */
    protected void createSSLContext(SSLHostConfig sslHostConfig) throws IllegalArgumentException {

        // HTTP/2 does not permit optional certificate authentication with any
        // version of TLS.
        if (sslHostConfig.getCertificateVerification().isOptional() &&
                negotiableProtocols.contains("h2")) {
            getLog().warn(sm.getString("sslHostConfig.certificateVerificationWithHttp2", sslHostConfig.getHostName()));
        }

        boolean firstCertificate = true;
        for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
            SSLUtil sslUtil = sslImplementation.getSSLUtil(certificate);
            if (firstCertificate) {
                firstCertificate = false;
                sslHostConfig.setEnabledProtocols(sslUtil.getEnabledProtocols());
                sslHostConfig.setEnabledCiphers(sslUtil.getEnabledCiphers());
            }

            SSLContext sslContext = certificate.getSslContext();
            SSLContext sslContextGenerated = certificate.getSslContextGenerated();
            // Generate the SSLContext from configuration unless (e.g. embedded) an SSLContext has been provided.
            // Need to handle both initial configuration and reload.
            // Initial, SSLContext provided     - sslContext will be non-null and sslContextGenerated will be null
            // Initial, SSLContext not provided - sslContext null and sslContextGenerated will be null
            // Reload,  SSLContext provided     - sslContext will be non-null and sslContextGenerated will be null
            // Reload,  SSLContext not provided - sslContext non-null and equal to sslContextGenerated
            if (sslContext == null || sslContext == sslContextGenerated) {
                try {
                    sslContext = sslUtil.createSSLContext(negotiableProtocols);
                } catch (Exception e) {
                    throw new IllegalArgumentException(sm.getString("endpoint.errorCreatingSSLContext"), e);
                }

                certificate.setSslContextGenerated(sslContext);
            }

            logCertificate(certificate);
        }
    }


    protected void logCertificate(SSLHostConfigCertificate certificate) {
        SSLHostConfig sslHostConfig = certificate.getSSLHostConfig();

        String certificateInfo;

        if (certificate.getStoreType() == StoreType.PEM) {
            // PEM file based
            certificateInfo = sm.getString("endpoint.tls.info.cert.pem", certificate.getCertificateKeyFile(),
                    certificate.getCertificateFile(), certificate.getCertificateChainFile());
        } else {
            // Keystore based
            String keyAlias = certificate.getCertificateKeyAlias();
            if (keyAlias == null) {
                keyAlias = SSLUtilBase.DEFAULT_KEY_ALIAS;
            }
            certificateInfo =
                    sm.getString("endpoint.tls.info.cert.keystore", certificate.getCertificateKeystoreFile(), keyAlias);
        }

        String trustStoreSource = sslHostConfig.getTruststoreFile();
        if (trustStoreSource == null) {
            trustStoreSource = sslHostConfig.getCaCertificateFile();
        }
        if (trustStoreSource == null) {
            trustStoreSource = sslHostConfig.getCaCertificatePath();
        }

        getLogCertificate().info(sm.getString("endpoint.tls.info", getName(), sslHostConfig.getHostName(),
                certificate.getType(), certificateInfo, trustStoreSource));

        if (getLogCertificate().isDebugEnabled()) {
            String alias = certificate.getCertificateKeyAlias();
            if (alias == null) {
                alias = SSLUtilBase.DEFAULT_KEY_ALIAS;
            }
            X509Certificate[] x509Certificates = certificate.getSslContext().getCertificateChain(alias);
            if (x509Certificates != null && x509Certificates.length > 0) {
                getLogCertificate().debug(generateCertificateDebug(x509Certificates[0]));
            } else {
                getLogCertificate().debug(sm.getString("endpoint.tls.cert.noCerts"));
            }
        }
    }


    protected String generateCertificateDebug(X509Certificate certificate) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[");
        try {
            byte[] certBytes = certificate.getEncoded();
            // SHA-256 fingerprint
            sb.append("\nSHA-256 fingerprint: ");
            MessageDigest sha512Digest = MessageDigest.getInstance("SHA-256");
            sha512Digest.update(certBytes);
            sb.append(HexUtils.toHexString(sha512Digest.digest()));
            // SHA-1 fingerprint
            sb.append("\nSHA-1 fingerprint: ");
            MessageDigest sha1Digest = MessageDigest.getInstance("SHA-1");
            sha1Digest.update(certBytes);
            sb.append(HexUtils.toHexString(sha1Digest.digest()));
        } catch (CertificateEncodingException e) {
            getLogCertificate().warn(sm.getString("endpoint.tls.cert.encodingError"), e);
        } catch (NoSuchAlgorithmException e) {
            // Unreachable code
            // All JREs are required to support SHA-1 and SHA-256
            throw new RuntimeException(e);
        }
        sb.append("\n");
        sb.append(certificate);
        sb.append("\n]");
        return sb.toString();
    }

    protected SSLEngine createSSLEngine(String sniHostName, List<Cipher> clientRequestedCiphers,
            List<String> clientRequestedApplicationProtocols) {
        SSLHostConfig sslHostConfig = getSSLHostConfig(sniHostName);

        SSLHostConfigCertificate certificate = selectCertificate(sslHostConfig, clientRequestedCiphers);

        SSLContext sslContext = certificate.getSslContext();
        if (sslContext == null) {
            throw new IllegalStateException(
                    sm.getString("endpoint.jsse.noSslContext", sniHostName));
        }

        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setEnabledCipherSuites(sslHostConfig.getEnabledCiphers());
        engine.setEnabledProtocols(sslHostConfig.getEnabledProtocols());

        SSLParameters sslParameters = engine.getSSLParameters();
        sslParameters.setUseCipherSuitesOrder(sslHostConfig.getHonorCipherOrder());
        if (clientRequestedApplicationProtocols != null
                && clientRequestedApplicationProtocols.size() > 0
                && negotiableProtocols.size() > 0) {
            // Only try to negotiate if both client and server have at least
            // one protocol in common
            // Note: Tomcat does not explicitly negotiate http/1.1
            List<String> commonProtocols = new ArrayList<>(negotiableProtocols);
            commonProtocols.retainAll(clientRequestedApplicationProtocols);
            if (commonProtocols.size() > 0) {
                String[] commonProtocolsArray = commonProtocols.toArray(new String[0]);
                sslParameters.setApplicationProtocols(commonProtocolsArray);
            }
        }
        switch (sslHostConfig.getCertificateVerification()) {
        case NONE:
            sslParameters.setNeedClientAuth(false);
            sslParameters.setWantClientAuth(false);
            break;
        case OPTIONAL:
        case OPTIONAL_NO_CA:
            sslParameters.setWantClientAuth(true);
            break;
        case REQUIRED:
            sslParameters.setNeedClientAuth(true);
            break;
        }
        // The getter (at least in OpenJDK and derivatives) returns a defensive copy
        engine.setSSLParameters(sslParameters);

        return engine;
    }


    private SSLHostConfigCertificate selectCertificate(
            SSLHostConfig sslHostConfig, List<Cipher> clientCiphers) {

        Set<SSLHostConfigCertificate> certificates = sslHostConfig.getCertificates(true);
        if (certificates.size() == 1) {
            return certificates.iterator().next();
        }

        LinkedHashSet<Cipher> serverCiphers = sslHostConfig.getCipherList();

        List<Cipher> candidateCiphers = new ArrayList<>();
        if (sslHostConfig.getHonorCipherOrder()) {
            candidateCiphers.addAll(serverCiphers);
            candidateCiphers.retainAll(clientCiphers);
        } else {
            candidateCiphers.addAll(clientCiphers);
            candidateCiphers.retainAll(serverCiphers);
        }

        for (Cipher candidate : candidateCiphers) {
            for (SSLHostConfigCertificate certificate : certificates) {
                if (certificate.getType().isCompatibleWith(candidate.getAu())) {
                    return certificate;
                }
            }
        }

        // No matches. Just return the first certificate. The handshake will
        // then fail due to no matching ciphers.
        return certificates.iterator().next();
    }


    protected void initialiseSsl() throws Exception {
        if (isSSLEnabled()) {
            sslImplementation = SSLImplementation.getInstance(getSslImplementationName());

            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
                createSSLContext(sslHostConfig);
            }

            // Validate default SSLHostConfigName
            if (sslHostConfigs.get(getDefaultSSLHostConfigName()) == null) {
                throw new IllegalArgumentException(sm.getString("endpoint.noSslHostConfig",
                        getDefaultSSLHostConfigName(), getName()));
            }

        }
    }


    protected void destroySsl() throws Exception {
        if (isSSLEnabled()) {
            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
                releaseSSLContext(sslHostConfig);
            }
        }
    }


    /**
     * Release the SSLContext, if any, associated with the SSLHostConfig.
     *
     * @param sslHostConfig The SSLHostConfig for which the SSLContext should be
     *                      released
     */
    protected void releaseSSLContext(SSLHostConfig sslHostConfig) {
        for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates()) {
            if (certificate.getSslContext() != null) {
                // Only release the SSLContext if we generated it.
                SSLContext sslContext = certificate.getSslContextGenerated();
                if (sslContext != null) {
                    sslContext.destroy();
                }
            }
        }
    }


    /**
     * Look up the SSLHostConfig for the given host name. Lookup order is:
     * <ol>
     * <li>exact match</li>
     * <li>wild card match</li>
     * <li>default SSLHostConfig</li>
     * </ol>
     *
     * @param sniHostName   Host name - must be in lower case
     *
     * @return The SSLHostConfig for the given host name.
     */
    protected SSLHostConfig getSSLHostConfig(String sniHostName) {
        SSLHostConfig result = null;

        if (sniHostName != null) {
            // First choice - direct match
            result = sslHostConfigs.get(sniHostName);
            if (result != null) {
                return result;
            }
            // Second choice, wildcard match
            int indexOfDot = sniHostName.indexOf('.');
            if (indexOfDot > -1) {
                result = sslHostConfigs.get("*" + sniHostName.substring(indexOfDot));
            }
        }

        // Fall-back. Use the default
        if (result == null) {
            result = sslHostConfigs.get(getDefaultSSLHostConfigName());
        }
        if (result == null) {
            // Should never happen.
            throw new IllegalStateException();
        }
        return result;
    }


    /**
     * Has the user requested that send file be used where possible?
     */
    private boolean useSendfile = true;
    public boolean getUseSendfile() {
        return useSendfile;
    }
    public void setUseSendfile(boolean useSendfile) {
        this.useSendfile = useSendfile;
    }


    /**
     * Time to wait for the internal executor (if used) to terminate when the
     * endpoint is stopped in milliseconds. Defaults to 5000 (5 seconds).
     */
    private long executorTerminationTimeoutMillis = 5000;

    public long getExecutorTerminationTimeoutMillis() {
        return executorTerminationTimeoutMillis;
    }

    public void setExecutorTerminationTimeoutMillis(
            long executorTerminationTimeoutMillis) {
        this.executorTerminationTimeoutMillis = executorTerminationTimeoutMillis;
    }


    /**
     * Priority of the acceptor threads.
     */
    protected int acceptorThreadPriority = Thread.NORM_PRIORITY;
    public void setAcceptorThreadPriority(int acceptorThreadPriority) {
        this.acceptorThreadPriority = acceptorThreadPriority;
    }
    public int getAcceptorThreadPriority() { return acceptorThreadPriority; }


    private int maxConnections = 8*1024;
    public void setMaxConnections(int maxCon) {
        this.maxConnections = maxCon;
        LimitLatch latch = this.connectionLimitLatch;
        if (latch != null) {
            // Update the latch that enforces this
            if (maxCon == -1) {
                releaseConnectionLatch();
            } else {
                latch.setLimit(maxCon);
            }
        } else if (maxCon > 0) {
            initializeConnectionLatch();
        }
    }
    public int getMaxConnections() { return this.maxConnections; }

    /**
     * Return the current count of connections handled by this endpoint, if the
     * connections are counted (which happens when the maximum count of
     * connections is limited), or <code>-1</code> if they are not. This
     * property is added here so that this value can be inspected through JMX.
     * It is visible on "ThreadPool" MBean.
     *
     * <p>The count is incremented by the Acceptor before it tries to accept a
     * new connection. Until the limit is reached and thus the count cannot be
     * incremented,  this value is more by 1 (the count of acceptors) than the
     * actual count of connections that are being served.
     *
     * @return The count
     */
    public long getConnectionCount() {
        LimitLatch latch = connectionLimitLatch;
        if (latch != null) {
            return latch.getCount();
        }
        return -1;
    }

    /**
     * External Executor based thread pool.
     */
    private Executor executor = null;
    public void setExecutor(Executor executor) {
        this.executor = executor;
        this.internalExecutor = (executor == null);
    }
    public Executor getExecutor() { return executor; }


    private boolean useVirtualThreads = false;
    public void setUseVirtualThreads(boolean useVirtualThreads) {
        this.useVirtualThreads = useVirtualThreads;
    }
    public boolean getUseVirtualThreads() {
        return useVirtualThreads;
    }


    /**
     * External Executor based thread pool for utility tasks.
     */
    private ScheduledExecutorService utilityExecutor = null;
    public void setUtilityExecutor(ScheduledExecutorService utilityExecutor) {
        this.utilityExecutor = utilityExecutor;
    }
    public ScheduledExecutorService getUtilityExecutor() {
        if (utilityExecutor == null) {
            getLog().warn(sm.getString("endpoint.warn.noUtilityExecutor"));
            utilityExecutor = new ScheduledThreadPoolExecutor(1);
        }
        return utilityExecutor;
    }


    /**
     * Server socket port.
     */
    private int port = -1;
    public int getPort() { return port; }
    public void setPort(int port ) { this.port=port; }


    private int portOffset = 0;
    public int getPortOffset() { return portOffset; }
    public void setPortOffset(int portOffset ) {
        if (portOffset < 0) {
            throw new IllegalArgumentException(
                    sm.getString("endpoint.portOffset.invalid", Integer.valueOf(portOffset)));
        }
        this.portOffset = portOffset;
    }


    public int getPortWithOffset() {
        // Zero is a special case and negative values are invalid
        int port = getPort();
        if (port > 0) {
            return port + getPortOffset();
        }
        return port;
    }


    public final int getLocalPort() {
        try {
            InetSocketAddress localAddress = getLocalAddress();
            if (localAddress == null) {
                return -1;
            }
            return localAddress.getPort();
        } catch (IOException ioe) {
            return -1;
        }
    }


    /**
     * Address for the server socket.
     */
    private InetAddress address;
    public InetAddress getAddress() { return address; }
    public void setAddress(InetAddress address) { this.address = address; }


    /**
     * Obtain the network address the server socket is bound to. This primarily
     * exists to enable the correct address to be used when unlocking the server
     * socket since it removes the guess-work involved if no address is
     * specifically set.
     *
     * @return The network address that the server socket is listening on or
     *         null if the server socket is not currently bound.
     *
     * @throws IOException If there is a problem determining the currently bound
     *                     socket
     */
    protected abstract InetSocketAddress getLocalAddress() throws IOException;


    /**
     * Allows the server developer to specify the acceptCount (backlog) that
     * should be used for server sockets. By default, this value
     * is 100.
     */
    private int acceptCount = 100;
    public void setAcceptCount(int acceptCount) { if (acceptCount > 0) {
        this.acceptCount = acceptCount;
    } }
    public int getAcceptCount() { return acceptCount; }

    /**
     * Controls when the Endpoint binds the port. <code>true</code>, the default
     * binds the port on {@link #init()} and unbinds it on {@link #destroy()}.
     * If set to <code>false</code> the port is bound on {@link #start()} and
     * unbound on {@link #stop()}.
     */
    private boolean bindOnInit = true;
    public boolean getBindOnInit() { return bindOnInit; }
    public void setBindOnInit(boolean b) { this.bindOnInit = b; }
    private volatile BindState bindState = BindState.UNBOUND;
    protected BindState getBindState() {
        return bindState;
    }

    /**
     * Keepalive timeout, if not set the soTimeout is used.
     */
    private Integer keepAliveTimeout = null;
    public int getKeepAliveTimeout() {
        if (keepAliveTimeout == null) {
            return getConnectionTimeout();
        } else {
            return keepAliveTimeout.intValue();
        }
    }
    public void setKeepAliveTimeout(int keepAliveTimeout) {
        this.keepAliveTimeout = Integer.valueOf(keepAliveTimeout);
    }


    /**
     * Socket TCP no delay.
     *
     * @return The current TCP no delay setting for sockets created by this
     *         endpoint
     */
    public boolean getTcpNoDelay() { return socketProperties.getTcpNoDelay();}
    public void setTcpNoDelay(boolean tcpNoDelay) { socketProperties.setTcpNoDelay(tcpNoDelay); }


    /**
     * Socket linger.
     *
     * @return The current socket linger time for sockets created by this
     *         endpoint
     */
    public int getConnectionLinger() { return socketProperties.getSoLingerTime(); }
    public void setConnectionLinger(int connectionLinger) {
        socketProperties.setSoLingerTime(connectionLinger);
        socketProperties.setSoLingerOn(connectionLinger>=0);
    }


    /**
     * Socket timeout.
     *
     * @return The current socket timeout for sockets created by this endpoint
     */
    public int getConnectionTimeout() { return socketProperties.getSoTimeout(); }
    public void setConnectionTimeout(int soTimeout) { socketProperties.setSoTimeout(soTimeout); }

    /**
     * SSL engine.
     */
    private boolean SSLEnabled = false;
    public boolean isSSLEnabled() { return SSLEnabled; }
    public void setSSLEnabled(boolean SSLEnabled) { this.SSLEnabled = SSLEnabled; }

    private int minSpareThreads = 10;
    public void setMinSpareThreads(int minSpareThreads) {
        this.minSpareThreads = minSpareThreads;
        Executor executor = this.executor;
        if (internalExecutor && executor instanceof ThreadPoolExecutor) {
            // The internal executor should always be an instance of
            // org.apache.tomcat.util.threads.ThreadPoolExecutor but it may be
            // null if the endpoint is not running.
            // This check also avoids various threading issues.
            ((ThreadPoolExecutor) executor).setCorePoolSize(minSpareThreads);
        }
    }
    public int getMinSpareThreads() {
        return Math.min(getMinSpareThreadsInternal(), getMaxThreads());
    }
    private int getMinSpareThreadsInternal() {
        if (internalExecutor) {
            return minSpareThreads;
        } else {
            return -1;
        }
    }


    /**
     * Maximum amount of worker threads.
     */
    private int maxThreads = 200;
    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        Executor executor = this.executor;
        if (internalExecutor && executor instanceof ThreadPoolExecutor) {
            // The internal executor should always be an instance of
            // org.apache.tomcat.util.threads.ThreadPoolExecutor but it may be
            // null if the endpoint is not running.
            // This check also avoids various threading issues.
            ((ThreadPoolExecutor) executor).setMaximumPoolSize(maxThreads);
        }
    }
    public int getMaxThreads() {
        if (internalExecutor) {
            return maxThreads;
        } else {
            return -1;
        }
    }


    /**
     * Task queue capacity for the thread pool.
     */
    private int maxQueueSize = Integer.MAX_VALUE;
    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }
    public int getMaxQueueSize() {
        if (internalExecutor) {
            return maxQueueSize;
        } else {
            return -1;
        }
    }


    /**
     * Amount of time in milliseconds before the internal thread pool stops any idle threads
     * if the amount of thread is greater than the minimum amount of spare threads.
     */
    private int threadsMaxIdleTime = 60000;
    public void setThreadsMaxIdleTime(int threadsMaxIdleTime) {
        this.threadsMaxIdleTime = threadsMaxIdleTime;
        Executor executor = this.executor;
        if (internalExecutor && executor instanceof ThreadPoolExecutor) {
            // The internal executor should always be an instance of
            // org.apache.tomcat.util.threads.ThreadPoolExecutor but it may be
            // null if the endpoint is not running.
            // This check also avoids various threading issues.
            ((ThreadPoolExecutor) executor).setKeepAliveTime(threadsMaxIdleTime, TimeUnit.MILLISECONDS);
        }
    }
    public int getThreadsMaxIdleTime() {
        if (internalExecutor) {
            return threadsMaxIdleTime;
        } else {
            return -1;
        }
    }

    /**
     * Priority of the worker threads.
     */
    protected int threadPriority = Thread.NORM_PRIORITY;
    public void setThreadPriority(int threadPriority) {
        // Can't change this once the executor has started
        this.threadPriority = threadPriority;
    }
    public int getThreadPriority() {
        if (internalExecutor) {
            return threadPriority;
        } else {
            return -1;
        }
    }


    /**
     * Max keep alive requests
     */
    private int maxKeepAliveRequests=100; // as in Apache HTTPD server
    public int getMaxKeepAliveRequests() {
        // Disable keep-alive if the server socket is not bound
        if (bindState.isBound()) {
            return maxKeepAliveRequests;
        } else {
            return 1;
        }
    }
    public void setMaxKeepAliveRequests(int maxKeepAliveRequests) {
        this.maxKeepAliveRequests = maxKeepAliveRequests;
    }


    /**
     * Name of the thread pool, which will be used for naming child threads.
     */
    private String name = "TP";
    public void setName(String name) { this.name = name; }
    public String getName() { return name; }


    /**
     * Name of domain to use for JMX registration.
     */
    private String domain;
    public void setDomain(String domain) { this.domain = domain; }
    public String getDomain() { return domain; }


    /**
     * The default is true - the created threads will be
     *  in daemon mode. If set to false, the control thread
     *  will not be daemon - and will keep the process alive.
     */
    private boolean daemon = true;
    public void setDaemon(boolean b) { daemon = b; }
    public boolean getDaemon() { return daemon; }


    /**
     * Expose asynchronous IO capability.
     */
    private boolean useAsyncIO = true;
    public void setUseAsyncIO(boolean useAsyncIO) { this.useAsyncIO = useAsyncIO; }
    public boolean getUseAsyncIO() { return useAsyncIO; }


    /**
     * The default behavior is to identify connectors uniquely with address
     * and port. However, certain connectors are not using that and need
     * some other identifier, which then can be used as a replacement.
     * @return the id
     */
    public String getId() {
        return null;
    }


    protected final List<String> negotiableProtocols = new ArrayList<>();
    public void addNegotiatedProtocol(String negotiableProtocol) {
        negotiableProtocols.add(negotiableProtocol);
    }
    public boolean hasNegotiableProtocols() {
        return (negotiableProtocols.size() > 0);
    }


    /**
     * Handling of accepted sockets.
     */
    private Handler<S> handler = null;
    public void setHandler(Handler<S> handler ) { this.handler = handler; }
    public Handler<S> getHandler() { return handler; }


    /**
     * Attributes provide a way for configuration to be passed to sub-components
     * without the {@link org.apache.coyote.ProtocolHandler} being aware of the
     * properties available on those sub-components.
     */
    protected HashMap<String, Object> attributes = new HashMap<>();

    /**
     * Generic property setter called when a property for which a specific
     * setter already exists within the
     * {@link org.apache.coyote.ProtocolHandler} needs to be made available to
     * sub-components. The specific setter will call this method to populate the
     * attributes.
     *
     * @param name  Name of property to set
     * @param value The value to set the property to
     */
    public void setAttribute(String name, Object value) {
        if (getLog().isTraceEnabled()) {
            getLog().trace(sm.getString("endpoint.setAttribute", name, value));
        }
        attributes.put(name, value);
    }
    /**
     * Used by sub-components to retrieve configuration information.
     *
     * @param key The name of the property for which the value should be
     *            retrieved
     *
     * @return The value of the specified property
     */
    public Object getAttribute(String key) {
        Object value = attributes.get(key);
        if (getLog().isTraceEnabled()) {
            getLog().trace(sm.getString("endpoint.getAttribute", key, value));
        }
        return value;
    }



    public boolean setProperty(String name, String value) {
        setAttribute(name, value);
        final String socketName = "socket.";
        try {
            if (name.startsWith(socketName)) {
                return IntrospectionUtils.setProperty(socketProperties, name.substring(socketName.length()), value);
            } else {
                return IntrospectionUtils.setProperty(this,name,value,false);
            }
        }catch ( Exception x ) {
            getLog().error(sm.getString("endpoint.setAttributeError", name, value), x);
            return false;
        }
    }
    public String getProperty(String name) {
        String value = (String) getAttribute(name);
        final String socketName = "socket.";
        if (value == null && name.startsWith(socketName)) {
            Object result = IntrospectionUtils.getProperty(socketProperties, name.substring(socketName.length()));
            if (result != null) {
                value = result.toString();
            }
        }
        return value;
    }

    /**
     * Return the amount of threads that are managed by the pool.
     *
     * @return the amount of threads that are managed by the pool
     */
    public int getCurrentThreadCount() {
        Executor executor = this.executor;
        if (executor != null) {
            if (executor instanceof ThreadPoolExecutor) {
                return ((ThreadPoolExecutor) executor).getPoolSize();
            } else if (executor instanceof java.util.concurrent.ThreadPoolExecutor) {
                return ((java.util.concurrent.ThreadPoolExecutor) executor).getPoolSize();
            } else if (executor instanceof ResizableExecutor) {
                return ((ResizableExecutor) executor).getPoolSize();
            } else {
                return -1;
            }
        } else {
            return -2;
        }
    }

    /**
     * Return the amount of threads that are in use
     *
     * @return the amount of threads that are in use
     */
    public int getCurrentThreadsBusy() {
        Executor executor = this.executor;
        if (executor != null) {
            if (executor instanceof ThreadPoolExecutor) {
                return ((ThreadPoolExecutor) executor).getActiveCount();
            } else if (executor instanceof java.util.concurrent.ThreadPoolExecutor) {
                return ((java.util.concurrent.ThreadPoolExecutor) executor).getActiveCount();
            } else if (executor instanceof ResizableExecutor) {
                return ((ResizableExecutor) executor).getActiveCount();
            } else {
                return -1;
            }
        } else {
            return -2;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPaused() {
        return paused;
    }


    public void createExecutor() {
        internalExecutor = true;
        if (getUseVirtualThreads()) {
            executor = new VirtualThreadExecutor(getName() + "-virt-");
        } else {
            TaskQueue taskqueue = new TaskQueue(maxQueueSize);
            TaskThreadFactory tf = new TaskThreadFactory(getName() + "-exec-", daemon, getThreadPriority());
            executor = new ThreadPoolExecutor(getMinSpareThreads(), getMaxThreads(), getThreadsMaxIdleTime(),
                    TimeUnit.MILLISECONDS, taskqueue, tf);
            taskqueue.setParent((ThreadPoolExecutor) executor);
        }
    }


    public void shutdownExecutor() {
        Executor executor = this.executor;
        if (executor != null && internalExecutor) {
            this.executor = null;
            if (executor instanceof ThreadPoolExecutor) {
                //this is our internal one, so we need to shut it down
                @SuppressWarnings("resource")
                ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
                tpe.shutdownNow();
                long timeout = getExecutorTerminationTimeoutMillis();
                if (timeout > 0) {
                    try {
                        tpe.awaitTermination(timeout, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    if (tpe.isTerminating()) {
                        getLog().warn(sm.getString("endpoint.warn.executorShutdown", getName()));
                    }
                }
                TaskQueue queue = (TaskQueue) tpe.getQueue();
                queue.setParent(null);
            }
        }
    }

    /**
     * Unlock the server socket acceptor threads using bogus connections.
     */
    protected void unlockAccept() {
        // Only try to unlock the acceptor if it is necessary
        if (acceptor == null || acceptor.getState() != AcceptorState.RUNNING) {
            return;
        }

        InetSocketAddress unlockAddress = null;
        InetSocketAddress localAddress = null;
        try {
            localAddress = getLocalAddress();
        } catch (IOException ioe) {
            getLog().debug(sm.getString("endpoint.debug.unlock.localFail", getName()), ioe);
        }
        if (localAddress == null) {
            getLog().warn(sm.getString("endpoint.debug.unlock.localNone", getName()));
            return;
        }

        try {
            unlockAddress = getUnlockAddress(localAddress);

            try (java.net.Socket s = new java.net.Socket()) {
                // Never going to read from this socket so the timeout doesn't matter. Use the unlock timeout.
                s.setSoTimeout(getSocketProperties().getUnlockTimeout());
                // Newer MacOS versions (e.g. Ventura 13.2) appear to linger for ~1s on close when linger is disabled.
                // That causes delays when running the unit tests. Explicitly enabling linger but with a timeout of
                // zero seconds seems to fix the issue.
                s.setSoLinger(true, 0);
                if (getLog().isTraceEnabled()) {
                    getLog().trace("About to unlock socket for:" + unlockAddress);
                }
                s.connect(unlockAddress, getSocketProperties().getUnlockTimeout());
                if (getLog().isTraceEnabled()) {
                    getLog().trace("Socket unlock completed for:" + unlockAddress);
                }
            }
            // Wait for up to 1000ms for acceptor thread to unlock. Particularly
            // for the unit tests, we want to exit this loop as quickly as
            // possible. However, we also don't want to trigger excessive CPU
            // usage if the unlock takes longer than expected. Therefore, we
            // initially wait for the unlock in a tight loop but if that takes
            // more than 1ms we start using short sleeps to reduce CPU usage.
            long startTime = System.nanoTime();
            while (startTime + 1_000_000_000 > System.nanoTime() && acceptor.getState() == AcceptorState.RUNNING) {
                if (startTime + 1_000_000 < System.nanoTime()) {
                    Thread.sleep(1);
                }
            }
        } catch(Throwable t) {
            ExceptionUtils.handleThrowable(t);
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString(
                        "endpoint.debug.unlock.fail", String.valueOf(getPortWithOffset())), t);
            }
        }
    }


    private static InetSocketAddress getUnlockAddress(InetSocketAddress localAddress) throws SocketException {
        if (localAddress.getAddress().isAnyLocalAddress()) {
            // Need a local address of the same type (IPv4 or IPV6) as the
            // configured bind address since the connector may be configured
            // to not map between types.
            InetAddress loopbackUnlockAddress = null;
            InetAddress linkLocalUnlockAddress = null;

            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (!networkInterface.isPointToPoint() && networkInterface.isUp()) {
                    Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress inetAddress = inetAddresses.nextElement();
                        if (localAddress.getAddress().getClass().isAssignableFrom(inetAddress.getClass())) {
                            if (inetAddress.isLoopbackAddress()) {
                                if (loopbackUnlockAddress == null) {
                                    loopbackUnlockAddress = inetAddress;
                                }
                            } else if (inetAddress.isLinkLocalAddress()) {
                                if (linkLocalUnlockAddress == null) {
                                    linkLocalUnlockAddress = inetAddress;
                                }
                            } else {
                                // Use a non-link local, non-loop back address by default
                                return new InetSocketAddress(inetAddress, localAddress.getPort());
                            }
                        }
                    }
                }
            }
            // Prefer loop back over link local since on some platforms (e.g.
            // OSX) some link local addresses are not included when listening on
            // all local addresses.
            if (loopbackUnlockAddress != null) {
                return new InetSocketAddress(loopbackUnlockAddress, localAddress.getPort());
            }
            if (linkLocalUnlockAddress != null) {
                return new InetSocketAddress(linkLocalUnlockAddress, localAddress.getPort());
            }
            // Fallback
            return new InetSocketAddress("localhost", localAddress.getPort());
        } else {
            return localAddress;
        }
    }


    // ---------------------------------------------- Request processing methods

    /**
     * Process the given SocketWrapper with the given status. Used to trigger
     * processing as if the Poller (for those endpoints that have one)
     * selected the socket.
     *
     * @param socketWrapper The socket wrapper to process
     * @param event         The socket event to be processed
     * @param dispatch      Should the processing be performed on a new
     *                          container thread
     *
     * @return if processing was triggered successfully
     */
    public boolean processSocket(SocketWrapperBase<S> socketWrapper,
            SocketEvent event, boolean dispatch) {
        try {
            if (socketWrapper == null) {
                return false;
            }
            SocketProcessorBase<S> sc = null;
            if (processorCache != null) {
                sc = processorCache.pop();
            }
            if (sc == null) {
                sc = createSocketProcessor(socketWrapper, event);
            } else {
                sc.reset(socketWrapper, event);
            }
            Executor executor = getExecutor();
            if (dispatch && executor != null) {
                executor.execute(sc);
            } else {
                sc.run();
            }
        } catch (RejectedExecutionException ree) {
            getLog().warn(sm.getString("endpoint.executor.fail", socketWrapper) , ree);
            return false;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            getLog().error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }


    protected abstract SocketProcessorBase<S> createSocketProcessor(
            SocketWrapperBase<S> socketWrapper, SocketEvent event);


    // ------------------------------------------------------- Lifecycle methods

    /*
     * NOTE: There is no maintenance of state or checking for valid transitions
     * within this class other than ensuring that bind/unbind are called in the
     * right place. It is expected that the calling code will maintain state and
     * prevent invalid state transitions.
     */

    public abstract void bind() throws Exception;

    public void unbind() throws Exception {
        for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
            for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates()) {
                /*
                 * Only remove any generated SSLContext. If the SSLContext was provided it is left in place in case the
                 * endpoint is re-started.
                 */
                certificate.setSslContextGenerated(null);
            }
        }
    }

    public abstract void startInternal() throws Exception;

    public abstract void stopInternal() throws Exception;


    private void bindWithCleanup() throws Exception {
        try {
            bind();
        } catch (Throwable t) {
            // Ensure open sockets etc. are cleaned up if something goes
            // wrong during bind
            ExceptionUtils.handleThrowable(t);
            unbind();
            throw t;
        }
    }


    public final void init() throws Exception {
        if (bindOnInit) {
            bindWithCleanup();
            bindState = BindState.BOUND_ON_INIT;
        }
        if (this.domain != null) {
            // Register endpoint (as ThreadPool - historical name)
            oname = new ObjectName(domain + ":type=ThreadPool,name=\"" + getName() + "\"");
            Registry.getRegistry(null, null).registerComponent(this, oname, null);

            ObjectName socketPropertiesOname = new ObjectName(domain +
                    ":type=SocketProperties,name=\"" + getName() + "\"");
            socketProperties.setObjectName(socketPropertiesOname);
            Registry.getRegistry(null, null).registerComponent(socketProperties, socketPropertiesOname, null);

            for (SSLHostConfig sslHostConfig : findSslHostConfigs()) {
                registerJmx(sslHostConfig);
            }
        }
    }


    private void registerJmx(SSLHostConfig sslHostConfig) {
        if (domain == null) {
            // Before init the domain is null
            return;
        }
        ObjectName sslOname = null;
        try {
            sslOname = new ObjectName(domain + ":type=SSLHostConfig,ThreadPool=\"" +
                    getName() + "\",name=" + ObjectName.quote(sslHostConfig.getHostName()));
            sslHostConfig.setObjectName(sslOname);
            try {
                Registry.getRegistry(null, null).registerComponent(sslHostConfig, sslOname, null);
            } catch (Exception e) {
                getLog().warn(sm.getString("endpoint.jmxRegistrationFailed", sslOname), e);
            }
        } catch (MalformedObjectNameException e) {
            getLog().warn(sm.getString("endpoint.invalidJmxNameSslHost",
                    sslHostConfig.getHostName()), e);
        }

        for (SSLHostConfigCertificate sslHostConfigCert : sslHostConfig.getCertificates()) {
            ObjectName sslCertOname = null;
            try {
                sslCertOname = new ObjectName(domain +
                        ":type=SSLHostConfigCertificate,ThreadPool=\"" + getName() +
                        "\",Host=" + ObjectName.quote(sslHostConfig.getHostName()) +
                        ",name=" + sslHostConfigCert.getType());
                sslHostConfigCert.setObjectName(sslCertOname);
                try {
                    Registry.getRegistry(null, null).registerComponent(
                            sslHostConfigCert, sslCertOname, null);
                } catch (Exception e) {
                    getLog().warn(sm.getString("endpoint.jmxRegistrationFailed", sslCertOname), e);
                }
            } catch (MalformedObjectNameException e) {
                getLog().warn(sm.getString("endpoint.invalidJmxNameSslHostCert",
                        sslHostConfig.getHostName(), sslHostConfigCert.getType()), e);
            }
        }
    }


    private void unregisterJmx(SSLHostConfig sslHostConfig) {
        Registry registry = Registry.getRegistry(null, null);
        registry.unregisterComponent(sslHostConfig.getObjectName());
        for (SSLHostConfigCertificate sslHostConfigCert : sslHostConfig.getCertificates()) {
            registry.unregisterComponent(sslHostConfigCert.getObjectName());
        }
    }


    public final void start() throws Exception {
        if (bindState == BindState.UNBOUND) {
            bindWithCleanup();
            bindState = BindState.BOUND_ON_START;
        }
        startInternal();
    }


    protected void startAcceptorThread() {
        acceptor = new Acceptor<>(this);
        String threadName = getName() + "-Acceptor";
        acceptor.setThreadName(threadName);
        Thread t = new Thread(acceptor, threadName);
        t.setPriority(getAcceptorThreadPriority());
        t.setDaemon(getDaemon());
        t.start();
    }


    /**
     * Pause the endpoint, which will stop it accepting new connections and
     * unlock the acceptor.
     */
    public void pause() {
        if (running && !paused) {
            paused = true;
            releaseConnectionLatch();
            unlockAccept();
            getHandler().pause();
        }
    }

    /**
     * Resume the endpoint, which will make it start accepting new connections
     * again.
     */
    public void resume() {
        if (running) {
            paused = false;
        }
    }

    public final void stop() throws Exception {
        stopInternal();
        if (bindState == BindState.BOUND_ON_START || bindState == BindState.SOCKET_CLOSED_ON_STOP) {
            unbind();
            bindState = BindState.UNBOUND;
        }
    }

    public final void destroy() throws Exception {
        if (bindState == BindState.BOUND_ON_INIT) {
            unbind();
            bindState = BindState.UNBOUND;
        }
        Registry registry = Registry.getRegistry(null, null);
        registry.unregisterComponent(oname);
        registry.unregisterComponent(socketProperties.getObjectName());
        for (SSLHostConfig sslHostConfig : findSslHostConfigs()) {
            unregisterJmx(sslHostConfig);
        }
    }


    protected abstract Log getLog();

    protected Log getLogCertificate() {
        return getLog();
    }

    protected LimitLatch initializeConnectionLatch() {
        if (maxConnections==-1) {
            return null;
        }
        if (connectionLimitLatch==null) {
            connectionLimitLatch = new LimitLatch(getMaxConnections());
        }
        return connectionLimitLatch;
    }

    private void releaseConnectionLatch() {
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) {
            latch.releaseAll();
        }
        connectionLimitLatch = null;
    }

    protected void countUpOrAwaitConnection() throws InterruptedException {
        if (maxConnections==-1) {
            return;
        }
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) {
            latch.countUpOrAwait();
        }
    }

    protected long countDownConnection() {
        if (maxConnections==-1) {
            return -1;
        }
        LimitLatch latch = connectionLimitLatch;
        if (latch!=null) {
            long result = latch.countDown();
            if (result<0) {
                getLog().warn(sm.getString("endpoint.warn.incorrectConnectionCount"));
            }
            return result;
        } else {
            return -1;
        }
    }


    /**
     * Close the server socket (to prevent further connections) if the server
     * socket was originally bound on {@link #start()} (rather than on
     * {@link #init()}).
     *
     * @see #getBindOnInit()
     */
    public final void closeServerSocketGraceful() {
        if (bindState == BindState.BOUND_ON_START) {
            // Stop accepting new connections
            acceptor.stopMillis(-1);
            // Release locks that may be preventing the acceptor from stopping
            releaseConnectionLatch();
            unlockAccept();
            // Signal to any multiplexed protocols (HTTP/2) that they may wish
            // to stop accepting new streams
            getHandler().pause();
            // Update the bindState. This has the side-effect of disabling
            // keep-alive for any in-progress connections
            bindState = BindState.SOCKET_CLOSED_ON_STOP;
            try {
                doCloseServerSocket();
            } catch (IOException ioe) {
                getLog().warn(sm.getString("endpoint.serverSocket.closeFailed", getName()), ioe);
            }
        }
    }


    /**
     * Wait for the client connections to the server to close gracefully. The
     * method will return when all of the client connections have closed or the
     * method has been waiting for {@code waitTimeMillis}.
     *
     * @param waitMillis    The maximum time to wait in milliseconds for the
     *                      client connections to close.
     *
     * @return The wait time, if any remaining when the method returned
     */
    public final long awaitConnectionsClose(long waitMillis) {
        while (waitMillis > 0 && !connections.isEmpty()) {
            try {
                Thread.sleep(50);
                waitMillis -= 50;
            } catch (InterruptedException e) {
                Thread.interrupted();
                waitMillis = 0;
            }
        }
        return waitMillis;
    }


    /**
     * Actually close the server socket but don't perform any other clean-up.
     *
     * @throws IOException If an error occurs closing the socket
     */
    protected abstract void doCloseServerSocket() throws IOException;

    protected abstract U serverSocketAccept() throws Exception;

    protected abstract boolean setSocketOptions(U socket);

    /**
     * Close the socket when the connection has to be immediately closed when
     * an error occurs while configuring the accepted socket or trying to
     * dispatch it for processing. The wrapper associated with the socket will
     * be used for the close.
     * @param socket The newly accepted socket
     */
    protected void closeSocket(U socket) {
        SocketWrapperBase<S> socketWrapper = connections.get(socket);
        if (socketWrapper != null) {
            socketWrapper.close();
        }
    }

    /**
     * Close the socket. This is used when the connector is not in a state
     * which allows processing the socket, or if there was an error which
     * prevented the allocation of the socket wrapper.
     * @param socket The newly accepted socket
     */
    protected abstract void destroySocket(U socket);
}

