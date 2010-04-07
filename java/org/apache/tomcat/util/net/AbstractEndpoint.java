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

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.StringTokenizer;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.net.jsse.JSSESocketFactory;
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
    private static final Log log = LogFactory.getLog(AbstractEndpoint.class);
    
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
    
    // Standard SSL Configuration attributes
    // JSSE
    // Standard configuration attribute names
    public static final String SSL_ATTR_ALGORITHM = "algorithm";
    public static final String SSL_ATTR_CLIENT_AUTH = "clientAuth";
    public static final String SSL_ATTR_KEYSTORE_FILE = "keystoreFile";
    public static final String SSL_ATTR_KEYSTORE_PASS = "keystorePass";
    public static final String SSL_ATTR_KEYSTORE_TYPE = "keystoreType";
    public static final String SSL_ATTR_KEYSTORE_PROVIDER = "keystoreProvider";
    public static final String SSL_ATTR_SSL_PROTOCOL = "sslProtocol";
    public static final String SSL_ATTR_CIPHERS = "ciphers";
    public static final String SSL_ATTR_CIPHERS_ARRAY = "ciphersArray";
    public static final String SSL_ATTR_KEY_ALIAS = "keyAlias";
    public static final String SSL_ATTR_KEY_PASS = "keyPass";
    public static final String SSL_ATTR_TRUSTSTORE_FILE = "truststoreFile";
    public static final String SSL_ATTR_TRUSTSTORE_PASS = "truststorePass";
    public static final String SSL_ATTR_TRUSTSTORE_TYPE = "truststoreType";
    public static final String SSL_ATTR_TRUSTSTORE_PROVIDER =
        "truststoreProvider";
    public static final String SSL_ATTR_TRUSTSTORE_ALGORITHM =
        "truststoreAlgorithm";
    public static final String SSL_ATTR_CRL_FILE =
        "crlFile";
    public static final String SSL_ATTR_TRUST_MAX_CERT_LENGTH =
        "trustMaxCertLength";
    public static final String SSL_ATTR_SESSION_CACHE_SIZE =
        "sessionCacheSize";
    public static final String SSL_ATTR_SESSION_TIMEOUT =
        "sessionTimeout";
    public static final String SSL_ATTR_ALLOW_UNSAFE_RENEG =
        "allowUnsafeLegacyRenegotiation";

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

    private int maxConnections = 10000;
    public void setMaxConnections(int maxCon) { this.maxConnections = maxCon; }
    public int  getMaxConnections() { return this.maxConnections; }
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
                return IntrospectionUtils.setProperty(this,name,value,false);
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
                saddr = new InetSocketAddress("localhost", getPort());
            } else {
                saddr = new InetSocketAddress(address,getPort());
            }
            s = new java.net.Socket();
            s.setSoTimeout(getSocketProperties().getSoTimeout());
            // TODO Consider hard-coding to s.setSoLinger(true,0)
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
    
    public abstract void pause();
    public abstract void resume();
    public abstract void start() throws Exception;
    public abstract void destroy() throws Exception;
    public abstract void init() throws Exception;
    
    public String adjustRelativePath(String path, String relativeTo) {
        File f = new File(path);
        if ( !f.isAbsolute()) {
            path = relativeTo + File.separator + path;
            f = new File(path);
        }
        if (!f.exists()) {
            log.warn("configured file:["+path+"] does not exist.");
        }
        return path;
    }
    
    public String defaultIfNull(String val, String defaultValue) {
        if (val==null) return defaultValue;
        return val;
    }
    
    // --------------------  SSL related properties --------------------

    private String algorithm = KeyManagerFactory.getDefaultAlgorithm();
    public String getAlgorithm() { return algorithm;}
    public void setAlgorithm(String s ) { this.algorithm = s;}

    private String clientAuth = "false";
    public String getClientAuth() { return clientAuth;}
    public void setClientAuth(String s ) { this.clientAuth = s;}
    
    private String keystoreFile = System.getProperty("user.home")+"/.keystore";
    public String getKeystoreFile() { return keystoreFile;}
    public void setKeystoreFile(String s ) { 
        String file = adjustRelativePath(s,System.getProperty("catalina.base"));
        this.keystoreFile = file; 
    }

    private String keystorePass = null;
    public String getKeystorePass() { return keystorePass;}
    public void setKeystorePass(String s ) { this.keystorePass = s;}
    
    private String keystoreType = "JKS";
    public String getKeystoreType() { return keystoreType;}
    public void setKeystoreType(String s ) { this.keystoreType = s;}

    private String keystoreProvider = null;
    public String getKeystoreProvider() { return keystoreProvider;}
    public void setKeystoreProvider(String s ) { this.keystoreProvider = s;}

    private String sslProtocol = "TLS"; 
    public String getSslProtocol() { return sslProtocol;}
    public void setSslProtocol(String s) { sslProtocol = s;}
    
    // Note: Some implementations use the comma separated string, some use
    // the array
    private String ciphers = null;
    private String[] ciphersarr = new String[0];
    public String[] getCiphersArray() { return this.ciphersarr;}
    public String getCiphers() { return ciphers;}
    public void setCiphers(String s) { 
        ciphers = s;
        if ( s == null ) ciphersarr = new String[0];
        else {
            StringTokenizer t = new StringTokenizer(s,",");
            ciphersarr = new String[t.countTokens()];
            for (int i=0; i<ciphersarr.length; i++ ) ciphersarr[i] = t.nextToken();
        }
    }

    private String keyAlias = null;
    public String getKeyAlias() { return keyAlias;}
    public void setKeyAlias(String s ) { keyAlias = s;}
    
    private String keyPass = JSSESocketFactory.DEFAULT_KEY_PASS;
    public String getKeyPass() { return keyPass;}
    public void setKeyPass(String s ) { this.keyPass = s;}

    private String truststoreFile = System.getProperty("javax.net.ssl.trustStore");
    public String getTruststoreFile() {return truststoreFile;}
    public void setTruststoreFile(String s) {
        String file = adjustRelativePath(s,System.getProperty("catalina.base"));
        this.truststoreFile = file;
    }

    private String truststorePass =
        System.getProperty("javax.net.ssl.trustStorePassword");
    public String getTruststorePass() {return truststorePass;}
    public void setTruststorePass(String truststorePass) {
        this.truststorePass = truststorePass;
    }
    
    private String truststoreType =
        System.getProperty("javax.net.ssl.trustStoreType");
    public String getTruststoreType() {return truststoreType;}
    public void setTruststoreType(String truststoreType) {
        this.truststoreType = truststoreType;
    }

    private String truststoreProvider = null;
    public String getTruststoreProvider() {return truststoreProvider;}
    public void setTruststoreProvider(String truststoreProvider) {
        this.truststoreProvider = truststoreProvider;
    }

    private String truststoreAlgorithm = null;
    public String getTruststoreAlgorithm() {return truststoreAlgorithm;}
    public void setTruststoreAlgorithm(String truststoreAlgorithm) {
        this.truststoreAlgorithm = truststoreAlgorithm;
    }

    private String crlFile = null;
    public String getCrlFile() {return crlFile;}
    public void setCrlFile(String crlFile) {
        this.crlFile = crlFile;
    }

    private String trustMaxCertLength = null;
    public String getTrustMaxCertLength() {return trustMaxCertLength;}
    public void setTrustMaxCertLength(String trustMaxCertLength) {
        this.trustMaxCertLength = trustMaxCertLength;
    }

    private String sessionCacheSize = null;
    public String getSessionCacheSize() { return sessionCacheSize;}
    public void setSessionCacheSize(String s) { sessionCacheSize = s;}

    private String sessionTimeout = "86400";
    public String getSessionTimeout() { return sessionTimeout;}
    public void setSessionTimeout(String s) { sessionTimeout = s;}

    private String allowUnsafeLegacyRenegotiation = null;
    public String getAllowUnsafeLegacyRenegotiation() {
        return allowUnsafeLegacyRenegotiation;
    }
    public void setAllowUnsafeLegacyRenegotiation(String s) {
        allowUnsafeLegacyRenegotiation = s;
    }

    
    
    private String sslEnabledProtocols=null; //"TLSv1,SSLv3,SSLv2Hello"
    private String[] sslEnabledProtocolsarr =  new String[0];
    public String[] getSslEnabledProtocolsArray() { return this.sslEnabledProtocolsarr;}
    public void setSslEnabledProtocols(String s) {
        this.sslEnabledProtocols = s;
        StringTokenizer t = new StringTokenizer(s,",");
        sslEnabledProtocolsarr = new String[t.countTokens()];
        for (int i=0; i<sslEnabledProtocolsarr.length; i++ ) sslEnabledProtocolsarr[i] = t.nextToken();
    }
        
}

