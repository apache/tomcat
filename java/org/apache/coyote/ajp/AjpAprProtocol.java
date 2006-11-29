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
import java.util.Hashtable;
import java.util.Iterator;
import java.util.concurrent.Executor;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ActionHook;
import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.RequestInfo;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.AprEndpoint.Handler;
import org.apache.tomcat.util.res.StringManager;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class AjpAprProtocol 
    implements ProtocolHandler, MBeanRegistration {
    
    
    protected static org.apache.juli.logging.Log log =
        org.apache.juli.logging.LogFactory.getLog(AjpAprProtocol.class);

    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


    // ------------------------------------------------------------ Constructor


    public AjpAprProtocol() {
        cHandler = new AjpConnectionHandler(this);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        //setServerSoTimeout(Constants.DEFAULT_SERVER_SOCKET_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }

    
    // ----------------------------------------------------- Instance Variables


    protected ObjectName tpOname;
    
    
    protected ObjectName rgOname;


    /**
     * Associated APR endpoint.
     */
    protected AprEndpoint ep = new AprEndpoint();


    /**
     * Configuration attributes.
     */
    protected Hashtable attributes = new Hashtable();


    /**
     * Should authentication be done in the native webserver layer, 
     * or in the Servlet container ?
     */
    protected boolean tomcatAuthentication = true;


    /**
     * Required secret.
     */
    protected String requiredSecret = null;


    /**
     * AJP packet size.
     */
    protected int packetSize = Constants.MAX_PACKET_SIZE;

    
    /**
     * Adapter which will process the requests recieved by this endpoint.
     */
    private Adapter adapter;
    
    
    /**
     * Connection handler for AJP.
     */
    private AjpConnectionHandler cHandler;


    // --------------------------------------------------------- Public Methods


    /** 
     * Pass config info
     */
    public void setAttribute(String name, Object value) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("ajpprotocol.setattribute", name, value));
        }
        attributes.put(name, value);
    }

    public Object getAttribute(String key) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("ajpprotocol.getattribute", key));
        }
        return attributes.get(key);
    }


    public Iterator getAttributeNames() {
        return attributes.keySet().iterator();
    }


    /**
     * Set a property.
     */
    public void setProperty(String name, String value) {
        setAttribute(name, value);
    }


    /**
     * Get a property
     */
    public String getProperty(String name) {
        return (String) getAttribute(name);
    }


    /**
     * The adapter, used to call the connector
     */
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }


    public Adapter getAdapter() {
        return adapter;
    }


    /** Start the protocol
     */
    public void init() throws Exception {
        ep.setName(getName());
        ep.setHandler(cHandler);
        ep.setUseSendfile(false);

        try {
            ep.init();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.initerror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled()) {
            log.info(sm.getString("ajpprotocol.init", getName()));
        }
    }


    public void start() throws Exception {
        if (this.domain != null ) {
            try {
                tpOname = new ObjectName
                    (domain + ":" + "type=ThreadPool,name=" + getName());
                Registry.getRegistry(null, null)
                    .registerComponent(ep, tpOname, null );
            } catch (Exception e) {
                log.error("Can't register threadpool" );
            }
            rgOname = new ObjectName
                (domain + ":type=GlobalRequestProcessor,name=" + getName());
            Registry.getRegistry(null, null).registerComponent
                (cHandler.global, rgOname, null);
        }

        try {
            ep.start();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.starterror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.start", getName()));
    }

    public void pause() throws Exception {
        try {
            ep.pause();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.pauseerror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.pause", getName()));
    }

    public void resume() throws Exception {
        try {
            ep.resume();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.resumeerror"), ex);
            throw ex;
        }
        if (log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.resume", getName()));
    }

    public void destroy() throws Exception {
        if (log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.stop", getName()));
        ep.destroy();
        if (tpOname!=null)
            Registry.getRegistry(null, null).unregisterComponent(tpOname);
        if (rgOname != null)
            Registry.getRegistry(null, null).unregisterComponent(rgOname);
    }


    // *
    public Executor getExecutor() {
        return ep.getExecutor();
    }
    
    // *
    public void setExecutor(Executor executor) {
        ep.setExecutor(executor);
    }
    
    public int getMaxThreads() {
        return ep.getMaxThreads();
    }

    public void setMaxThreads(int maxThreads) {
        ep.setMaxThreads(maxThreads);
        setAttribute("maxThreads", "" + maxThreads);
    }

    public void setThreadPriority(int threadPriority) {
        ep.setThreadPriority(threadPriority);
        setAttribute("threadPriority", "" + threadPriority);
    }
    
    public int getThreadPriority() {
        return ep.getThreadPriority();
    }
    

    public int getBacklog() {
        return ep.getBacklog();
    }


    public void setBacklog( int i ) {
        ep.setBacklog(i);
        setAttribute("backlog", "" + i);
    }


    public int getPort() {
        return ep.getPort();
    }


    public void setPort( int port ) {
        ep.setPort(port);
        setAttribute("port", "" + port);
    }


    public boolean getUseSendfile() {
        return ep.getUseSendfile();
    }


    public void setUseSendfile(boolean useSendfile) {
        // No sendfile for AJP
    }


    public InetAddress getAddress() {
        return ep.getAddress();
    }


    public void setAddress(InetAddress ia) {
        ep.setAddress(ia);
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
        return ("ajp-" + encodedAddr + ep.getPort());
    }


    public boolean getTcpNoDelay() {
        return ep.getTcpNoDelay();
    }


    public void setTcpNoDelay(boolean b) {
        ep.setTcpNoDelay(b);
        setAttribute("tcpNoDelay", "" + b);
    }


    public boolean getTomcatAuthentication() {
        return tomcatAuthentication;
    }


    public void setTomcatAuthentication(boolean tomcatAuthentication) {
        this.tomcatAuthentication = tomcatAuthentication;
    }


    public int getFirstReadTimeout() {
        return ep.getFirstReadTimeout();
    }


    public void setFirstReadTimeout(int i) {
        ep.setFirstReadTimeout(i);
        setAttribute("firstReadTimeout", "" + i);
    }


    public int getPollTime() {
        return ep.getPollTime();
    }


    public void setPollTime(int i) {
        ep.setPollTime(i);
        setAttribute("pollTime", "" + i);
    }


    public void setPollerSize(int i) {
        ep.setPollerSize(i); 
        setAttribute("pollerSize", "" + i);
    }


    public int getPollerSize() {
        return ep.getPollerSize();
    }


    public int getSoLinger() {
        return ep.getSoLinger();
    }


    public void setSoLinger(int i) {
        ep.setSoLinger(i);
        setAttribute("soLinger", "" + i);
    }


    public int getSoTimeout() {
        return ep.getSoTimeout();
    }


    public void setSoTimeout( int i ) {
        ep.setSoTimeout(i);
        setAttribute("soTimeout", "" + i);
    }

    
    public void setRequiredSecret(String requiredSecret) {
        this.requiredSecret = requiredSecret;
    }
    
    
    public int getPacketSize() {
        return packetSize;
    }


    public void setPacketSize(int i) {
        packetSize = i;
    }

    public int getKeepAliveTimeout() {
        return ep.getKeepAliveTimeout();
    }


    public void setKeepAliveTimeout( int i ) {
        ep.setKeepAliveTimeout(i);
        setAttribute("keepAliveTimeout", "" + i);
    }


    // --------------------------------------  AjpConnectionHandler Inner Class


    protected static class AjpConnectionHandler implements Handler {
        protected AjpAprProtocol proto;
        protected static int count = 0;
        protected RequestGroupInfo global=new RequestGroupInfo();
        protected ThreadLocal<AjpAprProcessor> localProcessor = new ThreadLocal<AjpAprProcessor>();

        public AjpConnectionHandler(AjpAprProtocol proto) {
            this.proto = proto;
        }

        // FIXME: Support for this could be added in AJP as well
        public SocketState event(long socket, SocketStatus status) {
            return SocketState.CLOSED;
        }
        
        public SocketState process(long socket) {
            AjpAprProcessor processor = null;
            try {
                processor = localProcessor.get();
                if (processor == null) {
                    processor = new AjpAprProcessor(proto.packetSize, proto.ep);
                    processor.setAdapter(proto.adapter);
                    processor.setTomcatAuthentication(proto.tomcatAuthentication);
                    processor.setRequiredSecret(proto.requiredSecret);
                    localProcessor.set(processor);
                    if (proto.getDomain() != null) {
                        synchronized (this) {
                            try {
                                RequestInfo rp = processor.getRequest().getRequestProcessor();
                                rp.setGlobalProcessor(global);
                                ObjectName rpName = new ObjectName
                                    (proto.getDomain() + ":type=RequestProcessor,worker="
                                        + proto.getName() + ",name=AjpRequest" + count++ );
                                Registry.getRegistry(null, null)
                                    .registerComponent(rp, rpName, null);
                            } catch (Exception ex) {
                                log.warn(sm.getString("ajpprotocol.request.register"));
                            }
                        }
                    }
                }

                if (processor instanceof ActionHook) {
                    ((ActionHook) processor).action(ActionCode.ACTION_START, null);
                }

                if (processor.process(socket)) {
                    return SocketState.OPEN;
                } else {
                    return SocketState.CLOSED;
                }

            } catch(java.net.SocketException e) {
                // SocketExceptions are normal
                AjpAprProtocol.log.debug
                    (sm.getString
                     ("ajpprotocol.proto.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                AjpAprProtocol.log.debug
                    (sm.getString
                     ("ajpprotocol.proto.ioexception.debug"), e);
            }
            // Future developers: if you discover any other
            // rare-but-nonfatal exceptions, catch them here, and log as
            // above.
            catch (Throwable e) {
                // any other exception or error is odd. Here we log it
                // with "ERROR" level, so it will show up even on
                // less-than-verbose logs.
                AjpAprProtocol.log.error
                    (sm.getString("ajpprotocol.proto.error"), e);
            } finally {
                if (processor instanceof ActionHook) {
                    ((ActionHook) processor).action(ActionCode.ACTION_STOP, null);
                }
            }
            return SocketState.CLOSED;
        }
    }


    // -------------------- Various implementation classes --------------------


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
