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
package javax.servlet;

import java.util.Collection;
import java.util.EnumSet;

/**
 * Interface through which a Filter may be further configured.
 *
 * @since Servlet 3.0
 */
public interface FilterRegistration extends Registration {

    /**
     * Add a mapping for this filter to one or more named Servlets.
     *
     * @param dispatcherTypes The dispatch types to which this filter should
     *                        apply
     * @param isMatchAfter    Should this filter be applied after any mappings
     *                        defined in the deployment descriptor
     *                        (<code>true</code>) or before?
     * @param servletNames    Requests mapped to these servlets will be
     *                        processed by this filter
     * @throws IllegalArgumentException if the list of servlet names is empty
     *                                  or null
     * @throws IllegalStateException if the associated ServletContext has
     *                               already been initialised
     */
    public void addMappingForServletNames(
            EnumSet<DispatcherType> dispatcherTypes,
            boolean isMatchAfter, String... servletNames);

    /**
     * Gets the currently available servlet name mappings of the Filter
     * represented by this FilterRegistration.
     *
     * @return a Collection of the Servlet name mappings
     */
    public Collection<String> getServletNameMappings();

    /**
     * Add a mapping for this filter to one or more URL patterns.
     *
     * @param dispatcherTypes The dispatch types to which this filter should
     *                        apply
     * @param isMatchAfter    Should this filter be applied after any mappings
     *                        defined in the deployment descriptor
     *                        (<code>true</code>) or before?
     * @param urlPatterns     The URL patterns to which this filter should be
     *                        applied
     * @throws IllegalArgumentException if the list of URL patterns is empty or
     *                                  null
     * @throws IllegalStateException if the associated ServletContext has
     *                               already been initialised
     */
    public void addMappingForUrlPatterns(
            EnumSet<DispatcherType> dispatcherTypes,
            boolean isMatchAfter, String... urlPatterns);

    /**
     * Gets the currently available URL pattern mappings of the Filter
     * represented by this FilterRegistration.
     *
     * @return a Collection of the URL pattern mappings
     */
    public Collection<String> getUrlPatternMappings();

    /**
     * Interface through which a Filter registered via one of the addFilter
     * methods on ServletContext may be further configured.
     */
    public static interface Dynamic extends FilterRegistration, Registration.Dynamic {
        // No additional methods
    }
}
