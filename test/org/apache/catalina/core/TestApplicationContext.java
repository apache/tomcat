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

import java.util.Collection;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.descriptor.JspPropertyGroupDescriptor;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestApplicationContext extends TomcatBaseTest {

    @Test
    public void testBug53257() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug53257/index.jsp");

        String result = res.toString();
        String[] lines = result.split("\n");
        for (String line : lines) {
            if (line.startsWith("FAIL")) {
                Assert.fail(line);
            }
        }
    }


    @Test
    public void testBug53467() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug53467].jsp", res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertTrue(res.toString().contains("<p>OK</p>"));
    }


    @Test(expected = IllegalArgumentException.class)
    public void testAddFilterWithFilterNameNull() throws LifecycleException {
        getServletContext().addFilter(null, (Filter) null);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testAddFilterWithFilterNameEmptyString() throws LifecycleException {
        getServletContext().addFilter("", (Filter) null);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testAddServletWithServletNameNull() throws LifecycleException {
        getServletContext().addServlet(null, (Servlet) null);
    }


    @Test(expected = IllegalArgumentException.class)
    public void testAddServletWithServletNameEmptyString() throws LifecycleException {
        getServletContext().addServlet("", (Servlet) null);
    }


    @Test
    public void testGetJspConfigDescriptor() throws Exception {
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, false);

        StandardContext standardContext =
                (StandardContext) tomcat.getHost().findChildren()[0];

        ServletContext servletContext = standardContext.getServletContext();

        Assert.assertNull(servletContext.getJspConfigDescriptor());

        tomcat.start();

        Assert.assertNotNull(servletContext.getJspConfigDescriptor());
    }

    @Test
    public void testJspPropertyGroupsAreIsolated() throws Exception {
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, false);

        StandardContext standardContext =
                (StandardContext) tomcat.getHost().findChildren()[0];

        ServletContext servletContext = standardContext.getServletContext();

        Assert.assertNull(servletContext.getJspConfigDescriptor());

        tomcat.start();

        JspConfigDescriptor jspConfigDescriptor =
                servletContext.getJspConfigDescriptor();
        Collection<JspPropertyGroupDescriptor> propertyGroups =
                jspConfigDescriptor.getJspPropertyGroups();
        Assert.assertFalse(propertyGroups.isEmpty());
        propertyGroups.clear();

        jspConfigDescriptor = servletContext.getJspConfigDescriptor();
        propertyGroups = jspConfigDescriptor.getJspPropertyGroups();
        Assert.assertFalse(propertyGroups.isEmpty());
    }


    private ServletContext getServletContext() throws LifecycleException {
        Tomcat tomcat = getTomcatInstanceTestWebapp(false, false);

        StandardContext standardContext =
                (StandardContext) tomcat.getHost().findChildren()[0];

        return standardContext.getServletContext();
    }


    @Test(expected = IllegalStateException.class)
    public void testSetInitParameter() throws Exception {
        getTomcatInstance().start();
        getServletContext().setInitParameter("name", "value");
    }
}
