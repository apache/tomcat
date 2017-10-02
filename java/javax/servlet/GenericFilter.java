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

import java.io.Serializable;
import java.util.Enumeration;

public abstract class GenericFilter implements Filter, FilterConfig, Serializable {

    private static final long serialVersionUID = 1L;

    private volatile FilterConfig filterConfig;


    @Override
    public String getInitParameter(String name) {
        return getFilterConfig().getInitParameter(name);
    }


    /**
     * This method returns the parameter's value if it exists, or defaultValue if not.
     *
     * @param name - The parameter's name
     * @param defaultValue - The default value to return if the parameter does not exist
     * @return The parameter's value or the default value if the parameter does not exist
     */
    public String getInitParameter(String name, String defaultValue){

        String value = getInitParameter(name);

        if (value == null)
            return defaultValue;

        return value;
    }


    @Override
    public Enumeration<String> getInitParameterNames() {
        return getFilterConfig().getInitParameterNames();
    }


    /**
     * Obtain the FilterConfig used to initialise this Filter instance.
     *
     * @return The config previously passed to the {@link #init(FilterConfig)}
     *         method
     */
    public FilterConfig getFilterConfig() {
        return filterConfig;
    }


    @Override
    public ServletContext getServletContext() {
        return getFilterConfig().getServletContext();
    }


    /**
     * This method stores filterConfig in a private variable and then calls init() which can be
     * overridden in sub-classes to provide ad-hoc implementation.
     *
     * @param filterConfig The configuration information associated with the
     *                     filter instance being initialised
     *
     * @throws ServletException
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig  = filterConfig;
        init();
    }


    /**
     * Convenience method for sub-classes to save them having to call
     * <code>super.init(config)</code>. This is a NO-OP by default.
     *
     * This method is called by init(config) after the config is set to this.filterConfig.
     *
     * @throws ServletException If an exception occurs that interrupts the
     *         Filter's normal operation
     */
    public void init() throws ServletException {
        // NO-OP
    }


    @Override
    public String getFilterName() {
        return getFilterConfig().getFilterName();
    }
}
