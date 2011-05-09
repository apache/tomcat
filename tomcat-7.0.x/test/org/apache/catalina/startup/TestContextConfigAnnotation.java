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

import java.io.File;
import java.net.URL;
import java.util.Set;

import javax.servlet.DispatcherType;

import junit.framework.TestCase;

import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.ServletDef;
import org.apache.catalina.deploy.WebXml;

/**
 * Check Servlet 3.0 Spec 8.2.3.3: Override annotation parameter from web.xml or
 * fragment.
 * 
 * @author Peter Rossbach
 * @version $Revision$
 */
public class TestContextConfigAnnotation extends TestCase {

    public void testAnnotation() throws Exception {
        WebXml webxml = new WebXml();
        ContextConfig config = new ContextConfig();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        assertTrue(pFile.exists());
        config.processAnnotationsFile(pFile, webxml);
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
        ContextConfig config = new ContextConfig();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        assertTrue(pFile.exists());
        config.processAnnotationsFile(pFile, webxml);

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

    public void testNoMapping() throws Exception {
        WebXml webxml = new WebXml();
        ContextConfig config = new ContextConfig();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/NoMappingParamServlet");
        assertTrue(pFile.exists());
        config.processAnnotationsFile(pFile, webxml);
        ServletDef servletDef = webxml.getServlets().get("param1");
        assertNull(servletDef);

        webxml.addServletMapping("/param", "param1");
        config.processAnnotationsFile(pFile, webxml);
        servletDef = webxml.getServlets().get("param1");
        assertNull(servletDef);

    }

    public void testSetupWebXMLNoMapping() throws Exception {
        WebXml webxml = new WebXml();
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
        assertTrue(pFile.exists());
        config.processAnnotationsFile(pFile, webxml);
        assertEquals("tomcat", servletDef.getParameterMap().get("foo"));
        assertEquals("World!", servletDef.getParameterMap().get("bar"));
        ServletDef servletDef1 = webxml.getServlets().get("param1");
        assertNotNull(servletDef1);
        assertEquals(servletDef, servletDef1);
    }

    public void testDuplicateMapping() throws Exception {
        WebXml webxml = new WebXml();
        ContextConfig config = new ContextConfig();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/DuplicateMappingParamServlet");
        assertTrue(pFile.exists());
        try {
            config.processAnnotationsFile(pFile, webxml);
            fail();
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        ServletDef servletDef = webxml.getServlets().get("param");
        assertNull(servletDef);
    }

    public void testFilterMapping() throws Exception {
        WebXml webxml = new WebXml();
        ContextConfig config = new ContextConfig();
        File sFile = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        config.processAnnotationsFile(sFile, webxml);
        File fFile = paramClassResource(
                "org/apache/catalina/startup/ParamFilter");
        config.processAnnotationsFile(fFile, webxml);
        FilterDef fdef = webxml.getFilters().get("paramFilter");
        assertNotNull(fdef);
        assertEquals("Servlet says: ",fdef.getParameterMap().get("message"));
    }
    
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
 
        ContextConfig config = new ContextConfig();
        File sFile = paramClassResource(
                "org/apache/catalina/startup/ParamServlet");
        config.processAnnotationsFile(sFile, webxml);
        File fFile = paramClassResource(
                "org/apache/catalina/startup/ParamFilter");
        config.processAnnotationsFile(fFile, webxml);
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

    public void testDuplicateFilterMapping() throws Exception {
        WebXml webxml = new WebXml();
        ContextConfig config = new ContextConfig();
        File pFile = paramClassResource(
                "org/apache/catalina/startup/DuplicateMappingParamFilter");
        assertTrue(pFile.exists());
        try {
            config.processAnnotationsFile(pFile, webxml);
            fail();
        } catch (IllegalArgumentException ex) {
            // ignore
        }
        FilterDef filterDef = webxml.getFilters().get("paramD");
        assertNull(filterDef);
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
