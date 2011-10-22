/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.DumpChannel;
import org.apache.tomcat.lite.io.IOBuffer;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;
import org.apache.tomcat.lite.io.SslProvider;
import org.apache.tomcat.lite.io.IOConnector.DataReceivedCallback;

/**
 * Manages HttpChannels and associated socket pool.
 *
 *
 * @author Costin Manolache
 */
public class HttpConnector {

    public static interface HttpChannelEvents {
        /** HttpChannel object created. It'll be used many times.
         * @throws IOException
         */
        public void onCreate(HttpChannel ch, HttpConnector con) throws IOException;

        /**
         * HttpChannel object no longer needed, out of pool.
         * @throws IOException
         */
        public void onDestroy(HttpChannel ch, HttpConnector con) throws IOException;
    }

    private static Logger log = Logger.getLogger("HttpConnector");

    /**
     * Cache HttpChannel/request/buffers
     */
    private int maxHttpPoolSize = 50;

    /**
     * Max number of connections to keep alive.
     * Each connection holds a header buffer and the socket.
     * ( we could skip the header buffer )
     */
    private int maxSocketPoolSize = 500; // 10000;

    private int keepAliveTimeMs = 300000;

    private List<HttpChannel> httpChannelPool = new ArrayList<HttpChannel>();

    protected IOConnector ioConnector;

    // for https connections
    protected SslProvider sslProvider;

    boolean debugHttp = false;
    boolean debug = false;

    boolean clientKeepAlive = true;
    boolean serverKeepAlive = true;

    HttpChannelEvents httpEvents;

    public AtomicInteger inUse = new AtomicInteger();
    public AtomicInteger newHttpChannel = new AtomicInteger();
    public AtomicInteger totalHttpChannel = new AtomicInteger();
    public AtomicInteger totalClientHttpChannel = new AtomicInteger();
    public AtomicInteger recycledChannels = new AtomicInteger();
    public AtomicInteger reusedChannels = new AtomicInteger();

    public HttpConnectionPool cpool = new HttpConnectionPool(this);

    // Host + context mapper.
    Dispatcher dispatcher;
    protected HttpService defaultService;
    int port = 8080;

    private Timer timer;

    boolean compression = true;

    boolean serverSSL = false;

    private static Timer defaultTimer = new Timer(true);

    public HttpConnector(IOConnector ioConnector) {
        this.ioConnector = ioConnector;
        dispatcher = new Dispatcher();
        defaultService = dispatcher;
        if (ioConnector != null) {
            timer = ioConnector.getTimer();
        } else {
            // tests
            timer = defaultTimer;
        }
    }

    protected HttpConnector() {
        this(null);
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public HttpConnectionPool getConnectionPool() {
        return cpool;
    }

    public HttpConnector withIOConnector(IOConnector selectors) {
        ioConnector = selectors;
        return this;
    }

    public void setDebug(boolean b) {
        this.debug = b;
    }

    public void setDebugHttp(boolean b) {
        this.debugHttp  = b;
    }

    public HttpConnector withSsl(SslProvider ssl) {
        sslProvider = ssl;
        return this;
    }

    HttpConnector setServerSsl(boolean b) {
        serverSSL = b;
        return this;
    }

    public SslProvider getSslProvider() {
        return sslProvider;
    }

    /**
     * Allow or disable compression for this connector.
     * Compression is enabled by default.
     */
    public HttpConnector setCompression(boolean b) {
        this.compression = b;
        return this;
    }

    public void setClientKeepAlive(boolean b) {
        this.clientKeepAlive = b;
    }

    public void setServerKeepAlive(boolean b) {
        this.serverKeepAlive = b;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isClientKeepAlive() {
        return clientKeepAlive;
    }

    public boolean isServerKeepAlive() {
        return serverKeepAlive;
    }

    public int getInUse() {
        return inUse.get();
    }

    public int getMaxHttpPoolSize() {
        return maxHttpPoolSize;
    }

    public void setMaxHttpPoolSize(int maxHttpPoolSize) {
        this.maxHttpPoolSize = maxHttpPoolSize;
    }

    public void setOnCreate(HttpChannelEvents callback) {
        httpEvents = callback;
    }

    /**
     *  Override to create customized client/server connections.
     *
     * @return
     * @throws IOException
     */
    protected HttpChannel create() throws IOException {
        HttpChannel res = new HttpChannel();
        newHttpChannel.incrementAndGet();
        res.setConnector(this);
        if (httpEvents != null) {
            httpEvents.onCreate(res, this);
        }
        if (debugHttp) {
            res.debug = debugHttp;
        }
        return res;
    }

    public HttpChannel get(String host, int port) throws IOException {
        HttpChannel http = get(false);
        http.setTarget(host + ":" + port);
        return http;
    }

    public HttpChannel getServer() {
        try {
            return get(true);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }

    public HttpRequest request(String host, int port) throws IOException {
        HttpChannel http = get(false);
        http.setTarget(host + ":" + port);
        return http.getRequest();

    }

    public HttpRequest request(CharSequence urlString) throws IOException {
        return get(urlString).getRequest();
    }

    /**
     * Get an existing AsyncHttp object. Since it uses many buffers and
     * objects - it's more efficient to pool it.
     *
     * release will return the object to the pool.
     * @throws IOException
     */
    public HttpChannel get(CharSequence urlString) throws IOException {
        URL url = new URL(urlString.toString());
        String host = url.getHost();
        int port = url.getPort();
        boolean secure = "http".equals(url.getAuthority());
        if (port == -1) {
            port = secure ? 443: 80;
        }
        // TODO: insert SSL filter
        HttpChannel http = get(false);
        http.setTarget(host + ":" + port);
        String path = url.getFile(); // path + qry
        // TODO: query string
        http.getRequest().requestURI().set(path);
        return http;
    }

    protected HttpChannel get(boolean server) throws IOException {
        HttpChannel processor = null;
        synchronized (httpChannelPool) {
            int cnt = httpChannelPool.size();
            if (cnt > 0) {
                processor = httpChannelPool.remove(cnt - 1);
            }
        }
        boolean reuse = false;
        totalHttpChannel.incrementAndGet();
        if (!server) {
            totalClientHttpChannel.incrementAndGet();
        }
        if (processor == null) {
            processor = create();
        } else {
            reuse = true;
            reusedChannels.incrementAndGet();
            processor.release = false;
        }
        processor.serverMode(server);
        if (debug) {
            log.info((reuse ? "REUSE ": "Create ") +
                    (server? " S" : "")
                    + " id=" + processor.ser +
                    " " + processor +
                    " size=" + httpChannelPool.size());
        }

        processor.setConnector(this);
        inUse.incrementAndGet();
        return processor;
    }

    protected void returnToPool(HttpChannel http) throws IOException {
        inUse.decrementAndGet();
        recycledChannels.incrementAndGet();
        int size = 0;
        boolean pool = false;

        http.recycle();
        http.setConnection(null);
        http.setConnector(null);

        // No more data - release the object
        synchronized (httpChannelPool) {
            size = httpChannelPool.size();
            if (httpChannelPool.contains(http)) {
                log.severe("Duplicate element in pool !");
            } else if (size < maxHttpPoolSize) {
                httpChannelPool.add(http);
                pool = true;
            }
        }

        if (!pool && httpEvents != null) {
            httpEvents.onDestroy(http, this);
        }
        if (debug) {
            log.info((pool ? "Return " : "Destroy ")
                    + http.getTarget() + " obj=" +
                    http + " size=" + size);
        }
    }


    public IOConnector getIOConnector() {
        return ioConnector;
    }


    public void setHttpService(HttpService s) {
        defaultService = s;
    }

    public void start() throws IOException {
        if (ioConnector != null) {
            ioConnector.acceptor(new AcceptorCallback(),
                    Integer.toString(port), null);
        }
    }

    /**
     *
     * TODO: only clean our state and sockets we listen on.
     *
     */
    public void stop() {
        if (ioConnector != null) {
            ioConnector.stop();
        }
    }

    protected void connectAndSend(HttpChannel httpCh) throws IOException {
        cpool.send(httpCh);

    }

    private class AcceptorCallback implements IOConnector.ConnectedCallback {
        @Override
        public void handleConnected(IOChannel accepted) throws IOException {
            handleAccepted(accepted);
        }
    }

    public HttpConnection handleAccepted(IOChannel accepted) throws IOException {
        // TODO: reuse
        HttpConnection shttp = cpool.accepted(accepted);
        shttp.serverMode = true;

        IOChannel head = accepted;
        IOChannel ch;

        String id = null;
        if (debugHttp) {
            id = port + "-" + accepted.getFirst().getAttribute(IOChannel.ATT_REMOTE_PORT);
            log.info("Accepted " + id);
            head = DumpChannel.wrap("SSL-" + id, head);
        }

        // TODO: seems cleaner this way...
        if (serverSSL) {
            ch = sslProvider.serverChannel(head);
            head.setHead(ch);
            head = ch;

            if (debugHttp) {
                head = DumpChannel.wrap("CLEAR-" + id, head);
            }
        }

        shttp.setSink(head);

        // Will read any data in the channel, notify data available up
        accepted.handleReceived(accepted);
        return shttp;
    }

    public HttpConnector setPort(int port2) {
        this.port = port2;
        return this;
    }

    /**
     * Actual HTTP/1.1 wire protocol.
     *
     */
    public static abstract class HttpConnection extends IOChannel
        implements DataReceivedCallback
    {
        protected HttpConnector httpConnector;
        protected boolean serverMode = false;

        protected BBuffer headRecvBuf = BBuffer.allocate(8192);
        protected CompressFilter compress = new CompressFilter();

        protected boolean secure = false;

        protected HttpConnectionPool.RemoteServer remoteHost;
        // If set, the connection is in use ( active )
        // null == keep alive. Changes synchronized on remoteHost
        // before/after request
        protected HttpChannel activeHttp;

        @Override
        public final void handleReceived(IOChannel ch) throws IOException {
            int before = ch.getIn().available();
            dataReceived(ch.getIn());
        }

        protected HttpChannel checkHttpChannel() throws IOException {
            return null;
        }

        /**
         * Called before a new request is sent, on a channel that is
         * reused.
         */
        public void beforeRequest() {
        }

        public void setSink(IOChannel ch) throws IOException {
            this.net = ch;
            ch.setDataReceivedCallback(this);
            ch.setDataFlushedCallback(this);
            // we may have data in the buffer;
            handleReceived(ch);
        }


        /**
         * Incoming data.
         */
        public abstract void dataReceived(IOBuffer iob) throws IOException;

        /**
         * Framing error, client interrupt, etc.
         */
        public void abort(HttpChannel http, String t) throws IOException {
        }

        protected void sendRequest(HttpChannel http)
            throws IOException {
        }

        protected void sendResponseHeaders(HttpChannel http)
            throws IOException {
        }

        public void startSending(HttpChannel http) throws IOException {
        }

        @Override
        public IOBuffer getIn() {
            return net == null ? null : net.getIn();
        }

        @Override
        public IOBuffer getOut() {
            return net == null ? null : net.getOut();
        }

        @Override
        public void startSending() throws IOException {
        }

        /** Called when the outgoing stream is closed:
         * - by an explicit call to close()
         * - when all content has been sent.
         */
        protected void outClosed(HttpChannel http) throws IOException {
        }

        /**
         * Called by HttpChannel when both input and output are fully
         * sent/received. When this happens the request is no longer associated
         * with the Connection, and the connection can be re-used.
         *
         * The channel can still be used to access the retrieved data that may
         * still be buffered until HttpChannel.release() is called.
         *
         * This method will be called only once, for both succesful and aborted
         * requests.
         */
        protected abstract void endSendReceive(HttpChannel httpChannel) throws IOException;

        public void withExtraBuffer(BBuffer received) {
            return;
        }

    }

}
