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


package org.apache.catalina.startup;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.deploy.ContextEjb;
import org.apache.catalina.deploy.ContextEnvironment;
import org.apache.catalina.deploy.ContextLocalEjb;
import org.apache.catalina.deploy.ContextResource;
import org.apache.catalina.deploy.ContextResourceEnvRef;
import org.apache.catalina.deploy.ContextService;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.JspPropertyGroup;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.MessageDestination;
import org.apache.catalina.deploy.MessageDestinationRef;
import org.apache.catalina.deploy.MultipartDef;
import org.apache.catalina.deploy.ResourceBase;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.deploy.SecurityRoleRef;
import org.apache.catalina.deploy.ServletDef;
import org.apache.tomcat.util.res.StringManager;

/**
 * Representation of common elements of web.xml and web-fragment.xml. Provides
 * a repository for parsed data before the elements are merged.
 * Validation is spread between multiple classes:
 * The digester checks for structural correctness (eg single login-config)
 * This class checks for invalid duplicates (eg filter/servlet names)
 * StandardContext will check validity of values (eg URL formats etc)
 */
public class WebXml {
    
    protected static final String ORDER_OTHERS =
        "org.apache.catalina.order.others";
    
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    private static final org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog(WebXml.class);
    
    // web.xml only elements
    // Absolute Ordering
    private Set<String> absoluteOrdering = null;
    public void addAbsoluteOrdering(String fragmentName) {
        if (absoluteOrdering == null) {
            absoluteOrdering = new LinkedHashSet<String>();
        }
        absoluteOrdering.add(fragmentName);
    }
    public void addAbsoluteOrderingOthers() {
        if (absoluteOrdering == null) {
            absoluteOrdering = new LinkedHashSet<String>();
        }
        absoluteOrdering.add(ORDER_OTHERS);
    }
    public Set<String> getAbsoluteOrdering() {
        return absoluteOrdering;
    }

    // web-fragment.xml only elements
    // Relative ordering
    private Set<String> after = new LinkedHashSet<String>();
    public void addAfterOrder(String fragmentName) {
        after.add(fragmentName);
    }
    public void addAfterOrderOthers() {
        if (before.contains(ORDER_OTHERS)) {
            throw new IllegalArgumentException(sm.getString(
                    "webXml.multipleOther"));
        }
        after.add(ORDER_OTHERS);
    }
    public Set<String> getAfterOrder() { return after; }
    
    private Set<String> before = new LinkedHashSet<String>();
    public void addBeforeOrder(String fragmentName) {
        before.add(fragmentName);
    }
    public void addBeforeOrderOthers() {
        if (after.contains(ORDER_OTHERS)) {
            throw new IllegalArgumentException(sm.getString(
                    "webXml.multipleOther"));
        }
        before.add(ORDER_OTHERS);
    }
    public Set<String> getBeforeOrder() { return before; }

    // Common elements and attributes
    
    // Required attribute of web-app element
    private String version = null;
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    // Optional publicId attribute
    private String publicId = null;
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    
    // Optional metadata-complete attribute
    private boolean metadataComplete = false;
    public boolean isMetadataComplete() { return metadataComplete; }
    public void setMetadataComplete(boolean metadataComplete) {
        this.metadataComplete = metadataComplete; }
    
    // Optional name element
    private String name = null;
    public String getName() { return name; }
    public void setName(String name) {
        if (ORDER_OTHERS.equalsIgnoreCase(name)) {
            // This is unusual. This name will be ignored. Log the fact.
            log.warn(sm.getString("webXml.reservedName", name));
        } else {
            this.name = name;
        }
    }

    // web-app elements
    // TODO: Ignored elements:
    // - description
    // - icon

    // display-name - TODO should support multiple with language
    private String displayName = null;
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    // distributable
    private boolean distributable = false;
    public boolean isDistributable() { return distributable; }
    public void setDistributable(boolean distributable) {
        this.distributable = distributable;
    }
    
    // context-param
    // TODO: description (multiple with language) is ignored
    private Map<String,String> contextParams = new HashMap<String,String>();
    public void addContextParam(String param, String value) {
        contextParams.put(param, value);
    }
    public Map<String,String> getContextParams() { return contextParams; }
    
    // filter
    // TODO: Should support multiple description elements with language
    // TODO: Should support multiple display-name elements with language
    // TODO: Should support multiple icon elements
    // TODO: Description for init-param is ignored
    private Map<String,FilterDef> filters =
        new LinkedHashMap<String,FilterDef>();
    public void addFilter(FilterDef filter) {
        if (filters.containsKey(filter.getFilterName())) {
            // Filter names must be unique within a web(-fragment).xml
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateFilter"));
        }
        filters.put(filter.getFilterName(), filter);
    }
    public Map<String,FilterDef> getFilters() { return filters; }
    
    // filter-mapping
    private Set<FilterMap> filterMaps = new LinkedHashSet<FilterMap>();
    public void addFilterMapping(FilterMap filterMap) {
        filterMaps.add(filterMap);
    }
    public Set<FilterMap> getFilterMappings() { return filterMaps; }
    
    // listener
    // TODO: description (multiple with language) is ignored
    // TODO: display-name (multiple with language) is ignored
    // TODO: icon (multiple) is ignored
    private Set<String> listeners = new LinkedHashSet<String>();
    public void addListener(String className) {
        listeners.add(className);
    }
    public Set<String> getListeners() { return listeners; }
    
    // servlet
    // TODO: description (multiple with language) is ignored
    // TODO: display-name (multiple with language) is ignored
    // TODO: icon (multiple) is ignored
    // TODO: init-param/description (multiple with language) is ignored
    // TODO: security-role-ref/description (multiple with language) is ignored
    private Map<String,ServletDef> servlets = new HashMap<String,ServletDef>();
    public void addServlet(ServletDef servletDef) {
        servlets.put(servletDef.getServletName(), servletDef);
    }
    public Map<String,ServletDef> getServlets() { return servlets; }
    
    // servlet-mapping
    private Map<String,String> servletMappings = new HashMap<String,String>();
    public void addServletMapping(String urlPattern, String servletName) {
        servletMappings.put(urlPattern, servletName);
    }
    public Map<String,String> getServletMappings() { return servletMappings; }
    
    // session-config/session-timeout
    // Digester will check there is only one of these
    private Integer sessionTimeout = null;
    public void setSessionTimeout(String timeout) {
        sessionTimeout = Integer.valueOf(timeout);
    }
    public Integer getSessionTimeout() { return sessionTimeout; }
    
    // mime-mapping
    private Map<String,String> mimeMappings = new HashMap<String,String>();
    public void addMimeMapping(String extension, String mimeType) {
        mimeMappings.put(extension, mimeType);
    }
    public Map<String,String> getMimeMappings() { return mimeMappings; }
    
    // welcome-file-list
    // When merging web.xml files it may be necessary for any new welcome files
    // to completely replace the current set
    private boolean replaceWelcomeFiles = false;
    public void setReplaceWelcomeFiles(boolean replaceWelcomeFiles) {
        this.replaceWelcomeFiles = replaceWelcomeFiles;
    }
    private Set<String> welcomeFiles = new LinkedHashSet<String>();
    public void addWelcomeFile(String welcomeFile) {
        if (replaceWelcomeFiles) {
            welcomeFiles.clear();
            replaceWelcomeFiles = false;
        }
        welcomeFiles.add(welcomeFile);
    }
    public Set<String> getWelcomeFiles() { return welcomeFiles; }
    
    // error-page
    private Map<String,ErrorPage> errorPages = new HashMap<String,ErrorPage>();
    public void addErrorPage(ErrorPage errorPage) {
        errorPages.put(errorPage.getName(), errorPage);
    }
    public Map<String,ErrorPage> getErrorPages() { return errorPages; }
    
    // Digester will check there is only one jsp-config
    // jsp-config/taglib or taglib (2.3 and earlier)
    private Map<String,String> taglibs = new HashMap<String,String>();
    public void addTaglib(String uri, String location) {
        taglibs.put(uri, location);
    }
    public Map<String,String> getTaglibs() { return taglibs; }
    
    // jsp-config/jsp-property-group
    private Set<JspPropertyGroup> jspPropertyGroups =
        new HashSet<JspPropertyGroup>();
    public void addJspPropertyGroup(JspPropertyGroup propertyGroup) {
        jspPropertyGroups.add(propertyGroup);
    }
    public Set<JspPropertyGroup> getJspPropertyGroups() {
        return jspPropertyGroups;
    }

    // security-constraint
    // TODO: Should support multiple display-name elements with language
    // TODO: Should support multiple description elements with language
    private Set<SecurityConstraint> securityConstraints =
        new HashSet<SecurityConstraint>();
    public void addSecurityConstraint(SecurityConstraint securityConstraint) {
        securityConstraints.add(securityConstraint);
    }
    public Set<SecurityConstraint> getSecurityConstraints() {
        return securityConstraints;
    }
    
    // login-config
    // Digester will check there is only one of these
    private LoginConfig loginConfig = null;
    public void setLoginConfig(LoginConfig loginConfig) {
        this.loginConfig = loginConfig;
    }
    public LoginConfig getLoginConfig() { return loginConfig; }
    
    // security-role
    // TODO: description (multiple with language) is ignored
    private Set<String> securityRoles = new HashSet<String>();
    public void addSecurityRole(String securityRole) {
        securityRoles.add(securityRole);
    }
    public Set<String> getSecurityRoles() { return securityRoles; }
    
    // env-entry
    // TODO: Should support multiple description elements with language
    private Map<String,ContextEnvironment> envEntries =
        new HashMap<String,ContextEnvironment>();
    public void addEnvEntry(ContextEnvironment envEntry) {
        if (envEntries.containsKey(envEntry.getName())) {
            // env-entry names must be unique within a web(-fragment).xml
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateEnvEntry"));
        }
        envEntries.put(envEntry.getName(),envEntry);
    }
    public Map<String,ContextEnvironment> getEnvEntries() { return envEntries; }
    
    // ejb-ref
    // TODO: Should support multiple description elements with language
    private Map<String,ContextEjb> ejbRefs = new HashMap<String,ContextEjb>();
    public void addEjbRef(ContextEjb ejbRef) {
        ejbRefs.put(ejbRef.getName(),ejbRef);
    }
    public Map<String,ContextEjb> getEjbRefs() { return ejbRefs; }
    
    // ejb-local-ref
    // TODO: Should support multiple description elements with language
    private Map<String,ContextLocalEjb> ejbLocalRefs =
        new HashMap<String,ContextLocalEjb>();
    public void addEjbLocalRef(ContextLocalEjb ejbLocalRef) {
        ejbLocalRefs.put(ejbLocalRef.getName(),ejbLocalRef);
    }
    public Map<String,ContextLocalEjb> getEjbLocalRefs() {
        return ejbLocalRefs;
    }
    
    // service-ref
    // TODO: Should support multiple description elements with language
    // TODO: Should support multiple display-names elements with language
    // TODO: Should support multiple icon elements ???
    private Map<String,ContextService> serviceRefs =
        new HashMap<String,ContextService>();
    public void addServiceRef(ContextService serviceRef) {
        serviceRefs.put(serviceRef.getName(), serviceRef);
    }
    public Map<String,ContextService> getServiceRefs() { return serviceRefs; }
    
    // resource-ref
    // TODO: Should support multiple description elements with language
    private Map<String,ContextResource> resourceRefs =
        new HashMap<String,ContextResource>();
    public void addResourceRef(ContextResource resourceRef) {
        if (resourceRefs.containsKey(resourceRef.getName())) {
            // resource-ref names must be unique within a web(-fragment).xml
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateResourceRef"));
        }
        resourceRefs.put(resourceRef.getName(), resourceRef);
    }
    public Map<String,ContextResource> getResourceRefs() {
        return resourceRefs;
    }
    
    // resource-env-ref
    // TODO: Should support multiple description elements with language
    private Map<String,ContextResourceEnvRef> resourceEnvRefs =
        new HashMap<String,ContextResourceEnvRef>();
    public void addResourceEnvRef(ContextResourceEnvRef resourceEnvRef) {
        if (resourceEnvRefs.containsKey(resourceEnvRef.getName())) {
            // resource-env-ref names must be unique within a web(-fragment).xml
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateResourceEnvRef"));
        }
        resourceEnvRefs.put(resourceEnvRef.getName(), resourceEnvRef);
    }
    public Map<String,ContextResourceEnvRef> getResourceEnvRefs() {
        return resourceEnvRefs;
    }
    
    // message-destination-ref
    // TODO: Should support multiple description elements with language
    private Map<String,MessageDestinationRef> messageDestinationRefs =
        new HashMap<String,MessageDestinationRef>();
    public void addMessageDestinationRef(
            MessageDestinationRef messageDestinationRef) {
        if (messageDestinationRefs.containsKey(
                messageDestinationRef.getName())) {
            // message-destination-ref names must be unique within a
            // web(-fragment).xml
            throw new IllegalArgumentException(sm.getString(
                    "webXml.duplicateMessageDestinationRef"));
        }
        messageDestinationRefs.put(messageDestinationRef.getName(),
                messageDestinationRef);
    }
    public Map<String,MessageDestinationRef> getMessageDestinationRefs() {
        return messageDestinationRefs;
    }
    
    // message-destination
    // TODO: Should support multiple description elements with language
    // TODO: Should support multiple display-names elements with language
    // TODO: Should support multiple icon elements ???
    private Map<String,MessageDestination> messageDestinations =
        new HashMap<String,MessageDestination>();
    public void addMessageDestination(
            MessageDestination messageDestination) {
        if (messageDestinations.containsKey(
                messageDestination.getName())) {
            // message-destination names must be unique within a
            // web(-fragment).xml
            throw new IllegalArgumentException(
                    sm.getString("webXml.duplicateMessageDestination"));
        }
        messageDestinations.put(messageDestination.getName(),
                messageDestination);
    }
    public Map<String,MessageDestination> getMessageDestinations() {
        return messageDestinations;
    }
    
    // locale-encoging-mapping-list
    private Map<String,String> localeEncodingMappings =
        new HashMap<String,String>();
    public void addLocaleEncodingMapping(String locale, String encoding) {
        localeEncodingMappings.put(locale, encoding);
    }
    public Map<String,String> getLocalEncodingMappings() {
        return localeEncodingMappings;
    }
    

    // Attributes not defined in web.xml or web-fragment.xml
    
    // URL of web-fragment
    private URL uRL = null;
    public void setURL(URL url) { this.uRL = url; }
    public URL getURL() { return uRL; }
    
    
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(32);
        buf.append("Name: ");
        buf.append(getName());
        buf.append(", URL: ");
        buf.append(getURL());
        return buf.toString();
    }
    
    
    /**
     * Configure a {@link Context} using the stored web.xml representation.
     *  
     * @param context   The context to be configured
     */
    public void configureContext(Context context) {
        // As far as possible, process in alphabetical order so it is easy to
        // check everything is present
        // Some validation depends on correct public ID
        context.setPublicId(publicId);

        // Everything else in order
        for (String contextParam : contextParams.keySet()) {
            context.addParameter(contextParam, contextParams.get(contextParam));
        }
        context.setDisplayName(displayName);
        context.setDistributable(distributable);
        for (ContextLocalEjb ejbLocalRef : ejbLocalRefs.values()) {
            context.getNamingResources().addLocalEjb(ejbLocalRef);
        }
        for (ContextEjb ejbRef : ejbRefs.values()) {
            context.getNamingResources().addEjb(ejbRef);
        }
        for (ContextEnvironment environment : envEntries.values()) {
            context.getNamingResources().addEnvironment(environment);
        }
        for (ErrorPage errorPage : errorPages.values()) {
            context.addErrorPage(errorPage);
        }
        for (FilterDef filter : filters.values()) {
            context.addFilterDef(filter);
        }
        for (FilterMap filterMap : filterMaps) {
            context.addFilterMap(filterMap);
        }
        // jsp-property-group needs to be after servlet configuration
        for (String listener : listeners) {
            context.addApplicationListener(listener);
        }
        for (String locale : localeEncodingMappings.keySet()) {
            context.addLocaleEncodingMappingParameter(locale,
                    localeEncodingMappings.get(locale));
        }
        // Prevents IAE
        if (loginConfig != null) {
            context.setLoginConfig(loginConfig);
        }
        for (MessageDestinationRef mdr : messageDestinationRefs.values()) {
            context.getNamingResources().addMessageDestinationRef(mdr);
        }

        // messageDestinations were ignored in Tomcat 6, so ignore here
        
        // TODO SERVLET3 - This needs to be more fine-grained. Whether or not to
        //                 process annotations on destroy() will depend on where
        //                 the filter/servlet was loaded from. Joy.
        context.setIgnoreAnnotations(metadataComplete);
        for (String extension : mimeMappings.keySet()) {
            context.addMimeMapping(extension, mimeMappings.get(extension));
        }
        // Name is just used for ordering
        for (ContextResourceEnvRef resource : resourceEnvRefs.values()) {
            context.getNamingResources().addResourceEnvRef(resource);
        }
        for (ContextResource resource : resourceRefs.values()) {
            context.getNamingResources().addResource(resource);
        }
        for (SecurityConstraint constraint : securityConstraints) {
            context.addConstraint(constraint);
        }
        for (String role : securityRoles) {
            context.addSecurityRole(role);
        }
        for (ContextService service : serviceRefs.values()) {
            context.getNamingResources().addService(service);
        }
        for (ServletDef servlet : servlets.values()) {
            Wrapper wrapper = context.createWrapper();
            // Description is ignored
            // Display name is ignored
            // Icons are ignored
            // Only set this if it is non-null else every servlet will get
            // marked as the JSP servlet
            String jspFile = servlet.getJspFile();
            if (jspFile != null) {
                wrapper.setJspFile(jspFile);
            }
            if (servlet.getLoadOnStartup() != null) {
                wrapper.setLoadOnStartup(servlet.getLoadOnStartup().intValue());
            }
            wrapper.setName(servlet.getServletName());
            Map<String,String> params = servlet.getParameterMap(); 
            for (String param : params.keySet()) {
                wrapper.addInitParameter(param, params.get(param));
            }
            wrapper.setRunAs(servlet.getRunAs());
            Set<SecurityRoleRef> roleRefs = servlet.getSecurityRoleRefs();
            for (SecurityRoleRef roleRef : roleRefs) {
                wrapper.addSecurityReference(
                        roleRef.getName(), roleRef.getLink());
            }
            wrapper.setServletClass(servlet.getServletClass());
            // TODO SERVLET3 - Multipart config
            context.addChild(wrapper);
        }
        for (String pattern : servletMappings.keySet()) {
            context.addServletMapping(pattern, servletMappings.get(pattern));
        }
        if (sessionTimeout != null) {
            context.setSessionTimeout(sessionTimeout.intValue());
        }
        for (String uri : taglibs.keySet()) {
            context.addTaglib(uri, taglibs.get(uri));
        }
        
        // Context doesn't use version directly
        
        for (String welcomeFile : welcomeFiles) {
            context.addWelcomeFile(welcomeFile);
        }

        // Do this last as it depends on servlets
        for (JspPropertyGroup jspPropertyGroup : jspPropertyGroups) {
            context.addJspMapping(jspPropertyGroup.getUrlPattern());
        }
    }
    
    /**
     * Merge the supplied web fragments into this main web.xml.
     * 
     * @param fragments     The fragments to merge in
     * @return <code>true</code> if merge is successful, else
     *         <code>false</code>
     */
    public boolean merge(Set<WebXml> fragments) {
        // As far as possible, process in alphabetical order so it is easy to
        // check everything is present
        
        // Merge rules vary from element to element. See SRV.8.2.3

        WebXml temp = new WebXml();
        Map<String,Boolean> mergeInjectionFlags =
            new HashMap<String, Boolean>();

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getContextParams(), contextParams,
                    temp.getContextParams(), fragment, "Context Parameter")) {
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
                        log.error(sm.getString(
                                "webXml.mergeConflictDisplayName",
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            displayName = temp.getDisplayName();
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
            if (!mergeResourceMap(fragment.getEjbLocalRefs(), ejbLocalRefs,
                    temp.getEjbLocalRefs(), mergeInjectionFlags, fragment)) {
                return false;
            }
        }
        ejbLocalRefs.putAll(temp.getEjbLocalRefs());
        mergeInjectionFlags.clear();

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getEjbRefs(), ejbRefs,
                    temp.getEjbRefs(), mergeInjectionFlags, fragment)) {
                return false;
            }
        }
        ejbRefs.putAll(temp.getEjbRefs());
        mergeInjectionFlags.clear();

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getEnvEntries(), envEntries,
                    temp.getEnvEntries(), mergeInjectionFlags, fragment)) {
                return false;
            }
        }
        envEntries.putAll(temp.getEnvEntries());
        mergeInjectionFlags.clear();

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getErrorPages(), errorPages,
                    temp.getErrorPages(), fragment, "Error Page")) {
                return false;
            }
        }
        errorPages.putAll(temp.getErrorPages());

        for (WebXml fragment : fragments) {
            for (FilterMap filterMap : fragment.getFilterMappings()) {
                // Always additive
                addFilterMapping(filterMap);
            }
        }

        for (WebXml fragment : fragments) {
            for (Map.Entry<String,FilterDef> entry :
                    fragment.getFilters().entrySet()) {
                if (filters.containsKey(entry.getKey())) {
                    mergeFilter(entry.getValue(),
                            filters.get(entry.getKey()), false);
                } else {
                    if (!(mergeFilter(entry.getValue(),
                            temp.getFilters().get(entry.getKey()), true))) {
                        log.error(sm.getString(
                                "webXml.mergeConflictFilter",
                                entry.getKey(),
                                fragment.getName(),
                                fragment.getURL()));

                        return false;
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
            if (!mergeMap(fragment.getLocalEncodingMappings(),
                    localeEncodingMappings, temp.getLocalEncodingMappings(),
                    fragment, "Locale Encoding Mapping")) {
                return false;
            }
        }
        localeEncodingMappings.putAll(temp.getLocalEncodingMappings());

        if (getLoginConfig() == null) {
            LoginConfig tempLoginConfig = null;
            for (WebXml fragment : fragments) {
                LoginConfig fragmentLoginConfig = fragment.loginConfig;
                if (fragmentLoginConfig != null) {
                    if (tempLoginConfig == null ||
                            fragmentLoginConfig.equals(tempLoginConfig)) {
                        tempLoginConfig = fragmentLoginConfig;
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictLoginConfig",
                                fragment.getName(),
                                fragment.getURL()));
                    }
                }
            }
        }


        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getMessageDestinationRefs(), messageDestinationRefs,
                    temp.getMessageDestinationRefs(), mergeInjectionFlags, fragment)) {
                return false;
            }
        }
        messageDestinationRefs.putAll(temp.getMessageDestinationRefs());
        mergeInjectionFlags.clear();

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getMessageDestinations(), messageDestinations,
                    temp.getMessageDestinations(), mergeInjectionFlags, fragment)) {
                return false;
            }
        }
        messageDestinations.putAll(temp.getMessageDestinations());
        mergeInjectionFlags.clear();

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getMimeMappings(), mimeMappings,
                    temp.getMimeMappings(), fragment, "Mime Mapping")) {
                return false;
            }
        }
        mimeMappings.putAll(temp.getMimeMappings());

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getResourceEnvRefs(), resourceEnvRefs,
                    temp.getResourceEnvRefs(), mergeInjectionFlags, fragment)) {
                return false;
            }
        }
        resourceEnvRefs.putAll(temp.getResourceEnvRefs());
        mergeInjectionFlags.clear();

        for (WebXml fragment : fragments) {
            if (!mergeResourceMap(fragment.getResourceRefs(), resourceRefs,
                    temp.getResourceRefs(), mergeInjectionFlags, fragment)) {
                return false;
            }
        }
        resourceRefs.putAll(temp.getResourceRefs());
        mergeInjectionFlags.clear();

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
            if (!mergeResourceMap(fragment.getServiceRefs(), serviceRefs,
                    temp.getServiceRefs(), mergeInjectionFlags, fragment)) {
                return false;
            }
        }
        serviceRefs.putAll(temp.getServiceRefs());
        mergeInjectionFlags.clear();

        for (WebXml fragment : fragments) {
            for (Map.Entry<String,String> mapping :
                    fragment.getServletMappings().entrySet()) {
                // Always additive
                addServletMapping(mapping.getKey(), mapping.getValue());
            }
        }

        for (WebXml fragment : fragments) {
            for (Map.Entry<String,ServletDef> entry :
                    fragment.getServlets().entrySet()) {
                if (servlets.containsKey(entry.getKey())) {
                    mergeServlet(entry.getValue(),
                            servlets.get(entry.getKey()), false);
                } else {
                    if (!(mergeServlet(entry.getValue(),
                            temp.getServlets().get(entry.getKey()), true))) {
                        log.error(sm.getString(
                                "webXml.mergeConflictServlet",
                                entry.getKey(),
                                fragment.getName(),
                                fragment.getURL()));

                        return false;
                    }
                }
            }
        }
        servlets.putAll(temp.getServlets());
        
        if (sessionTimeout == null) {
            for (WebXml fragment : fragments) {
                Integer value = fragment.getSessionTimeout(); 
                if (value != null) {
                    if (temp.getSessionTimeout() == null) {
                        temp.setSessionTimeout(value.toString());
                    } else {
                        log.error(sm.getString(
                                "webXml.mergeConflictSessionTimeout",
                                fragment.getName(),
                                fragment.getURL()));
                        return false;
                    }
                }
            }
            sessionTimeout = temp.getSessionTimeout();
        }

        for (WebXml fragment : fragments) {
            if (!mergeMap(fragment.getTaglibs(), taglibs,
                    temp.getTaglibs(), fragment, "Taglibs")) {
                return false;
            }
        }
        taglibs.putAll(temp.getTaglibs());

        for (WebXml fragment : fragments) {
            for (String welcomeFile : fragment.getWelcomeFiles()) {
                // Always additive
                addWelcomeFile(welcomeFile);
            }
        }

        return true;
    }
    
    private <T extends ResourceBase> boolean mergeResourceMap(
            Map<String, T> fragmentResources, Map<String, T> mainResources,
            Map<String, T> tempResources,
            Map<String,Boolean> mergeInjectionFlags, WebXml fragment) {
        for (T resource : fragmentResources.values()) {
            String resourceName = resource.getName();
            boolean mergeInjectionFlag = false;
            if (mainResources.containsKey(resourceName)) {
                if (mergeInjectionFlags.containsKey(resourceName)) {
                    mergeInjectionFlag =
                        mergeInjectionFlags.get(resourceName).booleanValue(); 
                } else {
                    if (mainResources.get(
                            resourceName).getInjectionTargets().size() == 0) {
                        mergeInjectionFlag = true;
                    }
                    mergeInjectionFlags.put(resourceName,
                            Boolean.valueOf(mergeInjectionFlag));
                }
                if (mergeInjectionFlag) {
                    mainResources.get(resourceName).getInjectionTargets().addAll(
                            resource.getInjectionTargets());
                }
            } else {
                // Not defined in main web.xml
                if (tempResources.containsKey(resourceName)) {
                    log.error(sm.getString(
                            "webXml.mergeConflictResource",
                            resourceName,
                            fragment.getName(),
                            fragment.getURL()));
                    return false;
                } 
                tempResources.put(resourceName, resource);
            }
        }
        return true;
    }
    
    private <T> boolean mergeMap(Map<String,T> fragmentMap,
            Map<String,T> mainMap, Map<String,T> tempMap, WebXml fragment,
            String mapName) {
        for (String key : fragmentMap.keySet()) {
            if (!mainMap.containsKey(key)) {
                // Not defined in main web.xml
                T value = fragmentMap.get(key);
                if (tempMap.containsKey(key)) {
                    if (value != null && !value.equals(
                            tempMap.get(key))) {
                        log.error(sm.getString(
                                "webXml.mergeConflictString",
                                mapName,
                                key,
                                fragment.getName(),
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
    
    private boolean mergeFilter(FilterDef src, FilterDef dest, boolean failOnConflict) {
        if (src.isAsyncSupported() != dest.isAsyncSupported()) {
            // Always fail
            return false;
        }
        
        if (dest.getFilterClass()  == null) {
            dest.setFilterClass(src.getFilterClass());
        } else if (src.getFilterClass() != null) {
            if (failOnConflict &&
                    !src.getFilterClass().equals(dest.getFilterClass())) {
                return false;
            }
        }
        
        for (Map.Entry<String,String> srcEntry :
                src.getParameterMap().entrySet()) {
            if (dest.getParameterMap().containsKey(srcEntry.getKey())) {
                if (failOnConflict && !dest.getParameterMap().get(
                        srcEntry.getKey()).equals(srcEntry.getValue())) {
                    return false;
                }
            } else {
                dest.addInitParameter(srcEntry.getKey(), srcEntry.getValue());
            }
        }
        return true;
    }
    
    private boolean mergeServlet(ServletDef src, ServletDef dest, boolean failOnConflict) {
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
                    (dest.getJspFile() != null ||
                            !src.getServletClass().equals(dest.getServletClass()))) {
                return false;
            }
            if (src.getJspFile() != null &&
                    (dest.getServletClass() != null ||
                            !src.getJspFile().equals(dest.getJspFile()))) {
                return false;
            }
        }
        
        // Additive
        for (SecurityRoleRef securityRoleRef : src.getSecurityRoleRefs()) {
            dest.addSecurityRoleRef(securityRoleRef);
        }
        
        if (dest.getLoadOnStartup() == null) {
            dest.setLoadOnStartup(src.getServletClass());
        } else if (src.getLoadOnStartup() != null) {
            if (failOnConflict &&
                    !src.getLoadOnStartup().equals(dest.getLoadOnStartup())) {
                return false;
            }
        }
        
        for (Map.Entry<String,String> srcEntry :
                src.getParameterMap().entrySet()) {
            if (dest.getParameterMap().containsKey(srcEntry.getKey())) {
                if (failOnConflict && !dest.getParameterMap().get(
                        srcEntry.getKey()).equals(srcEntry.getValue())) {
                    return false;
                }
            } else {
                dest.addInitParameter(srcEntry.getKey(), srcEntry.getValue());
            }
        }
        
        if (dest.getMultipartDef() == null) {
            dest.setMultipartDef(src.getMultipartDef());
        } else if (src.getMultipartDef() != null) {
            return mergeMultipartDef(src.getMultipartDef(),
                    dest.getMultipartDef(), failOnConflict);
        }
        
        return true;
    }

    private boolean mergeMultipartDef(MultipartDef src, MultipartDef dest,
            boolean failOnConflict) {

        if (dest.getLocation() == null) {
            dest.setLocation(src.getLocation());
        } else if (src.getLocation() != null) {
            if (failOnConflict &&
                    !src.getLocation().equals(dest.getLocation())) {
                return false;
            }
        }

        if (dest.getFileSizeThreshold() == null) {
            dest.setFileSizeThreshold(src.getFileSizeThreshold());
        } else if (src.getFileSizeThreshold() != null) {
            if (failOnConflict &&
                    !src.getFileSizeThreshold().equals(
                            dest.getFileSizeThreshold())) {
                return false;
            }
        }

        if (dest.getMaxFileSize() == null) {
            dest.setMaxFileSize(src.getMaxFileSize());
        } else if (src.getLocation() != null) {
            if (failOnConflict &&
                    !src.getMaxFileSize().equals(dest.getMaxFileSize())) {
                return false;
            }
        }

        if (dest.getMaxRequestSize() == null) {
            dest.setMaxRequestSize(src.getMaxRequestSize());
        } else if (src.getMaxRequestSize() != null) {
            if (failOnConflict &&
                    !src.getMaxRequestSize().equals(
                            dest.getMaxRequestSize())) {
                return false;
            }
        }

        return true;
    }
    
    
    /**
     * Generates the sub-set of the web-fragment.xml files to be processed in
     * the order that the fragments must be processed as per the rules in the
     * Servlet spec.
     * 
     * @param application   The application web.xml file
     * @param fragments     The map of fragment names to web fragments
     * @return Ordered list of web-fragment.xml files to process
     */
    protected static Set<WebXml> orderWebFragments(WebXml application,
            Map<String,WebXml> fragments) {

        Set<WebXml> orderedFragments = new LinkedHashSet<WebXml>();
        
        boolean absoluteOrdering =
            (application.getAbsoluteOrdering() != null);
        
        if (absoluteOrdering) {
            // Only those fragments listed should be processed
            Set<String> requestedOrder = application.getAbsoluteOrdering();
            
            for (String requestedName : requestedOrder) {
                if (WebXml.ORDER_OTHERS.equals(requestedName)) {
                    // Add all fragments not named explicitly at this point
                    for (String name : fragments.keySet()) {
                        if (!requestedOrder.contains(name)) {
                            WebXml fragment = fragments.get(name);
                            if (fragment != null) {
                                orderedFragments.add(fragment);
                            }
                        }
                    }
                } else {
                    WebXml fragment = fragments.get(requestedName);
                    if (fragment != null) {
                        orderedFragments.add(fragment);
                    }
                }
            }
        } else {
            List<String> order = new LinkedList<String>();
            // Start by adding all fragments - order doesn't matter
            order.addAll(fragments.keySet());
            
            // Now go through and move elements to start/end depending on if
            // they specify others
            for (WebXml fragment : fragments.values()) {
                String name = fragment.getName();
                if (fragment.getBeforeOrder().contains(WebXml.ORDER_OTHERS)) {
                    // Move to beginning
                    order.remove(name);
                    order.add(0, name);
                } else if (fragment.getAfterOrder().contains(WebXml.ORDER_OTHERS)) {
                    // Move to end
                    order.remove(name);
                    order.add(name);
                }
            }
            
            // Now apply remaining ordering
            for (WebXml fragment : fragments.values()) {
                String name = fragment.getName();
                for (String before : fragment.getBeforeOrder()) {
                    if (!before.equals(WebXml.ORDER_OTHERS) &&
                            order.contains(before) &&
                            order.indexOf(before) < order.indexOf(name)) {
                        order.remove(name);
                        order.add(order.indexOf(before), name);
                    }
                }
                for (String after : fragment.getAfterOrder()) {
                    if (!after.equals(WebXml.ORDER_OTHERS) &&
                            order.contains(after) &&
                            order.indexOf(after) > order.indexOf(name)) {
                        order.remove(name);
                        order.add(order.indexOf(after) + 1, name);
                    }
                }
            }
            
            // Finally check ordering was applied correctly - if there are
            // errors then that indicates circular references
            for (WebXml fragment : fragments.values()) {
                String name = fragment.getName();
                for (String before : fragment.getBeforeOrder()) {
                    if (!before.equals(WebXml.ORDER_OTHERS) &&
                            order.contains(before) &&
                            order.indexOf(before) < order.indexOf(name)) {
                        throw new IllegalArgumentException(sm.getString(""));
                    }
                }
                for (String after : fragment.getAfterOrder()) {
                    if (!after.equals(WebXml.ORDER_OTHERS) &&
                            order.contains(after) &&
                            order.indexOf(after) > order.indexOf(name)) {
                        throw new IllegalArgumentException();
                    }
                }
            }
            
            // Build the ordered list
            for (String name : order) {
                orderedFragments.add(fragments.get(name));
            }
        }
        
        return orderedFragments;
    }

}    

