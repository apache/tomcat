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
package org.apache.tomcat.spdy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Spdy context for 'proxy' or test mode spdy - no NPN, no SSL, no compression.
 *
 * This can be supported without JNI dependencies.
 * It can be modified to support SSL and compression - but so far the only way
 * to use NPN is via JNI.
 */
public class SpdyContextProxy extends SpdyContext {

    protected Socket getSocket(String host, int port) throws IOException {
        return new Socket(host, port);
    }

    @Override
    public SpdyConnection getConnection(String host, int port) throws IOException {
        try {
            Socket sock = getSocket(host, port);

            sock.getInputStream();
            SpdyConnectionSocket con = new SpdyConnectionSocket(this, sock);

            getExecutor().execute(con.inputThread);
            return con;
        } catch (IOException ex) {
            ex.printStackTrace();
            throw ex;
        }

    }

    public SpdyConnection getConnection(Socket socket) {
        return new SpdyConnectionSocket(this, socket);
    }

    public static class SpdyConnectionSocket extends SpdyConnection {
        Socket socket;


        Runnable inputThread = new Runnable() {
            @Override
            public void run() {
                onBlockingSocket();
                try {
                    inClosed = true;
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        public SpdyConnectionSocket(SpdyContext spdyContext) {
            super(spdyContext);
        }

        public SpdyConnectionSocket(SpdyContext spdyContext, Socket socket) {
            super(spdyContext);
            this.socket = socket;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }

        @Override
        public synchronized int write(byte[] data, int off, int len) throws IOException {
            socket.getOutputStream().write(data, off, len);
            return len;
        }

        @Override
        public int read(byte[] data, int off, int len) throws IOException {
            try {
                return socket.getInputStream().read(data, off, len);
            } catch (SocketTimeoutException ex) {
                return 0;
            }
        }
    }


    boolean running = true;
    ServerSocket serverSocket;

    @Override
    public void stop() throws IOException {
        running = false;
        serverSocket.close();
    }

    /**
     *  For small servers/testing: run in server mode.
     *  Need to override onSynStream() to implement the logic.
     */
    @Override
    public void listen(final int port, String cert, String key) throws IOException {
        getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                accept(port);
            }
        });
    }

    private void accept(int port) {
        try {
            serverSocket = new ServerSocket(port);
            while (running) {
                final Socket socket = serverSocket.accept();
                final SpdyConnection con = getConnection(socket);
                getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        con.onBlockingSocket();
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (IOException ex) {
            if (running) {
                ex.printStackTrace();
            }
            running = false;
        }
    }
}
