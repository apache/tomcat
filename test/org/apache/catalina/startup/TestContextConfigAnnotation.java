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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.Servlet;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Loader;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.ContextConfig.JavaClassCacheEntry;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.descriptor.web.ServletDef;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.junit.Before;
import org.junit.Test;

/**
 * Check Servlet 3.0 Spec 8.2.3.3: Override annotation parameter from web.xml or
 * fragment.
 *
 * @author Peter Rossbach
 * @author Simon Wang
 */
public class TestContextConfigAnnotation {
    private AnnotationScanner annotationScanner;
    
    @Before
    public void setup() {
        final Map<ServletContainerInitializer, Set<Class<?>>> initializerClassMap = new LinkedHashMap<ServletContainerInitializer, Set<Class<?>>>();
        final Map<Class<?>, Set<ServletContainerInitializer>> typeInitializerMap = new HashMap<Class<?>, Set<ServletContainerInitializer>>();
        final Map<String, JavaClassCacheEntry> javaClassCache = new HashMap<String, JavaClassCacheEntry>();

        annotationScanner = new AnnotationScanner(initializerClassMap,
                javaClassCache, typeInitializerMap, false, false, null);
    }
    
    @Test
    public void testAnnotation() throws Exception {
        WebXml webxml = new WebXml();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        assertTrue(pFile.exists());
        annotationScanner.processAnnotationsFile(pFile, webxml, false);
        ServletDef servletDef = webxml.getServlets().get("param");
        assertNotNull(servletDef);
        assertEquals("Hello", servletDef.getParameterMap().get("foo"));
        assertEquals("World!", servletDef.getParameterMap().get("bar"));
        assertEquals("param", webxml.getServletMappings().get(
                "/annotation/overwrite"));

        assertEquals("param", servletDef.getDescription());
        assertEquals("param", servletDef.getDisplayName());
        assertEquals("paramLarge.png", servletDef.getLargeIcon());
        assertEquals("paramSmall.png", servletDef.getSmallIcon());
        assertEquals(Boolean.FALSE, servletDef.getAsyncSupported());
        assertEquals(Integer.valueOf(0), servletDef.getLoadOnStartup());
        assertNull(servletDef.getEnabled());
        assertNull(servletDef.getJspFile());

    }

    @Test
    public void testOverwriteAnnotation() throws Exception {
        WebXml webxml = new WebXml();
        ServletDef servletDef = new ServletDef();
        servletDef.setServletName("param");
        servletDef.setServletClass("org.apache.catalina.startup.ParamServlet");
        servletDef.addInitParameter("foo", "tomcat");
        servletDef.setDescription("Description");
        servletDef.setDisplayName("DisplayName");
        servletDef.setLargeIcon("LargeIcon");
        servletDef.setSmallIcon("SmallIcon");
        servletDef.setAsyncSupported("true");
        servletDef.setLoadOnStartup("1");

        webxml.addServlet(servletDef);
        webxml.addServletMapping("/param", "param");
        File pFile = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        assertTrue(pFile.exists());
        annotationScanner.processAnnotationsFile(pFile, webxml, false);

        assertEquals(servletDef, webxml.getServlets().get("param"));

        assertEquals("tomcat", servletDef.getParameterMap().get("foo"));
        assertEquals("param", webxml.getServletMappings().get("/param"));
        // annotation mapping not added s. Servlet Spec 3.0 (Nov 2009)
        // 8.2.3.3.iv page 81
        assertNull(webxml.getServletMappings().get("/annotation/overwrite"));

        assertEquals("Description", servletDef.getDescription());
        assertEquals("DisplayName", servletDef.getDisplayName());
        assertEquals("LargeIcon", servletDef.getLargeIcon());
        assertEquals("SmallIcon", servletDef.getSmallIcon());
        assertEquals(Boolean.TRUE, servletDef.getAsyncSupported());
        assertEquals(Integer.valueOf(1), servletDef.getLoadOnStartup());
        assertNull(servletDef.getEnabled());
        assertNull(servletDef.getJspFile());
    }

    @Test
    public void testNoMapping() throws Exception {
        WebXml webxml = new WebXml();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/NoMappingParamServlet");
        assertTrue(pFile.exists());
        annotationScanner.processAnnotationsFile(pFile, webxml, false);
        ServletDef servletDef = webxml.getServlets().get("param1");
        assertNull(servletDef);

        webxml.addServletMapping("/param", "param1");
        annotationScanner.processAnnotationsFile(pFile, webxml, false);
        servletDef = webxml.getServlets().get("param1");
        assertNull(servletDef);

    }

    @Test
    public void testSetupWebXMLNoMapping() throws Exception {
        WebXml webxml = new WebXml();
        ServletDef servletDef = new ServletDef();
        servletDef.setServletName("param1");
        servletDef.setServletClass(
                "org.apache.catalina.startup.NoMappingParamServlet");
        servletDef.addInitParameter("foo", "tomcat");

        webxml.addServlet(servletDef);
        webxml.addServletMapping("/param", "param1");
        File pFile = paramClassResource(
                "org/apache/catalina/startup/NoMappingParamServlet");
        assertTrue(pFile.exists());
        annotationScanner.processAnnotationsFile(pFile, webxml, false);
        assertEquals("tomcat", servletDef.getParameterMap().get("foo"));
        assertEquals("World!", servletDef.getParameterMap().get("bar"));
        ServletDef servletDef1 = webxml.getServlets().get("param1");
        assertNotNull(servletDef1);
        assertEquals(servletDef, servletDef1);
    }

    @Test
    public void testDuplicateMapping() throws Exception {
        WebXml webxml = new WebXml();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/DuplicateMappingParamServlet");
        assertTrue(pFile.exists());
        try {
            annotationScanner.processAnnotationsFile(pFile, webxml, false);
            fail();
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        ServletDef servletDef = webxml.getServlets().get("param");
        assertNull(servletDef);
    }

    @Test
    public void testFilterMapping() throws Exception {
        WebXml webxml = new WebXml();
        File sFile = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        annotationScanner.processAnnotationsFile(sFile, webxml, false);
        File fFile = paramClassResource(
                "org/apache/catalina/startup/ParamFilter");
        annotationScanner.processAnnotationsFile(fFile, webxml, false);
        FilterDef fdef = webxml.getFilters().get("paramFilter");
        assertNotNull(fdef);
        assertEquals("Servlet says: ",fdef.getParameterMap().get("message"));
    }

    @Test
    public void testOverwriteFilterMapping() throws Exception {
        WebXml webxml = new WebXml();
        FilterDef filterDef = new FilterDef();
        filterDef.setFilterName("paramFilter");
        filterDef.setFilterClass("org.apache.catalina.startup.ParamFilter");
        filterDef.addInitParameter("message", "tomcat");
        filterDef.setDescription("Description");
        filterDef.setDisplayName("DisplayName");
        filterDef.setLargeIcon("LargeIcon");
        filterDef.setSmallIcon("SmallIcon");
        filterDef.setAsyncSupported("true");


        webxml.addFilter(filterDef);
        FilterMap filterMap = new FilterMap();
        filterMap.addURLPattern("/param1");
        filterMap.setFilterName("paramFilter");
        webxml.addFilterMapping(filterMap);

        File sFile = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        annotationScanner.processAnnotationsFile(sFile, webxml, false);
        File fFile = paramClassResource(
                "org/apache/catalina/startup/ParamFilter");
        annotationScanner.processAnnotationsFile(fFile, webxml, false);
        FilterDef fdef = webxml.getFilters().get("paramFilter");
        assertNotNull(fdef);
        assertEquals(filterDef,fdef);
        assertEquals("tomcat",fdef.getParameterMap().get("message"));
        Set<FilterMap> filterMappings = webxml.getFilterMappings();
        assertTrue(filterMappings.contains(filterMap));
        // annotation mapping not added s. Servlet Spec 3.0 (Nov 2009)
        // 8.2.3.3.vi page 81
        String[] urlPatterns = filterMap.getURLPatterns();
        assertNotNull(urlPatterns);
        assertEquals(1,urlPatterns.length);
        assertEquals("/param1",urlPatterns[0]);

        // check simple Parameter
        assertEquals("Description", fdef.getDescription());
        assertEquals("DisplayName", fdef.getDisplayName());
        assertEquals("LargeIcon", fdef.getLargeIcon());
        assertEquals("SmallIcon", fdef.getSmallIcon());
        // FIXME: Strange why servletDef is Boolean and FilterDef is String?
        assertEquals("true", fdef.getAsyncSupported());

        String[] dis = filterMap.getDispatcherNames();
        assertEquals(2, dis.length);
        assertEquals(DispatcherType.ERROR.toString(),dis[0]);
        assertEquals(DispatcherType.ASYNC.toString(),dis[1]);

    }

    @Test
    public void testDuplicateFilterMapping() throws Exception {
        WebXml webxml = new WebXml();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/DuplicateMappingParamFilter");
        assertTrue(pFile.exists());
        try {
            annotationScanner.processAnnotationsFile(pFile, webxml, false);
            fail();
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        FilterDef filterDef = webxml.getFilters().get("paramD");
        assertNull(filterDef);
    }

    @Test
    public void testCheckHandleTypes() throws Exception {
        annotationScanner.handlesTypesAnnotations = true;
        annotationScanner.handlesTypesNonAnnotations = true;

        // Need a Context, Loader and ClassLoader for checkHandleTypes
        StandardContext context = new StandardContext();
        context.setLoader(new TesterLoader());
        annotationScanner.context = context;

        // Add an SCI that has no interest in any type
        SCI sciNone = new SCI();
        annotationScanner.initializerClassMap.put(sciNone, new HashSet<Class<?>>());

        // Add an SCI with an interest in Servlets
        SCI sciServlet = new SCI();
        annotationScanner.initializerClassMap.put(sciServlet, new HashSet<Class<?>>());
        annotationScanner.typeInitializerMap.put(Servlet.class,
                new HashSet<ServletContainerInitializer>());
        
        HashSet<ServletContainerInitializer> typeInitializer;
        typeInitializer = (HashSet<ServletContainerInitializer>) annotationScanner.typeInitializerMap.get(Servlet.class);
        typeInitializer.add(sciServlet);

        // Add an SCI with an interest in Objects - i.e. everything
        SCI sciObject = new SCI();
        annotationScanner.initializerClassMap.put(sciObject, new HashSet<Class<?>>());
        annotationScanner.typeInitializerMap.put(Object.class,
                new HashSet<ServletContainerInitializer>());
        typeInitializer = (HashSet<ServletContainerInitializer>) annotationScanner.typeInitializerMap.get(Object.class);
        typeInitializer.add(sciObject);

        // Scan Servlet, Filter, Servlet, Listener
        WebXml ignore = new WebXml();
        File file = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        annotationScanner.processAnnotationsFile(file, ignore, false);
        file = paramClassResource("org/apache/catalina/startup/ParamFilter");
        annotationScanner.processAnnotationsFile(file, ignore, false);
        file = paramClassResource("org/apache/catalina/startup/TesterServlet");
        annotationScanner.processAnnotationsFile(file, ignore, false);
        file = paramClassResource("org/apache/catalina/startup/TestListener");
        annotationScanner.processAnnotationsFile(file, ignore, false);

        // Check right number of classes were noted to be handled
        assertEquals(0, ((Set<Class<?>>)annotationScanner.initializerClassMap.get(sciNone)).size());
        assertEquals(2, ((Set<Class<?>>)annotationScanner.initializerClassMap.get(sciServlet)).size());
        assertEquals(4, ((Set<Class<?>>)annotationScanner.initializerClassMap.get(sciObject)).size());
    }

    private static final class SCI implements ServletContainerInitializer {
        @Override
        public void onStartup(Set<Class<?>> c, ServletContext ctx)
                throws ServletException {
            // NO-OP. Just need a class that implements SCI.
        }
    }

    private static final class TesterLoader implements Loader {

        @Override
        public void backgroundProcess() {}
        @Override
        public ClassLoader getClassLoader() {
            return this.getClass().getClassLoader();
        }
        @Override
        public Context getContext() { return null; }
        @Override
        public void setContext(Context context) {}
        @Override
        public boolean getDelegate() { return false; }
        @Override
        public void setDelegate(boolean delegate) {}
        @Override
        public boolean getReloadable() { return false; }
        @Override
        public void setReloadable(boolean reloadable) {}
        @Override
        public void addPropertyChangeListener(PropertyChangeListener l) {
        }
        @Override
        public boolean modified() { return false; }
        @Override
        public void removePropertyChangeListener(PropertyChangeListener l) {}
    }

    /**
     * Find compiled test class
     *
     * @param className
     * @return File Resource
     */
    private File paramClassResource(String className) {
        URL url = getClass().getClassLoader().getResource(className + ".class");
        assertNotNull(url);

        File file = new File(url.getPath());
        return file;
    }
}