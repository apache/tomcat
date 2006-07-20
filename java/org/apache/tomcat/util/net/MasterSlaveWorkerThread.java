/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

import java.net.Socket;

import org.apache.tomcat.util.threads.ThreadWithAttributes;

/**
 * Regular master slave thread pool. Slave threads will wait for work.
 */
class MasterSlaveWorkerThread implements Runnable {

    protected PoolTcpEndpoint endpoint;
    protected String threadName;
    protected boolean stopped = false;
    private Object threadSync = new Object();
    private Thread thread = null;
    private boolean available = false;
    private Socket socket = null;
    private TcpConnection con = new TcpConnection();
    private Object[] threadData = null;

    
    public MasterSlaveWorkerThread(PoolTcpEndpoint endpoint, String threadName) {
        this.endpoint = endpoint;
        this.threadName = threadName;
    }


    /**
     * Process an incoming TCP/IP connection on the specified socket.  Any
     * exception that occurs during processing must be logged and swallowed.
     * <b>NOTE</b>:  This method is called from our Connector's thread.  We
     * must assign it to our own thread so that multiple simultaneous
     * requests can be handled.
     *
     * @param socket TCP socket to process
     */
    synchronized void assign(Socket socket) {

        // Wait for the Processor to get the previous Socket
        while (available) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }

        // Store the newly available Socket and notify our thread
        this.socket = socket;
        available = true;
        notifyAll();

    }

    
    /**
     * Await a newly assigned Socket from our Connector, or <code>null</code>
     * if we are supposed to shut down.
     */
    private synchronized Socket await() {

        // Wait for the Connector to provide a new Socket
        while (!available) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }

        // Notify the Connector that we have received this Socket
        Socket socket = this.socket;
        available = false;
        notifyAll();

        return (socket);

    }



    /**
     * The background thread that listens for incoming TCP/IP connections and
     * hands them off to an appropriate processor.
     */
    public void run() {

        // Process requests until we receive a shutdown signal
        while (!stopped) {

            // Wait for the next socket to be assigned
            Socket socket = await();
            if (socket == null)
                continue;

            // Process the request from this socket
            endpoint.processSocket(socket, con, threadData);

            // Finish up this request
            endpoint.recycleWorkerThread(this);

        }

        // Tell threadStop() we have shut ourselves down successfully
        synchronized (threadSync) {
            threadSync.notifyAll();
        }

    }


    /**
     * Start the background processing thread.
     */
    public void start() {
        threadData = endpoint.getConnectionHandler().init();
        thread = new ThreadWithAttributes(null, this);
        thread.setName(threadName);
        thread.setDaemon(true);
        thread.start();
    }


    /**
     * Stop the background processing thread.
     */
    public void stop() {
        stopped = true;
        assign(null);
        thread = null;
        threadData = null;
    }


}
