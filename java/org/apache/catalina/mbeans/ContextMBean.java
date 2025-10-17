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
package org.apache.catalina.mbeans;

import javax.management.MBeanException;

import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.ApplicationParameter;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

public class ContextMBean extends BaseCatalinaMBean<Context> {

    /**
     * Return the set of application parameters for this application.
     *
     * @return a string array with a representation of each parameter
     *
     * @throws MBeanException propagated from the managed resource access
     */
    public String[] findApplicationParameters() throws MBeanException {

        Context context = doGetManagedResource();

        ApplicationParameter[] params = context.findApplicationParameters();
        String[] stringParams = new String[params.length];
        for (int counter = 0; counter < params.length; counter++) {
            stringParams[counter] = params[counter].toString();
        }

        return stringParams;
    }


    /**
     * Return the security constraints for this web application. If there are none, a zero-length array is returned.
     *
     * @return a string array with a representation of each security constraint
     *
     * @throws MBeanException propagated from the managed resource access
     */
    public String[] findConstraints() throws MBeanException {

        Context context = doGetManagedResource();

        SecurityConstraint[] constraints = context.findConstraints();
        String[] stringConstraints = new String[constraints.length];
        for (int counter = 0; counter < constraints.length; counter++) {
            stringConstraints[counter] = constraints[counter].toString();
        }

        return stringConstraints;
    }


    /**
     * Return the error page entry for the specified HTTP error code, if any; otherwise return <code>null</code>.
     *
     * @param errorCode Error code to look up
     *
     * @return a string representation of the error page
     *
     * @throws MBeanException propagated from the managed resource access
     */
    public String findErrorPage(int errorCode) throws MBeanException {
        Context context = doGetManagedResource();
        ErrorPage errorPage = context.findErrorPage(errorCode);
        if (errorPage != null) {
            return errorPage.toString();
        } else {
            return null;
        }
    }


    /**
     * Return the error page entry for the specified Java exception type, if any; otherwise return <code>null</code>.
     *
     * @param exceptionType Exception type to look up
     *
     * @return a string representation of the error page
     *
     * @throws MBeanException propagated from the managed resource access
     */
    public String findErrorPage(Throwable exceptionType) throws MBeanException {
        Context context = doGetManagedResource();
        ErrorPage errorPage = context.findErrorPage(exceptionType);
        if (errorPage != null) {
            return errorPage.toString();
        } else {
            return null;
        }
    }


    /**
     * Return the set of defined error pages for all specified error codes and exception types.
     *
     * @return a string array with a representation of each error page
     *
     * @throws MBeanException propagated from the managed resource access
     */
    public String[] findErrorPages() throws MBeanException {

        Context context = doGetManagedResource();

        ErrorPage[] pages = context.findErrorPages();
        String[] stringPages = new String[pages.length];
        for (int counter = 0; counter < pages.length; counter++) {
            stringPages[counter] = pages[counter].toString();
        }

        return stringPages;
    }


    /**
     * Return the filter definition for the specified filter name, if any; otherwise return <code>null</code>.
     *
     * @param name Filter name to look up
     *
     * @return a string representation of the filter definition
     *
     * @throws MBeanException propagated from the managed resource access
     */
    public String findFilterDef(String name) throws MBeanException {

        Context context = doGetManagedResource();

        FilterDef filterDef = context.findFilterDef(name);
        if (filterDef != null) {
            return filterDef.toString();
        } else {
            return null;
        }
    }


    /**
     * Return the set of defined filters for this Context.
     *
     * @return a string array with a representation of all the filter definitions
     *
     * @throws MBeanException propagated from the managed resource access
     */
    public String[] findFilterDefs() throws MBeanException {

        Context context = doGetManagedResource();

        FilterDef[] filterDefs = context.findFilterDefs();
        String[] stringFilters = new String[filterDefs.length];
        for (int counter = 0; counter < filterDefs.length; counter++) {
            stringFilters[counter] = filterDefs[counter].toString();
        }

        return stringFilters;
    }


    /**
     * Return the set of filter mappings for this Context.
     *
     * @return a string array with a representation of all the filter mappings
     *
     * @throws MBeanException propagated from the managed resource access
     */
    public String[] findFilterMaps() throws MBeanException {

        Context context = doGetManagedResource();

        FilterMap[] maps = context.findFilterMaps();
        String[] stringMaps = new String[maps.length];
        for (int counter = 0; counter < maps.length; counter++) {
            stringMaps[counter] = maps[counter].toString();
        }

        return stringMaps;
    }
}
