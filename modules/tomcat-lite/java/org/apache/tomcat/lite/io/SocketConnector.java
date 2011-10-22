/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.apache.tomcat.lite.io.NioChannel.NioChannelCallback;

/**
 * Class for handling sockets. It manages a pool of SelectorThreads, fully
 * non-blocking. There is no caching or buffer management. SelectorChannel
 * represents on connection.
 *
 * In the old types, the connector was socket-centric, and quite ugly. After
 * many refactoring the buffers ( buckets and brigade ) and callbacks are
 * used everywhere, and the sockets play a supporting role.
 *
 * TODO: discover if APR is available and use it, or fall back to NIO.
 *
 * @author Costin Manolache
 */
public class SocketConnector extends IOConnector {
    static Logger log = Logger.getLogger(SocketConnector.class.getName());
    static boolean debug = false;

    // TODO: pool, balanced usage
    // TODO: bind into OM or callback when created

    private NioThread selector;

    // For resolving DNS ( i.e. connect )
    Executor threadPool = Executors.newCachedThreadPool();

    public SocketConnector() {
        timer = new Timer(true);
    }

    public SocketConnector(int port) {
        timer = new Timer(true);
    }

    /**
     * This may be blocking - involves host resolution, connect.
     * If the IP address is provided - it shouldn't block.
     */
    @Override
    public void connect(final String host, final int port,
                             final IOConnector.ConnectedCallback sc) throws IOException {
        final SocketIOChannel ioch = new SocketIOChannel(this, null, host + ":" + port);
        ioch.setConnectedCallback(sc);
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    getSelector().connect(new InetSocketAddress(host, port), ioch, null);
                } catch (Throwable e) {
                    e.printStackTrace();
                    try {
                        sc.handleConnected(ioch);
                        ioch.close();
                    } catch (Throwable e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * Create a new server socket, register the callback.
     * If port == 0 it'll use the inherited channel, i.e. inetd mode.
     * TODO: if port == -1, detect a free port. May block.
     */
    public void acceptor(final IOConnector.ConnectedCallback sc,
                         final CharSequence address, Object extra)
        throws IOException
    {
        final int port = Integer.parseInt(address.toString());
        NioChannelCallback acceptCb = new NioChannelCallback() {
            @Override
            public void handleClosed(NioChannel ch) throws IOException {
            }

            @Override
            public void handleConnected(NioChannel ch) throws IOException {
                SocketIOChannel ioch = new SocketIOChannel(SocketConnector.this,
                        ch, ":" + port);
                sc.handleConnected(ioch);
            }

            @Override
            public void handleReadable(NioChannel ch) throws IOException {
            }

            @Override
            public void handleWriteable(NioChannel ch) throws IOException {
            }
        };

        if (port == -1) {
            // TODO: find an unused port
        } else if (port == 0) {
            getSelector().inetdAcceptor(acceptCb);
        }  else {
            getSelector().acceptor(acceptCb, port, null, 200, 20000);
        }
    }

    static int id = 0;

    public synchronized NioThread getSelector() {
        if (selector == null) {
            String name = "SelectorThread-" + id++;
            selector = new NioThread(name, true);
        }

        return selector;
    }

    public void stop() {
        getSelector().stop();
    }


    // TODO: suspendAccept(boolean)

}
