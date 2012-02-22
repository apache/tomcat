/*
 */
package org.apache.tomcat.spdy;

import java.io.IOException;
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


    @Override
    public SpdyConnection getConnection(String host, int port) throws IOException {
        try {
            Socket sock = new Socket(host, port);

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
}
