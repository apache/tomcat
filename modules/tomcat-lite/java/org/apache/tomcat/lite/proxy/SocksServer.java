/*
 */
package org.apache.tomcat.lite.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tomcat.lite.io.IOBuffer;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;
import org.apache.tomcat.lite.io.SocketConnector;

/**
 * A test for the selector package, and helper for the proxy -
 * a SOCKS4a server.
 *
 * Besides the connection initialization, it's almost the
 *  same as the CONNECT method in http proxy.
 *
 * http://ftp.icm.edu.pl/packages/socks/socks4/SOCKS4.protocol
 * http://www.smartftp.com/Products/SmartFTP/RFC/socks4a.protocol
 * http://www.faqs.org/rfcs/rfc1928.html
 * https://svn.torproject.org/svn/tor/trunk/doc/spec/socks-extensions.txt
 *
 * In firefox, set network.proxy.socks_remote_dns = true to do DNS via proxy.
 *
 * Also interesting:
 * http://transocks.sourceforge.net/
 *
 * @author Costin Manolache
 */
public class SocksServer implements Runnable, IOConnector.ConnectedCallback {
    protected int port = 2080;

    protected IOConnector ioConnector;
    protected static Logger log = Logger.getLogger("SocksServer");

    protected long idleTimeout = 10 * 60000; // 10 min

    protected long lastConnection = 0;
    protected long totalConTime = 0;
    protected AtomicInteger totalConnections = new AtomicInteger();

    protected AtomicInteger active = new AtomicInteger();

    protected long inBytes;
    protected long outBytes;
    protected static int sockets;

    public int getPort() {
        return port;
    }

    public int getActive() {
        return active.get();
    }

    public int getTotal() {
        return totalConnections.get();
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void handleAccepted(IOChannel accepted) throws IOException {
        lastConnection = System.currentTimeMillis();
        active.incrementAndGet();
        totalConnections.incrementAndGet();
        sockets++;

        final SocksServerConnection socksCon = new SocksServerConnection(accepted);
        socksCon.pool = ioConnector;
        socksCon.server = this;

        accepted.setDataReceivedCallback(socksCon);
        socksCon.handleReceived(accepted);
    }

    /**
     * Exit if no activity happens.
     */
    public void setIdleTimeout(long to) {
        idleTimeout = to;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void stop() {
        ioConnector.stop();
    }

    public void initServer() throws IOException {
        if (ioConnector == null) {
            ioConnector = new SocketConnector();
        }
        ioConnector.acceptor(this, Integer.toString(port), null);

        final Timer timer = new Timer(true /* daemon */);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                // if lastConnection == 0 - it'll terminate on first timer
                float avg = (totalConnections.get() > 0) ?
                        totalConTime / totalConnections.get() : 0;
                System.err.println("Socks:"
                        + "\ttotal=" + totalConnections
                        + "\tin=" + inBytes
                        + "\tout=" + outBytes
                        + "\tavg=" + (int) avg);
                if (active.get() <= 0
                        && idleTimeout > 0
                        && System.currentTimeMillis() - lastConnection > idleTimeout) {
                    System.err.println("Idle timeout");
                    stop();
                    this.cancel();
                    timer.cancel();
                }
                } catch (Throwable t) {
                    log.log(Level.SEVERE, "Error in timer", t);
                }
            }
        }, 5 * 60 * 1000, 5 * 60 * 1000); // 5


    }


    public static class SocksServerConnection implements IOConnector.DataReceivedCallback, IOConnector.ConnectedCallback {

        protected SocksServer server;

        boolean headReceived;
        boolean head5Received = false;

        ByteBuffer headBuffer = ByteBuffer.allocate(256);
        ByteBuffer headReadBuffer = headBuffer.duplicate();

        ByteBuffer headResBuffer = ByteBuffer.allocate(256);
        IOConnector pool;
        byte ver;
        byte cmd;
        long startTime = System.currentTimeMillis();

        static final int CMD_CONNECT = 0;
        static final byte CMD_RESOLVE = (byte) 0xF0;

        int port;
        byte[] hostB = new byte[4];
        CharBuffer userId = CharBuffer.allocate(256);
        CharBuffer hostName = CharBuffer.allocate(256);

        SocketAddress sa = null;

        private byte atyp;

        IOChannel serverCh;

        public SocksServerConnection(IOChannel accepted) {
            this.serverCh = accepted;
        }

        protected void afterClientConnect(IOChannel clientCh) throws IOException {
            headResBuffer.clear();
            if (ver == 4) {
                headResBuffer.put((byte) 0);
                headResBuffer.put((byte) 90);
                for (int i = 0; i < 6; i++ ) {
                    headResBuffer.put((byte) 0);
                }
            } else {
                headResBuffer.put((byte) 5);
                headResBuffer.put((byte) 0);
                headResBuffer.put((byte) 0);
                headResBuffer.put((byte) 1); // ip

                headResBuffer.put(hostB);
                int port2 = (Integer) clientCh.getAttribute(IOChannel.ATT_REMOTE_PORT);
                headResBuffer.putShort((short) port2);
            }

            headResBuffer.flip();

            serverCh.getOut().queue(headResBuffer);
            log.fine("Connected " + sa.toString());

            if (headReadBuffer.remaining() > 0) {
                serverCh.getOut().queue(headReadBuffer);
            }
            serverCh.startSending();
        }

        public void afterClose() {
            long conTime = System.currentTimeMillis() - startTime;
            int a = server.active.decrementAndGet();
            if (a < 0) {
                System.err.println("negative !!");
                server.active.set(0);
            }
//            System.err.println(sa + "\tsR:" +
//                    received
//                    + "\tcR:" + clientReceived
//                    + "\tactive:" + a
//                    + "\ttotC:" + server.totalConnections
//                    + "\ttime:" + conTime);
//            server.inBytes += received;
//            server.totalConTime += conTime;
//            server.outBytes += clientReceived;
        }


        protected int parseHead() throws IOException {
            // data is between 0 and pos.
            int pos = headBuffer.position();
            headReadBuffer.clear();
            headReadBuffer.limit(pos);
            if (headReadBuffer.remaining() < 2) {
                return -1;
            }

            ByteBuffer bb = headReadBuffer;
            ver = bb.get();
            if (ver == 5) {
                return parseHead5();
            }
            if (headReadBuffer.remaining() < 8) {
                return -1;
            }
            cmd = bb.get();
            port = bb.getShort();
            bb.get(hostB);
            userId.clear();
            int rc = readStringZ(bb, userId);
            // Mozilla userid: MOZ ...
            if (rc == -1) {
                return rc;
            }
            if (hostB[0] == 0 && hostB[1] == 0 && hostB[2] == 0) {
                // 0.0.0.x
                atyp = 3;
                hostName.clear();
                rc = readStringZ(bb, hostName);
                if (rc == -1) {
                    return rc;
                }
            } else {
                atyp = 1;
            }

            headReceived = true;

            return 4;
        }

        protected int parseHead5_2() throws IOException {
            // data is between 0 and pos.
            int pos = headBuffer.position();

            headReadBuffer.clear();
            headReadBuffer.limit(pos);

            if (headReadBuffer.remaining() < 7) {
                return -1;
            }

            ByteBuffer bb = headReadBuffer;
            ver = bb.get();
            cmd = bb.get();
            bb.get(); // reserved
            atyp = bb.get();
            if (atyp == 1) {
                bb.get(hostB);
            } else if (atyp == 3) {
                hostName.clear();
                int rc = readStringN(bb, hostName);
                if (rc == -1) {
                    return rc;
                }
            } // ip6 not supported right now, easy to add

            port = bb.getShort();

            head5Received = true;

            return 5;
        }

        private int parseHead5() {
            ByteBuffer bb = headReadBuffer;
            int nrMethods = ((int)bb.get()) & 0xFF;
            if (bb.remaining() < nrMethods) {
                return -1;
            }
            for (int i = 0; i < nrMethods; i++) {
                // ignore
                bb.get();
            }
            return 5;
        }

        private int readStringZ(ByteBuffer bb, CharBuffer bc) throws IOException {
            bc.clear();
            while (true) {
                if (!bb.hasRemaining()) {
                    return -1; // not complete
                }
                byte b = bb.get();
                if (b == 0) {
                    bc.flip();
                    return 0;
                } else {
                    bc.put((char) b);
                }
            }
        }

        private int readStringN(ByteBuffer bb, CharBuffer bc) throws IOException {
            bc.clear();
            int len = ((int) bb.get()) & 0xff;
            for (int i = 0; i < len; i++) {
                if (!bb.hasRemaining()) {
                    return -1; // not complete
                }
                byte b = bb.get();
                bc.put((char) b);
            }
            bc.flip();
            return len;
        }

        static ExecutorService connectTP = Executors.newCachedThreadPool();

        protected void startClientConnection() throws IOException {
            // TODO: use different thread ?
            if (atyp == 3) {
                connectTP.execute(new Runnable() {

                    public void run() {
                        try {
                            sa = new InetSocketAddress(hostName.toString(), port);
                            pool.connect(hostName.toString(), port,
                                    SocksServerConnection.this);
                        } catch (Exception ex) {
                            log.severe("Error connecting");
                        }
                    }
                });
            } else {
                InetAddress addr = InetAddress.getByAddress(hostB);
                pool.connect(addr.toString(), port, this);
            } // TODO: ip6
        }

        public void handleConnected(IOChannel ioch) throws IOException {
            ioch.setDataReceivedCallback(new CopyCallback(serverCh));
            //ioch.setDataFlushedCallback(new ProxyFlushedCallback(serverCh, ioch));

            serverCh.setDataReceivedCallback(new CopyCallback(ioch));
            //serverCh.setDataFlushedCallback(new ProxyFlushedCallback(ioch, serverCh));

            afterClientConnect(ioch);

            ioch.sendHandleReceivedCallback();
        }


        @Override
        public void handleReceived(IOChannel net) throws IOException {
            IOBuffer ch = net.getIn();
            //SelectorChannel ch = (SelectorChannel) ioch;
                if (!headReceived) {
                    int rd = ch.read(headBuffer);
                    if (rd == 0) {
                        return;
                    }
                    if (rd == -1) {
                        ch.close();
                    }

                    rd = parseHead();
                    if (rd < 0) {
                        return; // need more
                    }
                    if (rd == 5) {
                        headResBuffer.clear();
                        headResBuffer.put((byte) 5);
                        headResBuffer.put((byte) 0);
                        headResBuffer.flip();
                        net.getOut().queue(headResBuffer);
                        net.startSending();
                        headReceived = true;
                        headBuffer.clear();
                        return;
                    } else {
                        headReceived = true;
                        head5Received = true;
                        startClientConnection();
                    }
                }

                if (!head5Received) {
                    int rd = ch.read(headBuffer);
                    if (rd == 0) {
                        return;
                    }
                    if (rd == -1) {
                        ch.close();
                    }

                    rd = parseHead5_2();
                    if (rd < 0) {
                        return; // need more
                    }

                    startClientConnection();
                }
        }
    }

    @Override
    public void run() {
        try {
            initServer();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void handleConnected(IOChannel ch) throws IOException {
        handleAccepted(ch);
    }
}
