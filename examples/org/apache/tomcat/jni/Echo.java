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

package org.apache.tomcat.jni;

import java.util.Properties;

import java.io.*;
import java.net.*;
import java.lang.*;

/** Echo server example
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

public class Echo {

    public static String echoEcho = null;
    public static String echoAddr = null;
    public static int echoPort    = 0;
    public static int echoNmax    = 0;
    public static int echoNrun    = 0;
    public static long echoPool   = 0;

    private static Poller echoPoller     = null;
    private static Acceptor echoAcceptor = null;

    private static Object threadLock = new Object();

    static {

        try {
            InputStream is = Echo.class.getResourceAsStream
                ("/org/apache/tomcat/jni/Echo.properties");
            Properties props = new Properties();
            props.load(is);
            is.close();
            echoAddr = props.getProperty("echo.ip", "127.0.0.1");
            echoPort = Integer.decode(props.getProperty("echo.port", "23"));
            echoNmax = Integer.decode(props.getProperty("echo.max", "1"));
        }
        catch (Throwable t) {
            ; // Nothing
        }
    }

    /* Acceptor thread. Listens for new connections */
    private class Acceptor extends Thread {
        private long serverSock = 0;
        private long inetAddress = 0;
        private long pool = 0;
        public Acceptor() {
            try {

                pool = Pool.create(Echo.echoPool);
                System.out.println("Accepting: " +  Echo.echoAddr + ":" +
                                   Echo.echoPort);
                inetAddress = Address.info(Echo.echoAddr, Socket.APR_INET,
                                           Echo.echoPort, 0,
                                           pool);
                serverSock = Socket.create(Socket.APR_INET, Socket.SOCK_STREAM,
                                           Socket.APR_PROTO_TCP, pool);
                Socket.bind(serverSock, inetAddress);
                Socket.listen(serverSock, 5);
            }
            catch( Exception ex ) {
                ex.printStackTrace();
            }
        }

        public void run() {
            int i = 0;
            try {
                while (true) {
                    long clientSock = Socket.accept(serverSock, pool);
                    System.out.println("Accepted id: " +  i);
                    Worker worker = new Worker(clientSock, i++,
                                               this.getClass().getName());
                    Echo.incThreads();
                    worker.start();
                }
            }
            catch( Exception ex ) {
                ex.printStackTrace();
            }
        }
    }

    /* Poller thread. Listens for new recycled connections */
    private class Poller extends Thread {
        private long serverPollset = 0;
        private long pool = 0;
        private int nsocks = 0;
        public Poller() {
            try {

                pool = Pool.create(Echo.echoPool);
                serverPollset = Poll.create(16, pool, 0);
            }
            catch( Exception ex ) {
                ex.printStackTrace();
            }
        }

        public void add(long socket, int workerId) {
            int rv = Poll.add(serverPollset, socket, workerId,
                              Poll.APR_POLLIN, 0);
            if (rv == Status.APR_SUCCESS) {
                System.out.println("Added worker " + workerId + " to pollset");
                nsocks++;
            }
        }

        public void remove(long socket, int workerId) {
            int rv = Poll.remove(serverPollset, socket);
            if (rv == Status.APR_SUCCESS) {
               nsocks--;
               System.out.println("Removed worker " + workerId + " from pollset");
            }
            else {
               System.out.println("Failed removing worker " + workerId + " from pollset");
            }
        }

        public void run() {
            while (true) {
                try {
                    if (nsocks < 1) {
                        Thread.sleep(1);
                        continue;
                    }
                    long [] desc = new long[16];
                    /* USe 1 second poll timeout */
                    int rv = Poll.poll(serverPollset, 1000000, desc);
                    for (int n = 0; n < rv; n++) {
                        long clientSock = Poll.socket(desc[n]);
                        int  workerId   = (int)Poll.data(desc[n]);
                        remove(clientSock, workerId);
                        Worker worker = new Worker(clientSock, workerId,
                                                   this.getClass().getName());
                        Echo.incThreads();
                        worker.start();
                    }
                }
                catch(Error err ) {
                    if (Status.APR_STATUS_IS_TIMEUP(err.getError())) {
                        /* TODO: deal with timeout */
                    }
                    else {
                        err.printStackTrace();
                        break;
                    }
                }
                catch( Exception ex ) {
                    ex.printStackTrace();
                    break;
                }
            }
        }
    }

    private class Worker extends Thread {
        private int workerId = 0;
        private long clientSock = 0;
        private byte [] wellcomeMsg = null;
        public Worker(long clientSocket, int workerId, String from) {
            this.clientSock = clientSocket;
            this.workerId = workerId;
            wellcomeMsg = ("Echo server id: " + this.workerId + " from " +
                           from + "\r\n").getBytes();
        }

        public void run() {
            boolean doClose = false;
            try {
                Socket.send(clientSock, wellcomeMsg, wellcomeMsg.length);
                /* Do a blocking read byte at a time */
                byte [] buf = new byte[1];
                while (Socket.recv(clientSock, buf, 1) == 1) {
                    if (buf[0] == '\n')
                        break;
                    else if (buf[0] == 'Q') {
                        doClose = true;
                        break;
                    }
                }
                if (doClose) {
                    try {
                        byte [] msg = ("Bye from worker: " + workerId + "\r\n").getBytes();
                        Socket.send(clientSock, msg, msg.length);
                    } catch(Exception e) { }

                    Socket.close(clientSock);
                }
                else {
                    try {
                        byte [] msg = ("Recycling worker: " + workerId + "\r\n").getBytes();
                        Socket.send(clientSock, msg, msg.length);
                    } catch(Exception e) { }
                    /* Put the socket to the keep-alive poll */
                    Echo.echoPoller.add(clientSock, workerId);
                }
            } catch (Exception e) {
                Socket.close(clientSock);
                e.printStackTrace();
            }
            Echo.decThreads();
            System.out.println("Worker: " +  workerId + " finished");
        }
    }

    public Echo()
    {
        int i;
        echoPool = Pool.create(0);
        echoAcceptor = new Acceptor();
        echoAcceptor.start();
        echoPoller = new Poller();
        echoPoller.start();

    }

    public static void incThreads() {
        synchronized(threadLock) {
            echoNrun++;
        }
    }

    public static void decThreads() {
        synchronized(threadLock) {
            echoNrun--;
        }
    }

    public static void main(String [] args) {
        try {
            Library.initialize(null);
            System.out.println("Starting Native Echo server example on port " +
                               echoAddr + ":" + echoPort);
            Echo echo = new Echo();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
