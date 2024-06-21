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
package jakarta.el;

import java.io.File;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.jsp.JspApplicationContext;
import jakarta.servlet.jsp.JspFactory;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;


public class TestOptionalELResolverInJsp extends TomcatBaseTest {

    @Test
    public void test() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctx = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        ctx.addApplicationListener(ResolverConfigListener.class.getName());

        tomcat.start();

        ByteChunk responseBody = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/el-optional.jsp", responseBody, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String result = responseBody.toString();
        assertEcho(result, "00-");
        assertEcho(result, "01-");
        assertEcho(result, "02-");
        assertEcho(result, "03-");
        assertEcho(result, "10-This is an instance of TesterBeanB");
        assertEcho(result, "11-test");
        assertEcho(result, "13-Returned from the doSomething() method");
        assertEcho(result, "20-");
        assertEcho(result, "21-");
        assertEcho(result, "22-");
        assertEcho(result, "23-");
        assertEcho(result, "30-This is an instance of TesterBeanB");
        assertEcho(result, "31-test");
        assertEcho(result, "33-Returned from the doSomething() method");
    }


    // Assertion for text contained with <p></p>, e.g. printed by tags:echo
    private static void assertEcho(String result, String expected) {
        Assert.assertTrue(result, result.indexOf("<p>" + expected + "</p>") > 0);
    }


    public static class ResolverConfigListener implements ServletContextListener {

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            ServletContext servletContext = sce.getServletContext();
            JspFactory jspFactory = JspFactory.getDefaultFactory();
            JspApplicationContext jspApplicationContext = jspFactory.getJspApplicationContext(servletContext);
            jspApplicationContext.addELResolver(new OptionalELResolver());
        }
    }
}
