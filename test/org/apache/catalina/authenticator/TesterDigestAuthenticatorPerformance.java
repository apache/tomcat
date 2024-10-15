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
package org.apache.catalina.authenticator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Request;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.filters.TesterHttpServletResponse;
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;

/*
 * This is an absolute performance test. There is no benefit it running it as part of a standard test run so it is
 * excluded due to the name starting Tester...
 */
public class TesterDigestAuthenticatorPerformance {

    private static String USER = "user";
    private static String PWD = "pwd";
    private static String ROLE = "role";
    private static String METHOD = "GET";
    private static String URI = "/protected";
    private static String CONTEXT_PATH = "/foo";
    private static String CLIENT_AUTH_HEADER = "authorization";
    private static String REALM = "TestRealm";
    private static String QOP = "auth";

    private static final AtomicInteger nonceCount = new AtomicInteger(0);

    private DigestAuthenticator authenticator = new DigestAuthenticator();


    @Test
    public void testSimple() throws Exception {
        doTest(4, 1000000);
    }

    public void doTest(int threadCount, int requestCount) throws Exception {

        TesterRunnable runnables[] = new TesterRunnable[threadCount];
        Thread threads[] = new Thread[threadCount];

        String nonce = authenticator.generateNonce(new TesterDigestRequest());

        // Create the runnables & threads
        for (int i = 0; i < threadCount; i++) {
            runnables[i] =
                    new TesterRunnable(authenticator, nonce, requestCount);
            threads[i] = new Thread(runnables[i]);
        }

        long start = System.currentTimeMillis();

        // Start the threads
        for (int i = 0; i < threadCount; i++) {
            threads[i].start();
        }

        // Wait for the threads to finish
        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }
        double wallTime = System.currentTimeMillis() - start;

        // Gather the results...
        double totalTime = 0;
        int totalSuccess = 0;
        for (int i = 0; i < threadCount; i++) {
            System.out.println("Thread: " + i + " Success: " +
                    runnables[i].getSuccess());
            totalSuccess = totalSuccess + runnables[i].getSuccess();
            totalTime = totalTime + runnables[i].getTime();
        }

        System.out.println("Average time per request (user): " +
                totalTime/(threadCount * requestCount));
        System.out.println("Average time per request (wall): " +
                wallTime/(threadCount * requestCount));

        Assert.assertEquals(((long)requestCount) * threadCount, totalSuccess);
    }

    @Before
    public void setUp() throws Exception {

        ConcurrentMessageDigest.init("MD5");

        // Configure the Realm
        TesterMapRealm realm = new TesterMapRealm();
        realm.addUser(USER, PWD);
        realm.addUserRole(USER, ROLE);

        // Add the Realm to the Context
        Context context = new StandardContext();
        context.setName(CONTEXT_PATH);
        context.setRealm(realm);

        Host host = new StandardHost();
        context.setParent(host);

        Engine engine = new StandardEngine();
        host.setParent(engine);

        Service service = new StandardService();
        engine.setService(service);

        // Configure the Login config
        LoginConfig config = new LoginConfig();
        config.setRealmName(REALM);
        context.setLoginConfig(config);

        // Make the Context and Realm visible to the Authenticator
        authenticator.setContainer(context);
        authenticator.setNonceCountWindowSize(8 * 1024);

        authenticator.start();
    }


    private static class TesterRunnable implements Runnable {

        private String nonce;
        private int requestCount;

        private int success = 0;
        private long time = 0;

        private TesterDigestRequest request;
        private HttpServletResponse response;
        private DigestAuthenticator authenticator;

        private static final String A1 = USER + ":" + REALM + ":" + PWD;
        private static final String A2 = METHOD + ":" + CONTEXT_PATH + URI;

        private static final String DIGEST_A1 = HexUtils.toHexString(
                ConcurrentMessageDigest.digest("MD5", A1.getBytes(StandardCharsets.UTF_8)));
        private static final String DIGEST_A2 = HexUtils.toHexString(
                ConcurrentMessageDigest.digest("MD5", A2.getBytes(StandardCharsets.UTF_8)));



        // All init code should be in here. run() needs to be quick
        TesterRunnable(DigestAuthenticator authenticator,
                String nonce, int requestCount) throws Exception {
            this.authenticator = authenticator;
            this.nonce = nonce;
            this.requestCount = requestCount;

            request = new TesterDigestRequest();
            request.getMappingData().context = authenticator.context;

            response = new TesterHttpServletResponse();
        }

        @Override
        public void run() {
            long start = System.currentTimeMillis();
            for (int i = 0; i < requestCount; i++) {
                try {
                    request.setAuthHeader(buildDigestResponse(nonce));
                    if (authenticator.authenticate(request, response)) {
                        success++;
                    }
                    // Clear out authenticated user ready for next iteration
                    request.setUserPrincipal(null);
                } catch (IOException ioe) {
                    // Ignore
                }
            }
            time = System.currentTimeMillis() - start;
        }

        public int getSuccess() {
            return success;
        }

        public long getTime() {
            return time;
        }

        private String buildDigestResponse(String nonce) {

            String ncString = String.format("%1$08x",
                    Integer.valueOf(nonceCount.incrementAndGet()));
            String cnonce = "cnonce";

            String response = DIGEST_A1 + ":" + nonce + ":" + ncString + ":" +
                    cnonce + ":" + QOP + ":" + DIGEST_A2;

            String md5response = HexUtils.toHexString(ConcurrentMessageDigest.digest(
                    "MD5", response.getBytes(StandardCharsets.UTF_8)));

            StringBuilder auth = new StringBuilder();
            auth.append("Digest username=\"");
            auth.append(USER);
            auth.append("\", realm=\"");
            auth.append(REALM);
            auth.append("\", nonce=\"");
            auth.append(nonce);
            auth.append("\", uri=\"");
            auth.append(CONTEXT_PATH + URI);
            auth.append("\", opaque=\"");
            auth.append(authenticator.getOpaque());
            auth.append("\", response=\"");
            auth.append(md5response);
            auth.append("\"");
            auth.append(", qop=");
            auth.append(QOP);
            auth.append(", nc=");
            auth.append(ncString);
            auth.append(", cnonce=\"");
            auth.append(cnonce);
            auth.append("\"");

            return auth.toString();
        }
    }


    private static class TesterDigestRequest extends Request {

        private String authHeader = null;

        TesterDigestRequest() {
            super(null, null);
        }

        @Override
        public String getRemoteAddr() {
            return "127.0.0.1";
        }

        public void setAuthHeader(String authHeader) {
            this.authHeader = authHeader;
        }

        @Override
        public String getHeader(String name) {
            if (CLIENT_AUTH_HEADER.equalsIgnoreCase(name)) {
                return authHeader;
            } else {
                return super.getHeader(name);
            }
        }

        @Override
        public String getMethod() {
            return METHOD;
        }

        @Override
        public String getQueryString() {
            return null;
        }

        @Override
        public String getRequestURI() {
            return CONTEXT_PATH + URI;
        }

        @Override
        public org.apache.coyote.Request getCoyoteRequest() {
            return new org.apache.coyote.Request();
        }
    }
}
