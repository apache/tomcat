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

package org.apache.tomcat.jni;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import junit.framework.TestCase;

import java.io.OutputStream;

/**
 * A basic test suite that tests Socket Server feature.
 * 
 * @author Jean-Frederic Clere
 * @version $Revision: 466585 $, $Date: 2006-10-22 00:16:34 +0200 (Sun, 22 Oct 2006) $ 
 * @see org.apache.tomcat.jni
 */
public class SocketServerTestSuite extends TestCase {

    private long serverSock = 0;
    private int port=6666;
    private String host="localhost";

    public static long serverPool = 0;

    public void testSocketServerTestSuite() throws Exception {

        /* Load APR library */
        Library.initialize(null);

        /* Create the server socket and listen on it */
        serverPool = Pool.create(0);
        long inetAddress = Address.info(host, Socket.APR_INET,
                                        port, 0, serverPool);
        serverSock = Socket.create(Socket.APR_INET, Socket.SOCK_STREAM,
                                   Socket.APR_PROTO_TCP, serverPool);
        int rc = Socket.bind(serverSock, inetAddress);
        if (rc != 0) {
            throw(new Exception("Can't bind: " + Error.strerror(rc)));
        }
        Socket.listen(serverSock, 5);

        /* Start the client that connects to the server */
        Client client = new Client();
        client.start(); 
        java.lang.Thread.sleep(100);
    
        /* Accept it */
        long clientSock = Socket.accept(serverSock);

        /* Try 2 milliseconds timeout */
        Socket.timeoutSet(clientSock, 2000);
        long timeout = Socket.timeoutGet(clientSock);
        if (timeout != 2000)
            throw new Exception("Socket.timeoutGet clientSock failed");

        long start = System.currentTimeMillis();
        byte [] buf = new byte[1];
        while (Socket.recv(clientSock, buf, 0, 1) == 1) {
        }
        long wait = System.currentTimeMillis() - start;
        if (wait < 1 || wait >3)
            throw new Exception("2 milliseconds client Socket.timeoutSet failed");

        /* Try 2 milliseconds timeout on accept socket */
        Socket.timeoutSet(serverSock, 2000);
        timeout = Socket.timeoutGet(serverSock);
        if (timeout != 2000)
            throw new Exception("Socket.timeoutGet serverSock failed");

        start = System.currentTimeMillis();
        boolean ok = false;
        try {
            clientSock = Socket.accept(serverSock);
        } catch (Exception ex) {
            ok = true;
        }
        wait = System.currentTimeMillis() - start;
        if (wait < 1 || wait >3 && ! ok)
            throw new Exception("2 milliseconds accept Socket.timeoutSet failed");

        /* Try APR_SO_NONBLOCK */
        Socket.optSet(serverSock, Socket.APR_SO_NONBLOCK, 1);
        int val = Socket.optGet(serverSock, Socket.APR_SO_NONBLOCK);
        if (val != 1)
            throw new Exception("Socket.optGet serverSock failed");

        start = System.currentTimeMillis();
        ok = false;
        try {
            clientSock = Socket.accept(serverSock);
        } catch (Exception ex) {
            ok = true;
        }
        wait = System.currentTimeMillis() - start;
        if (wait > 1 && ! ok)
            throw new Exception("non_blocking accept Socket.APR_SO_NONBLOCK failed");

        /* Try the same on client socket */
        client = new Client();
        client.start(); 
        java.lang.Thread.sleep(100);
        clientSock = Socket.accept(serverSock);
        Socket.optSet(clientSock, Socket.APR_SO_NONBLOCK, 1);
        val = Socket.optGet(clientSock, Socket.APR_SO_NONBLOCK);
        if (val != 1)
            throw new Exception("Socket.optGet clientSock failed");
        start = System.currentTimeMillis();
        while (Socket.recv(clientSock, buf, 0, 1) == 1) {
        }
        wait = System.currentTimeMillis() - start;
        if (wait > 1)
            throw new Exception("non_blocking client Socket.APR_SO_NONBLOCK failed");

        /* Now blocking */
        Socket.optSet(clientSock, Socket.APR_SO_NONBLOCK, 0);
        start = System.currentTimeMillis();
        while (Socket.recv(clientSock, buf, 0, 1) == 1) {
        }
        wait = System.currentTimeMillis() - start;
        if (wait < 1)
            throw new Exception("non_blocking client Socket.APR_SO_NONBLOCK false failed");
    }

    /* small client that connects and sends one byte */
    private class Client extends java.lang.Thread {
        java.net.Socket sock;
        public Client() throws Exception {
            sock = new java.net.Socket(host, port);
        }
        public void run() {
            try {
                OutputStream ou = sock.getOutputStream();
                ou.write('A');
                ou.flush();
                java.lang.Thread.sleep(10000);
                ou.close();
            } catch(Exception ex ) {
                ex.printStackTrace();
            }
        }
    }
    
    public static void main(String[] args) {
        TestRunner.run(suite());
    }
    
    public static Test suite()
    {
        TestSuite suite = new TestSuite( "Tomcat Native Server Socket" );
        suite.addTest(new TestSuite(SocketServerTestSuite.class));
        return suite;
    }
}
