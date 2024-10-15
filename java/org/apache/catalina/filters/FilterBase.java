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
package org.apache.catalina.filters;

import java.util.Enumeration;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * Base class for filters that provides generic initialisation and a simple no-op destruction.
 */
public abstract class FilterBase implements Filter {

    protected static final StringManager sm = StringManager.getManager(FilterBase.class);

    protected abstract Log getLogger();


    /**
     * Iterates over the configuration parameters and either logs a warning, or throws an exception for any parameter
     * that does not have a matching setter in this filter.
     *
     * @param filterConfig The configuration information associated with the filter instance being initialised
     *
     * @throws ServletException if {@link #isConfigProblemFatal()} returns {@code true} and a configured parameter does
     *                              not have a matching setter
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Enumeration<String> paramNames = filterConfig.getInitParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            if (!IntrospectionUtils.setProperty(this, paramName, filterConfig.getInitParameter(paramName))) {
                String msg = sm.getString("filterbase.noSuchProperty", paramName, this.getClass().getName());
                if (isConfigProblemFatal()) {
                    throw new ServletException(msg);
                } else {
                    getLogger().warn(msg);
                }
            }
        }
    }

    /**
     * Determines if an exception when calling a setter or an unknown configuration attribute triggers the failure of
     * the this filter which in turn will prevent the web application from starting.
     *
     * @return <code>true</code> if a problem should trigger the failure of this filter, else <code>false</code>
     */
    protected boolean isConfigProblemFatal() {
        return false;
    }
}
