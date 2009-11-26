/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.lite.servlet;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.tomcat.integration.ObjectManager;
import org.apache.tomcat.integration.simple.SimpleObjectManager;
import org.apache.tomcat.lite.http.DefaultHttpConnector;
import org.apache.tomcat.lite.http.Dispatcher;
import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpConnector;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.MappingData;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.io.WrappedException;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.MemoryIOConnector;
import org.apache.tomcat.lite.io.CBuffer;

/**
 * Helper allowing to run servlets using Tomcat lite http server.
 * 
 * This is not a full servlet engine - just a small subset allowing 
 * easier transition or reuse. 
 * 
 * @author Costin Manolache
 */
public class TomcatLite implements Runnable {

    static ServletApi api = ServletApi.get();
    
    private String serverDirName;
    private File workDir;
    
    // all contexts - hostMapper knows about hostnames and how they are mapped.
    // this shouldn't be needed if we want to delegate ctx management
    private ArrayList<ServletContextImpl> contexts = new ArrayList();

    URLClassLoader contextParentLoader;
    Logger log = Logger.getLogger("TomcatLite");
    //BaseMapper hostMapper = new BaseMapper();

    // Servlets to preload in each context, configurable from CLI or API
    Map<String,String> preloadServlets = new HashMap();
    Map<String,String> preloadMappings = new HashMap();
    
    Map<String,String> ctxDefaultInitParam = new HashMap();
    
    ObjectManager om;
    
    private HttpConnector httpConnector;
    
    static String SERVLETS_PACKAGE = "org.apache.tomcat.servlets";
    
    // can be set to ConfigLoader to skip auto-parsing web.xml
    private String deployListener = 
        "org.apache.tomcat.servlets.config.deploy.WarDeploy";

    int port = 8080;
    
    public TomcatLite() {
    }

    public TomcatLite(ObjectManager om) {
        this.setObjectManager(om);
    }

    // --------------- start/stop ---------------

    public static ObjectManager defaultObjectManager() {
        SimpleObjectManager cfg = new SimpleObjectManager();
        return cfg;
    }
    /**
     * Return the object manager associated with this tomcat.
     * If none set, create a minimal one with the default 
     * values.
     */
    public ObjectManager getObjectManager() {
        if (om == null) {
            om = defaultObjectManager();
            om.bind("TomcatLite", this);
        }
        return om;
    }
    
    public void setObjectManager(ObjectManager om) {
        this.om = om;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public List/*<ServletContextImpl>*/ getWebapps() {
        return contexts;
    }
    
    public URLClassLoader getContextParentLoader() {
        if (contextParentLoader == null) {
            
            ClassLoader parent = this.getClass().getClassLoader();
            contextParentLoader = new URLClassLoader(new URL[] {},
                    parent);
            
            /*if (engineRepo == null) {
                engineRepo = new Repository();
                engineRepo.setParentClassLoader(parent);
            }
            
            contextParentLoader = 
                engineRepo.getClassLoader();
            */
        }
        return contextParentLoader;
    }
        
    public void start() throws IOException {
        long t0 = System.currentTimeMillis();
        
        // start all contexts
        // init all contexts
        Iterator i1 = contexts.iterator();
        while (i1.hasNext()) {
           ServletContextImpl ctx = (ServletContextImpl) i1.next();
           try {
               ctx.start();
           } catch (Throwable e) {
               e.printStackTrace();
           }
        }
        long t1 = System.currentTimeMillis();
        log.fine("Engine.start() " + (t1-t0));
    }
    
      
    /**
     * Add a context - used for IntrospectionUtils.
     * 
     * ContextPath:ContextBaseDir
     */
    public void setContext(String c) throws ServletException {
        String[] pathDir = c.split(":", 2);
        String base = pathDir[0].trim();
        if (base.length() == 0) {
            addServletContext("", pathDir[1], null);            
        } else {
            addServletContext("", pathDir[1], base);
        }
    }
    
    public void setServletContexts(List<ServletContext> c) throws ServletException {
        for (ServletContext ctx: c) {
            addServletContext((ServletContextImpl) ctx);
        }
    }
    
    public void setDeployListener(String deploy) {
        this.deployListener = deploy;
    }
    
    public String getDeployListener() {
        return deployListener;
    }
    
    public void setPreload(String servletNameClass) {
        String[] nv = servletNameClass.split(":");
        preloadServlets.put(nv[0], nv[1]);
    }
    
    public void addPreload(String servletName, String servletClassName) {
        preloadServlets.put(servletName, servletClassName);
    }

    public void setDefaultInitParam(String nameValue) {
        String[] nv = nameValue.split(":");
        ctxDefaultInitParam.put(nv[0], nv[1]);
    }
    
    public void addDefaultInitParam(String name, String value) {
        ctxDefaultInitParam.put(name, value);
    }
    
    public void setPreloadMappings(String servletPath) {
        String[] nv = servletPath.split(":");
        preloadMappings.put(nv[0], nv[1]);
    }
    
    public void addPreloadMapping(String servletName, String path) {
        preloadMappings.put(servletName, path);
    }
    
    public void stop() {
        Iterator i1 = contexts.iterator();
        while (i1.hasNext()) {
           ServletContextImpl ctx = (ServletContextImpl) i1.next();
            try {
                ctx.destroy();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        try {
            stopConnector();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // -------------- Context add/remove --------------

    public static String[] DEFAULT_WELCOME = { "index.html" };
    
    public void addServletContext(ServletContextImpl ctx) throws ServletException {
        ctx.setTomcat(this);

        getDispatcher().addContext(ctx.getHostname(), 
                ctx.getContextPath(), ctx, null, null, ctxService);
       
        contexts.add(ctx);
        
        getObjectManager().bind("ServletContext:" + 
                ctx.getHostname() + ctx.getContextPath(), 
                ctx);

    }
    
    /**
     * Add a context. 
     * 
     * web.xml will be read as part of init, and the initialization will be 
     * part of start or lazy.
     * 
     * @param hostname - ""
     *            if default host, or string to be matched with Host header
     * @param basePath = directory where the webapp is installed           
     * @param path -
     *            context path, "/" for root, "/examples", etc
     * @return a servlet context
     * @throws ServletException
     */
    public ServletContextImpl addServletContext(String hostname, 
                                            String basePath,
                                            String path)
        throws ServletException
    {
        ServletContextImpl ctx = api.newContext();
        ctx.setContextPath(path);
        ctx.setBasePath(basePath);
        addServletContext(ctx);
        return ctx;
    }
    
    public static ServletRequestImpl getFacade(HttpRequest req) {
        ServletRequestImpl sreq = (ServletRequestImpl) req.wrapperRequest;
        if (sreq == null) {
            sreq = api.newRequest(req);
            req.wrapperRequest = sreq;
            req.nativeRequest = req.getHttpChannel(); // TODO ? 
            
            sreq.getResponse().setHttpResponse(req.getHttpChannel().getResponse());
        }
        return sreq;
    }
    
    public void removeServletContext(ServletContext sctx)
        throws ServletException
    {
        ServletContextImpl ctx = (ServletContextImpl) sctx;
        // TODO: destroy all servlets and filters
        // TODO: clean up any other reference to the context or its loader
        notifyRemove(ctx);
    }
    
    public Dispatcher getDispatcher() {
        return getHttpConnector().getDispatcher();
    }
    
    /** 
     * Required for ServletContext.getContext(uri);
     * @throws ServletException 
     * @throws IOException 
     */
    public ServletContextImpl getContext(ServletContextImpl impl, String uri) 
            throws IOException, ServletException {
        MappingData md = new MappingData();
        CBuffer hostMB = CBuffer.newInstance();
        CBuffer urlMB = CBuffer.newInstance();
        hostMB.set(impl.getHostname());
        urlMB.set(uri);
        getDispatcher().map(hostMB, urlMB, md);
        return (ServletContextImpl) md.context;
    }
    
    HttpService ctxService = new HttpService() {

        @Override
        public void service(HttpRequest httpReq, HttpResponse httpRes)
                throws IOException {
            HttpChannel client = httpReq.getHttpChannel();
            
            ServletRequestImpl req = getFacade(client.getRequest());
            ServletResponseImpl res = req.getResponse();
            
            try {
                TomcatLite.this.service(req, res);

                
                if (!req.isAsyncStarted()) {
                    // Recycle the facade objects - 
                    // low level recycled by connector

                    // Not an actual flush - only goes to next
                    res.getOutputBuffer().push();

                    req.recycle();
                }
            } catch (IOException ex) { 
                throw ex;
            } catch (Throwable t) {
                throw new WrappedException(t);
            }
        }
    };
    
    /** 
     * Service a request.  
     * The response is not flushed, and we don't recycle at the end.
     */
    public void service(ServletRequestImpl req, ServletResponseImpl res) 
            throws Exception, IOException {
        
        // TODO: move later
        req.parseSessionId();
        
        MappingData mapRes = req.getMappingData();
        ServletContextImpl ctx = (ServletContextImpl)mapRes.context;
        try {
          // context wrapper;
          mapRes.service = null;

          getDispatcher().map(ctx.getContextMap(), req.getHttpRequest().decodedURI(), mapRes);

          // Possible redirect
          CBuffer redirectPathMB = mapRes.redirectPath;
          if (redirectPathMB.length() != 0) {
              CBuffer redirectPath = CBuffer.newInstance();
              req.getHttpRequest().getUrlEncoding()
                  .urlEncode(redirectPathMB,
                          redirectPath, req.getHttpRequest().getCharEncoder());

              String query = req.getQueryString();
              if (req.isRequestedSessionIdFromURL()) {
                  // This is not optimal, but as this is not very common, it
                  // shouldn't matter
                  redirectPath.append(";")
                      .append(ServletRequestImpl.SESSION_PARAMETER_NAME)
                      .append("=") 
                      .append(req.getRequestedSessionId());
              }
              if (query != null) {
                  // This is not optimal, but as this is not very common, it
                  // shouldn't matter
                  redirectPath.append("?").append(query);
              }
              res.sendRedirect(redirectPath.toString());
              return;
          }
          
          req.setContext(ctx);
          req.parseSessionCookiesId();

          // bind class loader 
          Thread.currentThread().setContextClassLoader(ctx.getClassLoader());
          
          ServletConfigImpl h = (ServletConfigImpl) mapRes.getServiceObject();
          if (h != null) {
            req.setWrapper((ServletConfigImpl)mapRes.getServiceObject());
            h.serviceServlet(ctx, req, res, h, mapRes );
          }
        } catch (Throwable t) {
            log.log(Level.INFO, ctx.contextPath +  ": " + req.getRequest() + 
                    ": User exception in servlet ", t);
        } finally {
            if(mapRes != null ) 
                mapRes.recycle();
        }
    }
    

    // ------------ Notifications for JMX ----------------

    void notifyAdd(Object o) {
    }

    void notifyRemove(Object o) {
    }

    public void setServerDir(String dir) {
      this.serverDirName = dir;
    }
        
    public File getWork() {
        if (workDir == null) {
          if (serverDirName == null) {
              serverDirName = "./";
          }
          File rootDirFile = new File(serverDirName);
          workDir = new File(rootDirFile, "tomcat-work");
          if (workDir.exists()) {
            workDir.mkdirs();
          }
        }
        return workDir;
    }
    
    /** 
     * Load all context configs, loads the connector
     * 
     * @throws ServletException
     * @throws IOException
     */
    public void init() throws ServletException, IOException {
      if (contexts.size() == 0) {
        setContext("/:./webapps/ROOT");
      }
      Iterator i1 = contexts.iterator();
      while (i1.hasNext()) {
        ServletContextImpl ctx = (ServletContextImpl) i1.next();
        try {
          ctx.loadConfig();
        } catch (Throwable e) {
          e.printStackTrace();
        }
      }
    }
    
    public void startConnector() throws IOException {
        getHttpConnector().setPort(port);
        getHttpConnector().start();
    }

    public void stopConnector() throws Exception {
        getHttpConnector().stop();
    }

    public void run() {
        try {
            execute();
        } catch (ServletException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void execute() throws ServletException, IOException {
        init();
        start();
        startConnector();
    }

    void setHttpConnector(HttpConnector httpConnector) {
        this.httpConnector = httpConnector;
    }

    public HttpConnector getHttpConnector() {
        if (httpConnector == null) {
            httpConnector = DefaultHttpConnector.get();
        }
        return httpConnector;
    }
    
    HttpConnector local;
    
    public HttpConnector getLocalConnector() {
        if (local == null) {
            local = new HttpConnector(new MemoryIOConnector());
        }
        return local;
    }
}
