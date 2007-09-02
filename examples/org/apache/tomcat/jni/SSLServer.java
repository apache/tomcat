/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.jni;

import java.io.InputStream;
import java.util.Properties;

/** SSL Server server example
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

public class SSLServer {

    public static String serverAddr = null;
    public static int serverPort    = 0;
    public static int serverNmax    = 0;
    public static int serverNrun    = 0;
    public static long serverCtx    = 0;
    public static long serverPool   = 0;
    public static String serverCert = null;
    public static String serverKey  = null;
    public static String serverCiphers  = null;
    public static String serverPassword = null;
    public static String serverCAFile   = null;

    private static Acceptor serverAcceptor = null;

    private static Object threadLock = new Object();

    static {

        try {
            InputStream is = SSLServer.class.getResourceAsStream
                ("/org/apache/tomcat/jni/SSL.properties");
            Properties props = new Properties();
            props.load(is);
            is.close();
            serverAddr = props.getProperty("server.ip", "127.0.0.1");
            serverPort = Integer.decode(props.getProperty("server.port", "4443")).intValue();
            serverNmax = Integer.decode(props.getProperty("server.max", "1")).intValue();
            serverCert = props.getProperty("server.cert", "server.pem");
            serverKey  = props.getProperty("server.key", null);
            serverCAFile   = props.getProperty("server.cacertificate", null);
            serverCiphers  = props.getProperty("server.ciphers", "ALL");
            serverPassword = props.getProperty("server.password", null);
        }
        catch (Throwable t) {
            ; // Nothing
        }
    }

    public SSLServer()
    {
        serverPool = Pool.create(0);
        try {
            /* Create SSL Context, one for each Virtual Host */
            serverCtx = SSLContext.make(serverPool, SSL.SSL_PROTOCOL_SSLV2 | SSL.SSL_PROTOCOL_SSLV3, SSL.SSL_MODE_SERVER);
            /* List the ciphers that the client is permitted to negotiate. */
            SSLContext.setCipherSuite(serverCtx, serverCiphers);
            /* Load Server key and certificate */
            SSLContext.setCertificate(serverCtx, serverCert, serverKey, serverPassword, SSL.SSL_AIDX_RSA);
            SSLContext.setVerify(serverCtx, SSL.SSL_CVERIFY_NONE, 10);
            serverAcceptor = new Acceptor();
            serverAcceptor.start();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    public static void incThreads() {
        synchronized(threadLock) {
            serverNrun++;
        }
    }

    public static void decThreads() {
        synchronized(threadLock) {
            serverNrun--;
        }
    }

    /* Acceptor thread. Listens for new connections */
    private class Acceptor extends java.lang.Thread {
        private long serverSock = 0;
        private long inetAddress = 0;
        private long pool = 0;
        public Acceptor() throws Exception {
            try {

                pool = Pool.create(SSLServer.serverPool);
                System.out.println("Accepting: " +  SSLServer.serverAddr + ":" +
                                   SSLServer.serverPort);
                inetAddress = Address.info(SSLServer.serverAddr, Socket.APR_INET,
                                           SSLServer.serverPort, 0,
                                           pool);
                serverSock = Socket.create(Socket.APR_INET, Socket.SOCK_STREAM,
                                           Socket.APR_PROTO_TCP, pool);
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
                    SSLSocket.attach(SSLServer.serverCtx, clientSock);
                    i = SSLSocket.handshake(clientSock);
                    if (i == 0) {

                        Worker worker = new Worker(clientSock, i++,
                                                   this.getClass().getName());
                        SSLServer.incThreads();
                        worker.start();
                        
                    }
                    else {
                        System.out.println("Handshake error: " + SSL.getLastError());
                        Socket.destroy(clientSock);
                    }
                }
            }
            catch( Exception ex ) {
                ex.printStackTrace();
            }
        }
    }

    private class Worker extends java.lang.Thread {
        private int workerId = 0;
        private long clientSock = 0;
        private byte [] wellcomeMsg = null;

        public Worker(long clientSocket, int workerId, String from) {
            this.clientSock = clientSocket;
            this.workerId = workerId;
            wellcomeMsg = ("SSLServer server id: " + this.workerId + " from " +
                           from + "\r\n").getBytes();
        }

        public void run() {
            boolean doClose = false;
            try {
                Socket.send(clientSock, wellcomeMsg, 0, wellcomeMsg.length);
                while (!doClose) {
                    /* Do a blocking read byte at a time */
                    byte [] buf = new byte[1];
                    int ret;
                    ret = Socket.recv(clientSock, buf, 0, 1);
                    if (ret != 1)
                        throw(new Exception("Socket.recv failed"));

                    if (buf[0] == '\n')
                        continue;
                    else if (buf[0] == '!') {
                        doClose = true;
                    }
                    Socket.send(clientSock, buf, 0, 1);

                    if (doClose) {
                        try {
                            byte [] msg = ("Bye from worker: " + workerId + "\r\n").getBytes();
                            Socket.send(clientSock, msg, 0, msg.length);
                        } catch(Exception e) { }

                        Socket.close(clientSock);
                    }
                }
            } catch (Exception e) {
                Socket.destroy(clientSock);
                e.printStackTrace();
            }
            Echo.decThreads();
            System.out.println("Worker: " +  workerId + " finished");
        }
    }


    public static void main(String [] args) {
        try {
            Library.initialize(null);
            SSL.initialize(null);

            SSLServer server = new SSLServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
 }
