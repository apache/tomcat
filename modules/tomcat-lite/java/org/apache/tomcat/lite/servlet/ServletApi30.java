/*
 */
package org.apache.tomcat.lite.servlet;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.Part;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.integration.ObjectManager;
import org.apache.tomcat.lite.http.HttpRequest;

public class ServletApi30  extends ServletApi {
    public ServletContextImpl newContext() {
        return new ServletContextImpl() {
            protected void initEngineDefaults() throws ServletException {
                super.initEngineDefaults();
                setAttribute(InstanceManager.class.getName(),
                        new LiteInstanceManager(getObjectManager()));
            }
            @Override
            public Dynamic addFilter(String filterName, String className) {
                FilterConfigImpl fc = new FilterConfigImpl(this);
                fc.setData(filterName, null, new HashMap());
                fc.setData(filterName, className, new HashMap());
                filters.put(filterName, fc);
                return new DynamicFilterRegistration(fc);
            }

            @Override
            public Dynamic addFilter(String filterName, Filter filter) {
                FilterConfigImpl fc = new FilterConfigImpl(this);
                fc.setData(filterName, null, new HashMap());
                fc.setFilter(filter);
                filters.put(filterName, fc);
                return new DynamicFilterRegistration(fc);
            }

            @Override
            public Dynamic addFilter(String filterName,
                    Class<? extends Filter> filterClass) {
                FilterConfigImpl fc = new FilterConfigImpl(this);
                fc.setData(filterName, null, new HashMap());
                fc.setFilterClass(filterClass);
                filters.put(filterName, fc);
                return new DynamicFilterRegistration(fc);
            }

            @Override
            public javax.servlet.ServletRegistration.Dynamic addServlet(
                    String servletName, String className) {
                return null;
            }

            @Override
            public javax.servlet.ServletRegistration.Dynamic addServlet(
                    String servletName, Servlet servlet) {
                return null;
            }

            @Override
            public javax.servlet.ServletRegistration.Dynamic addServlet(
                    String servletName, Class<? extends Servlet> servletClass) {
                return null;
            }


            @Override
            public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
                return null;
            }


            @Override
            public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
                return null;
            }

            @Override
            public FilterRegistration getFilterRegistration(String filterName) {
                return null;
            }

            @Override
            public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
                return null;
            }

            @Override
            public JspConfigDescriptor getJspConfigDescriptor() {
                return null;
            }

            @Override
            public ServletRegistration getServletRegistration(String servletName) {
                return null;
            }

            @Override
            public Map<String, ? extends ServletRegistration> getServletRegistrations() {
                return null;
            }

            @Override
            public SessionCookieConfig getSessionCookieConfig() {
                return null;
            }

            @Override
            public void setSessionTrackingModes(
                    EnumSet<SessionTrackingMode> sessionTrackingModes)
                    throws IllegalStateException, IllegalArgumentException {
            }
            
            public int getMajorVersion() {
                return 3;
            }
            
            public int getMinorVersion() {
                return 0;
            }
            
        };
    }

    public ServletRequestImpl newRequest(HttpRequest req) {
        return new ServletRequestImpl(req) {

            @Override
            public Part getPart(String name) {
                return null;
            }

            @Override
            public Collection<Part> getParts() throws IOException,
                    ServletException {
                return null;
            }

            @Override
            public AsyncContext getAsyncContext() {
                return null;
            }

            @Override
            public DispatcherType getDispatcherType() {
                return null;
            }

            @Override
            public AsyncContext startAsync() {
                return null;
            }

            @Override
            public AsyncContext startAsync(ServletRequest servletRequest,
                    ServletResponse servletResponse) {
                return null;
            }
            
        };
    }
    
    private final class LiteInstanceManager implements InstanceManager {
        private ObjectManager om;

        public LiteInstanceManager(ObjectManager objectManager) {
            this.om = objectManager;
        }

        @Override
        public void destroyInstance(Object o)
                throws IllegalAccessException,
                InvocationTargetException {
        }

        @Override
        public Object newInstance(String className)
                throws IllegalAccessException,
                InvocationTargetException, NamingException,
                InstantiationException,
                ClassNotFoundException {
            return om.get(className);
        }

        @Override
        public Object newInstance(String fqcn,
                ClassLoader classLoader)
                throws IllegalAccessException,
                InvocationTargetException, NamingException,
                InstantiationException,
                ClassNotFoundException {
            return om.get(fqcn);
        }

        @Override
        public void newInstance(Object o)
                throws IllegalAccessException,
                InvocationTargetException, NamingException {
            om.bind(o.getClass().getName(), o);
        }
    }

    public static class DynamicFilterRegistration implements Dynamic {
        FilterConfigImpl fcfg;
        
        public DynamicFilterRegistration(
                org.apache.tomcat.lite.servlet.FilterConfigImpl fc) {
        }

        @Override
        public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes,
                                              boolean isMatchAfter,
                                              String... servletNames) {
            if (fcfg.ctx.startDone) {
                // Use the context method instead of the servlet API to 
                // add mappings after context init.
                throw new IllegalStateException();
            }
            ArrayList<String> dispatchers = new ArrayList<String>();
            for (DispatcherType dt: dispatcherTypes) {
                dispatchers.add(dt.name());
            }
            for (String servletName: servletNames) {
                fcfg.ctx.getFilterMapper().addMapping(fcfg.getFilterName(),
                        null, servletName, (String[]) dispatchers.toArray(), isMatchAfter);
            }
        }

        @Override
        public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes,
                                             boolean isMatchAfter,
                                             String... urlPatterns) {
            if (fcfg.ctx.startDone) {
                // Use the context method instead of the servlet API to 
                // add mappings after context init.
                throw new IllegalStateException();
            }
            ArrayList<String> dispatchers = new ArrayList<String>();
            for (DispatcherType dt: dispatcherTypes) {
                dispatchers.add(dt.name());
            }
            for (String url: urlPatterns) {
                fcfg.ctx.getFilterMapper().addMapping(fcfg.getFilterName(),
                        url, null, (String[]) dispatchers.toArray(), isMatchAfter);
            }
        }

        @Override
        public boolean setInitParameter(String name, String value)
                throws IllegalArgumentException, IllegalStateException {
            return fcfg.ctx.setInitParameter(fcfg.ctx, fcfg.initParams, 
                    name, value);
        }

        @Override
        public Set<String> setInitParameters(Map<String, String> initParameters)
                throws IllegalArgumentException, IllegalStateException {
            return ServletContextImpl.setInitParameters(fcfg.ctx, fcfg.initParams, 
                    initParameters);
        }

        @Override
        public void setAsyncSupported(boolean isAsyncSupported)
                throws IllegalStateException {
            fcfg.asyncSupported = isAsyncSupported;
        }

        @Override
        public Collection<String> getServletNameMappings() {
            return null;
        }

        @Override
        public Collection<String> getUrlPatternMappings() {
            return null;
        }

        @Override
        public String getClassName() {
            return null;
        }

        @Override
        public String getInitParameter(String name) {
            return null;
        }

        @Override
        public Map<String, String> getInitParameters() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }
    }

    class ServletDynamicRegistration implements Dynamic {
        ServletConfigImpl sc;
        
        @Override
        public void setAsyncSupported(boolean isAsyncSupported)
                throws IllegalStateException {
            sc.asyncSupported = isAsyncSupported;
        }

        @Override
        public boolean setInitParameter(String name, String value)
                throws IllegalArgumentException, IllegalStateException {
            return sc.setInitParameter(name, value);
        }

        @Override
        public Set<String> setInitParameters(Map<String, String> initParameters)
                throws IllegalArgumentException, IllegalStateException {
            return setInitParameters(initParameters);
        }

        @Override
        public void addMappingForServletNames(
                EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter,
                String... servletNames) {
        }

        @Override
        public void addMappingForUrlPatterns(
                EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter,
                String... urlPatterns) {
        }

        @Override
        public Collection<String> getServletNameMappings() {
            return null;
        }

        @Override
        public Collection<String> getUrlPatternMappings() {
            return null;
        }

        @Override
        public String getClassName() {
            return null;
        }

        @Override
        public String getInitParameter(String name) {
            return null;
        }

        @Override
        public Map<String, String> getInitParameters() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }
        
    }
    
}
