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
package jakarta.servlet.http;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;

import jakarta.servlet.ServletException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

/**
 *
 */
public abstract class HttpServletUriPathCanonicalizationBaseTest extends TomcatBaseTest {

    @Parameter(0)
    public String index;
    @Parameter(1)
    public String encodedUriPath;
    @Parameter(2)
    public String expectedDecodedPath;
    @Parameter(3)
    public Integer expectedRejectCode;

    @Test
    public void testServlet6_UriPathCanonicalization() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        // per servlet 6.2, rejectSuspiciousURIs should be enabled by default
        // tomcat.getConnector().setRejectSuspiciousURIs(true);
        // No file system docBase required
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "echoUriServlet", new EchoUriServlet());
        ctx.addServletMappingDecoded("/", "echoUriServlet");

        tomcat.start();

        MySimpleClient client = new MySimpleClient();
        client.setPort(getPort());
        client.setResponseBodyEncoding(Charset.forName("utf-8"));
        client.connect();
        client.setRequest(new String[] {
                "GET " + encodedUriPath + " HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Connection: close" + CRLF +
                CRLF });
        client.processRequest();
        int sc = client.getStatusCode();
        String body = client.getResponseBody();
        if (expectedRejectCode != null && expectedRejectCode.intValue() != 200) {
            Assert.assertEquals(expectedRejectCode.intValue(), sc);
        } else {
            Assert.assertEquals(expectedDecodedPath, body);
            Assert.assertEquals(200, sc);
        }
    }

    private static final class EchoUriServlet extends HttpServlet {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            if(req instanceof RequestFacade rf) {
                try {
                    Field f = RequestFacade.class.getDeclaredField("request");
                    f.setAccessible(true);
                    Request internalRequest=(Request) f.get(rf);
                    resp.setCharacterEncoding("utf-8");
                    resp.getWriter().write(internalRequest.getDecodedRequestURI());
                } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                    e.printStackTrace();
                }

            } else {
                resp.getWriter().write("Unkown");
            }
        }
    }

    private static final class MySimpleClient extends SimpleHttpClient {
        @Override
        public boolean isResponseBodyOK() {
            return getStatusCode() >= 200 && getStatusCode() < 300;
        }
    }

}
