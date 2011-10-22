/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.util.Timer;

public class MemoryIOConnector extends IOConnector {

    public static class MemoryIOChannel extends IOChannel {
        IOBuffer netIn = new IOBuffer(this) {
            protected void notifyDataAvailable(Object bb) throws IOException {
                sendHandleReceivedCallback();
                super.notifyDataAvailable(bb);
            }
        };
        IOBuffer netOut = new IOBuffer(this);

        /**
         * All flushed output will be saved to 'out'.
         */
        public BBuffer out = BBuffer.allocate(4096);

        public MemoryIOChannel() {
        }

        public void startSending() throws IOException {
            //
            IOBuffer bb = netOut;
            while (true) {
                if (bb.isClosedAndEmpty()) {
                    break;
                }
                BBucket first = bb.peekFirst();
                if (first == null) {
                    break;
                }
                BBucket iob = ((BBucket) first);
                out.append(iob.array(), iob.position(), iob.remaining());
                bb.advance(iob.remaining());
                iob.release();
            }

            handleFlushed(this);
        }

        @Override
        public IOBuffer getIn() {
            return netIn;
        }
        @Override
        public IOBuffer getOut() {
            return netOut;
        }
    }

    // TODO: in-process communication without sockets for testing
    ConnectedCallback acceptor;
    MemoryIOConnector server;

    public MemoryIOConnector() {
        timer = new Timer(true);
    }

    public MemoryIOConnector withServer(MemoryIOConnector server) {
        this.server = server;
        return server;
    }

    @Override
    public void acceptor(ConnectedCallback sc, CharSequence port, Object extra)
            throws IOException {
        this.acceptor = sc;
    }

    @Override
    public void connect(String host, int port, ConnectedCallback sc)
            throws IOException {
        IOChannel ch = new MemoryIOChannel();
        IOChannel sch = new MemoryIOChannel();
        // TODO: mix
        if (server != null && server.acceptor != null) {
            server.acceptor.handleConnected(sch);
        }
        sc.handleConnected(ch);
    }

}