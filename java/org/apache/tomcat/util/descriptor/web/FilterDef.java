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
import java.util.Map;

import jakarta.servlet.Filter;

import org.apache.tomcat.util.res.StringManager;


/**
     * Representation of a filter definition for a web application, as represented in a <code>&lt;filter&gt;</code> element
     * in the deployment descriptor.
     */
public class FilterDef implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final StringManager sm = StringManager.getManager(Constants.PACKAGE_NAME);

    /**
     * Default constructor for FilterDef.
     */
    public FilterDef() {
        // Default constructor
    }

    // ------------------------------------------------------------- Properties


    /**
     * The description of this filter.
     */
    private String description = null;

    /**
     * Returns the description of this filter.
     *
     * @return The description
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Sets the description of this filter.
     *
     * @param description The new description
     */
    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * The display name of this filter.
     */
    private String displayName = null;

    /**
     * Returns the display name of this filter.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Sets the display name of this filter.
     *
     * @param displayName The new display name
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    /**
     * The filter instance associated with this definition
     */
    private transient Filter filter = null;

    /**
     * Returns the filter instance associated with this definition.
     *
     * @return The filter instance
     */
    public Filter getFilter() {
        return filter;
    }

    /**
     * Sets the filter instance associated with this definition.
     *
     * @param filter The filter instance
     */
    public void setFilter(Filter filter) {
        this.filter = filter;
    }


    /**
     * The fully qualified name of the Java class that implements this filter.
     */
    private String filterClass = null;

    /**
     * Returns the fully qualified name of the Java class that implements this filter.
     *
     * @return The filter class name
     */
    public String getFilterClass() {
        return this.filterClass;
    }

    /**
     * Sets the fully qualified name of the Java class that implements this filter.
     *
     * @param filterClass The filter class name
     */
    public void setFilterClass(String filterClass) {
        this.filterClass = filterClass;
    }


    /**
     * The name of this filter, which must be unique among the filters defined for a particular web application.
     */
    private String filterName = null;

    /**
     * Returns the name of this filter.
     *
     * @return The filter name
     */
    public String getFilterName() {
        return this.filterName;
    }

    /**
     * Sets the name of this filter, which must be unique among the filters defined for a particular web application.
     *
     * @param filterName The new filter name
     */
    public void setFilterName(String filterName) {
        if (filterName == null || filterName.isEmpty()) {
            throw new IllegalArgumentException(sm.getString("filterDef.invalidFilterName", filterName));
        }
        this.filterName = filterName;
    }


    /**
     * The large icon associated with this filter.
     */
    private String largeIcon = null;

    /**
     * Returns the large icon associated with this filter.
     *
     * @return The large icon
     */
    public String getLargeIcon() {
        return this.largeIcon;
    }

    /**
     * Sets the large icon associated with this filter.
     *
     * @param largeIcon The new large icon
     */
    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }


    /**
     * The set of initialization parameters for this filter, keyed by parameter name.
     */
    private final Map<String,String> parameters = new HashMap<>();

    /**
     * Returns the set of initialization parameters for this filter, keyed by parameter name.
     *
     * @return The initialization parameter map
     */
    public Map<String,String> getParameterMap() {
        return this.parameters;
    }


    /**
     * The small icon associated with this filter.
     */
    private String smallIcon = null;

    /**
     * Returns the small icon associated with this filter.
     *
     * @return The small icon
     */
    public String getSmallIcon() {
        return this.smallIcon;
    }

    /**
     * Sets the small icon associated with this filter.
     *
     * @param smallIcon The new small icon
     */
    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }

    /**
     * The async-supported setting for this filter.
     */
    private String asyncSupported = null;

    /**
     * Returns the async-supported setting for this filter.
     *
     * @return The async-supported value
     */
    public String getAsyncSupported() {
        return asyncSupported;
    }

    /**
     * Sets the async-supported setting for this filter.
     *
     * @param asyncSupported The async-supported value
     */
    public void setAsyncSupported(String asyncSupported) {
        this.asyncSupported = asyncSupported;
        asyncSupportedBoolean = !("false".equalsIgnoreCase(asyncSupported));
    }

    /**
     * The async-supported boolean setting for this filter.
     */
    private boolean asyncSupportedBoolean = true;

    /**
     * Returns whether async processing is supported for this filter.
     *
     * @return True if async is supported, false otherwise
     */
    public boolean getAsyncSupportedBoolean() {
        return asyncSupportedBoolean;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * Add an initialization parameter to the set of parameters associated with this filter.
     *
     * @param name  The initialization parameter name
     * @param value The initialization parameter value
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
     * Render a String representation of this object.
     */
    @Override
    public String toString() {
        return "FilterDef[" + "filterName=" + this.filterName + ", filterClass=" + this.filterClass + ']';
    }


}
