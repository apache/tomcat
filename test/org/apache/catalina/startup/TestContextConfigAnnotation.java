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

import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.Servlet;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Loader;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.ContextConfig.JavaClassCacheEntry;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.descriptor.web.ServletDef;
import org.apache.tomcat.util.descriptor.web.WebXml;

/**
 * Check Servlet 3.0 Spec 8.2.3.3: Override annotation parameter from web.xml or
 * fragment.
 *
 * @author Peter Rossbach
 */
public class TestContextConfigAnnotation {

    @Test
    public void testAnnotation() throws Exception {
        WebXml webxml = new WebXml();
        Map<String,JavaClassCacheEntry> javaClassCache = new HashMap<>();
        ContextConfig config = new ContextConfig();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        Assert.assertTrue(pFile.exists());
        config.processAnnotationsFile(pFile, webxml, false, javaClassCache);
        ServletDef servletDef = webxml.getServlets().get("param");
        Assert.assertNotNull(servletDef);
        Assert.assertEquals("Hello", servletDef.getParameterMap().get("foo"));
        Assert.assertEquals("World!", servletDef.getParameterMap().get("bar"));
        Assert.assertEquals("param", webxml.getServletMappings().get(
                "/annotation/overwrite"));

        Assert.assertEquals("param", servletDef.getDescription());
        Assert.assertEquals("param", servletDef.getDisplayName());
        Assert.assertEquals("paramLarge.png", servletDef.getLargeIcon());
        Assert.assertEquals("paramSmall.png", servletDef.getSmallIcon());
        Assert.assertEquals(Boolean.FALSE, servletDef.getAsyncSupported());
        Assert.assertEquals(Integer.valueOf(0), servletDef.getLoadOnStartup());
        Assert.assertNull(servletDef.getEnabled());
        Assert.assertNull(servletDef.getJspFile());

    }

    @Test
    public void testOverwriteAnnotation() throws Exception {
        WebXml webxml = new WebXml();
        Map<String,JavaClassCacheEntry> javaClassCache = new HashMap<>();
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
        ContextConfig config = new ContextConfig();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        Assert.assertTrue(pFile.exists());
        config.processAnnotationsFile(pFile, webxml, false, javaClassCache);

        Assert.assertEquals(servletDef, webxml.getServlets().get("param"));

        Assert.assertEquals("tomcat", servletDef.getParameterMap().get("foo"));
        Assert.assertEquals("param", webxml.getServletMappings().get("/param"));
        // annotation mapping not added s. Servlet Spec 3.0 (Nov 2009)
        // 8.2.3.3.iv page 81
        Assert.assertNull(webxml.getServletMappings().get("/annotation/overwrite"));

        Assert.assertEquals("Description", servletDef.getDescription());
        Assert.assertEquals("DisplayName", servletDef.getDisplayName());
        Assert.assertEquals("LargeIcon", servletDef.getLargeIcon());
        Assert.assertEquals("SmallIcon", servletDef.getSmallIcon());
        Assert.assertEquals(Boolean.TRUE, servletDef.getAsyncSupported());
        Assert.assertEquals(Integer.valueOf(1), servletDef.getLoadOnStartup());
        Assert.assertNull(servletDef.getEnabled());
        Assert.assertNull(servletDef.getJspFile());
    }

    @Test
    public void testNoMapping() throws Exception {
        WebXml webxml = new WebXml();
        Map<String,JavaClassCacheEntry> javaClassCache = new HashMap<>();
        ContextConfig config = new ContextConfig();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/NoMappingParamServlet");
        Assert.assertTrue(pFile.exists());
        config.processAnnotationsFile(pFile, webxml, false, javaClassCache);
        ServletDef servletDef = webxml.getServlets().get("param1");
        Assert.assertNull(servletDef);

        webxml.addServletMapping("/param", "param1");
        config.processAnnotationsFile(pFile, webxml, false, javaClassCache);
        servletDef = webxml.getServlets().get("param1");
        Assert.assertNull(servletDef);

    }

    @Test
    public void testSetupWebXMLNoMapping() throws Exception {
        WebXml webxml = new WebXml();
        Map<String,JavaClassCacheEntry> javaClassCache = new HashMap<>();
        ServletDef servletDef = new ServletDef();
        servletDef.setServletName("param1");
        servletDef.setServletClass(
                "org.apache.catalina.startup.NoMappingParamServlet");
        servletDef.addInitParameter("foo", "tomcat");

        webxml.addServlet(servletDef);
        webxml.addServletMapping("/param", "param1");
        ContextConfig config = new ContextConfig();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/NoMappingParamServlet");
        Assert.assertTrue(pFile.exists());
        config.processAnnotationsFile(pFile, webxml, false, javaClassCache);
        Assert.assertEquals("tomcat", servletDef.getParameterMap().get("foo"));
        Assert.assertEquals("World!", servletDef.getParameterMap().get("bar"));
        ServletDef servletDef1 = webxml.getServlets().get("param1");
        Assert.assertNotNull(servletDef1);
        Assert.assertEquals(servletDef, servletDef1);
    }

    @Test
    public void testDuplicateMapping() throws Exception {
        WebXml webxml = new WebXml();
        Map<String,JavaClassCacheEntry> javaClassCache = new HashMap<>();
        ContextConfig config = new ContextConfig();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/DuplicateMappingParamServlet");
        Assert.assertTrue(pFile.exists());
        try {
            config.processAnnotationsFile(pFile, webxml, false, javaClassCache);
            Assert.fail();
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        ServletDef servletDef = webxml.getServlets().get("param");
        Assert.assertNull(servletDef);
    }

    @Test
    public void testFilterMapping() throws Exception {
        WebXml webxml = new WebXml();
        Map<String,JavaClassCacheEntry> javaClassCache = new HashMap<>();
        ContextConfig config = new ContextConfig();
        File sFile = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        config.processAnnotationsFile(sFile, webxml, false, javaClassCache);
        File fFile = paramClassResource(
                "org/apache/catalina/startup/ParamFilter");
        config.processAnnotationsFile(fFile, webxml, false, javaClassCache);
        FilterDef fdef = webxml.getFilters().get("paramFilter");
        Assert.assertNotNull(fdef);
        Assert.assertEquals("Servlet says: ",fdef.getParameterMap().get("message"));
    }

    @Test
    public void testOverwriteFilterMapping() throws Exception {
        WebXml webxml = new WebXml();
        Map<String,JavaClassCacheEntry> javaClassCache = new HashMap<>();
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
        filterMap.addURLPatternDecoded("/param1");
        filterMap.setFilterName("paramFilter");
        webxml.addFilterMapping(filterMap);

        ContextConfig config = new ContextConfig();
        File sFile = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        config.processAnnotationsFile(sFile, webxml, false, javaClassCache);
        File fFile = paramClassResource(
                "org/apache/catalina/startup/ParamFilter");
        config.processAnnotationsFile(fFile, webxml, false, javaClassCache);
        FilterDef fdef = webxml.getFilters().get("paramFilter");
        Assert.assertNotNull(fdef);
        Assert.assertEquals(filterDef,fdef);
        Assert.assertEquals("tomcat",fdef.getParameterMap().get("message"));
        Set<FilterMap> filterMappings = webxml.getFilterMappings();
        Assert.assertTrue(filterMappings.contains(filterMap));
        // annotation mapping not added s. Servlet Spec 3.0 (Nov 2009)
        // 8.2.3.3.vi page 81
        String[] urlPatterns = filterMap.getURLPatterns();
        Assert.assertNotNull(urlPatterns);
        Assert.assertEquals(1,urlPatterns.length);
        Assert.assertEquals("/param1",urlPatterns[0]);

        // check simple Parameter
        Assert.assertEquals("Description", fdef.getDescription());
        Assert.assertEquals("DisplayName", fdef.getDisplayName());
        Assert.assertEquals("LargeIcon", fdef.getLargeIcon());
        Assert.assertEquals("SmallIcon", fdef.getSmallIcon());
        // FIXME: Strange why servletDef is Boolean and FilterDef is String?
        Assert.assertEquals("true", fdef.getAsyncSupported());

        String[] dis = filterMap.getDispatcherNames();
        Assert.assertEquals(2, dis.length);
        Assert.assertEquals(DispatcherType.ERROR.toString(),dis[0]);
        Assert.assertEquals(DispatcherType.ASYNC.toString(),dis[1]);

    }

    @Test
    public void testDuplicateFilterMapping() throws Exception {
        WebXml webxml = new WebXml();
        Map<String,JavaClassCacheEntry> javaClassCache = new HashMap<>();
        ContextConfig config = new ContextConfig();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/DuplicateMappingParamFilter");
        Assert.assertTrue(pFile.exists());
        try {
            config.processAnnotationsFile(pFile, webxml, false, javaClassCache);
            Assert.fail();
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        FilterDef filterDef = webxml.getFilters().get("paramD");
        Assert.assertNull(filterDef);
    }

    @Test
    public void testCheckHandleTypes() throws Exception {
        Map<String,JavaClassCacheEntry> javaClassCache = new HashMap<>();
        ContextConfig config = new ContextConfig();
        config.handlesTypesAnnotations = true;
        config.handlesTypesNonAnnotations = true;

        // Need a Context, Loader and ClassLoader for checkHandleTypes
        StandardContext context = new StandardContext();
        context.setLoader(new TesterLoader());
        config.context = context;

        // Add an SCI that has no interest in any type
        SCI sciNone = new SCI();
        config.initializerClassMap.put(sciNone, new HashSet<Class<?>>());

        // Add an SCI with an interest in Servlets
        SCI sciServlet = new SCI();
        config.initializerClassMap.put(sciServlet, new HashSet<Class<?>>());
        config.typeInitializerMap.put(Servlet.class,
                new HashSet<ServletContainerInitializer>());
        config.typeInitializerMap.get(Servlet.class).add(sciServlet);

        // Add an SCI with an interest in Objects - i.e. everything
        SCI sciObject = new SCI();
        config.initializerClassMap.put(sciObject, new HashSet<Class<?>>());
        config.typeInitializerMap.put(Object.class,
                new HashSet<ServletContainerInitializer>());
        config.typeInitializerMap.get(Object.class).add(sciObject);

        // Scan Servlet, Filter, Servlet, Listener
        WebXml ignore = new WebXml();
        File file = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        config.processAnnotationsFile(file, ignore, false, javaClassCache);
        file = paramClassResource("org/apache/catalina/startup/ParamFilter");
        config.processAnnotationsFile(file, ignore, false, javaClassCache);
        file = paramClassResource("org/apache/catalina/startup/TesterServlet");
        config.processAnnotationsFile(file, ignore, false, javaClassCache);
        file = paramClassResource("org/apache/catalina/startup/TestListener");
        config.processAnnotationsFile(file, ignore, false, javaClassCache);

        // Check right number of classes were noted to be handled
        Assert.assertEquals(0, config.initializerClassMap.get(sciNone).size());
        Assert.assertEquals(2, config.initializerClassMap.get(sciServlet).size());
        Assert.assertEquals(4, config.initializerClassMap.get(sciObject).size());
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
     * @throws URISyntaxException
     */
    private File paramClassResource(String className) throws URISyntaxException {
        URL url = getClass().getClassLoader().getResource(className + ".class");
        Assert.assertNotNull(url);

        File file = new File(url.toURI());
        return file;
    }
}
