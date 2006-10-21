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
            echoPort = Integer.decode(props.getProperty("echo.port", "23")).intValue();
            echoNmax = Integer.decode(props.getProperty("echo.max", "1")).intValue();
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
        public Acceptor() throws Exception {
            try {

                pool = Pool.create(Echo.echoPool);
                System.out.println("Accepting: " +  Echo.echoAddr + ":" +
                                   Echo.echoPort);
                inetAddress = Address.info(Echo.echoAddr, Socket.APR_INET,
                                           Echo.echoPort, 0,
                                           pool);
                serverSock = Socket.create(Socket.APR_INET, Socket.SOCK_STREAM,
                                           Socket.APR_PROTO_TCP, pool);
                long sa = Address.get(Socket.APR_LOCAL, serverSock);
                Sockaddr addr = new Sockaddr();
                if (Address.fill(addr, sa)) {
                    System.out.println("Host: " + addr.hostname);
                    System.out.println("Server: " + addr.servname);
                    System.out.println("IP: " + Address.getip(sa) +
                                       ":" + addr.port);
                }
                int rc = Socket.bind(serverSock, inetAddress);
                if (rc != 0) {
                  throw(new Exception("Can't create Acceptor: bind: " + Error.strerror(rc)));
                }
                Socket.listen(serverSock, 5);
            }
            catch( Exception ex ) {
                ex.printStackTrace();
                throw(new Exception("Can't create Acceptor"));
            }
        }

        public void run() {
            int i = 0;
            try {
                while (true) {
                    long clientSock = Socket.accept(serverSock);
                    System.out.println("Accepted id: " +  i);

                    try {
                        long sa = Address.get(Socket.APR_REMOTE, clientSock);
                        Sockaddr raddr = new Sockaddr();
                        if (Address.fill(raddr, sa)) {
                            System.out.println("Remote Host: " + Address.getnameinfo(sa, 0));
                            System.out.println("Remote IP: " + Address.getip(sa) +
                                               ":" + raddr.port);
                        }
                        sa = Address.get(Socket.APR_LOCAL, clientSock);
                        Sockaddr laddr = new Sockaddr();
                        if (Address.fill(laddr, sa)) {
                            System.out.println("Local Host: " + laddr.hostname);
                            System.out.println("Local Server: " + Address.getnameinfo(sa, 0));
                            System.out.println("Local IP: " + Address.getip(sa) +
                                               ":" + laddr.port);
                        }

                    } catch (Exception e) {
                        // Ignore
                        e.printStackTrace();
                    }

                    Socket.timeoutSet(clientSock, 10000000);
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
                serverPollset = Poll.create(16, pool, 0, 10000000);
            }
            catch( Exception ex ) {
                ex.printStackTrace();
            }
        }

        public void add(long socket) {
            int rv = Poll.add(serverPollset, socket,
                              Poll.APR_POLLIN);
            if (rv == Status.APR_SUCCESS) {
                System.out.println("Added worker to pollset");
                nsocks++;
            }
        }

        public void remove(long socket) {
            int rv = Poll.remove(serverPollset, socket);
            if (rv == Status.APR_SUCCESS) {
               nsocks--;
               System.out.println("Removed worker from pollset");
            }
            else {
               System.out.println("Failed removing worker from pollset");
            }
        }

        public void run() {
            while (true) {
                try {
                    if (nsocks < 1) {
                        Thread.sleep(1);
                        continue;
                    }
                    /* Two times size then  created pollset */
                    long [] desc = new long[64];
                    /* USe 1 second poll timeout */
                    int rv = Poll.poll(serverPollset, 1000000, desc, false);
                    if (rv > 0) {
                        for (int n = 0; n < rv; n++) {
                            long clientSock = desc[n*2+1];
                            System.out.println("Poll flags " + desc[n*2]);
                            remove(clientSock);
                            Worker worker = new Worker(clientSock, n,
                                                       this.getClass().getName());
                            Echo.incThreads();
                            worker.start();
                        }
                    }
                    else {
                        if (Status.APR_STATUS_IS_TIMEUP(-rv))
                            System.out.println("Timeup");
                        else {
                            System.out.println("Error " + (-rv));
                        }
                    }
                }
                /* XXX: JFC quick hack
                catch(Error err ) {
                    if (Status.APR_STATUS_IS_TIMEUP(err.getError())) {
                        /0 TODO: deal with timeout 0/
                    }
                    else {
                        err.printStackTrace();
                        break;
                    }
                }
                 */
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
                Socket.send(clientSock, wellcomeMsg, 0, wellcomeMsg.length);
                /* Do a blocking read byte at a time */
                byte [] buf = new byte[1];
                while (Socket.recv(clientSock, buf, 0, 1) == 1) {
                    if (buf[0] == '\n')
                        break;
                    else if (buf[0] == '!') {
                        doClose = true;
                        break;
                    }
                }
                if (doClose) {
                    try {
                        byte [] msg = ("Bye from worker: " + workerId + "\r\n").getBytes();
                        Socket.send(clientSock, msg, 0, msg.length);
                    } catch(Exception e) { }

                    Socket.close(clientSock);
                }
                else {
                    try {
                        byte [] msg = ("Recycling worker: " + workerId + "\r\n").getBytes();
                        Socket.send(clientSock, msg, 0, msg.length);
                    } catch(Exception e) { }
                    /* Put the socket to the keep-alive poll */
                    Echo.echoPoller.add(clientSock);
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
        try {
            echoAcceptor = new Acceptor();
            echoAcceptor.start();
            echoPoller = new Poller();
            echoPoller.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

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
            long [] inf = new long[16];
            System.out.println("Info ...");
            System.out.println("  Native        " + Library.versionString());
            System.out.println("  APR           " + Library.aprVersionString());
            OS.info(inf);
            System.out.println("OS Info ...");
            System.out.println("  Physical      " + inf[0]);
            System.out.println("  Avail         " + inf[1]);
            System.out.println("  Swap          " + inf[2]);
            System.out.println("  Swap free     " + inf[3]);
            System.out.println("  Shared        " + inf[4]);
            System.out.println("  Buffers size  " + inf[5]);
            System.out.println("  Load          " + inf[6]);

            System.out.println("  Idle          " + inf[7]);
            System.out.println("  Kernel        " + inf[8]);
            System.out.println("  User          " + inf[9]);

            System.out.println("  Proc creation " + inf[10]);
            System.out.println("  Proc kernel   " + inf[11]);
            System.out.println("  Proc user     " + inf[12]);
            System.out.println("  Curr working  " + inf[13]);
            System.out.println("  Peak working  " + inf[14]);
            System.out.println("  Page faults   " + inf[15]);

            SSL.initialize(null);
            System.out.println("OpenSSL ...");
            System.out.println("  version       " + SSL.versionString());
            System.out.println("  number        " + SSL.version());

            System.out.println("Starting Native Echo server example on port " +
                               echoAddr + ":" + echoPort);
            Echo echo = new Echo();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
