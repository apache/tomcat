/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.DumpChannel;
import org.apache.tomcat.lite.io.IOBuffer;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;
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
    
    HttpConnectionManager conManager = new HttpConnectionManager();
    
    private static Logger log = Logger.getLogger("HttpConnector");
    private int maxHttpPoolSize = 20;
    
    private int maxSocketPoolSize = 100;
    private int keepAliveTimeMs = 30000;
    
    private Queue<HttpChannel> httpChannelPool = new ConcurrentLinkedQueue<HttpChannel>();

    protected IOConnector ioConnector;
    
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

    public ConnectionPool cpool = new ConnectionPool();
        
    // Host + context mapper.
    Dispatcher dispatcher;
    protected HttpService defaultService;
    int port = 8080;
    
    
    public HttpConnector(IOConnector ioConnector) {
        this.ioConnector = ioConnector;
        dispatcher = new Dispatcher();
        defaultService = dispatcher;
    }

    protected HttpConnector() {
        this(null);
    }
    
    public Dispatcher getDispatcher() {
        return dispatcher;
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
            processor = httpChannelPool.poll();
        }
        boolean reuse = false;
        totalHttpChannel.incrementAndGet();
        if (processor == null) {
            processor = create();
        } else {
            reuse = true;
            reusedChannels.incrementAndGet();
            processor.release = false;
        }
        if (!server) {
            totalClientHttpChannel.incrementAndGet();
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


    /**
     * Called by HttpChannel when the HTTP request is done, i.e. all 
     * sending/receiving is complete. The service may still use the 
     * HttpChannel object.
     * 
     * If keepOpen: clients will wait in the pool, detecting server close.
     * For server: will wait for new requests.
     * 
     * TODO: timeouts, better pool management
     */
    protected void returnSocket(IOChannel ch, boolean serverMode, 
                                boolean keepOpen) 
            throws IOException {
        // Now handle net - note that we could have reused the async object
        

    }
    
    protected void returnToPool(HttpChannel http) throws IOException {
        inUse.decrementAndGet();
        recycledChannels.incrementAndGet();
        if (debug) {
            log.info("Return " + http.getTarget() + " obj=" +
                http + " size=" + httpChannelPool.size());
        }
        
        http.recycle();
        
        // No more data - release the object
        synchronized (httpChannelPool) {
            http.setConnection(null);
            http.setConnector(null);
            if (httpChannelPool.contains(http)) {
                System.err.println("dup ? ");                
            }
            if (httpChannelPool.size() >= maxHttpPoolSize) {
                if (httpEvents != null) {
                    httpEvents.onDestroy(http, this);
                }
            } else {
                httpChannelPool.add(http);
            }
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
        String target = httpCh.getTarget();
        // TODO: SSL 
        HttpConnection ch = cpool.getChannel(target);

        if (ch == null) {
            if (debug) {
                httpCh.trace("HTTP_CONNECT: New connection " + target);
            }
            IOConnector.ConnectedCallback connected =
                new HttpConnectedCallback(this, httpCh);
            
            // will call sendRequestHeaders
            String[] hostPort = target.split(":");
            int targetPort = hostPort.length > 1 ? 
                    Integer.parseInt(hostPort[1]) : 80;
            getIOConnector().connect(hostPort[0], targetPort,
                    connected);
        } else {
            if (debug) {
                httpCh.trace("HTTP_CONNECT: Reuse connection " + target + " " + this);
            }
            // TODO retry if closed
            ch.beforeRequest();
            httpCh.setConnection(ch);
            ch.sendRequest(httpCh);
        }
    }
    
    static class HttpConnectedCallback implements IOConnector.ConnectedCallback {
        HttpConnector httpCon;
        HttpChannel httpCh;
        
        public HttpConnectedCallback(HttpConnector httpConnector,
                HttpChannel httpCh2) {
            this.httpCh = httpCh2;
            this.httpCon = httpConnector;
        }

        @Override
        public void handleConnected(IOChannel ch) throws IOException {
            if (httpCon.debugHttp) {
                IOChannel ch1 = new DumpChannel("");
                ch.addFilterAfter(ch1);
                ch = ch1;                        
            }
            httpCon.handleConnected(ch, httpCh);
        }
    }

    HttpConnection newConnection() {
        return conManager.newConnection(this);
    }


    private class AcceptorCallback implements IOConnector.ConnectedCallback {
        @Override
        public void handleConnected(IOChannel accepted) throws IOException {
            handleAccepted(accepted);
        }
    }

    public HttpConnection handleAccepted(IOChannel accepted) throws IOException {
        // TODO: reuse
        HttpConnection shttp = newConnection();
        shttp.serverMode = true;

        if (debugHttp) {
            log.info("Accepted " + accepted.getFirst().getPort(true));
            IOChannel ch = new DumpChannel("");
            accepted.addFilterAfter(ch);
            shttp.setSink(ch);
        } else {
            shttp.setSink(accepted);
        }
        // TODO: JSSE filter
        

        // Will read any data in the channel.
        
        accepted.handleReceived(accepted);
        return shttp;
    }

    public HttpConnector setPort(int port2) {
        this.port = port2;
        return this;
    }
    
    public void handleConnected(IOChannel net, HttpChannel httpCh) 
            throws IOException {
        if (!net.isOpen()) {
            httpCh.abort("Can't connect");
            return;
        }
        HttpConnection httpStream = newConnection();
        httpStream.setSink(net);

        // TODO: add it to the cpool
        httpCh.setConnection(httpStream);
        
        httpStream.sendRequest(httpCh);
    }

    public static class HttpConnectionManager {
        public HttpConnection newConnection(HttpConnector con) {
            return new Http11Connection(con);
        }
        
        public HttpConnection getFromPool(RemoteServer t) {
            return t.connections.remove(t.connections.size() - 1);            
        }
    }
    
    /**
     * Actual HTTP/1.1 wire protocol. 
     *  
     */
    public static class HttpConnection extends IOChannel
        implements DataReceivedCallback
    {
        protected HttpConnector httpConnector;
        protected boolean serverMode;

        protected BBuffer headRecvBuf = BBuffer.allocate(8192);
        

        @Override
        public void handleReceived(IOChannel ch) throws IOException {
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
        public void dataReceived(IOBuffer iob) throws IOException {
            
        }
        
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
            return net.getIn();
        }

        @Override
        public IOBuffer getOut() {
            return net.getOut();
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

        protected void endSendReceive(HttpChannel httpChannel) throws IOException {
            return;
        }

        public void withExtraBuffer(BBuffer received) {
            return;
        }
        
    }

    /** 
     * Connections for one remote host.
     * This should't be restricted by IP:port or even hostname,
     * for example if a server has multiple IPs or LB replicas - any would work.   
     */
    public static class RemoteServer {
        public ConnectionPool pool;
        public ArrayList<HttpConnection> connections = new ArrayList<HttpConnection>();
    }

    // TODO: add timeouts, limits per host/total, expire old entries 
    // TODO: discover apr and use it
    
    public class ConnectionPool {
        // visible for debugging - will be made private, with accessor 
        /**
         * Map from client names to socket pools.
         */
        public Map<CharSequence, RemoteServer> hosts = new HashMap<CharSequence, 
            RemoteServer>();
        boolean keepOpen = true;

        // Statistics
        public AtomicInteger waitingSockets = new AtomicInteger();
        public AtomicInteger closedSockets = new AtomicInteger();

        public AtomicInteger hits = new AtomicInteger();
        public AtomicInteger misses = new AtomicInteger();

        Timer timer;
        
        public int getTargetCount() {
            return hosts.size();
        }

        public int getSocketCount() {
            return waitingSockets.get();
        }

        public int getClosedSockets() {
            return closedSockets.get();
        }

        public String dumpSockets() {
            StringBuffer sb = new StringBuffer();
            for (CharSequence k: hosts.keySet()) {
                RemoteServer t = hosts.get(k);
                sb.append(k).append("=").append(t.connections.size()).append("\n");
            }
            return sb.toString();
        }

        public Set<CharSequence> getKeepAliveTargets() {
            return hosts.keySet();
        }

        /** 
         * @param key host:port, or some other key if multiple hosts:ips
         * are connected to equivalent servers ( LB ) 
         * @throws IOException 
         */
        public HttpConnection getChannel(CharSequence key) throws IOException {
            RemoteServer t = null;
            synchronized (hosts) {
                t = hosts.get(key);
                if (t == null) {
                    misses.incrementAndGet();
                    return null;
                }
            }
            HttpConnection res = null;
            synchronized (t) {
                if (t.connections.size() == 0) {
                    misses.incrementAndGet();
                    return null;
                } // one may be added - no harm.
                
                res = conManager.getFromPool(t);
                
                if (!res.isOpen()) {
                    res.setDataReceivedCallback(null);
                    res.close();
                    log.fine("Already closed " + res);
                    res = null;
                    misses.incrementAndGet();
                    return null;
                }
                waitingSockets.decrementAndGet();
            }
            hits.incrementAndGet();
            if (debug) {
                log.info("REUSE channel ..." + key + " " + res);
            }
            return res;      
        }

        /**
         * Must be called in IOThread for the channel
         */
        public void returnChannel(HttpConnection ch) 
                throws IOException {
            CharSequence key = ch.getTarget(); 
            if (key == null) {
                ch.close();
                if (debug) {
                    log.info("Return channel, no target ..." + key + " " + ch);
                }
                return;
            }
            
            if (!keepOpen) {
                ch.close();
                return;
            }
            
//            SocketIOChannel sdata = (SocketIOChannel) ch;
            if (!ch.isOpen()) {
                ch.close(); // make sure all closed
                if (debug) {
                    log.info("Return closed channel ..." + key + " " + ch);
                }
                return;
            }
            
            RemoteServer t = null;
            synchronized (hosts) {
                t = hosts.get(key);
                if (t == null) {
                    t = new RemoteServer();
                    t.pool = this;
                    hosts.put(key, t);
                }
            }
            waitingSockets.incrementAndGet();
            
            ch.ts = System.currentTimeMillis();
            synchronized (t) {
                t.connections.add(ch);      
            }
        }
        
        // Called by handleClosed
        void stopKeepAlive(IOChannel schannel) {
            CharSequence target = schannel.getTarget();
            RemoteServer t = null;
            synchronized (hosts) {
                t = hosts.get(target);
                if (t == null) {
                    return;
                }
            }
            synchronized (t) {
                if (t.connections.remove(schannel)) {      
                    waitingSockets.decrementAndGet();
                    if (t.connections.size() == 0) {
                        hosts.remove(target);
                    }
                }
            }
        }
    }

    public HttpConnector withConnectionManager(
            HttpConnectionManager connectionManager) {
        this.conManager = connectionManager;
        return this;
    }
}
