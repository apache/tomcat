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

import java.net.InetAddress;
import java.net.URLEncoder;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.coyote.AbstractProtocolHandler;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractHttp11Protocol extends AbstractProtocolHandler {
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    protected SSLImplementation sslImplementation = null;
    
    
    public InetAddress getAddress() { return endpoint.getAddress(); }
    public void setAddress(InetAddress ia) {
        endpoint.setAddress( ia );
        setAttribute("address", "" + ia);
    }
    
    public String getName() {
        String encodedAddr = "";
        if (getAddress() != null) {
            encodedAddr = "" + getAddress();
            if (encodedAddr.startsWith("/"))
                encodedAddr = encodedAddr.substring(1);
            encodedAddr = URLEncoder.encode(encodedAddr) + "-";
        }
        return ("http-" + encodedAddr + endpoint.getPort());
    }
    
    
    @Override
    public void pause() throws Exception {
        try {
            endpoint.pause();
        } catch (Exception ex) {
            getLog().error(sm.getString("http11protocol.endpoint.pauseerror"), ex);
            throw ex;
        }
        if(getLog().isInfoEnabled())
            getLog().info(sm.getString("http11protocol.pause", getName()));
    }

    @Override
    public void resume() throws Exception {
        try {
            endpoint.resume();
        } catch (Exception ex) {
            getLog().error(sm.getString("http11protocol.endpoint.resumeerror"), ex);
            throw ex;
        }
        if(getLog().isInfoEnabled())
            getLog().info(sm.getString("http11protocol.resume", getName()));
    }

    @Override
    public void stop() throws Exception {
        try {
            endpoint.stop();
        } catch (Exception ex) {
            getLog().error(sm.getString("http11protocol.endpoint.stoperror"), ex);
            throw ex;
        }
        if(getLog().isInfoEnabled())
            getLog().info(sm.getString("http11protocol.stop", getName()));
    }

    @Override
    public void destroy() throws Exception {
        if(getLog().isInfoEnabled())
            getLog().info(sm.getString("http11protocol.destroy", getName()));
        endpoint.destroy();
        if( tpOname!=null )
            Registry.getRegistry(null, null).unregisterComponent(tpOname);
        if( rgOname != null )
            Registry.getRegistry(null, null).unregisterComponent(rgOname);
    }
    
    public boolean isSSLEnabled() { return endpoint.isSSLEnabled();}
    public void setSSLEnabled(boolean SSLEnabled) { endpoint.setSSLEnabled(SSLEnabled);}    
    
    /**
     * This field indicates if the protocol is secure from the perspective of
     * the client (= https is used).
     */
    private boolean secure;
    public boolean getSecure() { return secure; }
    public void setSecure(boolean b) { 
        secure = b;         
        setAttribute("secure", "" + b);
    }
    
    /**
     * Processor cache.
     */
    private int processorCache;
    public int getProcessorCache() { return this.processorCache; }
    public void setProcessorCache(int processorCache) { this.processorCache = processorCache; }

    private int socketBuffer = 9000;
    public int getSocketBuffer() { return socketBuffer; }
    public void setSocketBuffer(int socketBuffer) { this.socketBuffer = socketBuffer; }

    // HTTP
    /**
     * Maximum number of requests which can be performed over a keepalive 
     * connection. The default is the same as for Apache HTTP Server.
     */
    public int getMaxKeepAliveRequests() { return endpoint.getMaxKeepAliveRequests(); }
    public void setMaxKeepAliveRequests(int mkar) {
        endpoint.setMaxKeepAliveRequests(mkar);
        setAttribute("maxKeepAliveRequests", "" + mkar);
    }
    
    /**
     * Return the Keep-Alive policy for the connection.
     */
    public boolean getKeepAlive() {
        return ((endpoint.getMaxKeepAliveRequests() != 0) && (endpoint.getMaxKeepAliveRequests() != 1));
    }

    /**
     * Set the keep-alive policy for this connection.
     */
    public void setKeepAlive(boolean keepAlive) {
        if (!keepAlive) {
            setMaxKeepAliveRequests(1);
        }
    }

    /**
     * The number of seconds Tomcat will wait for a subsequent request
     * before closing the connection.
     */
    public void setKeepAliveTimeout(int keepAliveTimeout) {
        endpoint.setKeepAliveTimeout(keepAliveTimeout);
    }
    
    public int getKeepAliveTimeout() {
        return endpoint.getKeepAliveTimeout();
    }

    public int getTimeout() {
        return getSoTimeout();
    }

    public void setTimeout( int timeout ) {
        setSoTimeout(timeout);
    }
    
    public int getConnectionTimeout() {
        return getSoTimeout();
    }

    public void setConnectionTimeout( int timeout ) {
        setSoTimeout(timeout);
    }

    public int getSoTimeout() {
        return endpoint.getSoTimeout();
    }

    public void setSoTimeout( int i ) {
        endpoint.setSoTimeout(i);
        setAttribute("soTimeout", "" + i);
        setAttribute("timeout", "" + i);
        setAttribute("connectionTimeout", "" + i);
    }
    
    // *
    /**
     * Maximum size of the post which will be saved when processing certain
     * requests, such as a POST.
     */
    private int maxSavePostSize = 4 * 1024;
    public int getMaxSavePostSize() { return maxSavePostSize; }
    public void setMaxSavePostSize(int valueI) { maxSavePostSize = valueI; }
    

    // HTTP
    /**
     * Maximum size of the HTTP message header.
     */
    private int maxHttpHeaderSize = 8 * 1024;
    public int getMaxHttpHeaderSize() { return maxHttpHeaderSize; }
    public void setMaxHttpHeaderSize(int valueI) { maxHttpHeaderSize = valueI; }

    
    // HTTP
    /**
     * If true, the regular socket timeout will be used for the full duration
     * of the connection.
     */
    private boolean disableUploadTimeout = true;
    public boolean getDisableUploadTimeout() { return disableUploadTimeout; }
    public void setDisableUploadTimeout(boolean isDisabled) { disableUploadTimeout = isDisabled; }
    
    
    // HTTP
    /**
     * Integrated compression support.
     */
    private String compression = "off";
    public String getCompression() { return compression; }
    public void setCompression(String valueS) { compression = valueS; }
        

    // HTTP
    private String noCompressionUserAgents = null;
    public String getNoCompressionUserAgents() { return noCompressionUserAgents; }
    public void setNoCompressionUserAgents(String valueS) { noCompressionUserAgents = valueS; }
    
    // HTTP
    private String compressableMimeTypes = "text/html,text/xml,text/plain";
    public String getCompressableMimeType() { return compressableMimeTypes; }
    public void setCompressableMimeType(String valueS) { compressableMimeTypes = valueS; }
    public String getCompressableMimeTypes() { return getCompressableMimeType(); }
    public void setCompressableMimeTypes(String valueS) { setCompressableMimeType(valueS); }
    
    // HTTP
    private int compressionMinSize = 2048;
    public int getCompressionMinSize() { return compressionMinSize; }
    public void setCompressionMinSize(int valueI) { compressionMinSize = valueI; }
 
    // HTTP
    /**
     * User agents regular expressions which should be restricted to HTTP/1.0 support.
     */
    private String restrictedUserAgents = null;
    public String getRestrictedUserAgents() { return restrictedUserAgents; }
    public void setRestrictedUserAgents(String valueS) { restrictedUserAgents = valueS; }

    // HTTP
    /**
     * Server header.
     */
    private String server;
    public void setServer( String server ) { this.server = server; }
    public String getServer() { return server; }
    
    // HTTP
    /**
     * Maximum size of trailing headers in bytes
     */
    private int maxTrailerSize = 8192;
    public int getMaxTrailerSize() { return maxTrailerSize; }
    public void setMaxTrailerSize(int maxTrailerSize) {
        this.maxTrailerSize = maxTrailerSize;
    }

    @Override
    public abstract void init() throws Exception;
    
    // -------------------- JMX related methods --------------------

    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    @Override
    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();
        return name;
    }

    @Override
    public void postRegister(Boolean registrationDone) {
        // NOOP
    }

    @Override
    public void preDeregister() throws Exception {
        // NOOP
    }

    @Override
    public void postDeregister() {
        // NOOP
    }
}
