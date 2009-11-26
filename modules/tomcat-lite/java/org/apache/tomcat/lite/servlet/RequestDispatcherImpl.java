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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.lite.http.MappingData;
import org.apache.tomcat.lite.io.CBuffer;


/**
 *
 */
public final class RequestDispatcherImpl implements RequestDispatcher {
    /**
     * The request attribute under which the original servlet path is stored
     * on an forwarded dispatcher request.
     */
    public static final String FORWARD_SERVLET_PATH_ATTR =
        "javax.servlet.forward.servlet_path";


    /**
     * The request attribute under which the original query string is stored
     * on an forwarded dispatcher request.
     */
    public static final String FORWARD_QUERY_STRING_ATTR =
        "javax.servlet.forward.query_string";

    /**
     * The request attribute under which the original request URI is stored
     * on an forwarded dispatcher request.
     */
    public static final String FORWARD_REQUEST_URI_ATTR =
        "javax.servlet.forward.request_uri";
    
    
    /**
     * The request attribute under which the original context path is stored
     * on an forwarded dispatcher request.
     */
    public static final String FORWARD_CONTEXT_PATH_ATTR =
        "javax.servlet.forward.context_path";


    /**
     * The request attribute under which the original path info is stored
     * on an forwarded dispatcher request.
     */
    public static final String FORWARD_PATH_INFO_ATTR =
        "javax.servlet.forward.path_info";

    /**
     * The request attribute under which we store the servlet name on a
     * named dispatcher request.
     */
    public static final String NAMED_DISPATCHER_ATTR =
        "org.apache.catalina.NAMED";

    /**
     * The request attribute under which the request URI of the included
     * servlet is stored on an included dispatcher request.
     */
    public static final String INCLUDE_REQUEST_URI_ATTR =
        "javax.servlet.include.request_uri";


    /**
     * The request attribute under which the context path of the included
     * servlet is stored on an included dispatcher request.
     */
    public static final String INCLUDE_CONTEXT_PATH_ATTR =
        "javax.servlet.include.context_path";


    /**
     * The request attribute under which the path info of the included
     * servlet is stored on an included dispatcher request.
     */
    public static final String INCLUDE_PATH_INFO_ATTR =
        "javax.servlet.include.path_info";


    /**
     * The request attribute under which the servlet path of the included
     * servlet is stored on an included dispatcher request.
     */
    public static final String INCLUDE_SERVLET_PATH_ATTR =
        "javax.servlet.include.servlet_path";


    /**
     * The request attribute under which the query string of the included
     * servlet is stored on an included dispatcher request.
     */
    public static final String INCLUDE_QUERY_STRING_ATTR =
        "javax.servlet.include.query_string";

    /**
     * The request attribute under which we expose the value of the
     * <code>&lt;jsp-file&gt;</code> value associated with this servlet,
     * if any.
     */
    public static final String JSP_FILE_ATTR =
        "org.apache.catalina.jsp_file";


    // ----------------------------------------------------- Instance Variables

    private static Logger log = Logger.getLogger(RequestDispatcherImpl.class.getName());

    private ServletContextImpl ctx = null;

    /**
     * The servlet name for a named dispatcher.
     */
    private String name = null;

    // Path for a path dispatcher
    private String path;

    /**
     * MappingData object - per thread for buffering.
     */
    private transient ThreadLocal<MappingData> localMappingData = 
        new ThreadLocal<MappingData>();

    /*
      OrigRequest(ServletRequestImpl) -> include/forward * -> this include
      
      On the path: user-defined RequestWrapper or our ServletRequestWrapper

      include() is called with a RequestWrapper(->...->origRequest) or origRequest
      
      Based on params, etc -> we wrap the req / response in ServletRequestWrapper,
       call filters+servlet. Inside, the req can be wrapped again in 
       userReqWrapper, and other include called. 
       
      
     */
    
    /**
     * The outermost request that will be passed on to the invoked servlet.
     */
    private ServletRequest outerRequest = null;

    /**
     * The outermost response that will be passed on to the invoked servlet.
     */
    private ServletResponse outerResponse = null;

    /**
     * The request wrapper we have created and installed (if any).
     */
    private ServletRequest wrapRequest = null;

    /**
     * The response wrapper we have created and installed (if any).
     */
    private ServletResponse wrapResponse = null;

    // Parameters used when constructing the dispatcvher 
    /**
     * The extra path information for this RequestDispatcher.
     */
    private String pathInfo = null;
    /**
     * The query string parameters for this RequestDispatcher.
     */
    private String queryString = null;
    /**
     * The request URI for this RequestDispatcher.
     */
    private String requestURI = null;
    /**
     * The servlet path for this RequestDispatcher.
     */
    private String servletPath = null;

    //
    private String origServletPath = null;
    
    /**
     * The Wrapper associated with the resource that will be forwarded to
     * or included.
     */
    private ServletConfigImpl wrapper = null;
    
    private Servlet servlet; 

    /** Named dispatcher 
     */
    public RequestDispatcherImpl(ServletConfigImpl wrapper, String name) {
        this.wrapper = wrapper;
        this.name = name;
        this.ctx = (ServletContextImpl) wrapper.getServletContext();

    }
    
    public RequestDispatcherImpl(ServletContextImpl ctx, String path) {
        this.path = path;
        this.ctx = ctx;
    }
   


    /**
     * Forward this request and response to another resource for processing.
     * Any runtime exception, IOException, or ServletException thrown by the
     * called servlet will be propogated to the caller.
     *
     * @param request The servlet request to be forwarded
     * @param response The servlet response to be forwarded
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet exception occurs
     */
    public void forward(ServletRequest request, ServletResponse response)
        throws ServletException, IOException
    {
        // Reset any output that has been buffered, but keep headers/cookies
        if (response.isCommitted()) {
            throw new IllegalStateException("forward(): response.isComitted()");
        }
        
        try {
            response.resetBuffer();
        } catch (IllegalStateException e) {
            throw e;
        }

        // Set up to handle the specified request and response
        setup(request, response, false);

        // Identify the HTTP-specific request and response objects (if any)
        HttpServletRequest hrequest = (HttpServletRequest) request;

        ServletRequestWrapperImpl wrequest =
            (ServletRequestWrapperImpl) wrapRequest();

        
        if (name != null) {
            wrequest.setRequestURI(hrequest.getRequestURI());
            wrequest.setContextPath(hrequest.getContextPath());
            wrequest.setServletPath(hrequest.getServletPath());
            wrequest.setPathInfo(hrequest.getPathInfo());
            wrequest.setQueryString(hrequest.getQueryString());

            
        } else { // path based
            mapPath();
            if (wrapper == null) {
                throw new ServletException("Forward not found " + 
                        path);
            }
            String contextPath = ctx.getContextPath();
            if (hrequest.getAttribute(FORWARD_REQUEST_URI_ATTR) == null) {
                wrequest.setAttribute(FORWARD_REQUEST_URI_ATTR,
                                      hrequest.getRequestURI());
                wrequest.setAttribute(FORWARD_CONTEXT_PATH_ATTR,
                                      hrequest.getContextPath());
                wrequest.setAttribute(FORWARD_SERVLET_PATH_ATTR,
                                      hrequest.getServletPath());
                wrequest.setAttribute(FORWARD_PATH_INFO_ATTR,
                                      hrequest.getPathInfo());
                wrequest.setAttribute(FORWARD_QUERY_STRING_ATTR,
                                      hrequest.getQueryString());
            }
 
            wrequest.setContextPath(contextPath);
            wrequest.setRequestURI(requestURI);
            wrequest.setServletPath(servletPath);
            wrequest.setPathInfo(pathInfo);
            if (queryString != null) {
                wrequest.setQueryString(queryString);
                wrequest.setQueryParams(queryString);
            }
        }
        processRequest(outerRequest, outerResponse);

        wrequest.recycle();
        unwrapRequest();

        // This is not a real close in order to support error processing
//        if ( log.isDebugEnabled() )
//            log.debug(" Disabling the response for futher output");

        if  (response instanceof ServletResponseImpl) {
            ((ServletResponseImpl) response).flushBuffer();
            ((ServletResponseImpl) response).setSuspended(true);
        } else {
            // Servlet SRV.6.2.2. The Resquest/Response may have been wrapped
            // and may no longer be instance of RequestFacade 
            if (log.isLoggable(Level.FINE)){
                log.fine( " The Response is vehiculed using a wrapper: " 
                           + response.getClass().getName() );
            }

            // Close anyway
            try {
                PrintWriter writer = response.getWriter();
                writer.close();
            } catch (IllegalStateException e) {
                try {
                    ServletOutputStream stream = response.getOutputStream();
                    stream.close();
                } catch (IllegalStateException f) {
                    ;
                } catch (IOException f) {
                    ;
                }
            } catch (IOException e) {
                ;
            }
        }
    }

    
    
    /**
     * Include the response from another resource in the current response.
     * Any runtime exception, IOException, or ServletException thrown by the
     * called servlet will be propogated to the caller.
     *
     * @param request The servlet request that is including this one
     * @param response The servlet response to be appended to
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet exception occurs
     */
    public void include(ServletRequest request, ServletResponse response)
        throws ServletException, IOException
    {

        // Set up to handle the specified request and response
        setup(request, response, true);

        // Create a wrapped response to use for this request
        // this actually gets inserted somewhere in the chain - it's not 
        // the last one, but first non-user response
        wrapResponse();
        ServletRequestWrapperImpl wrequest =
            (ServletRequestWrapperImpl) wrapRequest();


        // Handle an HTTP named dispatcher include
        if (name != null) {
            wrequest.setAttribute(NAMED_DISPATCHER_ATTR, name);
            if (servletPath != null) wrequest.setServletPath(servletPath);
            wrequest.setAttribute(WebappFilterMapper.DISPATCHER_TYPE_ATTR,
                                  new Integer(WebappFilterMapper.INCLUDE));
            wrequest.setAttribute(WebappFilterMapper.DISPATCHER_REQUEST_PATH_ATTR, 
                                  origServletPath);
        } else {
            mapPath();
            String contextPath = ctx.getContextPath();
            if (requestURI != null)
                wrequest.setAttribute(INCLUDE_REQUEST_URI_ATTR,
                                      requestURI);
            if (contextPath != null)
                wrequest.setAttribute(INCLUDE_CONTEXT_PATH_ATTR,
                                      contextPath);
            if (servletPath != null)
                wrequest.setAttribute(INCLUDE_SERVLET_PATH_ATTR,
                                      servletPath);
            if (pathInfo != null)
                wrequest.setAttribute(INCLUDE_PATH_INFO_ATTR,
                                      pathInfo);
            if (queryString != null) {
                wrequest.setAttribute(INCLUDE_QUERY_STRING_ATTR,
                                      queryString);
                wrequest.setQueryParams(queryString);
            }
            
            wrequest.setAttribute(WebappFilterMapper.DISPATCHER_TYPE_ATTR,
                                  new Integer(WebappFilterMapper.INCLUDE));
            wrequest.setAttribute(WebappFilterMapper.DISPATCHER_REQUEST_PATH_ATTR, 
                                  origServletPath);
        }

        invoke(outerRequest, outerResponse);

        wrequest.recycle();
        unwrapRequest();
        unwrapResponse();
    }


    // -------------------------------------------------------- Private Methods

    public void mapPath() {
        if (path == null || servletPath != null) return;
        
        // Retrieve the thread local URI, used for mapping
        // TODO: recycle RequestDispatcher stack and associated objects
        // instead of this object
        
        // Retrieve the thread local mapping data
        MappingData mappingData = (MappingData) localMappingData.get();
        if (mappingData == null) {
            mappingData = new MappingData();
            localMappingData.set(mappingData);
        }

        // Get query string
        int pos = path.indexOf('?');
        if (pos >= 0) {
            queryString = path.substring(pos + 1);
        } else {
            pos = path.length();
        }
 
        // Map the URI
        CBuffer uriMB = CBuffer.newInstance();
        //mappingData.localURIBytes;
        uriMB.recycle();
        //CharChunk uriCC = uriMB.getCharChunk();
        try {
            /*
             * Ignore any trailing path params (separated by ';') for mapping
             * purposes.
             * This is sometimes broken - path params can be on any path 
             * component, not just last.
             */
            int semicolon = path.indexOf(';');
            if (pos >= 0 && semicolon > pos) {
                semicolon = -1;
            }
            if (ctx.getContextPath().length() > 1 ) {
                uriMB.append(ctx.getContextPath());
            }
            uriMB.append(path, 0, 
                semicolon > 0 ? semicolon : pos);

            // TODO: make charBuffer part of request or something
            ctx.getEngine().getDispatcher().map(ctx.getContextMap(), uriMB, mappingData);
            
            // at least default wrapper must be returned
            
//            /*
//             * Append any trailing path params (separated by ';') that were
//             * ignored for mapping purposes, so that they're reflected in the
//             * RequestDispatcher's requestURI
//             */
//            if (semicolon > 0) {
//                // I don't think this will be used in future
//                charBuffer.append(path, 
//                    semicolon, pos - semicolon);
//            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "getRequestDispatcher()", e);
        }

        wrapper = (ServletConfigImpl) mappingData.getServiceObject();
        servletPath = mappingData.wrapperPath.toString();
        pathInfo = mappingData.pathInfo.toString();

        mappingData.recycle();

    }
    

    /**
     * Prepare the request based on the filter configuration.
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    private void processRequest(ServletRequest request, 
                                ServletResponse response)
            throws IOException, ServletException {
        Integer disInt = 
            (Integer) request.getAttribute(WebappFilterMapper.DISPATCHER_TYPE_ATTR);
        if (disInt != null) {
            if (disInt.intValue() != WebappFilterMapper.ERROR) {
                outerRequest.setAttribute
                    (WebappFilterMapper.DISPATCHER_REQUEST_PATH_ATTR,
                     origServletPath);
                outerRequest.setAttribute
                    (WebappFilterMapper.DISPATCHER_TYPE_ATTR,
                     new Integer(WebappFilterMapper.FORWARD));
            }
            invoke(outerRequest, response);
        }

    }
    
    
    

    /**
     * Ask the resource represented by this RequestDispatcher to process
     * the associated request, and create (or append to) the associated
     * response.
     * <p>
     * <strong>IMPLEMENTATION NOTE</strong>: This implementation assumes
     * that no filters are applied to a forwarded or included resource,
     * because they were already done for the original request.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    private void invoke(ServletRequest request, ServletResponse response)
            throws IOException, ServletException {

        // Checking to see if the context classloader is the current context
        // classloader. If it's not, we're saving it, and setting the context
        // classloader to the Context classloader
        ClassLoader oldCCL = Thread.currentThread().getContextClassLoader();
        ClassLoader contextClassLoader = ctx.getClassLoader();

        if (oldCCL != contextClassLoader) {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        } else {
            oldCCL = null;
        }

        // Initialize local variables we may need
        HttpServletResponse hresponse = (HttpServletResponse) response;
        IOException ioException = null;
        ServletException servletException = null;
        RuntimeException runtimeException = null;
        
        servletException = allocateServlet(hresponse, servletException);
                
        // Get the FilterChain Here
        WebappFilterMapper factory = 
            ((ServletContextImpl)wrapper.getServletContext()).getFilterMapper();
        
        FilterChainImpl filterChain = factory.createFilterChain(request,
                                                                wrapper,
                                                                servlet);

        // Call the service() method for the allocated servlet instance
        try {
            String jspFile = wrapper.getJspFile();
            if (jspFile != null)
                request.setAttribute(JSP_FILE_ATTR, jspFile);
            else
                request.removeAttribute(JSP_FILE_ATTR);
            // for includes/forwards
            if ((servlet != null) && (filterChain != null)) {
               filterChain.doFilter(request, response);
             }
        } catch (IOException e) {
            ctx.getLogger().log(Level.WARNING, "RequestDispatcherImpl error " + 
                    wrapper.getServletName(), e);
            ioException = e;
        } catch (UnavailableException e) {
            ctx.getLogger().log(Level.WARNING, "RequestDispatcherImpl error " + 
                    wrapper.getServletName(), e);
            servletException = e;
            wrapper.unavailable(e);
        } catch (ServletException e) {
            servletException = e;
        } catch (RuntimeException e) {
            ctx.getLogger().log(Level.WARNING, "RequestDispatcherImpl error " + 
                    wrapper.getServletName(), e);
            runtimeException = e;
        }
        request.removeAttribute(JSP_FILE_ATTR);

        // Release the filter chain (if any) for this request
        if (filterChain != null)
            filterChain.release();

        servletException = servletDealocate(servletException);

        // Reset the old context class loader
        if (oldCCL != null)
            Thread.currentThread().setContextClassLoader(oldCCL);
        
        // Unwrap request/response if needed
        unwrapRequest();
        unwrapResponse();

        // Rethrow an exception if one was thrown by the invoked servlet
        if (ioException != null)
            throw ioException;
        if (servletException != null)
            throw servletException;
        if (runtimeException != null)
            throw runtimeException;

    }

    private ServletException servletDealocate(ServletException servletException)
    {
        if (servlet != null) {
            wrapper.deallocate(servlet);
        }
        return servletException;
    }

    private ServletException allocateServlet(HttpServletResponse hresponse,
                                             ServletException servletException)
        throws IOException
    {
        boolean unavailable = false;

        // Check for the servlet being marked unavailable
        if (wrapper.isUnavailable()) {
            ctx.getLogger().log(Level.WARNING, "isUnavailable() " + wrapper.getServletName());
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE))
                hresponse.setDateHeader("Retry-After", available);
            hresponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, 
                    "Unavailable"); // No need to include internal info: wrapper.getServletName();
            unavailable = true;
        }

        // Allocate a servlet instance to process this request
        try {
            if (!unavailable) {
                servlet = wrapper.allocate();
            }
        } catch (ServletException e) {
            ctx.getLogger().log(Level.WARNING, "RequestDispatcher: allocate " + 
                             wrapper.toString());
            servletException = e;
            servlet = null;
        } catch (Throwable e) {
            ctx.getLogger().log(Level.WARNING, "allocate() error " + wrapper.getServletName(), e);
            servletException = new ServletException
                ("Allocate error " + wrapper.getServletName(), e);
            servlet = null;
        }
        return servletException;
    }


    /**
     * Set up to handle the specified request and response
     *
     * @param request The servlet request specified by the caller
     * @param response The servlet response specified by the caller
     * @param including Are we performing an include() as opposed to
     *  a forward()?
     */
    private void setup(ServletRequest request, ServletResponse response,
                       boolean including) {

        this.outerRequest = request;
        this.outerResponse = response;
    }


    /**
     * Unwrap the request if we have wrapped it. Not sure how it could end
     * up in the middle. 
     */
    private void unwrapRequest() {
        if (wrapRequest == null)
            return;

        ServletRequest previous = null;
        ServletRequest current = outerRequest;
        while (current != null) {
            // If we run into the container request we are done
            if (current instanceof ServletRequestImpl)
                break;

            // Remove the current request if it is our wrapper
            if (current == wrapRequest) {
                ServletRequest next =
                  ((ServletRequestWrapper) current).getRequest();
                if (previous == null)
                    outerRequest = next;
                else
                    ((ServletRequestWrapper) previous).setRequest(next);
                break;
            }

            // Advance to the next request in the chain
            previous = current;
            current = ((ServletRequestWrapper) current).getRequest();
        }
    }


    /**
     * Unwrap the response if we have wrapped it.
     */
    private void unwrapResponse() {
        if (wrapResponse == null)
            return;

        ServletResponse previous = null;
        ServletResponse current = outerResponse;
        while (current != null) {
            // If we run into the container response we are done
            if (current instanceof ServletResponseImpl)
                break;

            // Remove the current response if it is our wrapper
            if (current == wrapResponse) {
                ServletResponse next =
                  ((ServletResponseWrapper) current).getResponse();
                if (previous == null)
                    outerResponse = next;
                else
                    ((ServletResponseWrapper) previous).setResponse(next);
                break;
            }
            // Advance to the next response in the chain
            previous = current;
            current = ((ServletResponseWrapper) current).getResponse();
        }
    }


    /**
     * Create and return a request wrapper that has been inserted in the
     * appropriate spot in the request chain.
     */
    private ServletRequest wrapRequest() {
        // Locate the request we should insert in front of
        ServletRequest previous = null;
        ServletRequest current = outerRequest;
        while (current != null) {
            if (!(current instanceof ServletRequestWrapper))
                break;
            if (current instanceof ServletRequestWrapperImpl)
                break;
            if (current instanceof ServletRequestImpl)
                break;
            // user-specified
            previous = current;
            current = ((ServletRequestWrapper) current).getRequest();
        }
        // now previous will be a user-specified wrapper, 
        // and current one of our own wrappers ( deeper in stack )
        // ... current USER_previous USER USER
        // previous is null if the top request is ours.

        // Instantiate a new wrapper at this point and insert it in the chain
        ServletRequest wrapper = null;
        
        // Compute a crossContext flag
        boolean crossContext = isCrossContext();
        wrapper = 
            new ServletRequestWrapperImpl((HttpServletRequest) current, 
                                          ctx, crossContext);

        if (previous == null) {
            // outer becomes the wrapper, includes orig wrapper inside
            outerRequest = wrapper;
        } else {
            // outer remains user-specified sersvlet, delegating to 
            // our wrapper, which delegates to real request or our wrapper.
            ((ServletRequestWrapper) previous).setRequest(wrapper);
        }
        wrapRequest = wrapper;
        return (wrapper);
    }

    private boolean isCrossContext() {
        boolean crossContext = false;
        if ((outerRequest instanceof ServletRequestWrapperImpl) ||
                (outerRequest instanceof ServletRequestImpl) ||
                (outerRequest instanceof HttpServletRequest)) {
            HttpServletRequest houterRequest = 
                (HttpServletRequest) outerRequest;
            Object contextPath = 
                houterRequest.getAttribute(INCLUDE_CONTEXT_PATH_ATTR);
            if (contextPath == null) {
                // Forward
                contextPath = houterRequest.getContextPath();
            }
            crossContext = !(ctx.getContextPath().equals(contextPath));
        }
        return crossContext;
    }


    /**
     * Create and return a response wrapper that has been inserted in the
     * appropriate spot in the response chain.
     * 
     * Side effect: updates outerResponse, wrapResponse.
     * The chain is updated with a wrapper below lowest user wrapper
     */
    private ServletResponse wrapResponse() {
        // Locate the response we should insert in front of
        ServletResponse previous = null;
        ServletResponse current = outerResponse;
        while (current != null) {
            if (!(current instanceof ServletResponseWrapper))
                break;
            if (current instanceof ServletResponseImpl)
                break;
            previous = current;
            current = ((ServletResponseWrapper) current).getResponse();
        }

        // Instantiate a new wrapper at this point and insert it in the chain
        ServletResponse wrapper = 
             new ServletResponseIncludeWrapper(current);
            
        if (previous == null) {
            // outer is ours, we can wrap on top
            outerResponse = wrapper;
        } else {
            // outer is user-specified, leave it alone. 
            // we insert ourself below the lowest user-specified response
            ((ServletResponseWrapper) previous).setResponse(wrapper);
        }
        wrapResponse = wrapper;
        return (wrapper);

    }


}
