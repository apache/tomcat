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

import javax.servlet.http.HttpUpgradeHandler;

import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.Processor;
import org.apache.coyote.http11.upgrade.UpgradeProcessor;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.AprEndpoint.Handler;
import org.apache.tomcat.util.net.AprEndpoint.Poller;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class Http11AprProtocol extends AbstractHttp11Protocol<Long> {

    private static final Log log = LogFactory.getLog(Http11AprProtocol.class);

    @Override
    protected Log getLog() { return log; }


    @Override
    protected AbstractEndpoint.Handler getHandler() {
        return cHandler;
    }


    @Override
    public boolean isAprRequired() {
        // Override since this protocol implementation requires the APR/native
        // library
        return true;
    }


    public Http11AprProtocol() {
        super(new AprEndpoint());
        cHandler = new Http11ConnectionHandler(this);
        ((AprEndpoint) getEndpoint()).setHandler(cHandler);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }

    private final Http11ConnectionHandler cHandler;

    public boolean getUseSendfile() { return getEndpoint().getUseSendfile(); }
    public void setUseSendfile(boolean useSendfile) { ((AprEndpoint)getEndpoint()).setUseSendfile(useSendfile); }

    public int getPollTime() { return ((AprEndpoint)getEndpoint()).getPollTime(); }
    public void setPollTime(int pollTime) { ((AprEndpoint)getEndpoint()).setPollTime(pollTime); }

    public void setPollerSize(int pollerSize) { getEndpoint().setMaxConnections(pollerSize); }
    public int getPollerSize() { return getEndpoint().getMaxConnections(); }

    public int getSendfileSize() { return ((AprEndpoint)getEndpoint()).getSendfileSize(); }
    public void setSendfileSize(int sendfileSize) { ((AprEndpoint)getEndpoint()).setSendfileSize(sendfileSize); }

    public void setSendfileThreadCount(int sendfileThreadCount) { ((AprEndpoint)getEndpoint()).setSendfileThreadCount(sendfileThreadCount); }
    public int getSendfileThreadCount() { return ((AprEndpoint)getEndpoint()).getSendfileThreadCount(); }

    public boolean getDeferAccept() { return ((AprEndpoint)getEndpoint()).getDeferAccept(); }
    public void setDeferAccept(boolean deferAccept) { ((AprEndpoint)getEndpoint()).setDeferAccept(deferAccept); }

    // --------------------  SSL related properties --------------------

    /**
     * SSL protocol.
     */
    public String getSSLProtocol() { return ((AprEndpoint)getEndpoint()).getSSLProtocol(); }
    public void setSSLProtocol(String SSLProtocol) { ((AprEndpoint)getEndpoint()).setSSLProtocol(SSLProtocol); }


    /**
     * SSL password (if a cert is encrypted, and no password has been provided, a callback
     * will ask for a password).
     */
    public String getSSLPassword() { return ((AprEndpoint)getEndpoint()).getSSLPassword(); }
    public void setSSLPassword(String SSLPassword) { ((AprEndpoint)getEndpoint()).setSSLPassword(SSLPassword); }


    /**
     * SSL cipher suite.
     */
    public String getSSLCipherSuite() { return ((AprEndpoint)getEndpoint()).getSSLCipherSuite(); }
    public void setSSLCipherSuite(String SSLCipherSuite) { ((AprEndpoint)getEndpoint()).setSSLCipherSuite(SSLCipherSuite); }
    public String[] getCiphersUsed() { return getEndpoint().getCiphersUsed();}

    /**
     * SSL honor cipher order.
     *
     * Set to <code>true</code> to enforce the <i>server's</i> cipher order
     * instead of the default which is to allow the client to choose a
     * preferred cipher.
     */
    public boolean getSSLHonorCipherOrder() { return ((AprEndpoint)getEndpoint()).getSSLHonorCipherOrder(); }
    public void setSSLHonorCipherOrder(boolean SSLHonorCipherOrder) { ((AprEndpoint)getEndpoint()).setSSLHonorCipherOrder(SSLHonorCipherOrder); }


    /**
     * SSL certificate file.
     */
    public String getSSLCertificateFile() { return ((AprEndpoint)getEndpoint()).getSSLCertificateFile(); }
    public void setSSLCertificateFile(String SSLCertificateFile) { ((AprEndpoint)getEndpoint()).setSSLCertificateFile(SSLCertificateFile); }


    /**
     * SSL certificate key file.
     */
    public String getSSLCertificateKeyFile() { return ((AprEndpoint)getEndpoint()).getSSLCertificateKeyFile(); }
    public void setSSLCertificateKeyFile(String SSLCertificateKeyFile) { ((AprEndpoint)getEndpoint()).setSSLCertificateKeyFile(SSLCertificateKeyFile); }


    /**
     * SSL certificate chain file.
     */
    public String getSSLCertificateChainFile() { return ((AprEndpoint)getEndpoint()).getSSLCertificateChainFile(); }
    public void setSSLCertificateChainFile(String SSLCertificateChainFile) { ((AprEndpoint)getEndpoint()).setSSLCertificateChainFile(SSLCertificateChainFile); }


    /**
     * SSL CA certificate path.
     */
    public String getSSLCACertificatePath() { return ((AprEndpoint)getEndpoint()).getSSLCACertificatePath(); }
    public void setSSLCACertificatePath(String SSLCACertificatePath) { ((AprEndpoint)getEndpoint()).setSSLCACertificatePath(SSLCACertificatePath); }


    /**
     * SSL CA certificate file.
     */
    public String getSSLCACertificateFile() { return ((AprEndpoint)getEndpoint()).getSSLCACertificateFile(); }
    public void setSSLCACertificateFile(String SSLCACertificateFile) { ((AprEndpoint)getEndpoint()).setSSLCACertificateFile(SSLCACertificateFile); }


    /**
     * SSL CA revocation path.
     */
    public String getSSLCARevocationPath() { return ((AprEndpoint)getEndpoint()).getSSLCARevocationPath(); }
    public void setSSLCARevocationPath(String SSLCARevocationPath) { ((AprEndpoint)getEndpoint()).setSSLCARevocationPath(SSLCARevocationPath); }


    /**
     * SSL CA revocation file.
     */
    public String getSSLCARevocationFile() { return ((AprEndpoint)getEndpoint()).getSSLCARevocationFile(); }
    public void setSSLCARevocationFile(String SSLCARevocationFile) { ((AprEndpoint)getEndpoint()).setSSLCARevocationFile(SSLCARevocationFile); }


    /**
     * SSL verify client.
     */
    public String getSSLVerifyClient() { return ((AprEndpoint)getEndpoint()).getSSLVerifyClient(); }
    public void setSSLVerifyClient(String SSLVerifyClient) { ((AprEndpoint)getEndpoint()).setSSLVerifyClient(SSLVerifyClient); }


    /**
     * SSL verify depth.
     */
    public int getSSLVerifyDepth() { return ((AprEndpoint)getEndpoint()).getSSLVerifyDepth(); }
    public void setSSLVerifyDepth(int SSLVerifyDepth) { ((AprEndpoint)getEndpoint()).setSSLVerifyDepth(SSLVerifyDepth); }

    /**
     * Disable SSL compression.
     */
    public boolean getSSLDisableCompression() { return ((AprEndpoint)getEndpoint()).getSSLDisableCompression(); }
    public void setSSLDisableCompression(boolean disable) { ((AprEndpoint)getEndpoint()).setSSLDisableCompression(disable); }

    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        if (isSSLEnabled()) {
            return ("https-apr");
        } else {
            return ("http-apr");
        }
    }


    @Override
    public void start() throws Exception {
        super.start();
        if (npnHandler != null) {
            long sslCtx = ((AprEndpoint) getEndpoint()).getJniSslContext();
            npnHandler.init(getEndpoint(), sslCtx, getAdapter());
        }
    }

    // --------------------  Connection handler --------------------

    protected static class Http11ConnectionHandler
            extends AbstractConnectionHandler<Long,Http11AprProcessor> implements Handler {

        protected Http11AprProtocol proto;

        Http11ConnectionHandler(Http11AprProtocol proto) {
            this.proto = proto;
        }

        @Override
        protected AbstractProtocol<Long> getProtocol() {
            return proto;
        }

        @Override
        protected Log getLog() {
            return log;
        }

        /**
         * Expected to be used by the handler once the processor is no longer
         * required.
         *
         * @param socket
         * @param processor
         * @param isSocketClosing   Not used in HTTP
         * @param addToPoller
         */
        @Override
        public void release(SocketWrapperBase<Long> socket,
                Processor<Long> processor, boolean isSocketClosing,
                boolean addToPoller) {
            processor.recycle(isSocketClosing);
            recycledProcessors.push(processor);
            if (addToPoller && proto.getEndpoint().isRunning()) {
                ((AprEndpoint)proto.getEndpoint()).getPoller().add(
                        socket.getSocket().longValue(),
                        proto.getEndpoint().getKeepAliveTimeout(), true, false);
            }
        }

        @Override
        public SocketState process(SocketWrapperBase<Long> socket,
                SocketStatus status) {
            if (proto.npnHandler != null) {
                Processor<Long> processor = null;
                if (status == SocketStatus.OPEN_READ) {
                    processor = connections.get(socket.getSocket());

                }
                if (processor == null) {
                    // if not null - handled by http11
                    SocketState socketState = proto.npnHandler.process(socket, status);
                    // handled by npn protocol.
                    if (socketState == SocketState.CLOSED ||
                            socketState == SocketState.LONG) {
                        return socketState;
                    }
                }
            }
            return super.process(socket, status);
        }

        @Override
        protected void initSsl(SocketWrapperBase<Long> socket,
                Processor<Long> processor) {
            // NOOP for APR
        }

        @Override
        protected void longPoll(SocketWrapperBase<Long> socket,
                Processor<Long> processor) {

            if (processor.isAsync()) {
                // Async
                socket.setAsync(true);
            } else {
                // Upgraded
                Poller p = ((AprEndpoint) proto.getEndpoint()).getPoller();
                if (p == null) {
                    // Connector has been stopped
                    release(socket, processor, true, false);
                } else {
                    p.add(socket.getSocket().longValue(), -1, true, false);
                }
            }
        }

        @Override
        protected Http11AprProcessor createProcessor() {
            Http11AprProcessor processor = new Http11AprProcessor(
                    proto.getMaxHttpHeaderSize(), (AprEndpoint)proto.getEndpoint(),
                    proto.getMaxTrailerSize(), proto.getMaxExtensionSize(),
                    proto.getMaxSwallowSize());
            proto.configureProcessor(processor);
            // APR specific configuration
            processor.setClientCertProvider(proto.getClientCertProvider());
            register(processor);
            return processor;
        }

        @Override
        protected Processor<Long> createUpgradeProcessor(
                SocketWrapperBase<Long> socket, ByteBuffer leftoverInput,
                HttpUpgradeHandler httpUpgradeProcessor)
                throws IOException {
            return new UpgradeProcessor<>(socket, leftoverInput, httpUpgradeProcessor,
                    proto.getUpgradeAsyncWriteBufferSize());
        }
    }
}
