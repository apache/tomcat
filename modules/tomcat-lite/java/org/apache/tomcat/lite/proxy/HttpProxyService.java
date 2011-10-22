/*
 */
package org.apache.tomcat.lite.proxy;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.MultiMap;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.http.HttpChannel.RequestCompleted;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.IOChannel;
import org.apache.tomcat.lite.io.IOConnector;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.SocketConnector;

/**
 * Http callback for the server-side. Will forward all requests to
 * a remote http server - either using proxy mode ( GET http://... ) or
 * forward requests ( GET /foo -> will be served by the remote server ).
 *
 * This is not blocking (except the connect, which currenly blocks on dns).
 */
public class HttpProxyService implements HttpService {

    // target - when used in forwarding mode.
    String target = "localhost";
    int port = 8802;

    static Logger log = Logger.getLogger("HttpProxy");
    public static boolean debug = false;
    boolean keepOpen = true;

    // client side - this connect to the real server that generates the resp.
    ProxyClientCallback clientHeadersReceived = new ProxyClientCallback();

    HttpConnector httpConnector;
    IOConnector ioConnector;

    public HttpProxyService withSelector(IOConnector pool) {
        this.ioConnector = pool;
        return this;
    }

    public HttpProxyService withHttpClient(HttpConnector pool) {
        this.httpConnector = pool;
        return this;
    }

    public HttpProxyService withTarget(String host, int port) {
        this.target = host;
        this.port = port;
        return this;
    }

    private IOConnector getSelector() {
        if (ioConnector == null) {
            ioConnector = new SocketConnector();
        }
        return ioConnector;
    }

    private HttpConnector getHttpConnector() {
        if (httpConnector == null) {
            httpConnector = new HttpConnector(getSelector());
        }
        return httpConnector;
    }

    // Connects to the target CONNECT server, as client, forwards
    static class ProxyConnectClientConnection implements IOConnector.ConnectedCallback {

        IOChannel serverNet;
        private HttpChannel serverHttp;

        public ProxyConnectClientConnection(HttpChannel sproc) throws IOException {
            this.serverNet = sproc.getSink();
            this.serverHttp = sproc;
        }

        @Override
        public void handleConnected(IOChannel ioch) throws IOException {
            if (!ioch.isOpen()) {
                serverNet.close();
                log.severe("Connection failed");
                return;
            }
            afterClientConnect(ioch);

            ioch.setDataReceivedCallback(new CopyCallback(serverNet));
            //ioch.setDataFlushedCallback(new ProxyFlushedCallback(serverNet, ioch));
            serverNet.setDataReceivedCallback(new CopyCallback(ioch));
            //serverNet.setDataFlushedCallback(new ProxyFlushedCallback(ioch, serverNet));

            ioch.sendHandleReceivedCallback();
        }

        static byte[] OK = "HTTP/1.1 200 OK\r\n\r\n".getBytes();

        protected void afterClientConnect(IOChannel clientCh) throws IOException {
            serverNet.getOut().queue(OK);
            serverNet.startSending();

            serverHttp.release(); // no longer used
        }
    }

    /**
     * Parse the req, dispatch the connection.
     */
    @Override
    public void service(HttpRequest serverHttpReq, HttpResponse serverHttpRes)
            throws IOException {

        String dstHost = target; // default target ( for normal req ).
        int dstPort = port;

        // TODO: more flexibility/callbacks on selecting the target, acl, etc
        if (serverHttpReq.method().equals("CONNECT")) {
            // SSL proxy - just connect and forward all packets
            // TODO: optimize, add error checking
            String[] hostPort = serverHttpReq.requestURI().toString().split(":");
            String host = hostPort[0];
            int port = 443;
            if (hostPort.length > 1) {
                port = Integer.parseInt(hostPort[1]);
            }
            if (log.isLoggable(Level.FINE)) {
                HttpChannel server = serverHttpReq.getHttpChannel();
                log.info("NEW: " + server.getId() + " " + dstHost + " "  +
                        server.getRequest().getMethod() +
                        " " + server.getRequest().getRequestURI() + " " +
                        server.getIn());
            }

            try {
                getSelector().connect(host, port,
                        new ProxyConnectClientConnection(serverHttpReq.getHttpChannel()));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return;
        }


        CBuffer origURIx = serverHttpReq.requestURI();
//        String origURI = origURIx.toString();
//        if (origURI.startsWith("http://")) {
//            // Real proxy - extract client address, modify the uri.
//            // TODO: optimize the strings.
//            int start = origURI.indexOf('/', 7);
//            String hostPortS = (start == -1) ?
//                    origURI.subSequence(7, origURI.length()).toString() :
//                    origURI.subSequence(7, start).toString();
//            String[] hostPort = hostPortS.split(":");
//
//            dstHost = hostPort[0];
//            dstPort = (hostPort.length > 1) ? Integer.parseInt(hostPort[1]) :
//                80;
//
//            if (start >= 0) {
//                serverHttpReq.requestURI().set(origURI.substring(start));
//            } else {
//                serverHttpReq.requestURI().set("/");
//            }
//        } else {
            // Adjust the host header.
            CBuffer hostHdr =
                serverHttpReq.getMimeHeaders().getHeader("host");
            if (hostHdr != null) {
                hostHdr.recycle();
                CBuffer cb = hostHdr;
                cb.append(dstHost);
                if (dstPort != 80) {
                    cb.append(':');
                    cb.appendInt(dstPort);
                }
            }
//        }
        if (debug) {
            HttpChannel server = serverHttpReq.getHttpChannel();
            log.info("START: " + server.getId() + " " + dstHost + " "  +
                    server.getRequest().getMethod() +
                    " " + server.getRequest().getRequestURI() + " " +
                    server.getIn());
        }

        // Send the request with a non-blocking write
        HttpChannel serverHttp = serverHttpReq.getHttpChannel();

        // Client connection
        HttpChannel httpClient = getHttpConnector().get(dstHost, dstPort);

        serverHttp.getRequest().setAttribute("CLIENT", httpClient);
        httpClient.getRequest().setAttribute("SERVER", serverHttp);
        serverHttp.getRequest().setAttribute("P", httpClient);
        httpClient.getRequest().setAttribute("P", serverHttp);

        httpClient.setHttpService(clientHeadersReceived);

        // Will send the original request (TODO: small changes)
        // Response is not affected ( we use the callback )
        httpClient.getRequest().method().set(serverHttp.getRequest().method());
        httpClient.getRequest().requestURI().set(serverHttp.getRequest().requestURI());
        if (serverHttp.getRequest().queryString().length() != 0) {
            httpClient.getRequest().queryString().set(serverHttp.getRequest().queryString());
        }

        httpClient.getRequest().protocol().set(serverHttp.getRequest().protocol());

        //cstate.reqHeaders.addValue(name)
        copyHeaders(serverHttp.getRequest().getMimeHeaders(),
                httpClient.getRequest().getMimeHeaders() /*dest*/);

        // For debug
        httpClient.getRequest().getMimeHeaders().remove("Accept-Encoding");

        if (!keepOpen) {
            httpClient.getRequest().getMimeHeaders().setValue("Connection").set("Close");
        }

        // Any data
        serverHttp.setDataReceivedCallback(copy);
        copy.handleReceived(serverHttp);

        httpClient.send();


        //serverHttp.handleReceived(serverHttp.getSink());
        //httpClient.flush(); // send any data still there

        httpClient.setCompletedCallback(done);
        // Will call release()
        serverHttp.setCompletedCallback(done);

        serverHttpReq.async();
    }

    static HttpDoneCallback done = new HttpDoneCallback();
    static CopyCallback copy = new CopyCallback(null);
    // POST: after sendRequest(ch) we need to forward the body !!


    static void copyHeaders(MultiMap mimeHeaders, MultiMap dest)
            throws IOException {
        for (int i = 0; i < mimeHeaders.size(); i++) {
            CBuffer name = mimeHeaders.getName(i);
            CBuffer val = dest.addValue(name.toString());
            val.set(mimeHeaders.getValue(i));
        }
    }

    /**
     * HTTP _CLIENT_ callback - from tomcat to final target.
     */
    public class ProxyClientCallback implements HttpService {
        /**
         * Headers received from the client (content http server).
         * TODO: deal with version missmatches.
         */
        @Override
        public void service(HttpRequest clientHttpReq, HttpResponse clientHttpRes) throws IOException {
            HttpChannel serverHttp = (HttpChannel) clientHttpReq.getAttribute("SERVER");

            try {
                serverHttp.getResponse().setStatus(clientHttpRes.getStatus());
                serverHttp.getResponse().getMessageBuffer().set(clientHttpRes.getMessageBuffer());
                copyHeaders(clientHttpRes.getMimeHeaders(),
                        serverHttp.getResponse().getMimeHeaders());

                serverHttp.getResponse().getMimeHeaders().addValue("TomcatProxy").set("True");

                clientHttpReq.getHttpChannel().setDataReceivedCallback(copy);
                copy.handleReceived(clientHttpReq.getHttpChannel());

                serverHttp.startSending();


                //clientHttpReq.flush(); // send any data still there

                //  if (clientHttpReq.getHttpChannel().getIn().isClosedAndEmpty()) {
                //     serverHttp.getOut().close(); // all data from client already in buffers
                //  }

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    static final class HttpDoneCallback implements RequestCompleted {

        public HttpDoneCallback() {
        }

        @Override
        public void handle(HttpChannel doneCh, Object extraData) throws IOException {
            HttpChannel serverCh =
                (HttpChannel) doneCh.getRequest().getAttribute("SERVER");
            HttpChannel clientCh = doneCh;
            String tgt = "C";
            if (serverCh == null) {
                 serverCh = doneCh;
                 clientCh =
                    (HttpChannel) doneCh.getRequest().getAttribute("CLIENT");
                 tgt = "S";
            }
            if (serverCh == null || clientCh == null) {
                return;
            }
            if (doneCh.getError()) {
                serverCh.abort("Proxy error");
                clientCh.abort("Proxy error");
                return;
            }

            if (log.isLoggable(Level.FINE)) {
                HttpChannel peerCh =
                    (HttpChannel) doneCh.getRequest().getAttribute("SERVER");
                if (peerCh == null) {
                    peerCh =
                        (HttpChannel) doneCh.getRequest().getAttribute("CLIENT");
                } else {

                }
                log.info(tgt + " " + peerCh.getId() + " " +
                        doneCh.getTarget() + " " +
                        doneCh.getRequest().getMethod() +
                        " " + doneCh.getRequest().getRequestURI() + " " +
                        doneCh.getResponse().getStatus() + " IN:" + doneCh.getIn()
                        + " OUT:" + doneCh.getOut() +
                        " SIN:" + peerCh.getIn() +
                        " SOUT:" + peerCh.getOut() );
            }
            // stop forwarding. After this call the client object will be
            // recycled
            //clientCB.outBuffer = null;

            // We must releaes both at same time
            synchronized (this) {

                serverCh.complete();

                if (clientCh.getRequest().getAttribute("SERVER") == null) {
                    return;
                }
                if (clientCh.isDone() && serverCh.isDone()) {
                    clientCh.getRequest().setAttribute("SERVER", null);
                    serverCh.getRequest().setAttribute("CLIENT", null);
                    clientCh.getRequest().setAttribute("P", null);
                    serverCh.getRequest().setAttribute("P", null);
                    // Reuse the objects.
                    serverCh.release();
                    clientCh.release();
                }
            }
        }
    }


}
