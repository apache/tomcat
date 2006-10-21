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
import java.io.InterruptedIOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.AccessControlException;
import java.util.Stack;
import java.util.Vector;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.ThreadPool;
import org.apache.tomcat.util.threads.ThreadPoolRunnable;

/* Similar with MPM module in Apache2.0. Handles all the details related with
   "tcp server" functionality - thread management, accept policy, etc.
   It should do nothing more - as soon as it get a socket ( and all socket options
   are set, etc), it just handle the stream to ConnectionHandler.processConnection. (costin)
*/



/**
 * Handle incoming TCP connections.
 *
 * This class implement a simple server model: one listener thread accepts on a socket and
 * creates a new worker thread for each incoming connection.
 *
 * More advanced Endpoints will reuse the threads, use queues, etc.
 *
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Costin@eng.sun.com
 * @author Gal Shachor [shachor@il.ibm.com]
 * @author Yoav Shapira <yoavs@apache.org>
 */
public class PoolTcpEndpoint implements Runnable { // implements Endpoint {

    static Log log=LogFactory.getLog(PoolTcpEndpoint.class );

    private StringManager sm = 
        StringManager.getManager("org.apache.tomcat.util.net.res");

    private static final int BACKLOG = 100;
    private static final int TIMEOUT = 1000;

    private final Object threadSync = new Object();

    private int backlog = BACKLOG;
    private int serverTimeout = TIMEOUT;

    private InetAddress inet;
    private int port;

    private ServerSocketFactory factory;
    private ServerSocket serverSocket;

    private volatile boolean running = false;
    private volatile boolean paused = false;
    private boolean initialized = false;
    private boolean reinitializing = false;
    static final int debug=0;

    protected boolean tcpNoDelay=false;
    protected int linger=100;
    protected int socketTimeout=-1;
    private boolean lf = true;

    
    // ------ Leader follower fields

    
    TcpConnectionHandler handler;
    ThreadPoolRunnable listener;
    ThreadPool tp;

    
    // ------ Master slave fields

    /* The background thread. */
    private Thread thread = null;
    /* Available processors. */
    private Stack workerThreads = new Stack();
    private int curThreads = 0;
    private int maxThreads = 20;
    /* All processors which have been created. */
    private Vector created = new Vector();

    
    public PoolTcpEndpoint() {
	tp = new ThreadPool();
    }

    public PoolTcpEndpoint( ThreadPool tp ) {
        this.tp=tp;
    }

    // -------------------- Configuration --------------------

    public void setMaxThreads(int maxThreads) {
	if( maxThreads > 0)
	    tp.setMaxThreads(maxThreads);
    }

    public int getMaxThreads() {
        return tp.getMaxThreads();
    }

    public void setMaxSpareThreads(int maxThreads) {
	if(maxThreads > 0) 
	    tp.setMaxSpareThreads(maxThreads);
    }

    public int getMaxSpareThreads() {
        return tp.getMaxSpareThreads();
    }

    public void setMinSpareThreads(int minThreads) {
	if(minThreads > 0) 
	    tp.setMinSpareThreads(minThreads);
    }

    public int getMinSpareThreads() {
        return tp.getMinSpareThreads();
    }

    public void setThreadPriority(int threadPriority) {
      tp.setThreadPriority(threadPriority);
    }

    public int getThreadPriority() {
      return tp.getThreadPriority();
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port ) {
        this.port=port;
    }

    public InetAddress getAddress() {
	    return inet;
    }

    public void setAddress(InetAddress inet) {
	    this.inet=inet;
    }

    public void setServerSocket(ServerSocket ss) {
	    serverSocket = ss;
    }

    public void setServerSocketFactory(  ServerSocketFactory factory ) {
	    this.factory=factory;
    }

   ServerSocketFactory getServerSocketFactory() {
 	    return factory;
   }

    public void setConnectionHandler( TcpConnectionHandler handler ) {
    	this.handler=handler;
    }

    public TcpConnectionHandler getConnectionHandler() {
	    return handler;
    }

    public boolean isRunning() {
	return running;
    }
    
    public boolean isPaused() {
	return paused;
    }
    
    /**
     * Allows the server developer to specify the backlog that
     * should be used for server sockets. By default, this value
     * is 100.
     */
    public void setBacklog(int backlog) {
	if( backlog>0)
	    this.backlog = backlog;
    }

    public int getBacklog() {
        return backlog;
    }

    /**
     * Sets the timeout in ms of the server sockets created by this
     * server. This method allows the developer to make servers
     * more or less responsive to having their server sockets
     * shut down.
     *
     * <p>By default this value is 1000ms.
     */
    public void setServerTimeout(int timeout) {
	this.serverTimeout = timeout;
    }

    public boolean getTcpNoDelay() {
        return tcpNoDelay;
    }
    
    public void setTcpNoDelay( boolean b ) {
	tcpNoDelay=b;
    }

    public int getSoLinger() {
        return linger;
    }
    
    public void setSoLinger( int i ) {
	linger=i;
    }

    public int getSoTimeout() {
        return socketTimeout;
    }
    
    public void setSoTimeout( int i ) {
	socketTimeout=i;
    }
    
    public int getServerSoTimeout() {
        return serverTimeout;
    }  
    
    public void setServerSoTimeout( int i ) {
	serverTimeout=i;
    }

    public String getStrategy() {
        if (lf) {
            return "lf";
        } else {
            return "ms";
        }
    }
    
    public void setStrategy(String strategy) {
        if ("ms".equals(strategy)) {
            lf = false;
        } else {
            lf = true;
        }
    }

    public int getCurrentThreadCount() {
        return curThreads;
    }
    
    public int getCurrentThreadsBusy() {
        return curThreads - workerThreads.size();
    }
    
    // -------------------- Public methods --------------------

    public void initEndpoint() throws IOException, InstantiationException {
        try {
            if(factory==null)
                factory=ServerSocketFactory.getDefault();
            if(serverSocket==null) {
                try {
                    if (inet == null) {
                        serverSocket = factory.createSocket(port, backlog);
                    } else {
                        serverSocket = factory.createSocket(port, backlog, inet);
                    }
                } catch ( BindException be ) {
                    throw new BindException(be.getMessage() + ":" + port);
                }
            }
            if( serverTimeout >= 0 )
                serverSocket.setSoTimeout( serverTimeout );
        } catch( IOException ex ) {
            throw ex;
        } catch( InstantiationException ex1 ) {
            throw ex1;
        }
        initialized = true;
    }
    
    public void startEndpoint() throws IOException, InstantiationException {
        if (!initialized) {
            initEndpoint();
        }
        if (lf) {
            tp.start();
        }
        running = true;
        paused = false;
        if (lf) {
            listener = new LeaderFollowerWorkerThread(this);
            tp.runIt(listener);
        } else {
            maxThreads = getMaxThreads();
            threadStart();
        }
    }

    public void pauseEndpoint() {
        if (running && !paused) {
            paused = true;
            unlockAccept();
        }
    }

    public void resumeEndpoint() {
        if (running) {
            paused = false;
        }
    }

    public void stopEndpoint() {
        if (running) {
            if (lf) {
                tp.shutdown();
            }
            running = false;
            if (serverSocket != null) {
                closeServerSocket();
            }
            if (!lf) {
                threadStop();
            }
            initialized=false ;
        }
    }

    protected void closeServerSocket() {
        if (!paused)
            unlockAccept();
        try {
            if( serverSocket!=null)
                serverSocket.close();
        } catch(Exception e) {
            log.error(sm.getString("endpoint.err.close"), e);
        }
        serverSocket = null;
    }

    protected void unlockAccept() {
        Socket s = null;
        try {
            // Need to create a connection to unlock the accept();
            if (inet == null) {
                s = new Socket("127.0.0.1", port);
            } else {
                s = new Socket(inet, port);
                    // setting soLinger to a small value will help shutdown the
                    // connection quicker
                s.setSoLinger(true, 0);
            }
        } catch(Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.debug.unlock", "" + port), e);
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

    // -------------------- Private methods

    Socket acceptSocket() {
        if( !running || serverSocket==null ) return null;

        Socket accepted = null;

    	try {
            if(factory==null) {
                accepted = serverSocket.accept();
            } else {
                accepted = factory.acceptSocket(serverSocket);
            }
            if (null == accepted) {
                log.warn(sm.getString("endpoint.warn.nullSocket"));
            } else {
                if (!running) {
                    accepted.close();  // rude, but unlikely!
                    accepted = null;
                } else if (factory != null) {
                    factory.initSocket( accepted );
                }
            }
        }
        catch(InterruptedIOException iioe) {
            // normal part -- should happen regularly so
            // that the endpoint can release if the server
            // is shutdown.
        }
        catch (AccessControlException ace) {
            // When using the Java SecurityManager this exception
            // can be thrown if you are restricting access to the
            // socket with SocketPermission's.
            // Log the unauthorized access and continue
            String msg = sm.getString("endpoint.warn.security",
                                      serverSocket, ace);
            log.warn(msg);
        }
        catch (IOException e) {

            String msg = null;

            if (running) {
                msg = sm.getString("endpoint.err.nonfatal",
                        serverSocket, e);
                log.error(msg, e);
            }

            if (accepted != null) {
                try {
                    accepted.close();
                } catch(Throwable ex) {
                    msg = sm.getString("endpoint.err.nonfatal",
                                       accepted, ex);
                    log.warn(msg, ex);
                }
                accepted = null;
            }

            if( ! running ) return null;
            reinitializing = true;
            // Restart endpoint when getting an IOException during accept
            synchronized (threadSync) {
                if (reinitializing) {
                    reinitializing = false;
                    // 1) Attempt to close server socket
                    closeServerSocket();
                    initialized = false;
                    // 2) Reinit endpoint (recreate server socket)
                    try {
                        msg = sm.getString("endpoint.warn.reinit");
                        log.warn(msg);
                        initEndpoint();
                    } catch (Throwable t) {
                        msg = sm.getString("endpoint.err.nonfatal",
                                           serverSocket, t);
                        log.error(msg, t);
                    }
                    // 3) If failed, attempt to restart endpoint
                    if (!initialized) {
                        msg = sm.getString("endpoint.warn.restart");
                        log.warn(msg);
                        try {
                            stopEndpoint();
                            initEndpoint();
                            startEndpoint();
                        } catch (Throwable t) {
                            msg = sm.getString("endpoint.err.fatal",
                                               serverSocket, t);
                            log.error(msg, t);
                        }
                        // Current thread is now invalid: kill it
                        throw new ThreadDeath();
                    }
                }
            }

        }

        return accepted;
    }

    void setSocketOptions(Socket socket)
        throws SocketException {
        if(linger >= 0 ) 
            socket.setSoLinger( true, linger);
        if( tcpNoDelay )
            socket.setTcpNoDelay(tcpNoDelay);
        if( socketTimeout > 0 )
            socket.setSoTimeout( socketTimeout );
    }

    
    void processSocket(Socket s, TcpConnection con, Object[] threadData) {
        // Process the connection
        int step = 1;
        try {
            
            // 1: Set socket options: timeout, linger, etc
            setSocketOptions(s);
            
            // 2: SSL handshake
            step = 2;
            if (getServerSocketFactory() != null) {
                getServerSocketFactory().handshake(s);
            }
            
            // 3: Process the connection
            step = 3;
            con.setEndpoint(this);
            con.setSocket(s);
            getConnectionHandler().processConnection(con, threadData);
            
        } catch (SocketException se) {
            log.debug(sm.getString("endpoint.err.socket", s.getInetAddress()),
                    se);
            // Try to close the socket
            try {
                s.close();
            } catch (IOException e) {
            }
        } catch (Throwable t) {
            if (step == 2) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("endpoint.err.handshake"), t);
                }
            } else {
                log.error(sm.getString("endpoint.err.unexpected"), t);
            }
            // Try to close the socket
            try {
                s.close();
            } catch (IOException e) {
            }
        } finally {
            if (con != null) {
                con.recycle();
            }
        }
    }
    

    // -------------------------------------------------- Master Slave Methods


    /**
     * Create (or allocate) and return an available processor for use in
     * processing a specific HTTP request, if possible.  If the maximum
     * allowed processors have already been created and are in use, return
     * <code>null</code> instead.
     */
    private MasterSlaveWorkerThread createWorkerThread() {

        synchronized (workerThreads) {
            if (workerThreads.size() > 0) {
                return ((MasterSlaveWorkerThread) workerThreads.pop());
            }
            if ((maxThreads > 0) && (curThreads < maxThreads)) {
                return (newWorkerThread());
            } else {
                if (maxThreads < 0) {
                    return (newWorkerThread());
                } else {
                    return (null);
                }
            }
        }

    }

    
    /**
     * Create and return a new processor suitable for processing HTTP
     * requests and returning the corresponding responses.
     */
    private MasterSlaveWorkerThread newWorkerThread() {

        MasterSlaveWorkerThread workerThread = 
            new MasterSlaveWorkerThread(this, tp.getName() + "-" + (++curThreads));
        workerThread.start();
        created.addElement(workerThread);
        return (workerThread);

    }


    /**
     * Recycle the specified Processor so that it can be used again.
     *
     * @param processor The processor to be recycled
     */
    void recycleWorkerThread(MasterSlaveWorkerThread workerThread) {
        workerThreads.push(workerThread);
    }

    
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

            // Allocate a new worker thread
            MasterSlaveWorkerThread workerThread = createWorkerThread();
            if (workerThread == null) {
                try {
                    // Wait a little for load to go down: as a result, 
                    // no accept will be made until the concurrency is
                    // lower than the specified maxThreads, and current
                    // connections will wait for a little bit instead of
                    // failing right away.
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // Ignore
                }
                continue;
            }
            
            // Accept the next incoming connection from the server socket
            Socket socket = acceptSocket();

            // Hand this socket off to an appropriate processor
            workerThread.assign(socket);

            // The processor will recycle itself when it finishes

        }

        // Notify the threadStop() method that we have shut ourselves down
        synchronized (threadSync) {
            threadSync.notifyAll();
        }

    }


    /**
     * Start the background processing thread.
     */
    private void threadStart() {
        thread = new Thread(this, tp.getName());
        thread.setPriority(getThreadPriority());
        thread.setDaemon(true);
        thread.start();
    }


    /**
     * Stop the background processing thread.
     */
    private void threadStop() {
        thread = null;
    }


}
