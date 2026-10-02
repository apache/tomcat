/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.ha.context;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestReplicatedContext extends TomcatBaseTest {

    @Test
    public void testBug57425() throws LifecycleException, IOException {
        Tomcat tomcat = getTomcatInstance();
        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            ((StandardHost) host).setContextClass(ReplicatedContext.class.getName());
        }

        File root = new File("test/webapp");
        Context context = tomcat.addWebapp(host, "", root.getAbsolutePath());

        Tomcat.addServlet(context, "test", new AccessContextServlet());
        context.addServletMapping("/access", "test");

        tomcat.start();

        ByteChunk result = getUrl("http://localhost:" + getPort() + "/access");

        Assert.assertEquals("OK", result.toString());

    }

    private static class AccessContextServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            getServletContext().setAttribute("NULL", null);
            resp.getWriter().print("OK");
        }
    }

    @Test
    public void testInternalAttributesNotReplicated() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            ((StandardHost) host).setContextClass(ReplicatedContext.class.getName());
        }

        File root = new File("test/webapp");
        Context context = tomcat.addWebapp(host, "", root.getAbsolutePath());
        tomcat.start();

        ServletContext servletContext = context.getServletContext();

        String[] names = { "java.test.attr", "javax.test.attr", "jakarta.test.attr", "org.apache.test.attr" };
        for (String name : names) {
            servletContext.setAttribute(name, "value");
        }
        servletContext.setAttribute("test.attr", "value");

        ReplicatedContext.ReplApplContext replApplContext = getReplApplContext(context);
        Map<String,Object> replicated = replApplContext.getAttributeMap();

        for (String name : names) {
            Assert.assertEquals("value", servletContext.getAttribute(name));
            Assert.assertFalse("Attribute should not be replicated: " + name, replicated.containsKey(name));
            Assert.assertTrue("Attribute should be stored locally: " + name,
                    replApplContext.tomcatAttributes.containsKey(name));
        }

        Assert.assertEquals("value", servletContext.getAttribute("test.attr"));
        Assert.assertTrue("Attribute should be replicated", replicated.containsKey("test.attr"));
    }

    @Test
    public void testReadOnlyAttributeEnforcedLocally() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            ((StandardHost) host).setContextClass(ReplicatedContext.class.getName());
        }

        File root = new File("test/webapp");
        Context context = tomcat.addWebapp(host, "", root.getAbsolutePath());
        tomcat.start();

        ServletContext servletContext = context.getServletContext();

        // The temp dir is set (and made read only) during context start, while the
        // attribute is still stored locally rather than in the replicated map
        ReplicatedContext.ReplApplContext replApplContext = getReplApplContext(context);
        Object tempDir = replApplContext.tomcatAttributes.get(ServletContext.TEMPDIR);
        Assert.assertNotNull("Temp dir attribute should be stored locally", tempDir);
        Assert.assertFalse("Temp dir attribute should not be replicated",
                replApplContext.getAttributeMap().containsKey(ServletContext.TEMPDIR));

        // Attempts to replace or remove the read only attribute must be ignored
        servletContext.setAttribute(ServletContext.TEMPDIR, "unexpected");
        Assert.assertSame(tempDir, servletContext.getAttribute(ServletContext.TEMPDIR));

        servletContext.removeAttribute(ServletContext.TEMPDIR);
        Assert.assertSame(tempDir, servletContext.getAttribute(ServletContext.TEMPDIR));
    }

    private static ReplicatedContext.ReplApplContext getReplApplContext(Context context) throws Exception {
        Field field = StandardContext.class.getDeclaredField("context");
        field.setAccessible(true);
        return (ReplicatedContext.ReplApplContext) field.get(context);
    }
}
