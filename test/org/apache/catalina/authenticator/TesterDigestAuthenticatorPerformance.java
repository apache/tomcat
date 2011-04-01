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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.catalina.Context;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.startup.TestTomcat.MapRealm;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.MD5Encoder;
import org.apache.tomcat.util.buf.ByteChunk;

public class TesterDigestAuthenticatorPerformance extends TomcatBaseTest {

    private static String USER = "user";
    private static String PWD = "pwd";
    private static String ROLE = "role";
    private static String URI = "/protected";
    private static String CONTEXT_PATH = "/foo";
    private static String CLIENT_AUTH_HEADER = "authorization";
    private static String REALM = "TestRealm";
    private static String QOP = "auth";

    
    public void testSimple() throws Exception {
        doTest(100, 1000);
    }

    public void doTest(int threadCount, int requestCount) throws Exception {
        
        getTomcatInstance().start();

        TesterRunnable runnables[] = new TesterRunnable[threadCount];
        Thread threads[] = new Thread[threadCount];
        
        // Create the runnables & threads
        for (int i = 0; i < threadCount; i++) {
            runnables[i] = new TesterRunnable(i, requestCount);
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
        
        assertEquals(requestCount * threadCount, totalSuccess);
    }

    private class TesterRunnable implements Runnable {

        // Number of valid requests required
        private int requestCount;
        
        private String nonce;
        private String opaque;

        private String cnonce;

        private Map<String,List<String>> reqHeaders;
        private List<String> authHeader;
        
        private MessageDigest digester;
        private MD5Encoder encoder;

        private String md5a1;
        private String md5a2;

        private String path;

        private int success = 0;
        private long time = 0;

        // All init code should be in here. run() needs to be quick
        public TesterRunnable(int id, int requestCount) throws Exception {
            this.requestCount = requestCount;

            path = "http://localhost:" + getPort() + CONTEXT_PATH + URI;

            // Make the first request as we need the Digest challenge to obtain
            // the server nonce
            Map<String,List<String>> respHeaders =
                    new HashMap<String,List<String>>();
            getUrl(path, new ByteChunk(), respHeaders);
            
            nonce = TestDigestAuthenticator.getNonce(respHeaders);
            opaque = TestDigestAuthenticator.getOpaque(respHeaders);
            
            cnonce = "cnonce" + id;

            reqHeaders = new  HashMap<String,List<String>>();
            authHeader = new ArrayList<String>();
            reqHeaders.put(CLIENT_AUTH_HEADER, authHeader);
            
            digester = MessageDigest.getInstance("MD5");
            encoder = new MD5Encoder();

            String a1 = USER + ":" + REALM + ":" + PWD;
            String a2 = "GET:" + CONTEXT_PATH + URI;
            
            md5a1 = encoder.encode(digester.digest(a1.getBytes()));
            md5a2 = encoder.encode(digester.digest(a2.getBytes()));
        }

        @Override
        public void run() {
            int rc;
            int nc = 0;
            ByteChunk bc = new ByteChunk();
            long start = System.currentTimeMillis();
            for (int i = 0; i < requestCount; i++) {
                nc++;
                authHeader.clear();
                authHeader.add(buildDigestResponse(nc));
                
                rc = -1;
                bc.recycle();
                bc.reset();
                
                try {
                    rc = getUrl(path, bc, reqHeaders, null);
                } catch (IOException ioe) {
                    // Ignore
                }
             
                if (rc == 200 && "OK".equals(bc.toString())) {
                    success++;
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

        private String buildDigestResponse(int nc) {
            
            String ncString = String.format("%1$08x", Integer.valueOf(nc));

            String response = md5a1 + ":" + nonce + ":" + ncString + ":" +
                    cnonce + ":" + QOP + ":" + md5a2;

            String md5response =
                encoder.encode(digester.digest(response.getBytes()));
    
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
            auth.append(opaque);
            auth.append("\", response=\"");
            auth.append(md5response);
            auth.append("\"");
            auth.append(", qop=\"");
            auth.append(QOP);
            auth.append("\"");
            auth.append(", nc=\"");
            auth.append(ncString);
            auth.append("\"");
            auth.append(", cnonce=\"");
            auth.append(cnonce);
            auth.append("\"");

            return auth.toString();
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Configure a context with digest auth and a single protected resource
        Tomcat tomcat = getTomcatInstance();
        
        // Must have a real docBase - just use temp
        Context ctxt = tomcat.addContext(CONTEXT_PATH,
                System.getProperty("java.io.tmpdir"));
        
        // Add protected servlet
        Tomcat.addServlet(ctxt, "TesterServlet", new TesterServlet());
        ctxt.addServletMapping(URI, "TesterServlet");
        SecurityCollection collection = new SecurityCollection();
        collection.addPattern(URI);
        SecurityConstraint sc = new SecurityConstraint();
        sc.addAuthRole(ROLE);
        sc.addCollection(collection);
        ctxt.addConstraint(sc);
        
        // Configure the Realm
        MapRealm realm = new MapRealm();
        realm.addUser(USER, PWD);
        realm.addUserRole(USER, ROLE);
        ctxt.setRealm(realm);
        
        // Configure the authenticator
        LoginConfig lc = new LoginConfig();
        lc.setAuthMethod("DIGEST");
        lc.setRealmName(REALM);
        ctxt.setLoginConfig(lc);
        DigestAuthenticator authenticator = new DigestAuthenticator();
        authenticator.setCnonceCacheSize(100);
        ctxt.getPipeline().addValve(authenticator);
    }
}
