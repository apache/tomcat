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
package org.apache.jasper.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.runtime.ExceptionUtils;
import org.apache.tomcat.Jar;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.util.descriptor.web.FragmentJarScannerCallback;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.descriptor.web.WebXmlParser;
import org.apache.tomcat.util.scan.JarFactory;
import org.apache.tomcat.util.scan.StandardJarScanFilter;
import org.apache.tomcat.util.scan.StandardJarScanner;


/**
 * Simple <code>ServletContext</code> implementation without
 * HTTP-specific methods.
 *
 * @author Peter Rossbach (pr@webapp.de)
 */

public class JspCServletContext implements ServletContext {


    // ----------------------------------------------------- Instance Variables


    /**
     * Servlet context attributes.
     */
    private final Map<String,Object> myAttributes;


    /**
     * Servlet context initialization parameters.
     */
    private final ConcurrentMap<String,String> myParameters = new ConcurrentHashMap<>();


    /**
     * The log writer we will write log messages to.
     */
    private final PrintWriter myLogWriter;


    /**
     * The base URL (document root) for this context.
     */
    private final URL myResourceBaseURL;


    /**
     * Merged web.xml for the application.
     */
    private WebXml webXml;


    private List<URL> resourceJARs;


    private JspConfigDescriptor jspConfigDescriptor;


    /**
     * Web application class loader.
     */
    private final ClassLoader loader;


    // ----------------------------------------------------------- Constructors

    /**
     * Create a new instance of this ServletContext implementation.
     *
     * @param aLogWriter PrintWriter which is used for <code>log()</code> calls
     * @param aResourceBaseURL Resource base URL
     * @param classLoader   Class loader for this {@link ServletContext}
     * @param validate      Should a validating parser be used to parse web.xml?
     * @param blockExternal Should external entities be blocked when parsing
     *                      web.xml?
     * @throws JasperException An error occurred building the merged web.xml
     */
    public JspCServletContext(PrintWriter aLogWriter, URL aResourceBaseURL,
            ClassLoader classLoader, boolean validate, boolean blockExternal)
            throws JasperException {

        myAttributes = new HashMap<>();
        myParameters.put(Constants.XML_BLOCK_EXTERNAL_INIT_PARAM,
                String.valueOf(blockExternal));
        myLogWriter = aLogWriter;
        myResourceBaseURL = aResourceBaseURL;
        this.loader = classLoader;
        this.webXml = buildMergedWebXml(validate, blockExternal);
        jspConfigDescriptor = webXml.getJspConfigDescriptor();
    }

    private WebXml buildMergedWebXml(boolean validate, boolean blockExternal)
            throws JasperException {
        WebXml webXml = new WebXml();
        WebXmlParser webXmlParser = new WebXmlParser(validate, validate, blockExternal);
        // Use this class's classloader as Ant will have set the TCCL to its own
        webXmlParser.setClassLoader(getClass().getClassLoader());

        try {
            URL url = getResource(
                    org.apache.tomcat.util.descriptor.web.Constants.WEB_XML_LOCATION);
            if (!webXmlParser.parseWebXml(url, webXml, false)) {
                throw new JasperException(Localizer.getMessage("jspc.error.invalidWebXml"));
            }
        } catch (IOException e) {
            throw new JasperException(e);
        }

        // if the application is metadata-complete then we can skip fragment processing
        if (webXml.isMetadataComplete()) {
            return webXml;
        }

        // If an empty absolute ordering element is present, fragment processing
        // may be skipped.
        Set<String> absoluteOrdering = webXml.getAbsoluteOrdering();
        if (absoluteOrdering != null && absoluteOrdering.isEmpty()) {
            return webXml;
        }

        Map<String, WebXml> fragments = scanForFragments(webXmlParser);
        Set<WebXml> orderedFragments = WebXml.orderWebFragments(webXml, fragments, this);

        // Find resource JARs
        this.resourceJARs = scanForResourceJARs(orderedFragments, fragments.values());

        // JspC is not affected by annotations so skip that processing, proceed to merge
        webXml.merge(orderedFragments);
        return webXml;
    }


    private List<URL> scanForResourceJARs(Set<WebXml> orderedFragments, Collection<WebXml> fragments)
            throws JasperException {
        List<URL> resourceJars = new ArrayList<>();
        // Build list of potential resource JARs. Use same ordering as ContextConfig
        Set<WebXml> resourceFragments = new LinkedHashSet<>();
        for (WebXml fragment : orderedFragments) {
            resourceFragments.add(fragment);
        }
        for (WebXml fragment : fragments) {
            if (!resourceFragments.contains(fragment)) {
                resourceFragments.add(fragment);
            }
        }

        for (WebXml resourceFragment : resourceFragments) {
            try (Jar jar = JarFactory.newInstance(resourceFragment.getURL())) {
                if (jar.exists("META-INF/resources/")) {
                    // This is a resource JAR
                    resourceJars.add(resourceFragment.getURL());
                }
                jar.close();
            } catch (IOException ioe) {
                throw new JasperException(ioe);
            }
        }

        return resourceJars;
    }


    private Map<String, WebXml> scanForFragments(WebXmlParser webXmlParser) throws JasperException {
        StandardJarScanner scanner = new StandardJarScanner();
        // TODO - enabling this means initializing the classloader first in JspC
        scanner.setScanClassPath(false);
        // TODO - configure filter rules from Ant rather then system properties
        scanner.setJarScanFilter(new StandardJarScanFilter());

        FragmentJarScannerCallback callback =
                new FragmentJarScannerCallback(webXmlParser, false, true);
        scanner.scan(JarScanType.PLUGGABILITY, this, callback);
        if (!callback.isOk()) {
            throw new JasperException(Localizer.getMessage("jspc.error.invalidFragment"));
        }
        return callback.getFragments();
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Return the specified context attribute, if any.
     *
     * @param name Name of the requested attribute
     */
    @Override
    public Object getAttribute(String name) {
        return myAttributes.get(name);
    }


    /**
     * Return an enumeration of context attribute names.
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(myAttributes.keySet());
    }


    /**
     * Return the servlet context for the specified path.
     *
     * @param uripath Server-relative path starting with '/'
     */
    @Override
    public ServletContext getContext(String uripath) {
        return null;
    }


    /**
     * Return the context path.
     */
    @Override
    public String getContextPath() {
        return null;
    }


    /**
     * Return the specified context initialization parameter.
     *
     * @param name Name of the requested parameter
     */
    @Override
    public String getInitParameter(String name) {
        return myParameters.get(name);
    }


    /**
     * Return an enumeration of the names of context initialization
     * parameters.
     */
    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(myParameters.keySet());
    }


    /**
     * Return the Servlet API major version number.
     */
    @Override
    public int getMajorVersion() {
        return 3;
    }


    /**
     * Return the MIME type for the specified filename.
     *
     * @param file Filename whose MIME type is requested
     */
    @Override
    public String getMimeType(String file) {
        return null;
    }


    /**
     * Return the Servlet API minor version number.
     */
    @Override
    public int getMinorVersion() {
        return 1;
    }


    /**
     * Return a request dispatcher for the specified servlet name.
     *
     * @param name Name of the requested servlet
     */
    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        return null;
    }


    /**
     * Return the real path for the specified context-relative
     * virtual path.
     *
     * @param path The context-relative virtual path to resolve
     */
    @Override
    public String getRealPath(String path) {
        if (!myResourceBaseURL.getProtocol().equals("file"))
            return null;
        if (!path.startsWith("/"))
            return null;
        try {
            File f = new File(getResource(path).toURI());
            return f.getAbsolutePath();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            return null;
        }
    }


    /**
     * Return a request dispatcher for the specified context-relative path.
     *
     * @param path Context-relative path for which to acquire a dispatcher
     */
    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }


    /**
     * Return a URL object of a resource that is mapped to the
     * specified context-relative path.
     *
     * @param path Context-relative path of the desired resource
     *
     * @exception MalformedURLException if the resource path is
     *  not properly formed
     */
    @Override
    public URL getResource(String path) throws MalformedURLException {

        if (!path.startsWith("/")) {
            throw new MalformedURLException("Path '" + path + "' does not start with '/'");
        }

        // Strip leading '/'
        path = path.substring(1);

        URL url = new URL(myResourceBaseURL, path);
        try (InputStream is = url.openStream()) {
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            url = null;
        }

        // During initialisation, getResource() is called before resourceJARs is
        // initialised
        if (url == null && resourceJARs != null) {
            String jarPath = "META-INF/resources/" + path;
            for (URL jarUrl : resourceJARs) {
                try (Jar jar = JarFactory.newInstance(jarUrl)) {
                    if (jar.exists(jarPath)) {
                        return new URL(jar.getURL(jarPath));
                    }
                } catch (IOException ioe) {
                    // Ignore
                }
            }
        }
        return url;
    }


    /**
     * Return an InputStream allowing access to the resource at the
     * specified context-relative path.
     *
     * @param path Context-relative path of the desired resource
     */
    @Override
    public InputStream getResourceAsStream(String path) {
        try {
            return getResource(path).openStream();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            return null;
        }
    }


    /**
     * Return the set of resource paths for the "directory" at the
     * specified context path.
     *
     * @param path Context-relative base path
     */
    @Override
    public Set<String> getResourcePaths(String path) {

        Set<String> thePaths = new HashSet<>();
        if (!path.endsWith("/")) {
            path += "/";
        }
        String basePath = getRealPath(path);
        if (basePath != null) {
            File theBaseDir = new File(basePath);
            if (theBaseDir.isDirectory()) {
                String theFiles[] = theBaseDir.list();
                if (theFiles != null) {
                    for (int i = 0; i < theFiles.length; i++) {
                        File testFile = new File(basePath + File.separator + theFiles[i]);
                        if (testFile.isFile()) {
                            thePaths.add(path + theFiles[i]);
                        } else if (testFile.isDirectory()) {
                            thePaths.add(path + theFiles[i] + "/");
                        }
                    }
                }
            }
        }

        // During initialisation, getResourcePaths() is called before
        // resourceJARs is initialised
        if (resourceJARs != null) {
            String jarPath = "META-INF/resources" + path;
            for (URL jarUrl : resourceJARs) {
                try (Jar jar = JarFactory.newInstance(jarUrl)) {
                    jar.nextEntry();
                    for (String entryName = jar.getEntryName();
                            entryName != null;
                            jar.nextEntry(), entryName = jar.getEntryName()) {
                        if (entryName.startsWith(jarPath) &&
                                entryName.length() > jarPath.length()) {
                            // Let the Set implementation handle duplicates
                            int sep = entryName.indexOf("/", jarPath.length());
                            if (sep < 0) {
                                // This is a file - strip leading "META-INF/resources"
                                thePaths.add(entryName.substring(18));
                            } else {
                                // This is a directory - strip leading "META-INF/resources"
                                thePaths.add(entryName.substring(18, sep + 1));
                            }
                        }
                    }
                } catch (IOException e) {
                    log(e.getMessage(), e);
                }
            }
        }

        return thePaths;
    }


    /**
     * Return descriptive information about this server.
     */
    @Override
    public String getServerInfo() {
        return ("JspC/ApacheTomcat8");
    }


    /**
     * Return a null reference for the specified servlet name.
     *
     * @param name Name of the requested servlet
     *
     * @deprecated This method has been deprecated with no replacement
     */
    @Override
    @Deprecated
    public Servlet getServlet(String name) throws ServletException {
        return null;
    }


    /**
     * Return the name of this servlet context.
     */
    @Override
    public String getServletContextName() {
        return getServerInfo();
    }


    /**
     * Return an empty enumeration of servlet names.
     *
     * @deprecated This method has been deprecated with no replacement
     */
    @Override
    @Deprecated
    public Enumeration<String> getServletNames() {
        return new Vector<String>().elements();
    }


    /**
     * Return an empty enumeration of servlets.
     *
     * @deprecated This method has been deprecated with no replacement
     */
    @Override
    @Deprecated
    public Enumeration<Servlet> getServlets() {
        return new Vector<Servlet>().elements();
    }


    /**
     * Log the specified message.
     *
     * @param message The message to be logged
     */
    @Override
    public void log(String message) {
        myLogWriter.println(message);
    }


    /**
     * Log the specified message and exception.
     *
     * @param exception The exception to be logged
     * @param message The message to be logged
     *
     * @deprecated Use log(String,Throwable) instead
     */
    @Override
    @Deprecated
    public void log(Exception exception, String message) {
        log(message, exception);
    }


    /**
     * Log the specified message and exception.
     *
     * @param message The message to be logged
     * @param exception The exception to be logged
     */
    @Override
    public void log(String message, Throwable exception) {
        myLogWriter.println(message);
        exception.printStackTrace(myLogWriter);
    }


    /**
     * Remove the specified context attribute.
     *
     * @param name Name of the attribute to remove
     */
    @Override
    public void removeAttribute(String name) {
        myAttributes.remove(name);
    }


    /**
     * Set or replace the specified context attribute.
     *
     * @param name Name of the context attribute to set
     * @param value Corresponding attribute value
     */
    @Override
    public void setAttribute(String name, Object value) {
        myAttributes.put(name, value);
    }


    @Override
    public FilterRegistration.Dynamic addFilter(String filterName,
            String className) {
        return null;
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName,
            String className) {
        return null;
    }


    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return EnumSet.noneOf(SessionTrackingMode.class);
    }


    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return EnumSet.noneOf(SessionTrackingMode.class);
    }


    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return null;
    }


    @Override
    public void setSessionTrackingModes(
            Set<SessionTrackingMode> sessionTrackingModes) {
        // Do nothing
    }


    @Override
    public Dynamic addFilter(String filterName, Filter filter) {
        return null;
    }


    @Override
    public Dynamic addFilter(String filterName,
            Class<? extends Filter> filterClass) {
        return null;
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName,
            Servlet servlet) {
        return null;
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName,
            Class<? extends Servlet> servletClass) {
        return null;
    }


    @Override
    public <T extends Filter> T createFilter(Class<T> c)
            throws ServletException {
        return null;
    }


    @Override
    public <T extends Servlet> T createServlet(Class<T> c)
            throws ServletException {
        return null;
    }


    @Override
    public FilterRegistration getFilterRegistration(String filterName) {
        return null;
    }


    @Override
    public ServletRegistration getServletRegistration(String servletName) {
        return null;
    }


    @Override
    public boolean setInitParameter(String name, String value) {
        return myParameters.putIfAbsent(name, value) == null;
    }


    @Override
    public void addListener(Class<? extends EventListener> listenerClass) {
        // NOOP
    }


    @Override
    public void addListener(String className) {
        // NOOP
    }


    @Override
    public <T extends EventListener> void addListener(T t) {
        // NOOP
    }


    @Override
    public <T extends EventListener> T createListener(Class<T> c)
            throws ServletException {
        return null;
    }


    @Override
    public void declareRoles(String... roleNames) {
        // NOOP
    }


    @Override
    public ClassLoader getClassLoader() {
        return loader;
    }


    @Override
    public int getEffectiveMajorVersion() {
        return webXml.getMajorVersion();
    }


    @Override
    public int getEffectiveMinorVersion() {
        return webXml.getMinorVersion();
    }


    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        return null;
    }


    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return jspConfigDescriptor;
    }


    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        return null;
    }


    @Override
    public String getVirtualServerName() {
        return null;
    }
}
