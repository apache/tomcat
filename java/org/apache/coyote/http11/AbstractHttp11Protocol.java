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

import java.util.concurrent.ConcurrentHashMap;

import org.apache.coyote.AbstractProtocol;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractHttp11Protocol extends AbstractProtocol {

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    @Override
    protected String getProtocolName() {
        return "Http";
    }


    // ------------------------------------------------ HTTP specific properties
    // ------------------------------------------ managed in the ProtocolHandler

    private int socketBuffer = 9000;
    public int getSocketBuffer() { return socketBuffer; }
    public void setSocketBuffer(int socketBuffer) {
        this.socketBuffer = socketBuffer;
    }

    
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


    private String compressableMimeTypes = "text/html,text/xml,text/plain";
    public String getCompressableMimeType() { return compressableMimeTypes; }
    public void setCompressableMimeType(String valueS) {
        compressableMimeTypes = valueS;
    }
    public String getCompressableMimeTypes() {
        return getCompressableMimeType();
    }
    public void setCompressableMimeTypes(String valueS) {
        setCompressableMimeType(valueS);
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
     * This field indicates if the protocol is treated as if it is secure. This
     * normally means https is being used but can be used to fake https e.g
     * behind a reverse proxy.
     */
    private boolean secure;
    public boolean getSecure() { return secure; }
    public void setSecure(boolean b) { 
        secure = b;         
    }
    

    // ------------------------------------------------ HTTP specific properties
    // ------------------------------------------ passed through to the EndPoint
    
    public boolean isSSLEnabled() { return endpoint.isSSLEnabled();}
    public void setSSLEnabled(boolean SSLEnabled) {
        endpoint.setSSLEnabled(SSLEnabled);
    }    


    /**
     * Maximum number of requests which can be performed over a keepalive 
     * connection. The default is the same as for Apache HTTP Server.
     */
    public int getMaxKeepAliveRequests() { 
        return endpoint.getMaxKeepAliveRequests();
    }
    public void setMaxKeepAliveRequests(int mkar) {
        endpoint.setMaxKeepAliveRequests(mkar);
    }


    protected abstract static class AbstractHttp11ConnectionHandler<S,P extends AbstractHttp11Processor<S>>
            extends AbstractConnectionHandler {
        
        protected ConcurrentHashMap<SocketWrapper<S>,P> connections =
            new ConcurrentHashMap<SocketWrapper<S>,P>();

        protected RecycledProcessors<P> recycledProcessors =
            new RecycledProcessors<P>(this);
        
        @Override
        public void recycle() {
            recycledProcessors.clear();
        }
        
        public SocketState process(SocketWrapper<S> socket,
                SocketStatus status) {
            P processor = connections.remove(socket);

            socket.setAsync(false); //no longer check for timeout

            try {
                if (processor == null) {
                    processor = recycledProcessors.poll();
                }
                if (processor == null) {
                    processor = createProcessor();
                }

                initSsl(socket, processor);
                
                SocketState state = SocketState.CLOSED;
                do {
                    if (processor.isAsync() || state == SocketState.ASYNC_END) {
                        state = processor.asyncDispatch(status);
                    } else if (processor.comet) {
                        state = processor.event(status);
                    } else {
                        state = processor.process(socket);
                    }
    
                    if (state != SocketState.CLOSED && processor.isAsync()) {
                        state = processor.asyncPostProcess();
                    }
                } while (state == SocketState.ASYNC_END);

                if (state == SocketState.LONG) {
                    // In the middle of processing a request/response. Keep the
                    // socket associated with the processor. Exact requirements
                    // depend on type of long poll
                    longPoll(socket, processor);
                } else if (state == SocketState.OPEN){
                    // In keep-alive but between requests. OK to recycle
                    // processor. Continue to poll for the next request.
                    release(socket, processor, false, true);
                } else {
                    // Connection closed. OK to recycle the processor.
                    release(socket, processor, true, false);
                }
                return state;
            } catch(java.net.SocketException e) {
                // SocketExceptions are normal
                getLog().debug(sm.getString(
                        "http11protocol.proto.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                getLog().debug(sm.getString(
                        "http11protocol.proto.ioexception.debug"), e);
            }
            // Future developers: if you discover any other
            // rare-but-nonfatal exceptions, catch them here, and log as
            // above.
            catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                // any other exception or error is odd. Here we log it
                // with "ERROR" level, so it will show up even on
                // less-than-verbose logs.
                getLog().error(sm.getString("http11protocol.proto.error"), e);
            }
            release(socket, processor, true, false);
            return SocketState.CLOSED;
        }
        
        protected abstract P createProcessor();
        protected abstract void initSsl(SocketWrapper<S> socket, P processor);
        protected abstract void longPoll(SocketWrapper<S> socket, P processor);
        protected abstract void release(SocketWrapper<S> socket, P processor,
                boolean socketClosing, boolean addToPoller);        
    }
}
