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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.junit.Assert;
import org.junit.Test;

public class TestSocket {

    private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
    private static final Object clientWait = new Object();
    private static final Object serverWait = new Object();

    @Test
    public void testNonBlockingEagain() throws Exception {

        try {
            Library.initialize(null);
        } catch (Throwable t) {
            System.err.println("Unable to initialize APR library");
            t.printStackTrace(System.err);
            return;
        }

        Server s = new Server();

        int port = s.getPort();

        // Start the server
        java.lang.Thread t = new java.lang.Thread(s);
        t.setName("testNonBlockingEagain-Server");
        t.start();

        // Open a connection to the server
        String host = null;
        java.net.Socket socket = new java.net.Socket(host, port);
        // Infinite timeout
        socket.setSoTimeout(0);
        InputStream is = socket.getInputStream();

        ByteBuffer bb = ByteBuffer.allocate(20);
        int data = 0;


        // Read the first 20 digits.
        data = clientRead(is, bb, data, 20);

        // Block until the server fills up it's send buffers
        synchronized (clientWait) {
            clientWait.wait();
        }

        // Read the next 1000 digits
        data = clientRead(is, bb, data, 1000);

        // Unblock the server
        synchronized (serverWait) {
            serverWait.notify();
        }

        // Read to the end
        clientRead(is, bb, data, 100000);

        socket.close();
    }


    private int clientRead(InputStream is, ByteBuffer bb, int data, int limit)
            throws IOException {
        while (data < limit) {
            byte[] readBuffer = new byte[10];
            byte[] dataBuffer = new byte[10];
            int read = is.read(readBuffer);
            if (read == -1) {
                Assert.fail();
            }
            bb.put(readBuffer, 0, read);

            String expected = Integer.toString(data);
            // Short-cut In ISO-8859-1 1 char  uses 1 byte
            int len = expected.length();
            while (bb.position() >= len) {
                bb.flip();
                bb.get(dataBuffer, 0, len);
                String actual = new String(dataBuffer, 0, len, ISO_8859_1);
                Assert.assertEquals(expected, actual);
                data++;
                expected = Integer.toString(data);
                len = expected.length();
                bb.compact();
            }
        }
        return data;
    }


    private static final class Server implements Runnable {

        private final long rootPool;
        private final long serverSock;

        public Server() throws Exception {
            rootPool = Pool.create(0);

            // Server socket
            /*long serverSockPool = */ Pool.create(rootPool);
            long inetAddress =
                    Address.info(null, Socket.APR_INET, 0, 0, rootPool);
            serverSock = Socket.create(
                    Address.getInfo(inetAddress).family,
                    Socket.SOCK_STREAM,
                    Socket.APR_PROTO_TCP,
                    rootPool);

            int ret = Socket.bind(serverSock, inetAddress);
            if (ret != 0) {
                throw new IOException("Bind failed [" + ret + "]");
            }
            ret = Socket.listen(serverSock, 100);
            if (ret != 0) {
                throw new IOException("Listen failed [" + ret + "]");
            }
        }


        public int getPort() throws Exception {
            long sa = Address.get(Socket.APR_LOCAL, serverSock);
            Sockaddr addr = Address.getInfo(sa);
            return addr.port;
        }


        @Override
        public void run() {
            try {
                // Accept an incoming connection
                long socket = Socket.accept(serverSock);

                // Make socket non-blocking
                Socket.timeoutSet(socket, 0);

                boolean first = true;

                int data = 0;
                do {
                    String s = Integer.toString(data);
                    byte[] b = s.getBytes(ISO_8859_1);

                    int start = 0;
                    int len = b.length;
                    int written;

                    do {
                        written = Socket.send(socket, b, start, len);
                        if (written < 0) {
                            if (Status.APR_STATUS_IS_EAGAIN(-written)) {
                                // System.out.println("EAGAIN");
                                written = 0;
                            } else {
                                System.out.println("Error code [" + -written + "]");
                                throw new RuntimeException();
                            }
                        }

                        start += written;
                        len -= written;

                        if (written == 0 && len > 0) {
                            if (first) {
                                first = false;
                                // Stop client from blocking to free up some
                                // space so the server can write
                                synchronized (clientWait) {
                                    clientWait.notify();
                                }
                                // Wait for the client
                                synchronized (serverWait) {
                                    serverWait.wait();
                                }
                            }
                        }
                    } while (len > 0);

                    data++;
                } while (data < 100000);

            } catch (Exception e) {
                // May need to do something here
                e.printStackTrace();
            }
        }
    }
}
