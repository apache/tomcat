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
package org.apache.coyote.servlet;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.integration.ObjectManager;
import org.apache.tomcat.integration.simple.SimpleObjectManager;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.UriNormalizer;
import org.apache.tomcat.util.http.mapper.MappingData;

/**
 * Simpler, lower footprint serlvet engine.  
 * 
 * Uses ObjectManager to integate with an embedding app 
 * - the object names it uses:
 * 
 * Internal objects created by Tomcat and registered for management 
 * and injection: 
 * - Servlet:CONTEXT_PATH:SERVLETNAME - a ServletWrapper
 * - ServletContext:CONTEXT_PATH
 * - ProtocolHandler:ep-PORT - coyote ProtocolHandler
 * - CoyoteServer:CoyoteServer-PORT
 * - CoyoteAdapter:PATH - for the coyote used Adapter 
 * - TomcatLite - this object.
 * - Connector - the connector object
 * 
 * Plugins to be constructed by framework ( defaults set in initDefaults ):
 * - UserSessionManager
 * - UserTemplateClassMapper
 * - ContextPreinitListener
 * - Connector
 * - WebappServletMapper
 * - WebappFilterMapper
 * - default-servlet  
 * - jspwildcard-servlet
 * - Foo-servlet - servlet named Foo
 * 
 * 
 * @author Costin Manolache
 */
public class TomcatLite implements Runnable {

    private String serverDirName;
    private File workDir;
    
    // all contexts - hostMapper knows about hostnames and how they are mapped.
    // this shouldn't be needed if we want to delegate ctx management
    private ArrayList<ServletContextImpl> contexts = new ArrayList();

    URLClassLoader contextParentLoader;
    
    // Discovered or default Host/Context mapper
    Filter hostMapper;

    // Servlets to preload in each context, configurable from CLI or API
    Map<String,String> preloadServlets = new HashMap();
    Map<String,String> preloadMappings = new HashMap();
    
    Map<String,String> ctxDefaultInitParam = new HashMap();
        
    Connector connector;
    
    ObjectManager om;
    
    static String SERVLETS_PACKAGE = "org.apache.tomcat.servlets";
    
    
    protected boolean daemon = false;
    
    public TomcatLite() {
    }

    public TomcatLite(ObjectManager om) {
        this.setObjectManager(om);
    }

    // --------------- start/stop ---------------

    public static ObjectManager defaultObjectManager() {
        SimpleObjectManager cfg = new SimpleObjectManager();
        cfg.loadResource("org/apache/coyote/servlet/config.properties");
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
        }
        return om;
    }
    
    public void setObjectManager(ObjectManager om) {
        this.om = om;
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
        System.err.println("Engine.start() " + (t1-t0));
    }
    
      
    /**
     * Add a context - used for IntrospectionUtils.
     * 
     * ContextPath:ContextBaseDir
     */
    public void setContext(String c) throws ServletException {
        String[] pathDir = c.split(":", 2);
        addServletContext("", pathDir[1], pathDir[0]);
    }
    
    public void setServletContexts(List<ServletContext> c) throws ServletException {
        for (ServletContext ctx: c) {
            addServletContext((ServletContextImpl) ctx);
        }
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
        if (hostMapper == null) { 
            hostMapper = new WebappContextMapper();
        }

        ((WebappContextMapper) hostMapper).addHost(ctx.getHostname(), null);
        ((WebappContextMapper) hostMapper).addContext(ctx.getHostname(), 
                ctx);

        contexts.add(ctx);
        
        getObjectManager().bind("ServletContext:" + ctx.getContextPath(), 
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
    public ServletContext addServletContext(String hostname, 
                                            String basePath,
                                            String path)
        throws ServletException
    {
        ServletContextImpl ctx = new ServletContextImpl();
        ctx.setContextPath(path);
        ctx.setBasePath(basePath);
        addServletContext(ctx);
        return ctx;
    }
    
    public void removeServletContext(ServletContext sctx)
        throws ServletException
    {
        ServletContextImpl ctx = (ServletContextImpl) sctx;
        // TODO: destroy all servlets and filters
        // TODO: clean up any other reference to the context or its loader
        notifyRemove(ctx);
    }
    
    
    /** 
     * Required for ServletContext.getContext(uri);
     * @throws ServletException 
     * @throws IOException 
     */
    public ServletContextImpl getContext(ServletContextImpl impl, String uri) 
            throws IOException, ServletException {
        // Create a request - needs to be simplified
        ServletRequestImpl req = createMessage(impl, impl.contextPath, uri);
        hostMapper.doFilter(req, null, null);
        return req.getContext();
    }
    
    public ServletResponseImpl service(ServletRequestImpl req) throws IOException, Exception {
      ServletResponseImpl res = req.getResponse();
      service(req, res);
      endRequest(req, res);
      return res;
    }
    
    public void service(ServletRequestImpl req, ServletResponseImpl res) 
            throws Exception, IOException {
        // parse the session id from URI
        req.parseSessionId();
        
        try {
          UriNormalizer.decodeRequest(req.getHttpRequest().decodedURI(), 
                  req.getHttpRequest().requestURI(),
                  req.getHttpRequest().getURLDecoder());
        } catch(IOException ioe) {
            res.setStatus(400);
            return;
        }
        
        MappingData mapRes = req.getMappingData();
        try {
          // TODO: make hostMapper configurable, implement interface,
          // simple to set on ROOT context
          hostMapper.doFilter(req, null, null);
            

          ServletContextImpl ctx = (ServletContextImpl)mapRes.context;
          if( ctx == null ) {
            // TODO: 404
            res.setStatus(404);
            return;
          }
          req.setContext(ctx);

          // bind class loader 
          Thread.currentThread().setContextClassLoader(ctx.getClassLoader());

          WebappServletMapper mapper = ctx.getMapper();
          mapper.map(req.getHttpRequest().decodedURI(), mapRes);

          // Possible redirect
          MessageBytes redirectPathMB = mapRes.redirectPath;
          if (!redirectPathMB.isNull()) {
              String redirectPath = res.urlEncoder.encodeURL(redirectPathMB.toString());
              String query = req.getQueryString();
              if (req.isRequestedSessionIdFromURL()) {
                  // This is not optimal, but as this is not very common, it
                  // shouldn't matter
                  redirectPath = redirectPath + ";" + ServletRequestImpl.SESSION_PARAMETER_NAME + "=" 
                      + req.getRequestedSessionId();
              }
              if (query != null) {
                  // This is not optimal, but as this is not very common, it
                  // shouldn't matter
                  redirectPath = redirectPath + "?" + query;
              }
              res.sendRedirect(redirectPath);
              return;
          }
          
          req.parseSessionCookiesId();

          ServletConfigImpl h=(ServletConfigImpl)mapRes.wrapper;
          if (h != null) {
            req.setWrapper((ServletConfigImpl)mapRes.wrapper);
            serviceServlet(ctx, req, res, h, mapRes );
            // send the response...

            //res.flushBuffer();

            // Recycle the wrapper request and response
            //req.recycle();
            //res.recycle();
          }
        } finally {
            if(mapRes != null ) 
                mapRes.recycle();
        }
    }
    
    /** Coyote / mapper adapter. Result of the mapper.
     *  
     *  This replaces the valve chain, the path is: 
     *    1. coyote calls mapper -> result Adapter 
     *    2. service is called. Additional filters are set on the wrapper. 
     * @param mapRes 
     */
    private void serviceServlet(ServletContextImpl ctx, 
                               ServletRequestImpl req, 
                               ServletResponseImpl res,
                               ServletConfigImpl servletConfig, 
                               MappingData mapRes) 
            throws IOException {
        Servlet servlet = null;
        try {
            if (servletConfig.isUnavailable()) {
                handleUnavailable(res, servletConfig);
                return;
            }
            try {
                servlet = servletConfig.allocate();
            } catch(ServletException ex) {
                handleUnavailable(res, servletConfig);
            }
            WebappFilterMapper filterMap = ctx.getFilterMapper();
            FilterChainImpl chain = 
                filterMap.createFilterChain(req, servletConfig, servlet);
            
            try {
                if (chain == null) {
                    if (servlet != null) {
                        servlet.service(req, res);
                    } else {
                        System.err.println("No servlet " + req.getRequestURI());
                        res.sendError(404);
                    }
                } else {
                    chain.doFilter(req, res);
                }
            } catch(UnavailableException ex) {
                servletConfig.unavailable(ex);
                handleUnavailable(res, servletConfig);
                return;
            }
            
            // servlet completed without exception. Check status
            int status = res.getStatus();
            if (status != 200 && !res.isCommitted()) {
                String statusPage = ctx.findStatusPage(status);

                if (statusPage != null) {
                    ctx.handleStatusPage(req, res, status, statusPage);
                } else {
                    // Send a default message body.
                    // TODO: maybe other mechanism to customize default.
                    res.defaultStatusPage(status, res.getMessage());
                }
            }
        } catch (Throwable t) {
            ctx.handleError(req, res, t);
        } finally {
            if (servlet != null) {
                servletConfig.deallocate(servlet);
            }
        }
    }

    private void handleUnavailable(ServletResponseImpl response, 
                                   ServletConfigImpl servletConfig) 
            throws IOException {
        long available = servletConfig.getAvailable();
        if ((available > 0L) && (available < Long.MAX_VALUE))
            response.setDateHeader("Retry-After", available);
        //  TODO: handle via error pages !
        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, 
                "Service unavailable");
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
     * Init
     * 
     * @throws ServletException
     * @throws IOException
     */
    public void init() throws ServletException, IOException {
      getObjectManager().bind("TomcatLite", this);
      if (contexts.size() == 0) {
        setContext("/:./webapps/ROOT");
      }
      getConnector().setObjectManager(getObjectManager());
      Iterator i1 = contexts.iterator();
      while (i1.hasNext()) {
        ServletContextImpl ctx = (ServletContextImpl) i1.next();
        try {
          ctx.init();
        } catch (Throwable e) {
          e.printStackTrace();
        }
      }
    }
    
    /**
     * Initialize an webapp and add it to the server. 
     * - load web.xml
     * - call 
     * 
     * @param rootDir
     * @param path
     * @param deployServlet
     */
    public void init(String rootDir, String path) 
            throws ServletException, IOException {
        
        long t0 = System.currentTimeMillis();

        ServletContextImpl ctx = 
            (ServletContextImpl)addServletContext(null, 
                                                  rootDir, 
                                                  path);
        ctx.init();
        
        long t1 = System.currentTimeMillis();
        
        // At this point all config is loaded. Contexts are not yet init()
        // - this will happen on start.
        System.err.println("Context.init() " + path + " " + (t1-t0));
    }

    /** 
     * Get an empty request/response pair ( response available 
     * as getResponse() ). Optional set input and output buffers.
     * 
     * This can be used for a connector-less interface to tomcat lite.
     * 
     * TODO: make it independent of coyote !
     */
    
    public  ServletRequestImpl createMessage() {
      ServletRequestImpl req = new ServletRequestImpl();
      ServletResponseImpl res = req.getResponse();
      
      getConnector().initRequest(req, res);
      
      req.getHttpRequest().method().setString("GET");
      req.getHttpRequest().protocol().setString("HTTP/1.1");
      
      return req;
    }
    
    /**
     * Used internally for mapping. 
     */
    private ServletRequestImpl createMessage(ServletContextImpl deployCtx,
                                            String ctxPath,
                                            String reqPath) {
      ServletRequestImpl req = createMessage();
      req.setContextPath(ctxPath);
      req.setContext(deployCtx);
      req.setRequestURI(ctxPath + reqPath);
      return req;
    }
    

    /** 
     * Set a global filter that will be used for context mapping.
     * 
     * The filter will get a request, with requestUri and hostname set.
     *  
     * It needs to compute the context path and set it as an attribute.
     * 
     * Advanced features may include on-demand loading of webapps, large scale
     * virtual hosting, etc.
     */
    public void setContextMapper(Filter hostMapper2) {
        this.hostMapper = hostMapper2;
    }

    public void endRequest(ServletRequestImpl req,
                           ServletResponseImpl res) throws IOException {
     res.outputBuffer.flush();
     req.getConnector().finishResponse(res);
    }
    
    public Connector getConnector() {
        if (connector == null) {
            connector = (Connector) getObjectManager().get(Connector.class);
            setConnector(connector);
        }
        return connector;
    }

    public void setConnector(Connector c) {
        connector = c;
        connector.setTomcatLite(this);
        getObjectManager().bind("Connector", connector);
    }

    
    public void setDaemon(boolean d) {
        getConnector();
        if (connector != null) {
            connector.setDaemon(d);
        }
    }

    public void startConnector() throws IOException {
        getConnector();
        if (connector != null) {
            connector.start();
        }
    }

    public void stopConnector() throws Exception {
        if (connector != null) {
            connector.stop();
        }
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
}
