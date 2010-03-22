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
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executor;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractHttp11Protocol implements ProtocolHandler, MBeanRegistration {
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);
    
    protected abstract Log getLog();
    
    protected ObjectName tpOname = null;
    protected ObjectName rgOname = null;

    protected AbstractEndpoint endpoint=null;
    
    protected SSLImplementation sslImplementation = null;
    
    /**
     * The adapter, used to call the connector.
     */
    protected Adapter adapter;
    public void setAdapter(Adapter adapter) { this.adapter = adapter; }
    public Adapter getAdapter() { return adapter; }

    
    protected HashMap<String, Object> attributes = new HashMap<String, Object>();

    
    /**
     * Pass config info
     */
    public void setAttribute(String name, Object value) {
        if (getLog().isTraceEnabled()) {
            getLog().trace(sm.getString("http11protocol.setattribute", name, value));
        }
        attributes.put(name, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }

    /**
     * Set a property.
     */
    public boolean setProperty(String name, String value) {
        setAttribute(name, value); //store all settings
        if ( name!=null && (name.startsWith("socket.") ||name.startsWith("selectorPool.")) ){
            return endpoint.setProperty(name, value);
        } else {
            return endpoint.setProperty(name,value); //make sure we at least try to set all properties
        }
        
    }

    /**
     * Get a property
     */
    public String getProperty(String name) {
        return (String)getAttribute(name);
    }
    
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

    public void destroy() throws Exception {
        if(getLog().isInfoEnabled())
            getLog().info(sm.getString("http11protocol.stop", getName()));
        endpoint.destroy();
        if( tpOname!=null )
            Registry.getRegistry(null, null).unregisterComponent(tpOname);
        if( rgOname != null )
            Registry.getRegistry(null, null).unregisterComponent(rgOname);
    }
    
    public boolean isSSLEnabled() { return endpoint.isSSLEnabled();}
    public void setSSLEnabled(boolean SSLEnabled) { endpoint.setSSLEnabled(SSLEnabled);}    
    
    private boolean secure;
    public boolean getSecure() { return secure; }
    public void setSecure(boolean b) { 
        secure = b;         
        setAttribute("secure", "" + b);
    }
    
    /**
     * Processor cache.
     */
    private int processorCache = 200;
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
    
    public Executor getExecutor() { return endpoint.getExecutor(); }
    public void setExecutor(Executor executor) { endpoint.setExecutor(executor); }
    
    
    public int getMaxThreads() { return endpoint.getMaxThreads(); }
    public void setMaxThreads(int maxThreads) { endpoint.setMaxThreads(maxThreads); }


    public int getThreadPriority() { return endpoint.getThreadPriority(); }
    public void setThreadPriority(int threadPriority) { endpoint.setThreadPriority(threadPriority); }

    public int getPort() { return endpoint.getPort(); }
    public void setPort(int port) { endpoint.setPort(port); }

    public int getBacklog() { return endpoint.getBacklog(); }
    public void setBacklog(int backlog) { endpoint.setBacklog(backlog); }


    public boolean getTcpNoDelay() { return endpoint.getTcpNoDelay(); }
    public void setTcpNoDelay(boolean tcpNoDelay) { endpoint.setTcpNoDelay(tcpNoDelay); }

    public int getSoLinger() { return endpoint.getSoLinger(); }
    public void setSoLinger(int soLinger) { endpoint.setSoLinger(soLinger); }

    // JSSE SSL attrbutes
    public String getAlgorithm() { return endpoint.getAlgorithm();}
    public void setAlgorithm(String s ) { endpoint.setAlgorithm(s);}
    
    public String getClientAuth() { return endpoint.getClientAuth();}
    public void setClientAuth(String s ) { endpoint.setClientAuth(s);}

    public String getKeystoreFile() { return endpoint.getKeystoreFile();}
    public void setKeystoreFile(String s ) { endpoint.setKeystoreFile(s);}

    public String getKeystorePass() { return endpoint.getKeystorePass();}
    public void setKeystorePass(String s ) { endpoint.setKeystorePass(s);}
    
    public String getKeystoreType() { return endpoint.getKeystoreType();}
    public void setKeystoreType(String s ) { endpoint.setKeystoreType(s);}

    public String getKeystoreProvider() { return endpoint.getKeystoreProvider();}
    public void setKeystoreProvider(String s ) { endpoint.setKeystoreProvider(s);}

    public String getSslProtocol() { return endpoint.getSslProtocol();}
    public void setSslProtocol(String s) { endpoint.setSslProtocol(s);}
    
    public String getCiphers() { return endpoint.getCiphers();}
    public void setCiphers(String s) { endpoint.setCiphers(s);}

    public String getKeyAlias() { return endpoint.getKeyAlias();}
    public void setKeyAlias(String s ) { endpoint.setKeyAlias(s);}

    public String getKeyPass() { return endpoint.getKeyPass();}
    public void setKeyPass(String s ) { endpoint.setKeyPass(s);}
    
    public void setTruststoreFile(String f){ endpoint.setTruststoreFile(f);}
    public String getTruststoreFile(){ return endpoint.getTruststoreFile();}

    public void setTruststorePass(String p){ endpoint.setTruststorePass(p);}
    public String getTruststorePass(){return endpoint.getTruststorePass();}

    public void setTruststoreType(String t){ endpoint.setTruststoreType(t);}
    public String getTruststoreType(){ return endpoint.getTruststoreType();}

    public void setTruststoreProvider(String t){endpoint.setTruststoreProvider(t);}
    public String getTruststoreProvider(){ return endpoint.getTruststoreProvider();}

    public void setTruststoreAlgorithm(String a){endpoint.setTruststoreAlgorithm(a);}
    public String getTruststoreAlgorithm(){ return endpoint.getTruststoreAlgorithm();}
    
    public void setTrustMaxCertLength(String s){endpoint.setTrustMaxCertLength(s);}
    public String getTrustMaxCertLength(){ return endpoint.getTrustMaxCertLength();}
    
    public void setCrlFile(String s){endpoint.setCrlFile(s);}
    public String getCrlFile(){ return endpoint.getCrlFile();}
    
    public void setSessionCacheSize(String s){endpoint.setSessionCacheSize(s);}
    public String getSessionCacheSize(){ return endpoint.getTruststoreAlgorithm();}

    public void setSessionTimeout(String s){endpoint.setTruststoreAlgorithm(s);}
    public String getSessionTimeout(){ return endpoint.getTruststoreAlgorithm();}
    
    public void setAllowUnsafeLegacyRenegotiation(String s) {
        endpoint.setAllowUnsafeLegacyRenegotiation(s);
    }
    public String getAllowUnsafeLegacyRenegotiation() {
        return endpoint.getAllowUnsafeLegacyRenegotiation();
    }
    
    public abstract void init() throws Exception;
    public abstract void start() throws Exception;
    
    // -------------------- JMX related methods --------------------

    // *
    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();
        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }


    
}
