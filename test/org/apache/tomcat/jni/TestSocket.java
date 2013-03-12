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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.SecureRandom;

import javax.net.SocketFactory;
import javax.net.ssl.TrustManager;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.net.TesterSupport;

public class TestSocket {

    private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

    // 1,000        Writes all the data in the first write
    // 100,000      Shows strange return values for written
    // 10,000,000   Shows strange return values for written more clearly
    private static final int LIMIT = 100000;
    private static final byte[] DATA;
    private static final boolean ENABLE_SSL = true;

    static {
        int pos = 0;
        int size = 0;
        for (int i = 0; i < LIMIT; i++) {
            String s = Integer.toString(i);
            size += s.length();
        }
        DATA = new byte[size];
        for (int i = 0; i < LIMIT; i++) {
            String s = Integer.toString(i);
            byte[] b = s.getBytes(ISO_8859_1);
            System.arraycopy(b, 0, DATA, pos, b.length);
            pos += b.length;
        }
    }

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
        SocketFactory factory;
        if (ENABLE_SSL) {
            javax.net.ssl.SSLContext sc =
                    javax.net.ssl.SSLContext.getInstance("SSL");
            sc.init(null, new TrustManager[] {new TesterSupport.TrustAllCerts()} ,
                    new SecureRandom());
             factory = sc.getSocketFactory();
        } else {
            factory = SocketFactory.getDefault();
        }
        java.net.Socket socket = factory.createSocket(host, port);

        // Infinite timeout
        socket.setSoTimeout(0);
        InputStream is = socket.getInputStream();

        ByteBuffer bb = ByteBuffer.allocate(20);
        int data = 0;


        // Read the first 20 digits.
        data = clientRead(is, bb, data, 20);
        System.out.println("Client read first 20 digits");

        // Block until the server fills up it's send buffers and then sleep for
        // 5 seconds
        int count = 0;
        while (count < 3) {
            java.lang.Thread.sleep(1000);
            if (s.isPolling()) {
                count ++;
            }
        }

        // Read 30% of the data
        data = clientRead(is, bb, data, (int) (LIMIT * 0.3));
        System.out.println("Client read first 30% of digits");

        // Block until the server fills up it's send buffers and then sleep for
        // 5 seconds
        count = 0;
        while (count < 3) {
            java.lang.Thread.sleep(1000);
            if (s.isPolling()) {
                count ++;
            }
        }

        // Read to the end
        clientRead(is, bb, data, LIMIT);
        System.out.println("Client read all digits");

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
                if (data%1000 == 0) {
                    System.out.println("Client read to  [" + data + "]");
                }
            }
        }
        return data;
    }


    private static final class Server implements Runnable {

        private final long rootPool;
        private final long serverSocket;
        private final long serverSocketPool;
        private final long sslContext;
        private final long pollerPool;
        private final long poller;

        private volatile boolean polling = false;

        public Server() throws Exception {
            rootPool = Pool.create(0);

            // Server socket
            /*long serverSockPool = */ Pool.create(rootPool);
            long inetAddress =
                    Address.info(null, Socket.APR_INET, 0, 0, rootPool);
            serverSocket = Socket.create(
                    Address.getInfo(inetAddress).family,
                    Socket.SOCK_STREAM,
                    Socket.APR_PROTO_TCP,
                    rootPool);

            int ret = Socket.bind(serverSocket, inetAddress);
            if (ret != 0) {
                throw new IOException("Bind failed [" + ret + "]");
            }
            ret = Socket.listen(serverSocket, 100);
            if (ret != 0) {
                throw new IOException("Listen failed [" + ret + "]");
            }

            // Setup SSL
            if (ENABLE_SSL) {
                SSL.randSet("builtin");
                SSL.initialize(null);
                sslContext = SSLContext.make(
                        rootPool, SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);
                SSLContext.setCipherSuite(sslContext, "ALL");
                File certFile = new File(
                        "test/org/apache/tomcat/util/net/localhost-cert.pem");
                File keyFile = new File(
                        "test/org/apache/tomcat/util/net/localhost-key.pem");
                SSLContext.setCertificate(sslContext, certFile.getAbsolutePath(),
                        keyFile.getAbsolutePath(), null, SSL.SSL_AIDX_RSA);
                SSLContext.setVerify(sslContext, SSL.SSL_CVERIFY_NONE, 10);
            } else {
                sslContext = 0;
            }

            // Poller
            serverSocketPool = Pool.create(rootPool);
            pollerPool = Pool.create(serverSocketPool);
            poller = Poll.create(10, pollerPool, 0, -1);
        }


        public int getPort() throws Exception {
            long sa = Address.get(Socket.APR_LOCAL, serverSocket);
            Sockaddr addr = Address.getInfo(sa);
            return addr.port;
        }


        public boolean isPolling() {
            return polling;
        }

        @Override
        public void run() {
            try {
                // Accept an incoming connection
                long socket = Socket.accept(serverSocket);

                // Configure SSL
                if (sslContext != 0) {
                    SSLSocket.attach(sslContext, socket);
                    if (SSLSocket.handshake(socket) != 0) {
                        System.err.println("SSL handshake failed");
                    }
                }

                // Make socket non-blocking
                Socket.timeoutSet(socket, 0);

                int start = 0;
                int left = DATA.length;
                int written;

                System.out.println("To write [" + left + "]");
                do {
                    written = Socket.send(socket, DATA, start, left);
                    if (written < 0) {
                        if (Status.APR_STATUS_IS_EAGAIN(-written)) {
                            System.out.println("EAGAIN");
                            written = 0;
                        } else {
                            System.out.println("Error code [" + -written + "]");
                            throw new RuntimeException();
                        }
                    }

                    start += written;
                    left -= written;
                    System.out.println(
                            "Written: [" +written + "], Left [" + left + "]");

                    if (written == 0 && left > 0) {
                        // Write buffer is full. Poll until there is space
                        Poll.add(poller, socket, Poll.APR_POLLOUT);
                        polling = true;
                        int pollCount = 0;

                        int rv = 0;
                        long[] desc = new long[2];

                        while (rv == 0) {
                            System.out.println("Poll. Left [" + left + "]");
                            rv = Poll.poll(poller, 1000000, desc, true);
                            pollCount++;

                            if (rv > 0) {
                                // There is space. Continue to write.
                            } else if (-rv == Status.TIMEUP) {
                                rv = 0;
                                // Poll timed out. Poll again.
                            } else if (rv < 0) {
                                // Something went wrong
                                System.err.println(
                                        "Poller failure [" + -rv + "]");
                            } else {
                                // rv == 0. Poll again.
                            }

                            if (pollCount > 10) {
                                // Should never get stuck in a polling loop
                                // for this long.
                                System.err.println(
                                        "Polling loop [" + pollCount + "]");
                            }
                        }
                        polling = false;
                    }
                } while (left > 0);
            } catch (Exception e) {
                // May need to do something here
                e.printStackTrace();
            }
            // If this finishes early, let the client think it is polling so the
            // client can complete
            polling = true;
        }
    }
}
