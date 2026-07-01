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

import java.lang.reflect.Method;

import jakarta.servlet.ServletContext;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardPipeline;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;

/**
 * Unit tests for {@link LoadBalancerDrainingValve} covering URI manipulation behaviours
 * not covered by the parameterised integration test.
 */
public class TestLoadBalancerDrainingValveUnit {

    /**
     * collapseLeadingSlashes() is the private static helper that normalises URIs
     * before issuing the redirect.  Test it directly via reflection.
     */
    @Test
    public void testCollapseMultipleSlashesInURI() throws Exception {
        Method collapse = LoadBalancerDrainingValve.class
                .getDeclaredMethod("collapseLeadingSlashes", String.class);
        collapse.setAccessible(true);

        LoadBalancerDrainingValve valve = new LoadBalancerDrainingValve();

        // Multiple leading slashes are collapsed to one
        Assert.assertEquals("/test", collapse.invoke(valve, "///test"));
        Assert.assertEquals("/test/path", collapse.invoke(valve, "//test/path"));

        // A URI consisting entirely of slashes becomes a single slash
        Assert.assertEquals("/", collapse.invoke(valve, "///"));

        // URIs that need no change are returned as-is
        Assert.assertEquals("/test", collapse.invoke(valve, "/test"));
        Assert.assertEquals("test",  collapse.invoke(valve, "test"));
    }


    /**
     * When JK_LB_ACTIVATION=DIS and the session is invalid, the valve must
     * redirect the client after stripping the ;jsessionid parameter from the URI
     * so that the load-balancer can assign the client to a healthy node.
     */
    @Test
    public void testSessionURIParamStripped() throws Exception {
        IMocksControl control = EasyMock.createControl();
        ServletContext servletContext = control.createMock(ServletContext.class);
        Context ctx             = control.createMock(Context.class);
        Request request         = control.createMock(Request.class);
        Response response       = control.createMock(Response.class);

        // Minimal context stubs required for valve initialisation
        EasyMock.expect(ctx.getMBeanKeyProperties()).andStubReturn("");
        EasyMock.expect(ctx.getName()).andStubReturn("");
        EasyMock.expect(ctx.getPipeline()).andStubReturn(new StandardPipeline());
        EasyMock.expect(ctx.getDomain()).andStubReturn("foo");
        EasyMock.expect(ctx.getLogger())
                .andStubReturn(org.apache.juli.logging.LogFactory.getLog(LoadBalancerDrainingValve.class));
        EasyMock.expect(ctx.getServletContext()).andStubReturn(servletContext);

        // Simulate a disabled node with an invalid session
        EasyMock.expect(request.getAttribute(LoadBalancerDrainingValve.ATTRIBUTE_KEY_JK_LB_ACTIVATION))
                .andStubReturn("DIS");
        EasyMock.expect(Boolean.valueOf(request.isRequestedSessionIdValid()))
                .andStubReturn(Boolean.FALSE);

        // No cookies present — session cookie deletion path is not exercised here
        EasyMock.expect(request.getCookies()).andStubReturn(null);
        EasyMock.expect(request.getContext()).andStubReturn(ctx);
        // SessionConfig.getSessionCookieName / getSessionUriParamName both key off
        // getSessionCookieName(); returning "jsessionid" short-circuits both lookups.
        EasyMock.expect(ctx.getSessionCookieName()).andStubReturn("jsessionid");

        // URI carries a jsessionid path parameter that the valve must strip
        EasyMock.expect(request.getRequestURI()).andStubReturn("/test;jsessionid=abc123");
        EasyMock.expect(request.getQueryString()).andStubReturn(null);

        // The valve must redirect to the clean URI — jsessionid removed
        response.sendRedirect("/test", 307);
        EasyMock.expectLastCall();

        Valve next = control.createMock(Valve.class);
        // next.invoke must NOT be called; the valve redirects instead

        control.replay();

        LoadBalancerDrainingValve valve = new LoadBalancerDrainingValve();
        valve.setContainer(ctx);
        valve.init();
        valve.setNext(next);
        valve.invoke(request, response);

        control.verify();
    }
}
