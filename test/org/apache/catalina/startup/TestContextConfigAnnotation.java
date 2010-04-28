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

import junit.framework.TestCase;

import org.apache.catalina.deploy.ServletDef;
import org.apache.catalina.deploy.WebXml;

/**
* Check Servlet 3.0 Spec 8.2.3.3: Override annotation parameter from web.xml or fragment.
* 
* @author Peter Rossbach
* @version $Revision$ $Date$
*/
public class TestContextConfigAnnotation extends TestCase {

	public void testAnnotation() throws Exception {
		WebXml webxml = new WebXml();
		ContextConfig config = new ContextConfig();
		File pFile = paramServletClassResource("org/apache/catalina/startup/ParamServlet");
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
		assertEquals(new Integer(0), servletDef.getLoadOnStartup());
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
		File pFile = paramServletClassResource("org/apache/catalina/startup/ParamServlet");
		assertTrue(pFile.exists());
		config.processAnnotationsFile(pFile, webxml);
		
		assertEquals(servletDef, webxml.getServlets().get("param"));
		
		assertEquals("tomcat", servletDef.getParameterMap().get("foo"));
		assertEquals("param", webxml.getServletMappings().get("/param"));
		// annotation mapping not added s. Servlet Spec 3.0 (Nov 2009) 8.2.3.3.vi page 81
		assertNull(webxml.getServletMappings().get(
		        "/annotation/overwrite"));
		
		assertEquals("Description", servletDef.getDescription());
		assertEquals("DisplayName", servletDef.getDisplayName());
		assertEquals("LargeIcon", servletDef.getLargeIcon());
		assertEquals("SmallIcon", servletDef.getSmallIcon());
		assertEquals(Boolean.TRUE, servletDef.getAsyncSupported());
		assertEquals(new Integer(1), servletDef.getLoadOnStartup());
		assertNull(servletDef.getEnabled());
		assertNull(servletDef.getJspFile());
	}

	public void testNoMapping() throws Exception {
		WebXml webxml = new WebXml();
		ContextConfig config = new ContextConfig();
		File pFile = paramServletClassResource("org/apache/catalina/startup/NoMappingParamServlet");
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
		servletDef
		        .setServletClass("org.apache.catalina.startup.NoMappingParamServlet");
		servletDef.addInitParameter("foo", "tomcat");

		webxml.addServlet(servletDef);
		webxml.addServletMapping("/param", "param1");
		ContextConfig config = new ContextConfig();
		File pFile = paramServletClassResource("org/apache/catalina/startup/NoMappingParamServlet");
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
		File pFile = paramServletClassResource("org/apache/catalina/startup/DuplicateMappingParamServlet");
		assertTrue(pFile.exists());
		try {
			config.processAnnotationsFile(pFile, webxml);
			fail();
		} catch (IllegalArgumentException ex) {
			// ingore
		}
		ServletDef servletDef = webxml.getServlets().get("param");
		assertNull(servletDef);
	}

	/**
	 * Find newest class resource at eclipse and ant standard class output dirs!
	 * @param className
	 * @return File Resource
	 */
	private File paramServletClassResource(String className) {
		File antFile = new File("output/testclasses/" + className + ".class");
		File eclipseFile = new File(".settings/output/" + className + ".class");
		if (antFile.exists()) {
			if (eclipseFile.exists()) {
				if (antFile.lastModified() >= eclipseFile.lastModified()) {
					return antFile;
				} else {
					return eclipseFile;
				}
			} else {
				return antFile;
			}
		} else {
			return eclipseFile;
		}
	}
}
