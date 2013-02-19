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

import org.apache.tomcat.jni.SSLExt;
import org.apache.tomcat.jni.Status;
import org.apache.tomcat.jni.socket.AprSocket;
import org.apache.tomcat.jni.socket.AprSocketContext;
import org.apache.tomcat.jni.socket.AprSocketContext.NonBlockingPollHandler;
import org.apache.tomcat.jni.socket.AprSocketContext.TlsCertVerifier;


public class NetSupportOpenSSL extends SpdyContext.NetSupport {

    private AprSocketContext con;

    public NetSupportOpenSSL() {
        con = new AprSocketContext();
        //if (insecureCerts) {
        con.customVerification(new TlsCertVerifier() {
            @Override
            public void handshakeDone(AprSocket ch) {
            }
        });
        //}
        con.setNpn("spdy/2");
    }

    @Override
    public boolean isSpdy(Object socketW) {
        byte[] proto = new byte[32];
        int len = SSLExt.getNPN(((Long) socketW).longValue(), proto);
        return len == 6; // todo: check spdy/2
    }

    @Override
    public SpdyConnection getConnection(String host, int port) throws IOException {
        SpdyConnectionAprSocket spdy = new SpdyConnectionAprSocket(ctx);

        AprSocket ch = con.socket(host, port, ctx.tls);

        spdy.setSocket(ch);

        ch.connect();

        ch.setHandler(new SpdySocketHandler(spdy));

        // need to consume the input to receive more read events
        int rc = spdy.processInput();
        if (rc == SpdyConnection.CLOSE) {
            ch.close();
            throw new IOException("Error connecting");
        }

        return spdy;
    }

    @Override
    public void onAccept(Object socket) {
        onAcceptLong(((Long) socket).longValue());
    }

    public void onAcceptLong(long socket) {
        SpdyConnectionAprSocket spdy = new SpdyConnectionAprSocket(ctx);
        AprSocket s = con.socket(socket);
        spdy.setSocket(s);

        SpdySocketHandler handler = new SpdySocketHandler(spdy);
        s.setHandler(handler);
        handler.process(s, true, true, false);
    }

    public AprSocketContext getAprContext() {
        return con;
    }


    @Override
    public void listen(final int port, String cert, String key) throws IOException {
        con = new AprSocketContext() {
            @Override
            protected void onSocket(AprSocket s) {
                SpdyConnectionAprSocket spdy = new SpdyConnectionAprSocket(ctx);
                spdy.setSocket(s);

                SpdySocketHandler handler = new SpdySocketHandler(spdy);
                s.setHandler(handler);
            }
        };

        con.setNpn(SpdyContext.SPDY_NPN_OUT);
        con.setKeys(cert, key);

        con.listen(port);
    }

    @Override
    public void stop() throws IOException {
        con.stop();
    }

    // NB
    private static class SpdySocketHandler implements NonBlockingPollHandler {
        private final SpdyConnection con;

        SpdySocketHandler(SpdyConnection con) {
            this.con = con;
        }

        @Override
        public void closed(AprSocket ch) {
            // not used ( polling not implemented yet )
        }

        @Override
        public void process(AprSocket ch, boolean in, boolean out, boolean close) {
            try {
                int rc = con.processInput();
                if (rc == SpdyConnection.CLOSE) {
                    ch.close();
                }
                con.drain();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                ch.reset();
            }
        }

        @Override
        public void connected(AprSocket ch) {
        }

        @Override
        public void error(AprSocket ch, Throwable t) {
        }

    }

    private static class SpdyConnectionAprSocket extends SpdyConnection {
        private AprSocket socket;

        public SpdyConnectionAprSocket(SpdyContext spdyContext) {
            super(spdyContext);
        }

        public void setSocket(AprSocket ch) {
            this.socket = ch;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }

        @Override
        public int write(byte[] data, int off, int len) throws IOException {
            if (socket == null) {
                return -1;
            }
            int sent = socket.write(data, off, len);
            if (sent < 0) {
                return -1;
            }
            return sent;
        }

        /**
         * @throws IOException
         */
        @Override
        public int read(byte[] data, int off, int len) throws IOException {
            if (socket == null) {
                return -1;
            }
            int rd = socket.read(data, off, len);
            // org.apache.tomcat.jni.Socket.recv(socket, data, off, len);
            if (rd == -Status.APR_EOF) {
                return -1;
            }
            if (rd == -Status.TIMEUP || rd == -Status.EINTR || rd == -Status.EAGAIN) {
                rd = 0;
            }
            if (rd < 0) {
                return -1;
            }
            return rd;
        }
    }


}
