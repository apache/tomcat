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

package org.apache.tomcat.util.net;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;

import org.apache.catalina.Globals;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;


/**
 * Handle incoming TCP connections.
 *
 * This class implement a simple server model: one listener thread accepts on a socket and
 * creates a new worker thread for each incoming connection.
 *
 * More advanced Endpoints will reuse the threads, use queues, etc.
 *
 * @author James Duncan Davidson
 * @author Jason Hunter
 * @author James Todd
 * @author Costin Manolache
 * @author Gal Shachor
 * @author Yoav Shapira
 * @author Remy Maucherat
 */
public class JIoEndpoint extends AbstractEndpoint {


    // -------------------------------------------------------------- Constants

    private static final Log log = LogFactory.getLog(JIoEndpoint.class);

    // ----------------------------------------------------------------- Fields

    /**
     * Associated server socket.
     */
    protected ServerSocket serverSocket = null;
    

    // ------------------------------------------------------------- Properties

    /**
     * Generic properties - currently only socket.xxx properties
     */
    @Override
    public boolean setProperty(String name, String value) {
        final String socketName = "socket.";
        try {
            if (name.startsWith(socketName)) {
                return IntrospectionUtils.setProperty(socketProperties, name.substring(socketName.length()), value);
            }
            return super.setProperty(name, value);
        } catch ( Exception x ) {
            log.error("Unable to set attribute \""+name+"\" to \""+value+"\"",x);
            return false;
        }
    }
    
    /**
     * Acceptor thread count.
     */
    protected int acceptorThreadCount = 0;
    public void setAcceptorThreadCount(int acceptorThreadCount) { this.acceptorThreadCount = acceptorThreadCount; }
    public int getAcceptorThreadCount() { return acceptorThreadCount; }
    
    /**
     * Handling of accepted sockets.
     */
    protected Handler handler = null;
    public void setHandler(Handler handler ) { this.handler = handler; }
    public Handler getHandler() { return handler; }

    /**
     * Server socket factory.
     */
    protected ServerSocketFactory serverSocketFactory = null;
    public void setServerSocketFactory(ServerSocketFactory factory) { this.serverSocketFactory = factory; }
    public ServerSocketFactory getServerSocketFactory() { return serverSocketFactory; }




    
    // ------------------------------------------------ Handler Inner Interface


    /**
     * Bare bones interface used for socket processing. Per thread data is to be
     * stored in the ThreadWithAttributes extra folders, or alternately in
     * thread local fields.
     */
    public interface Handler {
        public SocketState process(SocketWrapper<Socket> socket);
        public SocketState process(SocketWrapper<Socket> socket, SocketStatus status);
    }


    // --------------------------------------------------- Acceptor Inner Class

    /**
     * Async timeout thread
     */
    protected class AsyncTimeout implements Runnable {
        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        public void run() {

            // Loop until we receive a shutdown command
            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
                long now = System.currentTimeMillis();
                Iterator<SocketWrapper<Socket>> sockets =
                    waitingRequests.iterator();
                while (sockets.hasNext()) {
                    SocketWrapper<Socket> socket = sockets.next();
                    long access = socket.getLastAccess();
                    if ((now-access)>socket.getTimeout()) {
                        processSocket(socket,SocketStatus.TIMEOUT);
                    }
                }
                
                // Loop if endpoint is paused
                while (paused) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
                
            }
        }
    }
    /**
     * Server socket acceptor thread.
     */
    protected class Acceptor implements Runnable {


        /**
         * The background thread that listens for incoming TCP/IP connections and
         * hands them off to an appropriate processor.
         */
        public void run() {

            // Loop until we receive a shutdown command
            while (running) {

                // Loop if endpoint is paused
                while (paused) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                // Accept the next incoming connection from the server socket
                try {
                    Socket socket = serverSocketFactory.acceptSocket(serverSocket);
                    serverSocketFactory.initSocket(socket);
                    // Hand this socket off to an appropriate processor
                    if (!processSocket(socket)) {
                        // Close socket right away
                        try {
                            socket.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                }catch ( IOException x ) {
                    if ( running ) log.error(sm.getString("endpoint.accept.fail"), x);
                } catch (Throwable t) {
                    log.error(sm.getString("endpoint.accept.fail"), t);
                }

                // The processor will recycle itself when it finishes

            }

        }

    }


    // ------------------------------------------- SocketProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool.
     */
    protected class SocketProcessor implements Runnable {
        
        protected SocketWrapper<Socket> socket = null;
        protected SocketStatus status = null;
        
        public SocketProcessor(SocketWrapper<Socket> socket) {
            if (socket==null) throw new NullPointerException();
            this.socket = socket;
        }

        public SocketProcessor(SocketWrapper<Socket> socket, SocketStatus status) {
            this(socket);
            this.status = status;
        }

        public void run() {
            boolean launch = false;
        	try {
        	    
        	    if (!socket.processing.compareAndSet(false, true)) {
        	        log.error("Unable to process socket. Invalid state.");
        	        return;
        	    }
        	    
        	    SocketState state = SocketState.OPEN;
        	    // Process the request from this socket
        	    if ( (!socket.isInitialized()) && (!setSocketOptions(socket.getSocket())) ) { 
        	        state = SocketState.CLOSED;
        	    }
        	    socket.setInitialized(true);

        	    if ( (state != SocketState.CLOSED) ) {
        	        state = (status==null)?handler.process(socket):handler.process(socket,status);
        	    }
        	    if (state == SocketState.CLOSED) {
        	        // Close socket
        	        if (log.isTraceEnabled()) {
        	            log.trace("Closing socket:"+socket);
        	        }
        	        try {
        	            socket.getSocket().close();
        	        } catch (IOException e) {
        	            // Ignore
        	        }
        	    } else if (state == SocketState.OPEN){
        	        socket.setKeptAlive(true);
        	        socket.access();
        	        //keepalive connection
        	        //TODO - servlet3 check async status, we may just be in a hold pattern
        	        launch = true;
        	    } else if (state == SocketState.LONG) {
        	        socket.access();
        	        waitingRequests.add(socket);
        	    }
        	} finally {
                socket.processing.set(false);
                if (launch) getExecutor().execute(new SocketProcessor(socket));
                socket = null;
            }
            // Finish up this request
            
        }
        
    }
    
    

    // -------------------- Public methods --------------------

    @Override
    public void init()
        throws Exception {

        if (initialized)
            return;
        
        // Initialize thread count defaults for acceptor
        if (acceptorThreadCount == 0) {
            acceptorThreadCount = 1;
        }
        if (serverSocketFactory == null) {
            serverSocketFactory = ServerSocketFactory.getDefault();
        }
        if (isSSLEnabled()) {
            serverSocketFactory.setAttribute(SSL_ATTR_ALGORITHM,
                    getAlgorithm());
            serverSocketFactory.setAttribute(SSL_ATTR_CLIENT_AUTH,
                    getClientAuth());
            serverSocketFactory.setAttribute(SSL_ATTR_KEYSTORE_FILE,
                    getKeystoreFile());
            serverSocketFactory.setAttribute(SSL_ATTR_KEYSTORE_PASS,
                    getKeystorePass());
            serverSocketFactory.setAttribute(SSL_ATTR_KEYSTORE_TYPE,
                    getKeystoreType());
            serverSocketFactory.setAttribute(SSL_ATTR_KEYSTORE_PROVIDER,
                    getKeystoreProvider());
            serverSocketFactory.setAttribute(SSL_ATTR_SSL_PROTOCOL,
                    getSslProtocol());
            serverSocketFactory.setAttribute(SSL_ATTR_CIPHERS,
                    getCiphers());
            serverSocketFactory.setAttribute(SSL_ATTR_KEY_ALIAS,
                    getKeyAlias());
            serverSocketFactory.setAttribute(SSL_ATTR_KEY_PASS,
                    getKeyPass());
            serverSocketFactory.setAttribute(SSL_ATTR_TRUSTSTORE_FILE,
                    getTruststoreFile());
            serverSocketFactory.setAttribute(SSL_ATTR_TRUSTSTORE_PASS,
                    getTruststorePass());
            serverSocketFactory.setAttribute(SSL_ATTR_TRUSTSTORE_TYPE,
                    getTruststoreType());
            serverSocketFactory.setAttribute(SSL_ATTR_TRUSTSTORE_PROVIDER,
                    getTruststoreProvider());
            serverSocketFactory.setAttribute(SSL_ATTR_TRUSTSTORE_ALGORITHM,
                    getTruststoreAlgorithm());
            serverSocketFactory.setAttribute(SSL_ATTR_CRL_FILE,
                    getCrlFile());
            serverSocketFactory.setAttribute(SSL_ATTR_TRUST_MAX_CERT_LENGTH,
                    getTrustMaxCertLength());
            serverSocketFactory.setAttribute(SSL_ATTR_SESSION_CACHE_SIZE,
                    getSessionCacheSize());
            serverSocketFactory.setAttribute(SSL_ATTR_SESSION_TIMEOUT,
                    getSessionTimeout());
            serverSocketFactory.setAttribute(SSL_ATTR_ALLOW_UNSAFE_RENEG,
                    getAllowUnsafeLegacyRenegotiation());
        }

        if (serverSocket == null) {
            try {
                if (getAddress() == null) {
                    serverSocket = serverSocketFactory.createSocket(getPort(), getBacklog());
                } else {
                    serverSocket = serverSocketFactory.createSocket(getPort(), getBacklog(), getAddress());
                }
            } catch (BindException orig) {
                String msg;
                if (getAddress() == null)
                    msg = orig.getMessage() + " <null>:" + getPort();
                else
                    msg = orig.getMessage() + " " +
                            getAddress().toString() + ":" + getPort();
                BindException be = new BindException(msg);
                be.initCause(orig);
                throw be;
            }
        }
        //if( serverTimeout >= 0 )
        //    serverSocket.setSoTimeout( serverTimeout );
        
        initialized = true;
        
    }
    
    @Override
    public void start() throws Exception {
        // Initialize socket if not done before
        if (!initialized) {
            init();
        }
        if (!running) {
            running = true;
            paused = false;

            // Create worker collection
            if (getExecutor() == null) {
                createExecutor();
            }

            // Start acceptor threads
            for (int i = 0; i < acceptorThreadCount; i++) {
                Thread acceptorThread = new Thread(new Acceptor(), getName() + "-Acceptor-" + i);
                acceptorThread.setPriority(threadPriority);
                acceptorThread.setDaemon(getDaemon());
                acceptorThread.start();
            }
        }
    }

    @Override
    public void pause() {
        if (running && !paused) {
            paused = true;
            unlockAccept();
        }
    }

    @Override
    public void resume() {
        if (running) {
            paused = false;
        }
    }

    public void stop() {
        if (running) {
            running = false;
            unlockAccept();
        }
        shutdownExecutor();
    }

    /**
     * Deallocate APR memory pools, and close server socket.
     */
    @Override
    public void destroy() throws Exception {
        if (running) {
            stop();
        }
        if (serverSocket != null) {
            try {
                if (serverSocket != null)
                    serverSocket.close();
            } catch (Exception e) {
                log.error(sm.getString("endpoint.err.close"), e);
            }
            serverSocket = null;
        }
        initialized = false ;
    }

  


    /**
     * Set the options for the current socket.
     */
    protected boolean setSocketOptions(Socket socket) {
        // Process the connection
        
        try {
            // 1: Set socket options: timeout, linger, etc
            socketProperties.setProperties(socket);
        } catch (SocketException s) {
            //error here is common if the client has reset the connection
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.err.unexpected"), s);
            }
            // Close the socket
            return false;
        } catch (Throwable t) {
            log.error(sm.getString("endpoint.err.unexpected"), t);
            // Close the socket
            return false;
        }
        try {
            // 2: SSL handshake
            serverSocketFactory.handshake(socket);
        } catch (Throwable t) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.err.handshake"), t);
            }
            // Tell to close the socket
            return false;
        }
        return true;
    }

    
    /**
     * Process given socket.
     */
    protected boolean processSocket(Socket socket) {
        try {
            SocketWrapper<Socket> wrapper = new SocketWrapper<Socket>(socket);
            wrapper.setKeepAliveLeft(getMaxKeepAliveRequests());
            getExecutor().execute(new SocketProcessor(wrapper));
        } catch (RejectedExecutionException x) {
            log.warn("Socket processing request was rejected for:"+socket,x);
            return false;
        } catch (Throwable t) {
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }
    
    public boolean processSocket(SocketWrapper<Socket> socket, SocketStatus status) {
        try {
            if (status == SocketStatus.OPEN || status == SocketStatus.STOP || status == SocketStatus.TIMEOUT) {
                if (waitingRequests.remove(socket)) {
                    SocketProcessor proc = new SocketProcessor(socket,status);
                    ClassLoader loader = Thread.currentThread().getContextClassLoader();
                    try {
                        //threads should not be created by the webapp classloader
                        if (Globals.IS_SECURITY_ENABLED) {
                            PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                                    getClass().getClassLoader());
                            AccessController.doPrivileged(pa);
                        } else {
                            Thread.currentThread().setContextClassLoader(
                                    getClass().getClassLoader());
                        }
                        getExecutor().execute(proc);
                    }finally {
                        if (Globals.IS_SECURITY_ENABLED) {
                            PrivilegedAction<Void> pa = new PrivilegedSetTccl(loader);
                            AccessController.doPrivileged(pa);
                        } else {
                            Thread.currentThread().setContextClassLoader(loader);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }

    protected ConcurrentLinkedQueue<SocketWrapper<Socket>> waitingRequests =
        new ConcurrentLinkedQueue<SocketWrapper<Socket>>();

    private static class PrivilegedSetTccl implements PrivilegedAction<Void> {

        private ClassLoader cl;

        PrivilegedSetTccl(ClassLoader cl) {
            this.cl = cl;
        }

        public Void run() {
            Thread.currentThread().setContextClassLoader(cl);
            return null;
        }
    }
    
}
