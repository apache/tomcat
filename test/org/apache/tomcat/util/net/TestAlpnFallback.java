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

package org.apache.tomcat.util.net;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http2.Http2TestBase;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestAlpnFallback extends Http2TestBase {

    @Test
    public void testAlpnFallbackToHttp11() throws Exception {
        TesterSupport.configureClientSsl();
        //There's no ALPN negotiation without TLS
        enableHttp2(true);

        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();
        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMappingDecoded("/", "snoop");

        tomcat.start();

        // HttpURLConnection does not support ALPN, so this request will connect over TLS without negotiating h2.
        // The connector must fall back to HTTP/1.1 rather than dropping the connection.
        ByteChunk res = new ByteChunk();
        getUrl("https://localhost:" + getPort() + "/", res, null);
        RequestDescriptor requestDesc = SnoopResult.parse(res.toString());
        Assert.assertEquals("HTTP/1.1", requestDesc.getRequestInfo("REQUEST-PROTOCOL"));
    }

}
