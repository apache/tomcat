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

package org.apache.catalina.connector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestValidateClientSessionId extends TomcatBaseTest {

    @Test
    public void testMaliciousSessionIdRejected() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();
        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/", "snoop");

        tomcat.start();

        Map<String, List<String>> reqHead = new HashMap<>();
        reqHead.put("Cookie", List.of("JSESSIONID=DUMMY_SESSION_ID"));

        ByteChunk res = new ByteChunk();
        getUrl("http://localhost:" + getPort() + "/?createSession=true", res, reqHead, null);

        RequestDescriptor requestDesc = SnoopResult.parse(res.toString());

        String actualSessionId = requestDesc.getRequestInfo("SESSION-ID");
        Assert.assertNotEquals("DUMMY_SESSION_ID", actualSessionId);
    }

    @Test
    public void testValidSessionIdAcceptedAcrossContexts() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx1 = tomcat.addContext("/app1", null);
        ctx1.setSessionCookiePath("/");
        Tomcat.addServlet(ctx1, "snoop", new SnoopServlet());
        ctx1.addServletMappingDecoded("/", "snoop");

        Context ctx2 = tomcat.addContext("/app2", null);
        ctx2.setSessionCookiePath("/");
        Tomcat.addServlet(ctx2, "snoop", new SnoopServlet());
        ctx2.addServletMappingDecoded("/", "snoop");

        tomcat.start();

        ByteChunk res = new ByteChunk();
        Map<String, List<String>> resHead = new HashMap<>();
        getUrl("http://localhost:" + getPort() + "/app1/?createSession=true", res, null, resHead);

        RequestDescriptor requestDesc = SnoopResult.parse(res.toString());
        String sessionId1 = requestDesc.getRequestInfo("SESSION-ID");

        Map<String, List<String>> reqHead = new HashMap<>();
        reqHead.put("Cookie", List.of("JSESSIONID=" + sessionId1));

        getUrl("http://localhost:" + getPort() + "/app2/?createSession=true", res, reqHead, null);

        requestDesc = SnoopResult.parse(res.toString());
        String sessionId2 = requestDesc.getRequestInfo("SESSION-ID");
        Assert.assertEquals(sessionId1, sessionId2);
    }

}
