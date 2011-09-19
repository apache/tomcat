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

import org.apache.coyote.AbstractProtocol;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.AprEndpoint.Handler;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class Http11AprProtocol extends AbstractHttp11Protocol {

    private static final Log log = LogFactory.getLog(Http11AprProtocol.class);


    @Override
    protected Log getLog() { return log; }


    @Override
    protected AbstractEndpoint.Handler getHandler() {
        return cHandler;
    }


    public Http11AprProtocol() {
        endpoint = new AprEndpoint();
        cHandler = new Http11ConnectionHandler(this);
        ((AprEndpoint) endpoint).setHandler(cHandler);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }

    private final Http11ConnectionHandler cHandler;

    public boolean getUseSendfile() { return ((AprEndpoint)endpoint).getUseSendfile(); }
    public void setUseSendfile(boolean useSendfile) { ((AprEndpoint)endpoint).setUseSendfile(useSendfile); }

    public int getPollTime() { return ((AprEndpoint)endpoint).getPollTime(); }
    public void setPollTime(int pollTime) { ((AprEndpoint)endpoint).setPollTime(pollTime); }

    public void setPollerSize(int pollerSize) { endpoint.setMaxConnections(pollerSize); }
    public int getPollerSize() { return endpoint.getMaxConnections(); }

    public void setPollerThreadCount(int pollerThreadCount) { ((AprEndpoint)endpoint).setPollerThreadCount(pollerThreadCount); }
    public int getPollerThreadCount() { return ((AprEndpoint)endpoint).getPollerThreadCount(); }
    
    public int getSendfileSize() { return ((AprEndpoint)endpoint).getSendfileSize(); }
    public void setSendfileSize(int sendfileSize) { ((AprEndpoint)endpoint).setSendfileSize(sendfileSize); }
    
    public void setSendfileThreadCount(int sendfileThreadCount) { ((AprEndpoint)endpoint).setSendfileThreadCount(sendfileThreadCount); }
    public int getSendfileThreadCount() { return ((AprEndpoint)endpoint).getSendfileThreadCount(); }

    public boolean getDeferAccept() { return ((AprEndpoint)endpoint).getDeferAccept(); }
    public void setDeferAccept(boolean deferAccept) { ((AprEndpoint)endpoint).setDeferAccept(deferAccept); }

    // --------------------  SSL related properties --------------------

    /**
     * SSL protocol.
     */
    public String getSSLProtocol() { return ((AprEndpoint)endpoint).getSSLProtocol(); }
    public void setSSLProtocol(String SSLProtocol) { ((AprEndpoint)endpoint).setSSLProtocol(SSLProtocol); }


    /**
     * SSL password (if a cert is encrypted, and no password has been provided, a callback
     * will ask for a password).
     */
    public String getSSLPassword() { return ((AprEndpoint)endpoint).getSSLPassword(); }
    public void setSSLPassword(String SSLPassword) { ((AprEndpoint)endpoint).setSSLPassword(SSLPassword); }


    /**
     * SSL cipher suite.
     */
    public String getSSLCipherSuite() { return ((AprEndpoint)endpoint).getSSLCipherSuite(); }
    public void setSSLCipherSuite(String SSLCipherSuite) { ((AprEndpoint)endpoint).setSSLCipherSuite(SSLCipherSuite); }


    /**
     * SSL certificate file.
     */
    public String getSSLCertificateFile() { return ((AprEndpoint)endpoint).getSSLCertificateFile(); }
    public void setSSLCertificateFile(String SSLCertificateFile) { ((AprEndpoint)endpoint).setSSLCertificateFile(SSLCertificateFile); }


    /**
     * SSL certificate key file.
     */
    public String getSSLCertificateKeyFile() { return ((AprEndpoint)endpoint).getSSLCertificateKeyFile(); }
    public void setSSLCertificateKeyFile(String SSLCertificateKeyFile) { ((AprEndpoint)endpoint).setSSLCertificateKeyFile(SSLCertificateKeyFile); }


    /**
     * SSL certificate chain file.
     */
    public String getSSLCertificateChainFile() { return ((AprEndpoint)endpoint).getSSLCertificateChainFile(); }
    public void setSSLCertificateChainFile(String SSLCertificateChainFile) { ((AprEndpoint)endpoint).setSSLCertificateChainFile(SSLCertificateChainFile); }


    /**
     * SSL CA certificate path.
     */
    public String getSSLCACertificatePath() { return ((AprEndpoint)endpoint).getSSLCACertificatePath(); }
    public void setSSLCACertificatePath(String SSLCACertificatePath) { ((AprEndpoint)endpoint).setSSLCACertificatePath(SSLCACertificatePath); }


    /**
     * SSL CA certificate file.
     */
    public String getSSLCACertificateFile() { return ((AprEndpoint)endpoint).getSSLCACertificateFile(); }
    public void setSSLCACertificateFile(String SSLCACertificateFile) { ((AprEndpoint)endpoint).setSSLCACertificateFile(SSLCACertificateFile); }


    /**
     * SSL CA revocation path.
     */
    public String getSSLCARevocationPath() { return ((AprEndpoint)endpoint).getSSLCARevocationPath(); }
    public void setSSLCARevocationPath(String SSLCARevocationPath) { ((AprEndpoint)endpoint).setSSLCARevocationPath(SSLCARevocationPath); }


    /**
     * SSL CA revocation file.
     */
    public String getSSLCARevocationFile() { return ((AprEndpoint)endpoint).getSSLCARevocationFile(); }
    public void setSSLCARevocationFile(String SSLCARevocationFile) { ((AprEndpoint)endpoint).setSSLCARevocationFile(SSLCARevocationFile); }


    /**
     * SSL verify client.
     */
    public String getSSLVerifyClient() { return ((AprEndpoint)endpoint).getSSLVerifyClient(); }
    public void setSSLVerifyClient(String SSLVerifyClient) { ((AprEndpoint)endpoint).setSSLVerifyClient(SSLVerifyClient); }


    /**
     * SSL verify depth.
     */
    public int getSSLVerifyDepth() { return ((AprEndpoint)endpoint).getSSLVerifyDepth(); }
    public void setSSLVerifyDepth(int SSLVerifyDepth) { ((AprEndpoint)endpoint).setSSLVerifyDepth(SSLVerifyDepth); }
    
    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        return ("http-apr");
    }


    // --------------------  Connection handler --------------------

    protected static class Http11ConnectionHandler
            extends AbstractConnectionHandler<Long,Http11AprProcessor> implements Handler {
        
        protected Http11AprProtocol proto;
        
        Http11ConnectionHandler(Http11AprProtocol proto) {
            this.proto = proto;
        }

        @Override
        protected AbstractProtocol getProtocol() {
            return proto;
        }

        @Override
        protected Log getLog() {
            return log;
        }
        
        @Override
        public void recycle() {
            recycledProcessors.clear();
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
        public void release(SocketWrapper<Long> socket,
                Http11AprProcessor processor, boolean isSocketClosing,
                boolean addToPoller) {
            processor.recycle();
            recycledProcessors.offer(processor);
            if (addToPoller && proto.endpoint.isRunning()) {
                ((AprEndpoint)proto.endpoint).getPoller().add(
                        socket.getSocket().longValue(), true);
            }
        }

        @Override
        protected void initSsl(SocketWrapper<Long> socket,
                Http11AprProcessor processor) {
            // NOOP for APR
        }

        @Override
        protected void longPoll(SocketWrapper<Long> socket,
                Http11AprProcessor processor) {
            connections.put(socket.getSocket(), processor);

            if (processor.isAsync()) {
                socket.setAsync(true);
            } else if (processor.comet && proto.endpoint.isRunning()) {
                ((AprEndpoint) proto.endpoint).getCometPoller().add(
                        socket.getSocket().longValue(), false);
            }
        }

        @Override
        protected Http11AprProcessor createProcessor() {
            Http11AprProcessor processor = new Http11AprProcessor(
                    proto.getMaxHttpHeaderSize(), (AprEndpoint)proto.endpoint,
                    proto.getMaxTrailerSize());
            processor.setAdapter(proto.adapter);
            processor.setMaxKeepAliveRequests(proto.getMaxKeepAliveRequests());
            processor.setKeepAliveTimeout(proto.getKeepAliveTimeout());
            processor.setConnectionUploadTimeout(
                    proto.getConnectionUploadTimeout());
            processor.setDisableUploadTimeout(proto.getDisableUploadTimeout());
            processor.setCompressionMinSize(proto.getCompressionMinSize());
            processor.setCompression(proto.getCompression());
            processor.setNoCompressionUserAgents(proto.getNoCompressionUserAgents());
            processor.setCompressableMimeTypes(proto.getCompressableMimeTypes());
            processor.setRestrictedUserAgents(proto.getRestrictedUserAgents());
            processor.setSocketBuffer(proto.getSocketBuffer());
            processor.setMaxSavePostSize(proto.getMaxSavePostSize());
            processor.setServer(proto.getServer());
            processor.setClientCertProvider(proto.getClientCertProvider());
            register(processor);
            return processor;
        }
    }
}
