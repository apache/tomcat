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
package org.apache.catalina.servlets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.WebResource;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.util.DOMWriter;
import org.apache.catalina.util.IOTools;
import org.apache.catalina.util.XMLWriter;
import org.apache.tomcat.PeriodicEventListener;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.http.ConcurrentDateFormat;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.RequestUtil;
import org.apache.tomcat.util.http.WebdavIfHeader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This servlet adds support for <a href="https://tools.ietf.org/html/rfc4918">WebDAV</a>
 * <a href="https://tools.ietf.org/html/rfc4918#section-18">level 3</a>. All the basic HTTP requests are handled by the
 * DefaultServlet.
 * <p>
 * The WebDAV servlet is only designed for use with path mapping. The WebdavServlet must not be used as the default
 * servlet (i.e. mapped to '/') or with any other mapping types as it will not work in those configurations.
 * <p>
 * By default, the entire web application is exposed via the WebDAV servlet. Mapping the WebDAV servlet to
 * <code>/*</code> provides WebDAV access to all the resources within the web application. To aid separation of normal
 * users and WebDAV users, the WebDAV servlet may be mounted at a sub-path (e.g. <code>/webdav/*</code>) which creates
 * an additional mapping for the entire web application under that sub-path, with WebDAV access to all the resources.
 * <p>
 * By default, the <code>WEB-INF</code> and <code>META-INF</code> directories are not accessible via WebDAV. This may
 * be changed by setting the <code>allowSpecialPaths</code> initialisation parameter to <code>true</code>.
 * <p>
 * It is also possible to enable WebDAV access to a sub-set of the standard web application URL space rather than
 * creating an additional, WebDAV specific mapping. To do this, map the WebDAV servlet to the desired sub-path and set
 * the <code>serveSubpathOnly</code> initialisation parameter to <code>true</code>.
 * <p>
 * Security constraints using the same URL pattern as the mapping (e.g. <code>/webdav/*</code>) can be used to limit the
 * users with access to WebDAV functionality. Care is required if using security constraints to further limit WebDAV
 * functionality. In particular, administrators should be aware that security constraints apply only to the request URL.
 * Security constraints do not apply to any destination URL associated with the WebDAV operation (such as COPY or MOVE).
 * <p>
 * To enable WebDAV for a context add the following to web.xml:
 *
 * <pre>
 * &lt;servlet&gt;
 *  &lt;servlet-name&gt;webdav&lt;/servlet-name&gt;
 *  &lt;servlet-class&gt;org.apache.catalina.servlets.WebdavServlet&lt;/servlet-class&gt;
 *    &lt;init-param&gt;
 *      &lt;param-name&gt;debug&lt;/param-name&gt;
 *      &lt;param-value&gt;0&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *      &lt;param-name&gt;listings&lt;/param-name&gt;
 *      &lt;param-value&gt;true&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *  &lt;/servlet&gt;
 *  &lt;servlet-mapping&gt;
 *    &lt;servlet-name&gt;webdav&lt;/servlet-name&gt;
 *    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *  &lt;/servlet-mapping&gt;
 * </pre>
 *
 * This will enable read only access with folder listings enabled. To enable read-write access add:
 *
 * <pre>
 *  &lt;init-param&gt;
 *    &lt;param-name&gt;readonly&lt;/param-name&gt;
 *    &lt;param-value&gt;false&lt;/param-value&gt;
 *  &lt;/init-param&gt;
 * </pre>
 *
 * To make the content editable via a different URL, use the following mapping:
 *
 * <pre>
 *  &lt;servlet-mapping&gt;
 *    &lt;servlet-name&gt;webdav&lt;/servlet-name&gt;
 *    &lt;url-pattern&gt;/webdavedit/*&lt;/url-pattern&gt;
 *  &lt;/servlet-mapping&gt;
 * </pre>
 *
 * By default access to /WEB-INF and META-INF are not available via WebDAV. To enable access to these URLs, add:
 *
 * <pre>
 *  &lt;init-param&gt;
 *    &lt;param-name&gt;allowSpecialPaths&lt;/param-name&gt;
 *    &lt;param-value&gt;true&lt;/param-value&gt;
 *  &lt;/init-param&gt;
 * </pre>
 *
 * Don't forget to secure access appropriately to the editing URLs, especially if allowSpecialPaths is used. With the
 * mapping configuration above, the context will be accessible to normal users as before. Those users with the necessary
 * access will be able to edit content available via http://host:port/context/content using
 * http://host:port/context/webdavedit/content
 * <p>
 * The Servlet provides support for arbitrary dead properties on all resources (dead properties are properties whose
 * values are not protected by the server, such as the content length of a resource). By default the Servlet will use
 * non persistent memory storage for them. Persistence can be achieved by implementing the <code>PropertyStore</code>
 * interface and configuring the Servlet to use that store. The <code>propertyStore</code> init-param allows configuring
 * the class name of the store to use, while the parameters in the form of <code>store.xxx</code> will be set on the
 * store object as bean properties. For example, this would configure a store with class
 * <code>com.MyPropertyStore</code>, and set its property <code>myName</code> to value <code>myValue</code>:
 *
 * <pre>
 *  &lt;init-param&gt;
 *    &lt;param-name&gt;propertyStore&lt;/param-name&gt;
 *    &lt;param-value&gt;com.MyPropertyStore&lt;/param-value&gt;
 *  &lt;/init-param&gt;
 *  &lt;init-param&gt;
 *    &lt;param-name&gt;store.myName&lt;/param-name&gt;
 *    &lt;param-value&gt;myValue&lt;/param-value&gt;
 *  &lt;/init-param&gt;
 * </pre>
 * <p>
 *
 * @see <a href="https://tools.ietf.org/html/rfc4918">RFC 4918</a>
 */
public class WebdavServlet extends DefaultServlet implements PeriodicEventListener {

    private static final long serialVersionUID = 1L;


    // -------------------------------------------------------------- Constants

    private static final String METHOD_PROPFIND = "PROPFIND";
    private static final String METHOD_PROPPATCH = "PROPPATCH";
    private static final String METHOD_MKCOL = "MKCOL";
    private static final String METHOD_COPY = "COPY";
    private static final String METHOD_MOVE = "MOVE";
    private static final String METHOD_LOCK = "LOCK";
    private static final String METHOD_UNLOCK = "UNLOCK";


    /**
     * Default lock timeout value.
     */
    private static final int DEFAULT_TIMEOUT = 3600;


    /**
     * Maximum lock timeout.
     */
    private static final int MAX_TIMEOUT = 604800;


    /**
     * Default maximum depth.
     */
    private static final int MAX_DEPTH = 3;


    /**
     * Default namespace.
     */
    protected static final String DEFAULT_NAMESPACE = "DAV:";


    /**
     * Pre generated raw XML for supported locks.
     */
    protected static final String SUPPORTED_LOCKS =
            "\n  <D:lockentry><D:lockscope><D:exclusive/></D:lockscope><D:locktype><D:write/></D:locktype></D:lockentry>\n" +
                    "  <D:lockentry><D:lockscope><D:shared/></D:lockscope><D:locktype><D:write/></D:locktype></D:lockentry>\n";

    /**
     * Simple date format for the creation date ISO representation (partial).
     */
    protected static final ConcurrentDateFormat creationDateFormat =
            new ConcurrentDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US, TimeZone.getTimeZone("GMT"));


    /**
     * Lock scheme used.
     */
    protected static final String LOCK_SCHEME = "urn:uuid:";

    // ----------------------------------------------------- Instance Variables

    /**
     * Repository of all locks, keyed by path.
     */
    private final ConcurrentHashMap<String,LockInfo> resourceLocks = new ConcurrentHashMap<>();


    /**
     * Map of all shared locks, keyed by lock token.
     */
    private final ConcurrentHashMap<String,LockInfo> sharedLocks = new ConcurrentHashMap<>();


    /**
     * Default depth in spec is infinite.
     */
    private int maxDepth = MAX_DEPTH;


    /**
     * Is access allowed via WebDAV to the special paths (/WEB-INF and /META-INF)?
     */
    private boolean allowSpecialPaths = false;


    /**
     * Is the if header processing strict.
     */
    private boolean strictIfProcessing = true;

    /**
     * Serve resources from the mounted subpath only, restoring the behavior of {@code DefaultServlet}.
     */
    private boolean serveSubpathOnly = false;


    /**
     * Property store used for storage of dead properties.
     */
    private PropertyStore store = null;


    // --------------------------------------------------------- Public Methods


    @Override
    public void init() throws ServletException {

        super.init();

        // Validate that the Servlet is only mapped to wildcard mappings
        String servletName = getServletConfig().getServletName();
        ServletRegistration servletRegistration =
                getServletConfig().getServletContext().getServletRegistration(servletName);
        Collection<String> servletMappings = servletRegistration.getMappings();
        for (String mapping : servletMappings) {
            if (!mapping.endsWith("/*")) {
                log(sm.getString("webdavservlet.nonWildcardMapping", mapping));
            }
        }

        if (getServletConfig().getInitParameter("maxDepth") != null) {
            maxDepth = Integer.parseInt(getServletConfig().getInitParameter("maxDepth"));
        }

        if (getServletConfig().getInitParameter("allowSpecialPaths") != null) {
            allowSpecialPaths = Boolean.parseBoolean(getServletConfig().getInitParameter("allowSpecialPaths"));
        }

        if (getServletConfig().getInitParameter("strictIfProcessing") != null) {
            strictIfProcessing = Boolean.parseBoolean(getServletConfig().getInitParameter("strictIfProcessing"));
        }

        if (getServletConfig().getInitParameter("serveSubpathOnly") != null) {
            serveSubpathOnly = Boolean.parseBoolean(getServletConfig().getInitParameter("serveSubpathOnly"));
        }

        String propertyStore = getServletConfig().getInitParameter("propertyStore");
        if (propertyStore != null) {
            try {
                Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(propertyStore);
                store = (PropertyStore) clazz.getConstructor().newInstance();
                // Set init parameters as properties on the store
                Enumeration<String> parameterNames = getServletConfig().getInitParameterNames();
                while (parameterNames.hasMoreElements()) {
                    String parameterName = parameterNames.nextElement();
                    if (parameterName.startsWith("store.")) {
                        StringBuilder actualMethod = new StringBuilder();
                        String parameterValue = getServletConfig().getInitParameter(parameterName);
                        parameterName = parameterName.substring("store.".length());
                        if (!IntrospectionUtils.setProperty(store, parameterName, parameterValue, true, actualMethod)) {
                            log(sm.getString("webdavservlet.noStoreParameter", parameterName, parameterValue));
                        }
                    }
                }
            } catch (Exception e) {
                log(sm.getString("webdavservlet.storeError"), e);
            }
        }
        if (store == null) {
            log(sm.getString("webdavservlet.memorystore"));
            store = new MemoryPropertyStore();
        }
        store.init();
    }


    @Override
    public void destroy() {
        store.destroy();
    }


    @Override
    public void periodicEvent() {
        // Check expiration of all locks
        for (LockInfo currentLock : sharedLocks.values()) {
            if (currentLock.hasExpired()) {
                sharedLocks.remove(currentLock.path);
            }
        }
        for (LockInfo currentLock : resourceLocks.values()) {
            if (currentLock.isExclusive()) {
                if (currentLock.hasExpired()) {
                    resourceLocks.remove(currentLock.path);
                }
            } else {
                for (String token : currentLock.sharedTokens) {
                    if (sharedLocks.get(token) == null) {
                        currentLock.sharedTokens.remove(token);
                    }
                }
                if (currentLock.sharedTokens.isEmpty()) {
                    resourceLocks.remove(currentLock.path);
                }
            }
        }
        store.periodicEvent();
    }


    // ------------------------------------------------ PropertyStore Interface


    /**
     * Handling of dead properties on resources. This interface allows providing storage for dead properties. Store
     * configuration is done through the <code>propertyStore</code> init parameter of the WebDAV Servlet, which should
     * contain the class name of the store.
     */
    public interface PropertyStore {

        /**
         * Initialize the store. This is tied to the Servlet lifecycle and is called by its init method.
         */
        void init();

        /**
         * Destroy the store. This is tied to the Servlet lifecycle and is called by its destroy method.
         */
        void destroy();

        /**
         * Periodic event for maintenance tasks.
         */
        void periodicEvent();

        /**
         * Copy resource. Dead properties should be copied to the destination path.
         *
         * @param source      the copy source path
         * @param destination the copy destination path
         */
        void copy(String source, String destination);

        /**
         * Delete specified resource. Dead properties on a deleted resource should be deleted.
         *
         * @param resource the path of the resource to delete
         */
        void delete(String resource);

        /**
         * Generate propfind XML fragments for dead properties.
         *
         * @param resource     the resource path
         * @param property     the dead property, if null then all dead properties must be written
         * @param nameOnly     true if only the property name element should be generated
         * @param generatedXML the current generated XML for the PROPFIND response
         *
         * @return true if a property was specified and a corresponding dead property was found on the resource, false
         *             otherwise
         */
        boolean propfind(String resource, Node property, boolean nameOnly, XMLWriter generatedXML);

        /**
         * Apply proppatch to the specified resource.
         *
         * @param resource   the resource path on which to apply the proppatch
         * @param operations the set and remove to apply, the final status codes of the result should be set on each
         *                       operation
         */
        void proppatch(String resource, ArrayList<ProppatchOperation> operations);

    }

    // ----------------------------------------- ProppatchOperation Inner Class


    /**
     * Represents a PROPPATCH sub operation to be performed.
     */
    public static class ProppatchOperation {
        private final PropertyUpdateType updateType;
        private final Node propertyNode;
        private final boolean protectedProperty;
        private int statusCode = HttpServletResponse.SC_OK;

        /**
         * PROPPATCH operation constructor.
         *
         * @param updateType   the update type, either SET or REMOVE
         * @param propertyNode the XML node that contains the property name (and value if SET)
         */
        public ProppatchOperation(PropertyUpdateType updateType, Node propertyNode) {
            this.updateType = updateType;
            this.propertyNode = propertyNode;
            String davName = getDAVNode(propertyNode);
            // displayname and getcontentlanguage are the DAV: properties that should not be protected
            protectedProperty =
                    davName != null && (!(davName.equals("displayname") || davName.equals("getcontentlanguage")));
        }

        /**
         * @return the updateType for this operation
         */
        public PropertyUpdateType getUpdateType() {
            return this.updateType;
        }

        /**
         * @return the propertyNode the XML node that contains the property name (and value if SET)
         */
        public Node getPropertyNode() {
            return this.propertyNode;
        }

        /**
         * @return the statusCode the statusCode to set as a result of the operation
         */
        public int getStatusCode() {
            return this.statusCode;
        }

        /**
         * @param statusCode the statusCode to set as a result of the operation
         */
        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        /**
         * @return <code>true</code> if the property is protected
         */
        public boolean getProtectedProperty() {
            return this.protectedProperty;
        }
    }

    /**
     * Type of PROPFIND request.
     */
    enum PropfindType {
        FIND_BY_PROPERTY,
        FIND_ALL_PROP,
        FIND_PROPERTY_NAMES
    }


    /**
     * Type of property update in a PROPPATCH.
     */
    enum PropertyUpdateType {
        SET,
        REMOVE
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Return JAXP document builder instance.
     *
     * @return the document builder
     *
     * @throws ServletException document builder creation failed (wrapped <code>ParserConfigurationException</code>
     *                              exception)
     */
    protected DocumentBuilder getDocumentBuilder() throws ServletException {
        DocumentBuilder documentBuilder = null;
        DocumentBuilderFactory documentBuilderFactory = null;
        try {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilderFactory.setExpandEntityReferences(false);
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
            documentBuilder.setEntityResolver(new WebdavResolver(this.getServletContext()));
        } catch (ParserConfigurationException e) {
            throw new ServletException(sm.getString("webdavservlet.jaxpfailed"));
        }
        return documentBuilder;
    }


    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        final String path = getRelativePath(req);

        // Error page check needs to come before special path check since
        // custom error pages are often located below WEB-INF so they are
        // not directly accessible.
        if (req.getDispatcherType() == DispatcherType.ERROR) {
            doGet(req, resp);
            return;
        }

        // Block access to special subdirectories.
        // DefaultServlet assumes it services resources from the root of the web app
        // and doesn't add any special path protection
        // WebdavServlet remounts the webapp under a new path, so this check is
        // necessary on all methods (including GET).
        if (isSpecialPath(path)) {
            resp.sendError(WebdavStatus.SC_NOT_FOUND);
            return;
        }

        final String method = req.getMethod();

        if (debug > 0) {
            log("[" + method + "] " + path);
        }

        if (method.equals(METHOD_PROPFIND)) {
            doPropfind(req, resp);
        } else if (method.equals(METHOD_PROPPATCH)) {
            doProppatch(req, resp);
        } else if (method.equals(METHOD_MKCOL)) {
            doMkcol(req, resp);
        } else if (method.equals(METHOD_COPY)) {
            doCopy(req, resp);
        } else if (method.equals(METHOD_MOVE)) {
            doMove(req, resp);
        } else if (method.equals(METHOD_LOCK)) {
            doLock(req, resp);
        } else if (method.equals(METHOD_UNLOCK)) {
            doUnlock(req, resp);
        } else {
            // DefaultServlet processing
            super.service(req, resp);
        }
    }


    @Override
    protected boolean checkIfHeaders(HttpServletRequest request, HttpServletResponse response, WebResource resource)
            throws IOException {

        // Skip regular HTTP evaluation for a null resource
        if (resource != null && !super.checkIfHeaders(request, response, resource)) {
            return false;
        }

        // Process the WebDAV If header using Apache Jackrabbit code
        String ifHeaderValue = request.getHeader("If");
        if (ifHeaderValue != null) {
            WebdavIfHeader ifHeader = new WebdavIfHeader(getUriPrefix(request), ifHeaderValue);
            if (!ifHeader.hasValue()) {
                // Allow bad if syntax, will only be used for lock tokens
                return !strictIfProcessing;
            }
            String path = getRelativePath(request);
            // Get all hrefs from the if header
            Iterator<String> hrefs = ifHeader.getResources();

            String currentPath = null;
            String currentHref = null;
            WebResource currentWebResource = null;
            if (hrefs.hasNext()) {
                currentHref = hrefs.next();
                currentPath = getPathFromHref(currentHref, request);
                if (currentPath == null) {
                    // The path was invalid
                    return false;
                }
                currentWebResource = resources.getResource(currentPath);
            } else {
                currentPath = path;
                currentHref = getEncodedPath(path, resource, request);
                currentWebResource = resource;
            }

            // Iterate over all resources
            do {
                boolean exists = currentWebResource != null && currentWebResource.exists();
                String eTag = exists ? generateETag(currentWebResource) : "";

                // Collect all locks active on resource
                ArrayList<String> lockTokens = new ArrayList<>();
                // No lock evaluation for non existing paths in strict mode
                // Problem: when doing a put with a locked parent folder, need to submit a tagged production with
                // the parent path and the token, simply submitting the token in the if would fail the precondition.
                if (!strictIfProcessing || exists) {
                    String parentPath = currentPath;
                    do {
                        LockInfo parentLock = resourceLocks.get(parentPath);
                        if (parentLock != null) {
                            if (parentLock.hasExpired()) {
                                resourceLocks.remove(parentPath);
                            } else {
                                if ((parentPath != currentPath && parentLock.depth > 0) || parentPath == currentPath) {
                                    if (parentLock.isExclusive()) {
                                        lockTokens.add(LOCK_SCHEME + parentLock.token);
                                    } else {
                                        for (String token : parentLock.sharedTokens) {
                                            if (sharedLocks.get(token) == null) {
                                                parentLock.sharedTokens.remove(token);
                                            }
                                        }
                                        if (parentLock.sharedTokens.isEmpty()) {
                                            resourceLocks.remove(parentLock.path);
                                        }
                                        for (String token : parentLock.sharedTokens) {
                                            LockInfo sharedLock = sharedLocks.get(token);
                                            if (sharedLock != null) {
                                                if ((parentPath != currentPath && sharedLock.depth > 0) ||
                                                        parentPath == currentPath) {
                                                    lockTokens.add(LOCK_SCHEME + token);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        int slash = parentPath.lastIndexOf('/');
                        if (slash < 0) {
                            break;
                        }
                        parentPath = parentPath.substring(0, slash);
                    } while (true);
                }

                // Evaluation
                if (ifHeader.matches(currentHref, lockTokens, eTag)) {
                    return true;
                }

                if (hrefs.hasNext()) {
                    currentHref = hrefs.next();
                    currentPath = getPathFromHref(currentHref, request);
                    currentWebResource = resources.getResource(currentPath);
                } else {
                    break;
                }
            } while (true);

            return false;
        }

        return true;
    }


    /**
     * Override the DefaultServlet implementation and only use the PathInfo. If the ServletPath is non-null, it will be
     * because the WebDAV servlet has been mapped to a url other than /* to configure editing at different url than
     * normal viewing.
     *
     * @param request        The servlet request we are processing
     * @param allowEmptyPath Used only to identify a call from DefaultServlet, to avoid removing the trailing slash
     *
     * @return the relative path
     */
    @Override
    protected String getRelativePath(HttpServletRequest request, boolean allowEmptyPath) {
        if (serveSubpathOnly) {
            return super.getRelativePath(request, allowEmptyPath);
        }

        String pathInfo;

        if (request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null) {
            // For includes, get the info from the attributes
            pathInfo = (String) request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
        } else {
            pathInfo = request.getPathInfo();
        }

        StringBuilder result = new StringBuilder();
        if (pathInfo != null) {
            result.append(pathInfo);
        }
        if (result.length() == 0) {
            result.append('/');
        }
        String resultString = result.toString();
        if (!allowEmptyPath && resultString.length() > 1 && resultString.endsWith("/")) {
            resultString = resultString.substring(0, resultString.length() - 1);
        }
        return resultString;
    }


    @Override
    protected String getPathPrefix(final HttpServletRequest request) {
        if (serveSubpathOnly) {
            return super.getPathPrefix(request);
        }

        // Repeat the servlet path (e.g. /webdav/) in the listing path
        String contextPath = request.getContextPath();
        if (request.getServletPath() != null) {
            contextPath = contextPath + request.getServletPath();
        }
        return contextPath;
    }


    @Override
    protected String determineMethodsAllowed(HttpServletRequest req) {

        WebResource resource = resources.getResource(getRelativePath(req));

        // These methods are always allowed. They may return a 404 (not a 405)
        // if the resource does not exist.
        StringBuilder methodsAllowed = new StringBuilder("OPTIONS, GET, POST, HEAD");

        if (!isReadOnly()) {
            methodsAllowed.append(", DELETE");
            if (!resource.isDirectory()) {
                methodsAllowed.append(", PUT");
            }
        }

        // Trace - assume disabled unless we can prove otherwise
        if (req instanceof RequestFacade && ((RequestFacade) req).getAllowTrace()) {
            methodsAllowed.append(", TRACE");
        }

        methodsAllowed.append(", LOCK, UNLOCK, PROPPATCH, COPY, MOVE");

        if (isListings()) {
            methodsAllowed.append(", PROPFIND");
        }

        if (!resource.exists()) {
            methodsAllowed.append(", MKCOL");
        }

        return methodsAllowed.toString();
    }


    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.addHeader("DAV", "1,2,3");
        resp.addHeader("Allow", determineMethodsAllowed(req));
        resp.addHeader("MS-Author-Via", "DAV");
    }


    /**
     * PROPFIND Method.
     *
     * @param req  The Servlet request
     * @param resp The Servlet response
     *
     * @throws ServletException If an error occurs
     * @throws IOException      If an IO error occurs
     */
    protected void doPropfind(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        if (!isListings()) {
            sendNotAllowed(req, resp);
            return;
        }

        String path = getRelativePath(req);

        // Properties which are to be displayed.
        List<Node> properties = new ArrayList<>();
        // Propfind depth
        int depth = maxDepth;
        // Propfind type
        PropfindType type = null;

        String depthStr = req.getHeader("Depth");

        if (depthStr == null) {
            depth = maxDepth;
        } else {
            if (depthStr.equals("0")) {
                depth = 0;
            } else if (depthStr.equals("1")) {
                depth = 1;
            } else if (depthStr.equals("infinity")) {
                depth = maxDepth;
            } else {
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            }
        }

        byte[] body = null;
        try (InputStream is = req.getInputStream(); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            IOTools.flow(is, os);
            body = os.toByteArray();
        } catch (IOException e) {
            resp.sendError(WebdavStatus.SC_BAD_REQUEST);
            return;
        }
        if (body.length > 0) {
            DocumentBuilder documentBuilder = getDocumentBuilder();

            try {
                Document document = documentBuilder.parse(new InputSource(new ByteArrayInputStream(body)));

                // Get the root element of the document
                Element rootElement = document.getDocumentElement();
                if (!"propfind".equals(getDAVNode(rootElement))) {
                    resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                    return;
                }
                NodeList childList = rootElement.getChildNodes();

                for (int i = 0; i < childList.getLength(); i++) {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType()) {
                        case Node.TEXT_NODE:
                            break;
                        case Node.ELEMENT_NODE:
                            String nodeName = getDAVNode(currentNode);
                            if ("prop".equals(nodeName)) {
                                if (type != null) {
                                    // Another was already defined
                                    resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                                    return;
                                }
                                type = PropfindType.FIND_BY_PROPERTY;
                                NodeList propChildList = currentNode.getChildNodes();
                                for (int j = 0; j < propChildList.getLength(); j++) {
                                    Node currentNode2 = propChildList.item(j);
                                    switch (currentNode2.getNodeType()) {
                                        case Node.TEXT_NODE:
                                            break;
                                        case Node.ELEMENT_NODE:
                                            properties.add(currentNode2);
                                            break;
                                    }
                                }
                            }
                            if ("propname".equals(nodeName)) {
                                if (type != null) {
                                    // Another was already defined
                                    resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                                    return;
                                }
                                type = PropfindType.FIND_PROPERTY_NAMES;
                            }
                            if ("allprop".equals(nodeName)) {
                                if (type != null) {
                                    // Another was already defined
                                    resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                                    return;
                                }
                                type = PropfindType.FIND_ALL_PROP;
                            }
                            break;
                    }
                }
            } catch (SAXException | IOException e) {
                // Something went wrong - bad request
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            }
            if (type == null) {
                // Nothing meaningful in the propfind element
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            }
        } else {
            type = PropfindType.FIND_ALL_PROP;
        }

        WebResource resource = resources.getResource(path);
        if (!checkIfHeaders(req, resp, resource)) {
            resp.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }

        if (!resource.exists()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        resp.setStatus(WebdavStatus.SC_MULTI_STATUS);

        resp.setContentType("text/xml; charset=UTF-8");

        // Create multistatus object
        XMLWriter generatedXML = new XMLWriter(resp.getWriter());
        generatedXML.writeXMLHeader();

        generatedXML.writeElement("D", DEFAULT_NAMESPACE, "multistatus", XMLWriter.OPENING);

        if (depth == 0) {
            propfindResource(generatedXML, getEncodedPath(path, resource, req), path, type, properties,
                    resource.isFile(), resource.getCreation(), resource.getLastModified(), resource.getContentLength(),
                    getServletContext().getMimeType(resource.getName()), generateETag(resource));
        } else {
            // The stack always contains the object of the current level
            Deque<String> stack = new ArrayDeque<>();
            stack.addFirst(path);

            // Stack of the objects one level below
            Deque<String> stackBelow = new ArrayDeque<>();

            while ((!stack.isEmpty()) && (depth >= 0)) {

                String currentPath = stack.remove();

                // Exclude any resource in the /WEB-INF and /META-INF subdirectories
                if (isSpecialPath(currentPath)) {
                    continue;
                }

                resource = resources.getResource(currentPath);
                // File is in directory listing but doesn't appear to exist
                // Broken symlink or odd permission settings?
                if (resource.exists()) {
                    propfindResource(generatedXML, getEncodedPath(currentPath, resource, req), currentPath, type,
                            properties, resource.isFile(), resource.getCreation(), resource.getLastModified(),
                            resource.getContentLength(), getServletContext().getMimeType(resource.getName()),
                            generateETag(resource));
                }

                if (resource.isDirectory() && (depth > 0)) {

                    String[] entries = resources.list(currentPath);
                    for (String entry : entries) {
                        String newPath = currentPath;
                        if (!(newPath.endsWith("/"))) {
                            newPath += "/";
                        }
                        newPath += entry;
                        stackBelow.addFirst(newPath);
                    }

                }

                if (stack.isEmpty()) {
                    depth--;
                    stack = stackBelow;
                    stackBelow = new ArrayDeque<>();
                }

                generatedXML.sendData();

            }
        }

        generatedXML.writeElement("D", "multistatus", XMLWriter.CLOSING);

        generatedXML.sendData();

    }


    /**
     * PROPPATCH Method. Dead properties support is a SHOULD in the specification and are not implemented.
     *
     * @param req  The Servlet request
     * @param resp The Servlet response
     *
     * @throws ServletException If an error occurs
     * @throws IOException      If an IO error occurs
     */
    protected void doProppatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String path = getRelativePath(req);

        WebResource resource = resources.getResource(path);
        if (!checkIfHeaders(req, resp, resource)) {
            resp.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }
        if (!resource.exists()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (isReadOnly()) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(path, req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        DocumentBuilder documentBuilder = getDocumentBuilder();
        ArrayList<ProppatchOperation> operations = new ArrayList<>();

        byte[] body = null;
        try (InputStream is = req.getInputStream(); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            IOTools.flow(is, os);
            body = os.toByteArray();
        } catch (IOException e) {
            resp.sendError(WebdavStatus.SC_BAD_REQUEST);
            return;
        }
        if (body.length <= 0) {
            resp.sendError(WebdavStatus.SC_BAD_REQUEST);
            return;
        }
        try {
            Document document = documentBuilder.parse(new InputSource(new ByteArrayInputStream(body)));

            // Get the root element of the document
            Element rootElement = document.getDocumentElement();
            if (!"propertyupdate".equals(getDAVNode(rootElement))) {
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            }
            NodeList childList = rootElement.getChildNodes();

            for (int i = 0; i < childList.getLength(); i++) {
                Node currentNode = childList.item(i);
                switch (currentNode.getNodeType()) {
                    case Node.TEXT_NODE:
                        break;
                    case Node.ELEMENT_NODE:
                        String nodeName = getDAVNode(currentNode);
                        if ("set".equals(nodeName)) {
                            NodeList setChildList = currentNode.getChildNodes();
                            for (int j = 0; j < setChildList.getLength(); j++) {
                                Node currentNode2 = setChildList.item(j);
                                switch (currentNode2.getNodeType()) {
                                    case Node.TEXT_NODE:
                                        break;
                                    case Node.ELEMENT_NODE:
                                        if ("prop".equals(getDAVNode(currentNode2))) {
                                            NodeList propChildList = currentNode2.getChildNodes();
                                            Node property = null;
                                            for (int k = 0; k < propChildList.getLength(); k++) {
                                                Node currentNode3 = propChildList.item(k);
                                                switch (currentNode3.getNodeType()) {
                                                    case Node.TEXT_NODE:
                                                        break;
                                                    case Node.ELEMENT_NODE:
                                                        property = currentNode3;
                                                        break;
                                                }
                                            }
                                            if (property != null) {
                                                operations
                                                        .add(new ProppatchOperation(PropertyUpdateType.SET, property));
                                            } else {
                                                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                                                return;
                                            }
                                        }
                                        break;
                                }
                            }
                        }
                        if ("remove".equals(nodeName)) {
                            NodeList removeChildList = currentNode.getChildNodes();
                            for (int j = 0; j < removeChildList.getLength(); j++) {
                                Node currentNode2 = removeChildList.item(j);
                                switch (currentNode2.getNodeType()) {
                                    case Node.TEXT_NODE:
                                        break;
                                    case Node.ELEMENT_NODE:
                                        if ("prop".equals(getDAVNode(currentNode2))) {
                                            NodeList propChildList = currentNode2.getChildNodes();
                                            Node property = null;
                                            for (int k = 0; k < propChildList.getLength(); k++) {
                                                Node currentNode3 = propChildList.item(k);
                                                switch (currentNode3.getNodeType()) {
                                                    case Node.TEXT_NODE:
                                                        break;
                                                    case Node.ELEMENT_NODE:
                                                        property = currentNode3;
                                                        break;
                                                }
                                            }
                                            if (property != null) {
                                                operations.add(
                                                        new ProppatchOperation(PropertyUpdateType.REMOVE, property));
                                            } else {
                                                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                                                return;
                                            }
                                        }
                                        break;
                                }
                            }
                        }
                        break;
                }
            }
        } catch (SAXException | IOException e) {
            // Something went wrong - bad request
            resp.sendError(WebdavStatus.SC_BAD_REQUEST);
            return;
        }

        store.proppatch(path, operations);

        resp.setStatus(WebdavStatus.SC_MULTI_STATUS);

        resp.setContentType("text/xml; charset=UTF-8");

        // Create multistatus object
        XMLWriter generatedXML = new XMLWriter(resp.getWriter());
        generatedXML.writeXMLHeader();

        generatedXML.writeElement("D", DEFAULT_NAMESPACE, "multistatus", XMLWriter.OPENING);
        generatedXML.writeElement("D", "response", XMLWriter.OPENING);

        // Generating href element
        generatedXML.writeElement("D", "href", XMLWriter.OPENING);
        generatedXML.writeText(getEncodedPath(path, resource, req));
        generatedXML.writeElement("D", "href", XMLWriter.CLOSING);

        for (ProppatchOperation operation : operations) {
            generatedXML.writeElement("D", "propstat", XMLWriter.OPENING);
            generatedXML.writeElement("D", "prop", XMLWriter.OPENING);
            generatedXML.writeElement(operation.propertyNode.getPrefix(), operation.propertyNode.getNamespaceURI(),
                    operation.propertyNode.getLocalName(), XMLWriter.NO_CONTENT);
            generatedXML.writeElement("D", "prop", XMLWriter.CLOSING);
            generatedXML.writeElement("D", "status", XMLWriter.OPENING);
            generatedXML.writeText("HTTP/1.1 " + String.valueOf(operation.getStatusCode()) + " ");
            generatedXML.writeElement("D", "status", XMLWriter.CLOSING);
            if (operation.getProtectedProperty() && operation.getStatusCode() == HttpServletResponse.SC_FORBIDDEN) {
                generatedXML.writeElement("D", "error", XMLWriter.OPENING);
                generatedXML.writeElement("D", "cannot-modify-protected-property", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("D", "error", XMLWriter.CLOSING);
            }
            generatedXML.writeElement("D", "propstat", XMLWriter.CLOSING);
        }

        generatedXML.writeElement("D", "response", XMLWriter.CLOSING);
        generatedXML.writeElement("D", "multistatus", XMLWriter.CLOSING);

        generatedXML.sendData();

    }


    /**
     * MKCOL Method.
     *
     * @param req  The Servlet request
     * @param resp The Servlet response
     *
     * @throws ServletException If an error occurs
     * @throws IOException      If an IO error occurs
     */
    protected void doMkcol(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String path = getRelativePath(req);

        WebResource resource = resources.getResource(path);
        if (!checkIfHeaders(req, resp, resource)) {
            resp.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }

        // Can't create a collection if a resource already exists at the given
        // path
        if (resource.exists()) {
            sendNotAllowed(req, resp);
            return;
        }

        if (isReadOnly()) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(path, req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        if (req.getContentLengthLong() > 0 || "chunked".equalsIgnoreCase(req.getHeader("Transfer-Encoding"))) {
            // No support for MKCOL bodies, which are non standard
            resp.sendError(WebdavStatus.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        if (resources.mkdir(path)) {
            resp.setStatus(WebdavStatus.SC_CREATED);
        } else {
            resp.sendError(WebdavStatus.SC_CONFLICT);
        }
    }


    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        if (isReadOnly()) {
            sendNotAllowed(req, resp);
            return;
        }

        String path = getRelativePath(req);

        deleteResource(path, req, resp);
    }


    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String path = getRelativePath(req);

        WebResource resource = resources.getResource(path);
        if (!checkIfHeaders(req, resp, resource)) {
            resp.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }

        if (isLocked(path, req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        if (resource.isDirectory()) {
            sendNotAllowed(req, resp);
            return;
        }

        super.doPut(req, resp);

    }


    /**
     * COPY Method.
     *
     * @param req  The Servlet request
     * @param resp The Servlet response
     *
     * @throws IOException If an IO error occurs
     */
    protected void doCopy(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        if (isReadOnly()) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        String path = getRelativePath(req);

        copyResource(path, req, resp);
    }


    /**
     * MOVE Method.
     *
     * @param req  The Servlet request
     * @param resp The Servlet response
     *
     * @throws IOException If an IO error occurs
     */
    protected void doMove(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        if (isReadOnly()) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        String path = getRelativePath(req);

        if (isLocked(path, req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        if (copyResource(path, req, resp)) {
            deleteResource(path, req, resp, false);
        }
    }


    /**
     * LOCK Method.
     *
     * @param req  The Servlet request
     * @param resp The Servlet response
     *
     * @throws ServletException If an error occurs
     * @throws IOException      If an IO error occurs
     */
    protected void doLock(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        if (isReadOnly()) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        String path = getRelativePath(req);

        WebResource resource = resources.getResource(path);
        if (!checkIfHeaders(req, resp, resource)) {
            resp.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }

        LockInfo lock = new LockInfo(maxDepth);
        lock.principal = req.getRemoteUser();

        // Parsing lock request

        // Parsing depth header

        String depthStr = req.getHeader("Depth");

        if (depthStr == null) {
            lock.depth = maxDepth;
        } else {
            if (depthStr.equals("0")) {
                lock.depth = 0;
            } else if (depthStr.equals("infinity")) {
                lock.depth = maxDepth;
            } else {
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            }
        }

        // Parsing timeout header

        int lockDuration = DEFAULT_TIMEOUT;
        String lockDurationStr = req.getHeader("Timeout");
        if (lockDurationStr != null) {
            if (lockDurationStr.startsWith("Second-")) {
                try {
                    lockDuration = Integer.parseInt(lockDurationStr.substring("Second-".length()));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            } else if (lockDurationStr.equals("Infinite")) {
                lockDuration = MAX_TIMEOUT;
            }
            if (lockDuration == 0) {
                lockDuration = DEFAULT_TIMEOUT;
            }
            if (lockDuration > MAX_TIMEOUT) {
                lockDuration = MAX_TIMEOUT;
            }
        }
        lock.expiresAt = System.currentTimeMillis() + (lockDuration * 1000);

        boolean lockCreation = false;

        Node lockInfoNode = null;

        byte[] body = null;
        try (InputStream is = req.getInputStream(); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            IOTools.flow(is, os);
            body = os.toByteArray();
        } catch (IOException e) {
            resp.sendError(WebdavStatus.SC_BAD_REQUEST);
            return;
        }
        if (body.length > 0) {
            DocumentBuilder documentBuilder = getDocumentBuilder();

            try {
                Document document = documentBuilder.parse(new InputSource(new ByteArrayInputStream(body)));

                // Get the root element of the document
                Element rootElement = document.getDocumentElement();
                if (!"lockinfo".equals(getDAVNode(rootElement))) {
                    resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                    return;
                }
                lockInfoNode = rootElement;
                lockCreation = true;
            } catch (IOException | SAXException e) {
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            }
        }

        if (lockInfoNode != null) {

            // Reading lock information

            NodeList childList = lockInfoNode.getChildNodes();
            StringWriter strWriter = null;
            DOMWriter domWriter = null;

            Node lockScopeNode = null;
            Node lockTypeNode = null;
            Node lockOwnerNode = null;

            for (int i = 0; i < childList.getLength(); i++) {
                Node currentNode = childList.item(i);
                switch (currentNode.getNodeType()) {
                    case Node.TEXT_NODE:
                        break;
                    case Node.ELEMENT_NODE:
                        if ("lockscope".equals(getDAVNode(currentNode))) {
                            lockScopeNode = currentNode;
                        }
                        if ("locktype".equals(getDAVNode(currentNode))) {
                            lockTypeNode = currentNode;
                        }
                        if ("owner".equals(getDAVNode(currentNode))) {
                            lockOwnerNode = currentNode;
                        }
                        break;
                }
            }

            if (lockScopeNode != null) {

                childList = lockScopeNode.getChildNodes();
                for (int i = 0; i < childList.getLength(); i++) {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType()) {
                        case Node.TEXT_NODE:
                            break;
                        case Node.ELEMENT_NODE:
                            lock.scope = getDAVNode(currentNode);
                            break;
                    }
                }

                if (lock.scope == null) {
                    // Bad request
                    resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                    return;
                }

            } else {
                // Bad request
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            }

            if (lockTypeNode != null) {

                childList = lockTypeNode.getChildNodes();
                for (int i = 0; i < childList.getLength(); i++) {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType()) {
                        case Node.TEXT_NODE:
                            break;
                        case Node.ELEMENT_NODE:
                            lock.type = getDAVNode(currentNode);
                            break;
                    }
                }

                if (lock.type == null) {
                    // Bad request
                    resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                    return;
                }

            } else {
                // Bad request
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            }

            if (lockOwnerNode != null) {

                childList = lockOwnerNode.getChildNodes();
                for (int i = 0; i < childList.getLength(); i++) {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType()) {
                        case Node.TEXT_NODE:
                            lock.owner += currentNode.getNodeValue();
                            break;
                        case Node.ELEMENT_NODE:
                            strWriter = new StringWriter();
                            domWriter = new DOMWriter(strWriter);
                            domWriter.print(currentNode);
                            lock.owner += strWriter.toString();
                            break;
                    }
                }

                if (lock.owner == null) {
                    // Bad request
                    resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                    return;
                }

            } else {
                lock.owner = "";
            }
        }

        lock.path = path;

        if (lockCreation) {

            // Check if the resource or a parent is already locked
            String parentPath = path;
            do {
                LockInfo parentLock = resourceLocks.get(parentPath);
                if (parentLock != null) {
                    if (parentLock.hasExpired()) {
                        resourceLocks.remove(parentPath);
                    } else if (parentLock.isExclusive() || lock.isExclusive()) {
                        // A parent collection of this collection is locked
                        resp.setStatus(WebdavStatus.SC_LOCKED);
                        return;
                    }
                }
                int slash = parentPath.lastIndexOf('/');
                if (slash < 0) {
                    break;
                }
                parentPath = parentPath.substring(0, slash);
            } while (true);

            // Generating lock id
            lock.token = UUID.randomUUID().toString();

            if (resource.isDirectory() && lock.depth == maxDepth) {

                // Locking a collection (and all its member resources)

                // Checking if a child resource of this collection is
                // already locked
                // Note: it is likely faster in many cases to go over the full lock list than trying to go over all the
                // children (recursively)
                List<String> lockPaths = new ArrayList<>();
                for (LockInfo currentLock : resourceLocks.values()) {
                    if (currentLock.hasExpired()) {
                        resourceLocks.remove(currentLock.path);
                        continue;
                    }
                    if ((currentLock.isExclusive() || lock.isExclusive()) &&
                            currentLock.path.startsWith(lock.path + "/")) {
                        // A child resource of this collection is locked
                        lockPaths.add(currentLock.lockroot);
                    }
                }

                if (!lockPaths.isEmpty()) {

                    // One of the child paths was locked
                    // We generate a multistatus error report

                    resp.setStatus(WebdavStatus.SC_MULTI_STATUS);

                    XMLWriter generatedXML = new XMLWriter();
                    generatedXML.writeXMLHeader();

                    generatedXML.writeElement("D", DEFAULT_NAMESPACE, "multistatus", XMLWriter.OPENING);

                    generatedXML.writeElement("D", "response", XMLWriter.OPENING);
                    generatedXML.writeElement("D", "href", XMLWriter.OPENING);
                    generatedXML.writeText(getEncodedPath(path, resource, req));
                    generatedXML.writeElement("D", "href", XMLWriter.CLOSING);
                    generatedXML.writeElement("D", "status", XMLWriter.OPENING);
                    generatedXML.writeText("HTTP/1.1 " + WebdavStatus.SC_FAILED_DEPENDENCY + " ");
                    generatedXML.writeElement("D", "status", XMLWriter.CLOSING);
                    generatedXML.writeElement("D", "response", XMLWriter.CLOSING);
                    for (String lockPath : lockPaths) {
                        generatedXML.writeElement("D", "response", XMLWriter.OPENING);
                        generatedXML.writeElement("D", "href", XMLWriter.OPENING);
                        generatedXML.writeText(lockPath);
                        generatedXML.writeElement("D", "href", XMLWriter.CLOSING);
                        generatedXML.writeElement("D", "status", XMLWriter.OPENING);
                        generatedXML.writeText("HTTP/1.1 " + WebdavStatus.SC_LOCKED + " ");
                        generatedXML.writeElement("D", "status", XMLWriter.CLOSING);
                        generatedXML.writeElement("D", "response", XMLWriter.CLOSING);
                    }

                    generatedXML.writeElement("D", "multistatus", XMLWriter.CLOSING);

                    Writer writer = resp.getWriter();
                    writer.write(generatedXML.toString());
                    writer.close();

                    return;
                }

            } else {

                // Locking a single resource

                // Checking if a resource exists at this path
                if (!resource.exists()) {
                    // RFC 4918 removes lock null, instead an empty file is created
                    if (!resources.write(path, new ByteArrayInputStream(new byte[0]), false)) {
                        resp.sendError(WebdavStatus.SC_CONFLICT);
                        return;
                    } else {
                        resp.setStatus(HttpServletResponse.SC_CREATED);
                    }
                }

            }

            lock.lockroot = getEncodedPath(lock.path, resource, req);
            if (lock.isExclusive()) {
                resourceLocks.put(path, lock);
            } else {
                // Checking if there is already a shared lock on this path
                LockInfo sharedLock = resourceLocks.get(path);
                if (sharedLock == null) {
                    sharedLock = new LockInfo(maxDepth);
                    sharedLock.scope = "shared";
                    sharedLock.path = path;
                    sharedLock.lockroot = lock.lockroot;
                    sharedLock.depth = maxDepth;
                    resourceLocks.put(path, sharedLock);
                }
                sharedLock.sharedTokens.add(lock.token);
                sharedLocks.put(lock.token, lock);
            }

            // Add the Lock-Token header as by RFC 2518 8.10.1
            resp.addHeader("Lock-Token", "<" + LOCK_SCHEME + lock.token + ">");

        }

        if (!lockCreation) {

            String ifHeader = req.getHeader("If");
            if (ifHeader == null) {
                // Bad request
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            }

            LockInfo toRenew = null;
            String parentPath = path;
            do {
                LockInfo parentLock = resourceLocks.get(parentPath);
                if (parentLock != null) {
                    if (parentLock.hasExpired()) {
                        resourceLocks.remove(parentPath);
                    } else {
                        if ((parentPath != path && parentLock.depth > 0) || parentPath == path) {
                            if (parentLock.isExclusive()) {
                                if (ifHeader.contains(":" + parentLock.token + ">") && (parentLock.principal == null ||
                                        parentLock.principal.equals(req.getRemoteUser()))) {
                                    toRenew = parentLock;
                                    break;
                                }
                            } else {
                                for (String token : parentLock.sharedTokens) {
                                    if (ifHeader.contains(":" + token + ">")) {
                                        LockInfo sharedLock = sharedLocks.get(token);
                                        if (sharedLock != null && (sharedLock.principal == null ||
                                                sharedLock.principal.equals(req.getRemoteUser()))) {
                                            if ((parentPath != path && sharedLock.depth > 0) || parentPath == path) {
                                                toRenew = sharedLock;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                int slash = parentPath.lastIndexOf('/');
                if (slash < 0) {
                    break;
                }
                parentPath = parentPath.substring(0, slash);
            } while (true);

            if (toRenew != null) {
                if (!toRenew.hasExpired()) {
                    toRenew.expiresAt = lock.expiresAt;
                } else {
                    toRenew = null;
                }
            }
            lock = toRenew;
        }

        // Set the status, then generate the XML response containing
        // the lock information
        XMLWriter generatedXML = new XMLWriter();
        generatedXML.writeXMLHeader();
        generatedXML.writeElement("D", DEFAULT_NAMESPACE, "prop", XMLWriter.OPENING);

        generatedXML.writeElement("D", "lockdiscovery", XMLWriter.OPENING);

        if (lock != null) {
            lock.toXML(generatedXML);
        }

        generatedXML.writeElement("D", "lockdiscovery", XMLWriter.CLOSING);

        generatedXML.writeElement("D", "prop", XMLWriter.CLOSING);

        resp.setContentType("text/xml; charset=UTF-8");
        Writer writer = resp.getWriter();
        writer.write(generatedXML.toString());
        writer.close();
    }


    /**
     * UNLOCK Method.
     *
     * @param req  The Servlet request
     * @param resp The Servlet response
     *
     * @throws IOException If an IO error occurs
     */
    protected void doUnlock(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        if (isReadOnly()) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        String path = getRelativePath(req);

        WebResource resource = resources.getResource(path);
        if (!checkIfHeaders(req, resp, resource)) {
            resp.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }

        String lockTokenHeader = req.getHeader("Lock-Token");
        if (lockTokenHeader == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        boolean unlocked = false;
        String parentPath = path;
        do {
            LockInfo parentLock = resourceLocks.get(parentPath);
            if (parentLock != null) {
                if (parentLock.hasExpired()) {
                    resourceLocks.remove(parentPath);
                } else {
                    if ((parentPath != path && parentLock.depth > 0) || parentPath == path) {
                        if (parentLock.isExclusive()) {
                            if (lockTokenHeader.contains(":" + parentLock.token + ">") &&
                                    (parentLock.principal == null ||
                                            parentLock.principal.equals(req.getRemoteUser()))) {
                                resourceLocks.remove(parentPath);
                                unlocked = true;
                                break;
                            } else {
                                // No parent exclusive lock will be found
                                unlocked = false;
                                break;
                            }
                        } else {
                            for (String token : parentLock.sharedTokens) {
                                if (lockTokenHeader.contains(":" + token + ">")) {
                                    LockInfo lock = sharedLocks.get(token);
                                    if (lock == null) {
                                        parentLock.sharedTokens.remove(token);
                                    } else if (lock.principal == null || lock.principal.equals(req.getRemoteUser())) {
                                        // The shared lock might not have the same depth
                                        if ((parentPath != path && lock.depth > 0) || parentPath == path) {
                                            parentLock.sharedTokens.remove(token);
                                            sharedLocks.remove(token);
                                            unlocked = true;
                                        }
                                    }
                                    // Unlike the if header, this can only match one token
                                    break;
                                }
                            }
                            if (parentLock.sharedTokens.isEmpty()) {
                                resourceLocks.remove(parentPath);
                            }
                        }
                    }
                }
            }
            int slash = parentPath.lastIndexOf('/');
            if (slash < 0) {
                break;
            }
            parentPath = parentPath.substring(0, slash);
        } while (true);

        if (unlocked) {
            resp.setStatus(WebdavStatus.SC_NO_CONTENT);
        } else {
            sendReport(req, resp, parentPath, WebdavStatus.SC_CONFLICT, "lock-token-matches-request-uri");
        }
    }


    // -------------------------------------------------------- Private Methods


    /**
     * Checks whether a given path refers to a resource under <code>WEB-INF</code> or <code>META-INF</code>.
     *
     * @param path the full path of the resource being accessed
     *
     * @return <code>true</code> if the resource specified is under a special path
     */
    private boolean isSpecialPath(final String path) {
        if (!allowSpecialPaths) {
            String upperCasePath = path.toUpperCase(Locale.ENGLISH);
            if (upperCasePath.startsWith("/WEB-INF/") || upperCasePath.startsWith("/META-INF/")
                    || upperCasePath.equals("/WEB-INF") || upperCasePath.equals("/META-INF")) {
                return true;
            }
        }
        return false;
    }


    private String getEncodedPath(String path, WebResource resource, HttpServletRequest request) {
        String href = getPathPrefix(request);
        if ((href.endsWith("/")) && (path.startsWith("/"))) {
            href += path.substring(1);
        } else {
            href += path;
        }
        if (resource != null && resource.isDirectory() && (!href.endsWith("/"))) {
            href += "/";
        }

        return rewriteUrl(href);
    }


    private String getUriPrefix(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName();
    }


    private String getPathFromHref(String href, HttpServletRequest req) {

        if (href == null || href.isEmpty()) {
            return null;
        }

        URI hrefUri;
        try {
            hrefUri = new URI(href);
        } catch (URISyntaxException e) {
            return null;
        }

        String hrefPath = hrefUri.getPath();

        // Avoid path traversals
        if (!hrefPath.equals(RequestUtil.normalize(hrefPath))) {
            return null;
        }

        if (hrefUri.isAbsolute()) {
            if (!req.getServerName().equals(hrefUri.getHost())) {
                return null;
            }
        }

        if (hrefPath.length() > 1 && hrefPath.endsWith("/")) {
            hrefPath = hrefPath.substring(0, hrefPath.length() - 1);
        }

        // Verify context path
        String reqContextPath = getPathPrefix(req);
        if (!hrefPath.startsWith(reqContextPath + "/")) {
            return null;
        }

        // Remove context path & servlet path
        hrefPath = hrefPath.substring(reqContextPath.length());

        if (debug > 0) {
            log(href + " Href path: " + hrefPath);
        }

        // Protect special subdirectories
        if (isSpecialPath(hrefPath)) {
            return null;
        }

        return hrefPath;
    }


    /**
     * Check to see if a resource is currently write locked. The method will look at the "If" header to make sure the
     * client has demonstrated knowledge of the appropriate lock tokens.
     *
     * @param path The relative path
     * @param req  Servlet request
     *
     * @return <code>true</code> if the resource is locked (and no appropriate lock token has been found for at least
     *             one of the non-shared locks which are present on the resource).
     */
    private boolean isLocked(String path, HttpServletRequest req) {

        if (path == null) {
            path = getRelativePath(req);
        }

        String ifHeader = req.getHeader("If");
        if (ifHeader == null) {
            ifHeader = "";
        }

        return isLocked(path, req.getRemoteUser(), ifHeader);
    }


    /**
     * Check to see if a resource is currently write locked.
     *
     * @param path      Path of the resource
     * @param principal The authenticated principal name
     * @param ifHeader  "If" HTTP header which was included in the request
     *
     * @return <code>true</code> if the resource is locked (and no appropriate lock token has been found for at least
     *             one of the non-shared locks which are present on the resource).
     */
    private boolean isLocked(String path, String principal, String ifHeader) {

        boolean unmatchedSharedLock = false;
        // Check if the resource or a parent is already locked
        String parentPath = path;
        do {
            LockInfo parentLock = resourceLocks.get(parentPath);
            if (parentLock != null) {
                if (parentLock.hasExpired()) {
                    resourceLocks.remove(parentPath);
                } else {
                    if ((parentPath != path && parentLock.depth > 0) || parentPath == path) {
                        if (parentLock.isExclusive()) {
                            if (ifHeader.contains(":" + parentLock.token + ">") &&
                                    (parentLock.principal == null || parentLock.principal.equals(principal))) {
                                return false;
                            }
                            return true;
                        } else {
                            for (String token : parentLock.sharedTokens) {
                                LockInfo lock = sharedLocks.get(token);
                                if (lock != null) {
                                    // The shared lock might not have the same depth
                                    if ((parentPath != path && lock.depth > 0) || parentPath == path) {
                                        if (ifHeader.contains(":" + token + ">") &&
                                                (lock.principal == null || lock.principal.equals(principal))) {
                                            return false;
                                        }
                                        // Since it is a shared lock, continue to look up the tree but note that there
                                        // was a lock
                                        unmatchedSharedLock = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            int slash = parentPath.lastIndexOf('/');
            if (slash < 0) {
                break;
            }
            parentPath = parentPath.substring(0, slash);
        } while (true);

        return unmatchedSharedLock;
    }


    /**
     * Copy a resource.
     *
     * @param path Path of the resource to copy
     * @param req  Servlet request
     * @param resp Servlet response
     *
     * @return boolean true if the copy is successful
     *
     * @throws IOException If an IO error occurs
     */
    private boolean copyResource(String path, HttpServletRequest req, HttpServletResponse resp) throws IOException {

        // Check the source exists
        WebResource source = resources.getResource(path);
        if (!source.exists()) {
            resp.sendError(WebdavStatus.SC_NOT_FOUND);
            return false;
        }
        if (!checkIfHeaders(req, resp, source)) {
            resp.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            return false;
        }

        // Parsing destination header
        // See RFC 4918
        String destinationHeader = req.getHeader("Destination");

        if (destinationHeader == null || destinationHeader.isEmpty()) {
            resp.sendError(WebdavStatus.SC_BAD_REQUEST);
            return false;
        }

        URI destinationUri;
        try {
            destinationUri = new URI(destinationHeader);
        } catch (URISyntaxException e) {
            resp.sendError(WebdavStatus.SC_BAD_REQUEST);
            return false;
        }

        String destinationPath = destinationUri.getPath();

        // Destination isn't allowed to use '.' or '..' segments
        if (!destinationPath.equals(RequestUtil.normalize(destinationPath))) {
            resp.sendError(WebdavStatus.SC_BAD_REQUEST);
            return false;
        }

        if (destinationUri.isAbsolute()) {
            // Scheme and host need to match
            if (!req.getScheme().equals(destinationUri.getScheme()) ||
                    !req.getServerName().equals(destinationUri.getHost())) {
                resp.sendError(WebdavStatus.SC_FORBIDDEN);
                return false;
            }
            // Port needs to match too but handled separately as the logic is a
            // little more complicated
            if (req.getServerPort() != destinationUri.getPort()) {
                if (destinationUri.getPort() == -1 && ("http".equals(req.getScheme()) && req.getServerPort() == 80 ||
                        "https".equals(req.getScheme()) && req.getServerPort() == 443)) {
                    // All good.
                } else {
                    resp.sendError(WebdavStatus.SC_FORBIDDEN);
                    return false;
                }
            }
        }

        if (destinationPath.length() > 1 && destinationPath.endsWith("/")) {
            destinationPath = destinationPath.substring(0, destinationPath.length() - 1);
        }

        // Cross-context operations aren't supported
        String reqContextPath = getPathPrefix(req);
        String expectedTargetPath = reqContextPath;
        // Also ensure copy (and move) operations do not escape the configured sub-path when limited to the sub-path
        if (serveSubpathOnly && req.getServletPath() != null) {
            expectedTargetPath = expectedTargetPath + req.getServletPath();
        }
        if (!destinationPath.startsWith(expectedTargetPath + "/")) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        // Remove context path & servlet path
        destinationPath = destinationPath.substring(reqContextPath.length());

        if (debug > 0) {
            log("Dest path: " + destinationPath);
        }

        // Check destination path to protect special subdirectories
        if (isSpecialPath(destinationPath)) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        if (destinationPath.equals(path)) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        // Check src / dest are not sub-dirs of each other
        if (destinationPath.startsWith(path) && destinationPath.charAt(path.length()) == '/' ||
                path.startsWith(destinationPath) && path.charAt(destinationPath.length()) == '/') {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        // Check if destination is locked
        if (isLocked(destinationPath, req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return false;
        }

        boolean overwrite = true;
        String overwriteHeader = req.getHeader("Overwrite");
        if (overwriteHeader != null) {
            if (overwriteHeader.equalsIgnoreCase("T")) {
                overwrite = true;
            } else {
                overwrite = false;
            }
        }

        // Overwriting the destination
        WebResource destination = resources.getResource(destinationPath);
        if (overwrite) {
            // Delete destination resource, if it exists
            if (destination.exists()) {
                if (!deleteResource(destinationPath, req, resp, true)) {
                    return false;
                }
            } else {
                resp.setStatus(WebdavStatus.SC_CREATED);
            }
        } else {
            // If the destination exists, then it's a conflict
            if (destination.exists()) {
                resp.sendError(WebdavStatus.SC_PRECONDITION_FAILED);
                return false;
            }
        }

        // Copying source to destination

        Map<String,Integer> errorList = new LinkedHashMap<>();

        boolean infiniteCopy = true;
        String depthHeader = req.getHeader("Depth");
        if (depthHeader != null) {
            if (depthHeader.equals("infinity")) {
                // NO-OP - this is the default
            } else if (depthHeader.equals("0")) {
                infiniteCopy = false;
            } else {
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return false;
            }
        }

        boolean result = copyResource(errorList, path, destinationPath, infiniteCopy);

        if ((!result) || (!errorList.isEmpty())) {
            if (errorList.size() == 1) {
                resp.sendError(errorList.values().iterator().next().intValue());
            } else {
                sendReport(req, resp, errorList);
            }
            return false;
        }

        // Copy was successful
        if (destination.exists()) {
            resp.setStatus(WebdavStatus.SC_NO_CONTENT);
        } else {
            resp.setStatus(WebdavStatus.SC_CREATED);
        }

        return true;
    }


    /**
     * Copy a collection.
     *
     * @param errorList    Map containing the list of errors which occurred during the copy operation
     * @param source       Path of the resource to be copied
     * @param dest         Destination path
     * @param infiniteCopy {@code true} if this copy is to be an infinite copy, otherwise {@code false} for a shallow
     *                         copy
     *
     * @return <code>true</code> if the copy was successful
     */
    private boolean copyResource(Map<String,Integer> errorList, String source, String dest, boolean infiniteCopy) {

        if (debug > 1) {
            log("Copy: " + source + " To: " + dest + " Infinite: " + infiniteCopy);
        }

        WebResource sourceResource = resources.getResource(source);

        if (sourceResource.isDirectory()) {
            if (!resources.mkdir(dest)) {
                WebResource destResource = resources.getResource(dest);
                if (!destResource.isDirectory()) {
                    errorList.put(dest, Integer.valueOf(WebdavStatus.SC_CONFLICT));
                    return false;
                }
            } else {
                store.copy(source, dest);
            }

            if (infiniteCopy) {
                String[] entries = resources.list(source);
                for (String entry : entries) {
                    String childDest = dest;
                    if (!childDest.equals("/")) {
                        childDest += "/";
                    }
                    childDest += entry;
                    String childSrc = source;
                    if (!childSrc.equals("/")) {
                        childSrc += "/";
                    }
                    childSrc += entry;
                    copyResource(errorList, childSrc, childDest, true);
                }
            }
        } else if (sourceResource.isFile()) {
            WebResource destResource = resources.getResource(dest);
            if (!destResource.exists() && !destResource.getWebappPath().endsWith("/")) {
                int lastSlash = destResource.getWebappPath().lastIndexOf('/');
                if (lastSlash > 0) {
                    String parent = destResource.getWebappPath().substring(0, lastSlash);
                    WebResource parentResource = resources.getResource(parent);
                    if (!parentResource.isDirectory()) {
                        errorList.put(source, Integer.valueOf(WebdavStatus.SC_CONFLICT));
                        return false;
                    }
                }
            }
            // WebDAV Litmus test attempts to copy/move a file over a collection
            // Need to remove trailing / from destination to enable test to pass
            if (!destResource.exists() && dest.endsWith("/") && dest.length() > 1) {
                // Convert destination name from collection (with trailing '/')
                // to file (without trailing '/')
                dest = dest.substring(0, dest.length() - 1);
            }
            try (InputStream is = sourceResource.getInputStream()) {
                if (!resources.write(dest, is, false)) {
                    errorList.put(source, Integer.valueOf(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
                    return false;
                } else {
                    store.copy(source, dest);
                }
            } catch (IOException e) {
                log(sm.getString("webdavservlet.inputstreamclosefail", source), e);
            }
        } else {
            errorList.put(source, Integer.valueOf(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
            return false;
        }
        return true;
    }


    /**
     * Delete a resource.
     *
     * @param path Path of the resource which is to be deleted
     * @param req  Servlet request
     * @param resp Servlet response
     *
     * @return <code>true</code> if the delete is successful
     *
     * @throws IOException If an IO error occurs
     */
    private boolean deleteResource(String path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        WebResource resource = resources.getResource(path);
        if (!checkIfHeaders(req, resp, resource)) {
            resp.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            return false;
        }
        return deleteResource(path, req, resp, true);
    }


    /**
     * Delete a resource.
     *
     * @param path      Path of the resource which is to be deleted
     * @param req       Servlet request
     * @param resp      Servlet response
     * @param setStatus Should the response status be set on successful completion
     *
     * @return <code>true</code> if the delete is successful
     *
     * @throws IOException If an IO error occurs
     */
    private boolean deleteResource(String path, HttpServletRequest req, HttpServletResponse resp, boolean setStatus)
            throws IOException {

        if (isLocked(path, req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return false;
        }

        WebResource resource = resources.getResource(path);

        if (!resource.exists()) {
            resp.sendError(WebdavStatus.SC_NOT_FOUND);
            return false;
        }

        if (!resource.isDirectory()) {
            if (!resource.delete()) {
                sendNotAllowed(req, resp);
                return false;
            }
            deletedResource(path);
        } else {

            Map<String,Integer> errorList = new LinkedHashMap<>();

            deleteCollection(req, path, errorList);
            if (!resource.delete()) {
                /*
                 * See RFC 4918, section 9.6.1, last paragraph.
                 *
                 * If a child resource can't be deleted then the parent resource SHOULD NOT be included in the
                 * multi-status response since the notice of the failure to delete the child implies that all parent
                 * resources could also not be deleted.
                 */
                if (resources.list(path).length == 0) {
                    /*
                     * The resource could not be deleted. If the resource is a directory and it has no children (or all
                     * those children have been successfully deleted) then it should be listed in the multi-status
                     * response.
                     */
                    errorList.put(path, Integer.valueOf(WebdavStatus.SC_METHOD_NOT_ALLOWED));
                }
            } else {
                deletedResource(path);
            }

            if (!errorList.isEmpty()) {
                sendReport(req, resp, errorList);
                return false;
            }
        }
        if (setStatus) {
            resp.setStatus(WebdavStatus.SC_NO_CONTENT);
        }
        return true;
    }


    /**
     * Deletes a collection.
     *
     * @param req       The Servlet request
     * @param path      Path to the collection to be deleted
     * @param errorList Contains the list of the errors which occurred
     */
    private void deleteCollection(HttpServletRequest req, String path, Map<String,Integer> errorList) {

        if (debug > 1) {
            log("Delete collection: " + path);
        }

        // Prevent deletion of special subdirectories
        if (isSpecialPath(path)) {
            errorList.put(path, Integer.valueOf(WebdavStatus.SC_FORBIDDEN));
            return;
        }

        String ifHeader = req.getHeader("If");
        if (ifHeader == null) {
            ifHeader = "";
        }

        String[] entries = resources.list(path);

        for (String entry : entries) {
            String childName = path;
            if (!childName.equals("/")) {
                childName += "/";
            }
            childName += entry;

            if (isLocked(childName, req.getRemoteUser(), ifHeader)) {

                errorList.put(childName, Integer.valueOf(WebdavStatus.SC_LOCKED));

            } else {
                WebResource childResource = resources.getResource(childName);
                if (childResource.isDirectory()) {
                    deleteCollection(req, childName, errorList);
                }

                if (!childResource.delete()) {
                    /*
                     * See RFC 4918, section 9.6.1, last paragraph.
                     *
                     * If a child resource can't be deleted then the parent resource SHOULD NOT be included in the
                     * multi-status response since the notice of the failure to delete the child implies that all parent
                     * resources could also not be deleted.
                     */
                    if (!childResource.isDirectory() || resources.list(childName).length == 0) {
                        /*
                         * The resource could not be deleted. If the resource is not a directory or if the resource is a
                         * directory and it has no children (or all those children have been successfully deleted) then
                         * it should be listed in the multi-status response.
                         */
                        errorList.put(childName, Integer.valueOf(WebdavStatus.SC_METHOD_NOT_ALLOWED));
                    }
                } else {
                    deletedResource(childName);
                }
            }
        }
    }


    private void deletedResource(String path) {
        LockInfo lock = resourceLocks.remove(path);
        if (lock != null && !lock.isExclusive()) {
            for (String token : lock.sharedTokens) {
                sharedLocks.remove(token);
            }
        }
        store.delete(path);
    }


    /**
     * Send a multistatus element containing a complete error report to the client.
     *
     * @param req       Servlet request
     * @param resp      Servlet response
     * @param errorList List of error to be displayed
     *
     * @throws IOException If an IO error occurs
     */
    private void sendReport(HttpServletRequest req, HttpServletResponse resp, Map<String,Integer> errorList)
            throws IOException {

        resp.setStatus(WebdavStatus.SC_MULTI_STATUS);

        XMLWriter generatedXML = new XMLWriter();
        generatedXML.writeXMLHeader();

        generatedXML.writeElement("D", DEFAULT_NAMESPACE, "multistatus", XMLWriter.OPENING);

        for (Map.Entry<String,Integer> errorEntry : errorList.entrySet()) {
            String errorPath = errorEntry.getKey();
            int errorCode = errorEntry.getValue().intValue();

            generatedXML.writeElement("D", "response", XMLWriter.OPENING);

            generatedXML.writeElement("D", "href", XMLWriter.OPENING);
            generatedXML.writeText(getEncodedPath(errorPath, null, req));
            generatedXML.writeElement("D", "href", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "status", XMLWriter.OPENING);
            generatedXML.writeText("HTTP/1.1 " + errorCode + " ");
            generatedXML.writeElement("D", "status", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "response", XMLWriter.CLOSING);
        }

        generatedXML.writeElement("D", "multistatus", XMLWriter.CLOSING);

        Writer writer = resp.getWriter();
        writer.write(generatedXML.toString());
        writer.close();
    }


    private void sendReport(HttpServletRequest req, HttpServletResponse resp, String errorPath, int errorCode,
            String error) throws IOException {
        resp.setStatus(errorCode);

        XMLWriter generatedXML = new XMLWriter();
        generatedXML.writeXMLHeader();

        generatedXML.writeElement("D", DEFAULT_NAMESPACE, "error", XMLWriter.OPENING);

        if (errorPath != null && errorPath.length() > 0) {
            generatedXML.writeElement("D", error, XMLWriter.OPENING);
            generatedXML.writeElement("D", "href", XMLWriter.OPENING);
            generatedXML.writeText(getEncodedPath(errorPath, null, req));
            generatedXML.writeElement("D", "href", XMLWriter.CLOSING);
            generatedXML.writeElement("D", error, XMLWriter.CLOSING);
        } else {
            generatedXML.writeElement("D", error, XMLWriter.NO_CONTENT);
        }

        generatedXML.writeElement("D", "error", XMLWriter.CLOSING);

        Writer writer = resp.getWriter();
        writer.write(generatedXML.toString());
        writer.close();
    }


    private void propfindResource(XMLWriter generatedXML, String rewrittenUrl, String path, PropfindType propFindType,
            List<Node> properties, boolean isFile, long created, long lastModified, long contentLength,
            String contentType, String eTag) {

        generatedXML.writeElement("D", "response", XMLWriter.OPENING);
        String status = "HTTP/1.1 " + WebdavStatus.SC_OK + " ";

        // Generating href element
        generatedXML.writeElement("D", "href", XMLWriter.OPENING);
        generatedXML.writeText(rewrittenUrl);
        generatedXML.writeElement("D", "href", XMLWriter.CLOSING);

        String resourceName = path;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            resourceName = resourceName.substring(lastSlash + 1);
        }

        switch (propFindType) {

            case FIND_ALL_PROP:

                generatedXML.writeElement("D", "propstat", XMLWriter.OPENING);
                generatedXML.writeElement("D", "prop", XMLWriter.OPENING);

                generatedXML.writeProperty("D", "creationdate", getISOCreationDate(created));
                generatedXML.writeProperty("D", "getlastmodified", FastHttpDateFormat.formatDate(lastModified));
                if (isFile) {
                    generatedXML.writeProperty("D", "getcontentlength", Long.toString(contentLength));
                    if (contentType != null) {
                        generatedXML.writeProperty("D", "getcontenttype", contentType);
                    }
                    generatedXML.writeProperty("D", "getetag", eTag);
                    generatedXML.writeElement("D", "resourcetype", XMLWriter.NO_CONTENT);
                } else {
                    generatedXML.writeElement("D", "resourcetype", XMLWriter.OPENING);
                    generatedXML.writeElement("D", "collection", XMLWriter.NO_CONTENT);
                    generatedXML.writeElement("D", "resourcetype", XMLWriter.CLOSING);
                }

                store.propfind(path, null, false, generatedXML);

                generatedXML.writeElement("D", "supportedlock", XMLWriter.OPENING);
                generatedXML.writeRaw(SUPPORTED_LOCKS);
                generatedXML.writeElement("D", "supportedlock", XMLWriter.CLOSING);

                generateLockDiscovery(path, generatedXML);

                generatedXML.writeElement("D", "prop", XMLWriter.CLOSING);
                generatedXML.writeElement("D", "status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement("D", "status", XMLWriter.CLOSING);
                generatedXML.writeElement("D", "propstat", XMLWriter.CLOSING);

                break;

            case FIND_PROPERTY_NAMES:

                generatedXML.writeElement("D", "propstat", XMLWriter.OPENING);
                generatedXML.writeElement("D", "prop", XMLWriter.OPENING);

                generatedXML.writeElement("D", "creationdate", XMLWriter.NO_CONTENT);
                if (isFile) {
                    generatedXML.writeElement("D", "getcontentlength", XMLWriter.NO_CONTENT);
                    if (contentType != null) {
                        generatedXML.writeElement("D", "getcontenttype", XMLWriter.NO_CONTENT);
                    }
                    generatedXML.writeElement("D", "getetag", XMLWriter.NO_CONTENT);
                }
                generatedXML.writeElement("D", "getlastmodified", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("D", "resourcetype", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("D", "lockdiscovery", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("D", "supportedlock", XMLWriter.NO_CONTENT);
                store.propfind(path, null, true, generatedXML);

                generatedXML.writeElement("D", "prop", XMLWriter.CLOSING);
                generatedXML.writeElement("D", "status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement("D", "status", XMLWriter.CLOSING);
                generatedXML.writeElement("D", "propstat", XMLWriter.CLOSING);

                break;

            case FIND_BY_PROPERTY:

                List<Node> propertiesNotFound = new ArrayList<>();

                // Parse the list of properties

                generatedXML.writeElement("D", "propstat", XMLWriter.OPENING);
                generatedXML.writeElement("D", "prop", XMLWriter.OPENING);

                for (Node propertyNode : properties) {
                    String property = getDAVNode(propertyNode);
                    boolean protectedProperty = property != null &&
                            (!(property.equals("displayname") || property.equals("getcontentlanguage")));
                    if (property == null || !protectedProperty) {
                        if (!store.propfind(path, propertyNode, false, generatedXML)) {
                            propertiesNotFound.add(propertyNode);
                        }
                    } else if (property.equals("creationdate")) {
                        generatedXML.writeProperty("D", "creationdate", getISOCreationDate(created));
                    } else if (property.equals("getcontentlength")) {
                        if (isFile) {
                            generatedXML.writeProperty("D", "getcontentlength", Long.toString(contentLength));
                        } else {
                            propertiesNotFound.add(propertyNode);
                        }
                    } else if (property.equals("getcontenttype")) {
                        if (isFile && contentType != null) {
                            generatedXML.writeProperty("D", "getcontenttype", contentType);
                        } else {
                            propertiesNotFound.add(propertyNode);
                        }
                    } else if (property.equals("getetag")) {
                        if (isFile) {
                            generatedXML.writeProperty("D", "getetag", eTag);
                        } else {
                            propertiesNotFound.add(propertyNode);
                        }
                    } else if (property.equals("getlastmodified")) {
                        generatedXML.writeProperty("D", "getlastmodified", FastHttpDateFormat.formatDate(lastModified));
                    } else if (property.equals("resourcetype")) {
                        if (isFile) {
                            generatedXML.writeElement("D", "resourcetype", XMLWriter.NO_CONTENT);
                        } else {
                            generatedXML.writeElement("D", "resourcetype", XMLWriter.OPENING);
                            generatedXML.writeElement("D", "collection", XMLWriter.NO_CONTENT);
                            generatedXML.writeElement("D", "resourcetype", XMLWriter.CLOSING);
                        }
                    } else if (property.equals("supportedlock")) {
                        generatedXML.writeElement("D", "supportedlock", XMLWriter.OPENING);
                        generatedXML.writeRaw(SUPPORTED_LOCKS);
                        generatedXML.writeElement("D", "supportedlock", XMLWriter.CLOSING);
                    } else if (property.equals("lockdiscovery")) {
                        generateLockDiscovery(path, generatedXML);
                    } else {
                        propertiesNotFound.add(propertyNode);
                    }
                }

                generatedXML.writeElement("D", "prop", XMLWriter.CLOSING);
                generatedXML.writeElement("D", "status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement("D", "status", XMLWriter.CLOSING);
                generatedXML.writeElement("D", "propstat", XMLWriter.CLOSING);

                if (!propertiesNotFound.isEmpty()) {

                    status = "HTTP/1.1 " + WebdavStatus.SC_NOT_FOUND + " ";

                    generatedXML.writeElement("D", "propstat", XMLWriter.OPENING);
                    generatedXML.writeElement("D", "prop", XMLWriter.OPENING);

                    for (Node propertyNotFoundNode : propertiesNotFound) {
                        String propertyNotFound = getDAVNode(propertyNotFoundNode);
                        if (propertyNotFound != null) {
                            generatedXML.writeElement("D", propertyNotFound, XMLWriter.NO_CONTENT);
                        } else {
                            generatedXML.writeElement(null, propertyNotFoundNode.getNamespaceURI(),
                                    propertyNotFoundNode.getLocalName(), XMLWriter.NO_CONTENT);
                        }
                    }

                    generatedXML.writeElement("D", "prop", XMLWriter.CLOSING);
                    generatedXML.writeElement("D", "status", XMLWriter.OPENING);
                    generatedXML.writeText(status);
                    generatedXML.writeElement("D", "status", XMLWriter.CLOSING);
                    generatedXML.writeElement("D", "propstat", XMLWriter.CLOSING);
                }

                break;
        }

        generatedXML.writeElement("D", "response", XMLWriter.CLOSING);
    }


    /**
     * Print the lock discovery information associated with a path.
     *
     * @param path         Path
     * @param generatedXML XML data to which the locks info will be appended
     */
    private void generateLockDiscovery(String path, XMLWriter generatedXML) {

        generatedXML.writeElement("D", "lockdiscovery", XMLWriter.OPENING);

        String parentPath = path;
        do {
            LockInfo parentLock = resourceLocks.get(parentPath);
            if (parentLock != null) {
                if (parentLock.hasExpired()) {
                    resourceLocks.remove(parentPath);
                } else {
                    if ((parentPath != path && parentLock.depth > 0) || parentPath == path) {
                        if (parentLock.isExclusive()) {
                            parentLock.toXML(generatedXML);
                        } else {
                            for (String lockToken : parentLock.sharedTokens) {
                                LockInfo sharedLock = sharedLocks.get(lockToken);
                                if (sharedLock != null) {
                                    if (sharedLock.hasExpired()) {
                                        sharedLocks.remove(lockToken);
                                    } else {
                                        if ((parentPath != path && sharedLock.depth > 0) || parentPath == path) {
                                            sharedLock.toXML(generatedXML);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            int slash = parentPath.lastIndexOf('/');
            if (slash < 0) {
                break;
            }
            parentPath = parentPath.substring(0, slash);
        } while (true);

        generatedXML.writeElement("D", "lockdiscovery", XMLWriter.CLOSING);
    }


    /**
     * Get creation date in ISO format.
     *
     * @return the formatted creation date
     */
    private static String getISOCreationDate(long creationDate) {
        return creationDateFormat.format(new Date(creationDate));
    }


    private static String getDAVNode(Node node) {
        if (DEFAULT_NAMESPACE.equals(node.getNamespaceURI())) {
            return node.getLocalName();
        }
        return null;
    }


    private static boolean propertyEquals(Node node1, Node node2) {
        if (node1.getLocalName().equals(node2.getLocalName()) &&
                ((node1.getNamespaceURI() == null && node2.getNamespaceURI() == null) ||
                        (node1.getNamespaceURI() != null && node1.getNamespaceURI().equals(node2.getNamespaceURI())))) {
            return true;
        }
        return false;
    }


    // -------------------------------------------------- LockInfo Inner Class

    /**
     * Holds a lock information.
     */
    private static class LockInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        LockInfo(int maxDepth) {
            this.maxDepth = maxDepth;
        }


        // ------------------------------------------------- Instance Variables

        private final int maxDepth;

        String path = "/";
        String lockroot = "/";
        String type = "write";
        String scope = "exclusive";
        int depth = 0;
        String owner = "";
        String token = "";
        List<String> sharedTokens = new CopyOnWriteArrayList<>();
        long expiresAt = 0;
        String principal = null;


        // ----------------------------------------------------- Public Methods

        @Override
        public String toString() {

            StringBuilder result = new StringBuilder("Type:");
            result.append(type);
            result.append("\nScope:");
            result.append(scope);
            result.append("\nDepth:");
            result.append(depth);
            result.append("\nOwner:");
            result.append(owner);
            result.append("\nExpiration:");
            result.append(FastHttpDateFormat.formatDate(expiresAt));
            result.append("\nToken:");
            result.append(token);
            result.append("\n");
            return result.toString();
        }


        /**
         * @return true if the lock has expired.
         */
        public boolean hasExpired() {
            return sharedTokens.size() == 0 && System.currentTimeMillis() > expiresAt;
        }


        /**
         * @return true if the lock is exclusive.
         */
        public boolean isExclusive() {
            return scope.equals("exclusive");
        }


        /**
         * Get an XML representation of this lock token.
         *
         * @param generatedXML The XML write to which the fragment will be appended
         */
        public void toXML(XMLWriter generatedXML) {

            generatedXML.writeElement("D", "activelock", XMLWriter.OPENING);

            generatedXML.writeElement("D", "locktype", XMLWriter.OPENING);
            generatedXML.writeElement("D", type, XMLWriter.NO_CONTENT);
            generatedXML.writeElement("D", "locktype", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "lockscope", XMLWriter.OPENING);
            generatedXML.writeElement("D", scope, XMLWriter.NO_CONTENT);
            generatedXML.writeElement("D", "lockscope", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "depth", XMLWriter.OPENING);
            if (depth == maxDepth) {
                generatedXML.writeText("Infinity");
            } else {
                generatedXML.writeText("0");
            }
            generatedXML.writeElement("D", "depth", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "owner", XMLWriter.OPENING);
            generatedXML.writeText(owner);
            generatedXML.writeElement("D", "owner", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "timeout", XMLWriter.OPENING);
            long timeout = (expiresAt - System.currentTimeMillis()) / 1000;
            generatedXML.writeText("Second-" + timeout);
            generatedXML.writeElement("D", "timeout", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "lockroot", XMLWriter.OPENING);
            generatedXML.writeElement("D", "href", XMLWriter.OPENING);
            generatedXML.writeText(lockroot);
            generatedXML.writeElement("D", "href", XMLWriter.CLOSING);
            generatedXML.writeElement("D", "lockroot", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "locktoken", XMLWriter.OPENING);
            generatedXML.writeElement("D", "href", XMLWriter.OPENING);
            generatedXML.writeText(LOCK_SCHEME + token);
            generatedXML.writeElement("D", "href", XMLWriter.CLOSING);
            generatedXML.writeElement("D", "locktoken", XMLWriter.CLOSING);

            generatedXML.writeElement("D", "activelock", XMLWriter.CLOSING);
        }
    }


    // --------------------------------------------- WebdavResolver Inner Class


    /**
     * Work around for XML parsers that don't fully respect
     * {@link DocumentBuilderFactory#setExpandEntityReferences(boolean)} when called with <code>false</code>. External
     * references are filtered out for security reasons. See CVE-2007-5461.
     */
    private static class WebdavResolver implements EntityResolver {
        private ServletContext context;

        WebdavResolver(ServletContext theContext) {
            context = theContext;
        }

        @Override
        public InputSource resolveEntity(String publicId, String systemId) {
            context.log(sm.getString("webdavservlet.externalEntityIgnored", publicId, systemId));
            return new InputSource(new StringReader("Ignored external entity"));
        }
    }


    // ------------------------------------- TransientPropertyStore Inner Class


    /**
     * Default property store, which provides memory storage without persistence.
     */
    public static class MemoryPropertyStore implements PropertyStore {

        private final ConcurrentHashMap<String,ArrayList<Node>> deadProperties = new ConcurrentHashMap<>();

        @Override
        public void init() {
        }

        @Override
        public void destroy() {
        }

        @Override
        public void periodicEvent() {
        }

        @Override
        public void copy(String source, String destination) {
            ArrayList<Node> properties = deadProperties.get(source);
            ArrayList<Node> propertiesDest = deadProperties.get(destination);
            if (properties != null) {
                if (propertiesDest == null) {
                    propertiesDest = new ArrayList<>();
                    deadProperties.put(destination, propertiesDest);
                }
                synchronized (properties) {
                    synchronized (propertiesDest) {
                        for (Node node : properties) {
                            node = node.cloneNode(true);
                            boolean found = false;
                            for (int i = 0; i < propertiesDest.size(); i++) {
                                Node propertyNode = propertiesDest.get(i);
                                if (propertyEquals(node, propertyNode)) {
                                    found = true;
                                    propertiesDest.set(i, node);
                                    break;
                                }
                            }
                            if (!found) {
                                propertiesDest.add(node);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void delete(String resource) {
            deadProperties.remove(resource);
        }

        @Override
        public boolean propfind(String resource, Node property, boolean nameOnly, XMLWriter generatedXML) {
            ArrayList<Node> properties = deadProperties.get(resource);
            if (properties != null) {
                synchronized (properties) {
                    if (nameOnly) {
                        // Add the names of all properties
                        for (Node node : properties) {
                            generatedXML.writeElement(null, node.getNamespaceURI(), node.getLocalName(),
                                    XMLWriter.NO_CONTENT);
                        }
                    } else if (property != null) {
                        // Add a single property
                        Node foundNode = null;
                        for (Node node : properties) {
                            if (propertyEquals(node, property)) {
                                foundNode = node;
                            }
                        }
                        if (foundNode != null) {
                            StringWriter strWriter = new StringWriter();
                            DOMWriter domWriter = new DOMWriter(strWriter);
                            domWriter.print(foundNode);
                            generatedXML.writeRaw(strWriter.toString());
                            return true;
                        }
                    } else {
                        StringWriter strWriter = new StringWriter();
                        DOMWriter domWriter = new DOMWriter(strWriter);
                        // Add all properties
                        for (Node node : properties) {
                            domWriter.print(node);
                        }
                        generatedXML.writeRaw(strWriter.toString());
                    }
                }
            }
            return false;
        }

        @Override
        public void proppatch(String resource, ArrayList<ProppatchOperation> operations) {
            boolean protectedProperty = false;
            // Check for the protected properties
            for (ProppatchOperation operation : operations) {
                if (operation.getProtectedProperty()) {
                    protectedProperty = true;
                    operation.setStatusCode(HttpServletResponse.SC_FORBIDDEN);
                }
            }
            if (protectedProperty) {
                for (ProppatchOperation operation : operations) {
                    if (!operation.getProtectedProperty()) {
                        operation.setStatusCode(WebdavStatus.SC_FAILED_DEPENDENCY);
                    }
                }
            } else {
                ArrayList<Node> properties = deadProperties.get(resource);
                if (properties == null) {
                    properties = new ArrayList<>();
                    deadProperties.put(resource, properties);
                }
                synchronized (properties) {
                    for (ProppatchOperation operation : operations) {
                        if (operation.getUpdateType() == PropertyUpdateType.SET) {
                            Node node = operation.getPropertyNode().cloneNode(true);
                            boolean found = false;
                            for (int i = 0; i < properties.size(); i++) {
                                Node propertyNode = properties.get(i);
                                if (propertyEquals(node, propertyNode)) {
                                    found = true;
                                    properties.set(i, node);
                                    break;
                                }
                            }
                            if (!found) {
                                properties.add(node);
                            }
                        }
                        if (operation.getUpdateType() == PropertyUpdateType.REMOVE) {
                            Node node = operation.getPropertyNode();
                            for (int i = 0; i < properties.size(); i++) {
                                Node propertyNode = properties.get(i);
                                if (propertyEquals(node, propertyNode)) {
                                    properties.remove(i);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

    }


}


// -------------------------------------------------------- WebdavStatus Class

/**
 * Wraps the HttpServletResponse class to abstract the specific protocol used. To support other protocols we would only
 * need to modify this class and the WebDavRetCode classes.
 *
 * @author Marc Eaddy
 */
class WebdavStatus {

    // ------------------------------------------------------ HTTP Status Codes


    /**
     * Status code (200) indicating the request succeeded normally.
     */
    public static final int SC_OK = HttpServletResponse.SC_OK;


    /**
     * Status code (201) indicating the request succeeded and created a new resource on the server.
     */
    public static final int SC_CREATED = HttpServletResponse.SC_CREATED;


    /**
     * Status code (202) indicating that a request was accepted for processing, but was not completed.
     */
    public static final int SC_ACCEPTED = HttpServletResponse.SC_ACCEPTED;


    /**
     * Status code (204) indicating that the request succeeded but that there was no new information to return.
     */
    public static final int SC_NO_CONTENT = HttpServletResponse.SC_NO_CONTENT;


    /**
     * Status code (301) indicating that the resource has permanently moved to a new location, and that future
     * references should use a new URI with their requests.
     */
    public static final int SC_MOVED_PERMANENTLY = HttpServletResponse.SC_MOVED_PERMANENTLY;


    /**
     * Status code (302) indicating that the resource has temporarily moved to another location, but that future
     * references should still use the original URI to access the resource.
     */
    public static final int SC_MOVED_TEMPORARILY = HttpServletResponse.SC_MOVED_TEMPORARILY;


    /**
     * Status code (304) indicating that a conditional GET operation found that the resource was available and not
     * modified.
     */
    public static final int SC_NOT_MODIFIED = HttpServletResponse.SC_NOT_MODIFIED;


    /**
     * Status code (400) indicating the request sent by the client was syntactically incorrect.
     */
    public static final int SC_BAD_REQUEST = HttpServletResponse.SC_BAD_REQUEST;


    /**
     * Status code (401) indicating that the request requires HTTP authentication.
     */
    public static final int SC_UNAUTHORIZED = HttpServletResponse.SC_UNAUTHORIZED;


    /**
     * Status code (403) indicating the server understood the request but refused to fulfill it.
     */
    public static final int SC_FORBIDDEN = HttpServletResponse.SC_FORBIDDEN;


    /**
     * Status code (404) indicating that the requested resource is not available.
     */
    public static final int SC_NOT_FOUND = HttpServletResponse.SC_NOT_FOUND;


    /**
     * Status code (500) indicating an error inside the HTTP service which prevented it from fulfilling the request.
     */
    public static final int SC_INTERNAL_SERVER_ERROR = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;


    /**
     * Status code (501) indicating the HTTP service does not support the functionality needed to fulfill the request.
     */
    public static final int SC_NOT_IMPLEMENTED = HttpServletResponse.SC_NOT_IMPLEMENTED;


    /**
     * Status code (502) indicating that the HTTP server received an invalid response from a server it consulted when
     * acting as a proxy or gateway.
     */
    public static final int SC_BAD_GATEWAY = HttpServletResponse.SC_BAD_GATEWAY;


    /**
     * Status code (503) indicating that the HTTP service is temporarily overloaded, and unable to handle the request.
     */
    public static final int SC_SERVICE_UNAVAILABLE = HttpServletResponse.SC_SERVICE_UNAVAILABLE;


    /**
     * Status code (100) indicating the client may continue with its request. This interim response is used to inform
     * the client that the initial part of the request has been received and has not yet been rejected by the server.
     */
    public static final int SC_CONTINUE = HttpServletResponse.SC_CONTINUE;


    /**
     * Status code (405) indicating the method specified is not allowed for the resource.
     */
    public static final int SC_METHOD_NOT_ALLOWED = HttpServletResponse.SC_METHOD_NOT_ALLOWED;


    /**
     * Status code (409) indicating that the request could not be completed due to a conflict with the current state of
     * the resource.
     */
    public static final int SC_CONFLICT = HttpServletResponse.SC_CONFLICT;


    /**
     * Status code (412) indicating the precondition given in one or more of the request-header fields evaluated to
     * false when it was tested on the server.
     */
    public static final int SC_PRECONDITION_FAILED = HttpServletResponse.SC_PRECONDITION_FAILED;


    /**
     * Status code (413) indicating the server is refusing to process a request because the request entity is larger
     * than the server is willing or able to process.
     */
    public static final int SC_REQUEST_TOO_LONG = HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE;


    /**
     * Status code (415) indicating the server is refusing to service the request because the entity of the request is
     * in a format not supported by the requested resource for the requested method.
     */
    public static final int SC_UNSUPPORTED_MEDIA_TYPE = HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;


    // -------------------------------------------- Extended WebDav status code


    /**
     * Status code (207) indicating that the response requires providing status for multiple independent operations.
     */
    public static final int SC_MULTI_STATUS = 207;


    /**
     * Status code (422) indicating that the server understands the content type of the request but is unable to process
     * the contained instructions.
     */
    public static final int SC_UNPROCESSABLE_CONTENT = HttpServletResponse.SC_UNPROCESSABLE_CONTENT;


    /**
     * Status code (420) indicating the method was not executed on a particular resource within its scope because some
     * part of the method's execution failed causing the entire method to be aborted.
     */
    public static final int SC_METHOD_FAILURE = 420;


    /**
     * Status code (423) indicating the destination resource of a method is locked, and either the request did not
     * contain a valid Lock-Info header, or the Lock-Info header identifies a lock held by another principal.
     */
    public static final int SC_LOCKED = 423;

    /**
     * Status code (424) indicating that another dependent operation failed.
     */
    public static final int SC_FAILED_DEPENDENCY = 424;

    /**
     * Status code (507) indicating that the server does not have enough storage to complete the operation.
     */
    public static final int SC_INSUFFICIENT_STORAGE = 507;
}
