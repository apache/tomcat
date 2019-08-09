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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingListener;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Manager;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.easymock.EasyMock;
import org.easymock.IExpectationSetters;

public class TestCrawlerSessionManagerValve {

    private static final Manager TEST_MANAGER;

    static {
        TEST_MANAGER = new StandardManager();
        TEST_MANAGER.setContainer(new StandardContext());
    }



    @Test
    public void testCrawlerIpsPositive() throws Exception {
        CrawlerSessionManagerValve valve = new CrawlerSessionManagerValve();
        valve.setCrawlerIps("216\\.58\\.206\\.174");
        valve.setCrawlerUserAgents(valve.getCrawlerUserAgents());
        valve.setNext(EasyMock.createMock(Valve.class));
        HttpSession session = createSessionExpectations(valve, true);
        Request request = createRequestExpectations("216.58.206.174", session, true);

        EasyMock.replay(request, session);

        valve.invoke(request, EasyMock.createMock(Response.class));

        EasyMock.verify(request, session);
    }

    @Test
    public void testCrawlerIpsNegative() throws Exception {
        CrawlerSessionManagerValve valve = new CrawlerSessionManagerValve();
        valve.setCrawlerIps("216\\.58\\.206\\.174");
        valve.setCrawlerUserAgents(valve.getCrawlerUserAgents());
        valve.setNext(EasyMock.createMock(Valve.class));
        HttpSession session = createSessionExpectations(valve, false);
        Request request = createRequestExpectations("127.0.0.1", session, false);

        EasyMock.replay(request, session);

        valve.invoke(request, EasyMock.createMock(Response.class));

        EasyMock.verify(request, session);
    }

    @Test
    public void testCrawlerMultipleHostsHostAware() throws Exception {
        CrawlerSessionManagerValve valve = new CrawlerSessionManagerValve();
        valve.setCrawlerUserAgents(valve.getCrawlerUserAgents());
        valve.setHostAware(true);
        valve.setContextAware(true);
        valve.setNext(EasyMock.createMock(Valve.class));

        verifyCrawlingLocalhost(valve, "localhost");
        verifyCrawlingLocalhost(valve, "example.invalid");
    }

    @Test
    public void testCrawlerMultipleContextsContextAware() throws Exception {
        CrawlerSessionManagerValve valve = new CrawlerSessionManagerValve();
        valve.setCrawlerUserAgents(valve.getCrawlerUserAgents());
        valve.setHostAware(true);
        valve.setContextAware(true);
        valve.setNext(EasyMock.createMock(Valve.class));

        verifyCrawlingContext(valve, "/examples");
        verifyCrawlingContext(valve, null);
    }

    @Test
    public void testCrawlersSessionIdIsRemovedAfterSessionExpiry() throws IOException, ServletException {
        CrawlerSessionManagerValve valve = new CrawlerSessionManagerValve();
        valve.setCrawlerIps("216\\.58\\.206\\.174");
        valve.setCrawlerUserAgents(valve.getCrawlerUserAgents());
        valve.setNext(EasyMock.createMock(Valve.class));
        valve.setSessionInactiveInterval(0);
        StandardSession session = new StandardSession(TEST_MANAGER);
        session.setId("id");
        session.setValid(true);

        Request request = createRequestExpectations("216.58.206.174", session, true);

        EasyMock.replay(request);

        valve.invoke(request, EasyMock.createMock(Response.class));

        EasyMock.verify(request);

        MatcherAssert.assertThat(valve.getClientIpSessionId().values(), CoreMatchers.hasItem("id"));

        session.expire();

        Assert.assertEquals(0, valve.getClientIpSessionId().values().size());
    }


    private void verifyCrawlingLocalhost(CrawlerSessionManagerValve valve, String hostname)
            throws IOException, ServletException {
        HttpSession session = createSessionExpectations(valve, true);
        Request request = createRequestExpectations("127.0.0.1", session, true, hostname, "/examples", "tomcatBot 1.0");

        EasyMock.replay(request, session);

        valve.invoke(request, EasyMock.createMock(Response.class));

        EasyMock.verify(request, session);
    }


    private void verifyCrawlingContext(CrawlerSessionManagerValve valve, String contextPath)
            throws IOException, ServletException {
        HttpSession session = createSessionExpectations(valve, true);
        Request request = createRequestExpectations("127.0.0.1", session, true, "localhost", contextPath, "tomcatBot 1.0");

        EasyMock.replay(request, session);

        valve.invoke(request, EasyMock.createMock(Response.class));

        EasyMock.verify(request, session);
    }


    private HttpSession createSessionExpectations(CrawlerSessionManagerValve valve, boolean isBot) {
        HttpSession session = EasyMock.createMock(HttpSession.class);
        if (isBot) {
            EasyMock.expect(session.getId()).andReturn("id").times(2);
            session.setAttribute(EasyMock.eq(valve.getClass().getName()), EasyMock.anyObject(HttpSessionBindingListener.class));
            EasyMock.expectLastCall();
            session.setMaxInactiveInterval(60);
            EasyMock.expectLastCall();
        }
        return session;
    }


    private Request createRequestExpectations(String ip, HttpSession session, boolean isBot) {
        return createRequestExpectations(ip, session, isBot, "localhost", "/examples", "something 1.0");
    }

    private Request createRequestExpectations(String ip, HttpSession session, boolean isBot, String hostname,
            String contextPath, String userAgent) {
        Request request = EasyMock.createMock(Request.class);
        EasyMock.expect(request.getRemoteAddr()).andReturn(ip);
        EasyMock.expect(request.getHost()).andReturn(simpleHostWithName(hostname));
        EasyMock.expect(request.getContext()).andReturn(simpleContextWithName(contextPath));
        IExpectationSetters<HttpSession> setter = EasyMock.expect(request.getSession(false))
                .andReturn(null);
        if (isBot) {
            setter.andReturn(session);
        }
        EasyMock.expect(request.getHeaders("user-agent")).andReturn(Collections.enumeration(Arrays.asList(userAgent)));
        return request;
    }

    private Host simpleHostWithName(String hostname) {
        Host host = EasyMock.createMock(Host.class);
        EasyMock.expect(host.getName()).andReturn(hostname);
        EasyMock.replay(host);
        return host;
    }

    private Context simpleContextWithName(String contextPath) {
        if (contextPath == null) {
            return null;
        }
        Context context = EasyMock.createMock(Context.class);
        EasyMock.expect(context.getName()).andReturn(contextPath);
        EasyMock.replay(context);
        return context;
    }
}
