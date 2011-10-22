package org.apache.coyote.lite;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ActionHook;
import org.apache.coyote.Adapter;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.lite.http.HttpClient;
import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpConnectionPool;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.HttpServer;
import org.apache.tomcat.lite.http.MultiMap;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.http.HttpConnectionPool.RemoteServer;
import org.apache.tomcat.lite.http.HttpConnector.HttpChannelEvents;
import org.apache.tomcat.lite.http.HttpConnector.HttpConnection;
import org.apache.tomcat.lite.http.MultiMap.Entry;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.IOConnector;
import org.apache.tomcat.lite.io.SocketConnector;
import org.apache.tomcat.lite.io.SslProvider;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.modeler.Registry;

/**
 * Work in progress - use the refactored http as a coyote connector.
 * Just basic requests work right now - need to implement all the
 * methods of coyote.
 *
 *
 * @author Costin Manolache
 */
public class LiteProtocolHandler implements ProtocolHandler {

    Adapter adapter;
    Map<String, Object> attributes = new HashMap<String, Object>();


    HttpConnector httpConnServer;
    int port = 8999;

    // Tomcat JMX integration
    Registry registry;

    public LiteProtocolHandler() {
    }

    @Override
    public void destroy() throws Exception {
    }

    @Override
    public Adapter getAdapter() {
        return adapter;
    }

    @Override
    public Object getAttribute(String name) {
        // TODO: dynamic
        return attributes.get(name);
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }

    @Override
    public void init() throws Exception {
        registry = Registry.getRegistry(null, null);
        httpConnServer = HttpServer.newServer(port);

        httpConnServer.getDispatcher().setDefaultService(new HttpService() {
            @Override
            public void service(HttpRequest httpReq, HttpResponse httpRes)
                    throws IOException {
                coyoteService(httpReq, httpRes);
            }

        });
        final String base = "" + port;
        bind("Httpconnector-" + port, httpConnServer);
        bind("HttpconnectorPool-" + port, httpConnServer.cpool);
        IOConnector io = httpConnServer.getIOConnector();
        int ioLevel = 0;
        while (io != null) {
            bind("IOConnector-" + (ioLevel++) + "-" + base, io);
            if (io instanceof SocketConnector) {
                bind("NioThread-" + base,
                        ((SocketConnector) io).getSelector());

            }
            io = io.getNet();
        }
        httpConnServer.cpool.setEvents(new HttpConnectionPool.HttpConnectionPoolEvents() {

            @Override
            public void closedConnection(RemoteServer host, HttpConnection con) {
                unbind("HttpConnection-" + base + "-" + con.getId());
            }

            @Override
            public void newConnection(RemoteServer host, HttpConnection con) {
                bind("HttpConnection-" + base + "-" + con.getId(), con);
            }

            @Override
            public void newTarget(RemoteServer host) {
                bind("AsyncHttp-" + base + "-" + host.target, host);
            }

            @Override
            public void targetRemoved(RemoteServer host) {
                unbind("AsyncHttp-" + base + "-" + host.target);
            }

        });

        httpConnServer.setOnCreate(new HttpChannelEvents() {
            @Override
            public void onCreate(HttpChannel data, HttpConnector extraData)
                    throws IOException {
                bind("AsyncHttp-" + base + "-" + data.getId(), data);
            }
            @Override
            public void onDestroy(HttpChannel data, HttpConnector extraData)
                    throws IOException {
                unbind("AsyncHttp-" + base + "-" + data.getId());
            }
        });

        // TODO: process attributes via registry !!

    }

    private void bind(String name, Object o) {
        try {
            registry.registerComponent(o, "TomcatLite:name=" + name, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unbind(String name) {
        registry.unregisterComponent("name=" + name);
    }

    @Override
    public void pause() throws Exception {
    }

    @Override
    public void resume() throws Exception {
    }

    @Override
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;

    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public void start() throws Exception {
        httpConnServer.start();
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Wrap old tomcat buffer to lite buffer.
     */
    private void wrap(MessageBytes dest, CBuffer buffer) {
        dest.setChars(buffer.array(), buffer.position(),
                buffer.length());
    }

    /**
     * Main lite service method, will wrap to coyote request
     */
    private void coyoteService(final HttpRequest httpReq, final HttpResponse httpRes) {
        // TODO: reuse, per req
        RequestData rc = new RequestData();
        rc.init(httpReq, httpRes);

        try {
            adapter.service(rc.req, rc.res);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * ActionHook implementation, include coyote request/response objects.
     */
    public class RequestData implements ActionHook {
        private final class LiteOutputBuffer implements OutputBuffer {
            @Override
            public int doWrite(org.apache.tomcat.util.buf.ByteChunk chunk,
                    Response response) throws IOException {
                httpRes.getBody().append(chunk.getBuffer(), chunk.getStart(),
                        chunk.getLength());
                return chunk.getLength();
            }
        }

        OutputBuffer outputBuffer = new LiteOutputBuffer();
        // TODO: recycle, etc.
        Request req = new Request();

        Response res = new Response();
        HttpResponse httpRes;
        HttpRequest httpReq;

        InputBuffer inputBuffer = new InputBuffer() {
            @Override
            public int doRead(ByteChunk bchunk, Request request)
                    throws IOException {
                httpReq.getBody().waitData(httpReq.getHttpChannel().getIOTimeout());
                int rd =
                    httpReq.getBody().read(bchunk.getBytes(),
                        bchunk.getStart(), bchunk.getBytes().length);
                if (rd > 0) {
                    bchunk.setEnd(bchunk.getEnd() + rd);
                }
                return rd;
            }
        };

        public RequestData() {
            req.setInputBuffer(inputBuffer);
            res.setOutputBuffer(outputBuffer);
            req.setResponse(res);
            res.setRequest(req);
            res.setHook(this);
        }

        public void init(HttpRequest httpReq, HttpResponse httpRes) {
            this.httpRes = httpRes;
            this.httpReq = httpReq;
            // TODO: turn http request into a coyote request - copy all fields,
            // add hooks where needed.

            wrap(req.decodedURI(), httpReq.decodedURI());
            wrap(req.method(), httpReq.method());
            wrap(req.protocol(), httpReq.protocol());
            wrap(req.requestURI(), httpReq.requestURI());
            wrap(req.queryString(), httpReq.queryString());

            req.setServerPort(httpReq.getServerPort());
            req.serverName().setString(req.localName().toString());

            MultiMap mimeHeaders = httpReq.getMimeHeaders();
            MimeHeaders coyoteHeaders = req.getMimeHeaders();
            for (int i = 0; i < mimeHeaders.size(); i++ ) {
                Entry entry = mimeHeaders.getEntry(i);
                MessageBytes val =
                    coyoteHeaders.addValue(entry.getName().toString());
                val.setString(entry.getValue().toString());
            }
        }

        /**
         * Send an action to the connector.
         *
         * @param actionCode Type of the action
         * @param param Action parameter
         */
        public void action(ActionCode actionCode, Object param) {

            if (actionCode == ActionCode.ACTION_POST_REQUEST) {
                commit(); // make sure it's sent - on errors
            } else if (actionCode == ActionCode.ACTION_COMMIT) {
                commit();
            } else if (actionCode == ActionCode.ACTION_ACK) {
                // Done automatically by http connector
            } else if (actionCode == ActionCode.ACTION_CLIENT_FLUSH) {
                try {
                    httpReq.send();
                } catch (IOException e) {
                    httpReq.getHttpChannel().abort(e);
                    res.setErrorException(e);
                }

            } else if (actionCode == ActionCode.ACTION_CLOSE) {
                // Close

                // End the processing of the current request, and stop any further
                // transactions with the client

//                comet = false;
//                try {
//                    outputBuffer.endRequest();
//                } catch (IOException e) {
//                    // Set error flag
//                    error = true;
//                }

            } else if (actionCode == ActionCode.ACTION_RESET) {
                // Reset response
                // Note: This must be called before the response is committed
                httpRes.getBody().clear();

            } else if (actionCode == ActionCode.ACTION_CUSTOM) {

                // Do nothing

            } else if (actionCode == ActionCode.ACTION_REQ_HOST_ADDR_ATTRIBUTE) {
                req.remoteAddr().setString(httpReq.remoteAddr().toString());
            } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE) {
                req.localName().setString(httpReq.localName().toString());
            } else if (actionCode == ActionCode.ACTION_REQ_HOST_ATTRIBUTE) {
                req.remoteHost().setString(httpReq.remoteHost().toString());
            } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_ADDR_ATTRIBUTE) {
                req.localAddr().setString(httpReq.localAddr().toString());
            } else if (actionCode == ActionCode.ACTION_REQ_REMOTEPORT_ATTRIBUTE) {
                req.setRemotePort(httpReq.getRemotePort());
            } else if (actionCode == ActionCode.ACTION_REQ_LOCALPORT_ATTRIBUTE) {
                req.setLocalPort(httpReq.getLocalPort());
            } else if (actionCode == ActionCode.ACTION_REQ_SSL_ATTRIBUTE ) {

                Object sslAtt = httpReq.getHttpChannel().getNet().getAttribute(SslProvider.ATT_SSL_CIPHER);
                req.setAttribute("javax.servlet.request.cipher_suite", sslAtt);

                sslAtt = httpReq.getHttpChannel().getNet().getAttribute(SslProvider.ATT_SSL_KEY_SIZE);
                req.setAttribute("javax.servlet.request.key_size", sslAtt);

                sslAtt = httpReq.getHttpChannel().getNet().getAttribute(SslProvider.ATT_SSL_SESSION_ID);
                req.setAttribute("javax.servlet.request.ssl_session", sslAtt);

            } else if (actionCode == ActionCode.ACTION_REQ_SSL_CERTIFICATE) {

                Object cert = httpReq.getHttpChannel().getNet().getAttribute(SslProvider.ATT_SSL_CERT);
                req.setAttribute("javax.servlet.request.X509Certificate", cert);

            } else if (actionCode == ActionCode.ACTION_REQ_SET_BODY_REPLAY) {
                ByteChunk body = (ByteChunk) param;
                httpReq.getBody().clear();
                try {
                    httpReq.getBody().append(body.getBuffer(), body.getStart(), body.getLength());
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else if (actionCode == ActionCode.ACTION_AVAILABLE) {
                req.setAvailable(httpReq.getBody().available());
            } else if (actionCode == ActionCode.ACTION_COMET_BEGIN) {
//                comet = true;
            } else if (actionCode == ActionCode.ACTION_COMET_END) {
//                comet = false;
            } else if (actionCode == ActionCode.ACTION_COMET_CLOSE) {
                //no op
            } else if (actionCode == ActionCode.ACTION_COMET_SETTIMEOUT) {
                //no op
//            } else if (actionCode == ActionCode.ACTION_ASYNC_START) {
//                //TODO SERVLET3 - async
//            } else if (actionCode == ActionCode.ACTION_ASYNC_COMPLETE) {
//                //TODO SERVLET3 - async
//            } else if (actionCode == ActionCode.ACTION_ASYNC_SETTIMEOUT) {
//                //TODO SERVLET3 - async
            }


        }

        private void commit() {
            if (res.isCommitted())
                return;

            // TODO: copy headers, fields
            httpRes.setStatus(res.getStatus());
            httpRes.setMessage(res.getMessage());
            MultiMap mimeHeaders = httpRes.getMimeHeaders();
            MimeHeaders coyoteHeaders = res.getMimeHeaders();
            for (int i = 0; i < coyoteHeaders.size(); i++ ) {
                MessageBytes name = coyoteHeaders.getName(i);
                MessageBytes val = coyoteHeaders.getValue(i);
                Entry entry = mimeHeaders.addEntry(name.toString());
                entry.getValue().set(val.toString());
            }
            String contentType = res.getContentType();
            if (contentType != null) {
                mimeHeaders.addEntry("Content-Type").getValue().set(contentType);
            }
            String contentLang = res.getContentType();
            if (contentLang != null) {
                mimeHeaders.addEntry("Content-Language").getValue().set(contentLang);
            }
            long contentLength = res.getContentLengthLong();
            if (contentLength != -1) {
                httpRes.setContentLength(contentLength);
            }
            String lang = res.getContentLanguage();
            if (lang != null) {
                httpRes.setHeader("Content-Language", lang);
            }

            try {
                httpReq.send();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
