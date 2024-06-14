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
import java.net.URI;
import java.net.URISyntaxException;
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
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.FilterRegistration.Dynamic;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.descriptor.JspConfigDescriptor;

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
    private final Map<String,String> myParameters = new ConcurrentHashMap<>();


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
        Set<WebXml> resourceFragments = new LinkedHashSet<>(orderedFragments);
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

    @Override
    public Object getAttribute(String name) {
        return myAttributes.get(name);
    }


    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(myAttributes.keySet());
    }


    @Override
    public ServletContext getContext(String uripath) {
        return null;
    }


    @Override
    public String getContextPath() {
        return null;
    }


    @Override
    public String getInitParameter(String name) {
        return myParameters.get(name);
    }


    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(myParameters.keySet());
    }


    @Override
    public int getMajorVersion() {
        return 4;
    }


    @Override
    public String getMimeType(String file) {
        return null;
    }


    @Override
    public int getMinorVersion() {
        return 0;
    }


    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        return null;
    }


    @Override
    public String getRealPath(String path) {
        if (!myResourceBaseURL.getProtocol().equals("file")) {
            return null;
        }
        if (!path.startsWith("/")) {
            return null;
        }
        try {
            URL url = getResource(path);
            if (url == null) {
                return null;
            }
            File f = new File(url.toURI());
            return f.getAbsolutePath();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            return null;
        }
    }


    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }


    @Override
    public URL getResource(String path) throws MalformedURLException {

        if (!path.startsWith("/")) {
            throw new MalformedURLException(Localizer.getMessage("jsp.error.URLMustStartWithSlash", path));
        }

        // Strip leading '/'
        path = path.substring(1);

        URL url = null;
        try {
            URI uri = new URI(myResourceBaseURL.toExternalForm() + path);
            url = uri.toURL();
            try (InputStream is = url.openStream()) {
            }
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
                        return new URI(jar.getURL(jarPath)).toURL();
                    }
                } catch (IOException | URISyntaxException ioe) {
                    // Ignore
                }
            }
        }
        return url;
    }


    @Override
    public InputStream getResourceAsStream(String path) {
        try {
            URL url = getResource(path);
            if (url == null) {
                return null;
            }
            return url.openStream();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            return null;
        }
    }


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
                    for (String theFile : theFiles) {
                        File testFile = new File(basePath + File.separator + theFile);
                        if (testFile.isFile()) {
                            thePaths.add(path + theFile);
                        } else if (testFile.isDirectory()) {
                            thePaths.add(path + theFile + "/");
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
                            int sep = entryName.indexOf('/', jarPath.length());
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


    @Override
    public String getServerInfo() {
        return "JspC/ApacheTomcat11";
    }


    @Override
    public String getServletContextName() {
        return getServerInfo();
    }


    @Override
    public void log(String message) {
        myLogWriter.println(message);
    }


    @Override
    public void log(String message, Throwable exception) {
        myLogWriter.println(message);
        exception.printStackTrace(myLogWriter);
    }


    @Override
    public void removeAttribute(String name) {
        myAttributes.remove(name);
    }


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
    public ServletRegistration.Dynamic addJspFile(String jspName, String jspFile) {
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

    @Override
    public int getSessionTimeout() {
        return 0;
    }

    @Override
    public void setSessionTimeout(int sessionTimeout) {
        // NO-OP
    }

    @Override
    public String getRequestCharacterEncoding() {
        return null;
    }

    @Override
    public void setRequestCharacterEncoding(String encoding) {
        // NO-OP
    }

    @Override
    public String getResponseCharacterEncoding() {
        return null;
    }

    @Override
    public void setResponseCharacterEncoding(String encoding) {
        // NO-OP
    }
}
