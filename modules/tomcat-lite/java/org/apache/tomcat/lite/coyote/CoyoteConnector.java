/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.lite.coyote;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ActionHook;
import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.integration.ObjectManager;
import org.apache.tomcat.lite.BodyReader;
import org.apache.tomcat.lite.BodyWriter;
import org.apache.tomcat.lite.ClientAbortException;
import org.apache.tomcat.lite.Connector;
import org.apache.tomcat.lite.ServletRequestImpl;
import org.apache.tomcat.lite.ServletResponseImpl;
import org.apache.tomcat.lite.TomcatLite;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.UriNormalizer;
import org.apache.tomcat.util.http.HttpRequest;
import org.apache.tomcat.util.http.HttpResponse;
import org.apache.tomcat.util.net.SocketStatus;

public class CoyoteConnector implements Adapter, Connector {

    private TomcatLite lite;

    public CoyoteConnector() {
    }



    public void acknowledge(HttpServletResponse res) throws IOException {
        Response cres = (Response) ((ServletResponseImpl) res).getHttpResponse().nativeResponse;
        cres.acknowledge();
    }

    public void reset(HttpServletResponse res) {
        Response cres = (Response) ((ServletResponseImpl) res).getHttpResponse().nativeResponse;
        cres.reset();
    }

    public void recycle(HttpServletRequest req, HttpServletResponse res) {

    }

    public static HttpResponse getResponse(final Response cres) {
        HttpResponse hres = new HttpResponse() {
            public int getStatus() {
                return cres.getStatus();
            }
            public void setStatus(int i) {
                super.setStatus(i);
                cres.setStatus(i);
            }
            public void setMessage(String s) {
                super.setMessage(s);
                cres.setMessage(s);
            }
            public String getMessage() {
                return cres.getMessage();
            }
            public boolean isCommitted() {
                return cres.isCommitted();
            }

            public void setCommitted(boolean b) {
                cres.setCommitted(b);
            }
        };

        hres.setMimeHeaders(cres.getMimeHeaders());
        hres.nativeResponse = cres;

        return hres;
    }

    public static HttpRequest getRequest(Request req) {

        HttpRequest httpReq = new HttpRequest(req.scheme(),
                req.method(),
                req.unparsedURI(),
                req.protocol(),
                req.getMimeHeaders(),
                req.requestURI(),
                req.decodedURI(),
                req.query(), req.getParameters(),
                req.serverName(),
                req.getCookies()) {

        };
        httpReq.nativeRequest = req;

        // TODO: anything else computed in coyote ?

        return httpReq;
    }

    @Override
    public void initRequest(HttpServletRequest hreq, HttpServletResponse hres) {
        ServletRequestImpl req = (ServletRequestImpl) hreq;
        ServletResponseImpl res = (ServletResponseImpl) hres;
        req.setConnector(this);

        Request creq = new Request();
        Response cres = new Response();
        HttpResponse nRes = getResponse(cres);

        BodyWriter out = new BodyWriter(4096);
        out.setConnector(this, res);

        res.setHttpResponse(nRes, out);

        cres.setRequest(creq);
        cres.setHook(new ActionHook() {
          public void action(ActionCode actionCode,
                             Object param) {
          }
        });

        BodyReader in = new BodyReader();
        in.setConnector(this, req);
        HttpRequest nReq = getRequest(creq);
        req.setHttpRequest(nReq, in);

    }


    // ---- Coyote Adapter interface ---

    @Override
    public void service(Request creq, Response cres) throws Exception {
        long t0 = System.currentTimeMillis();

        // compute decodedURI - not done by connector
        UriNormalizer.decodeRequest(creq.decodedURI(), creq.requestURI(), creq.getURLDecoder());

        // find the facades
        ServletRequestImpl req = (ServletRequestImpl) creq.getNote(ADAPTER_REQ_NOTE);
        ServletResponseImpl res = (ServletResponseImpl) cres.getNote(ADAPTER_RES_NOTE);


        if (req == null) {
          req = new ServletRequestImpl();
          res = req.getResponse();

          BodyReader in = new BodyReader();
          in.setConnector(this, req);

          HttpRequest nReq = getRequest(creq);
          nReq.setServerPort(creq.getServerPort());
          HttpResponse nRes = getResponse(cres);

          req.setHttpRequest(nReq, in);
          BodyWriter out = new BodyWriter(4096);
          out.setConnector(this, res);

          res.setHttpResponse(nRes, out);

          creq.setNote(ADAPTER_REQ_NOTE, req);
          cres.setNote(ADAPTER_RES_NOTE, res);

        }
        req.setConnector(this);

        try {
            lite.service(req, res);
        } catch(IOException ex) {
            throw ex;
        } catch( Throwable t ) {
            t.printStackTrace();
        } finally {
            long t1 = System.currentTimeMillis();

//            log.info("<<<<<<<< DONE: " + creq.method() + " " +
//                    creq.decodedURI() + " " +
//                    res.getStatus() + " " +
//                    (t1 - t0));

            // Final processing
            // TODO: only if not commet, this doesn't work with the
            // other connectors since we don't have the info
            // TODO: add this note in the nio/apr connectors
            // TODO: play nice with TomcatLite, other adapters that flush/close
            if (cres.getNote(COMET_RES_NOTE) == null) {

                if (!res.isCommitted()) {
                    cres.sendHeaders();
                }
                res.getOutputBuffer().flush();

                BodyWriter mw = res.getBodyWriter();
                //MessageWriter.getWriter(creq, cres, 0);
                mw.flush();
                mw.recycle();

                BodyReader reader = req.getBodyReader();
                //getReader(creq);
                reader.recycle();

                cres.finish();

                creq.recycle();
                cres.recycle();

                req.recycle();
                res.recycle();
            }
        }
    }

    @Override
    public boolean event(Request req, Response res, SocketStatus status)
        throws Exception {
      return false;
    }


    public void setTomcatLite(TomcatLite lite) {
        this.lite = lite;
    }


    public String getRemoteHost(HttpServletRequest hreq) {
        ServletRequestImpl req = (ServletRequestImpl) hreq;

        Request creq = (Request) req.getHttpRequest().nativeRequest;
        creq.action(ActionCode.ACTION_REQ_HOST_ATTRIBUTE, creq);
        return creq.remoteHost().toString();
    }

    public String getRemoteAddr(HttpServletRequest hreq) {
        ServletRequestImpl req = (ServletRequestImpl) hreq;

        Request creq = (Request) req.getHttpRequest().nativeRequest;
        creq.action(ActionCode.ACTION_REQ_HOST_ADDR_ATTRIBUTE, creq);
        return creq.remoteAddr().toString();
    }


    @Override
    public void beforeClose(HttpServletResponse res, int len) throws IOException {
        Response cres = (Response) ((ServletResponseImpl) res).getHttpResponse().nativeResponse;

        if ((!cres.isCommitted())
                && (cres.getContentLengthLong() == -1)) {
                // Flushing the char buffer
                // If this didn't cause a commit of the response, the final content
                // length can be calculated
                if (!cres.isCommitted()) {
                    cres.setContentLength(len);
                }
            }
    }

    public int doRead(ServletRequestImpl hreq, ByteChunk bb) throws IOException {
        ServletRequestImpl req = (ServletRequestImpl) hreq;

        Request creq = (Request) req.getHttpRequest().nativeRequest;
        return creq.doRead(bb);
    }

    @Override
    public void doWrite(HttpServletResponse res, ByteChunk chunk)
            throws IOException {
        Response cres = (Response) ((ServletResponseImpl) res).getHttpResponse().nativeResponse;
        cres.doWrite(chunk);

    }


    @Override
    public void realFlush(HttpServletResponse res) throws IOException {
        Response cres = (Response) ((ServletResponseImpl) res).getHttpResponse().nativeResponse;
        cres.action(ActionCode.ACTION_CLIENT_FLUSH,
                cres);
        // If some exception occurred earlier, or if some IOE occurred
        // here, notify the servlet with an IOE
        if (cres.isExceptionPresent()) {
            throw new ClientAbortException
            (cres.getErrorException());
        }

    }


    @Override
    public void sendHeaders(HttpServletResponse res) throws IOException {
        Response cres = (Response) ((ServletResponseImpl) res).getHttpResponse().nativeResponse;

        // This should happen before 'prepareResponse' is called !!
        // Now update coyote response based on response
        // don't set charset/locale - they're computed in lite
        cres.setContentType(res.getContentType());
        cres.sendHeaders();
    }

    @Override
    public void finishResponse(HttpServletResponse res) throws IOException {
        Response cres = (Response) ((ServletResponseImpl) res).getHttpResponse().nativeResponse;
        cres.finish();
    }

    protected int port = 8800;
    protected boolean daemon = false;

    /**
     * Note indicating the response is COMET.
     */
    public static final int COMET_RES_NOTE = 2;
    public static final int COMET_REQ_NOTE = 2;

    public static final int ADAPTER_RES_NOTE = 1;
    public static final int ADAPTER_REQ_NOTE = 1;

    protected ProtocolHandler proto;

    //protected Adapter adapter = new MapperAdapter();
    protected int maxThreads = 20;
    boolean started = false;
    boolean async = false; // use old nio connector

    protected ObjectManager om;


    public void setObjectManager(ObjectManager om) {
        this.om = om;
    }

    /**
     * Add an adapter. If more than the 'default' adapter is
     * added, a MapperAdapter will be inserted.
     *
     * @param path Use "/" for the default.
     * @param adapter
     */
//    public void addAdapter(String path, Adapter added) {
//        if ("/".equals(path)) {
//            ((MapperAdapter) adapter).setDefaultAdapter(added);
//        } else {
//            ((MapperAdapter) adapter).getMapper().addWrapper(path, added);
//        }
//    }

    /**
     */
    public void run() {
        try {
            init();
            start();
        } catch(IOException ex) {
            ex.printStackTrace();
        }
    }

    public void setDaemon(boolean b) {
      daemon = b;
    }

    protected void initAdapters() {
        if (proto == null) {
            addProtocolHandler(port, daemon);
        }
      // adapter = ...
      // Adapter secondaryadapter = ...
      //registry.registerComponent(secondaryadapter, ":name=adapter", null);
    }

    public void stop() throws Exception {
      if (!started) {
        return;
      }
      proto.destroy();
      started = false;
    }

//    /**
//     *  Simple CLI support - arg is a path:className pair.
//     */
//    public void setAdapter(String arg)  {
//      String[] pathClass = arg.split(":", 2);
//      try {
//        Class c = Class.forName(pathClass[1]);
//        Adapter a = (Adapter) c.newInstance();
//        addAdapter(pathClass[0],a);
//      } catch (Throwable e) {
//        e.printStackTrace();
//      }
//    }

    public void setConnector(ProtocolHandler h) {
        this.proto = h;
        h.setAttribute("port", Integer.toString(port));

        om.bind("ProtocolHandler:" + "ep-" + port, proto);
    }

    public void addProtocolHandler(int port, boolean daemon) {
        Http11NioProtocol proto = new Http11NioProtocol();
        proto.setCompression("on");
        proto.setCompressionMinSize(32);
        proto.setPort(port);
        proto.getEndpoint().setDaemon(daemon);
        setConnector(proto);
        setPort(port);
        setDaemon(daemon);
    }

    public void addProtocolHandler(ProtocolHandler proto,
                                   int port, boolean daemon) {
        setConnector(proto);
        setPort(port);
        setDaemon(daemon);
    }

    public void setPort(int port) {
        if (proto != null) {
            proto.setAttribute("port", Integer.toString(port));
        }
        this.port = port;
      }


    public void init() {
        //JdkLoggerConfig.loadCustom();
        om.bind("CoyoteConnector:" + "CoyoteConnector-" + port,
                this);
    }


    public void start() throws IOException {
      try {
        if (started) {
          return;
        }
        init();
        initAdapters();

        // not required - should run fine without a connector.
        if (proto != null) {
            proto.setAdapter(this);

            proto.init();
            proto.start();
        }

        started = true;
      } catch (Throwable e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }

    public boolean getStarted() {
      return started;
    }

    public boolean asyncDispatch(Request req,Response res, SocketStatus status) throws Exception {
        // implement me
        return false;
    }

}
