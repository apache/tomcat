/*
 */
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpConnector.HttpConnection;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;

/**
 * - Holds references to all active and kept-alive connections.
 * - makes decisions on accepting more connections, closing old
 * connections, etc
 *
 */
public class HttpConnectionPool {
    // TODO: add timeouts, limits per host/total, expire old entries

    public static interface HttpConnectionPoolEvents {
        public void newTarget(RemoteServer host);

        public void targetRemoved(RemoteServer host);

        public void newConnection(RemoteServer host, HttpConnection con);
        public void closedConnection(RemoteServer host, HttpConnection con);
    }

    /**
     * Connections for one remote host.
     * This should't be restricted by IP:port or even hostname,
     * for example if a server has multiple IPs or LB replicas - any would work.
     */
    public static class RemoteServer {
        // all access sync on RemoteServer
        private SpdyConnection spdy;

        // all access sync on RemoteServer
        private ArrayList<Http11Connection> connections
            = new ArrayList<Http11Connection>();

        Queue<HttpChannel> pending = new LinkedList<HttpChannel>();


        // TODO: setter, default from connector
        private int maxConnections = 20;

        AtomicInteger activeRequests = new AtomicInteger();
        AtomicInteger totalRequests = new AtomicInteger();
        private volatile long lastActivity;

        public String target;

        public synchronized List<HttpConnector.HttpConnection> getConnections()
        {
            return new ArrayList<HttpConnection>(connections);
        }

        public synchronized Collection<HttpChannel> getActives() {
            ArrayList<HttpChannel> actives = new ArrayList();
            for (Http11Connection con: connections) {
                if (con.activeHttp != null) {
                    actives.add(con.activeHttp);
                }
            }
            if (spdy != null) {
                actives.addAll(spdy.getActives());
            }

            return actives;
        }

        public synchronized void touch() {
            lastActivity = System.currentTimeMillis();
        }
    }

    private HttpConnectionPoolEvents poolEvents;

    private static Logger log = Logger.getLogger("HttpConnector");

    // visible for debugging - will be made private, with accessor
    /**
     * Map from client names to socket pools.
     */
    public Map<CharSequence, HttpConnectionPool.RemoteServer> hosts = new HashMap<CharSequence,
        HttpConnectionPool.RemoteServer>();

    // Statistics
    public AtomicInteger waitingSockets = new AtomicInteger();
    public AtomicInteger closedSockets = new AtomicInteger();

    public AtomicInteger hits = new AtomicInteger();
    public AtomicInteger misses = new AtomicInteger();
    public AtomicInteger queued = new AtomicInteger();

    public AtomicInteger activeRequests = new AtomicInteger();

    private static boolean debug = false;
    HttpConnector httpConnector;

    public HttpConnectionPool(HttpConnector httpConnector) {
        this.httpConnector = httpConnector;
    }

    public int getTargetCount() {
        return hosts.size();
    }

    public int getSocketCount() {
        return waitingSockets.get();
    }

    public int getClosedSockets() {
        return closedSockets.get();
    }

    public Set<CharSequence> getKeepAliveTargets() {
        return hosts.keySet();
    }

    public List<RemoteServer> getServers() {
        return new ArrayList<RemoteServer>(hosts.values());
    }

    public void setEvents(HttpConnectionPoolEvents events) {
        this.poolEvents = events;
    }
    /**
     * Stop all cached connections.
     */
    public void clear() throws IOException {
        synchronized (hosts) {
            int active = 0;
            for (RemoteServer rs: hosts.values()) {
                synchronized (rs) {
                    int hostActive = 0;
                    if (rs.spdy != null) {
                        if (rs.spdy.channels.size() == 0) {
                            rs.spdy.close();
                            rs.spdy = null;
                        } else {
                            hostActive += rs.spdy.channels.size();
                        }
                    }
                    for (Http11Connection con: rs.connections) {
                        if (con.activeHttp == null) {
                            con.close();
                        } else {
                            hostActive++;
                        }
                    }
                    if (hostActive != rs.activeRequests.get()) {
                        log.warning("Active missmatch " + rs.target + " " +
                                hostActive + " "
                                + rs.activeRequests.get());
                        rs.activeRequests.set(hostActive);
                    }
                    active += hostActive;
                }
            }
            if (active != this.activeRequests.get()) {
                log.warning("Active missmatch " + active + " "
                        + activeRequests.get());
                activeRequests.set(active);
            }
        }
    }

    /**
     * Stop all active and cached connections
     * @throws IOException
     */
    public void abort() throws IOException {
        // TODO
        clear();
        hosts.clear();
    }

    /**
     * @param key host:port, or some other key if multiple hosts:ips
     * are connected to equivalent servers ( LB )
     * @param httpCh
     * @throws IOException
     */
    public void send(HttpChannel httpCh)
            throws IOException {
        String target = httpCh.getTarget();
        HttpConnection con = null;
        // TODO: check ssl on connection - now if a second request
        // is received on a ssl connection - we just send it
        boolean ssl = httpCh.getRequest().isSecure();

        HttpConnectionPool.RemoteServer remoteServer = null;
        synchronized (hosts) {
            remoteServer = hosts.get(target);
            if (remoteServer == null) {
                remoteServer = new HttpConnectionPool.RemoteServer();
                remoteServer.target = target;
                hosts.put(target, remoteServer);
            }
        }

        // TODO: remove old servers and connections

        // Temp magic - until a better negotiation is defined
        boolean forceSpdy = "SPDY/1.0".equals(httpCh.getRequest().getProtocol());
        if (forceSpdy) {
            // switch back the protocol
            httpCh.getRequest().setProtocol("HTTP/1.1");
        }

        activeRequests.incrementAndGet();
        remoteServer.activeRequests.incrementAndGet();

        // if we already have a spdy connection or explicitely
        // requested.
        if (forceSpdy || remoteServer.spdy != null) {
            synchronized (remoteServer) {
                if (remoteServer.spdy == null) {
                    remoteServer.spdy = new SpdyConnection(httpConnector,
                            remoteServer);
                }
                con = remoteServer.spdy;
            }

            // Will be queued - multiple threads may try to send
            // at the same time, and we need to queue anyways.
            con.sendRequest(httpCh);
        } else {
            synchronized (remoteServer) {
                Http11Connection hcon;
                for (int i = 0; i < remoteServer.connections.size(); i++) {
                    hcon = (Http11Connection) remoteServer.connections.get(i);
                    if (hcon != null && hcon.activeHttp == null) {
                        hcon.beforeRequest(); // recycle

                        hcon.activeHttp = httpCh;
                        con = hcon;
                        break;
                    }
                }
                if (con == null) {
//                    if (remoteServer.connections.size() > remoteServer.maxConnections) {
//                        remoteServer.pending.add(httpCh);
//                        queued.incrementAndGet();
//                        if (debug) {
//                            log.info("Queue: " + target + " " + remoteServer.connections.size());
//                        }
//                        return;
//                    }
                    hcon = new Http11Connection(httpConnector);
                    hcon.setTarget(target);
                    hcon.activeHttp = httpCh;
                    hcon.remoteHost = remoteServer;
                    remoteServer.connections.add(hcon);
                    con = hcon;
                }
            }


            // we got a connection - make sure we're connected
            http11ConnectOrSend(httpCh, target, con, ssl);
        }
    }

    private void http11ConnectOrSend(HttpChannel httpCh, String target,
            HttpConnection con, boolean ssl) throws IOException {
        httpCh.setConnection(con);

        if (con.isOpen()) {
            hits.incrementAndGet();
//            if (debug) {
//                log.info("HTTP_CONNECT: Reuse connection " + target + " " + this);
//            }
            con.sendRequest(httpCh);
        } else {
            misses.incrementAndGet();
            if (debug) {
                log.info("HTTP_CONNECT: Start connection " + target + " " + this);
            }
            httpConnect(httpCh, target, ssl,
                    (Http11Connection) con);
        }
    }

    void httpConnect(HttpChannel httpCh, String target,
            boolean ssl, IOConnector.ConnectedCallback cb)
            throws IOException {
        if (debug) {
            log.info("HTTP_CONNECT: New connection " + target);
        }
        String[] hostPort = target.split(":");

        int targetPort = ssl ? 443 : 80;
        if (hostPort.length > 1) {
            targetPort = Integer.parseInt(hostPort[1]);
        }

        httpConnector.getIOConnector().connect(hostPort[0], targetPort,
                cb);
    }

    public void afterRequest(HttpChannel http, HttpConnection con,
            boolean keepAlive)
                throws IOException {
        activeRequests.decrementAndGet();
        if (con.remoteHost != null) {
            con.remoteHost.touch();
            con.remoteHost.activeRequests.decrementAndGet();
        }
        if (con.serverMode) {
            afterServerRequest(con, keepAlive);
        } else {
            afterClientRequest(con);
        }
    }

    private void afterClientRequest(HttpConnection con)
            throws IOException {
        RemoteServer remoteServer = con.remoteHost;
        HttpChannel req = null;

        // If we have pending requests ( because too many active limit ), pick
        // one and send it.
        synchronized (remoteServer) {
            // If closed - we can remove the object - or
            // let a background thread do it, in case it's needed
            // again.
            if (remoteServer.pending.size() == 0) {
                con.activeHttp = null;
                return;
            }
            req = remoteServer.pending.remove();
            con.activeHttp = req;
            if (debug) {
                log.info("After request: send pending " + remoteServer.pending.size());
            }
        }

        http11ConnectOrSend(req, con.getTarget().toString(),
                con, req.getRequest().isSecure());
    }

    RemoteServer serverPool = new RemoteServer();

    public void afterServerRequest(HttpConnection con, boolean keepAlive)
            throws IOException {
        con.activeHttp = null;
        if (!keepAlive) {
            synchronized (serverPool) {
                // I could also reuse the object.
                serverPool.connections.remove(con);
            }
        }
    }

    public HttpConnection accepted(IOChannel accepted) {
        Http11Connection con = new Http11Connection(httpConnector);
        con.remoteHost = serverPool;
        synchronized (serverPool) {
            serverPool.connections.add(con);
        }
        return con;
    }


    // Called by handleClosed
    void stopKeepAlive(IOChannel schannel) {
        CharSequence target = schannel.getTarget();
        HttpConnectionPool.RemoteServer remoteServer = null;
        synchronized (hosts) {
            remoteServer = hosts.get(target);
            if (remoteServer == null) {
                return;
            }
        }
        synchronized (remoteServer) {
            if (remoteServer.connections.remove(schannel)) {
                waitingSockets.decrementAndGet();
                if (remoteServer.connections.size() == 0) {
                    hosts.remove(target);
                }
            }
        }
    }


}
