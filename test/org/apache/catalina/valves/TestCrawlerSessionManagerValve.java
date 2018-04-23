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

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.easymock.EasyMock;
import org.easymock.IExpectationSetters;

public class TestCrawlerSessionManagerValve {

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


    private void verifyCrawlingLocalhost(CrawlerSessionManagerValve valve, String hostname)
            throws IOException, ServletException {
        HttpSession session = createSessionExpectations(valve, true);
        Request request = createRequestExpectations("127.0.0.1", session, true, hostname, "tomcatBot 1.0");

        EasyMock.replay(request, session);

        valve.invoke(request, EasyMock.createMock(Response.class));

        EasyMock.verify(request, session);
    }


    private HttpSession createSessionExpectations(CrawlerSessionManagerValve valve, boolean isBot) {
        HttpSession session = EasyMock.createMock(HttpSession.class);
        if (isBot) {
            EasyMock.expect(session.getId()).andReturn("id").times(2);
            session.setAttribute(valve.getClass().getName(), valve);
            EasyMock.expectLastCall();
            session.setMaxInactiveInterval(60);
            EasyMock.expectLastCall();
        }
        return session;
    }


    private Request createRequestExpectations(String ip, HttpSession session, boolean isBot) {
        return createRequestExpectations(ip, session, isBot, "localhost", "something 1.0");
    }

    private Request createRequestExpectations(String ip, HttpSession session, boolean isBot, String hostname, String userAgent) {
        Request request = EasyMock.createMock(Request.class);
        EasyMock.expect(request.getRemoteAddr()).andReturn(ip);
        EasyMock.expect(request.getHost()).andReturn(simpleHostWithName(hostname));
        EasyMock.expect(request.getContext()).andReturn(simpleContextWithName());
        IExpectationSetters<HttpSession> setter = EasyMock.expect(request.getSession(false))
                .andReturn(null);
        if (isBot) {
            setter.andReturn(session);
        }
        EasyMock.expect(request.getHeaders("user-agent")).andAnswer(() -> Collections.enumeration(Arrays.asList(userAgent)));
        return request;
    }

    private Host simpleHostWithName(String hostname) {
        Host host = EasyMock.createMock(Host.class);
        EasyMock.expect(host.getName()).andReturn(hostname);
        EasyMock.replay(host);
        return host;
    }

    private Context simpleContextWithName() {
        Context context = EasyMock.createMock(Context.class);
        EasyMock.expect(context.getName()).andReturn("/examples");
        EasyMock.replay(context);
        return context;
    }
}
