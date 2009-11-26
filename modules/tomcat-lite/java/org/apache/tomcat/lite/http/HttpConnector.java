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
import org.apache.tomcat.lite.io.DumpChannel;
import org.apache.tomcat.lite.io.BBucket;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;

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
    private int maxHttpPoolSize = 20;
    
    private int maxSocketPoolSize = 100;
    private int keepAliveTimeMs = 30000;
    
    private Queue<HttpChannel> httpChannelPool = new ConcurrentLinkedQueue<HttpChannel>();

    private IOConnector ioConnector;
    
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
        HttpChannel http = get(false, host, port);
        http.setTarget(host, port);
        return http;
    }
    
    public HttpChannel getServer() {
        try {
            return get(true, null, 0);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
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
        HttpChannel http = get(false, host, port);
        http.setTarget(host, port);
        String path = url.getFile(); // path + qry
        // TODO: query string
        http.getRequest().requestURI().set(path);
        return http;
    }
    
    protected HttpChannel get(boolean server, CharSequence host, int port) throws IOException {
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
            log.info((reuse ? "REUSE ": "Create ")
                    + host + ":" + port + 
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
        if (serverMode) {
            BBucket first = ch.getIn().peekFirst();
            if (first != null) {
                HttpChannel http = getServer();
                if (debug) {
                    http.trace("PIPELINED request " + first + " " + http.httpService); 
                }
                http.setChannel(ch);
                http.setHttpService(defaultService);
                
                // In case it was disabled
                if (ch != null) {
                    if (ch.isOpen()) {
                        ch.readInterest(true);
                    }
                    // Notify that data was received. The callback must deal with
                    // pre-existing data.
                    ch.sendHandleReceivedCallback();
                }
                http.handleReceived(http.getSink());
                return;
            }
        }
        if (serverMode && !serverKeepAlive) {
            keepOpen = false;
        }
        if (!serverMode && !clientKeepAlive) {
            keepOpen = false;
        }
        

        if (keepOpen) {
            // reuse the socket
            if (serverMode) {
                if (debug) {
                    log.info(">>> server socket KEEP_ALIVE " + ch.getTarget() + 
                            " " + ch);
                }
                ch.readInterest(true);
                ch.setDataReceivedCallback(receiveCallback);
                ch.setDataFlushedCallback(null);
                
                cpool.returnChannel(ch);
                // TODO: timeout event to close it
                //                ch.setTimer(10000, new Runnable() {
                //                    @Override
                //                    public void run() {
                //                        System.err.println("Keep alive timeout");
                //                    }
                //                });
            } else {
                if (debug) {
                    log.info(">>> client socket KEEP_ALIVE " + ch.getTarget() + 
                            " " + ch);
                }
                ch.readInterest(true);
                ch.setDataReceivedCallback(clientReceiveCallback);
                ch.setDataFlushedCallback(null);
                
                cpool.returnChannel(ch);
            }
        } else { 
            if (debug) {
                log.info("--- Close socket, no keepalive " + ch);
            }
            if (ch != null) {
                ch.close();
            }
        }
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
            http.resetBuffers();
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
    
    // Host + context mapper.
    Dispatcher dispatcher;
    HttpService defaultService;
    int port = 8080;
    
    
    public void setHttpService(HttpService s) {
        defaultService = s;
    }
    
    public void start() throws IOException {
        if (ioConnector != null) {
            ioConnector.acceptor(new AcceptorCallback(this, defaultService), 
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
    
    private static class AcceptorCallback implements IOConnector.ConnectedCallback {
        HttpConnector httpCon;
        HttpService callback;
        
        public AcceptorCallback(HttpConnector asyncHttpConnector,
                HttpService headersReceived) {
            this.httpCon = asyncHttpConnector;
            this.callback = headersReceived;
        }

        @Override
        public void handleConnected(IOChannel accepted) throws IOException {
            HttpChannel shttp = httpCon.getServer();
            if (callback != null) {
                shttp.setHttpService(callback);
            }
            if (httpCon.debugHttp) {
                IOChannel ch = new DumpChannel("");
                accepted.addFilterAfter(ch);
                shttp.setChannel(ch);
            } else {
                shttp.setChannel(accepted);
            }
            // TODO: JSSE filter
            

            // Will read any data in the channel.
            
            accepted.handleReceived(accepted);
        }

    }


    private IOConnector.DataReceivedCallback receiveCallback = 
        new IOConnector.DataReceivedCallback() {
        /** For keepalive - for server
         * 
         * @param peer
         * @throws IOException
         */
        @Override
        public void handleReceived(IOChannel net) throws IOException {
            cpool.stopKeepAlive(net);
            if (!net.isOpen()) {
                return;
            }
            HttpChannel shttp = getServer();
            shttp.setChannel(net);
            shttp.setHttpService(defaultService);
            net.sendHandleReceivedCallback();
        }
    };


    // Sate-less, just closes the net.
    private IOConnector.DataReceivedCallback clientReceiveCallback = 
        new IOConnector.DataReceivedCallback() {
        
        @Override
        public void handleReceived(IOChannel net) throws IOException {
            if (!net.isOpen()) {
                cpool.stopKeepAlive(net);
                return;
            }
            log.warning("Unexpected message from server in client keep alive " 
                    + net.getIn());
            if (net.isOpen()) {
                net.close();
            }
        }
        
    };

    public HttpConnector setPort(int port2) {
        this.port = port2;
        return this;
    }
    
    /** 
     * Connections for one remote host.
     * This should't be restricted by IP:port or even hostname,
     * for example if a server has multiple IPs or LB replicas - any would work.   
     */
    public static class RemoteServer {
        public ConnectionPool pool;
        public ArrayList<IOChannel> connections = new ArrayList<IOChannel>();
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
        public IOChannel getChannel(CharSequence key) throws IOException {
            RemoteServer t = null;
            synchronized (hosts) {
                t = hosts.get(key);
                if (t == null) {
                    misses.incrementAndGet();
                    return null;
                }
            }
            IOChannel res = null;
            synchronized (t) {
                if (t.connections.size() == 0) {
                    misses.incrementAndGet();
                    hosts.remove(key); 
                    return null;
                } // one may be added - no harm.
                res = t.connections.remove(t.connections.size() - 1);

                if (t.connections.size() == 0) {
                    hosts.remove(key); 
                } 
                if (res == null) {
                    log.fine("Null connection ?");
                    misses.incrementAndGet();
                    return null;
                }
                
                if (!res.isOpen()) {
                    res.setDataReceivedCallback(null);
                    res.close();
                    log.fine("Already closed " + res);
                    //res.keepAliveServer = null;
                    res = null;
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
        public void returnChannel(IOChannel ch) 
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
                // sdata.keepAliveServer = t;
                t.connections.add(ch);      
                //sdata.ch.setDataCallbacks(readable, null, cc);
                ch.readInterest(true);
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
}
