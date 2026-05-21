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
package org.apache.catalina.valves;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestHealthCheckValve extends TomcatBaseTest {

    @Test
    public void testServerHealthCheck() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        HealthCheckValve healthCheckValve = new HealthCheckValve();
        ctx.getParent().getPipeline().addValve(healthCheckValve);

        tomcat.start();

        ByteChunk result = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/foo", result, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        result.recycle();

        rc = getUrl("http://localhost:" + getPort() + healthCheckValve.getPath(), result, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertTrue(result.toString().contains("UP"));

        result.recycle();
        ctx.stop();

        rc = getUrl("http://localhost:" + getPort() + healthCheckValve.getPath(), result, null);
        Assert.assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, rc);
        Assert.assertTrue(result.toString().contains("DOWN"));

        healthCheckValve.setCheckContainersAvailable(false);
        result.recycle();

        rc = getUrl("http://localhost:" + getPort() + healthCheckValve.getPath(), result, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertTrue(result.toString().contains("UP"));
    }

    @Test
    public void testContextHealthCheck() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        HealthCheckValve healthCheckValve = new HealthCheckValve();
        ctx.getPipeline().addValve(healthCheckValve);

        tomcat.start();

        ByteChunk result = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + healthCheckValve.getPath(), result, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertTrue(result.toString().contains("UP"));

        result.recycle();
        ctx.stop();
        healthCheckValve.setPath("/customhealthpath");

        rc = getUrl("http://localhost:" + getPort() + healthCheckValve.getPath(), result, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        result.recycle();
        Tomcat.addServlet(ctx, "dummy", new DummyServlet());
        Container wrapper = ctx.findChild("dummy");
        ctx.start();
        wrapper.stop();

        rc = getUrl("http://localhost:" + getPort() + healthCheckValve.getPath(), result, null);
        Assert.assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, rc);
        Assert.assertTrue(result.toString().contains("DOWN"));
    }

    private static final class DummyServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        public void service(ServletRequest request, ServletResponse response) {
        }
    }


    @Test
    public void testGetSetProperties() {
        HealthCheckValve valve = new HealthCheckValve();

        // Default path
        Assert.assertEquals("/health", valve.getPath());

        valve.setPath("/ready");
        Assert.assertEquals("/ready", valve.getPath());

        // Default checkContainersAvailable
        Assert.assertTrue(valve.getCheckContainersAvailable());

        valve.setCheckContainersAvailable(false);
        Assert.assertFalse(valve.getCheckContainersAvailable());

        valve.setCheckContainersAvailable(true);
        Assert.assertTrue(valve.getCheckContainersAvailable());
    }


    @Test
    public void testCustomPath() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        HealthCheckValve healthCheckValve = new HealthCheckValve();
        healthCheckValve.setPath("/ready");
        ctx.getParent().getPipeline().addValve(healthCheckValve);

        tomcat.start();

        ByteChunk result = new ByteChunk();

        // Default /health should NOT match
        int rc = getUrl("http://localhost:" + getPort() + "/health", result, null);
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, rc);

        result.recycle();

        // Custom /ready should match
        rc = getUrl("http://localhost:" + getPort() + "/ready", result, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertTrue(result.toString().contains("UP"));
    }
}
