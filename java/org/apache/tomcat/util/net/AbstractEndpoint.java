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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.ResizableExecutor;
import org.apache.tomcat.util.threads.TaskQueue;
import org.apache.tomcat.util.threads.TaskThreadFactory;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
/**
 * 
 * @author fhanik
 * @author Mladen Turk
 * @author Remy Maucherat
 */
public abstract class AbstractEndpoint {
    protected static final Log log = LogFactory.getLog(AbstractEndpoint.class);
    
    // -------------------------------------------------------------- Constants
    protected static final StringManager sm = StringManager.getManager("org.apache.tomcat.util.net.res");

    /**
     * The Request attribute key for the cipher suite.
     */
    public static final String CIPHER_SUITE_KEY = "javax.servlet.request.cipher_suite";

    /**
     * The Request attribute key for the key size.
     */
    public static final String KEY_SIZE_KEY = "javax.servlet.request.key_size";

    /**
     * The Request attribute key for the client certificate chain.
     */
    public static final String CERTIFICATE_KEY = "javax.servlet.request.X509Certificate";

    /**
     * The Request attribute key for the session id.
     * This one is a Tomcat extension to the Servlet spec.
     */
    public static final String SESSION_ID_KEY = "javax.servlet.request.ssl_session";

    /**
     * The request attribute key for the session manager.
     * This one is a Tomcat extension to the Servlet spec.
     */
    public static final String SESSION_MGR = "javax.servlet.request.ssl_session_mgr";
   
    /**
     * Different types of socket states to react upon
     */
    public static interface Handler {
        public enum SocketState {
            OPEN, CLOSED, LONG
        }
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
     * Track the initialization state of the endpoint.
     */
    protected boolean initialized = false;
    
    /**
     * Are we using an internal executor
     */
    protected volatile boolean internalExecutor = false;
    
    /**
     * Socket properties
     */
    protected SocketProperties socketProperties = new SocketProperties();
    public SocketProperties getSocketProperties() {
        return socketProperties;
    }




    
    // ----------------------------------------------------------------- Properties

    /**
     * External Executor based thread pool.
     */
    private Executor executor = null;
    public void setExecutor(Executor executor) { 
        this.executor = executor;
        this.internalExecutor = (executor==null);
    }
    public Executor getExecutor() { return executor; }

    
    /**
     * Server socket port.
     */
    private int port;
    public int getPort() { return port; }
    public void setPort(int port ) { this.port=port; }


    /**
     * Address for the server socket.
     */
    private InetAddress address;
    public InetAddress getAddress() { return address; }
    public void setAddress(InetAddress address) { this.address = address; }
    
    /**
     * Allows the server developer to specify the backlog that
     * should be used for server sockets. By default, this value
     * is 100.
     */
    private int backlog = 100;
    public void setBacklog(int backlog) { if (backlog > 0) this.backlog = backlog; }
    public int getBacklog() { return backlog; }

    /**
     * Keepalive timeout, if lesser or equal to 0 then soTimeout will be used.
     */
    private int keepAliveTimeout = 0;
    public void setKeepAliveTimeout(int keepAliveTimeout) { this.keepAliveTimeout = keepAliveTimeout; }
    public int getKeepAliveTimeout() { return keepAliveTimeout;}


    /**
     * Socket TCP no delay.
     */
    public boolean getTcpNoDelay() { return socketProperties.getTcpNoDelay();}
    public void setTcpNoDelay(boolean tcpNoDelay) { socketProperties.setTcpNoDelay(tcpNoDelay); }


    /**
     * Socket linger.
     */
    public int getSoLinger() { return socketProperties.getSoLingerTime(); }
    public void setSoLinger(int soLinger) { 
        socketProperties.setSoLingerTime(soLinger);
        socketProperties.setSoLingerOn(soLinger>=0);
    }


    /**
     * Socket timeout.
     */
    public int getSoTimeout() { return socketProperties.getSoTimeout(); }
    public void setSoTimeout(int soTimeout) { socketProperties.setSoTimeout(soTimeout); }

    /**
     * SSL engine.
     */
    private boolean SSLEnabled = false;
    public boolean isSSLEnabled() { return SSLEnabled; }
    public void setSSLEnabled(boolean SSLEnabled) { this.SSLEnabled = SSLEnabled; }


    private int minSpareThreads = 10;
    public int getMinSpareThreads() {
        return Math.min(minSpareThreads,getMaxThreads());
    }
    public void setMinSpareThreads(int minSpareThreads) {
        this.minSpareThreads = minSpareThreads;
        if (running && executor!=null) {
            if (executor instanceof java.util.concurrent.ThreadPoolExecutor) {
                ((java.util.concurrent.ThreadPoolExecutor)executor).setCorePoolSize(maxThreads);
            } else if (executor instanceof ResizableExecutor) {
                ((ResizableExecutor)executor).resizePool(minSpareThreads, maxThreads);
            }
        }
    }
    
    /**
     * Maximum amount of worker threads.
     */
    private int maxThreads = 200;
    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        if (running && executor!=null) {
            if (executor instanceof java.util.concurrent.ThreadPoolExecutor) {
                ((java.util.concurrent.ThreadPoolExecutor)executor).setMaximumPoolSize(maxThreads);
            } else if (executor instanceof ResizableExecutor) {
                ((ResizableExecutor)executor).resizePool(minSpareThreads, maxThreads);
            }
        }
    }
    public int getMaxThreads() {
        if (running && executor!=null) {
            if (executor instanceof java.util.concurrent.ThreadPoolExecutor) {
                return ((java.util.concurrent.ThreadPoolExecutor)executor).getMaximumPoolSize();
            } else if (executor instanceof ResizableExecutor) {
                return ((ResizableExecutor)executor).getMaxThreads();
            } else {
                return -1;
            }
        } else {
            return maxThreads;
        }
    }

    /**
     * Max keep alive requests 
     */
    private int maxKeepAliveRequests=100; // as in Apache HTTPD server
    public int getMaxKeepAliveRequests() {
        return maxKeepAliveRequests;
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
     * The default is true - the created threads will be
     *  in daemon mode. If set to false, the control thread
     *  will not be daemon - and will keep the process alive.
     */
    private boolean daemon = true;
    public void setDaemon(boolean b) { daemon = b; }
    public boolean getDaemon() { return daemon; }


    /**
     * Priority of the worker threads.
     */
    protected int threadPriority = Thread.NORM_PRIORITY;
    public void setThreadPriority(int threadPriority) { this.threadPriority = threadPriority; }
    public int getThreadPriority() { return threadPriority; }



    /**
     * Generic properties, introspected
     */
    public boolean setProperty(String name, String value) {
        final String socketName = "socket.";
        try {
            if (name.startsWith(socketName)) {
                return IntrospectionUtils.setProperty(socketProperties, name.substring(socketName.length()), value);
            } else {
                return IntrospectionUtils.setProperty(this,name,value);
            }
        }catch ( Exception x ) {
            log.error("Unable to set attribute \""+name+"\" to \""+value+"\"",x);
            return false;
        }
    }

    /**
     * Return the amount of threads that are managed by the pool.
     *
     * @return the amount of threads that are managed by the pool
     */
    public int getCurrentThreadCount() {
        if (executor!=null) {
            if (executor instanceof ThreadPoolExecutor) {
                return ((ThreadPoolExecutor)executor).getPoolSize();
            } else if (executor instanceof ResizableExecutor) {
                return ((ResizableExecutor)executor).getPoolSize();
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
        if (executor!=null) {
            if (executor instanceof ThreadPoolExecutor) {
                return ((ThreadPoolExecutor)executor).getActiveCount();
            } else if (executor instanceof ResizableExecutor) {
                return ((ResizableExecutor)executor).getActiveCount();
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
        TaskQueue taskqueue = new TaskQueue();
        TaskThreadFactory tf = new TaskThreadFactory(getName() + "-exec-", daemon, getThreadPriority());
        executor = new ThreadPoolExecutor(getMinSpareThreads(), getMaxThreads(), 60, TimeUnit.SECONDS,taskqueue, tf);
        taskqueue.setParent( (ThreadPoolExecutor) executor);
    }
    
    public void shutdownExecutor() {
        if ( executor!=null && internalExecutor ) {
            if ( executor instanceof ThreadPoolExecutor ) {
                //this is our internal one, so we need to shut it down
                ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
                tpe.shutdownNow();
                TaskQueue queue = (TaskQueue) tpe.getQueue();
                queue.setParent(null);
            }
            executor = null;
        }
    }

    /**
     * Unlock the server socket accept using a bogus connection.
     */
    protected void unlockAccept() {
        java.net.Socket s = null;
        InetSocketAddress saddr = null;
        try {
            // Need to create a connection to unlock the accept();
            if (address == null) {
                //TODO fix IPv6
                saddr = new InetSocketAddress("127.0.0.1", getPort());
            } else {
                saddr = new InetSocketAddress(address,getPort());
            }
            s = new java.net.Socket();
            s.setSoTimeout(getSocketProperties().getSoTimeout());
            s.setSoLinger(getSocketProperties().getSoLingerOn(),getSocketProperties().getSoLingerTime());
            if (log.isDebugEnabled()) {
                log.debug("About to unlock socket for:"+saddr);
            }
            s.connect(saddr,getSocketProperties().getUnlockTimeout());
            if (log.isDebugEnabled()) {
                log.debug("Socket unlock completed for:"+saddr);
            }
        } catch(Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.debug.unlock", "" + getPort()), e);
            }
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }    
    
}

