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

import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/*
 * Tests for server-side sockets.
 *
 * While System.nanotime() is available and may have a resolution of ~100ns on
 * some platforms, those same platforms do not use as precise a timer for socket
 * timeouts. Therefore, a much larger error margin (100ms) is used.
 *
 * It is known that this larger error margin is required for Windows 10. It may
 * be worth revisiting the choice of error margin once that platform is no
 * longer supported.
 *
 * @deprecated  The scope of the APR/Native Library will be reduced in Tomcat
 *              10.1.x onwards to only those components required to provide
 *              OpenSSL integration with the NIO and NIO2 connectors.
 */
@Deprecated
public class TestSocketServer extends AbstractJniTest {

    private static final String HOST = "localhost";
    // 100ms == 100,000,000ns
    private static final long ERROR_MARGIN = 100000000;

    private int port = 0;
    private long serverSocket = 0;
    private long clientSocket = 0;

    @Before
    public void init() throws Exception {
        long serverPool = Pool.create(0);
        long inetAddress = Address.info(HOST, Socket.APR_INET,
                                        0, 0, serverPool);
        serverSocket = Socket.create(Socket.APR_INET, Socket.SOCK_STREAM,
                                   Socket.APR_PROTO_TCP, serverPool);
        if (OS.IS_UNIX) {
            Socket.optSet(serverSocket, Socket.APR_SO_REUSEADDR, 1);
        }
        int rc = Socket.bind(serverSocket, inetAddress);
        Assert.assertEquals("Can't bind: " + Error.strerror(rc), 0, rc);
        Socket.listen(serverSocket, 5);
        if (!OS.IS_UNIX) {
            Socket.optSet(serverSocket, Socket.APR_SO_REUSEADDR, 1);
        }

        long localAddress = Address.get(Socket.APR_LOCAL, serverSocket);
        port = Address.getInfo(localAddress).port;
    }


    @After
    public void destroy() {
        if (clientSocket != 0) {
            Socket.close(clientSocket);
            Socket.destroy(clientSocket);
        }
        if (serverSocket != 0) {
            Socket.close(serverSocket);
            Socket.destroy(serverSocket);
        }
    }


    @Test
    public void testPort() {
        Assert.assertTrue(port > 0);
    }


    @Test
    public void testBlockingReadFromClientWithTimeout() throws Exception {
        /* Start the client that connects to the server */
        Client client = new Client(port);
        client.start();

        /* Accept the client connection */
        clientSocket = Socket.accept(serverSocket);

        /* Configure a 1s timeout for reading from client */
        Socket.timeoutSet(clientSocket, 1000000);
        long timeout = Socket.timeoutGet(clientSocket);
        Assert.assertEquals("Socket.timeoutGet clientSocket failed", 1000000, timeout);

        byte [] buf = new byte[1];
        long start = System.nanoTime();
        while (Socket.recv(clientSocket, buf, 0, 1) == 1) {
        }
        long wait = System.nanoTime() - start;
        Assert.assertFalse("Socket.timeoutSet failed (<1s) [" + wait + "] +-[" + ERROR_MARGIN + "]",
                wait < 1000000000 - ERROR_MARGIN);
        Assert.assertFalse("Socket.timeoutSet failed (>2s) [" + wait + "] +-[" + ERROR_MARGIN + "]",
                wait > 2000000000 + ERROR_MARGIN);

        client.countDown();
        client.join();
    }


    @Test
    public void testNonBlockingReadFromClient() throws Exception {
        /* Start the client that connects to the server */
        Client client = new Client(port);
        client.start();

        /* Accept the client connection */
        clientSocket = Socket.accept(serverSocket);

        /* Configure the connection for non-blocking */
        Socket.optSet(clientSocket, Socket.APR_SO_NONBLOCK, 1);
        int val = Socket.optGet(clientSocket, Socket.APR_SO_NONBLOCK);
        Assert.assertEquals("Socket.optGet clientSocket failed", 1, val);

        byte [] buf = new byte[1];
        long start = System.nanoTime();
        while (Socket.recv(clientSocket, buf, 0, 1) == 1) {
        }
        long wait = System.nanoTime() - start;
        Assert.assertFalse("non_blocking client Socket.APR_SO_NONBLOCK failed (>2ms) [" + wait +
                "] +-[" + ERROR_MARGIN + "]", wait > 2000000 + ERROR_MARGIN);

        client.countDown();
        client.join();
    }


    @Test
    public void testNonBlockingReadThenBlockingReadFromClient() throws Exception {
        /* Start the client that connects to the server */
        Client client = new Client(port);
        client.start();

        /* Accept the client connection */
        clientSocket = Socket.accept(serverSocket);

        /* Configure the connection for non-blocking */
        Socket.optSet(clientSocket, Socket.APR_SO_NONBLOCK, 1);

        byte [] buf = new byte[1];
        long start = System.nanoTime();
        while (Socket.recv(clientSocket, buf, 0, 1) == 1) {
        }
        long wait = System.nanoTime() - start;
        Assert.assertFalse("non_blocking client Socket.APR_SO_NONBLOCK failed (>1ms) [" + wait +
                "] +-[" + ERROR_MARGIN + "]", wait > 1000000 + ERROR_MARGIN);

        /* Configure for blocking */
        Socket.optSet(clientSocket, Socket.APR_SO_NONBLOCK, 0);
        Socket.timeoutSet(clientSocket, 2000);
        start = System.nanoTime();
        while (Socket.recv(clientSocket, buf, 0, 1) == 1) {
        }
        wait = System.nanoTime() - start;
        Assert.assertFalse("non_blocking client Socket.APR_SO_NONBLOCK false failed (<1ms) [" +
                wait + "] +-[" + ERROR_MARGIN + "]", wait < 1000000 - ERROR_MARGIN);

        client.countDown();
        client.join();
    }


    @Test
    public void testNonBlockingAcceptWithNoClient() throws Exception {
        Socket.optSet(serverSocket, Socket.APR_SO_NONBLOCK, 1);
        int val = Socket.optGet(serverSocket, Socket.APR_SO_NONBLOCK);
        Assert.assertEquals("Socket.optGet serverSocket failed", 1, val);

        long start = System.nanoTime();
        boolean ok = false;
        try {
            Socket.accept(serverSocket);
        } catch (Exception ex) {
            ok = true;
        }
        long wait = System.nanoTime() - start;
        Assert.assertTrue("Timeout failed", ok);
        Assert.assertFalse("non_blocking accept Socket.APR_SO_NONBLOCK failed (>10ms) [" + wait +
                "] +-[" + ERROR_MARGIN + "]", wait > 10000000 + ERROR_MARGIN);
    }


    /**
     * Simple client that connects, sends a single byte then closes the
     * connection.
     */
    private static class Client extends java.lang.Thread {

        private final int port;
        private final CountDownLatch complete = new CountDownLatch(1);

        public Client(int port) throws Exception {
            this.port = port;
        }

        public void countDown() {
            complete.countDown();
        }

        @Override
        public void run() {

            try (java.net.Socket sock = new java.net.Socket(TestSocketServer.HOST, port)) {
                OutputStream os = sock.getOutputStream();
                os.write('A');
                os.flush();
                complete.await();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
