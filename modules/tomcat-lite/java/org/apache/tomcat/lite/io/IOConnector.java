/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.util.Timer;


/**
 * Factory for IOChannels, with support for caching.
 *
 *
 * @author Costin Manolache
 */
public abstract class IOConnector {

    public static interface DataReceivedCallback {
        /**
         * Called when data or EOF has been received.
         */
        public void handleReceived(IOChannel ch) throws IOException;
    }

    /**
     * Callback for accept and connect.
     *
     * Will also be called if an error happens while connecting, in
     * which case the connection will be closed.
     */
    public static interface ConnectedCallback {
        public void handleConnected(IOChannel ch) throws IOException;
    }

    public static interface DataFlushedCallback {
        public void handleFlushed(IOChannel ch) throws IOException;
    }

    protected Timer timer;

    public Timer getTimer() {
        return timer;
    }

    /**
     * If the connector is layered on top of a different connector,
     * return the lower layer ( for example the socket connector)
     */
    public IOConnector getNet() {
        return null;
    }

    public abstract void acceptor(IOConnector.ConnectedCallback sc,
                         CharSequence port, Object extra)
        throws IOException;

    // TODO: failures ?
    // TODO: use String target or url
    public abstract void connect(String host, int port,
            IOConnector.ConnectedCallback sc) throws IOException;

    public void stop() {
        if (timer != null) {
            timer.cancel();
        }
    }
}
