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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    private static org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog(WebXml.class);
    
    // web.xml only elements
    // Absolute Ordering
    private Set<String> absoluteOrdering = new LinkedHashSet<String>();
    public void addAbsoluteOrdering(String name) {
        absoluteOrdering.add(name);
    }
    public void addAbsoluteOrderingOthers() {
        absoluteOrdering.add(ORDER_OTHERS);
    }

    // web-fragment.xml only elements
    // Relative ordering
    private Set<String> after = new LinkedHashSet<String>();
    public void addAfterOrdering(String name) {
        after.add(name);
    }
    public void addAfterOrderingOthers() {
        if (before.contains(ORDER_OTHERS)) {
            throw new IllegalArgumentException(sm.getString(
                    "webXmlFragment.multipleOther"));
        }
        after.add(ORDER_OTHERS);
    }
    private Set<String> before = new LinkedHashSet<String>();
    public void addBeforeOrdering(String name) {
        before.add(name);
    }
    public void addBeforeOrderingOthers() {
        if (after.contains(ORDER_OTHERS)) {
            throw new IllegalArgumentException(sm.getString(
                    "webXmlFragment.multipleOther"));
        }
        before.add(ORDER_OTHERS);
    }
    
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
            log.warn(sm.getString("webXmlCommon.reservedName", name));
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
    public void addContextParam(String name, String value) {
        contextParams.put(name, value);
    }
    public Map<String,String> getContextParams() { return contextParams; }
    
    // filter
    // TODO: Should support multiple description elements with language
    // TODO: Should support multiple display-name elements with language
    // TODO: Should support multiple icon elements
    // TODO: Description for init-param is ignored
    private Map<String,FilterDef> filters = new HashMap<String,FilterDef>();
    public void addFilter(FilterDef filter) {
        if (filters.containsKey(filter.getFilterName())) {
            // Filter names must be unique within a web(-fragment).xml
            throw new IllegalArgumentException(
                    sm.getString("webXmlCommon.duplicateFilter"));
        }
        filters.put(filter.getFilterName(), filter);
    }
    public Map<String,FilterDef> getFilters() { return filters; }
    
    // filter-mapping
    private Set<FilterMap> filterMaps = new HashSet<FilterMap>();
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
    
    // error-page
    private Set<ErrorPage> errorPages = new HashSet<ErrorPage>();
    public void addErrorPage(ErrorPage errorPage) {
        errorPages.add(errorPage);
    }
    public Set<ErrorPage> getErrorPages() { return errorPages; }
    
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
                    sm.getString("webXmlCommon.duplicateEnvEntry"));
        }
        envEntries.put(envEntry.getName(),envEntry);
    }
    public Map<String,ContextEnvironment> getEnvEntries() { return envEntries; }
    
    // ejb-ref
    // TODO: Should support multiple description elements with language
    private Set<ContextEjb> ejbRefs = new HashSet<ContextEjb>();
    public void addEjbRef(ContextEjb ejbRef) { ejbRefs.add(ejbRef); }
    public Set<ContextEjb> getEjbRefs() { return ejbRefs; }
    
    // ejb-local-ref
    // TODO: Should support multiple description elements with language
    private Set<ContextLocalEjb> ejbLocalRefs = new HashSet<ContextLocalEjb>();
    public void addEjbLocalRef(ContextLocalEjb ejbLocalRef) {
        ejbLocalRefs.add(ejbLocalRef);
    }
    public Set<ContextLocalEjb> getEjbLocalRefs() { return ejbLocalRefs; }
    
    // service-ref
    // TODO: Should support multiple description elements with language
    // TODO: Should support multiple display-names elements with language
    // TODO: Should support multiple icon elements ???
    private Set<ContextService> serviceRefs = new HashSet<ContextService>();
    public void addServiceRef(ContextService serviceRef) {
        serviceRefs.add(serviceRef);
    }
    public Set<ContextService> getServiceRefs() { return serviceRefs; }
    
    // resource-ref
    // TODO: Should support multiple description elements with language
    private Map<String,ContextResource> resourceRefs =
        new HashMap<String,ContextResource>();
    public void addResourceRef(ContextResource resourceRef) {
        if (resourceRefs.containsKey(resourceRef.getName())) {
            // resource-ref names must be unique within a web(-fragment).xml
            throw new IllegalArgumentException(
                    sm.getString("webXmlCommon.duplicateResourceRef"));
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
                    sm.getString("webXmlCommon.duplicateResourceEnvRef"));
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
                    "webXmlCommon.duplicateMessageDestinationRef"));
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
                    sm.getString("webXmlCommon.duplicateMessageDestination"));
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
        for (ContextLocalEjb ejbLocalRef : ejbLocalRefs) {
            context.getNamingResources().addLocalEjb(ejbLocalRef);
        }
        for (ContextEjb ejbRef : ejbRefs) {
            context.getNamingResources().addEjb(ejbRef);
        }
        for (ContextEnvironment environment : envEntries.values()) {
            context.getNamingResources().addEnvironment(environment);
        }
        for (ErrorPage errorPage : errorPages) {
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
        for (ContextService service : serviceRefs) {
            context.getNamingResources().addService(service);
        }
        for (ServletDef servlet : servlets.values()) {
            Wrapper wrapper = context.createWrapper();
            // Description is ignored
            // Display name is ignored
            // Icons are ignored
            wrapper.setJspFile(servlet.getJspFile());
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
}
