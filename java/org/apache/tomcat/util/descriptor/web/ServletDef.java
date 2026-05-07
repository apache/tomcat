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

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.tomcat.util.res.StringManager;


/**
 * Representation of a servlet definition for a web application, as represented in a <code>&lt;servlet&gt;</code>
 * element in the deployment descriptor.
 */

public class ServletDef implements Serializable {

    /**
     * Default constructor.
     */
    public ServletDef() {
    }

    @Serial
    private static final long serialVersionUID = 1L;

    private static final StringManager sm = StringManager.getManager(Constants.PACKAGE_NAME);

    // ------------------------------------------------------------- Properties


    /**
     * The description of this servlet.
     */
    private String description = null;

    /**
     * Returns the description of this servlet.
     *
     * @return the description
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Sets the description of this servlet.
     *
     * @param description the description
     */
    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * The display name of this servlet.
     */
    private String displayName = null;

    /**
     * Returns the display name of this servlet.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Sets the display name of this servlet.
     *
     * @param displayName the display name
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    /**
     * The small icon associated with this servlet.
     */
    private String smallIcon = null;

    /**
     * Returns the small icon associated with this servlet.
     *
     * @return the small icon
     */
    public String getSmallIcon() {
        return this.smallIcon;
    }

    /**
     * Sets the small icon associated with this servlet.
     *
     * @param smallIcon the small icon
     */
    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }

    /**
     * The large icon associated with this servlet.
     */
    private String largeIcon = null;

    /**
     * Returns the large icon associated with this servlet.
     *
     * @return the large icon
     */
    public String getLargeIcon() {
        return this.largeIcon;
    }

    /**
     * Sets the large icon associated with this servlet.
     *
     * @param largeIcon the large icon
     */
    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }


    /**
     * The name of this servlet, which must be unique among the servlets defined for a particular web application.
     */
    private String servletName = null;

    /**
     * Returns the name of this servlet.
     *
     * @return the servlet name
     */
    public String getServletName() {
        return this.servletName;
    }

    /**
     * Sets the name of this servlet.
     *
     * @param servletName the servlet name
     */
    public void setServletName(String servletName) {
        if (servletName == null || servletName.isEmpty()) {
            throw new IllegalArgumentException(sm.getString("servletDef.invalidServletName", servletName));
        }
        this.servletName = servletName;
    }


    /**
     * The fully qualified name of the Java class that implements this servlet.
     */
    private String servletClass = null;

    /**
     * Returns the fully qualified name of the Java class that implements this servlet.
     *
     * @return the servlet class
     */
    public String getServletClass() {
        return this.servletClass;
    }

    /**
     * Sets the fully qualified name of the Java class that implements this servlet.
     *
     * @param servletClass the servlet class
     */
    public void setServletClass(String servletClass) {
        this.servletClass = servletClass;
    }


    /**
     * The name of the JSP file to which this servlet definition applies
     */
    private String jspFile = null;

    /**
     * Returns the name of the JSP file to which this servlet definition applies.
     *
     * @return the JSP file
     */
    public String getJspFile() {
        return this.jspFile;
    }

    /**
     * Sets the name of the JSP file to which this servlet definition applies.
     *
     * @param jspFile the JSP file
     */
    public void setJspFile(String jspFile) {
        this.jspFile = jspFile;
    }


    /**
     * The set of initialization parameters for this servlet, keyed by parameter name.
     */
    private final Map<String,String> parameters = new HashMap<>();

    /**
     * Returns the set of initialization parameters for this servlet, keyed by parameter name.
     *
     * @return the parameter map
     */
    public Map<String,String> getParameterMap() {
        return this.parameters;
    }

    /**
     * Add an initialization parameter to the set of parameters associated with this servlet.
     *
     * @param name  The initialisation parameter name
     * @param value The initialisation parameter value
     */
    public void addInitParameter(String name, String value) {

        if (parameters.containsKey(name)) {
            // The spec does not define this but the TCK expects the first
            // definition to take precedence
            return;
        }
        parameters.put(name, value);

    }

    /**
     * The load-on-startup order for this servlet
     */
    private Integer loadOnStartup = null;

    /**
     * Returns the load-on-startup order for this servlet.
     *
     * @return the load-on-startup order
     */
    public Integer getLoadOnStartup() {
        return this.loadOnStartup;
    }

    /**
     * Sets the load-on-startup order for this servlet.
     *
     * @param loadOnStartup the load-on-startup order as a string
     */
    public void setLoadOnStartup(String loadOnStartup) {
        this.loadOnStartup = Integer.valueOf(loadOnStartup);
    }


    /**
     * The run-as configuration for this servlet
     */
    private String runAs = null;

    /**
     * Returns the run-as configuration for this servlet.
     *
     * @return the run-as role name
     */
    public String getRunAs() {
        return this.runAs;
    }

    /**
     * Sets the run-as configuration for this servlet.
     *
     * @param runAs the run-as role name
     */
    public void setRunAs(String runAs) {
        this.runAs = runAs;
    }


    /**
     * The set of security role references for this servlet
     */
    private final Set<SecurityRoleRef> securityRoleRefs = new HashSet<>();

    /**
     * Returns the set of security role references for this servlet.
     *
     * @return the security role references
     */
    public Set<SecurityRoleRef> getSecurityRoleRefs() {
        return this.securityRoleRefs;
    }

    /**
     * Add a security-role-ref to the set of security-role-refs associated with this servlet.
     *
     * @param securityRoleRef The security role
     */
    public void addSecurityRoleRef(SecurityRoleRef securityRoleRef) {
        securityRoleRefs.add(securityRoleRef);
    }

    /**
     * The multipart configuration, if any, for this servlet
     */
    private MultipartDef multipartDef = null;

    /**
     * Returns the multipart configuration for this servlet.
     *
     * @return the multipart configuration
     */
    public MultipartDef getMultipartDef() {
        return this.multipartDef;
    }

    /**
     * Sets the multipart configuration for this servlet.
     *
     * @param multipartDef the multipart configuration
     */
    public void setMultipartDef(MultipartDef multipartDef) {
        this.multipartDef = multipartDef;
    }


    /**
     * Does this servlet support async.
     */
    private Boolean asyncSupported = null;

    /**
     * Returns whether this servlet supports async processing.
     *
     * @return whether async is supported
     */
    public Boolean getAsyncSupported() {
        return this.asyncSupported;
    }

    /**
     * Sets whether this servlet supports async processing.
     *
     * @param asyncSupported whether async is supported as a string
     */
    public void setAsyncSupported(String asyncSupported) {
        this.asyncSupported = Boolean.valueOf(asyncSupported);
    }


    /**
     * Is this servlet enabled.
     */
    private Boolean enabled = null;

    /**
     * Returns whether this servlet is enabled.
     *
     * @return whether the servlet is enabled
     */
    public Boolean getEnabled() {
        return this.enabled;
    }

    /**
     * Sets whether this servlet is enabled.
     *
     * @param enabled whether the servlet is enabled as a string
     */
    public void setEnabled(String enabled) {
        this.enabled = Boolean.valueOf(enabled);
    }


    /**
     * Can this ServletDef be overridden by an SCI?
     */
    private boolean overridable = false;

    /**
     * Returns whether this ServletDef can be overridden by a Servlet Container Initializer.
     *
     * @return whether the servlet definition is overridable
     */
    public boolean isOverridable() {
        return overridable;
    }

    /**
     * Sets whether this ServletDef can be overridden by a Servlet Container Initializer.
     *
     * @param overridable whether the servlet definition is overridable
     */
    public void setOverridable(boolean overridable) {
        this.overridable = overridable;
    }

}
