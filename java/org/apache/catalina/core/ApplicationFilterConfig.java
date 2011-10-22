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


package org.apache.catalina.core;


import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.management.ObjectName;
import javax.naming.NamingException;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.Enumerator;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;


/**
 * Implementation of a <code>javax.servlet.FilterConfig</code> useful in
 * managing the filter instances instantiated when a web application
 * is first started.
 *
 * @author Craig R. McClanahan
 * @version $Id$
 */

public final class ApplicationFilterConfig implements FilterConfig, Serializable {

    private static final long serialVersionUID = 1L;

    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    private static final org.apache.juli.logging.Log log =
        LogFactory.getLog(ApplicationFilterConfig.class);

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new ApplicationFilterConfig for the specified filter
     * definition.
     *
     * @param context The context with which we are associated
     * @param filterDef Filter definition for which a FilterConfig is to be
     *  constructed
     *
     * @exception ClassCastException if the specified class does not implement
     *  the <code>javax.servlet.Filter</code> interface
     * @exception ClassNotFoundException if the filter class cannot be found
     * @exception IllegalAccessException if the filter class cannot be
     *  publicly instantiated
     * @exception InstantiationException if an exception occurs while
     *  instantiating the filter object
     * @exception ServletException if thrown by the filter's init() method
     * @throws NamingException
     * @throws InvocationTargetException
     */
    ApplicationFilterConfig(Context context, FilterDef filterDef)
        throws ClassCastException, ClassNotFoundException,
               IllegalAccessException, InstantiationException,
               ServletException, InvocationTargetException, NamingException {

        super();

        this.context = context;
        setFilterDef(filterDef);
        if (filterDef.getFilter() != null) {
            this.filter = filterDef.getFilter();
            getInstanceManager().newInstance(filter);
            initFilter();
        }
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The Context with which we are associated.
     */
    private transient Context context = null;


    /**
     * The application Filter we are configured for.
     */
    private transient Filter filter = null;


    /**
     * The <code>FilterDef</code> that defines our associated Filter.
     */
    private FilterDef filterDef = null;

    /**
     * the InstanceManager used to create and destroy filter instances.
     */
    private transient InstanceManager instanceManager;

    /**
     * JMX registration name
     */
    private ObjectName oname;

    // --------------------------------------------------- FilterConfig Methods


    /**
     * Return the name of the filter we are configuring.
     */
    @Override
    public String getFilterName() {
        return (filterDef.getFilterName());
    }

    /**
     * Return the class of the filter we are configuring.
     */
    public String getFilterClass() {
        return filterDef.getFilterClass();
    }

    /**
     * Return a <code>String</code> containing the value of the named
     * initialization parameter, or <code>null</code> if the parameter
     * does not exist.
     *
     * @param name Name of the requested initialization parameter
     */
    @Override
    public String getInitParameter(String name) {

        Map<String,String> map = filterDef.getParameterMap();
        if (map == null) {
            return (null);
        }

        return map.get(name);

    }


    /**
     * Return an <code>Enumeration</code> of the names of the initialization
     * parameters for this Filter.
     */
    @Override
    public Enumeration<String> getInitParameterNames() {
        Map<String,String> map = filterDef.getParameterMap();

        if (map == null) {
            return (new Enumerator<String>(new ArrayList<String>()));
        }

        return new Enumerator<String>(map.keySet());

    }


    /**
     * Return the ServletContext of our associated web application.
     */
    @Override
    public ServletContext getServletContext() {

        return this.context.getServletContext();

    }


    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ApplicationFilterConfig[");
        sb.append("name=");
        sb.append(filterDef.getFilterName());
        sb.append(", filterClass=");
        sb.append(filterDef.getFilterClass());
        sb.append("]");
        return (sb.toString());

    }

    // --------------------------------------------------------- Public Methods

    public Map<String, String> getFilterInitParameterMap() {
        return Collections.unmodifiableMap(filterDef.getParameterMap());
    }

    // -------------------------------------------------------- Package Methods


    /**
     * Return the application Filter we are configured for.
     *
     * @exception ClassCastException if the specified class does not implement
     *  the <code>javax.servlet.Filter</code> interface
     * @exception ClassNotFoundException if the filter class cannot be found
     * @exception IllegalAccessException if the filter class cannot be
     *  publicly instantiated
     * @exception InstantiationException if an exception occurs while
     *  instantiating the filter object
     * @exception ServletException if thrown by the filter's init() method
     * @throws NamingException
     * @throws InvocationTargetException
     */
    Filter getFilter() throws ClassCastException, ClassNotFoundException,
        IllegalAccessException, InstantiationException, ServletException,
        InvocationTargetException, NamingException {

        // Return the existing filter instance, if any
        if (this.filter != null)
            return (this.filter);

        // Identify the class loader we will be using
        String filterClass = filterDef.getFilterClass();
        this.filter = (Filter) getInstanceManager().newInstance(filterClass);

        initFilter();

        return (this.filter);

    }

    private void initFilter() throws ServletException {
        if (context instanceof StandardContext &&
                context.getSwallowOutput()) {
            try {
                SystemLogHandler.startCapture();
                filter.init(this);
            } finally {
                String capturedlog = SystemLogHandler.stopCapture();
                if (capturedlog != null && capturedlog.length() > 0) {
                    getServletContext().log(capturedlog);
                }
            }
        } else {
            filter.init(this);
        }

        // Expose filter via JMX
        registerJMX();
    }

    /**
     * Return the filter definition we are configured for.
     */
    FilterDef getFilterDef() {

        return (this.filterDef);

    }

    /**
     * Release the Filter instance associated with this FilterConfig,
     * if there is one.
     */
    void release() {

        unregisterJMX();

        if (this.filter != null)
        {
            if (Globals.IS_SECURITY_ENABLED) {
                try {
                    SecurityUtil.doAsPrivilege("destroy", filter);
                } catch(java.lang.Exception ex){
                    context.getLogger().error("ApplicationFilterConfig.doAsPrivilege", ex);
                }
                SecurityUtil.remove(filter);
            } else {
                filter.destroy();
            }
            if (!context.getIgnoreAnnotations()) {
                try {
                    ((StandardContext) context).getInstanceManager().destroyInstance(this.filter);
                } catch (Exception e) {
                    context.getLogger().error("ApplicationFilterConfig.preDestroy", e);
                }
            }
        }
        this.filter = null;

     }


    /**
     * Set the filter definition we are configured for.  This has the side
     * effect of instantiating an instance of the corresponding filter class.
     *
     * @param filterDef The new filter definition
     *
     * @exception ClassCastException if the specified class does not implement
     *  the <code>javax.servlet.Filter</code> interface
     * @exception ClassNotFoundException if the filter class cannot be found
     * @exception IllegalAccessException if the filter class cannot be
     *  publicly instantiated
     * @exception InstantiationException if an exception occurs while
     *  instantiating the filter object
     * @exception ServletException if thrown by the filter's init() method
     * @throws NamingException
     * @throws InvocationTargetException
     */
    void setFilterDef(FilterDef filterDef)
        throws ClassCastException, ClassNotFoundException,
               IllegalAccessException, InstantiationException,
               ServletException, InvocationTargetException, NamingException {

        this.filterDef = filterDef;
        if (filterDef == null) {

            // Release any previously allocated filter instance
            if (this.filter != null){
                if (Globals.IS_SECURITY_ENABLED) {
                    try{
                        SecurityUtil.doAsPrivilege("destroy", filter);
                    } catch(java.lang.Exception ex){
                        context.getLogger().error("ApplicationFilterConfig.doAsPrivilege", ex);
                    }
                    SecurityUtil.remove(filter);
                } else {
                    filter.destroy();
                }
                if (!context.getIgnoreAnnotations()) {
                    try {
                        ((StandardContext) context).getInstanceManager().destroyInstance(this.filter);
                    } catch (Exception e) {
                        context.getLogger().error("ApplicationFilterConfig.preDestroy", e);
                    }
                }
            }
            this.filter = null;

        } else {
            // Allocate a new filter instance if necessary
            if (filterDef.getFilter() == null) {
                getFilter();
            }
        }

    }


    // -------------------------------------------------------- Private Methods

    private InstanceManager getInstanceManager() {
        if (instanceManager == null) {
            if (context instanceof StandardContext) {
                instanceManager = ((StandardContext)context).getInstanceManager();
            } else {
                instanceManager = new DefaultInstanceManager(null,
                        new HashMap<String, Map<String, String>>(),
                        context,
                        getClass().getClassLoader());
            }
        }
        return instanceManager;
    }

    private void registerJMX() {
        String parentName = context.getName();
        if (!parentName.startsWith("/")) {
            parentName = "/" + parentName;
        }

        String hostName = context.getParent().getName();
        hostName = (hostName == null) ? "DEFAULT" : hostName;

        // domain == engine name
        String domain = context.getParent().getParent().getName();

        String webMod = "//" + hostName + parentName;
        String onameStr = null;
        if (context instanceof StandardContext) {
            StandardContext standardContext = (StandardContext) context;
            onameStr = domain + ":j2eeType=Filter,name=" +
                 filterDef.getFilterName() + ",WebModule=" + webMod +
                 ",J2EEApplication=" +
                 standardContext.getJ2EEApplication() + ",J2EEServer=" +
                 standardContext.getJ2EEServer();
        } else {
            onameStr = domain + ":j2eeType=Filter,name=" +
                 filterDef.getFilterName() + ",WebModule=" + webMod;
        }
        try {
            oname = new ObjectName(onameStr);
            Registry.getRegistry(null, null).registerComponent(this, oname,
                    null);
        } catch (Exception ex) {
            log.info(sm.getString("applicationFilterConfig.jmxRegisterFail",
                    getFilterClass(), getFilterName()), ex);
        }
    }

    private void unregisterJMX() {
        // unregister this component
        if (oname != null) {
            try {
                Registry.getRegistry(null, null).unregisterComponent(oname);
                if (log.isDebugEnabled())
                    log.debug(sm.getString(
                            "applicationFilterConfig.jmxUnregister",
                            getFilterClass(), getFilterName()));
            } catch(Exception ex) {
                log.error(sm.getString(
                        "applicationFilterConfig.jmxUnregisterFail",
                        getFilterClass(), getFilterName()), ex);
            }
        }

    }
}
