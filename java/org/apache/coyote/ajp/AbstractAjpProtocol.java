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
package org.apache.coyote.ajp;

import java.net.InetAddress;
import java.util.regex.Pattern;

import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.Processor;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.UpgradeToken;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * This the base implementation for the AJP protocol handlers. Implementations typically extend this base class rather
 * than implement {@link org.apache.coyote.ProtocolHandler}. All of the implementations that ship with Tomcat are
 * implemented this way.
 *
 * @param <S> The type of socket used by the implementation
 */
public abstract class AbstractAjpProtocol<S> extends AbstractProtocol<S> {

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(AbstractAjpProtocol.class);


    /**
     * Creates a new AJP protocol handler.
     *
     * @param endpoint The endpoint for low-level network I/O
     */
    public AbstractAjpProtocol(AbstractEndpoint<S,?> endpoint) {
        super(endpoint);
        setConnectionTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        // AJP does not use Send File
        getEndpoint().setUseSendfile(false);
        // AJP listens on loopback by default
        getEndpoint().setAddress(InetAddress.getLoopbackAddress());
    }


    /**
     * Gets the name of the protocol.
     *
     * @return the protocol name
     */
    @Override
    protected String getProtocolName() {
        return "Ajp";
    }


    /**
     * {@inheritDoc} Overridden to make getter accessible to other classes in this package.
     */
    @Override
    protected AbstractEndpoint<S,?> getEndpoint() {
        return super.getEndpoint();
    }


    /**
     * {@inheritDoc} AJP does not support protocol negotiation so this always returns null.
     */
    @Override
    protected UpgradeProtocol getNegotiatedProtocol(String name) {
        return null;
    }


    /**
     * {@inheritDoc} AJP does not support protocol upgrade so this always returns null.
     */
    @Override
    protected UpgradeProtocol getUpgradeProtocol(String name) {
        return null;
    }

    // ------------------------------------------------- AJP specific properties
    // ------------------------------------------ managed in the ProtocolHandler

    private boolean ajpFlush = true;

    /**
     * Gets whether AJP flush packets are used.
     *
     * @return <code>true</code> if flush packets are used
     */
    public boolean getAjpFlush() {
        return ajpFlush;
    }

    /**
     * Configure whether to aend an AJP flush packet when flushing. A flush packet is a zero byte AJP13 SEND_BODY_CHUNK
     * packet. mod_jk and mod_proxy_ajp interpret this as a request to flush data to the client. AJP always does flush
     * at the end of the response, so if it is not important, that the packets get streamed up to the client, do not use
     * extra flush packets. For compatibility and to stay on the safe side, flush packets are enabled by default.
     *
     * @param ajpFlush The new flush setting
     */
    public void setAjpFlush(boolean ajpFlush) {
        this.ajpFlush = ajpFlush;
    }


    private boolean tomcatAuthentication = true;

    /**
     * Should authentication be done in the native web server layer, or in the Servlet container ?
     *
     * @return {@code true} if authentication should be performed by Tomcat, otherwise {@code false}
     */
    public boolean getTomcatAuthentication() {
        return tomcatAuthentication;
    }

    /**
     * Sets whether authentication should be done in Tomcat.
     *
     * @param tomcatAuthentication {@code true} if authentication should be performed by Tomcat
     */
    public void setTomcatAuthentication(boolean tomcatAuthentication) {
        this.tomcatAuthentication = tomcatAuthentication;
    }


    private boolean tomcatAuthorization = false;

    /**
     * Should authentication be done in the native web server layer and authorization in the Servlet container?
     *
     * @return {@code true} if authorization should be performed by Tomcat, otherwise {@code false}
     */
    public boolean getTomcatAuthorization() {
        return tomcatAuthorization;
    }

    /**
     * Sets whether authorization should be done by Tomcat.
     *
     * @param tomcatAuthorization {@code true} if authorization should be performed by Tomcat
     */
    public void setTomcatAuthorization(boolean tomcatAuthorization) {
        this.tomcatAuthorization = tomcatAuthorization;
    }


    private String secret = null;

    /**
     * Set the secret that must be included with every request.
     *
     * @param secret The required secret
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * Gets the secret that must be included with every request.
     *
     * @return the secret
     */
    protected String getSecret() {
        return secret;
    }


    private boolean secretRequired = true;

    /**
     * Sets whether a secret is required with every request.
     *
     * @param secretRequired {@code true} if a secret is required
     */
    public void setSecretRequired(boolean secretRequired) {
        this.secretRequired = secretRequired;
    }

    /**
     * Gets whether a secret is required with every request.
     *
     * @return {@code true} if a secret is required
     */
    public boolean getSecretRequired() {
        return secretRequired;
    }


    private Pattern allowedRequestAttributesPattern;

    /**
     * Sets the pattern for allowed request attributes.
     *
     * @param allowedRequestAttributesPattern The regex pattern
     */
    public void setAllowedRequestAttributesPattern(String allowedRequestAttributesPattern) {
        this.allowedRequestAttributesPattern = Pattern.compile(allowedRequestAttributesPattern);
    }

    /**
     * Gets the pattern for allowed request attributes.
     *
     * @return the pattern string
     */
    public String getAllowedRequestAttributesPattern() {
        return allowedRequestAttributesPattern.pattern();
    }

    /**
     * Gets the compiled pattern for allowed request attributes.
     *
     * @return the pattern
     */
    protected Pattern getAllowedRequestAttributesPatternInternal() {
        return allowedRequestAttributesPattern;
    }


    /**
     * AJP packet size.
     */
    private int packetSize = Constants.MAX_PACKET_SIZE;

    /**
     * Gets the AJP packet size.
     *
     * @return the packet size
     */
    public int getPacketSize() {
        return packetSize;
    }

    /**
     * Sets the AJP packet size.
     *
     * @param packetSize The packet size (must be at least MAX_PACKET_SIZE)
     */
    public void setPacketSize(int packetSize) {
        this.packetSize = Math.max(packetSize, Constants.MAX_PACKET_SIZE);
    }


    /**
     * Gets the desired buffer size for AJP packets.
     *
     * @return the desired buffer size
     */
    @Override
    public int getDesiredBufferSize() {
        return getPacketSize() - Constants.SEND_HEAD_LEN;
    }


    // --------------------------------------------- SSL is not supported in AJP

    /**
     * Adds an SSL host configuration. AJP does not support SSL so this logs a warning.
     *
     * @param sslHostConfig The SSL host configuration
     */
    @Override
    public void addSslHostConfig(SSLHostConfig sslHostConfig) {
        getLog().warn(sm.getString("ajpprotocol.noSSL", sslHostConfig.getHostName()));
    }

    /**
     * Adds an SSL host configuration. AJP does not support SSL so this logs a warning.
     *
     * @param sslHostConfig The SSL host configuration
     * @param replace       Whether to replace existing configurations
     */
    @Override
    public void addSslHostConfig(SSLHostConfig sslHostConfig, boolean replace) {
        getLog().warn(sm.getString("ajpprotocol.noSSL", sslHostConfig.getHostName()));
    }

    /**
     * Finds SSL host configurations. AJP does not support SSL so this always returns an empty array.
     *
     * @return an empty array
     */
    @Override
    public SSLHostConfig[] findSslHostConfigs() {
        return new SSLHostConfig[0];
    }

    /**
     * Adds an upgrade protocol. AJP does not support upgrade so this logs a warning.
     *
     * @param upgradeProtocol The upgrade protocol
     */
    @Override
    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol) {
        getLog().warn(sm.getString("ajpprotocol.noUpgrade", upgradeProtocol.getClass().getName()));
    }

    /**
     * Finds upgrade protocols. AJP does not support upgrade so this always returns an empty array.
     *
     * @return an empty array
     */
    @Override
    public UpgradeProtocol[] findUpgradeProtocols() {
        return new UpgradeProtocol[0];
    }


    /**
     * Creates a new AJP processor.
     *
     * @return the processor
     */
    @Override
    protected Processor createProcessor() {
        return new AjpProcessor(this, getAdapter());
    }


    /**
     * Creates an upgrade processor. AJP does not support upgrade so this always throws.
     *
     * @param socket       The socket wrapper
     * @param upgradeToken The upgrade token
     *
     * @return never returns
     *
     * @throws IllegalStateException always
     */
    @Override
    protected Processor createUpgradeProcessor(SocketWrapperBase<?> socket, UpgradeToken upgradeToken) {
        throw new IllegalStateException(
                sm.getString("ajpprotocol.noUpgradeHandler", upgradeToken.httpUpgradeHandler().getClass().getName()));
    }


    /**
     * Starts the AJP protocol handler. Validates that a secret is configured if required.
     *
     * @throws Exception if start fails
     */
    @Override
    public void start() throws Exception {
        if (getSecretRequired()) {
            String secret = getSecret();
            if (secret == null || secret.isEmpty()) {
                throw new IllegalArgumentException(sm.getString("ajpprotocol.noSecret"));
            }
        }
        super.start();
    }
}
