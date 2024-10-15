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
package jakarta.servlet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;

@RunWith(Parameterized.class)
public class TestServletRequestParametersQueryString extends ServletRequestParametersBaseTest {

    private static final Integer SC_OK = Integer.valueOf(HttpServletResponse.SC_OK);
    private static final Integer SC_BAD_REQUEST = Integer.valueOf(HttpServletResponse.SC_BAD_REQUEST);
    private static final Integer ZERO = Integer.valueOf(0);
    private static final Integer TWO = Integer.valueOf(2);

    @Parameterized.Parameters(name = "{index}: queryString[{0}], expectedStatusCode[{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        // Empty parameter
        parameterSets.add(new Object[] { "before=aaa&&after=zzz", SC_OK, TWO} );

        // Invalid parameter
        parameterSets.add(new Object[] { "before=aaa&=value&after=zzz", SC_BAD_REQUEST, ZERO} );

        // Invalid %nn encoding
        parameterSets.add(new Object[] { "before=aaa&test=val%GGue&after=zzz", SC_BAD_REQUEST, ZERO} );

        // Invalid UTF-8 byte
        parameterSets.add(new Object[] { "before=aaa&test=val%FFue&after=zzz", SC_BAD_REQUEST, ZERO} );

        // There are no unmappable UTF-8 code points

        // Too many parameters
        parameterSets.add(new Object[] { "before=aaa&test=value&after=zzz&extra=yyy", SC_BAD_REQUEST, ZERO} );

        return parameterSets;
    }

    @Parameter(0)
    public String queryString;

    @Parameter(1)
    public int expectedStatusCode;

    @Parameter(2)
    public int expectedValidParameterCount;


    @Test
    public void testParameterParsing() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        tomcat.getConnector().setMaxParameterCount(3);

        // No file system docBase required
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        // Map the test Servlet
        ParameterParsingServlet parameterParsingServlet = new ParameterParsingServlet();
        Tomcat.addServlet(ctx, "parameterParsingServlet", parameterParsingServlet);
        ctx.addServletMappingDecoded("/", "parameterParsingServlet");

        tomcat.start();

        TestParameterClient client = new TestParameterClient();
        client.setPort(getPort());
        client.setRequest(new String[] {
                "GET /?" + queryString +" HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: close" + CRLF +
                CRLF });
        client.setResponseBodyEncoding(StandardCharsets.UTF_8);
        client.connect();
        client.processRequest();

        Assert.assertEquals(expectedStatusCode, client.getStatusCode());

        Map<String,List<String>> parameters = parseReportedParameters(client);

        Assert.assertEquals(expectedValidParameterCount, parameters.size());
    }
}