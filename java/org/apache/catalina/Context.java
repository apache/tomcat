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
package org.apache.catalina;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletSecurityElement;
import jakarta.servlet.descriptor.JspConfigDescriptor;

import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.tomcat.ContextBind;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.apache.tomcat.util.descriptor.web.ApplicationParameter;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.file.ConfigurationSource.Resource;
import org.apache.tomcat.util.http.CookieProcessor;

/**
 * A <b>Context</b> is a Container that represents a servlet context, and therefore an individual web application, in
 * the Catalina servlet engine. It is therefore useful in almost every deployment of Catalina (even if a Connector
 * attached to a web server (such as Apache) uses the web server's facilities to identify the appropriate Wrapper to
 * handle this request. It also provides a convenient mechanism to use Interceptors that see every request processed by
 * this particular web application.
 * <p>
 * The parent Container attached to a Context is generally a Host, but may be some other implementation, or may be
 * omitted if it is not necessary.
 * <p>
 * The child containers attached to a Context are generally implementations of Wrapper (representing individual servlet
 * definitions).
 * <p>
 *
 * @author Craig R. McClanahan
 */
public interface Context extends Container, ContextBind {


    // ----------------------------------------------------- Manifest Constants

    /**
     * Container event for adding a welcome file.
     */
    String ADD_WELCOME_FILE_EVENT = "addWelcomeFile";

    /**
     * Container event for removing a wrapper.
     */
    String REMOVE_WELCOME_FILE_EVENT = "removeWelcomeFile";

    /**
     * Container event for clearing welcome files.
     */
    String CLEAR_WELCOME_FILES_EVENT = "clearWelcomeFiles";

    /**
     * Container event for changing the ID of a session.
     */
    String CHANGE_SESSION_ID_EVENT = "changeSessionId";

    /**
     * Prefix for resource lookup.
     */
    String WEBAPP_PROTOCOL = "webapp:";


    // ------------------------------------------------------------- Properties

    /**
     * Returns <code>true</code> if requests mapped to servlets without "multipart config" to parse multipart/form-data
     * requests anyway.
     *
     * @return <code>true</code> if requests mapped to servlets without "multipart config" to parse multipart/form-data
     *             requests, <code>false</code> otherwise.
     */
    boolean getAllowCasualMultipartParsing();


    /**
     * Set to <code>true</code> to allow requests mapped to servlets that do not explicitly declare @MultipartConfig or
     * have &lt;multipart-config&gt; specified in web.xml to parse multipart/form-data requests.
     *
     * @param allowCasualMultipartParsing <code>true</code> to allow such casual parsing, <code>false</code> otherwise.
     */
    void setAllowCasualMultipartParsing(boolean allowCasualMultipartParsing);


    /**
     * Obtain the registered application event listeners.
     *
     * @return An array containing the application event listener instances for this web application in the order they
     *             were specified in the web application deployment descriptor
     */
    Object[] getApplicationEventListeners();


    /**
     * Store the set of initialized application event listener objects, in the order they were specified in the web
     * application deployment descriptor, for this application.
     *
     * @param listeners The set of instantiated listener objects.
     */
    void setApplicationEventListeners(Object listeners[]);


    /**
     * Obtain the registered application lifecycle listeners.
     *
     * @return An array containing the application lifecycle listener instances for this web application in the order
     *             they were specified in the web application deployment descriptor
     */
    Object[] getApplicationLifecycleListeners();


    /**
     * Store the set of initialized application lifecycle listener objects, in the order they were specified in the web
     * application deployment descriptor, for this application.
     *
     * @param listeners The set of instantiated listener objects.
     */
    void setApplicationLifecycleListeners(Object listeners[]);


    /**
     * Obtain the character set name to use with the given Locale. Note that different Contexts may have different
     * mappings of Locale to character set.
     *
     * @param locale The locale for which the mapped character set should be returned
     *
     * @return The name of the character set to use with the given Locale
     */
    String getCharset(Locale locale);


    /**
     * Return the URL of the XML descriptor for this context.
     *
     * @return The URL of the XML descriptor for this context
     */
    URL getConfigFile();


    /**
     * Set the URL of the XML descriptor for this context.
     *
     * @param configFile The URL of the XML descriptor for this context.
     */
    void setConfigFile(URL configFile);


    /**
     * Return the "correctly configured" flag for this Context.
     *
     * @return <code>true</code> if the Context has been correctly configured, otherwise <code>false</code>
     */
    boolean getConfigured();


    /**
     * Set the "correctly configured" flag for this Context. This can be set to false by startup listeners that detect a
     * fatal configuration error to avoid the application from being made available.
     *
     * @param configured The new correctly configured flag
     */
    void setConfigured(boolean configured);


    /**
     * Return the "use cookies for session ids" flag.
     *
     * @return <code>true</code> if it is permitted to use cookies to track session IDs for this web application,
     *             otherwise <code>false</code>
     */
    boolean getCookies();


    /**
     * Set the "use cookies for session ids" flag.
     *
     * @param cookies The new flag
     */
    void setCookies(boolean cookies);


    /**
     * Gets the name to use for session cookies. Overrides any setting that may be specified by the application.
     *
     * @return The value of the default session cookie name or null if not specified
     */
    String getSessionCookieName();


    /**
     * Sets the name to use for session cookies. Overrides any setting that may be specified by the application.
     *
     * @param sessionCookieName The name to use
     */
    void setSessionCookieName(String sessionCookieName);


    /**
     * Gets the value of the use HttpOnly cookies for session cookies flag.
     *
     * @return <code>true</code> if the HttpOnly flag should be set on session cookies
     */
    boolean getUseHttpOnly();


    /**
     * Sets the use HttpOnly cookies for session cookies flag.
     *
     * @param useHttpOnly Set to <code>true</code> to use HttpOnly cookies for session cookies
     */
    void setUseHttpOnly(boolean useHttpOnly);


    /**
     * Should the {@code Partitioned} attribute be added to session cookies created for this web application.
     * <p>
     * The name of the attribute used to indicate a partitioned cookie as part of
     * <a href="https://developers.google.com/privacy-sandbox/3pcd#partitioned">CHIPS</a> is not defined by an RFC and
     * may change in a non-backwards compatible way once equivalent functionality is included in an RFC.
     *
     * @return {@code true} if the {@code Partitioned} attribute should be added to session cookies created for this web
     *             application, otherwise {@code false}
     */
    boolean getUsePartitioned();


    /**
     * Configure whether the {@code Partitioned} attribute should be added to session cookies created for this web
     * application.
     * <p>
     * The name of the attribute used to indicate a partitioned cookie as part of
     * <a href="https://developers.google.com/privacy-sandbox/3pcd#partitioned">CHIPS</a> is not defined by an RFC and
     * may change in a non-backwards compatible way once equivalent functionality is included in an RFC.
     *
     * @param usePartitioned {@code true} if the {@code Partitioned} attribute should be added to session cookies
     *                           created for this web application, otherwise {@code false}
     */
    void setUsePartitioned(boolean usePartitioned);


    /**
     * Gets the domain to use for session cookies. Overrides any setting that may be specified by the application.
     *
     * @return The value of the default session cookie domain or null if not specified
     */
    String getSessionCookieDomain();


    /**
     * Sets the domain to use for session cookies. Overrides any setting that may be specified by the application.
     *
     * @param sessionCookieDomain The domain to use
     */
    void setSessionCookieDomain(String sessionCookieDomain);


    /**
     * Gets the path to use for session cookies. Overrides any setting that may be specified by the application.
     *
     * @return The value of the default session cookie path or null if not specified
     */
    String getSessionCookiePath();


    /**
     * Sets the path to use for session cookies. Overrides any setting that may be specified by the application.
     *
     * @param sessionCookiePath The path to use
     */
    void setSessionCookiePath(String sessionCookiePath);


    /**
     * Is a / added to the end of the session cookie path to ensure browsers, particularly IE, don't send a session
     * cookie for context /foo with requests intended for context /foobar.
     *
     * @return <code>true</code> if the slash is added, otherwise <code>false</code>
     */
    boolean getSessionCookiePathUsesTrailingSlash();


    /**
     * Configures if a / is added to the end of the session cookie path to ensure browsers, particularly IE, don't send
     * a session cookie for context /foo with requests intended for context /foobar.
     *
     * @param sessionCookiePathUsesTrailingSlash <code>true</code> if the slash is should be added, otherwise
     *                                               <code>false</code>
     */
    void setSessionCookiePathUsesTrailingSlash(boolean sessionCookiePathUsesTrailingSlash);


    /**
     * Return the "allow crossing servlet contexts" flag.
     *
     * @return <code>true</code> if cross-contest requests are allowed from this web applications, otherwise
     *             <code>false</code>
     */
    boolean getCrossContext();


    /**
     * Return the alternate Deployment Descriptor name.
     *
     * @return the name
     */
    String getAltDDName();


    /**
     * Set an alternate Deployment Descriptor name.
     *
     * @param altDDName The new name
     */
    void setAltDDName(String altDDName);


    /**
     * Set the "allow crossing servlet contexts" flag.
     *
     * @param crossContext The new cross contexts flag
     */
    void setCrossContext(boolean crossContext);


    /**
     * Return the deny-uncovered-http-methods flag for this web application.
     *
     * @return The current value of the flag
     */
    boolean getDenyUncoveredHttpMethods();


    /**
     * Set the deny-uncovered-http-methods flag for this web application.
     *
     * @param denyUncoveredHttpMethods The new deny-uncovered-http-methods flag
     */
    void setDenyUncoveredHttpMethods(boolean denyUncoveredHttpMethods);


    /**
     * Return the display name of this web application.
     *
     * @return The display name
     */
    String getDisplayName();


    /**
     * Set the display name of this web application.
     *
     * @param displayName The new display name
     */
    void setDisplayName(String displayName);


    /**
     * Get the distributable flag for this web application.
     *
     * @return The value of the distributable flag for this web application.
     */
    boolean getDistributable();


    /**
     * Set the distributable flag for this web application.
     *
     * @param distributable The new distributable flag
     */
    void setDistributable(boolean distributable);


    /**
     * Obtain the document root for this Context.
     *
     * @return An absolute pathname or a relative (to the Host's appBase) pathname.
     */
    String getDocBase();


    /**
     * Set the document root for this Context. This can be either an absolute pathname or a relative pathname. Relative
     * pathnames are relative to the containing Host's appBase.
     *
     * @param docBase The new document root
     */
    void setDocBase(String docBase);


    /**
     * Return the URL encoded context path
     *
     * @return The URL encoded (with UTF-8) context path
     */
    String getEncodedPath();


    /**
     * Determine if annotations parsing is currently disabled
     *
     * @return {@code true} if annotation parsing is disabled for this web application
     */
    boolean getIgnoreAnnotations();


    /**
     * Set the boolean on the annotations parsing for this web application.
     *
     * @param ignoreAnnotations The boolean on the annotations parsing
     */
    void setIgnoreAnnotations(boolean ignoreAnnotations);


    /**
     * Determine if the metadata of the application is complete. This typically means annotations which add to the
     * application metadata will be ignored.
     *
     * @return {@code true} if metadata is complete for this web application
     */
    boolean getMetadataComplete();


    /**
     * Set the boolean on the metadata complete flag for this web application.
     *
     * @param metadataComplete The boolean on the metadata complete flag
     */
    void setMetadataComplete(boolean metadataComplete);


    /**
     * @return the login configuration descriptor for this web application.
     */
    LoginConfig getLoginConfig();


    /**
     * Set the login configuration descriptor for this web application.
     *
     * @param config The new login configuration
     */
    void setLoginConfig(LoginConfig config);


    /**
     * @return the naming resources associated with this web application.
     */
    NamingResourcesImpl getNamingResources();


    /**
     * Set the naming resources for this web application.
     *
     * @param namingResources The new naming resources
     */
    void setNamingResources(NamingResourcesImpl namingResources);


    /**
     * @return the context path for this web application.
     */
    String getPath();


    /**
     * Set the context path for this web application.
     *
     * @param path The new context path
     */
    void setPath(String path);


    /**
     * @return the public identifier of the deployment descriptor DTD that is currently being parsed.
     */
    String getPublicId();


    /**
     * Set the public identifier of the deployment descriptor DTD that is currently being parsed.
     *
     * @param publicId The public identifier
     */
    void setPublicId(String publicId);


    /**
     * @return the reloadable flag for this web application.
     */
    boolean getReloadable();


    /**
     * Set the reloadable flag for this web application.
     *
     * @param reloadable The new reloadable flag
     */
    void setReloadable(boolean reloadable);


    /**
     * @return the override flag for this web application.
     */
    boolean getOverride();


    /**
     * Set the override flag for this web application.
     *
     * @param override The new override flag
     */
    void setOverride(boolean override);


    /**
     * @return the privileged flag for this web application.
     */
    boolean getPrivileged();


    /**
     * Set the privileged flag for this web application.
     *
     * @param privileged The new privileged flag
     */
    void setPrivileged(boolean privileged);


    /**
     * @return the Servlet context for which this Context is a facade.
     */
    ServletContext getServletContext();


    /**
     * @return the default session timeout (in minutes) for this web application.
     */
    int getSessionTimeout();


    /**
     * Set the default session timeout (in minutes) for this web application.
     *
     * @param timeout The new default session timeout
     */
    void setSessionTimeout(int timeout);


    /**
     * Returns <code>true</code> if remaining request data will be read (swallowed) even the request violates a data
     * size constraint.
     *
     * @return <code>true</code> if data will be swallowed (default), <code>false</code> otherwise.
     */
    boolean getSwallowAbortedUploads();


    /**
     * Set to <code>false</code> to disable request data swallowing after an upload was aborted due to size constraints.
     *
     * @param swallowAbortedUploads <code>false</code> to disable swallowing, <code>true</code> otherwise (default).
     */
    void setSwallowAbortedUploads(boolean swallowAbortedUploads);

    /**
     * @return the value of the swallowOutput flag.
     */
    boolean getSwallowOutput();


    /**
     * Set the value of the swallowOutput flag. If set to true, the system.out and system.err will be redirected to the
     * logger during a servlet execution.
     *
     * @param swallowOutput The new value
     */
    void setSwallowOutput(boolean swallowOutput);


    /**
     * @return the Java class name of the Wrapper implementation used for servlets registered in this Context.
     */
    String getWrapperClass();


    /**
     * Set the Java class name of the Wrapper implementation used for servlets registered in this Context.
     *
     * @param wrapperClass The new wrapper class
     */
    void setWrapperClass(String wrapperClass);


    /**
     * Will the parsing of web.xml and web-fragment.xml files for this Context be performed by a namespace aware parser?
     *
     * @return true if namespace awareness is enabled.
     */
    boolean getXmlNamespaceAware();


    /**
     * Controls whether the parsing of web.xml and web-fragment.xml files for this Context will be performed by a
     * namespace aware parser.
     *
     * @param xmlNamespaceAware true to enable namespace awareness
     */
    void setXmlNamespaceAware(boolean xmlNamespaceAware);


    /**
     * Will the parsing of web.xml and web-fragment.xml files for this Context be performed by a validating parser?
     *
     * @return true if validation is enabled.
     */
    boolean getXmlValidation();


    /**
     * Controls whether the parsing of web.xml and web-fragment.xml files for this Context will be performed by a
     * validating parser.
     *
     * @param xmlValidation true to enable xml validation
     */
    void setXmlValidation(boolean xmlValidation);


    /**
     * Will the parsing of web.xml, web-fragment.xml, *.tld, *.jspx, *.tagx and tagplugin.xml files for this Context
     * block the use of external entities?
     *
     * @return true if access to external entities is blocked
     */
    boolean getXmlBlockExternal();


    /**
     * Controls whether the parsing of web.xml, web-fragment.xml, *.tld, *.jspx, *.tagx and tagplugin.xml files for this
     * Context will block the use of external entities.
     *
     * @param xmlBlockExternal true to block external entities
     */
    void setXmlBlockExternal(boolean xmlBlockExternal);


    /**
     * Will the parsing of *.tld files for this Context be performed by a validating parser?
     *
     * @return true if validation is enabled.
     */
    boolean getTldValidation();


    /**
     * Controls whether the parsing of *.tld files for this Context will be performed by a validating parser.
     *
     * @param tldValidation true to enable xml validation
     */
    void setTldValidation(boolean tldValidation);


    /**
     * Get the Jar Scanner to be used to scan for JAR resources for this context.
     *
     * @return The Jar Scanner configured for this context.
     */
    JarScanner getJarScanner();

    /**
     * Set the Jar Scanner to be used to scan for JAR resources for this context.
     *
     * @param jarScanner The Jar Scanner to be used for this context.
     */
    void setJarScanner(JarScanner jarScanner);

    /**
     * @return the {@link Authenticator} that is used by this context. This is always non-{@code null} for a started
     *             Context
     */
    Authenticator getAuthenticator();

    /**
     * Set whether or not the effective web.xml for this context should be logged on context start.
     *
     * @param logEffectiveWebXml set to <code>true</code> to log the complete web.xml that will be used for the webapp
     */
    void setLogEffectiveWebXml(boolean logEffectiveWebXml);

    /**
     * Should the effective web.xml for this context be logged on context start?
     *
     * @return true if the reconstructed web.xml that will be used for the webapp should be logged
     */
    boolean getLogEffectiveWebXml();

    /**
     * @return the instance manager associated with this context.
     */
    InstanceManager getInstanceManager();

    /**
     * Set the instance manager associated with this context.
     *
     * @param instanceManager the new instance manager instance
     */
    void setInstanceManager(InstanceManager instanceManager);

    /**
     * Sets the regular expression that specifies which container provided SCIs should be filtered out and not used for
     * this context. Matching uses {@link java.util.regex.Matcher#find()} so the regular expression only has to match a
     * sub-string of the fully qualified class name of the container provided SCI for it to be filtered out.
     *
     * @param containerSciFilter The regular expression against which the fully qualified class name of each container
     *                               provided SCI should be checked
     */
    void setContainerSciFilter(String containerSciFilter);

    /**
     * Obtains the regular expression that specifies which container provided SCIs should be filtered out and not used
     * for this context. Matching uses {@link java.util.regex.Matcher#find()} so the regular expression only has to
     * match a sub-string of the fully qualified class name of the container provided SCI for it to be filtered out.
     *
     * @return The regular expression against which the fully qualified class name of each container provided SCI will
     *             be checked
     */
    String getContainerSciFilter();


    /**
     * @return the value of the parallel annotation scanning flag. If true, it will dispatch scanning to the utility
     *             executor.
     */
    boolean getParallelAnnotationScanning();

    /**
     * Set the parallel annotation scanning value.
     *
     * @param parallelAnnotationScanning new parallel annotation scanning flag
     */
    void setParallelAnnotationScanning(boolean parallelAnnotationScanning);


    // --------------------------------------------------------- Public Methods

    /**
     * Add a new Listener class name to the set of Listeners configured for this application.
     *
     * @param listener Java class name of a listener class
     */
    void addApplicationListener(String listener);


    /**
     * Add a new application parameter for this application.
     *
     * @param parameter The new application parameter
     */
    void addApplicationParameter(ApplicationParameter parameter);


    /**
     * Add a security constraint to the set for this web application.
     *
     * @param constraint The security constraint that should be added
     */
    void addConstraint(SecurityConstraint constraint);


    /**
     * Add an error page for the specified error or Java exception.
     *
     * @param errorPage The error page definition to be added
     */
    void addErrorPage(ErrorPage errorPage);


    /**
     * Add a filter definition to this Context.
     *
     * @param filterDef The filter definition to be added
     */
    void addFilterDef(FilterDef filterDef);


    /**
     * Add a filter mapping to this Context.
     *
     * @param filterMap The filter mapping to be added
     */
    void addFilterMap(FilterMap filterMap);

    /**
     * Add a filter mapping to this Context before the mappings defined in the deployment descriptor but after any other
     * mappings added via this method.
     *
     * @param filterMap The filter mapping to be added
     *
     * @exception IllegalArgumentException if the specified filter name does not match an existing filter definition, or
     *                                         the filter mapping is malformed
     */
    void addFilterMapBefore(FilterMap filterMap);


    /**
     * Add a Locale Encoding Mapping (see Sec 5.4 of Servlet spec 2.4)
     *
     * @param locale   locale to map an encoding for
     * @param encoding encoding to be used for a give locale
     */
    void addLocaleEncodingMappingParameter(String locale, String encoding);


    /**
     * Add a new MIME mapping, replacing any existing mapping for the specified extension.
     *
     * @param extension Filename extension being mapped
     * @param mimeType  Corresponding MIME type
     */
    void addMimeMapping(String extension, String mimeType);


    /**
     * Add a new context initialization parameter, replacing any existing value for the specified name.
     *
     * @param name  Name of the new parameter
     * @param value Value of the new parameter
     */
    void addParameter(String name, String value);


    /**
     * Add a security role reference for this web application.
     *
     * @param role Security role used in the application
     * @param link Actual security role to check for
     */
    void addRoleMapping(String role, String link);


    /**
     * Add a new security role for this web application.
     *
     * @param role New security role
     */
    void addSecurityRole(String role);


    /**
     * Add a new servlet mapping, replacing any existing mapping for the specified pattern.
     *
     * @param pattern URL pattern to be mapped
     * @param name    Name of the corresponding servlet to execute
     */
    default void addServletMappingDecoded(String pattern, String name) {
        addServletMappingDecoded(pattern, name, false);
    }


    /**
     * Add a new servlet mapping, replacing any existing mapping for the specified pattern.
     *
     * @param pattern     URL pattern to be mapped
     * @param name        Name of the corresponding servlet to execute
     * @param jspWildcard true if name identifies the JspServlet and pattern contains a wildcard; false otherwise
     */
    void addServletMappingDecoded(String pattern, String name, boolean jspWildcard);


    /**
     * Add a resource which will be watched for reloading by the host auto deployer. Note: this will not be used in
     * embedded mode.
     *
     * @param name Path to the resource, relative to docBase
     */
    void addWatchedResource(String name);


    /**
     * Add a new welcome file to the set recognized by this Context.
     *
     * @param name New welcome file name
     */
    void addWelcomeFile(String name);


    /**
     * Add the classname of a LifecycleListener to be added to each Wrapper appended to this Context.
     *
     * @param listener Java class name of a LifecycleListener class
     */
    void addWrapperLifecycle(String listener);


    /**
     * Add the classname of a ContainerListener to be added to each Wrapper appended to this Context.
     *
     * @param listener Java class name of a ContainerListener class
     */
    void addWrapperListener(String listener);


    /**
     * Factory method to create and return a new InstanceManager instance. This can be used for framework integration or
     * easier configuration with custom Context implementations.
     *
     * @return the instance manager
     */
    InstanceManager createInstanceManager();

    /**
     * Factory method to create and return a new Wrapper instance, of the Java implementation class appropriate for this
     * Context implementation. The constructor of the instantiated Wrapper will have been called, but no properties will
     * have been set.
     *
     * @return a newly created wrapper instance that is used to wrap a Servlet
     */
    Wrapper createWrapper();


    /**
     * @return the set of application listener class names configured for this application.
     */
    String[] findApplicationListeners();


    /**
     * @return the set of application parameters for this application.
     */
    ApplicationParameter[] findApplicationParameters();


    /**
     * @return the set of security constraints for this web application. If there are none, a zero-length array is
     *             returned.
     */
    SecurityConstraint[] findConstraints();


    /**
     * @return the error page entry for the specified HTTP error code, if any; otherwise return <code>null</code>.
     *
     * @param errorCode Error code to look up
     */
    ErrorPage findErrorPage(int errorCode);


    /**
     * Find and return the ErrorPage instance for the specified exception's class, or an ErrorPage instance for the
     * closest superclass for which there is such a definition. If no associated ErrorPage instance is found, return
     * <code>null</code>.
     *
     * @param throwable The exception type for which to find an ErrorPage
     *
     * @return the error page entry for the specified Java exception type, if any; otherwise return {@code null}.
     */
    ErrorPage findErrorPage(Throwable throwable);


    /**
     * @return the set of defined error pages for all specified error codes and exception types.
     */
    ErrorPage[] findErrorPages();


    /**
     * @return the filter definition for the specified filter name, if any; otherwise return <code>null</code>.
     *
     * @param filterName Filter name to look up
     */
    FilterDef findFilterDef(String filterName);


    /**
     * @return the set of defined filters for this Context.
     */
    FilterDef[] findFilterDefs();


    /**
     * @return the set of filter mappings for this Context.
     */
    FilterMap[] findFilterMaps();


    /**
     * @return the MIME type to which the specified extension is mapped, if any; otherwise return <code>null</code>.
     *
     * @param extension Extension to map to a MIME type
     */
    String findMimeMapping(String extension);


    /**
     * @return the extensions for which MIME mappings are defined. If there are none, a zero-length array is returned.
     */
    String[] findMimeMappings();


    /**
     * @return the value for the specified context initialization parameter name, if any; otherwise return
     *             <code>null</code>.
     *
     * @param name Name of the parameter to return
     */
    String findParameter(String name);


    /**
     * @return the names of all defined context initialization parameters for this Context. If no parameters are
     *             defined, a zero-length array is returned.
     */
    String[] findParameters();


    /**
     * For the given security role (as used by an application), return the corresponding role name (as defined by the
     * underlying Realm) if there is one. Otherwise, return the specified role unchanged.
     *
     * @param role Security role to map
     *
     * @return The role name that was mapped to the specified role
     */
    String findRoleMapping(String role);


    /**
     * @return <code>true</code> if the specified security role is defined for this application; otherwise return
     *             <code>false</code>.
     *
     * @param role Security role to verify
     */
    boolean findSecurityRole(String role);


    /**
     * @return the security roles defined for this application. If none have been defined, a zero-length array is
     *             returned.
     */
    String[] findSecurityRoles();


    /**
     * @return the servlet name mapped by the specified pattern (if any); otherwise return <code>null</code>.
     *
     * @param pattern Pattern for which a mapping is requested
     */
    String findServletMapping(String pattern);


    /**
     * @return the patterns of all defined servlet mappings for this Context. If no mappings are defined, a zero-length
     *             array is returned.
     */
    String[] findServletMappings();


    /**
     * @return the associated ThreadBindingListener.
     */
    ThreadBindingListener getThreadBindingListener();


    /**
     * Get the associated ThreadBindingListener.
     *
     * @param threadBindingListener Set the listener that will receive notifications when entering and exiting the
     *                                  application scope
     */
    void setThreadBindingListener(ThreadBindingListener threadBindingListener);


    /**
     * @return the set of watched resources for this Context. If none are defined, a zero length array will be returned.
     */
    String[] findWatchedResources();


    /**
     * @return <code>true</code> if the specified welcome file is defined for this Context; otherwise return
     *             <code>false</code>.
     *
     * @param name Welcome file to verify
     */
    boolean findWelcomeFile(String name);


    /**
     * @return the set of welcome files defined for this Context. If none are defined, a zero-length array is returned.
     */
    String[] findWelcomeFiles();


    /**
     * @return the set of LifecycleListener classes that will be added to newly created Wrappers automatically.
     */
    String[] findWrapperLifecycles();


    /**
     * @return the set of ContainerListener classes that will be added to newly created Wrappers automatically.
     */
    String[] findWrapperListeners();


    /**
     * Notify all {@link jakarta.servlet.ServletRequestListener}s that a request has started.
     *
     * @param request The request object that will be passed to the listener
     *
     * @return <code>true</code> if the listeners fire successfully, else <code>false</code>
     */
    boolean fireRequestInitEvent(ServletRequest request);

    /**
     * Notify all {@link jakarta.servlet.ServletRequestListener}s that a request has ended.
     *
     * @param request The request object that will be passed to the listener
     *
     * @return <code>true</code> if the listeners fire successfully, else <code>false</code>
     */
    boolean fireRequestDestroyEvent(ServletRequest request);

    /**
     * Reload this web application, if reloading is supported.
     *
     * @exception IllegalStateException if the <code>reloadable</code> property is set to <code>false</code>.
     */
    void reload();


    /**
     * Remove the specified application listener class from the set of listeners for this application.
     *
     * @param listener Java class name of the listener to be removed
     */
    void removeApplicationListener(String listener);


    /**
     * Remove the application parameter with the specified name from the set for this application.
     *
     * @param name Name of the application parameter to remove
     */
    void removeApplicationParameter(String name);


    /**
     * Remove the specified security constraint from this web application.
     *
     * @param constraint Constraint to be removed
     */
    void removeConstraint(SecurityConstraint constraint);


    /**
     * Remove the error page for the specified error code or Java language exception, if it exists; otherwise, no action
     * is taken.
     *
     * @param errorPage The error page definition to be removed
     */
    void removeErrorPage(ErrorPage errorPage);


    /**
     * Remove the specified filter definition from this Context, if it exists; otherwise, no action is taken.
     *
     * @param filterDef Filter definition to be removed
     */
    void removeFilterDef(FilterDef filterDef);


    /**
     * Remove a filter mapping from this Context.
     *
     * @param filterMap The filter mapping to be removed
     */
    void removeFilterMap(FilterMap filterMap);


    /**
     * Remove the MIME mapping for the specified extension, if it exists; otherwise, no action is taken.
     *
     * @param extension Extension to remove the mapping for
     */
    void removeMimeMapping(String extension);


    /**
     * Remove the context initialization parameter with the specified name, if it exists; otherwise, no action is taken.
     *
     * @param name Name of the parameter to remove
     */
    void removeParameter(String name);


    /**
     * Remove any security role reference for the specified name
     *
     * @param role Security role (as used in the application) to remove
     */
    void removeRoleMapping(String role);


    /**
     * Remove any security role with the specified name.
     *
     * @param role Security role to remove
     */
    void removeSecurityRole(String role);


    /**
     * Remove any servlet mapping for the specified pattern, if it exists; otherwise, no action is taken.
     *
     * @param pattern URL pattern of the mapping to remove
     */
    void removeServletMapping(String pattern);


    /**
     * Remove the specified watched resource name from the list associated with this Context.
     *
     * @param name Name of the watched resource to be removed
     */
    void removeWatchedResource(String name);


    /**
     * Remove the specified welcome file name from the list recognized by this Context.
     *
     * @param name Name of the welcome file to be removed
     */
    void removeWelcomeFile(String name);


    /**
     * Remove a class name from the set of LifecycleListener classes that will be added to newly created Wrappers.
     *
     * @param listener Class name of a LifecycleListener class to be removed
     */
    void removeWrapperLifecycle(String listener);


    /**
     * Remove a class name from the set of ContainerListener classes that will be added to newly created Wrappers.
     *
     * @param listener Class name of a ContainerListener class to be removed
     */
    void removeWrapperListener(String listener);


    /**
     * @return the real path for a given virtual path, if possible; otherwise return <code>null</code>.
     *
     * @param path The path to the desired resource
     */
    String getRealPath(String path);


    /**
     * @return the effective major version of the Servlet spec used by this context.
     */
    int getEffectiveMajorVersion();


    /**
     * Set the effective major version of the Servlet spec used by this context.
     *
     * @param major Set the version number
     */
    void setEffectiveMajorVersion(int major);


    /**
     * @return the effective minor version of the Servlet spec used by this context.
     */
    int getEffectiveMinorVersion();


    /**
     * Set the effective minor version of the Servlet spec used by this context.
     *
     * @param minor Set the version number
     */
    void setEffectiveMinorVersion(int minor);


    /**
     * @return the JSP configuration for this context. Will be null if there is no JSP configuration.
     */
    JspConfigDescriptor getJspConfigDescriptor();


    /**
     * Set the JspConfigDescriptor for this context. A null value indicates there is not JSP configuration.
     *
     * @param descriptor the new JSP configuration
     */
    void setJspConfigDescriptor(JspConfigDescriptor descriptor);


    /**
     * Add a ServletContainerInitializer instance to this web application.
     *
     * @param sci     The instance to add
     * @param classes The classes in which the initializer expressed an interest
     */
    void addServletContainerInitializer(ServletContainerInitializer sci, Set<Class<?>> classes);


    /**
     * Is this Context paused whilst it is reloaded?
     *
     * @return <code>true</code> if the context has been paused
     */
    boolean getPaused();


    /**
     * Is this context using version 2.2 of the Servlet spec?
     *
     * @return <code>true</code> for a legacy Servlet 2.2 webapp
     */
    boolean isServlet22();


    /**
     * Notification that Servlet security has been dynamically set in a
     * {@link jakarta.servlet.ServletRegistration.Dynamic}
     *
     * @param registration           Servlet security was modified for
     * @param servletSecurityElement new security constraints for this Servlet
     *
     * @return urls currently mapped to this registration that are already present in web.xml
     */
    Set<String> addServletSecurity(ServletRegistration.Dynamic registration,
            ServletSecurityElement servletSecurityElement);

    /**
     * Sets the (comma separated) list of Servlets that expect a resource to be present. Used to ensure that welcome
     * files associated with Servlets that expect a resource to be present are not mapped when there is no resource.
     *
     * @param resourceOnlyServlets The Servlet names comma separated list
     */
    void setResourceOnlyServlets(String resourceOnlyServlets);

    /**
     * Obtains the list of Servlets that expect a resource to be present.
     *
     * @return A comma separated list of Servlet names as used in web.xml
     */
    String getResourceOnlyServlets();

    /**
     * Checks the named Servlet to see if it expects a resource to be present.
     *
     * @param servletName Name of the Servlet (as per web.xml) to check
     *
     * @return <code>true</code> if the Servlet expects a resource, otherwise <code>false</code>
     */
    boolean isResourceOnlyServlet(String servletName);

    /**
     * @return the base name to use for WARs, directories or context.xml files for this context.
     */
    String getBaseName();

    /**
     * Set the version of this web application - used to differentiate different versions of the same web application
     * when using parallel deployment.
     *
     * @param webappVersion The webapp version associated with the context, which should be unique
     */
    void setWebappVersion(String webappVersion);

    /**
     * @return The version of this web application, used to differentiate different versions of the same web application
     *             when using parallel deployment. If not specified, defaults to the empty string.
     */
    String getWebappVersion();

    /**
     * Configure whether or not requests listeners will be fired on forwards for this Context.
     *
     * @param enable <code>true</code> to fire request listeners when forwarding
     */
    void setFireRequestListenersOnForwards(boolean enable);

    /**
     * @return whether or not requests listeners will be fired on forwards for this Context.
     */
    boolean getFireRequestListenersOnForwards();

    /**
     * Configures if a user presents authentication credentials, whether the context will process them when the request
     * is for a non-protected resource.
     *
     * @param enable <code>true</code> to perform authentication even outside security constraints
     */
    void setPreemptiveAuthentication(boolean enable);

    /**
     * @return if a user presents authentication credentials, will the context will process them when the request is for
     *             a non-protected resource.
     */
    boolean getPreemptiveAuthentication();

    /**
     * Configures if a response body is included when a redirect response is sent to the client.
     *
     * @param enable <code>true</code> to send a response body for redirects
     */
    void setSendRedirectBody(boolean enable);

    /**
     * @return if the context is configured to include a response body as part of a redirect response.
     */
    boolean getSendRedirectBody();

    /**
     * @return the Loader with which this Context is associated.
     */
    Loader getLoader();

    /**
     * Set the Loader with which this Context is associated.
     *
     * @param loader The newly associated loader
     */
    void setLoader(Loader loader);

    /**
     * @return the Resources with which this Context is associated.
     */
    WebResourceRoot getResources();

    /**
     * Set the Resources object with which this Context is associated.
     *
     * @param resources The newly associated Resources
     */
    void setResources(WebResourceRoot resources);

    /**
     * @return the Manager with which this Context is associated. If there is no associated Manager, return
     *             <code>null</code>.
     */
    Manager getManager();


    /**
     * Set the Manager with which this Context is associated.
     *
     * @param manager The newly associated Manager
     */
    void setManager(Manager manager);

    /**
     * Sets the flag that indicates if /WEB-INF/classes should be treated like an exploded JAR and JAR resources made
     * available as if they were in a JAR.
     *
     * @param addWebinfClassesResources The new value for the flag
     */
    void setAddWebinfClassesResources(boolean addWebinfClassesResources);

    /**
     * @return the flag that indicates if /WEB-INF/classes should be treated like an exploded JAR and JAR resources made
     *             available as if they were in a JAR.
     */
    boolean getAddWebinfClassesResources();

    /**
     * Add a post construct method definition for the given class, if there is an existing definition for the specified
     * class - IllegalArgumentException will be thrown.
     *
     * @param clazz  Fully qualified class name
     * @param method Post construct method name
     *
     * @throws IllegalArgumentException if the fully qualified class name or method name are <code>NULL</code>; if there
     *                                      is already post construct method definition for the given class
     */
    void addPostConstructMethod(String clazz, String method);

    /**
     * Add a pre destroy method definition for the given class, if there is an existing definition for the specified
     * class - IllegalArgumentException will be thrown.
     *
     * @param clazz  Fully qualified class name
     * @param method Post construct method name
     *
     * @throws IllegalArgumentException if the fully qualified class name or method name are <code>NULL</code>; if there
     *                                      is already pre destroy method definition for the given class
     */
    void addPreDestroyMethod(String clazz, String method);

    /**
     * Removes the post construct method definition for the given class, if it exists; otherwise, no action is taken.
     *
     * @param clazz Fully qualified class name
     */
    void removePostConstructMethod(String clazz);

    /**
     * Removes the pre destroy method definition for the given class, if it exists; otherwise, no action is taken.
     *
     * @param clazz Fully qualified class name
     */
    void removePreDestroyMethod(String clazz);

    /**
     * Returns the method name that is specified as post construct method for the given class, if it exists; otherwise
     * <code>NULL</code> will be returned.
     *
     * @param clazz Fully qualified class name
     *
     * @return the method name that is specified as post construct method for the given class, if it exists; otherwise
     *             <code>NULL</code> will be returned.
     */
    String findPostConstructMethod(String clazz);

    /**
     * Returns the method name that is specified as pre destroy method for the given class, if it exists; otherwise
     * <code>NULL</code> will be returned.
     *
     * @param clazz Fully qualified class name
     *
     * @return the method name that is specified as pre destroy method for the given class, if it exists; otherwise
     *             <code>NULL</code> will be returned.
     */
    String findPreDestroyMethod(String clazz);

    /**
     * Returns a map with keys - fully qualified class names of the classes that have post construct methods and the
     * values are the corresponding method names. If there are no such classes an empty map will be returned.
     *
     * @return a map with keys - fully qualified class names of the classes that have post construct methods and the
     *             values are the corresponding method names.
     */
    Map<String,String> findPostConstructMethods();

    /**
     * Returns a map with keys - fully qualified class names of the classes that have pre destroy methods and the values
     * are the corresponding method names. If there are no such classes an empty map will be returned.
     *
     * @return a map with keys - fully qualified class names of the classes that have pre destroy methods and the values
     *             are the corresponding method names.
     */
    Map<String,String> findPreDestroyMethods();

    /**
     * @return the token necessary for operations on the associated JNDI naming context.
     */
    Object getNamingToken();

    /**
     * Sets the {@link CookieProcessor} that will be used to process cookies for this Context.
     *
     * @param cookieProcessor The new cookie processor
     *
     * @throws IllegalArgumentException If a {@code null} CookieProcessor is specified
     */
    void setCookieProcessor(CookieProcessor cookieProcessor);

    /**
     * @return the {@link CookieProcessor} that will be used to process cookies for this Context.
     */
    CookieProcessor getCookieProcessor();

    /**
     * When a client provides the ID for a new session, should that ID be validated? The only use case for using a
     * client provided session ID is to have a common session ID across multiple web applications. Therefore, any client
     * provided session ID should already exist in another web application. If this check is enabled, the client
     * provided session ID will only be used if the session ID exists in at least one other web application for the
     * current host. Note that the following additional tests are always applied, irrespective of this setting:
     * <ul>
     * <li>The session ID is provided by a cookie</li>
     * <li>The session cookie has a path of {@code /}</li>
     * </ul>
     *
     * @param validateClientProvidedNewSessionId {@code true} if validation should be applied
     */
    void setValidateClientProvidedNewSessionId(boolean validateClientProvidedNewSessionId);

    /**
     * Will client provided session IDs be validated (see {@link #setValidateClientProvidedNewSessionId(boolean)})
     * before use?
     *
     * @return {@code true} if validation will be applied. Otherwise, {@code
     *         false}
     */
    boolean getValidateClientProvidedNewSessionId();

    /**
     * If enabled, requests for a web application context root will be redirected (adding a trailing slash) by the
     * Mapper. This is more efficient but has the side effect of confirming that the context path is valid.
     *
     * @param mapperContextRootRedirectEnabled Should the redirects be enabled?
     */
    void setMapperContextRootRedirectEnabled(boolean mapperContextRootRedirectEnabled);

    /**
     * Determines if requests for a web application context root will be redirected (adding a trailing slash) by the
     * Mapper. This is more efficient but has the side effect of confirming that the context path is valid.
     *
     * @return {@code true} if the Mapper level redirect is enabled for this Context.
     */
    boolean getMapperContextRootRedirectEnabled();

    /**
     * If enabled, requests for a directory will be redirected (adding a trailing slash) by the Mapper. This is more
     * efficient but has the side effect of confirming that the directory is valid.
     *
     * @param mapperDirectoryRedirectEnabled Should the redirects be enabled?
     */
    void setMapperDirectoryRedirectEnabled(boolean mapperDirectoryRedirectEnabled);

    /**
     * Determines if requests for a directory will be redirected (adding a trailing slash) by the Mapper. This is more
     * efficient but has the side effect of confirming that the directory is valid.
     *
     * @return {@code true} if the Mapper level redirect is enabled for this Context.
     */
    boolean getMapperDirectoryRedirectEnabled();

    /**
     * Controls whether HTTP 1.1 and later location headers generated by a call to
     * {@link jakarta.servlet.http.HttpServletResponse#sendRedirect(String)} will use relative or absolute redirects.
     * <p>
     * Relative redirects are more efficient but may not work with reverse proxies that change the context path. It
     * should be noted that it is not recommended to use a reverse proxy to change the context path because of the
     * multiple issues it creates.
     * <p>
     * Absolute redirects should work with reverse proxies that change the context path but may cause issues with the
     * {@link org.apache.catalina.filters.RemoteIpFilter} if the filter is changing the scheme and/or port.
     *
     * @param useRelativeRedirects {@code true} to use relative redirects and {@code false} to use absolute redirects
     */
    void setUseRelativeRedirects(boolean useRelativeRedirects);

    /**
     * Will HTTP 1.1 and later location headers generated by a call to
     * {@link jakarta.servlet.http.HttpServletResponse#sendRedirect(String)} use relative or absolute redirects.
     *
     * @return {@code true} if relative redirects will be used {@code false} if absolute redirects are used.
     *
     * @see #setUseRelativeRedirects(boolean)
     */
    boolean getUseRelativeRedirects();

    /**
     * Are paths used in calls to obtain a request dispatcher expected to be encoded? This affects both how Tomcat
     * handles calls to obtain a request dispatcher as well as how Tomcat generates paths used to obtain request
     * dispatchers internally.
     *
     * @param dispatchersUseEncodedPaths {@code true} to use encoded paths, otherwise {@code false}
     */
    void setDispatchersUseEncodedPaths(boolean dispatchersUseEncodedPaths);

    /**
     * Are paths used in calls to obtain a request dispatcher expected to be encoded? This applies to both how Tomcat
     * handles calls to obtain a request dispatcher as well as how Tomcat generates paths used to obtain request
     * dispatchers internally.
     *
     * @return {@code true} if encoded paths will be used, otherwise {@code false}
     */
    boolean getDispatchersUseEncodedPaths();

    /**
     * Set the default request body encoding for this web application.
     *
     * @param encoding The default encoding
     */
    void setRequestCharacterEncoding(String encoding);

    /**
     * Get the default request body encoding for this web application.
     *
     * @return The default request body encoding
     */
    String getRequestCharacterEncoding();

    /**
     * Set the default response body encoding for this web application.
     *
     * @param encoding The default encoding
     */
    void setResponseCharacterEncoding(String encoding);

    /**
     * Get the default response body encoding for this web application.
     *
     * @return The default response body encoding
     */
    String getResponseCharacterEncoding();

    /**
     * Configure if, when returning a context path from
     * {@link jakarta.servlet.http.HttpServletRequest#getContextPath()}, the return value is allowed to contain multiple
     * leading '/' characters.
     *
     * @param allowMultipleLeadingForwardSlashInPath The new value for the flag
     */
    void setAllowMultipleLeadingForwardSlashInPath(boolean allowMultipleLeadingForwardSlashInPath);

    /**
     * When returning a context path from {@link jakarta.servlet.http.HttpServletRequest#getContextPath()}, is it
     * allowed to contain multiple leading '/' characters?
     *
     * @return <code>true</code> if multiple leading '/' characters are allowed, otherwise <code>false</code>
     */
    boolean getAllowMultipleLeadingForwardSlashInPath();


    void incrementInProgressAsyncCount();


    void decrementInProgressAsyncCount();


    /**
     * Configure whether Tomcat will attempt to create an upload target used by this web application if it does not
     * exist when the web application attempts to use it.
     *
     * @param createUploadTargets {@code true} if Tomcat should attempt to create the upload target, otherwise
     *                                {@code false}
     */
    void setCreateUploadTargets(boolean createUploadTargets);


    /**
     * Will Tomcat attempt to create an upload target used by this web application if it does not exist when the web
     * application attempts to use it?
     *
     * @return {@code true} if Tomcat will attempt to create an upload target otherwise {@code false}
     */
    boolean getCreateUploadTargets();


    /**
     * If this is <code>true</code>, every request that is associated with a session will cause the session's last
     * accessed time to be updated regardless of whether or not the request explicitly accesses the session. If
     * <code>org.apache.catalina.STRICT_SERVLET_COMPLIANCE</code> is set to <code>true</code>, the default of this
     * setting will be <code>true</code>, else the default value will be <code>false</code>.
     *
     * @return the flag value
     */
    boolean getAlwaysAccessSession();


    /**
     * Set the session access behavior.
     *
     * @param alwaysAccessSession the new flag value
     */
    void setAlwaysAccessSession(boolean alwaysAccessSession);


    /**
     * If this is <code>true</code> then the path passed to <code>ServletContext.getResource()</code> or
     * <code>ServletContext.getResourceAsStream()</code> must start with &quot;/&quot;. If <code>false</code>, code like
     * <code>getResource("myfolder/myresource.txt")</code> will work as Tomcat will prepend &quot;/&quot; to the
     * provided path. If <code>org.apache.catalina.STRICT_SERVLET_COMPLIANCE</code> is set to <code>true</code>, the
     * default of this setting will be <code>true</code>, else the default value will be <code>false</code>.
     *
     * @return the flag value
     */
    boolean getContextGetResourceRequiresSlash();


    /**
     * Allow using <code>ServletContext.getResource()</code> or <code>ServletContext.getResourceAsStream()</code>
     * without a leading &quot;/&quot;.
     *
     * @param contextGetResourceRequiresSlash the new flag value
     */
    void setContextGetResourceRequiresSlash(boolean contextGetResourceRequiresSlash);


    /**
     * If this is <code>true</code> then any wrapped request or response object passed to an application dispatcher will
     * be checked to ensure that it has wrapped the original request or response. If
     * <code>org.apache.catalina.STRICT_SERVLET_COMPLIANCE</code> is set to <code>true</code>, the default of this
     * setting will be <code>true</code>, else the default value will be <code>false</code>.
     *
     * @return the flag value
     */
    boolean getDispatcherWrapsSameObject();


    /**
     * Allow disabling the object wrap check in the request dispatcher.
     *
     * @param dispatcherWrapsSameObject the new flag value
     */
    void setDispatcherWrapsSameObject(boolean dispatcherWrapsSameObject);


    /**
     * If this is <code>true</code>, then following a forward the response will be unwrapped to suspend the Catalina
     * response instead of simply closing the top level response. The default value is <code>true</code>.
     *
     * @return the flag value
     */
    boolean getSuspendWrappedResponseAfterForward();


    /**
     * Allows unwrapping the response object to suspend the response following a forward.
     *
     * @param suspendWrappedResponseAfterForward the new flag value
     */
    void setSuspendWrappedResponseAfterForward(boolean suspendWrappedResponseAfterForward);


    /**
     * Find configuration file with the specified path, first looking into the webapp resources, then delegating to
     * <code>ConfigFileLoader.getSource().getResource</code>. The <code>WEBAPP_PROTOCOL</code> constant prefix is used
     * to denote webapp resources.
     *
     * @param name The resource name
     *
     * @return the resource
     *
     * @throws IOException if an error occurs or if the resource does not exist
     */
    default Resource findConfigFileResource(String name) throws IOException {
        if (name.startsWith(WEBAPP_PROTOCOL)) {
            String path = name.substring(WEBAPP_PROTOCOL.length());
            WebResource resource = getResources().getResource(path);
            if (resource.canRead() && resource.isFile()) {
                InputStream stream = resource.getInputStream();
                try {
                    return new Resource(stream, resource.getURL().toURI());
                } catch (URISyntaxException e) {
                    stream.close();
                }
            }
            throw new FileNotFoundException(name);
        }
        return ConfigFileLoader.getSource().getResource(name);
    }


    /**
     * Obtain the current configuration for the handling of encoded reverse solidus (%5c - \) characters in paths used
     * to obtain {@link RequestDispatcher} instances for this {@link Context}.
     *
     * @return Obtain the current configuration for the handling of encoded reverse solidus characters
     */
    default String getEncodedReverseSolidusHandling() {
        return EncodedSolidusHandling.DECODE.getValue();
    }


    /**
     * Configure the handling for encoded reverse solidus (%5c - \) characters in paths used to obtain
     * {@link RequestDispatcher} instances for this {@link Context}.
     *
     * @param encodedReverseSolidusHandling One of the values of {@link EncodedSolidusHandling}
     */
    default void setEncodedReverseSolidusHandling(String encodedReverseSolidusHandling) {
        throw new UnsupportedOperationException();
    }


    /**
     * Obtain the current configuration for the handling of encoded reverse solidus (%5c - \) characters in paths used
     * to obtain {@link RequestDispatcher} instances for this {@link Context}.
     *
     * @return Obtain the current configuration for the handling of encoded reverse solidus characters
     */
    default EncodedSolidusHandling getEncodedReverseSolidusHandlingEnum() {
        return EncodedSolidusHandling.DECODE;
    }


    /**
     * Obtain the current configuration for the handling of encoded solidus (%2f - /) characters in paths used to obtain
     * {@link RequestDispatcher} instances for this {@link Context}.
     *
     * @return Obtain the current configuration for the handling of encoded solidus characters
     */
    default String getEncodedSolidusHandling() {
        return EncodedSolidusHandling.REJECT.getValue();
    }


    /**
     * Configure the handling for encoded solidus (%2f - /) characters in paths used to obtain {@link RequestDispatcher}
     * instances for this {@link Context}.
     *
     * @param encodedSolidusHandling One of the values of {@link EncodedSolidusHandling}
     */
    default void setEncodedSolidusHandling(String encodedSolidusHandling) {
        throw new UnsupportedOperationException();
    }


    /**
     * Obtain the current configuration for the handling of encoded solidus (%2f - /) characters in paths used to obtain
     * {@link RequestDispatcher} instances for this {@link Context}.
     *
     * @return Obtain the current configuration for the handling of encoded solidus characters
     */
    default EncodedSolidusHandling getEncodedSolidusHandlingEnum() {
        return EncodedSolidusHandling.REJECT;
    }
}
