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
package org.apache.tomcat.util.descriptor.web;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.descriptor.JspPropertyGroupDescriptor;
import jakarta.servlet.descriptor.TaglibDescriptor;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.descriptor.XmlIdentifiers;
import org.apache.tomcat.util.digester.DocumentProperties;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;

/**
 * Representation of common elements of web.xml and web-fragment.xml. Provides a repository for parsed data before the
 * elements are merged. Validation is spread between multiple classes: The digester checks for structural correctness
 * (e.g. single login-config) This class checks for invalid duplicates (e.g. filter/servlet names) StandardContext will
 * check validity of values (e.g. URL formats etc)
 */
public class WebXml extends XmlEncodingBase implements DocumentProperties.Charset {

    /**
     * Constructs a new WebXml.
     */
    public WebXml() {
    }

    /**
     * Marker for "others" in ordering.
     */
    protected static final String ORDER_OTHERS = "org.apache.catalina.order.others";

    private static final StringManager sm = StringManager.getManager(Constants.PACKAGE_NAME);

    private final Log log = LogFactory.getLog(WebXml.class); // must not be static

    /**
     * Global defaults are overridable but Servlets and Servlet mappings need to be unique. Duplicates normally trigger
     * an error. This flag indicates if newly added Servlet elements are marked as overridable.
     */
    private boolean overridable = false;

    /**
     * Returns whether this web fragment is overridable.
     *
     * @return {@code true} if overridable
     */
    public boolean isOverridable() {
        return overridable;
    }

    /**
     * Sets whether this web fragment is overridable.
     *
     * @param overridable The overridable flag
     */
    public void setOverridable(boolean overridable) {
        this.overridable = overridable;
    }

    /*
     * Ideally, fragment names will be unique. If they are not, Tomcat needs to know as the action that the
     * specification requires (see 8.2.2 1.e and 2.c) varies depending on the ordering method used.
     */
    private final List<String> duplicates = new ArrayList<>();

    /**
     * Returns whether this web fragment has duplicate entries.
     *
     * @return {@code true} if duplicates exist
     */
    public boolean isDuplicated() {
        return !duplicates.isEmpty();
    }

    /**
     * Adds a duplicate fragment name.
     *
     * @param duplicate The duplicate fragment name
     */
    public void addDuplicate(String duplicate) {
        this.duplicates.add(duplicate);
    }

    /**
     * Returns the list of duplicate fragment names.
     *
     * @return the duplicate fragment names
     */
    public List<String> getDuplicates() {
        return new ArrayList<>(this.duplicates);
    }

    /**
     * web.xml only elements Absolute Ordering
     */
    private Set<String> absoluteOrdering = null;

    /**
     * Initializes the absolute ordering set if it has not been created yet.
     */
    public void createAbsoluteOrdering() {
        if (absoluteOrdering == null) {
            absoluteOrdering = new LinkedHashSet<>();
        }
    }

    /**
     * Adds a fragment name to the absolute ordering.
     *
     * @param fragmentName The fragment name to add
     */
    public void addAbsoluteOrdering(String fragmentName) {
        createAbsoluteOrdering();
        absoluteOrdering.add(fragmentName);
    }

    /**
     * Adds "others" to the absolute ordering.
     */
    public void addAbsoluteOrderingOthers() {
        createAbsoluteOrdering();
        absoluteOrdering.add(ORDER_OTHERS);
    }

    /**
     * Returns the absolute ordering set.
     *
     * @return the absolute ordering
     */
    public Set<String> getAbsoluteOrdering() {
        return absoluteOrdering;
    }

    /**
     * web-fragment.xml only elements Relative ordering
     */
    private final Set<String> after = new LinkedHashSet<>();

    /**
     * Adds a fragment name to the after ordering.
     *
     * @param fragmentName The fragment name to add
     */
    public void addAfterOrdering(String fragmentName) {
        after.add(fragmentName);
    }

    /**
     * Adds "others" to the after ordering.
     *
     * @throws IllegalArgumentException If "others" is already in the before ordering
     */
    public void addAfterOrderingOthers() {
        if (before.contains(ORDER_OTHERS)) {
            throw new IllegalArgumentException(sm.getString("webXml.multipleOther"));
        }
        after.add(ORDER_OTHERS);
    }

    /**
     * Returns the after ordering set.
     *
     * @return the after ordering
     */
    public Set<String> getAfterOrdering() {
        return after;
    }

    private final Set<String> before = new LinkedHashSet<>();

    /**
     * Adds a fragment name to the before ordering.
     *
     * @param fragmentName The fragment name to add
     */
    public void addBeforeOrdering(String fragmentName) {
        before.add(fragmentName);
    }

    /**
     * Adds "others" to the before ordering.
     *
     * @throws IllegalArgumentException If "others" is already in the after ordering
     */
    public void addBeforeOrderingOthers() {
        if (after.contains(ORDER_OTHERS)) {
            throw new IllegalArgumentException(sm.getString("webXml.multipleOther"));
        }
        before.add(ORDER_OTHERS);
    }

    /**
     * Returns the before ordering set.
     *
     * @return the before ordering
     */
    public Set<String> getBeforeOrdering() {
        return before;
    }

    // Common elements and attributes
    // Required attribute of web-app element
    /**
     * Returns the version of the web.xml.
     *
     * @return the version
     */
    public String getVersion() {
        StringBuilder sb = new StringBuilder(3);
        sb.append(majorVersion);
        sb.append('.');
        sb.append(minorVersion);
        return sb.toString();
    }

    /**
     * Set the version for this web.xml file
     *
     * @param version Values of <code>null</code> will be ignored
     */
    public void setVersion(String version) {
        if (version == null) {
            return;
        }
        switch (version) {
            case "2.4":
                majorVersion = 2;
                minorVersion = 4;
                break;
            case "2.5":
                majorVersion = 2;
                minorVersion = 5;
                break;
            case "3.0":
                majorVersion = 3;
                minorVersion = 0;
                break;
            case "3.1":
                majorVersion = 3;
                minorVersion = 1;
                break;
            case "4.0":
                majorVersion = 4;
                minorVersion = 0;
                break;
            case "5.0":
                majorVersion = 5;
                minorVersion = 0;
                break;
            case "6.0":
                majorVersion = 6;
                minorVersion = 0;
                break;
            case "6.1":
                majorVersion = 6;
                minorVersion = 1;
                break;
            default:
                log.warn(sm.getString("webXml.version.unknown", version));
        }
    }


    // Optional publicId attribute
    private String publicId = null;

    /**
     * Returns the public identifier.
     *
     * @return the public identifier
     */
    public String getPublicId() {
        return publicId;
    }

    /**
     * Sets the public identifier.
     *
     * @param publicId The public identifier
     */
    public void setPublicId(String publicId) {
        // Update major and minor version
        if (publicId == null) {
            return;
        }
        switch (publicId) {
            case XmlIdentifiers.WEB_22_PUBLIC:
                majorVersion = 2;
                minorVersion = 2;
                this.publicId = publicId;
                break;
            case XmlIdentifiers.WEB_23_PUBLIC:
                majorVersion = 2;
                minorVersion = 3;
                this.publicId = publicId;
                break;
            default:
                log.warn(sm.getString("webXml.unrecognisedPublicId", publicId));
                break;
        }
    }

    // Optional metadata-complete attribute
    private boolean metadataComplete = false;

    /**
     * Returns whether metadata is complete.
     *
     * @return {@code true} if metadata is complete
     */
    public boolean isMetadataComplete() {
        return metadataComplete;
    }

    /**
     * Sets whether metadata is complete.
     *
     * @param metadataComplete The metadata complete flag
     */
    public void setMetadataComplete(boolean metadataComplete) {
        this.metadataComplete = metadataComplete;
    }

    // Optional name element
    private String name = null;

    /**
     * Returns the name of the web application.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the web application.
     *
     * @param name The name
     */
    public void setName(String name) {
        if (ORDER_OTHERS.equalsIgnoreCase(name)) {
            // This is unusual. This name will be ignored. Log the fact.
            log.warn(sm.getString("webXml.reservedName", name));
        } else {
            this.name = name;
        }
    }

    // Derived major and minor version attributes
    private int majorVersion = 6;
    private int minorVersion = 0;

    /**
     * Returns the major version number.
     *
     * @return the major version
     */
    public int getMajorVersion() {
        return majorVersion;
    }

    /**
     * Returns the minor version number.
     *
     * @return the minor version
     */
    public int getMinorVersion() {
        return minorVersion;
    }

    // web-app elements
    // TODO: Ignored elements:
    // - description
    // - icon

    // display-name - TODO should support multiple with language
    private String displayName = null;

    /**
     * Returns the display name of the web application.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the display name.
     *
     * @param displayName The display name
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    // distributable
    private boolean distributable = false;

    /**
     * Returns whether the web application is distributable.
     *
     * @return {@code true} if distributable
     */
    public boolean isDistributable() {
        return distributable;
    }

    /**
     * Sets whether the web application is distributable.
     *
     * @param distributable The distributable flag
     */
    public void setDistributable(boolean distributable) {
        this.distributable = distributable;
    }

    // deny-uncovered-http-methods
    private boolean denyUncoveredHttpMethods = false;

    /**
     * Returns whether uncovered HTTP methods are denied.
     *
     * @return {@code true} if uncovered HTTP methods are denied
     */
    public boolean getDenyUncoveredHttpMethods() {
        return denyUncoveredHttpMethods;
    }

    /**
     * Sets whether uncovered HTTP methods are denied.
     *
     * @param denyUncoveredHttpMethods The deny uncovered HTTP methods flag
     */
    public void setDenyUncoveredHttpMethods(boolean denyUncoveredHttpMethods) {
        this.denyUncoveredHttpMethods = denyUncoveredHttpMethods;
    }

    // context-param
    // TODO: description (multiple with language) is ignored
    private final Map<String,String> contextParams = new HashMap<>();

    /**
     * Adds a context parameter.
     *
     * @param param  The parameter name
     * @param value  The parameter value
     */
    public void addContextParam(String param, String value) {
        contextParams.put(param, value);
    }

    /**
     * Returns the context parameters.
     *
     * @return the context parameters
     */
    public Map<String,String> getContextParams() {
        return contextParams;
    }

    // filter
    // TODO: Should support multiple description elements with language
    // TODO: Should support multiple display-name elements with language
    // TODO: Should support multiple icon elements
    // TODO: Description for init-param is ignored
    private final Map<String,FilterDef> filters = new LinkedHashMap<>();

    /**
     * Adds a filter definition.
     *
     * @param filter The filter definition
     *
     * @throws IllegalArgumentException If a filter with the same name already exists
     */
    public void addFilter(FilterDef filter) {
        if (filters.containsKey(filter.getFilterName())) {
            // Filter names must be unique within a web(-fragment).xml
            throw new IllegalArgumentException(sm.getString("webXml.duplicateFilter", filter.getFilterName()));
        }
        filters.put(filter.getFilterName(), filter);
    }

    /**
     * Returns the filter definitions.
     *
     * @return the filter definitions
     */
    public Map<String,FilterDef> getFilters() {
        return filters;
    }

    // filter-mapping
    private final Set<FilterMap> filterMaps = new LinkedHashSet<>();
    private final Set<String> filterMappingNames = new HashSet<>();

    /**
     * Adds a filter mapping.
     *
     * @param filterMap The filter mapping
     */
    public void addFilterMapping(FilterMap filterMap) {
        filterMap.setCharset(getCharset());
        filterMaps.add(filterMap);
        filterMappingNames.add(filterMap.getFilterName());
    }

    /**
     * Returns the filter mappings.
     *
     * @return the filter mappings
     */
    public Set<FilterMap> getFilterMappings() {
        return filterMaps;
    }

    // listener
    // TODO: description (multiple with language) is ignored
    // TODO: display-name (multiple with language) is ignored
    // TODO: icon (multiple) is ignored
    private final Set<String> listeners = new LinkedHashSet<>();

    /**
     * Adds a listener class.
     *
     * @param className The listener class name
     */
    public void addListener(String className) {
        listeners.add(className);
    }

    /**
     * Returns the listener class names.
     *
     * @return the listener class names
     */
    public Set<String> getListeners() {
        return listeners;
    }

    // servlet
    // TODO: description (multiple with language) is ignored
    // TODO: display-name (multiple with language) is ignored
    // TODO: icon (multiple) is ignored
    // TODO: init-param/description (multiple with language) is ignored
    // TODO: security-role-ref/description (multiple with language) is ignored
    private final Map<String,ServletDef> servlets = new HashMap<>();

    /**
     * Adds a servlet definition.
     *
     * @param servletDef The servlet definition
     */
    public void addServlet(ServletDef servletDef) {
        servlets.put(servletDef.getServletName(), servletDef);
        if (overridable) {
            servletDef.setOverridable(true);
        }
    }

    /**
     * Returns the servlet definitions.
     *
     * @return the servlet definitions
     */
    public Map<String,ServletDef> getServlets() {
        return servlets;
    }

    // servlet-mapping
    // Note: URLPatterns from web.xml may be URL encoded
    // (https://svn.apache.org/r285186)
    private final Map<String,String> servletMappings = new HashMap<>();
    private final Set<String> servletMappingNames = new HashSet<>();

    /**
     * Adds a servlet URL mapping.
     *
     * @param urlPattern   The URL pattern
     * @param servletName  The servlet name
     */
    public void addServletMapping(String urlPattern, String servletName) {
        addServletMappingDecoded(UDecoder.URLDecode(urlPattern, getCharset()), servletName);
    }

    /**
     * Adds a servlet URL mapping (already decoded).
     *
     * @param urlPattern   The URL pattern
     * @param servletName  The servlet name
     *
     * @throws IllegalArgumentException If a duplicate mapping exists
     */
    public void addServletMappingDecoded(String urlPattern, String servletName) {
        String oldServletName = servletMappings.put(urlPattern, servletName);
        if (oldServletName != null) {
            // Duplicate mapping. As per clarification from the Servlet EG,
            // deployment should fail.
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateServletMapping", oldServletName, servletName, urlPattern));
        }
        servletMappingNames.add(servletName);
    }

    /**
     * Returns the servlet URL mappings.
     *
     * @return the servlet mappings
     */
    public Map<String,String> getServletMappings() {
        return servletMappings;
    }

    // session-config
    // Digester will check there is only one of these
    private SessionConfig sessionConfig = new SessionConfig();

    /**
     * Sets the session configuration.
     *
     * @param sessionConfig The session configuration
     */
    public void setSessionConfig(SessionConfig sessionConfig) {
        this.sessionConfig = sessionConfig;
    }

    /**
     * Returns the session configuration.
     *
     * @return the session configuration
     */
    public SessionConfig getSessionConfig() {
        return sessionConfig;
    }

    // mime-mapping
    private final Map<String,String> mimeMappings = new HashMap<>();

    /**
     * Adds a MIME mapping.
     *
     * @param extension  The file extension
     * @param mimeType   The MIME type
     */
    public void addMimeMapping(String extension, String mimeType) {
        mimeMappings.put(extension, mimeType);
    }

    /**
     * Returns the MIME mappings.
     *
     * @return the MIME mappings
     */
    public Map<String,String> getMimeMappings() {
        return mimeMappings;
    }

    // welcome-file-list merge control
    private boolean replaceWelcomeFiles = false;
    private boolean alwaysAddWelcomeFiles = true;

    /**
     * Sets whether welcome files should replace existing ones.
     *
     * @param replaceWelcomeFiles The replace welcome files flag
     */
    public void setReplaceWelcomeFiles(boolean replaceWelcomeFiles) {
        this.replaceWelcomeFiles = replaceWelcomeFiles;
    }

    /**
     * When merging from this web.xml, should the welcome files be added to the target web.xml even if it already
     * contains welcome file definitions.
     *
     * @param alwaysAddWelcomeFiles <code>true</code> to add welcome files
     */
    public void setAlwaysAddWelcomeFiles(boolean alwaysAddWelcomeFiles) {
        this.alwaysAddWelcomeFiles = alwaysAddWelcomeFiles;
    }

    // welcome-file-list
    private final Set<String> welcomeFiles = new LinkedHashSet<>();

    /**
     * Adds a welcome file.
     *
     * @param welcomeFile The welcome file name
     */
    public void addWelcomeFile(String welcomeFile) {
        if (replaceWelcomeFiles) {
            welcomeFiles.clear();
            replaceWelcomeFiles = false;
        }
        welcomeFiles.add(welcomeFile);
    }

    /**
     * Returns the welcome files.
     *
     * @return the welcome files
     */
    public Set<String> getWelcomeFiles() {
        return welcomeFiles;
    }

    // error-page
    private final Map<String,ErrorPage> errorPages = new HashMap<>();

    /**
     * Adds an error page.
     *
     * @param errorPage The error page
     */
    public void addErrorPage(ErrorPage errorPage) {
        errorPage.setCharset(getCharset());
        errorPages.put(errorPage.getName(), errorPage);
    }

    /**
     * Returns the error pages.
     *
     * @return the error pages
     */
    public Map<String,ErrorPage> getErrorPages() {
        return errorPages;
    }

    // Digester will check there is only one jsp-config
    // jsp-config/taglib or taglib (2.3 and earlier)
    private final Map<String,String> taglibs = new HashMap<>();

    /**
     * Adds a taglib mapping.
     *
     * @param uri      The taglib URI
     * @param location The taglib location
     */
    public void addTaglib(String uri, String location) {
        if (taglibs.containsKey(uri)) {
            // Taglib URIs must be unique within a web(-fragment).xml
            throw new IllegalArgumentException(sm.getString("webXml.duplicateTaglibUri", uri));
        }
        taglibs.put(uri, location);
    }

    /**
     * Returns the taglib mappings.
     *
     * @return the taglib mappings
     */
    public Map<String,String> getTaglibs() {
        return taglibs;
    }

    // jsp-config/jsp-property-group
    private final Set<JspPropertyGroup> jspPropertyGroups = new LinkedHashSet<>();

    /**
     * Adds a JSP property group.
     *
     * @param propertyGroup The JSP property group
     */
    public void addJspPropertyGroup(JspPropertyGroup propertyGroup) {
        propertyGroup.setCharset(getCharset());
        jspPropertyGroups.add(propertyGroup);
    }

    /**
     * Returns the JSP property groups.
     *
     * @return the JSP property groups
     */
    public Set<JspPropertyGroup> getJspPropertyGroups() {
        return jspPropertyGroups;
    }

    // security-constraint
    // TODO: Should support multiple display-name elements with language
    // TODO: Should support multiple description elements with language
    private final Set<SecurityConstraint> securityConstraints = new HashSet<>();

    /**
     * Adds a security constraint.
     *
     * @param securityConstraint The security constraint
     */
    public void addSecurityConstraint(SecurityConstraint securityConstraint) {
        securityConstraint.setCharset(getCharset());
        securityConstraints.add(securityConstraint);
    }

    /**
     * Returns the security constraints.
     *
     * @return the security constraints
     */
    public Set<SecurityConstraint> getSecurityConstraints() {
        return securityConstraints;
    }

    // login-config
    // Digester will check there is only one of these
    private LoginConfig loginConfig = null;

    /**
     * Sets the login configuration.
     *
     * @param loginConfig The login configuration
     */
    public void setLoginConfig(LoginConfig loginConfig) {
        loginConfig.setCharset(getCharset());
        this.loginConfig = loginConfig;
    }

    /**
     * Returns the login configuration.
     *
     * @return the login configuration
     */
    public LoginConfig getLoginConfig() {
        return loginConfig;
    }

    // security-role
    // TODO: description (multiple with language) is ignored
    private final Set<String> securityRoles = new HashSet<>();

    /**
     * Adds a security role.
     *
     * @param securityRole The security role name
     */
    public void addSecurityRole(String securityRole) {
        securityRoles.add(securityRole);
    }

    /**
     * Returns the security roles.
     *
     * @return the security roles
     */
    public Set<String> getSecurityRoles() {
        return securityRoles;
    }

    // env-entry
    // TODO: Should support multiple description elements with language
    private final Map<String,ContextEnvironment> envEntries = new HashMap<>();

    /**
     * Adds an environment entry.
     *
     * @param envEntry The environment entry
     *
     * @throws IllegalArgumentException If an entry with the same name already exists
     */
    public void addEnvEntry(ContextEnvironment envEntry) {
        if (envEntries.containsKey(envEntry.getName())) {
            // env-entry names must be unique within a web(-fragment).xml
            throw new IllegalArgumentException(sm.getString("webXml.duplicateEnvEntry", envEntry.getName()));
        }
        envEntries.put(envEntry.getName(), envEntry);
    }

    /**
     * Returns the environment entries.
     *
     * @return the environment entries
     */
    public Map<String,ContextEnvironment> getEnvEntries() {
        return envEntries;
    }

    // ejb-ref
    // TODO: Should support multiple description elements with language
    private final Map<String,ContextEjb> ejbRefs = new HashMap<>();

    /**
     * Adds an EJB reference.
     *
     * @param ejbRef The EJB reference
     */
    public void addEjbRef(ContextEjb ejbRef) {
        ejbRefs.put(ejbRef.getName(), ejbRef);
    }

    /**
     * Returns the EJB references.
     *
     * @return the EJB references
     */
    public Map<String,ContextEjb> getEjbRefs() {
        return ejbRefs;
    }

    // ejb-local-ref
    // TODO: Should support multiple description elements with language
    private final Map<String,ContextLocalEjb> ejbLocalRefs = new HashMap<>();

    /**
     * Adds an EJB local reference.
     *
     * @param ejbLocalRef The EJB local reference
     */
    public void addEjbLocalRef(ContextLocalEjb ejbLocalRef) {
        ejbLocalRefs.put(ejbLocalRef.getName(), ejbLocalRef);
    }

    /**
     * Returns the EJB local references.
     *
     * @return the EJB local references
     */
    public Map<String,ContextLocalEjb> getEjbLocalRefs() {
        return ejbLocalRefs;
    }

    // service-ref
    // TODO: Should support multiple description elements with language
    // TODO: Should support multiple display-names elements with language
    // TODO: Should support multiple icon elements ???
    private final Map<String,ContextService> serviceRefs = new HashMap<>();

    /**
     * Adds a service reference.
     *
     * @param serviceRef The service reference
     */
    public void addServiceRef(ContextService serviceRef) {
        serviceRefs.put(serviceRef.getName(), serviceRef);
    }

    /**
     * Returns the service references.
     *
     * @return the service references
     */
    public Map<String,ContextService> getServiceRefs() {
        return serviceRefs;
    }

    // resource-ref
    // TODO: Should support multiple description elements with language
    private final Map<String,ContextResource> resourceRefs = new HashMap<>();

    /**
     * Adds a resource reference.
     *
     * @param resourceRef The resource reference
     *
     * @throws IllegalArgumentException If a reference with the same name already exists
     */
    public void addResourceRef(ContextResource resourceRef) {
        if (resourceRefs.containsKey(resourceRef.getName())) {
            // resource-ref names must be unique within a web(-fragment).xml
            throw new IllegalArgumentException(sm.getString("webXml.duplicateResourceRef", resourceRef.getName()));
        }
        resourceRefs.put(resourceRef.getName(), resourceRef);
    }

    /**
     * Returns the resource references.
     *
     * @return the resource references
     */
    public Map<String,ContextResource> getResourceRefs() {
        return resourceRefs;
    }

    // resource-env-ref
    // TODO: Should support multiple description elements with language
    private final Map<String,ContextResourceEnvRef> resourceEnvRefs = new HashMap<>();

    /**
     * Adds a resource environment reference.
     *
     * @param resourceEnvRef The resource environment reference
     *
     * @throws IllegalArgumentException If a reference with the same name already exists
     */
    public void addResourceEnvRef(ContextResourceEnvRef resourceEnvRef) {
        if (resourceEnvRefs.containsKey(resourceEnvRef.getName())) {
            // resource-env-ref names must be unique within a web(-fragment).xml
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateResourceEnvRef", resourceEnvRef.getName()));
        }
        resourceEnvRefs.put(resourceEnvRef.getName(), resourceEnvRef);
    }

    /**
     * Returns the resource environment references.
     *
     * @return the resource environment references
     */
    public Map<String,ContextResourceEnvRef> getResourceEnvRefs() {
        return resourceEnvRefs;
    }

    // message-destination-ref
    // TODO: Should support multiple description elements with language
    private final Map<String,MessageDestinationRef> messageDestinationRefs = new HashMap<>();

    /**
     * Adds a message destination reference.
     *
     * @param messageDestinationRef The message destination reference
     *
     * @throws IllegalArgumentException If a reference with the same name already exists
     */
    public void addMessageDestinationRef(MessageDestinationRef messageDestinationRef) {
        if (messageDestinationRefs.containsKey(messageDestinationRef.getName())) {
            // message-destination-ref names must be unique within a
            // web(-fragment).xml
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateMessageDestinationRef", messageDestinationRef.getName()));
        }
        messageDestinationRefs.put(messageDestinationRef.getName(), messageDestinationRef);
    }

    /**
     * Returns the message destination references.
     *
     * @return the message destination references
     */
    public Map<String,MessageDestinationRef> getMessageDestinationRefs() {
        return messageDestinationRefs;
    }

    // message-destination
    // TODO: Should support multiple description elements with language
    // TODO: Should support multiple display-names elements with language
    // TODO: Should support multiple icon elements ???
    private final Map<String,MessageDestination> messageDestinations = new HashMap<>();

    /**
     * Adds a message destination.
     *
     * @param messageDestination The message destination
     *
     * @throws IllegalArgumentException If a destination with the same name already exists
     */
    public void addMessageDestination(MessageDestination messageDestination) {
        if (messageDestinations.containsKey(messageDestination.getName())) {
            // message-destination names must be unique within a
            // web(-fragment).xml
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateMessageDestination", messageDestination.getName()));
        }
        messageDestinations.put(messageDestination.getName(), messageDestination);
    }

    /**
     * Returns the message destinations.
     *
     * @return the message destinations
     */
    public Map<String,MessageDestination> getMessageDestinations() {
        return messageDestinations;
    }

    // locale-encoding-mapping-list
    private final Map<String,String> localeEncodingMappings = new HashMap<>();

    /**
     * Adds a locale-encoding mapping.
     *
     * @param locale   The locale
     * @param encoding The encoding
     */
    public void addLocaleEncodingMapping(String locale, String encoding) {
        localeEncodingMappings.put(locale, encoding);
    }

    /**
     * Returns the locale-encoding mappings.
     *
     * @return the locale-encoding mappings
     */
    public Map<String,String> getLocaleEncodingMappings() {
        return localeEncodingMappings;
    }

    // post-construct elements
    private final Map<String,String> postConstructMethods = new HashMap<>();

    /**
     * Adds a post-construct method.
     *
     * @param clazz  The class name
     * @param method The method name
     */
    public void addPostConstructMethods(String clazz, String method) {
        if (!postConstructMethods.containsKey(clazz)) {
            postConstructMethods.put(clazz, method);
        }
    }

    /**
     * Returns the post-construct methods.
     *
     * @return the post-construct methods
     */
    public Map<String,String> getPostConstructMethods() {
        return postConstructMethods;
    }

    // pre-destroy elements
    private final Map<String,String> preDestroyMethods = new HashMap<>();

    /**
     * Adds a pre-destroy method.
     *
     * @param clazz  The class name
     * @param method The method name
     */
    public void addPreDestroyMethods(String clazz, String method) {
        if (!preDestroyMethods.containsKey(clazz)) {
            preDestroyMethods.put(clazz, method);
        }
    }

    /**
     * Returns the pre-destroy methods.
     *
     * @return the pre-destroy methods
     */
    public Map<String,String> getPreDestroyMethods() {
        return preDestroyMethods;
    }

    /**
     * Returns the JSP configuration descriptor.
     *
     * @return the JSP configuration descriptor
     */
    public JspConfigDescriptor getJspConfigDescriptor() {
        if (jspPropertyGroups.isEmpty() && taglibs.isEmpty()) {
            return null;
        }

        Collection<JspPropertyGroupDescriptor> descriptors = new ArrayList<>(jspPropertyGroups.size());
        for (JspPropertyGroup jspPropertyGroup : jspPropertyGroups) {
            JspPropertyGroupDescriptor descriptor = new JspPropertyGroupDescriptorImpl(jspPropertyGroup);
            descriptors.add(descriptor);

        }

        Collection<TaglibDescriptor> tlds = new HashSet<>(taglibs.size());
        for (Entry<String,String> entry : taglibs.entrySet()) {
            TaglibDescriptor descriptor = new TaglibDescriptorImpl(entry.getValue(), entry.getKey());
            tlds.add(descriptor);
        }
        return new JspConfigDescriptorImpl(descriptors, tlds);
    }

    private String requestCharacterEncoding;

    /**
     * Returns the request character encoding.
     *
     * @return the request character encoding
     */
    public String getRequestCharacterEncoding() {
        return requestCharacterEncoding;
    }

    /**
     * Sets the request character encoding.
     *
     * @param requestCharacterEncoding The request character encoding
     */
    public void setRequestCharacterEncoding(String requestCharacterEncoding) {
        if (requestCharacterEncoding != null) {
            try {
                B2CConverter.getCharset(requestCharacterEncoding);
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException(e);
            }
        }
        this.requestCharacterEncoding = requestCharacterEncoding;
    }

    private String responseCharacterEncoding;

    /**
     * Returns the response character encoding.
     *
     * @return the response character encoding
     */
    public String getResponseCharacterEncoding() {
        return responseCharacterEncoding;
    }

    /**
     * Sets the response character encoding.
     *
     * @param responseCharacterEncoding The response character encoding
     */
    public void setResponseCharacterEncoding(String responseCharacterEncoding) {
        if (responseCharacterEncoding != null) {
            try {
                B2CConverter.getCharset(responseCharacterEncoding);
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException(e);
            }
        }
        this.responseCharacterEncoding = responseCharacterEncoding;
    }

    // Attributes not defined in web.xml or web-fragment.xml

    // URL of JAR / exploded JAR for this web-fragment
    private URL uRL = null;

    /**
     * Sets the URL of the web.xml.
     *
     * @param url The URL
     */
    public void setURL(URL url) {
        this.uRL = url;
    }

    /**
     * Returns the URL of the web.xml.
     *
     * @return the URL
     */
    public URL getURL() {
        return uRL;
    }

    // Name of jar file
    private String jarName = null;

    /**
     * Sets the JAR name for this web fragment.
     *
     * @param jarName The JAR name
     */
    public void setJarName(String jarName) {
        this.jarName = jarName;
    }

    /**
     * Returns the JAR name for this web fragment.
     *
     * @return the JAR name
     */
    public String getJarName() {
        return jarName;
    }

    // Is this JAR part of the application or is it a container JAR? Assume it
    // is.
    private boolean webappJar = true;

    /**
     * Sets whether this descriptor came from a webapp JAR.
     *
     * @param webappJar The webapp JAR flag
     */
    public void setWebappJar(boolean webappJar) {
        this.webappJar = webappJar;
    }

    /**
     * Returns whether this descriptor came from a webapp JAR.
     *
     * @return {@code true} if from a webapp JAR
     */
    public boolean getWebappJar() {
        return webappJar;
    }

    // Does this web application delegate first for class loading?
    private boolean delegate = false;

    /**
     * Returns whether this web application delegates first for class loading.
     *
     * @return {@code true} if delegation is enabled
     */
    public boolean getDelegate() {
        return delegate;
    }

    /**
     * Sets whether this web application delegates first for class loading.
     *
     * @param delegate The delegate flag
     */
    public void setDelegate(boolean delegate) {
        this.delegate = delegate;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(32);
        buf.append("Name: ");
        buf.append(getName());
        buf.append(", URL: ");
        buf.append(getURL());
        return buf.toString();
    }

    private static final String INDENT2 = "  ";
    private static final String INDENT4 = "    ";
    private static final String INDENT6 = "      ";

    /**
     * Generate a web.xml in String form that matches the representation stored in this object.
     *
     * @return The complete contents of web.xml as a String
     */
    public String toXml() {
        StringBuilder sb = new StringBuilder(2048);
        // TODO - Various, icon, description etc elements are skipped - mainly
        // because they are ignored when web.xml is parsed - see above

        // NOTE - Elements need to be written in the order defined in the 2.3
        // DTD else validation of the merged web.xml will fail

        // NOTE - Some elements need to be skipped based on the version of the
        // specification being used. Version is validated and starts at
        // 2.2. The version tests used in this method take advantage of
        // this.

        // Declaration
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        // Root element
        if (publicId != null) {
            sb.append("<!DOCTYPE web-app PUBLIC\n");
            sb.append("  \"");
            sb.append(publicId);
            sb.append("\"\n");
            sb.append("  \"");
            if (XmlIdentifiers.WEB_22_PUBLIC.equals(publicId)) {
                sb.append(XmlIdentifiers.WEB_22_SYSTEM);
            } else {
                sb.append(XmlIdentifiers.WEB_23_SYSTEM);
            }
            sb.append("\">\n");
            sb.append("<web-app>");
        } else {
            String javaeeNamespace = null;
            String webXmlSchemaLocation = null;
            String version = getVersion();
            if ("2.4".equals(version)) {
                javaeeNamespace = XmlIdentifiers.JAVAEE_1_4_NS;
                webXmlSchemaLocation = XmlIdentifiers.WEB_24_XSD;
            } else if ("2.5".equals(version)) {
                javaeeNamespace = XmlIdentifiers.JAVAEE_5_NS;
                webXmlSchemaLocation = XmlIdentifiers.WEB_25_XSD;
            } else if ("3.0".equals(version)) {
                javaeeNamespace = XmlIdentifiers.JAVAEE_6_NS;
                webXmlSchemaLocation = XmlIdentifiers.WEB_30_XSD;
            } else if ("3.1".equals(version)) {
                javaeeNamespace = XmlIdentifiers.JAVAEE_7_NS;
                webXmlSchemaLocation = XmlIdentifiers.WEB_31_XSD;
            } else if ("4.0".equals(version)) {
                javaeeNamespace = XmlIdentifiers.JAVAEE_8_NS;
                webXmlSchemaLocation = XmlIdentifiers.WEB_40_XSD;
            } else if ("5.0".equals(version)) {
                javaeeNamespace = XmlIdentifiers.JAKARTAEE_9_NS;
                webXmlSchemaLocation = XmlIdentifiers.WEB_50_XSD;
            } else if ("6.0".equals(version)) {
                javaeeNamespace = XmlIdentifiers.JAKARTAEE_10_NS;
                webXmlSchemaLocation = XmlIdentifiers.WEB_60_XSD;
            } else if ("6.1".equals(version)) {
                javaeeNamespace = XmlIdentifiers.JAKARTAEE_11_NS;
                webXmlSchemaLocation = XmlIdentifiers.WEB_61_XSD;
            }
            sb.append("<web-app xmlns=\"");
            sb.append(javaeeNamespace);
            sb.append("\"\n");
            sb.append("         xmlns:xsi=");
            sb.append("\"http://www.w3.org/2001/XMLSchema-instance\"\n");
            sb.append("         xsi:schemaLocation=\"");
            sb.append(javaeeNamespace);
            sb.append(' ');
            sb.append(webXmlSchemaLocation);
            sb.append("\"\n");
            sb.append("         version=\"");
            sb.append(getVersion());
            sb.append("\"");
            if ("2.4".equals(version)) {
                sb.append(">\n\n");
            } else {
                sb.append("\n         metadata-complete=\"true\">\n\n");
            }
        }

        appendElement(sb, INDENT2, "display-name", displayName);

        if (isDistributable()) {
            sb.append("  <distributable/>\n\n");
        }

        for (Map.Entry<String,String> entry : contextParams.entrySet()) {
            sb.append("  <context-param>\n");
            appendElement(sb, INDENT4, "param-name", entry.getKey());
            appendElement(sb, INDENT4, "param-value", entry.getValue());
            sb.append("  </context-param>\n");
        }
        sb.append('\n');

        // Filters were introduced in Servlet 2.3
        if (getMajorVersion() > 2 || getMinorVersion() > 2) {
            for (Map.Entry<String,FilterDef> entry : filters.entrySet()) {
                FilterDef filterDef = entry.getValue();
                sb.append("  <filter>\n");
                appendElement(sb, INDENT4, "description", filterDef.getDescription());
                appendElement(sb, INDENT4, "display-name", filterDef.getDisplayName());
                appendElement(sb, INDENT4, "filter-name", filterDef.getFilterName());
                appendElement(sb, INDENT4, "filter-class", filterDef.getFilterClass());
                // Async support was introduced for Servlet 3.0 onwards
                if (getMajorVersion() != 2) {
                    appendElement(sb, INDENT4, "async-supported", filterDef.getAsyncSupported());
                }
                for (Map.Entry<String,String> param : filterDef.getParameterMap().entrySet()) {
                    sb.append("    <init-param>\n");
                    appendElement(sb, INDENT6, "param-name", param.getKey());
                    appendElement(sb, INDENT6, "param-value", param.getValue());
                    sb.append("    </init-param>\n");
                }
                sb.append("  </filter>\n");
            }
            sb.append('\n');

            for (FilterMap filterMap : filterMaps) {
                sb.append("  <filter-mapping>\n");
                appendElement(sb, INDENT4, "filter-name", filterMap.getFilterName());
                if (filterMap.getMatchAllServletNames()) {
                    sb.append("    <servlet-name>*</servlet-name>\n");
                } else {
                    for (String servletName : filterMap.getServletNames()) {
                        appendElement(sb, INDENT4, "servlet-name", servletName);
                    }
                }
                if (filterMap.getMatchAllUrlPatterns()) {
                    sb.append("    <url-pattern>*</url-pattern>\n");
                } else {
                    for (String urlPattern : filterMap.getURLPatterns()) {
                        appendElement(sb, INDENT4, "url-pattern", encodeUrl(urlPattern));
                    }
                }
                // dispatcher was added in Servlet 2.4
                if (getMajorVersion() > 2 || getMinorVersion() > 3) {
                    for (String dispatcher : filterMap.getDispatcherNames()) {
                        if (getMajorVersion() == 2 && DispatcherType.ASYNC.name().equals(dispatcher)) {
                            continue;
                        }
                        appendElement(sb, INDENT4, "dispatcher", dispatcher);
                    }
                }
                sb.append("  </filter-mapping>\n");
            }
            sb.append('\n');
        }

        // Listeners were introduced in Servlet 2.3
        if (getMajorVersion() > 2 || getMinorVersion() > 2) {
            for (String listener : listeners) {
                sb.append("  <listener>\n");
                appendElement(sb, INDENT4, "listener-class", listener);
                sb.append("  </listener>\n");
            }
            sb.append('\n');
        }

        for (Map.Entry<String,ServletDef> entry : servlets.entrySet()) {
            ServletDef servletDef = entry.getValue();
            sb.append("  <servlet>\n");
            appendElement(sb, INDENT4, "description", servletDef.getDescription());
            appendElement(sb, INDENT4, "display-name", servletDef.getDisplayName());
            appendElement(sb, INDENT4, "servlet-name", entry.getKey());
            appendElement(sb, INDENT4, "servlet-class", servletDef.getServletClass());
            appendElement(sb, INDENT4, "jsp-file", servletDef.getJspFile());
            for (Map.Entry<String,String> param : servletDef.getParameterMap().entrySet()) {
                sb.append("    <init-param>\n");
                appendElement(sb, INDENT6, "param-name", param.getKey());
                appendElement(sb, INDENT6, "param-value", param.getValue());
                sb.append("    </init-param>\n");
            }
            appendElement(sb, INDENT4, "load-on-startup", servletDef.getLoadOnStartup());
            appendElement(sb, INDENT4, "enabled", servletDef.getEnabled());
            // Async support was introduced for Servlet 3.0 onwards
            if (getMajorVersion() != 2) {
                appendElement(sb, INDENT4, "async-supported", servletDef.getAsyncSupported());
            }
            // servlet/run-as was introduced in Servlet 2.3
            if (getMajorVersion() > 2 || getMinorVersion() > 2) {
                if (servletDef.getRunAs() != null) {
                    sb.append("    <run-as>\n");
                    appendElement(sb, INDENT6, "role-name", servletDef.getRunAs());
                    sb.append("    </run-as>\n");
                }
            }
            for (SecurityRoleRef roleRef : servletDef.getSecurityRoleRefs()) {
                sb.append("    <security-role-ref>\n");
                appendElement(sb, INDENT6, "role-name", roleRef.getName());
                appendElement(sb, INDENT6, "role-link", roleRef.getLink());
                sb.append("    </security-role-ref>\n");
            }
            // multipart-config was added in Servlet 3.0
            if (getMajorVersion() != 2) {
                MultipartDef multipartDef = servletDef.getMultipartDef();
                if (multipartDef != null) {
                    sb.append("    <multipart-config>\n");
                    appendElement(sb, INDENT6, "location", multipartDef.getLocation());
                    appendElement(sb, INDENT6, "max-file-size", multipartDef.getMaxFileSize());
                    appendElement(sb, INDENT6, "max-request-size", multipartDef.getMaxRequestSize());
                    appendElement(sb, INDENT6, "file-size-threshold", multipartDef.getFileSizeThreshold());
                    sb.append("    </multipart-config>\n");
                }
            }
            sb.append("  </servlet>\n");
        }
        sb.append('\n');

        for (Map.Entry<String,String> entry : servletMappings.entrySet()) {
            sb.append("  <servlet-mapping>\n");
            appendElement(sb, INDENT4, "servlet-name", entry.getValue());
            appendElement(sb, INDENT4, "url-pattern", encodeUrl(entry.getKey()));
            sb.append("  </servlet-mapping>\n");
        }
        sb.append('\n');

        if (sessionConfig != null) {
            sb.append("  <session-config>\n");
            appendElement(sb, INDENT4, "session-timeout", sessionConfig.getSessionTimeout());
            if (majorVersion >= 3) {
                sb.append("    <cookie-config>\n");
                appendElement(sb, INDENT6, "name", sessionConfig.getCookieName());
                appendElement(sb, INDENT6, "domain", sessionConfig.getCookieDomain());
                appendElement(sb, INDENT6, "path", sessionConfig.getCookiePath());
                appendElement(sb, INDENT6, "comment", sessionConfig.getCookieComment());
                appendElement(sb, INDENT6, "http-only", sessionConfig.getCookieHttpOnly());
                appendElement(sb, INDENT6, "secure", sessionConfig.getCookieSecure());
                appendElement(sb, INDENT6, "max-age", sessionConfig.getCookieMaxAge());
                sb.append("    </cookie-config>\n");
                for (SessionTrackingMode stm : sessionConfig.getSessionTrackingModes()) {
                    appendElement(sb, INDENT4, "tracking-mode", stm.name());
                }
            }
            sb.append("  </session-config>\n\n");
        }

        for (Map.Entry<String,String> entry : mimeMappings.entrySet()) {
            sb.append("  <mime-mapping>\n");
            appendElement(sb, INDENT4, "extension", entry.getKey());
            appendElement(sb, INDENT4, "mime-type", entry.getValue());
            sb.append("  </mime-mapping>\n");
        }
        sb.append('\n');

        if (!welcomeFiles.isEmpty()) {
            sb.append("  <welcome-file-list>\n");
            for (String welcomeFile : welcomeFiles) {
                appendElement(sb, INDENT4, "welcome-file", welcomeFile);
            }
            sb.append("  </welcome-file-list>\n\n");
        }

        for (ErrorPage errorPage : errorPages.values()) {
            String exceptionType = errorPage.getExceptionType();
            int errorCode = errorPage.getErrorCode();

            if (exceptionType == null && errorCode == 0 && getMajorVersion() == 2) {
                // Default error pages are only supported from 3.0 onwards
                continue;
            }
            sb.append("  <error-page>\n");
            if (errorPage.getExceptionType() != null) {
                appendElement(sb, INDENT4, "exception-type", exceptionType);
            } else if (errorPage.getErrorCode() > 0) {
                appendElement(sb, INDENT4, "error-code", Integer.toString(errorCode));
            }
            appendElement(sb, INDENT4, "location", errorPage.getLocation());
            sb.append("  </error-page>\n");
        }
        sb.append('\n');

        // jsp-config was added in Servlet 2.4. Prior to that, tag-libs was used
        // directly and jsp-property-group did not exist
        if (!taglibs.isEmpty() || !jspPropertyGroups.isEmpty()) {
            if (getMajorVersion() > 2 || getMinorVersion() > 3) {
                sb.append("  <jsp-config>\n");
            }
            for (Map.Entry<String,String> entry : taglibs.entrySet()) {
                sb.append("    <taglib>\n");
                appendElement(sb, INDENT6, "taglib-uri", entry.getKey());
                appendElement(sb, INDENT6, "taglib-location", entry.getValue());
                sb.append("    </taglib>\n");
            }
            if (getMajorVersion() > 2 || getMinorVersion() > 3) {
                for (JspPropertyGroup jpg : jspPropertyGroups) {
                    sb.append("    <jsp-property-group>\n");
                    for (String urlPattern : jpg.getUrlPatterns()) {
                        appendElement(sb, INDENT6, "url-pattern", encodeUrl(urlPattern));
                    }
                    appendElement(sb, INDENT6, "el-ignored", jpg.getElIgnored());
                    appendElement(sb, INDENT6, "page-encoding", jpg.getPageEncoding());
                    appendElement(sb, INDENT6, "scripting-invalid", jpg.getScriptingInvalid());
                    appendElement(sb, INDENT6, "is-xml", jpg.getIsXml());
                    for (String prelude : jpg.getIncludePreludes()) {
                        appendElement(sb, INDENT6, "include-prelude", prelude);
                    }
                    for (String coda : jpg.getIncludeCodas()) {
                        appendElement(sb, INDENT6, "include-coda", coda);
                    }
                    appendElement(sb, INDENT6, "deferred-syntax-allowed-as-literal", jpg.getDeferredSyntax());
                    appendElement(sb, INDENT6, "trim-directive-whitespaces", jpg.getTrimWhitespace());
                    appendElement(sb, INDENT6, "default-content-type", jpg.getDefaultContentType());
                    appendElement(sb, INDENT6, "buffer", jpg.getBuffer());
                    appendElement(sb, INDENT6, "error-on-undeclared-namespace", jpg.getErrorOnUndeclaredNamespace());
                    sb.append("    </jsp-property-group>\n");
                }
                sb.append("  </jsp-config>\n\n");
            }
        }

        // resource-env-ref was introduced in Servlet 2.3
        if (getMajorVersion() > 2 || getMinorVersion() > 2) {
            for (ContextResourceEnvRef resourceEnvRef : resourceEnvRefs.values()) {
                sb.append("  <resource-env-ref>\n");
                appendElement(sb, INDENT4, "description", resourceEnvRef.getDescription());
                appendElement(sb, INDENT4, "resource-env-ref-name", resourceEnvRef.getName());
                appendElement(sb, INDENT4, "resource-env-ref-type", resourceEnvRef.getType());
                appendElement(sb, INDENT4, "mapped-name", resourceEnvRef.getProperty("mappedName"));
                for (InjectionTarget target : resourceEnvRef.getInjectionTargets()) {
                    sb.append("    <injection-target>\n");
                    appendElement(sb, INDENT6, "injection-target-class", target.getTargetClass());
                    appendElement(sb, INDENT6, "injection-target-name", target.getTargetName());
                    sb.append("    </injection-target>\n");
                }
                appendElement(sb, INDENT4, "lookup-name", resourceEnvRef.getLookupName());
                sb.append("  </resource-env-ref>\n");
            }
            sb.append('\n');
        }

        for (ContextResource resourceRef : resourceRefs.values()) {
            sb.append("  <resource-ref>\n");
            appendElement(sb, INDENT4, "description", resourceRef.getDescription());
            appendElement(sb, INDENT4, "res-ref-name", resourceRef.getName());
            appendElement(sb, INDENT4, "res-type", resourceRef.getType());
            appendElement(sb, INDENT4, "res-auth", resourceRef.getAuth());
            // resource-ref/res-sharing-scope was introduced in Servlet 2.3
            if (getMajorVersion() > 2 || getMinorVersion() > 2) {
                appendElement(sb, INDENT4, "res-sharing-scope", resourceRef.getScope());
            }
            appendElement(sb, INDENT4, "mapped-name", resourceRef.getProperty("mappedName"));
            for (InjectionTarget target : resourceRef.getInjectionTargets()) {
                sb.append("    <injection-target>\n");
                appendElement(sb, INDENT6, "injection-target-class", target.getTargetClass());
                appendElement(sb, INDENT6, "injection-target-name", target.getTargetName());
                sb.append("    </injection-target>\n");
            }
            appendElement(sb, INDENT4, "lookup-name", resourceRef.getLookupName());
            sb.append("  </resource-ref>\n");
        }
        sb.append('\n');

        for (SecurityConstraint constraint : securityConstraints) {
            sb.append("  <security-constraint>\n");
            // security-constraint/display-name was introduced in Servlet 2.3
            if (getMajorVersion() > 2 || getMinorVersion() > 2) {
                appendElement(sb, INDENT4, "display-name", constraint.getDisplayName());
            }
            for (SecurityCollection collection : constraint.findCollections()) {
                sb.append("    <web-resource-collection>\n");
                appendElement(sb, INDENT6, "web-resource-name", collection.getName());
                appendElement(sb, INDENT6, "description", collection.getDescription());
                for (String urlPattern : collection.findPatterns()) {
                    appendElement(sb, INDENT6, "url-pattern", encodeUrl(urlPattern));
                }
                for (String method : collection.findMethods()) {
                    appendElement(sb, INDENT6, "http-method", method);
                }
                for (String method : collection.findOmittedMethods()) {
                    appendElement(sb, INDENT6, "http-method-omission", method);
                }
                sb.append("    </web-resource-collection>\n");
            }
            if (constraint.findAuthRoles().length > 0) {
                sb.append("    <auth-constraint>\n");
                for (String role : constraint.findAuthRoles()) {
                    appendElement(sb, INDENT6, "role-name", role);
                }
                sb.append("    </auth-constraint>\n");
            }
            if (constraint.getUserConstraint() != null) {
                sb.append("    <user-data-constraint>\n");
                appendElement(sb, INDENT6, "transport-guarantee", constraint.getUserConstraint());
                sb.append("    </user-data-constraint>\n");
            }
            sb.append("  </security-constraint>\n");
        }
        sb.append('\n');

        if (loginConfig != null) {
            sb.append("  <login-config>\n");
            appendElement(sb, INDENT4, "auth-method", loginConfig.getAuthMethod());
            appendElement(sb, INDENT4, "realm-name", loginConfig.getRealmName());
            if (loginConfig.getErrorPage() != null || loginConfig.getLoginPage() != null) {
                sb.append("    <form-login-config>\n");
                appendElement(sb, INDENT6, "form-login-page", loginConfig.getLoginPage());
                appendElement(sb, INDENT6, "form-error-page", loginConfig.getErrorPage());
                sb.append("    </form-login-config>\n");
            }
            sb.append("  </login-config>\n\n");
        }

        for (String roleName : securityRoles) {
            sb.append("  <security-role>\n");
            appendElement(sb, INDENT4, "role-name", roleName);
            sb.append("  </security-role>\n");
        }

        for (ContextEnvironment envEntry : envEntries.values()) {
            sb.append("  <env-entry>\n");
            appendElement(sb, INDENT4, "description", envEntry.getDescription());
            appendElement(sb, INDENT4, "env-entry-name", envEntry.getName());
            appendElement(sb, INDENT4, "env-entry-type", envEntry.getType());
            appendElement(sb, INDENT4, "env-entry-value", envEntry.getValue());
            appendElement(sb, INDENT4, "mapped-name", envEntry.getProperty("mappedName"));
            for (InjectionTarget target : envEntry.getInjectionTargets()) {
                sb.append("    <injection-target>\n");
                appendElement(sb, INDENT6, "injection-target-class", target.getTargetClass());
                appendElement(sb, INDENT6, "injection-target-name", target.getTargetName());
                sb.append("    </injection-target>\n");
            }
            appendElement(sb, INDENT4, "lookup-name", envEntry.getLookupName());
            sb.append("  </env-entry>\n");
        }
        sb.append('\n');

        for (ContextEjb ejbRef : ejbRefs.values()) {
            sb.append("  <ejb-ref>\n");
            appendElement(sb, INDENT4, "description", ejbRef.getDescription());
            appendElement(sb, INDENT4, "ejb-ref-name", ejbRef.getName());
            appendElement(sb, INDENT4, "ejb-ref-type", ejbRef.getType());
            appendElement(sb, INDENT4, "home", ejbRef.getHome());
            appendElement(sb, INDENT4, "remote", ejbRef.getRemote());
            appendElement(sb, INDENT4, "ejb-link", ejbRef.getLink());
            appendElement(sb, INDENT4, "mapped-name", ejbRef.getProperty("mappedName"));
            for (InjectionTarget target : ejbRef.getInjectionTargets()) {
                sb.append("    <injection-target>\n");
                appendElement(sb, INDENT6, "injection-target-class", target.getTargetClass());
                appendElement(sb, INDENT6, "injection-target-name", target.getTargetName());
                sb.append("    </injection-target>\n");
            }
            appendElement(sb, INDENT4, "lookup-name", ejbRef.getLookupName());
            sb.append("  </ejb-ref>\n");
        }
        sb.append('\n');

        // ejb-local-ref was introduced in Servlet 2.3
        if (getMajorVersion() > 2 || getMinorVersion() > 2) {
            for (ContextLocalEjb ejbLocalRef : ejbLocalRefs.values()) {
                sb.append("  <ejb-local-ref>\n");
                appendElement(sb, INDENT4, "description", ejbLocalRef.getDescription());
                appendElement(sb, INDENT4, "ejb-ref-name", ejbLocalRef.getName());
                appendElement(sb, INDENT4, "ejb-ref-type", ejbLocalRef.getType());
                appendElement(sb, INDENT4, "local-home", ejbLocalRef.getHome());
                appendElement(sb, INDENT4, "local", ejbLocalRef.getLocal());
                appendElement(sb, INDENT4, "ejb-link", ejbLocalRef.getLink());
                appendElement(sb, INDENT4, "mapped-name", ejbLocalRef.getProperty("mappedName"));
                for (InjectionTarget target : ejbLocalRef.getInjectionTargets()) {
                    sb.append("    <injection-target>\n");
                    appendElement(sb, INDENT6, "injection-target-class", target.getTargetClass());
                    appendElement(sb, INDENT6, "injection-target-name", target.getTargetName());
                    sb.append("    </injection-target>\n");
                }
                appendElement(sb, INDENT4, "lookup-name", ejbLocalRef.getLookupName());
                sb.append("  </ejb-local-ref>\n");
            }
            sb.append('\n');
        }

        // service-ref was introduced in Servlet 2.4
        if (getMajorVersion() > 2 || getMinorVersion() > 3) {
            for (ContextService serviceRef : serviceRefs.values()) {
                sb.append("  <service-ref>\n");
                appendElement(sb, INDENT4, "description", serviceRef.getDescription());
                appendElement(sb, INDENT4, "display-name", serviceRef.getDisplayname());
                appendElement(sb, INDENT4, "service-ref-name", serviceRef.getName());
                appendElement(sb, INDENT4, "service-interface", serviceRef.getInterface());
                appendElement(sb, INDENT4, "service-ref-type", serviceRef.getType());
                appendElement(sb, INDENT4, "wsdl-file", serviceRef.getWsdlfile());
                appendElement(sb, INDENT4, "jaxrpc-mapping-file", serviceRef.getJaxrpcmappingfile());
                String qname = serviceRef.getServiceqnameNamespaceURI();
                if (qname != null) {
                    qname = qname + ":";
                }
                qname = qname + serviceRef.getServiceqnameLocalpart();
                appendElement(sb, INDENT4, "service-qname", qname);
                Iterator<String> endpointIter = serviceRef.getServiceendpoints();
                while (endpointIter.hasNext()) {
                    String endpoint = endpointIter.next();
                    sb.append("    <port-component-ref>\n");
                    appendElement(sb, INDENT6, "service-endpoint-interface", endpoint);
                    appendElement(sb, INDENT6, "port-component-link", serviceRef.getProperty(endpoint));
                    sb.append("    </port-component-ref>\n");
                }
                Iterator<String> handlerIter = serviceRef.getHandlers();
                while (handlerIter.hasNext()) {
                    String handler = handlerIter.next();
                    sb.append("    <handler>\n");
                    ContextHandler ch = serviceRef.getHandler(handler);
                    appendElement(sb, INDENT6, "handler-name", ch.getName());
                    appendElement(sb, INDENT6, "handler-class", ch.getHandlerclass());
                    sb.append("    </handler>\n");
                }
                // TODO handler-chains
                appendElement(sb, INDENT4, "mapped-name", serviceRef.getProperty("mappedName"));
                for (InjectionTarget target : serviceRef.getInjectionTargets()) {
                    sb.append("    <injection-target>\n");
                    appendElement(sb, INDENT6, "injection-target-class", target.getTargetClass());
                    appendElement(sb, INDENT6, "injection-target-name", target.getTargetName());
                    sb.append("    </injection-target>\n");
                }
                appendElement(sb, INDENT4, "lookup-name", serviceRef.getLookupName());
                sb.append("  </service-ref>\n");
            }
            sb.append('\n');
        }

        if (!postConstructMethods.isEmpty()) {
            for (Entry<String,String> entry : postConstructMethods.entrySet()) {
                sb.append("  <post-construct>\n");
                appendElement(sb, INDENT4, "lifecycle-callback-class", entry.getKey());
                appendElement(sb, INDENT4, "lifecycle-callback-method", entry.getValue());
                sb.append("  </post-construct>\n");
            }
            sb.append('\n');
        }

        if (!preDestroyMethods.isEmpty()) {
            for (Entry<String,String> entry : preDestroyMethods.entrySet()) {
                sb.append("  <pre-destroy>\n");
                appendElement(sb, INDENT4, "lifecycle-callback-class", entry.getKey());
                appendElement(sb, INDENT4, "lifecycle-callback-method", entry.getValue());
                sb.append("  </pre-destroy>\n");
            }
            sb.append('\n');
        }

        // message-destination-ref, message-destination were introduced in
        // Servlet 2.4
        if (getMajorVersion() > 2 || getMinorVersion() > 3) {
            for (MessageDestinationRef mdr : messageDestinationRefs.values()) {
                sb.append("  <message-destination-ref>\n");
                appendElement(sb, INDENT4, "description", mdr.getDescription());
                appendElement(sb, INDENT4, "message-destination-ref-name", mdr.getName());
                appendElement(sb, INDENT4, "message-destination-type", mdr.getType());
                appendElement(sb, INDENT4, "message-destination-usage", mdr.getUsage());
                appendElement(sb, INDENT4, "message-destination-link", mdr.getLink());
                appendElement(sb, INDENT4, "mapped-name", mdr.getProperty("mappedName"));
                for (InjectionTarget target : mdr.getInjectionTargets()) {
                    sb.append("    <injection-target>\n");
                    appendElement(sb, INDENT6, "injection-target-class", target.getTargetClass());
                    appendElement(sb, INDENT6, "injection-target-name", target.getTargetName());
                    sb.append("    </injection-target>\n");
                }
                appendElement(sb, INDENT4, "lookup-name", mdr.getLookupName());
                sb.append("  </message-destination-ref>\n");
            }
            sb.append('\n');

            for (MessageDestination md : messageDestinations.values()) {
                sb.append("  <message-destination>\n");
                appendElement(sb, INDENT4, "description", md.getDescription());
                appendElement(sb, INDENT4, "display-name", md.getDisplayName());
                appendElement(sb, INDENT4, "message-destination-name", md.getName());
                appendElement(sb, INDENT4, "mapped-name", md.getProperty("mappedName"));
                appendElement(sb, INDENT4, "lookup-name", md.getLookupName());
                sb.append("  </message-destination>\n");
            }
            sb.append('\n');
        }

        // locale-encoding-mapping-list was introduced in Servlet 2.4
        if (getMajorVersion() > 2 || getMinorVersion() > 3) {
            if (!localeEncodingMappings.isEmpty()) {
                sb.append("  <locale-encoding-mapping-list>\n");
                for (Map.Entry<String,String> entry : localeEncodingMappings.entrySet()) {
                    sb.append("    <locale-encoding-mapping>\n");
                    appendElement(sb, INDENT6, "locale", entry.getKey());
                    appendElement(sb, INDENT6, "encoding", entry.getValue());
                    sb.append("    </locale-encoding-mapping>\n");
                }
                sb.append("  </locale-encoding-mapping-list>\n");
                sb.append("\n");
            }
        }

        // deny-uncovered-http-methods was introduced in Servlet 3.1
        if (getMajorVersion() > 3 || (getMajorVersion() == 3 && getMinorVersion() > 0)) {
            if (denyUncoveredHttpMethods) {
                sb.append("  <deny-uncovered-http-methods/>");
                sb.append("\n");
            }
        }

        // request-encoding and response-encoding was introduced in Servlet 4.0
        if (getMajorVersion() >= 4) {
            appendElement(sb, INDENT2, "request-character-encoding", requestCharacterEncoding);
            appendElement(sb, INDENT2, "response-character-encoding", responseCharacterEncoding);
        }
        sb.append("</web-app>");
        return sb.toString();
    }


    private String encodeUrl(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }


    private static void appendElement(StringBuilder sb, String indent, String elementName, String value) {
        if (value == null) {
            return;
        }
        if (value.isEmpty()) {
            sb.append(indent);
            sb.append('<');
            sb.append(elementName);
            sb.append("/>\n");
        } else {
            sb.append(indent);
            sb.append('<');
            sb.append(elementName);
            sb.append('>');
            sb.append(Escape.xml(value));
            sb.append("</");
            sb.append(elementName);
            sb.append(">\n");
        }
    }

    private static void appendElement(StringBuilder sb, String indent, String elementName, Object value) {
        if (value == null) {
            return;
        }
        appendElement(sb, indent, elementName, value.toString());
    }


    /**
     * Merge the supplied web fragments into this main web.xml.
     *
     * @param fragments The fragments to merge in
     *
     * @return <code>true</code> if merge is successful, else <code>false</code>
     */
    public boolean merge(Set<WebXml> fragments) {
        // As far as possible, process in alphabetical order so it is easy to
        // check everything is present

        // Merge rules vary from element to element. See SRV.8.2.3

        WebXml temp = new WebXml();

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getContextParams(), contextParams, temp.getContextParams(), fragment,
                    "Context Parameter")) {
                return false;
            }
        }
        contextParams.putAll(temp.getContextParams());

        if (displayName == null) {
            for (WebXml fragment : fragments) {
                String value = fragment.getDisplayName();
                if (value != null) {
                    if (temp.getDisplayName() == null) {
                        temp.setDisplayName(value);
                    } else {
                        log.error(
                                sm.getString("webXml.mergeConflictDisplayName", fragment.getName(), fragment.getURL()));
                        return false;
                    }
                }
            }
            displayName = temp.getDisplayName();
        }

        // Note: Not permitted in fragments, but we also use fragments for
        // per-Host and global defaults so they may appear there
        if (!denyUncoveredHttpMethods) {
            for (WebXml fragment : fragments) {
                if (fragment.getDenyUncoveredHttpMethods()) {
                    denyUncoveredHttpMethods = true;
                    break;
                }
            }
        }
        if (requestCharacterEncoding == null) {
            for (WebXml fragment : fragments) {
                if (fragment.getRequestCharacterEncoding() != null) {
                    requestCharacterEncoding = fragment.getRequestCharacterEncoding();
                }
            }
        }
        if (responseCharacterEncoding == null) {
            for (WebXml fragment : fragments) {
                if (fragment.getResponseCharacterEncoding() != null) {
                    responseCharacterEncoding = fragment.getResponseCharacterEncoding();
                }
            }
        }

        if (distributable) {
            for (WebXml fragment : fragments) {
                if (!fragment.isDistributable()) {
                    distributable = false;
                    break;
                }
            }
        }

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getEjbLocalRefs(), ejbLocalRefs, temp.getEjbLocalRefs(), fragment)) {
                return false;
            }
        }
        ejbLocalRefs.putAll(temp.getEjbLocalRefs());

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getEjbRefs(), ejbRefs, temp.getEjbRefs(), fragment)) {
                return false;
            }
        }
        ejbRefs.putAll(temp.getEjbRefs());

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getEnvEntries(), envEntries, temp.getEnvEntries(), fragment)) {
                return false;
            }
        }
        envEntries.putAll(temp.getEnvEntries());

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getErrorPages(), errorPages, temp.getErrorPages(), fragment, "Error Page")) {
                return false;
            }
        }
        errorPages.putAll(temp.getErrorPages());

        // As per 'clarification' from the Servlet EG, filter definitions in the
        // main web.xml override those in fragments and those in fragments
        // override those in annotations
        List<FilterMap> filterMapsToAdd = new ArrayList<>();
        for (WebXml fragment : fragments) {
            for (FilterMap filterMap : fragment.getFilterMappings()) {
                if (!filterMappingNames.contains(filterMap.getFilterName())) {
                    filterMapsToAdd.add(filterMap);
                }
            }
        }
        for (FilterMap filterMap : filterMapsToAdd) {
            // Additive
            addFilterMapping(filterMap);
        }

        for (WebXml fragment : fragments) {
            for (Map.Entry<String,FilterDef> entry : fragment.getFilters().entrySet()) {
                if (filters.containsKey(entry.getKey())) {
                    mergeFilter(entry.getValue(), filters.get(entry.getKey()), false);
                } else {
                    if (temp.getFilters().containsKey(entry.getKey())) {
                        if (!(mergeFilter(entry.getValue(), temp.getFilters().get(entry.getKey()), true))) {
                            log.error(sm.getString("webXml.mergeConflictFilter", entry.getKey(), fragment.getName(),
                                    fragment.getURL()));

                            return false;
                        }
                    } else {
                        temp.getFilters().put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        filters.putAll(temp.getFilters());

        for (WebXml fragment : fragments) {
            for (JspPropertyGroup jspPropertyGroup : fragment.getJspPropertyGroups()) {
                // Always additive
                addJspPropertyGroup(jspPropertyGroup);
            }
        }

        for (WebXml fragment : fragments) {
            for (String listener : fragment.getListeners()) {
                // Always additive
                addListener(listener);
            }
        }

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getLocaleEncodingMappings(), localeEncodingMappings,
                    temp.getLocaleEncodingMappings(), fragment, "Locale Encoding Mapping")) {
                return false;
            }
        }
        localeEncodingMappings.putAll(temp.getLocaleEncodingMappings());

        if (getLoginConfig() == null) {
            LoginConfig tempLoginConfig = null;
            for (WebXml fragment : fragments) {
                LoginConfig fragmentLoginConfig = fragment.loginConfig;
                if (fragmentLoginConfig != null) {
                    if (tempLoginConfig == null || fragmentLoginConfig.equals(tempLoginConfig)) {
                        tempLoginConfig = fragmentLoginConfig;
                    } else {
                        log.error(
                                sm.getString("webXml.mergeConflictLoginConfig", fragment.getName(), fragment.getURL()));
                    }
                }
            }
            loginConfig = tempLoginConfig;
        }

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getMessageDestinationRefs(), messageDestinationRefs,
                    temp.getMessageDestinationRefs(), fragment)) {
                return false;
            }
        }
        messageDestinationRefs.putAll(temp.getMessageDestinationRefs());

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getMessageDestinations(), messageDestinations, temp.getMessageDestinations(),
                    fragment)) {
                return false;
            }
        }
        messageDestinations.putAll(temp.getMessageDestinations());

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getMimeMappings(), mimeMappings, temp.getMimeMappings(), fragment, "Mime Mapping")) {
                return false;
            }
        }
        mimeMappings.putAll(temp.getMimeMappings());

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getResourceEnvRefs(), resourceEnvRefs, temp.getResourceEnvRefs(),
                    fragment)) {
                return false;
            }
        }
        resourceEnvRefs.putAll(temp.getResourceEnvRefs());

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getResourceRefs(), resourceRefs, temp.getResourceRefs(), fragment)) {
                return false;
            }
        }
        resourceRefs.putAll(temp.getResourceRefs());

        for (WebXml fragment : fragments) {
            for (SecurityConstraint constraint : fragment.getSecurityConstraints()) {
                // Always additive
                addSecurityConstraint(constraint);
            }
        }

        for (WebXml fragment : fragments) {
            for (String role : fragment.getSecurityRoles()) {
                // Always additive
                addSecurityRole(role);
            }
        }

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getServiceRefs(), serviceRefs, temp.getServiceRefs(), fragment)) {
                return false;
            }
        }
        serviceRefs.putAll(temp.getServiceRefs());

        // As per 'clarification' from the Servlet EG, servlet definitions and
        // mappings in the main web.xml override those in fragments and those in
        // fragments override those in annotations
        // Skip servlet definitions and mappings from fragments that are
        // defined in web.xml
        List<Map.Entry<String,String>> servletMappingsToAdd = new ArrayList<>();
        for (WebXml fragment : fragments) {
            for (Map.Entry<String,String> servletMap : fragment.getServletMappings().entrySet()) {
                if (!servletMappingNames.contains(servletMap.getValue()) &&
                        !servletMappings.containsKey(servletMap.getKey())) {
                    servletMappingsToAdd.add(servletMap);
                }
            }
        }

        // Add fragment mappings
        for (Map.Entry<String,String> mapping : servletMappingsToAdd) {
            addServletMappingDecoded(mapping.getKey(), mapping.getValue());
        }

        for (WebXml fragment : fragments) {
            for (Map.Entry<String,ServletDef> entry : fragment.getServlets().entrySet()) {
                if (servlets.containsKey(entry.getKey())) {
                    mergeServlet(entry.getValue(), servlets.get(entry.getKey()), false);
                } else {
                    if (temp.getServlets().containsKey(entry.getKey())) {
                        if (!(mergeServlet(entry.getValue(), temp.getServlets().get(entry.getKey()), true))) {
                            log.error(sm.getString("webXml.mergeConflictServlet", entry.getKey(), fragment.getName(),
                                    fragment.getURL()));

                            return false;
                        }
                    } else {
                        temp.getServlets().put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        servlets.putAll(temp.getServlets());

        if (sessionConfig.getSessionTimeout() == null) {
            for (WebXml fragment : fragments) {
                Integer value = fragment.getSessionConfig().getSessionTimeout();
                if (value != null) {
                    if (temp.getSessionConfig().getSessionTimeout() == null) {
                        temp.getSessionConfig().setSessionTimeout(value.toString());
                    } else if (value.equals(temp.getSessionConfig().getSessionTimeout())) {
                        // Fragments use same value - no conflict
                    } else {
                        log.error(sm.getString("webXml.mergeConflictSessionTimeout", fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            if (temp.getSessionConfig().getSessionTimeout() != null) {
                sessionConfig.setSessionTimeout(temp.getSessionConfig().getSessionTimeout().toString());
            }
        }

        if (sessionConfig.getCookieName() == null) {
            for (WebXml fragment : fragments) {
                String value = fragment.getSessionConfig().getCookieName();
                if (value != null) {
                    if (temp.getSessionConfig().getCookieName() == null) {
                        temp.getSessionConfig().setCookieName(value);
                    } else if (value.equals(temp.getSessionConfig().getCookieName())) {
                        // Fragments use same value - no conflict
                    } else {
                        log.error(sm.getString("webXml.mergeConflictSessionCookieName", fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            sessionConfig.setCookieName(temp.getSessionConfig().getCookieName());
        }

        Map<String,String> mainAttributes = getSessionConfig().getCookieAttributes();
        Map<String,String> mergedFragmentAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (WebXml fragment : fragments) {
            for (Map.Entry<String,String> attribute : fragment.getSessionConfig().getCookieAttributes().entrySet()) {
                // Skip any attribute in a fragment that is defined in the main web.xml
                if (!mainAttributes.containsKey(attribute.getKey())) {
                    if (mergedFragmentAttributes.containsKey(attribute.getKey())) {
                        // Attribute has already been seen.
                        // If values are the same, NO-OP. If they are different
                        // trigger a merge error
                        if (!mergedFragmentAttributes.get(attribute.getKey()).equals(attribute.getValue())) {
                            log.error(sm.getString("webXml.mergeConflictSessionCookieAttributes", fragment.getName(),
                                    fragment.getURL()));
                            return false;
                        }
                    } else {
                        // First time this attribute has been seen. Add it.
                        mergedFragmentAttributes.put(attribute.getKey(), attribute.getValue());
                    }
                }
            }
        }
        mainAttributes.putAll(mergedFragmentAttributes);

        if (sessionConfig.getSessionTrackingModes().isEmpty()) {
            for (WebXml fragment : fragments) {
                EnumSet<SessionTrackingMode> value = fragment.getSessionConfig().getSessionTrackingModes();
                if (!value.isEmpty()) {
                    if (temp.getSessionConfig().getSessionTrackingModes().isEmpty()) {
                        temp.getSessionConfig().getSessionTrackingModes().addAll(value);
                    } else if (value.equals(temp.getSessionConfig().getSessionTrackingModes())) {
                        // Fragments use same value - no conflict
                    } else {
                        log.error(sm.getString("webXml.mergeConflictSessionTrackingMode", fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            sessionConfig.getSessionTrackingModes().addAll(temp.getSessionConfig().getSessionTrackingModes());
        }

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getTaglibs(), taglibs, temp.getTaglibs(), fragment, "Taglibs")) {
                return false;
            }
        }
        taglibs.putAll(temp.getTaglibs());

        for (WebXml fragment : fragments) {
            if (fragment.alwaysAddWelcomeFiles || welcomeFiles.isEmpty()) {
                for (String welcomeFile : fragment.getWelcomeFiles()) {
                    addWelcomeFile(welcomeFile);
                }
            }
        }

        if (postConstructMethods.isEmpty()) {
            for (WebXml fragment : fragments) {
                if (!mergeLifecycleCallback(fragment.getPostConstructMethods(), temp.getPostConstructMethods(),
                        fragment, "Post Construct Methods")) {
                    return false;
                }
            }
            postConstructMethods.putAll(temp.getPostConstructMethods());
        }

        if (preDestroyMethods.isEmpty()) {
            for (WebXml fragment : fragments) {
                if (!mergeLifecycleCallback(fragment.getPreDestroyMethods(), temp.getPreDestroyMethods(), fragment,
                        "Pre Destroy Methods")) {
                    return false;
                }
            }
            preDestroyMethods.putAll(temp.getPreDestroyMethods());
        }

        return true;
    }

    private <T extends ResourceBase> boolean mergeResourceMap(Map<String,T> fragmentResources,
            Map<String,T> mainResources, Map<String,T> tempResources, WebXml fragment) {
        for (T resource : fragmentResources.values()) {
            String resourceName = resource.getName();
            if (mainResources.containsKey(resourceName)) {
                mainResources.get(resourceName).getInjectionTargets().addAll(resource.getInjectionTargets());
            } else {
                // Not defined in main web.xml
                T existingResource = tempResources.get(resourceName);
                if (existingResource != null) {
                    if (!existingResource.equals(resource)) {
                        log.error(sm.getString("webXml.mergeConflictResource", resourceName, fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                } else {
                    tempResources.put(resourceName, resource);
                }
            }
        }
        return true;
    }

    private <T> boolean mergeMap(Map<String,T> fragmentMap, Map<String,T> mainMap, Map<String,T> tempMap,
            WebXml fragment, String mapName) {
        for (Entry<String,T> entry : fragmentMap.entrySet()) {
            final String key = entry.getKey();
            if (!mainMap.containsKey(key)) {
                // Not defined in main web.xml
                T value = entry.getValue();
                if (tempMap.containsKey(key)) {
                    if (value != null && !value.equals(tempMap.get(key))) {
                        log.error(sm.getString("webXml.mergeConflictString", mapName, key, fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                } else {
                    tempMap.put(key, value);
                }
            }
        }
        return true;
    }

    private static boolean mergeFilter(FilterDef src, FilterDef dest, boolean failOnConflict) {
        if (dest.getAsyncSupported() == null) {
            dest.setAsyncSupported(src.getAsyncSupported());
        } else if (src.getAsyncSupported() != null) {
            if (failOnConflict && !src.getAsyncSupported().equals(dest.getAsyncSupported())) {
                return false;
            }
        }

        if (dest.getFilterClass() == null) {
            dest.setFilterClass(src.getFilterClass());
        } else if (src.getFilterClass() != null) {
            if (failOnConflict && !src.getFilterClass().equals(dest.getFilterClass())) {
                return false;
            }
        }

        for (Map.Entry<String,String> srcEntry : src.getParameterMap().entrySet()) {
            if (dest.getParameterMap().containsKey(srcEntry.getKey())) {
                if (failOnConflict && !dest.getParameterMap().get(srcEntry.getKey()).equals(srcEntry.getValue())) {
                    return false;
                }
            } else {
                dest.addInitParameter(srcEntry.getKey(), srcEntry.getValue());
            }
        }
        return true;
    }

    private static boolean mergeServlet(ServletDef src, ServletDef dest, boolean failOnConflict) {
        // These tests should be unnecessary...
        if (dest.getServletClass() != null && dest.getJspFile() != null) {
            return false;
        }
        if (src.getServletClass() != null && src.getJspFile() != null) {
            return false;
        }


        if (dest.getServletClass() == null && dest.getJspFile() == null) {
            dest.setServletClass(src.getServletClass());
            dest.setJspFile(src.getJspFile());
        } else if (failOnConflict) {
            if (src.getServletClass() != null &&
                    (dest.getJspFile() != null || !src.getServletClass().equals(dest.getServletClass()))) {
                return false;
            }
            if (src.getJspFile() != null &&
                    (dest.getServletClass() != null || !src.getJspFile().equals(dest.getJspFile()))) {
                return false;
            }
        }

        // Additive
        for (SecurityRoleRef securityRoleRef : src.getSecurityRoleRefs()) {
            dest.addSecurityRoleRef(securityRoleRef);
        }

        if (dest.getLoadOnStartup() == null) {
            if (src.getLoadOnStartup() != null) {
                dest.setLoadOnStartup(src.getLoadOnStartup().toString());
            }
        } else if (src.getLoadOnStartup() != null) {
            if (failOnConflict && !src.getLoadOnStartup().equals(dest.getLoadOnStartup())) {
                return false;
            }
        }

        if (dest.getEnabled() == null) {
            if (src.getEnabled() != null) {
                dest.setEnabled(src.getEnabled().toString());
            }
        } else if (src.getEnabled() != null) {
            if (failOnConflict && !src.getEnabled().equals(dest.getEnabled())) {
                return false;
            }
        }

        for (Map.Entry<String,String> srcEntry : src.getParameterMap().entrySet()) {
            if (dest.getParameterMap().containsKey(srcEntry.getKey())) {
                if (failOnConflict && !dest.getParameterMap().get(srcEntry.getKey()).equals(srcEntry.getValue())) {
                    return false;
                }
            } else {
                dest.addInitParameter(srcEntry.getKey(), srcEntry.getValue());
            }
        }

        if (dest.getMultipartDef() == null) {
            dest.setMultipartDef(src.getMultipartDef());
        } else if (src.getMultipartDef() != null) {
            return mergeMultipartDef(src.getMultipartDef(), dest.getMultipartDef(), failOnConflict);
        }

        if (dest.getAsyncSupported() == null) {
            if (src.getAsyncSupported() != null) {
                dest.setAsyncSupported(src.getAsyncSupported().toString());
            }
        } else if (src.getAsyncSupported() != null) {
            return !failOnConflict || src.getAsyncSupported().equals(dest.getAsyncSupported());
        }

        return true;
    }

    private static boolean mergeMultipartDef(MultipartDef src, MultipartDef dest, boolean failOnConflict) {

        if (dest.getLocation() == null) {
            dest.setLocation(src.getLocation());
        } else if (src.getLocation() != null) {
            if (failOnConflict && !src.getLocation().equals(dest.getLocation())) {
                return false;
            }
        }

        if (dest.getFileSizeThreshold() == null) {
            dest.setFileSizeThreshold(src.getFileSizeThreshold());
        } else if (src.getFileSizeThreshold() != null) {
            if (failOnConflict && !src.getFileSizeThreshold().equals(dest.getFileSizeThreshold())) {
                return false;
            }
        }

        if (dest.getMaxFileSize() == null) {
            dest.setMaxFileSize(src.getMaxFileSize());
        } else if (src.getMaxFileSize() != null) {
            if (failOnConflict && !src.getMaxFileSize().equals(dest.getMaxFileSize())) {
                return false;
            }
        }

        if (dest.getMaxRequestSize() == null) {
            dest.setMaxRequestSize(src.getMaxRequestSize());
        } else if (src.getMaxRequestSize() != null) {
            return !failOnConflict || src.getMaxRequestSize().equals(dest.getMaxRequestSize());
        }

        return true;
    }


    private boolean mergeLifecycleCallback(Map<String,String> fragmentMap, Map<String,String> tempMap, WebXml fragment,
            String mapName) {
        for (Entry<String,String> entry : fragmentMap.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            if (tempMap.containsKey(key)) {
                if (value != null && !value.equals(tempMap.get(key))) {
                    log.error(sm.getString("webXml.mergeConflictString", mapName, key, fragment.getName(),
                            fragment.getURL()));
                    return false;
                }
            } else {
                tempMap.put(key, value);
            }
        }
        return true;
    }


    /**
     * Generates the sub-set of the web-fragment.xml files to be processed in the order that the fragments must be
     * processed as per the rules in the Servlet spec.
     *
     * @param application    The application web.xml file
     * @param fragments      The map of fragment names to web fragments
     * @param servletContext The servlet context the fragments are associated with
     *
     * @return Ordered list of web-fragment.xml files to process
     */
    public static Set<WebXml> orderWebFragments(WebXml application, Map<String,WebXml> fragments,
            ServletContext servletContext) {
        return application.orderWebFragments(fragments, servletContext);
    }


    private Set<WebXml> orderWebFragments(Map<String,WebXml> fragments, ServletContext servletContext) {

        Set<WebXml> orderedFragments = new LinkedHashSet<>();

        boolean absoluteOrdering = getAbsoluteOrdering() != null;
        boolean orderingPresent = false;

        if (absoluteOrdering) {
            orderingPresent = true;
            // Only those fragments listed should be processed
            Set<String> requestedOrder = getAbsoluteOrdering();

            for (String requestedName : requestedOrder) {
                if (ORDER_OTHERS.equals(requestedName)) {
                    // Add all fragments not named explicitly at this point
                    for (Entry<String,WebXml> entry : fragments.entrySet()) {
                        if (!requestedOrder.contains(entry.getKey())) {
                            WebXml fragment = entry.getValue();
                            if (fragment != null) {
                                orderedFragments.add(fragment);
                            }
                        }
                    }
                } else {
                    WebXml fragment = fragments.get(requestedName);
                    if (fragment != null) {
                        orderedFragments.add(fragment);
                    } else {
                        log.warn(sm.getString("webXml.wrongFragmentName", requestedName));
                    }
                }
            }
        } else {
            // Stage 0. Check there were no fragments with duplicate names
            for (WebXml fragment : fragments.values()) {
                if (fragment.isDuplicated()) {
                    List<String> duplicates = fragment.getDuplicates();
                    duplicates.add(0, fragment.getURL().toString());
                    throw new IllegalArgumentException(
                            sm.getString("webXml.duplicateFragment", fragment.getName(), duplicates));
                }
            }
            // Stage 1. Make all dependencies bidirectional - this makes the
            // next stage simpler.
            for (WebXml fragment : fragments.values()) {
                Iterator<String> before = fragment.getBeforeOrdering().iterator();
                while (before.hasNext()) {
                    orderingPresent = true;
                    String beforeEntry = before.next();
                    if (!beforeEntry.equals(ORDER_OTHERS)) {
                        WebXml beforeFragment = fragments.get(beforeEntry);
                        if (beforeFragment == null) {
                            before.remove();
                        } else {
                            beforeFragment.addAfterOrdering(fragment.getName());
                        }
                    }
                }
                Iterator<String> after = fragment.getAfterOrdering().iterator();
                while (after.hasNext()) {
                    orderingPresent = true;
                    String afterEntry = after.next();
                    if (!afterEntry.equals(ORDER_OTHERS)) {
                        WebXml afterFragment = fragments.get(afterEntry);
                        if (afterFragment == null) {
                            after.remove();
                        } else {
                            afterFragment.addBeforeOrdering(fragment.getName());
                        }
                    }
                }
            }

            // Stage 2. Make all fragments that are implicitly before/after
            // others explicitly so. This is iterative so the next
            // stage doesn't have to be.
            for (WebXml fragment : fragments.values()) {
                if (fragment.getBeforeOrdering().contains(ORDER_OTHERS)) {
                    makeBeforeOthersExplicit(fragment.getAfterOrdering(), fragments);
                }
                if (fragment.getAfterOrdering().contains(ORDER_OTHERS)) {
                    makeAfterOthersExplicit(fragment.getBeforeOrdering(), fragments);
                }
            }

            // Stage 3. Separate into three groups
            Set<WebXml> beforeSet = new HashSet<>();
            Set<WebXml> othersSet = new HashSet<>();
            Set<WebXml> afterSet = new HashSet<>();

            for (WebXml fragment : fragments.values()) {
                if (fragment.getBeforeOrdering().contains(ORDER_OTHERS)) {
                    beforeSet.add(fragment);
                    fragment.getBeforeOrdering().remove(ORDER_OTHERS);
                } else if (fragment.getAfterOrdering().contains(ORDER_OTHERS)) {
                    afterSet.add(fragment);
                    fragment.getAfterOrdering().remove(ORDER_OTHERS);
                } else {
                    othersSet.add(fragment);
                }
            }

            // Stage 4. Decouple the groups so the ordering requirements for
            // each fragment in the group only refer to other fragments
            // in the group. Ordering requirements outside the group
            // will be handled by processing the groups in order.
            // Note: Only after ordering requirements are considered.
            // This is OK because of the processing in stage 1.
            decoupleOtherGroups(beforeSet);
            decoupleOtherGroups(othersSet);
            decoupleOtherGroups(afterSet);

            // Stage 5. Order each group
            // Note: Only after ordering requirements are considered.
            // This is OK because of the processing in stage 1.
            orderFragments(orderedFragments, beforeSet);
            orderFragments(orderedFragments, othersSet);
            orderFragments(orderedFragments, afterSet);
        }

        // Container fragments are always included
        Set<WebXml> containerFragments = new LinkedHashSet<>();
        // Find all the container fragments and remove any present from the
        // ordered list
        for (WebXml fragment : fragments.values()) {
            if (!fragment.getWebappJar()) {
                containerFragments.add(fragment);
                orderedFragments.remove(fragment);
            }
        }

        // Avoid NPE when unit testing
        if (servletContext != null) {
            // Publish the ordered fragments. The app does not need to know
            // about container fragments
            List<String> orderedJarFileNames = null;
            if (orderingPresent) {
                orderedJarFileNames = new ArrayList<>();
                for (WebXml fragment : orderedFragments) {
                    orderedJarFileNames.add(fragment.getJarName());
                }
            }
            servletContext.setAttribute(ServletContext.ORDERED_LIBS, orderedJarFileNames);
        }

        // The remainder of the processing needs to know about container
        // fragments
        if (!containerFragments.isEmpty()) {
            Set<WebXml> result = new LinkedHashSet<>();
            if (containerFragments.iterator().next().getDelegate()) {
                result.addAll(containerFragments);
                result.addAll(orderedFragments);
            } else {
                result.addAll(orderedFragments);
                result.addAll(containerFragments);
            }
            return result;
        } else {
            return orderedFragments;
        }
    }

    private static void decoupleOtherGroups(Set<WebXml> group) {
        Set<String> names = new HashSet<>();
        for (WebXml fragment : group) {
            names.add(fragment.getName());
        }
        for (WebXml fragment : group) {
            fragment.getAfterOrdering().removeIf(entry -> !names.contains(entry));
        }
    }

    private static void orderFragments(Set<WebXml> orderedFragments, Set<WebXml> unordered) {
        Set<WebXml> addedThisRound = new HashSet<>();
        Set<WebXml> addedLastRound = new HashSet<>();
        while (!unordered.isEmpty()) {
            Iterator<WebXml> source = unordered.iterator();
            while (source.hasNext()) {
                WebXml fragment = source.next();
                for (WebXml toRemove : addedLastRound) {
                    fragment.getAfterOrdering().remove(toRemove.getName());
                }
                if (fragment.getAfterOrdering().isEmpty()) {
                    addedThisRound.add(fragment);
                    orderedFragments.add(fragment);
                    source.remove();
                }
            }
            if (addedThisRound.isEmpty()) {
                // Circular
                throw new IllegalArgumentException(sm.getString("webXml.mergeConflictOrder"));
            }
            addedLastRound.clear();
            addedLastRound.addAll(addedThisRound);
            addedThisRound.clear();
        }
    }

    private static void makeBeforeOthersExplicit(Set<String> beforeOrdering, Map<String,WebXml> fragments) {
        for (String before : beforeOrdering) {
            if (!before.equals(ORDER_OTHERS)) {
                WebXml webXml = fragments.get(before);
                if (!webXml.getBeforeOrdering().contains(ORDER_OTHERS)) {
                    webXml.addBeforeOrderingOthers();
                    makeBeforeOthersExplicit(webXml.getAfterOrdering(), fragments);
                }
            }
        }
    }

    private static void makeAfterOthersExplicit(Set<String> afterOrdering, Map<String,WebXml> fragments) {
        for (String after : afterOrdering) {
            if (!after.equals(ORDER_OTHERS)) {
                WebXml webXml = fragments.get(after);
                if (!webXml.getAfterOrdering().contains(ORDER_OTHERS)) {
                    webXml.addAfterOrderingOthers();
                    makeAfterOthersExplicit(webXml.getBeforeOrdering(), fragments);
                }
            }
        }
    }
}
