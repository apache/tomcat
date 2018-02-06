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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for server-side sockets using any local address (0.0.0.0 or ::).
 */
public class TestSocketServerAnyLocalAddress extends AbstractJniTest {

    // Excessive but allows for slow systems
    private static final int TIMEOUT_MICROSECONDS = 10 * 1000 * 1000;

    private long serverSocket = 0;
    private long clientSocket = 0;


    @Before
    public void init() throws Exception {
        long serverPool = Pool.create(0);
        long inetAddress = Address.info(null, Socket.APR_UNSPEC,
                                        0, 0, serverPool);
        serverSocket = Socket.create(Address.getInfo(inetAddress).family, Socket.SOCK_STREAM,
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
    public void testWithClient() throws Exception {
        /* Start the client that connects to the server */
        Client client = new Client(serverSocket);
        client.start();

        boolean running = true;
        while (running) {
            /* Accept the client connection */
            clientSocket = Socket.accept(serverSocket);

            /* Configure a 10s timeout for reading from client */
            Socket.timeoutSet(clientSocket, TIMEOUT_MICROSECONDS);

            byte [] buf = new byte[1];
            while (Socket.recv(clientSocket, buf, 0, 1) == 1) {
                // If 'A' was read, echo back 'Z'
                if (buf[0] == 'A') {
                    buf[0] = 'Z';
                    Socket.send(clientSocket, buf, 0, 1);
                }
            }
            if (buf[0] == 'E') {
                running = false;
            } else if (buf[0] == 'Z') {
                // NO-OP - connection closing
            } else {
                Assert.fail("Unexpected data [" + (char) buf[0] + "]");
            }
        }

        client.join();
    }


    /**
     * Simple client that connects, sends a single byte then closes the
     * connection.
     */
    private static class Client extends java.lang.Thread {

        private final long serverSocket;

        public Client(long serverSocket) throws Exception {
            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {

            try {
                InetSocketAddress connectAddress = getConnectAddress(serverSocket);
                java.net.Socket sock = new java.net.Socket();
                sock.connect(connectAddress, TIMEOUT_MICROSECONDS);
                sock.setSoTimeout(TIMEOUT_MICROSECONDS);
                OutputStream ou = sock.getOutputStream();
                InputStream in =  sock.getInputStream();
                ou.write('A');
                ou.flush();
                int rep = in.read();
                sock.close();
                if (rep != 'Z') {
                     throw new Exception("Read wrong data [" + rep + "]");
                }

                sock = new java.net.Socket();
                sock.connect(connectAddress, TIMEOUT_MICROSECONDS);
                sock.setSoTimeout(TIMEOUT_MICROSECONDS);
                ou = sock.getOutputStream();
                ou.write('E');
                ou.flush();
                sock.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        /*
         * Assumes server is listening on any local address
         */
        private static InetSocketAddress getConnectAddress(long serverSocket) throws Exception {
            long sa = Address.get(Socket.APR_LOCAL, serverSocket);
            Sockaddr addr = Address.getInfo(sa);
            InetSocketAddress localAddress;
            if (addr.family == Socket.APR_INET6) {
                localAddress = new InetSocketAddress("::", addr.port);
            } else {
                localAddress = new InetSocketAddress("0.0.0.0", addr.port);
            }

            // Need a local address of the same type (IPv4 or IPV6) as the
            // configured bind address since the connector may be configured
            // to not map between types.
            InetAddress loopbackConnectAddress = null;
            InetAddress linkLocalConnectAddress = null;

            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (localAddress.getAddress().getClass().isAssignableFrom(inetAddress.getClass())) {
                        if (inetAddress.isLoopbackAddress()) {
                            if (loopbackConnectAddress == null) {
                                loopbackConnectAddress = inetAddress;
                            }
                        } else if (inetAddress.isLinkLocalAddress()) {
                            if (linkLocalConnectAddress == null) {
                                linkLocalConnectAddress = inetAddress;
                            }
                        } else {
                            // Use a non-link local, non-loop back address by default
                            return new InetSocketAddress(inetAddress, localAddress.getPort());
                        }
                    }
                }
            }
            // Prefer loop back over link local since on some platforms (e.g.
            // OSX) some link local addresses are not included when listening on
            // all local addresses.
            if (loopbackConnectAddress != null) {
                return new InetSocketAddress(loopbackConnectAddress, localAddress.getPort());
            }
            if (linkLocalConnectAddress != null) {
                return new InetSocketAddress(linkLocalConnectAddress, localAddress.getPort());
            }
            // Fallback
            return new InetSocketAddress("localhost", localAddress.getPort());
        }
    }
}
