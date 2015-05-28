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
package org.apache.coyote.http11;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpUpgradeHandler;

import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.Processor;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.coyote.http11.upgrade.UpgradeProcessorExternal;
import org.apache.coyote.http11.upgrade.UpgradeProcessorInternal;
//import org.apache.coyote.http2.Http2Protocol;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SocketWrapperBase;

public abstract class AbstractHttp11Protocol<S> extends AbstractProtocol<S> {

    public AbstractHttp11Protocol(AbstractEndpoint<S> endpoint) {
        super(endpoint);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);

        // TODO: Make this configurable via nested UpgradeProtocol elements in
        //       the Connector.
        //       This is disabled by default otherwise it will break the
        //       APR/native connector with clients that support h2 with ALPN
        //       (because the Http2Protocol is only stubbed out)
        //addUpgradeProtocol(new Http2Protocol());
    }


    @Override
    protected String getProtocolName() {
        return "Http";
    }


    /**
     * {@inheritDoc}
     * <p>
     * Over-ridden here to make the method visible to nested classes.
     */
    @Override
    protected AbstractEndpoint<S> getEndpoint() {
        return super.getEndpoint();
    }


    // ------------------------------------------------ HTTP specific properties
    // ------------------------------------------ managed in the ProtocolHandler

    /**
     * Maximum size of the post which will be saved when processing certain
     * requests, such as a POST.
     */
    private int maxSavePostSize = 4 * 1024;
    public int getMaxSavePostSize() { return maxSavePostSize; }
    public void setMaxSavePostSize(int valueI) { maxSavePostSize = valueI; }


    /**
     * Maximum size of the HTTP message header.
     */
    private int maxHttpHeaderSize = 8 * 1024;
    public int getMaxHttpHeaderSize() { return maxHttpHeaderSize; }
    public void setMaxHttpHeaderSize(int valueI) { maxHttpHeaderSize = valueI; }


    /**
     * Specifies a different (usually  longer) connection timeout during data
     * upload.
     */
    private int connectionUploadTimeout = 300000;
    public int getConnectionUploadTimeout() { return connectionUploadTimeout; }
    public void setConnectionUploadTimeout(int i) {
        connectionUploadTimeout = i;
    }


    /**
     * If true, the connectionUploadTimeout will be ignored and the regular
     * socket timeout will be used for the full duration of the connection.
     */
    private boolean disableUploadTimeout = true;
    public boolean getDisableUploadTimeout() { return disableUploadTimeout; }
    public void setDisableUploadTimeout(boolean isDisabled) {
        disableUploadTimeout = isDisabled;
    }


    /**
     * Integrated compression support.
     */
    private String compression = "off";
    public String getCompression() { return compression; }
    public void setCompression(String valueS) { compression = valueS; }


    private String noCompressionUserAgents = null;
    public String getNoCompressionUserAgents() {
        return noCompressionUserAgents;
    }
    public void setNoCompressionUserAgents(String valueS) {
        noCompressionUserAgents = valueS;
    }


    private String compressableMimeType = "text/html,text/xml,text/plain,text/css";
    private String[] compressableMimeTypes = null;
    public String getCompressableMimeType() { return compressableMimeType; }
    public void setCompressableMimeType(String valueS) {
        compressableMimeType = valueS;
        compressableMimeTypes = null;
    }
    public String[] getCompressableMimeTypes() {
        String[] result = compressableMimeTypes;
        if (result != null) {
            return result;
        }
        List<String> values = new ArrayList<>();
        StringTokenizer tokens = new StringTokenizer(compressableMimeType, ",");
        while (tokens.hasMoreTokens()) {
            String token = tokens.nextToken().trim();
            if (token.length() > 0) {
                values.add(token);
            }
        }
        result = values.toArray(new String[values.size()]);
        compressableMimeTypes = result;
        return result;
    }


    private int compressionMinSize = 2048;
    public int getCompressionMinSize() { return compressionMinSize; }
    public void setCompressionMinSize(int valueI) {
        compressionMinSize = valueI;
    }


    /**
     * Regular expression that defines the User agents which should be
     * restricted to HTTP/1.0 support.
     */
    private String restrictedUserAgents = null;
    public String getRestrictedUserAgents() { return restrictedUserAgents; }
    public void setRestrictedUserAgents(String valueS) {
        restrictedUserAgents = valueS;
    }


    /**
     * Server header.
     */
    private String server;
    public String getServer() { return server; }
    public void setServer( String server ) {
        this.server = server;
    }


    /**
     * Maximum size of trailing headers in bytes
     */
    private int maxTrailerSize = 8192;
    public int getMaxTrailerSize() { return maxTrailerSize; }
    public void setMaxTrailerSize(int maxTrailerSize) {
        this.maxTrailerSize = maxTrailerSize;
    }


    /**
     * Maximum size of extension information in chunked encoding
     */
    private int maxExtensionSize = 8192;
    public int getMaxExtensionSize() { return maxExtensionSize; }
    public void setMaxExtensionSize(int maxExtensionSize) {
        this.maxExtensionSize = maxExtensionSize;
    }


    /**
     * Maximum amount of request body to swallow.
     */
    private int maxSwallowSize = 2 * 1024 * 1024;
    public int getMaxSwallowSize() { return maxSwallowSize; }
    public void setMaxSwallowSize(int maxSwallowSize) {
        this.maxSwallowSize = maxSwallowSize;
    }


    /**
     * This field indicates if the protocol is treated as if it is secure. This
     * normally means https is being used but can be used to fake https e.g
     * behind a reverse proxy.
     */
    private boolean secure;
    public boolean getSecure() { return secure; }
    public void setSecure(boolean b) {
        secure = b;
    }


    /**
     * The names of headers that are allowed to be sent via a trailer when using
     * chunked encoding. They are stored in lower case.
     */
    private Set<String> allowedTrailerHeaders =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    public void setAllowedTrailerHeaders(String commaSeparatedHeaders) {
        // Jump through some hoops so we don't end up with an empty set while
        // doing updates.
        Set<String> toRemove = new HashSet<>();
        toRemove.addAll(allowedTrailerHeaders);
        if (commaSeparatedHeaders != null) {
            String[] headers = commaSeparatedHeaders.split(",");
            for (String header : headers) {
                String trimmedHeader = header.trim().toLowerCase(Locale.ENGLISH);
                if (toRemove.contains(trimmedHeader)) {
                    toRemove.remove(trimmedHeader);
                } else {
                    allowedTrailerHeaders.add(trimmedHeader);
                }
            }
            allowedTrailerHeaders.removeAll(toRemove);
        }
    }
    public String getAllowedTrailerHeaders() {
        // Chances of a size change between these lines are small enough that a
        // sync is unnecessary.
        List<String> copy = new ArrayList<>(allowedTrailerHeaders.size());
        copy.addAll(allowedTrailerHeaders);
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (String header : copy) {
            if (first) {
                first = false;
            } else {
                result.append(',');
            }
            result.append(header);
        }
        return result.toString();
    }
    public void addAllowedTrailerHeader(String header) {
        if (header != null) {
            allowedTrailerHeaders.add(header.trim().toLowerCase(Locale.ENGLISH));
        }
    }
    public void removeAllowedTrailerHeader(String header) {
        if (header != null) {
            allowedTrailerHeaders.remove(header.trim().toLowerCase(Locale.ENGLISH));
        }
    }


    /**
     * The protocols that are available via internal Tomcat support for access
     * via HTTP upgrade.
     */
    private final Map<String,UpgradeProtocol> httpUpgradeProtocols = new HashMap<>();
    /**
     * The protocols that are available via internal Tomcat support for access
     * via ALPN negotiation.
     */
    private final Map<String,UpgradeProtocol> negotiatedProtocols = new HashMap<>();
    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol) {
        httpUpgradeProtocols.put(upgradeProtocol.getHttpUpgradeName(), upgradeProtocol);
        negotiatedProtocols.put(upgradeProtocol.getAlpnName(), upgradeProtocol);
        getEndpoint().addNegotiatedProtocol(upgradeProtocol.getAlpnName());
    }
    @Override
    public UpgradeProtocol getNegotiatedProtocol(String negotiatedName) {
        return negotiatedProtocols.get(negotiatedName);
    }


    // ------------------------------------------------ HTTP specific properties
    // ------------------------------------------ passed through to the EndPoint

    public boolean isSSLEnabled() { return getEndpoint().isSSLEnabled();}
    public void setSSLEnabled(boolean SSLEnabled) {
        getEndpoint().setSSLEnabled(SSLEnabled);
    }


    public boolean getUseSendfile() { return getEndpoint().getUseSendfile(); }
    public void setUseSendfile(boolean useSendfile) { getEndpoint().setUseSendfile(useSendfile); }


    /**
     * @return The maximum number of requests which can be performed over a
     *         keep-alive connection. The default is the same as for Apache HTTP
     *         Server (100).
     */
    public int getMaxKeepAliveRequests() {
        return getEndpoint().getMaxKeepAliveRequests();
    }
    public void setMaxKeepAliveRequests(int mkar) {
        getEndpoint().setMaxKeepAliveRequests(mkar);
    }


    // ----------------------------------------------- HTTPS specific properties
    // ------------------------------------------ passed through to the EndPoint

    public String getDefaultSSLHostConfigName() {
        return getEndpoint().getDefaultSSLHostConfigName();
    }
    public void setDefaultSSLHostConfigName(String defaultSSLHostConfigName) {
        getEndpoint().setDefaultSSLHostConfigName(defaultSSLHostConfigName);
        if (defaultSSLHostConfig != null) {
            defaultSSLHostConfig.setHostName(defaultSSLHostConfigName);
        }
    }


    @Override
    public void addSslHostConfig(SSLHostConfig sslHostConfig) {
        getEndpoint().addSslHostConfig(sslHostConfig);
    }


    // ----------------------------------------------- HTTPS specific properties
    // -------------------------------------------- Handled via an SSLHostConfig

    private SSLHostConfig defaultSSLHostConfig = null;
    private void registerDefaultSSLHostConfig() {
        if (defaultSSLHostConfig == null) {
            defaultSSLHostConfig = new SSLHostConfig();
            defaultSSLHostConfig.setHostName(getDefaultSSLHostConfigName());
            getEndpoint().addSslHostConfig(defaultSSLHostConfig);
        }
    }


    // TODO: All of these SSL setters can be removed once it is no longer
    // necessary to support the old configuration attributes (Tomcat 10?).

    public void setSslEnabledProtocols(String enabledProtocols) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setProtocols(enabledProtocols);
    }
    public void setSSLProtocol(String sslProtocol) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setProtocols(sslProtocol);
    }


    public void setKeystoreFile(String keystoreFile) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeystoreFile(keystoreFile);
    }
    public void setSSLCertificateFile(String certificateFile) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateFile(certificateFile);
    }
    public void setSSLCertificateKeyFile(String certificateKeyFile) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeyFile(certificateKeyFile);
    }


    public void setAlgorithm(String keyManagerAlgorithm) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setKeyManagerAlgorithm(keyManagerAlgorithm);
    }


    public void setClientAuth(String certificateVerification) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateVerification(certificateVerification);
    }


    public void setSSLVerifyClient(String certificateVerification) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateVerification(certificateVerification);
    }


    public void setTrustMaxCertLength(int certificateVerificationDepth){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateVerificationDepth(certificateVerificationDepth);
    }
    public void setSSLVerifyDepth(int certificateVerificationDepth) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateVerificationDepth(certificateVerificationDepth);
    }


    public void setUseServerCipherSuitesOrder(boolean honorCipherOrder) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setHonorCipherOrder(honorCipherOrder);
    }
    public void setSSLHonorCipherOrder(boolean honorCipherOrder) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setHonorCipherOrder(honorCipherOrder);
    }


    public void setCiphers(String ciphers) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCiphers(ciphers);
    }
    public void setSSLCipherSuite(String ciphers) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCiphers(ciphers);
    }

    public void setKeystorePass(String certificateKeystorePassword) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeystorePassword(certificateKeystorePassword);
    }

    public void setKeyPass(String certificateKeyPassword) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeyPassword(certificateKeyPassword);
    }
    public void setSSLPassword(String certificateKeyPassword) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeyPassword(certificateKeyPassword);
    }


    public void setCrlFile(String certificateRevocationListFile){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateRevocationListFile(certificateRevocationListFile);
    }
    public void setSSLCARevocationFile(String certificateRevocationListFile) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateRevocationListFile(certificateRevocationListFile);
    }
    public void setSSLCARevocationPath(String certificateRevocationListPath) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateRevocationListPath(certificateRevocationListPath);
    }


    public void setKeystoreType(String certificateKeystoreType) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeystoreType(certificateKeystoreType);
    }


    public void setKeystoreProvider(String certificateKeystoreProvider) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeystoreProvider(certificateKeystoreProvider);
    }


    public void setKeyAlias(String certificateKeyAlias) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateKeyAlias(certificateKeyAlias);
    }


    public void setTruststoreAlgorithm(String truststoreAlgorithm){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setTruststoreAlgorithm(truststoreAlgorithm);
    }


    public void setTruststoreFile(String truststoreFile){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setTruststoreFile(truststoreFile);
    }


    public void setTruststorePass(String truststorePassword){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setTruststorePassword(truststorePassword);
    }


    public void setTruststoreType(String truststoreType){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setTruststoreType(truststoreType);
    }


    public void setTruststoreProvider(String truststoreProvider){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setTruststoreProvider(truststoreProvider);
    }


    public void setSslProtocol(String sslProtocol) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setSslProtocol(sslProtocol);
    }


    public void setSessionCacheSize(int sessionCacheSize){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setSessionCacheSize(sessionCacheSize);
    }


    public void setSessionTimeout(int sessionTimeout){
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setSessionTimeout(sessionTimeout);
    }


    public void setSSLCertificateChainFile(String certificateChainFile) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCertificateChainFile(certificateChainFile);
    }


    public void setSSLCACertificatePath(String caCertificatePath) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCaCertificatePath(caCertificatePath);
    }


    public void setSSLCACertificateFile(String caCertificateFile) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setCaCertificateFile(caCertificateFile);
    }


    public void setSSLDisableCompression(boolean disableCompression) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setDisableCompression(disableCompression);
    }


    public void setSSLDisableSessionTickets(boolean disableSessionTickets) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setDisableSessionTickets(disableSessionTickets);
    }


    public void setTrustManagerClassName(String trustManagerClassName) {
        registerDefaultSSLHostConfig();
        defaultSSLHostConfig.setTrustManagerClassName(trustManagerClassName);
    }


    // ------------------------------------------------------------- Common code

    // Common configuration required for all new HTTP11 processors
    protected void configureProcessor(Http11Processor processor) {
        processor.setAdapter(getAdapter());
        processor.setMaxKeepAliveRequests(getMaxKeepAliveRequests());
        processor.setConnectionUploadTimeout(getConnectionUploadTimeout());
        processor.setDisableUploadTimeout(getDisableUploadTimeout());
        processor.setCompressionMinSize(getCompressionMinSize());
        processor.setCompression(getCompression());
        processor.setNoCompressionUserAgents(getNoCompressionUserAgents());
        processor.setCompressableMimeTypes(getCompressableMimeTypes());
        processor.setRestrictedUserAgents(getRestrictedUserAgents());
        processor.setMaxSavePostSize(getMaxSavePostSize());
        processor.setServer(getServer());
        processor.setClientCertProvider(getClientCertProvider());
    }


    protected abstract static class AbstractHttp11ConnectionHandler<S>
            extends AbstractConnectionHandler<S,Http11Processor> {

        private final AbstractHttp11Protocol<S> proto;


        protected AbstractHttp11ConnectionHandler(AbstractHttp11Protocol<S> proto) {
            this.proto = proto;
        }


        @Override
        protected AbstractHttp11Protocol<S> getProtocol() {
            return proto;
        }


        @Override
        public Http11Processor createProcessor() {
            Http11Processor processor = new Http11Processor(
                    proto.getMaxHttpHeaderSize(), proto.getEndpoint(), proto.getMaxTrailerSize(),
                    proto.allowedTrailerHeaders, proto.getMaxExtensionSize(),
                    proto.getMaxSwallowSize());
            proto.configureProcessor(processor);
            register(processor);
            return processor;
        }


        @Override
        protected Processor createUpgradeProcessor(
                SocketWrapperBase<?> socket, ByteBuffer leftoverInput,
                HttpUpgradeHandler httpUpgradeHandler)
                throws IOException {
            if (httpUpgradeHandler instanceof InternalHttpUpgradeHandler) {
                return new UpgradeProcessorInternal(socket, leftoverInput,
                        (InternalHttpUpgradeHandler) httpUpgradeHandler);
            } else {
                return new UpgradeProcessorExternal(socket, leftoverInput, httpUpgradeHandler);
            }
        }


        @Override
        protected void longPoll(SocketWrapperBase<?> socket, Processor processor) {
            if (processor.isAsync()) {
                // Async
                socket.setAsync(true);
            } else {
                // Either:
                //  - this is an upgraded connection
                //  - the request line/headers have not been completely
                //    read
                socket.registerReadInterest();
            }
        }
    }
}
