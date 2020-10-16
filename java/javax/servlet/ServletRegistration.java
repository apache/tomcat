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
import java.util.Set;

/**
 * Interface through which a Servlet may be further configured.
 *
 * @since Servlet 3.0
 */
public interface ServletRegistration extends Registration {

    /**
     * Adds a servlet mapping with the given URL patterns for the Servlet
     * represented by this ServletRegistration. If any of the specified URL
     * patterns are already mapped to a different Servlet, no updates will
     * be performed.
     *
     * If this method is called multiple times, each successive call adds to
     * the effects of the former. The returned set is not backed by the
     * ServletRegistration object, so changes in the returned set are not
     * reflected in the ServletRegistration object, and vice-versa.
     *
     * @param urlPatterns The URL patterns that this Servlet should be mapped to
     * @return the (possibly empty) Set of URL patterns that are already mapped
     * to a different Servlet
     * @throws IllegalArgumentException if urlPattern is null or empty
     * @throws IllegalStateException if the associated ServletContext has
     *                                  already been initialised
     */
    public Set<String> addMapping(String... urlPatterns);

    /**
     * Gets the currently available mappings of the Servlet represented by this
     * ServletRegistration.
     *
     * If permitted, any changes to the returned Collection must not affect this
     * ServletRegistration.
     *
     * @return a (possibly empty) Collection of the currently available mappings
     * of the Servlet represented by this ServletRegistration
     */
    public Collection<String> getMappings();

    public String getRunAsRole();

    /**
     * Interface through which a Servlet registered via one of the addServlet
     * methods on ServletContext may be further configured.
     */
    public static interface Dynamic extends ServletRegistration, Registration.Dynamic {
        public void setLoadOnStartup(int loadOnStartup);
        public Set<String> setServletSecurity(ServletSecurityElement constraint);
        public void setMultipartConfig(MultipartConfigElement multipartConfig);
        public void setRunAsRole(String roleName);
    }
}
