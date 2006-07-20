/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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


import javax.servlet.ServletContext;

import org.apache.tomcat.util.http.mapper.Mapper;

import org.apache.catalina.deploy.ApplicationParameter;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.NamingResources;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.util.CharsetMapper;


/**
 * A <b>Context</b> is a Container that represents a servlet context, and
 * therefore an individual web application, in the Catalina servlet engine.
 * It is therefore useful in almost every deployment of Catalina (even if a
 * Connector attached to a web server (such as Apache) uses the web server's
 * facilities to identify the appropriate Wrapper to handle this request.
 * It also provides a convenient mechanism to use Interceptors that see
 * every request processed by this particular web application.
 * <p>
 * The parent Container attached to a Context is generally a Host, but may
 * be some other implementation, or may be omitted if it is not necessary.
 * <p>
 * The child containers attached to a Context are generally implementations
 * of Wrapper (representing individual servlet definitions).
 * <p>
 *
 * @author Craig R. McClanahan
 * @version $Revision: 303431 $ $Date: 2004-10-26 17:42:05 +0200 (mar., 26 oct. 2004) $
 */

public interface Context extends Container {


    // ----------------------------------------------------- Manifest Constants


    /**
     * The LifecycleEvent type sent when a context is reloaded.
     */
    public static final String RELOAD_EVENT = "reload";


    // ------------------------------------------------------------- Properties


    /**
     * Return the set of initialized application event listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @exception IllegalStateException if this method is called before
     *  this application has started, or after it has been stopped
     */
    public Object[] getApplicationEventListeners();


    /**
     * Store the set of initialized application event listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @param listeners The set of instantiated listener objects.
     */
    public void setApplicationEventListeners(Object listeners[]);


    /**
     * Return the set of initialized application lifecycle listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @exception IllegalStateException if this method is called before
     *  this application has started, or after it has been stopped
     */
    public Object[] getApplicationLifecycleListeners();


    /**
     * Store the set of initialized application lifecycle listener objects,
     * in the order they were specified in the web application deployment
     * descriptor, for this application.
     *
     * @param listeners The set of instantiated listener objects.
     */
    public void setApplicationLifecycleListeners(Object listeners[]);


    /**
     * Return the application available flag for this Context.
     */
    public boolean getAvailable();


    /**
     * Set the application available flag for this Context.
     *
     * @param available The new application available flag
     */
    public void setAvailable(boolean available);


    /**
     * Return the Locale to character set mapper for this Context.
     */
    public CharsetMapper getCharsetMapper();


    /**
     * Set the Locale to character set mapper for this Context.
     *
     * @param mapper The new mapper
     */
    public void setCharsetMapper(CharsetMapper mapper);


    /**
     * Return the path to a file to save this Context information.
     */
    public String getConfigFile();


    /**
     * Set the path to a file to save this Context information.
     *
     * @param configFile The path to a file to save this Context information.
     */
    public void setConfigFile(String configFile);


    /**
     * Return the "correctly configured" flag for this Context.
     */
    public boolean getConfigured();


    /**
     * Set the "correctly configured" flag for this Context.  This can be
     * set to false by startup listeners that detect a fatal configuration
     * error to avoid the application from being made available.
     *
     * @param configured The new correctly configured flag
     */
    public void setConfigured(boolean configured);


    /**
     * Return the "use cookies for session ids" flag.
     */
    public boolean getCookies();


    /**
     * Set the "use cookies for session ids" flag.
     *
     * @param cookies The new flag
     */
    public void setCookies(boolean cookies);


    /**
     * Return the "allow crossing servlet contexts" flag.
     */
    public boolean getCrossContext();


    
    /**
     * Return the alternate Deployment Descriptor name.
     */
    public String getAltDDName();
    
    
    /**
     * Set an alternate Deployment Descriptor name.
     */
    public void setAltDDName(String altDDName) ;
    
    
    /**
     * Set the "allow crossing servlet contexts" flag.
     *
     * @param crossContext The new cross contexts flag
     */
    public void setCrossContext(boolean crossContext);


    /**
     * Return the display name of this web application.
     */
    public String getDisplayName();


    /**
     * Set the display name of this web application.
     *
     * @param displayName The new display name
     */
    public void setDisplayName(String displayName);


    /**
     * Return the distributable flag for this web application.
     */
    public boolean getDistributable();


    /**
     * Set the distributable flag for this web application.
     *
     * @param distributable The new distributable flag
     */
    public void setDistributable(boolean distributable);


    /**
     * Return the document root for this Context.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     */
    public String getDocBase();


    /**
     * Set the document root for this Context.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     *
     * @param docBase The new document root
     */
    public void setDocBase(String docBase);


    /**
     * Return the URL encoded context path, using UTF-8.
     */
    public String getEncodedPath();


    /**
     * Return the boolean on the annotations parsing.
     */
    public boolean getIgnoreAnnotations();
    
    
    /**
     * Set the boolean on the annotations parsing for this web 
     * application.
     * 
     * @param ignoreAnnotations The boolean on the annotations parsing
     */
    public void setIgnoreAnnotations(boolean ignoreAnnotations);
    
    
    /**
     * Return the login configuration descriptor for this web application.
     */
    public LoginConfig getLoginConfig();


    /**
     * Set the login configuration descriptor for this web application.
     *
     * @param config The new login configuration
     */
    public void setLoginConfig(LoginConfig config);


    /**
     * Get the request dispatcher mapper.
     */
    public Mapper getMapper();


    /**
     * Return the naming resources associated with this web application.
     */
    public NamingResources getNamingResources();


    /**
     * Set the naming resources for this web application.
     *
     * @param namingResources The new naming resources
     */
    public void setNamingResources(NamingResources namingResources);


    /**
     * Return the context path for this web application.
     */
    public String getPath();


    /**
     * Set the context path for this web application.
     *
     * @param path The new context path
     */
    public void setPath(String path);


    /**
     * Return the public identifier of the deployment descriptor DTD that is
     * currently being parsed.
     */
    public String getPublicId();


    /**
     * Set the public identifier of the deployment descriptor DTD that is
     * currently being parsed.
     *
     * @param publicId The public identifier
     */
    public void setPublicId(String publicId);


    /**
     * Return the reloadable flag for this web application.
     */
    public boolean getReloadable();


    /**
     * Set the reloadable flag for this web application.
     *
     * @param reloadable The new reloadable flag
     */
    public void setReloadable(boolean reloadable);


    /**
     * Return the override flag for this web application.
     */
    public boolean getOverride();


    /**
     * Set the override flag for this web application.
     *
     * @param override The new override flag
     */
    public void setOverride(boolean override);


    /**
     * Return the privileged flag for this web application.
     */
    public boolean getPrivileged();


    /**
     * Set the privileged flag for this web application.
     *
     * @param privileged The new privileged flag
     */
    public void setPrivileged(boolean privileged);


    /**
     * Return the servlet context for which this Context is a facade.
     */
    public ServletContext getServletContext();


    /**
     * Return the default session timeout (in minutes) for this
     * web application.
     */
    public int getSessionTimeout();


    /**
     * Set the default session timeout (in minutes) for this
     * web application.
     *
     * @param timeout The new default session timeout
     */
    public void setSessionTimeout(int timeout);


    /**
     * Return the value of the swallowOutput flag.
     */
    public boolean getSwallowOutput();


    /**
     * Set the value of the swallowOutput flag. If set to true, the system.out
     * and system.err will be redirected to the logger during a servlet
     * execution.
     *
     * @param swallowOutput The new value
     */
    public void setSwallowOutput(boolean swallowOutput);


    /**
     * Return the Java class name of the Wrapper implementation used
     * for servlets registered in this Context.
     */
    public String getWrapperClass();


    /**
     * Set the Java class name of the Wrapper implementation used
     * for servlets registered in this Context.
     *
     * @param wrapperClass The new wrapper class
     */
    public void setWrapperClass(String wrapperClass);


    // --------------------------------------------------------- Public Methods


    /**
     * Add a new Listener class name to the set of Listeners
     * configured for this application.
     *
     * @param listener Java class name of a listener class
     */
    public void addApplicationListener(String listener);


    /**
     * Add a new application parameter for this application.
     *
     * @param parameter The new application parameter
     */
    public void addApplicationParameter(ApplicationParameter parameter);


    /**
     * Add a security constraint to the set for this web application.
     */
    public void addConstraint(SecurityConstraint constraint);


    /**
     * Add an error page for the specified error or Java exception.
     *
     * @param errorPage The error page definition to be added
     */
    public void addErrorPage(ErrorPage errorPage);


    /**
     * Add a filter definition to this Context.
     *
     * @param filterDef The filter definition to be added
     */
    public void addFilterDef(FilterDef filterDef);


    /**
     * Add a filter mapping to this Context.
     *
     * @param filterMap The filter mapping to be added
     */
    public void addFilterMap(FilterMap filterMap);


    /**
     * Add the classname of an InstanceListener to be added to each
     * Wrapper appended to this Context.
     *
     * @param listener Java class name of an InstanceListener class
     */
    public void addInstanceListener(String listener);


    /**
     * Add the given URL pattern as a jsp-property-group.  This maps
     * resources that match the given pattern so they will be passed
     * to the JSP container.  Though there are other elements in the
     * property group, we only care about the URL pattern here.  The
     * JSP container will parse the rest.
     *
     * @param pattern URL pattern to be mapped 
     */
    public void addJspMapping(String pattern);


    /**
     * Add a Locale Encoding Mapping (see Sec 5.4 of Servlet spec 2.4)
     *
     * @param locale locale to map an encoding for
     * @param encoding encoding to be used for a give locale
     */
    public void addLocaleEncodingMappingParameter(String locale, String encoding);


    /**
     * Add a new MIME mapping, replacing any existing mapping for
     * the specified extension.
     *
     * @param extension Filename extension being mapped
     * @param mimeType Corresponding MIME type
     */
    public void addMimeMapping(String extension, String mimeType);


    /**
     * Add a new context initialization parameter, replacing any existing
     * value for the specified name.
     *
     * @param name Name of the new parameter
     * @param value Value of the new  parameter
     */
    public void addParameter(String name, String value);


    /**
     * Add a security role reference for this web application.
     *
     * @param role Security role used in the application
     * @param link Actual security role to check for
     */
    public void addRoleMapping(String role, String link);


    /**
     * Add a new security role for this web application.
     *
     * @param role New security role
     */
    public void addSecurityRole(String role);


    /**
     * Add a new servlet mapping, replacing any existing mapping for
     * the specified pattern.
     *
     * @param pattern URL pattern to be mapped
     * @param name Name of the corresponding servlet to execute
     */
    public void addServletMapping(String pattern, String name);


    /**
     * Add a JSP tag library for the specified URI.
     *
     * @param uri URI, relative to the web.xml file, of this tag library
     * @param location Location of the tag library descriptor
     */
    public void addTaglib(String uri, String location);

    
    /**
     * Add a resource which will be watched for reloading by the host auto
     * deployer. Note: this will not be used in embedded mode.
     * 
     * @param name Path to the resource, relative to docBase
     */
    public void addWatchedResource(String name);
    

    /**
     * Add a new welcome file to the set recognized by this Context.
     *
     * @param name New welcome file name
     */
    public void addWelcomeFile(String name);


    /**
     * Add the classname of a LifecycleListener to be added to each
     * Wrapper appended to this Context.
     *
     * @param listener Java class name of a LifecycleListener class
     */
    public void addWrapperLifecycle(String listener);


    /**
     * Add the classname of a ContainerListener to be added to each
     * Wrapper appended to this Context.
     *
     * @param listener Java class name of a ContainerListener class
     */
    public void addWrapperListener(String listener);


    /**
     * Factory method to create and return a new Wrapper instance, of
     * the Java implementation class appropriate for this Context
     * implementation.  The constructor of the instantiated Wrapper
     * will have been called, but no properties will have been set.
     */
    public Wrapper createWrapper();


    /**
     * Return the set of application listener class names configured
     * for this application.
     */
    public String[] findApplicationListeners();


    /**
     * Return the set of application parameters for this application.
     */
    public ApplicationParameter[] findApplicationParameters();


    /**
     * Return the set of security constraints for this web application.
     * If there are none, a zero-length array is returned.
     */
    public SecurityConstraint[] findConstraints();


    /**
     * Return the error page entry for the specified HTTP error code,
     * if any; otherwise return <code>null</code>.
     *
     * @param errorCode Error code to look up
     */
    public ErrorPage findErrorPage(int errorCode);


    /**
     * Return the error page entry for the specified Java exception type,
     * if any; otherwise return <code>null</code>.
     *
     * @param exceptionType Exception type to look up
     */
    public ErrorPage findErrorPage(String exceptionType);



    /**
     * Return the set of defined error pages for all specified error codes
     * and exception types.
     */
    public ErrorPage[] findErrorPages();


    /**
     * Return the filter definition for the specified filter name, if any;
     * otherwise return <code>null</code>.
     *
     * @param filterName Filter name to look up
     */
    public FilterDef findFilterDef(String filterName);


    /**
     * Return the set of defined filters for this Context.
     */
    public FilterDef[] findFilterDefs();


    /**
     * Return the set of filter mappings for this Context.
     */
    public FilterMap[] findFilterMaps();


    /**
     * Return the set of InstanceListener classes that will be added to
     * newly created Wrappers automatically.
     */
    public String[] findInstanceListeners();


    /**
     * Return the MIME type to which the specified extension is mapped,
     * if any; otherwise return <code>null</code>.
     *
     * @param extension Extension to map to a MIME type
     */
    public String findMimeMapping(String extension);


    /**
     * Return the extensions for which MIME mappings are defined.  If there
     * are none, a zero-length array is returned.
     */
    public String[] findMimeMappings();


    /**
     * Return the value for the specified context initialization
     * parameter name, if any; otherwise return <code>null</code>.
     *
     * @param name Name of the parameter to return
     */
    public String findParameter(String name);


    /**
     * Return the names of all defined context initialization parameters
     * for this Context.  If no parameters are defined, a zero-length
     * array is returned.
     */
    public String[] findParameters();


    /**
     * For the given security role (as used by an application), return the
     * corresponding role name (as defined by the underlying Realm) if there
     * is one.  Otherwise, return the specified role unchanged.
     *
     * @param role Security role to map
     */
    public String findRoleMapping(String role);


    /**
     * Return <code>true</code> if the specified security role is defined
     * for this application; otherwise return <code>false</code>.
     *
     * @param role Security role to verify
     */
    public boolean findSecurityRole(String role);


    /**
     * Return the security roles defined for this application.  If none
     * have been defined, a zero-length array is returned.
     */
    public String[] findSecurityRoles();


    /**
     * Return the servlet name mapped by the specified pattern (if any);
     * otherwise return <code>null</code>.
     *
     * @param pattern Pattern for which a mapping is requested
     */
    public String findServletMapping(String pattern);


    /**
     * Return the patterns of all defined servlet mappings for this
     * Context.  If no mappings are defined, a zero-length array is returned.
     */
    public String[] findServletMappings();


    /**
     * Return the context-relative URI of the error page for the specified
     * HTTP status code, if any; otherwise return <code>null</code>.
     *
     * @param status HTTP status code to look up
     */
    public String findStatusPage(int status);


    /**
     * Return the set of HTTP status codes for which error pages have
     * been specified.  If none are specified, a zero-length array
     * is returned.
     */
    public int[] findStatusPages();


    /**
     * Return the tag library descriptor location for the specified taglib
     * URI, if any; otherwise, return <code>null</code>.
     *
     * @param uri URI, relative to the web.xml file
     */
    public String findTaglib(String uri);


    /**
     * Return the URIs of all tag libraries for which a tag library
     * descriptor location has been specified.  If none are specified,
     * a zero-length array is returned.
     */
    public String[] findTaglibs();


    /**
     * Return the set of watched resources for this Context. If none are 
     * defined, a zero length array will be returned.
     */
    public String[] findWatchedResources();
    

    /**
     * Return <code>true</code> if the specified welcome file is defined
     * for this Context; otherwise return <code>false</code>.
     *
     * @param name Welcome file to verify
     */
    public boolean findWelcomeFile(String name);

    
    /**
     * Return the set of welcome files defined for this Context.  If none are
     * defined, a zero-length array is returned.
     */
    public String[] findWelcomeFiles();


    /**
     * Return the set of LifecycleListener classes that will be added to
     * newly created Wrappers automatically.
     */
    public String[] findWrapperLifecycles();


    /**
     * Return the set of ContainerListener classes that will be added to
     * newly created Wrappers automatically.
     */
    public String[] findWrapperListeners();


    /**
     * Reload this web application, if reloading is supported.
     *
     * @exception IllegalStateException if the <code>reloadable</code>
     *  property is set to <code>false</code>.
     */
    public void reload();


    /**
     * Remove the specified application listener class from the set of
     * listeners for this application.
     *
     * @param listener Java class name of the listener to be removed
     */
    public void removeApplicationListener(String listener);


    /**
     * Remove the application parameter with the specified name from
     * the set for this application.
     *
     * @param name Name of the application parameter to remove
     */
    public void removeApplicationParameter(String name);


    /**
     * Remove the specified security constraint from this web application.
     *
     * @param constraint Constraint to be removed
     */
    public void removeConstraint(SecurityConstraint constraint);


    /**
     * Remove the error page for the specified error code or
     * Java language exception, if it exists; otherwise, no action is taken.
     *
     * @param errorPage The error page definition to be removed
     */
    public void removeErrorPage(ErrorPage errorPage);


    /**
     * Remove the specified filter definition from this Context, if it exists;
     * otherwise, no action is taken.
     *
     * @param filterDef Filter definition to be removed
     */
    public void removeFilterDef(FilterDef filterDef);


    /**
     * Remove a filter mapping from this Context.
     *
     * @param filterMap The filter mapping to be removed
     */
    public void removeFilterMap(FilterMap filterMap);


    /**
     * Remove a class name from the set of InstanceListener classes that
     * will be added to newly created Wrappers.
     *
     * @param listener Class name of an InstanceListener class to be removed
     */
    public void removeInstanceListener(String listener);


    /**
     * Remove the MIME mapping for the specified extension, if it exists;
     * otherwise, no action is taken.
     *
     * @param extension Extension to remove the mapping for
     */
    public void removeMimeMapping(String extension);


    /**
     * Remove the context initialization parameter with the specified
     * name, if it exists; otherwise, no action is taken.
     *
     * @param name Name of the parameter to remove
     */
    public void removeParameter(String name);


    /**
     * Remove any security role reference for the specified name
     *
     * @param role Security role (as used in the application) to remove
     */
    public void removeRoleMapping(String role);


    /**
     * Remove any security role with the specified name.
     *
     * @param role Security role to remove
     */
    public void removeSecurityRole(String role);


    /**
     * Remove any servlet mapping for the specified pattern, if it exists;
     * otherwise, no action is taken.
     *
     * @param pattern URL pattern of the mapping to remove
     */
    public void removeServletMapping(String pattern);


    /**
     * Remove the tag library location forthe specified tag library URI.
     *
     * @param uri URI, relative to the web.xml file
     */
    public void removeTaglib(String uri);

    
    /**
     * Remove the specified watched resource name from the list associated
     * with this Context.
     * 
     * @param name Name of the watched resource to be removed
     */
    public void removeWatchedResource(String name);
    

    /**
     * Remove the specified welcome file name from the list recognized
     * by this Context.
     *
     * @param name Name of the welcome file to be removed
     */
    public void removeWelcomeFile(String name);


    /**
     * Remove a class name from the set of LifecycleListener classes that
     * will be added to newly created Wrappers.
     *
     * @param listener Class name of a LifecycleListener class to be removed
     */
    public void removeWrapperLifecycle(String listener);


    /**
     * Remove a class name from the set of ContainerListener classes that
     * will be added to newly created Wrappers.
     *
     * @param listener Class name of a ContainerListener class to be removed
     */
    public void removeWrapperListener(String listener);


    /**
     * Get the server.xml <context> attribute's xmlNamespaceAware.
     * @return true if namespace awarenes is enabled.
     *
     */
    public boolean getXmlNamespaceAware();


    /**
     * Get the server.xml <context> attribute's xmlValidation.
     * @return true if validation is enabled.
     *
     */
    public boolean getXmlValidation();


    /**
     * Set the validation feature of the XML parser used when
     * parsing xml instances.
     * @param xmlValidation true to enable xml instance validation
     */
    public void setXmlValidation(boolean xmlValidation);


   /**
     * Set the namespace aware feature of the XML parser used when
     * parsing xml instances.
     * @param xmlNamespaceAware true to enable namespace awareness
     */
    public void setXmlNamespaceAware(boolean xmlNamespaceAware);
    /**
     * Get the server.xml <context> attribute's xmlValidation.
     * @return true if validation is enabled.
     */
     

    /**
     * Set the validation feature of the XML parser used when
     * parsing tlds files. 
     * @param tldValidation true to enable xml instance validation
     */
    public void setTldValidation(boolean tldValidation);


    /**
     * Get the server.xml <context> attribute's webXmlValidation.
     * @return true if validation is enabled.
     *
     */
    public boolean getTldValidation();


    /**
     * Get the server.xml <host> attribute's xmlNamespaceAware.
     * @return true if namespace awarenes is enabled.
     */
    public boolean getTldNamespaceAware();


    /**
     * Set the namespace aware feature of the XML parser used when
     * parsing xml instances.
     * @param tldNamespaceAware true to enable namespace awareness
     */
    public void setTldNamespaceAware(boolean tldNamespaceAware);


}

