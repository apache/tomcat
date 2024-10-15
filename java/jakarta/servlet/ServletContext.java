/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.servlet;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.descriptor.JspConfigDescriptor;

/**
 * Defines a set of methods that a servlet uses to communicate with its servlet container, for example, to get the MIME
 * type of a file, dispatch requests, or write to a log file.
 * <p>
 * There is one context per "web application" per Java Virtual Machine. (A "web application" is a collection of servlets
 * and content installed under a specific subset of the server's URL namespace such as <code>/catalog</code> and
 * possibly installed via a <code>.war</code> file.)
 * <p>
 * In the case of a web application marked "distributed" in its deployment descriptor, there will be one context
 * instance for each virtual machine. In this situation, the context cannot be used as a location to share global
 * information (because the information won't be truly global). Use an external resource like a database instead.
 * <p>
 * The <code>ServletContext</code> object is contained within the {@link ServletConfig} object, which the Web server
 * provides the servlet when the servlet is initialized.
 *
 * @see Servlet#getServletConfig
 * @see ServletConfig#getServletContext
 */
public interface ServletContext {

    /**
     * The name of the ServletContext attribute that holds the temporary file location for the web application.
     */
    String TEMPDIR = "jakarta.servlet.context.tempdir";

    /**
     * The name of the ServletContext attribute that holds the ordered list of web fragments for this web application.
     *
     * @since Servlet 3.0
     */
    String ORDERED_LIBS = "jakarta.servlet.context.orderedLibs";

    /**
     * Return the main path associated with this context.
     *
     * @return The main context path
     *
     * @since Servlet 2.5
     */
    String getContextPath();

    /**
     * Returns a <code>ServletContext</code> object that corresponds to a specified URL on the server.
     * <p>
     * This method allows servlets to gain access to the context for various parts of the server, and as needed obtain
     * {@link RequestDispatcher} objects from the context. The given path must be begin with "/", is interpreted
     * relative to the server's document root and is matched against the context roots of other web applications hosted
     * on this container.
     * <p>
     * In a security conscious environment, the servlet container may return <code>null</code> for a given URL.
     *
     * @param uripath a <code>String</code> specifying the context path of another web application in the container.
     *
     * @return the <code>ServletContext</code> object that corresponds to the named URL, or null if either none exists
     *             or the container wishes to restrict this access.
     *
     * @see RequestDispatcher
     */
    ServletContext getContext(String uripath);

    /**
     * Returns the major version of the Java Servlet API that this servlet container supports. All implementations that
     * comply with Version 6.0 must have this method return the integer 6.
     *
     * @return 6
     */
    int getMajorVersion();

    /**
     * Returns the minor version of the Servlet API that this servlet container supports. All implementations that
     * comply with Version 6.0 must have this method return the integer 0.
     *
     * @return 0
     */
    int getMinorVersion();

    /**
     * Obtain the major version of the servlet specification for which this web application is implemented.
     *
     * @return The major version declared in web.xml
     *
     * @since Servlet 3.0
     */
    int getEffectiveMajorVersion();

    /**
     * Obtain the minor version of the servlet specification for which this web application is implemented.
     *
     * @return The minor version declared in web.xml
     *
     * @since Servlet 3.0
     */
    int getEffectiveMinorVersion();

    /**
     * Returns the MIME type of the specified file, or <code>null</code> if the MIME type is not known. The MIME type is
     * determined by the configuration of the servlet container, and may be specified in a web application deployment
     * descriptor. Common MIME types are <code>"text/html"</code> and <code>"image/gif"</code>.
     *
     * @param file a <code>String</code> specifying the name of a file
     *
     * @return a <code>String</code> specifying the file's MIME type
     */
    String getMimeType(String file);

    /**
     * Returns a directory-like listing of all the paths to resources within the web application whose longest sub-path
     * matches the supplied path argument. Paths indicating subdirectory paths end with a '/'. The returned paths are
     * all relative to the root of the web application and have a leading '/'. For example, for a web application
     * containing<br>
     * <br>
     * /welcome.html<br>
     * /catalog/index.html<br>
     * /catalog/products.html<br>
     * /catalog/offers/books.html<br>
     * /catalog/offers/music.html<br>
     * /customer/login.jsp<br>
     * /WEB-INF/web.xml<br>
     * /WEB-INF/classes/com.acme.OrderServlet.class,<br>
     * <br>
     * getResourcePaths("/") returns {"/welcome.html", "/catalog/", "/customer/", "/WEB-INF/"}<br>
     * getResourcePaths("/catalog/") returns {"/catalog/index.html", "/catalog/products.html", "/catalog/offers/"}.<br>
     *
     * @param path the partial path used to match the resources, which must start with a /
     *
     * @return a Set containing the directory listing, or null if there are no resources in the web application whose
     *             path begins with the supplied path.
     *
     * @since Servlet 2.3
     */
    Set<String> getResourcePaths(String path);

    /**
     * Returns a URL to the resource that is mapped to a specified path. The path must begin with a "/" and is
     * interpreted as relative to the current context root.
     * <p>
     * This method allows the servlet container to make a resource available to servlets from any source. Resources can
     * be located on a local or remote file system, in a database, or in a <code>.war</code> file.
     * <p>
     * The servlet container must implement the URL handlers and <code>URLConnection</code> objects that are necessary
     * to access the resource.
     * <p>
     * This method returns <code>null</code> if no resource is mapped to the pathname.
     * <p>
     * Some containers may allow writing to the URL returned by this method using the methods of the URL class.
     * <p>
     * The resource content is returned directly, so be aware that requesting a <code>.jsp</code> page returns the JSP
     * source code. Use a <code>RequestDispatcher</code> instead to include results of an execution.
     * <p>
     * This method has a different purpose than <code>java.lang.Class.getResource</code>, which looks up resources based
     * on a class loader. This method does not use class loaders.
     *
     * @param path a <code>String</code> specifying the path to the resource
     *
     * @return the resource located at the named path, or <code>null</code> if there is no resource at that path
     *
     * @exception MalformedURLException if the pathname is not given in the correct form
     */
    URL getResource(String path) throws MalformedURLException;

    /**
     * Returns the resource located at the named path as an <code>InputStream</code> object.
     * <p>
     * The data in the <code>InputStream</code> can be of any type or length. The path must be specified according to
     * the rules given in <code>getResource</code>. This method returns <code>null</code> if no resource exists at the
     * specified path.
     * <p>
     * Meta-information such as content length and content type that is available via <code>getResource</code> method is
     * lost when using this method.
     * <p>
     * The servlet container must implement the URL handlers and <code>URLConnection</code> objects necessary to access
     * the resource.
     * <p>
     * This method is different from <code>java.lang.Class.getResourceAsStream</code>, which uses a class loader. This
     * method allows servlet containers to make a resource available to a servlet from any location, without using a
     * class loader.
     *
     * @param path a <code>String</code> specifying the path to the resource
     *
     * @return the <code>InputStream</code> returned to the servlet, or <code>null</code> if no resource exists at the
     *             specified path
     */
    InputStream getResourceAsStream(String path);

    /**
     * Returns a {@link RequestDispatcher} object that acts as a wrapper for the resource located at the given path. A
     * <code>RequestDispatcher</code> object can be used to forward a request to the resource or to include the resource
     * in a response. The resource can be dynamic or static.
     * <p>
     * The pathname must begin with a "/" and is interpreted as relative to the current context root. Use
     * <code>getContext</code> to obtain a <code>RequestDispatcher</code> for resources in foreign contexts. This method
     * returns <code>null</code> if the <code>ServletContext</code> cannot return a <code>RequestDispatcher</code>.
     *
     * @param path a <code>String</code> specifying the pathname to the resource
     *
     * @return a <code>RequestDispatcher</code> object that acts as a wrapper for the resource at the specified path, or
     *             <code>null</code> if the <code>ServletContext</code> cannot return a <code>RequestDispatcher</code>
     *
     * @see RequestDispatcher
     * @see ServletContext#getContext
     */
    RequestDispatcher getRequestDispatcher(String path);

    /**
     * Returns a {@link RequestDispatcher} object that acts as a wrapper for the named servlet.
     * <p>
     * Servlets (and JSP pages also) may be given names via server administration or via a web application deployment
     * descriptor. A servlet instance can determine its name using {@link ServletConfig#getServletName}.
     * <p>
     * This method returns <code>null</code> if the <code>ServletContext</code> cannot return a
     * <code>RequestDispatcher</code> for any reason.
     *
     * @param name a <code>String</code> specifying the name of a servlet to wrap
     *
     * @return a <code>RequestDispatcher</code> object that acts as a wrapper for the named servlet, or
     *             <code>null</code> if the <code>ServletContext</code> cannot return a <code>RequestDispatcher</code>
     *
     * @see RequestDispatcher
     * @see ServletContext#getContext
     * @see ServletConfig#getServletName
     */
    RequestDispatcher getNamedDispatcher(String name);

    /**
     * Writes the specified message to a servlet log file, usually an event log. The name and type of the servlet log
     * file is specific to the servlet container.
     *
     * @param msg a <code>String</code> specifying the message to be written to the log file
     */
    void log(String msg);

    /**
     * Writes an explanatory message and a stack trace for a given <code>Throwable</code> exception to the servlet log
     * file. The name and type of the servlet log file is specific to the servlet container, usually an event log.
     *
     * @param message   a <code>String</code> that describes the error or exception
     * @param throwable the <code>Throwable</code> error or exception
     */
    void log(String message, Throwable throwable);

    /**
     * Returns a <code>String</code> containing the real path for a given virtual path. For example, the path
     * "/index.html" returns the absolute file path on the server's filesystem would be served by a request for
     * "http://host/contextPath/index.html", where contextPath is the context path of this ServletContext..
     * <p>
     * The real path returned will be in a form appropriate to the computer and operating system on which the servlet
     * container is running, including the proper path separators. This method returns <code>null</code> if the servlet
     * container cannot translate the virtual path to a real path for any reason (such as when the content is being made
     * available from a <code>.war</code> archive).
     *
     * @param path a <code>String</code> specifying a virtual path
     *
     * @return a <code>String</code> specifying the real path, or null if the translation cannot be performed
     */
    String getRealPath(String path);

    /**
     * Returns the name and version of the servlet container on which the servlet is running.
     * <p>
     * The form of the returned string is <i>servername</i>/<i>versionnumber</i>. For example, the JavaServer Web
     * Development Kit may return the string <code>JavaServer Web Dev Kit/1.0</code>.
     * <p>
     * The servlet container may return other optional information after the primary string in parentheses, for example,
     * <code>JavaServer Web Dev Kit/1.0 (JDK 1.1.6; Windows NT 4.0 x86)</code>.
     *
     * @return a <code>String</code> containing at least the servlet container name and version number
     */
    String getServerInfo();

    /**
     * Returns a <code>String</code> containing the value of the named context-wide initialization parameter, or
     * <code>null</code> if the parameter does not exist.
     * <p>
     * This method can make available configuration information useful to an entire "web application". For example, it
     * can provide a web site administrator's email address or the name of a system that holds critical data.
     *
     * @param name a <code>String</code> containing the name of the parameter whose value is requested
     *
     * @return a <code>String</code> containing the value of the initialization parameter
     *
     * @throws NullPointerException If the provided parameter name is <code>null</code>
     *
     * @see ServletConfig#getInitParameter
     */
    String getInitParameter(String name);

    /**
     * Returns the names of the context's initialization parameters as an <code>Enumeration</code> of
     * <code>String</code> objects, or an empty <code>Enumeration</code> if the context has no initialization
     * parameters.
     *
     * @return an <code>Enumeration</code> of <code>String</code> objects containing the names of the context's
     *             initialization parameters
     *
     * @see ServletConfig#getInitParameter
     */

    Enumeration<String> getInitParameterNames();

    /**
     * Set the given initialisation parameter to the given value.
     *
     * @param name  Name of initialisation parameter
     * @param value Value for initialisation parameter
     *
     * @return <code>true</code> if the call succeeds or <code>false</code> if the call fails because an initialisation
     *             parameter with the same name has already been set
     *
     * @throws IllegalStateException         If initialisation of this ServletContext has already completed
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     * @throws NullPointerException          If the provided parameter name is <code>null</code>
     *
     * @since Servlet 3.0
     */
    boolean setInitParameter(String name, String value);

    /**
     * Returns the servlet container attribute with the given name, or <code>null</code> if there is no attribute by
     * that name. An attribute allows a servlet container to give the servlet additional information not already
     * provided by this interface. See your server documentation for information about its attributes. A list of
     * supported attributes can be retrieved using <code>getAttributeNames</code>.
     * <p>
     * The attribute is returned as a <code>java.lang.Object</code> or some subclass. Attribute names should follow the
     * same convention as package names. The Jakarta EE platform reserves names matching <code>jakarta.*</code>.
     *
     * @param name a <code>String</code> specifying the name of the attribute
     *
     * @return an <code>Object</code> containing the value of the attribute, or <code>null</code> if no attribute exists
     *             matching the given name
     *
     * @throws NullPointerException If the provided attribute name is <code>null</code>
     *
     * @see ServletContext#getAttributeNames
     */
    Object getAttribute(String name);

    /**
     * Returns an <code>Enumeration</code> containing the attribute names available within this servlet context. Use the
     * {@link #getAttribute} method with an attribute name to get the value of an attribute.
     *
     * @return an <code>Enumeration</code> of attribute names
     *
     * @see #getAttribute
     */
    Enumeration<String> getAttributeNames();

    /**
     * Binds an object to a given attribute name in this servlet context. If the name specified is already used for an
     * attribute, this method will replace the attribute with the new to the new attribute.
     * <p>
     * If listeners are configured on the <code>ServletContext</code> the container notifies them accordingly.
     * <p>
     * If a null value is passed, the effect is the same as calling <code>removeAttribute()</code>.
     * <p>
     * Attribute names should follow the same convention as package names. The Jakarta EE platform reserves names
     * matching <code>jakarta.*</code>.
     *
     * @param name   a <code>String</code> specifying the name of the attribute
     * @param object an <code>Object</code> representing the attribute to be bound
     *
     * @throws NullPointerException If the provided attribute name is <code>null</code>
     */
    void setAttribute(String name, Object object);

    /**
     * Removes the attribute with the given name from the servlet context. After removal, subsequent calls to
     * {@link #getAttribute} to retrieve the attribute's value will return <code>null</code>.
     * <p>
     * If listeners are configured on the <code>ServletContext</code> the container notifies them accordingly.
     *
     * @param name a <code>String</code> specifying the name of the attribute to be removed
     */
    void removeAttribute(String name);

    /**
     * Returns the name of this web application corresponding to this ServletContext as specified in the deployment
     * descriptor for this web application by the display-name element.
     *
     * @return The name of the web application or null if no name has been declared in the deployment descriptor.
     *
     * @since Servlet 2.3
     */
    String getServletContextName();

    /**
     * Register a servlet implementation for use in this ServletContext.
     *
     * @param servletName The name of the servlet to register
     * @param className   The implementation class for the servlet
     *
     * @return The registration object that enables further configuration
     *
     * @throws IllegalStateException         If the context has already been initialised
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    ServletRegistration.Dynamic addServlet(String servletName, String className);

    /**
     * Register a servlet instance for use in this ServletContext.
     *
     * @param servletName The name of the servlet to register
     * @param servlet     The Servlet instance to register
     *
     * @return The registration object that enables further configuration
     *
     * @throws IllegalStateException         If the context has already been initialised
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet);

    /**
     * Add servlet to the context.
     *
     * @param servletName  Name of servlet to add
     * @param servletClass Class of servlet to add
     *
     * @return <code>null</code> if the servlet has already been fully defined, else a
     *             {@link jakarta.servlet.ServletRegistration.Dynamic} object that can be used to further configure the
     *             servlet
     *
     * @throws IllegalStateException         If the context has already been initialised
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass);

    /**
     * Add a JSP to the context.
     *
     * @param jspName The servlet name under which this JSP file should be registered
     * @param jspFile The path, relative to the web application root, for the JSP file to be used for this servlet
     *
     * @return a {@link jakarta.servlet.ServletRegistration.Dynamic} object that can be used to further configure the
     *             servlet
     *
     * @since Servlet 4.0
     */
    ServletRegistration.Dynamic addJspFile(String jspName, String jspFile);

    /**
     * Create an Servlet instance using the given class. The instance is just created. No initialisation occurs.
     *
     * @param <T> The type for the given class
     * @param c   The the class for which an instance should be created
     *
     * @return The created Servlet instance.
     *
     * @throws ServletException              If the servlet instance cannot be created.
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    <T extends Servlet> T createServlet(Class<T> c) throws ServletException;

    /**
     * Obtain the details of the named servlet.
     *
     * @param servletName The name of the Servlet of interest
     *
     * @return The registration details for the named Servlet or <code>null</code> if no Servlet has been registered
     *             with the given name
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    ServletRegistration getServletRegistration(String servletName);

    /**
     * Obtain a Map of servlet names to servlet registrations for all servlets registered with this context.
     *
     * @return A Map of servlet names to servlet registrations for all servlets registered with this context
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    Map<String,? extends ServletRegistration> getServletRegistrations();

    /**
     * Add filter to context.
     *
     * @param filterName Name of filter to add
     * @param className  Name of filter class
     *
     * @return <code>null</code> if the filter has already been fully defined, else a
     *             {@link jakarta.servlet.FilterRegistration.Dynamic} object that can be used to further configure the
     *             filter
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     * @throws IllegalStateException         If the context has already been initialised
     *
     * @since Servlet 3.0
     */
    FilterRegistration.Dynamic addFilter(String filterName, String className);

    /**
     * Add filter to context.
     *
     * @param filterName Name of filter to add
     * @param filter     Filter to add
     *
     * @return <code>null</code> if the filter has already been fully defined, else a
     *             {@link jakarta.servlet.FilterRegistration.Dynamic} object that can be used to further configure the
     *             filter
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     * @throws IllegalStateException         If the context has already been initialised
     *
     * @since Servlet 3.0
     */
    FilterRegistration.Dynamic addFilter(String filterName, Filter filter);

    /**
     * Add filter to context.
     *
     * @param filterName  Name of filter to add
     * @param filterClass Class of filter to add
     *
     * @return <code>null</code> if the filter has already been fully defined, else a
     *             {@link jakarta.servlet.FilterRegistration.Dynamic} object that can be used to further configure the
     *             filter
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     * @throws IllegalStateException         If the context has already been initialised
     *
     * @since Servlet 3.0
     */
    FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass);

    /**
     * Create a Filter instance using the given class. The instance is just created. No initialisation occurs.
     *
     * @param <T> The type for the given class
     * @param c   The the class for which an instance should be created
     *
     * @return The created Filter instance.
     *
     * @throws ServletException              If the Filter instance cannot be created
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    <T extends Filter> T createFilter(Class<T> c) throws ServletException;

    /**
     * TODO SERVLET3 - Add comments
     *
     * @param filterName TODO
     *
     * @return TODO
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    FilterRegistration getFilterRegistration(String filterName);

    /**
     * @return TODO
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0 TODO SERVLET3 - Add comments
     */
    Map<String,? extends FilterRegistration> getFilterRegistrations();

    /**
     * @return TODO
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0 TODO SERVLET3 - Add comments
     */
    SessionCookieConfig getSessionCookieConfig();

    /**
     * Configures the available session tracking modes for this web application.
     *
     * @param sessionTrackingModes The session tracking modes to use for this web application
     *
     * @throws IllegalArgumentException      If sessionTrackingModes specifies {@link SessionTrackingMode#SSL} in
     *                                           combination with any other {@link SessionTrackingMode}
     * @throws IllegalStateException         If the context has already been initialised
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes);

    /**
     * Obtains the default session tracking modes for this web application. By default {@link SessionTrackingMode#URL}
     * is always supported, {@link SessionTrackingMode#COOKIE} is supported unless the <code>cookies</code> attribute
     * has been set to <code>false</code> for the context and {@link SessionTrackingMode#SSL} is supported if at least
     * one of the connectors used by this context has the attribute <code>secure</code> set to <code>true</code>.
     *
     * @return The set of default session tracking modes for this web application
     *
     * @since Servlet 3.0
     */
    Set<SessionTrackingMode> getDefaultSessionTrackingModes();

    /**
     * Obtains the currently enabled session tracking modes for this web application.
     *
     * @return The value supplied via {@link #setSessionTrackingModes(Set)} if one was previously set, else return the
     *             defaults
     *
     * @since Servlet 3.0
     */
    Set<SessionTrackingMode> getEffectiveSessionTrackingModes();

    /**
     * TODO SERVLET3 - Add comments
     *
     * @param className TODO
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    void addListener(String className);

    /**
     * TODO SERVLET3 - Add comments
     *
     * @param <T> TODO
     * @param t   TODO
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    <T extends EventListener> void addListener(T t);

    /**
     * TODO SERVLET3 - Add comments
     *
     * @param listenerClass TODO
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    void addListener(Class<? extends EventListener> listenerClass);

    /**
     * TODO SERVLET3 - Add comments
     *
     * @param <T> TODO
     * @param c   TODO
     *
     * @return TODO
     *
     * @throws ServletException              TODO
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     *
     * @since Servlet 3.0
     */
    <T extends EventListener> T createListener(Class<T> c) throws ServletException;

    /**
     * @return TODO
     *
     * @since Servlet 3.0 TODO SERVLET3 - Add comments
     */
    JspConfigDescriptor getJspConfigDescriptor();

    /**
     * Get the web application class loader associated with this ServletContext.
     *
     * @return The associated web application class loader
     *
     * @since Servlet 3.0
     */
    ClassLoader getClassLoader();

    /**
     * Add to the declared roles for this ServletContext.
     *
     * @param roleNames The roles to add
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     * @throws IllegalArgumentException      If the list of roleNames is null or empty
     * @throws IllegalStateException         If the ServletContext has already been initialised
     *
     * @since Servlet 3.0
     */
    void declareRoles(String... roleNames);

    /**
     * Get the primary name of the virtual host on which this context is deployed. The name may or may not be a valid
     * host name.
     *
     * @return The primary name of the virtual host on which this context is deployed
     *
     * @since Servlet 3.1
     */
    String getVirtualServerName();

    /**
     * Get the default session timeout.
     *
     * @return The current default session timeout in minutes
     *
     * @since Servlet 4.0
     */
    int getSessionTimeout();

    /**
     * Set the default session timeout. This method may only be called before the ServletContext is initialised.
     *
     * @param sessionTimeout The new default session timeout in minutes.
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     * @throws IllegalStateException         If the ServletContext has already been initialised
     *
     * @since Servlet 4.0
     */
    void setSessionTimeout(int sessionTimeout);

    /**
     * Get the default character encoding for reading request bodies.
     *
     * @return The character encoding name or {@code null} if no default has been specified
     *
     * @since Servlet 4.0
     */
    String getRequestCharacterEncoding();

    /**
     * Set the default character encoding to use for reading request bodies. Calling this method will over-ride any
     * value set in the deployment descriptor.
     *
     * @param encoding The name of the character encoding to use
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     * @throws IllegalStateException         If the ServletContext has already been initialised
     *
     * @since Servlet 4.0
     */
    void setRequestCharacterEncoding(String encoding);

    /**
     * Sets the request character encoding for this ServletContext.
     * <p>
     * Implementations are strongly encouraged to override this default method and provide a more efficient
     * implementation.
     *
     * @param encoding request character encoding
     *
     * @throws IllegalStateException         if this ServletContext has already been initialized
     * @throws UnsupportedOperationException if this ServletContext was passed to the
     *                                           {@link ServletContextListener#contextInitialized} method of a
     *                                           {@link ServletContextListener} that was neither declared in
     *                                           {@code web.xml} or {@code web-fragment.xml}, nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}
     *
     * @since Servlet 6.1
     */
    default void setRequestCharacterEncoding(Charset encoding) {
        setRequestCharacterEncoding(encoding.name());
    }

    /**
     * Get the default character encoding for writing response bodies.
     *
     * @return The character encoding name or {@code null} if no default has been specified
     *
     * @since Servlet 4.0
     */
    String getResponseCharacterEncoding();

    /**
     * Sets the response character encoding for this ServletContext.
     * <p>
     * Implementations are strongly encouraged to override this default method and provide a more efficient
     * implementation.
     *
     * @param encoding response character encoding
     *
     * @throws IllegalStateException         if this ServletContext has already been initialized
     * @throws UnsupportedOperationException if this ServletContext was passed to the
     *                                           {@link ServletContextListener#contextInitialized} method of a
     *                                           {@link ServletContextListener} that was neither declared in
     *                                           {@code web.xml} or {@code web-fragment.xml}, nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}
     *
     * @since Servlet 6.1
     */
    default void setResponseCharacterEncoding(Charset encoding) {
        setResponseCharacterEncoding(encoding.name());
    }

    /**
     * Set the default character encoding to use for writing response bodies. Calling this method will over-ride any
     * value set in the deployment descriptor.
     *
     * @param encoding The name of the character encoding to use
     *
     * @throws UnsupportedOperationException If called from a
     *                                           {@link ServletContextListener#contextInitialized(ServletContextEvent)}
     *                                           method of a {@link ServletContextListener} that was not defined in a
     *                                           web.xml file, a web-fragment.xml file nor annotated with
     *                                           {@link jakarta.servlet.annotation.WebListener}. For example, a
     *                                           {@link ServletContextListener} defined in a TLD would not be able to
     *                                           use this method.
     * @throws IllegalStateException         If the ServletContext has already been initialised
     *
     * @since Servlet 4.0
     */
    void setResponseCharacterEncoding(String encoding);
}
