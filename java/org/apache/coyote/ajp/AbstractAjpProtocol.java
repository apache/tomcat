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
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractAjpProtocol implements ProtocolHandler, MBeanRegistration {
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);
    
    protected abstract Log getLog();
    
    protected ObjectName tpOname = null;
    protected ObjectName rgOname = null;

    protected AbstractEndpoint endpoint = null;
    
    /**
     * The adapter, used to call the connector.
     */
    protected Adapter adapter;
    
    protected HashMap<String, Object> attributes = new HashMap<String, Object>();
    
    /** 
     * Pass config info
     */
    @Override
    public void setAttribute(String name, Object value) {
        if (getLog().isTraceEnabled()) {
            getLog().trace(sm.getString("ajpprotocol.setattribute", name, value));
        }
        attributes.put(name, value);
    }

    @Override
    public Object getAttribute(String key) {
        if (getLog().isTraceEnabled()) {
            getLog().trace(sm.getString("ajpprotocol.getattribute", key));
        }
        return attributes.get(key);
    }


    @Override
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

    /**
     * The adapter, used to call the connector
     */
    @Override
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }


    @Override
    public Adapter getAdapter() {
        return adapter;
    }

    @Override
    public void pause() throws Exception {
        try {
            endpoint.pause();
        } catch (Exception ex) {
            getLog().error(sm.getString("ajpprotocol.endpoint.pauseerror"), ex);
            throw ex;
        }
        if (getLog().isInfoEnabled())
            getLog().info(sm.getString("ajpprotocol.pause", getName()));
    }

    @Override
    public void resume() throws Exception {
        try {
            endpoint.resume();
        } catch (Exception ex) {
            getLog().error(sm.getString("ajpprotocol.endpoint.resumeerror"), ex);
            throw ex;
        }
        if (getLog().isInfoEnabled())
            getLog().info(sm.getString("ajpprotocol.resume", getName()));
    }

    @Override
    public void stop() throws Exception {
        try {
            endpoint.stop();
        } catch (Exception ex) {
            getLog().error(sm.getString("ajpprotocol.endpoint.stoperror"), ex);
            throw ex;
        }
        if (getLog().isInfoEnabled())
            getLog().info(sm.getString("ajpprotocol.stop", getName()));
    }

    @Override
    public void destroy() throws Exception {
        if (getLog().isInfoEnabled())
            getLog().info(sm.getString("ajpprotocol.destroy", getName()));
        endpoint.destroy();
        if (tpOname!=null)
            Registry.getRegistry(null, null).unregisterComponent(tpOname);
        if (rgOname != null)
            Registry.getRegistry(null, null).unregisterComponent(rgOname);
    }

    // *
    public String getName() {
        String encodedAddr = "";
        if (getAddress() != null) {
            encodedAddr = "" + getAddress();
            if (encodedAddr.startsWith("/"))
                encodedAddr = encodedAddr.substring(1);
            encodedAddr = URLEncoder.encode(encodedAddr) + "-";
        }
        return ("ajp-" + encodedAddr + endpoint.getPort());
    }

    /**
     * Processor cache.
     */
    protected int processorCache = -1;
    public int getProcessorCache() { return this.processorCache; }
    public void setProcessorCache(int processorCache) { this.processorCache = processorCache; }

    @Override
    public Executor getExecutor() { return endpoint.getExecutor(); }
    public void setExecutor(Executor executor) { endpoint.setExecutor(executor); }
    
    public int getMaxThreads() { return endpoint.getMaxThreads(); }
    public void setMaxThreads(int maxThreads) { endpoint.setMaxThreads(maxThreads); }

    public int getThreadPriority() { return endpoint.getThreadPriority(); }
    public void setThreadPriority(int threadPriority) { endpoint.setThreadPriority(threadPriority); }

    public int getBacklog() { return endpoint.getBacklog(); }
    public void setBacklog(int backlog) { endpoint.setBacklog(backlog); }

    public int getPort() { return endpoint.getPort(); }
    public void setPort(int port) { endpoint.setPort(port); }

    public InetAddress getAddress() { return endpoint.getAddress(); }
    public void setAddress(InetAddress ia) { endpoint.setAddress(ia); }

    public boolean getTcpNoDelay() { return endpoint.getTcpNoDelay(); }
    public void setTcpNoDelay(boolean tcpNoDelay) { endpoint.setTcpNoDelay(tcpNoDelay); }

    public int getSoLinger() { return endpoint.getSoLinger(); }
    public void setSoLinger(int soLinger) { endpoint.setSoLinger(soLinger); }

    public int getSoTimeout() { return endpoint.getSoTimeout(); }
    public void setSoTimeout(int soTimeout) { endpoint.setSoTimeout(soTimeout); }

    /**
     * Should authentication be done in the native webserver layer, 
     * or in the Servlet container ?
     */
    protected boolean tomcatAuthentication = true;
    public boolean getTomcatAuthentication() { return tomcatAuthentication; }
    public void setTomcatAuthentication(boolean tomcatAuthentication) { this.tomcatAuthentication = tomcatAuthentication; }

    /**
     * Required secret.
     */
    protected String requiredSecret = null;
    public void setRequiredSecret(String requiredSecret) { this.requiredSecret = requiredSecret; }
    
    /**
     * AJP packet size.
     */
    protected int packetSize = Constants.MAX_PACKET_SIZE;
    public int getPacketSize() { return packetSize; }
    public void setPacketSize(int packetSize) {
        if(packetSize < Constants.MAX_PACKET_SIZE) {
            this.packetSize = Constants.MAX_PACKET_SIZE;
        } else {
            this.packetSize = packetSize;
        }
    }

    
    /**
     * The number of seconds Tomcat will wait for a subsequent request
     * before closing the connection.
     */
    protected int keepAliveTimeout = -1;
    public int getKeepAliveTimeout() { return keepAliveTimeout; }
    public void setKeepAliveTimeout(int timeout) { keepAliveTimeout = timeout; }

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
