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
package org.apache.catalina.servlets;

import java.io.File;
import java.io.FileWriter;
import java.util.UUID;

import org.apache.catalina.Context;
import org.junit.Assert;
import org.junit.Test;

public class TestWebdavServletRfcSection_9_11 extends WebdavServletRfcSectionBase {
    /**
     * 9.11. UNLOCK Method
     * <p>
     * The UNLOCK method removes the lock identified by the lock token in the Lock-Token request header. The Request-URI
     * MUST identify a resource within the scope of the lock.
     * <p>
     * Note that use of the Lock-Token header to provide the lock token is not consistent with other state-changing
     * methods, which all require an If header with the lock token. Thus, the If header is not needed to provide the
     * lock token. Naturally, when the If header is present, it has its normal meaning as a conditional header.
     * <p>
     * For a successful response to this method, the server MUST delete the lock entirely.
     * <p>
     * If all resources that have been locked under the submitted lock token cannot be unlocked, then the UNLOCK request
     * MUST fail.
     * <p>
     * A successful response to an UNLOCK method does not mean that the resource is necessarily unlocked. It means that
     * the specific lock corresponding to the specified token no longer exists.
     * <p>
     * Any DAV-compliant resource that supports the LOCK method MUST support the UNLOCK method. This method is
     * idempotent, but not safe (see Section 9.1 of [RFC2616]). Responses to this method MUST NOT be cached.
     */
    @Test
    public void testRfc_9_11() throws Exception {
        Context ctx = prepareContext("rfc_9_11");
        prepareWebdav(ctx, true, false);
        prepareExpirePolicy(ctx);

        String collection1 = UUID.randomUUID().toString();
        Assert.assertTrue(new File(getWebappAbsolutePath(), collection1).mkdir());
        String collection11 = UUID.randomUUID().toString();
        Assert.assertTrue("Failed to mkdir of ",
                new File(getWebappAbsolutePath() + File.separator + collection1, collection11).mkdir());

        String collection12 = UUID.randomUUID().toString();
        Assert.assertTrue("Failed to mkdir of ",
                new File(getWebappAbsolutePath() + File.separator + collection1, collection12).mkdir());
        String collection121 = UUID.randomUUID().toString();
        Assert.assertTrue("Failed to mkdir of ",
                new File(getWebappAbsolutePath() + File.separator + collection1 + File.separator + collection12,
                        collection121).mkdir());
        String collection13 = UUID.randomUUID().toString();
        Assert.assertTrue("Failed to mkdir of ",
                new File(getWebappAbsolutePath() + File.separator + collection1, collection13).mkdir());
        String file121 = "file121_readme.txt";
        try (FileWriter fw = new FileWriter(getWebappAbsolutePath() + File.separator + collection1 + File.separator +
                collection12 + File.separator + file121)) {
            fw.write("file121_readme.txt-v1");
            fw.flush();
        }
        getTomcatInstance().start();

        WebdavClient client = new WebdavClient();
        client.setPort(getPort());

        String lockOwner = "Owner_Rfc_9_11";
        /* Lock on a mapped url */
        String lockToken = client.lockResource("/" + collection1 + "/" + collection11, lockOwner, true,
                client.DEPTH_INFINITY, "Unexpected response", null);
        Assert.assertNotNull(lockToken);

        /*
         * The Request-URI MUST identify a resource within the scope of the lock.
         */
        client.unlockResource("/" + collection1 + "/" + collection12, lockToken,
                "9.11. UNLOCK Method - try unlock resource out of scope, 4xx status code expected.",
                r -> r.getStatusCode() >= 400 && r.getStatusCode() < 500);

        /* Unlock with wrong token */
        client.unlockResource("/" + collection1 + "/" + collection11, "WrongToken",
                "9.11. UNLOCK Method - try unlock with a wrong lock token, 4xx status code expected.",
                r -> r.getStatusCode() >= 400 && r.getStatusCode() < 500);
        // client.unlockResource("/" + collection1 + "/" + collection11, "PREFIX_"+lockToken+"_SUFFIX",
        // "9.11. UNLOCK Method - try unlock with a wrong lock token, 4xx status code expected.",
        // r -> r.getStatusCode() >= 400 && r.getStatusCode() < 500);
        // TODO: FIXME: enable above case.
        client.unlockResource("/" + collection1 + "/" + collection11, lockToken, "Unexpected response", null);

        /**
         * For a successful response to this method, the server MUST delete the lock entirely.
         * <p>
         * assume dir /xx/c1/c11/c111 exists:
         * 
         * <pre>
         * 1. Lock /xx/c1, receive t1; 
         * 2. unlock /xx/c1/c11 with t1; 
         * expect: t1 removed entirely, and token on /xx/c1 must be removed entirely.
         * </pre>
         */
        lockToken = client.lockResource("/" + collection1 + "/" + collection12, lockOwner, true, client.DEPTH_INFINITY,
                "Unexpected response", null);
        client.unlockResource("/" + collection1 + "/" + collection12 + "/" + collection121, lockToken,
                "Unexpected response", null);
        /* Retry unlock */
        client.unlockResource("/" + collection1 + "/" + collection12, lockToken, "Unexpected response",
                r -> WebdavStatus.SC_CONFLICT == r.getStatusCode());

        String anotherLockToken =
                client.lockResource("/" + collection1 + "/" + collection12, lockOwner, true, client.DEPTH_INFINITY,
                        "Unexpected response", r -> r.isResponse200() || WebdavStatus.SC_CREATED == r.getStatusCode());

        Assert.assertNotNull(
                "9.11. UNLOCK Method - previous unlock should remove lock entirely, a NotNull token expected",
                anotherLockToken);

        /**
         * If all resources that have been locked under the submitted lock token cannot be unlocked, then the UNLOCK
         * request MUST fail.
         */
        // Get file121
        client.getResource("/" + collection1 + "/" + collection12 + "/" + file121, "Unexpected response", null);
        String etagValue = matchHeaders(client.getResponseHeaders(), h -> h.toLowerCase().startsWith("etag")).get(0)
                .substring("etag: ".length());
        client.unlockResource("/" + collection1 + "/" + collection12, anotherLockToken,
                "<http://localhost:" + getPort() + "/" + collection1 + "/" + collection12 + "/" + file121 + "> (<" +
                        anotherLockToken + "> [" + "wrongETagValue" + "])",
                "9.11. UNLOCK Method - UNLOCK fail expected due to 'If' header mismatch, ",
                r -> WebdavStatus.SC_PRECONDITION_FAILED == r.getStatusCode());
        String wrongETagValue = "PREFIX_" + etagValue + "_SUFFIX";
        client.unlockResource("/" + collection1 + "/" + collection12, anotherLockToken,
                "<http://localhost:" + getPort() + "/" + collection1 + "/" + collection12 + "/" + file121 + "> (<" +
                        anotherLockToken + "> [" + wrongETagValue + "])",
                "9.11. UNLOCK Method - UNLOCK fail expected due to 'If' header mismatch, ",
                r -> WebdavStatus.SC_PRECONDITION_FAILED == r.getStatusCode());

        client.getResource("/" + collection1 + "/" + collection12 + "/" + file121, "Unexpected response", null);
        Assert.assertTrue(client.isResponseCacheable());
        etagValue = matchHeaders(client.getResponseHeaders(), h -> h.toLowerCase().startsWith("etag")).get(0)
                .substring("etag: ".length());
        client.unlockResource("/" + collection1 + "/" + collection12, anotherLockToken,
                "<http://localhost:" + getPort() + "/" + collection1 + "/" + collection12 + "/" + file121 + "> (<" +
                        anotherLockToken + "> [" + etagValue + "])",
                "9.11. UNLOCK Method - UNLOCK SC_NO_CONTENT expected.",
                r -> WebdavStatus.SC_NO_CONTENT == r.getStatusCode());

        /* Responses to this method MUST NOT be cached. */
        if(client.isResponseCacheable()) {
            Assert.fail(
                    "9.11. UNLOCK Method - Responses to this method MUST NOT be cached - Please check cache-control, expires, and etag");
        }

    }

    /**
     * 9.11.1. Status Codes
     * <p>
     */
//    @Test
    public void testRfc_9_11_1() throws Exception {
        Context ctx = prepareContext("rfc_9_11_1");
        prepareWebdav(ctx, true, false);
        prepareExpirePolicy(ctx);

        String collection1 = UUID.randomUUID().toString();
        Assert.assertTrue(new File(getWebappAbsolutePath(), collection1).mkdir());
        String collection11 = UUID.randomUUID().toString();
        Assert.assertTrue("Failed to mkdir of ",
                new File(getWebappAbsolutePath() + File.separator + collection1, collection11).mkdir());

        getTomcatInstance().start();

        WebdavClient client = new WebdavClient();
        client.setPort(getPort());

        String lockOwner = "Owner_Rfc_9_11";
        /* Lock on a mapped url */
        String lockToken = client.lockResource("/" + collection1 + "/" + collection11, lockOwner, true,
                client.DEPTH_INFINITY, "Unexpected response", null);
        Assert.assertNotNull(lockToken);
        // 400
        client.unlockResource("/" + collection1 + "/" + collection11, null,
                "400 (Bad Request) - No lock token was provided.", r -> r.isResponse400());
        // 403

    }
}
