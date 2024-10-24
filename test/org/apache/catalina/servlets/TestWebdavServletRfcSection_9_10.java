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
import java.util.Objects;
import java.util.UUID;

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.junit.Assert;
import org.junit.Test;

import jakarta.servlet.http.HttpServletResponse;

public class TestWebdavServletRfcSection_9_10 extends WebdavServletRfcSectionBase {
    /**
     * 9.10. LOCK Method
     * <p>
     * The following sections describe the LOCK method, which is used to take out a lock of any access type and to
     * refresh an existing lock. These sections on the LOCK method describe only those semantics that are specific to
     * the LOCK method and are independent of the access type of the lock being requested. Any resource that supports
     * the LOCK method MUST, at minimum, support the XML request and response formats defined herein. This method is
     * neither idempotent nor safe (see Section 9.1 of [RFC2616]). Responses to this method MUST NOT be cached.
     */
    @Test
    public void testRfc_9_10() throws Exception {
        Context ctx = prepareContext("rfc_9_10");
        prepareWebdav(ctx, true, false);
        prepareExpirePolicy(ctx);

        String newDir = UUID.randomUUID().toString();
        Assert.assertTrue(new File(getWebappAbsolutePath(), newDir).mkdir());

        getTomcatInstance().start();

        String mappedUrl = "/" + newDir;

        WebdavClient client = new WebdavClient();
        client.setPort(getPort());

        String lockOwner = "Owner_Rfc_9_10";
        /* Lock on a mapped url */
        client.lockResource(mappedUrl, lockOwner, true, "0", "Unexpected status code.",
                c -> c.isResponse200() || WebdavStatus.SC_CREATED == c.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("opaquelocktoken:"));
        /* Check cache */
        if (client.isResponseCacheable()) {
            Assert.fail(
                    "9.10. LOCK Method - Responses to this method MUST NOT be cached - Please check cache-control, expires, and etag");
        }
    }

    /**
     * 9.10.1. Creating a Lock on an Existing Resource
     * <p>
     * A LOCK request to an existing resource will create a lock on the resource identified by the Request-URI, provided
     * the resource is not already locked with a conflicting lock. The resource identified in the Request-URI becomes
     * the root of the lock. LOCK method requests to create a new lock MUST have an XML request body. The server MUST
     * preserve the information provided by the client in the 'owner' element in the LOCK request. The LOCK request MAY
     * have a Timeout header.
     * <p>
     * When a new lock is created, the LOCK response:
     * <p>
     * o MUST contain a body with the value of the DAV:lockdiscovery property in a prop XML element. This MUST contain
     * the full information about the lock just granted, while information about other (shared) locks is OPTIONAL.
     * <p>
     * o MUST include the Lock-Token response header with the token associated with the new lock.
     */
    @Test
    public void testRfc_9_10_1() throws Exception {
        Context ctx = prepareContext("rfc_9_10_1");
        prepareWebdav(ctx, true, false);

        String newDir = UUID.randomUUID().toString();
        Assert.assertTrue(new File(getWebappAbsolutePath(), newDir).mkdir());

        getTomcatInstance().start();

        String mappedUrl = "/" + newDir;
        WebdavClient client = new WebdavClient();
        client.setPort(getPort());

        String lockOwner = "Owner_Rfc_9_10_1";
        /* Lock on a mapped url */
        client.lockResource(mappedUrl, lockOwner, true, "infinity", "Unexpected status coude.",
                c -> c.isResponse200() || c.isResponse204());
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody().contains("opaquelocktoken:"));

        Assert.assertTrue(
                "9.10.1. Creating a Lock on an Existing Resource - MUST contain a body with the value of the DAV:lockdiscovery property in a prop XML element",
                client.getResponseBody().contains(":lockdiscovery"));
        Assert.assertTrue(
                "9.10.1. Creating a Lock on an Existing Resource - MUST contain the full information about the lock just granted",
                client.getResponseBody().contains(":owner") && client.getResponseBody().contains(lockOwner));
    }

    /**
     * 9.10.2. Refreshing Locks
     */
//    @Test
    public void testRfc_9_10_2() throws Exception {
        Context ctx = prepareContext("rfc_9_10_2");
        prepareWebdav(ctx, true, false);
        String newDir = UUID.randomUUID().toString();
        Assert.assertTrue(new File(getWebappAbsolutePath(), newDir).mkdir());

        getTomcatInstance().start();
        String mappedUrl = "/" + newDir;
        WebdavClient client = new WebdavClient();
        client.setPort(getPort());

        String lockOwner = "Owner_Rfc_9_10_2";

        /* Lock on a mapped url */
        String lockToken = client.lockResource(mappedUrl, lockOwner, true, null, "Unexpected status coude.",
                c -> c.isResponse200() || c.isResponse204());
        Thread.sleep(1000L);
        /**
         * Refreshing
         */
        /*
         * This request MUST NOT have a body and it MUST specify which lock to refresh by using the 'If' header with a
         * single lock token (only one lock may be refreshed at a time).
         */
        /* Negative case: With wrong lock token */
        client.refreshResourceLock(mappedUrl, "(<"+lockToken+"z>)", "9.10.2. Refreshing Locks - This request MUST NOT have a body and it MUST specify which lock to refresh by using the 'If' header with a single lock token (only one lock may be refreshed at a time).",r->r.getStatusCode()>=400);
        /* Negative case: With body */
        String lockBody = buildLockBody(true, lockOwner);
        client.setRequest(new String[] { "LOCK " + mappedUrl + " HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF + "Content-Length: " + lockBody.length() +
                SimpleHttpClient.CRLF + "Connection: Close" + SimpleHttpClient.CRLF + "If: (<" + lockToken + ">)" +
                SimpleHttpClient.CRLF + SimpleHttpClient.CRLF + lockBody });

        client.connect();
        client.processRequest(true);
        Assert.assertTrue(
                "9.10.2. Refreshing Locks - This request MUST NOT have a body and it MUST specify which lock to refresh by using the 'If' header with a single lock token (only one lock may be refreshed at a time).",
                client.getStatusCode() >= 400);

        /*
         * Positive case
         */
        client.refreshResourceLock(mappedUrl, "(<" + lockToken + ">)", "Unexpected status coude.",
                c -> c.isResponse200());
        /*
         * The Lock-Token header is not returned in the response for a successful refresh LOCK request, but the LOCK
         * response body MUST contain the new value for the DAV:lockdiscovery property.
         */
        Assert.assertTrue("9.10.2. Refreshing Locks - MUST contain the new value for the DAV:lockdiscovery property",
                client.getResponseBody().contains(":lockdiscovery"));

        /* A server MUST ignore the Depth header on a LOCK refresh. */
        /*
         * see rfc4918 - 10.2. Depth Header
         * 
         * Depth = "Depth" ":" ("0" | "1" | "infinity")
         */
        client.setRequest(new String[] { "LOCK " + mappedUrl + " HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF + "Connection: Close" + SimpleHttpClient.CRLF +
                "If: (<" + lockToken + ">)" + SimpleHttpClient.CRLF + "Depth: infinity" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });

        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue("", client.getResponseBody().contains(lockToken));
        Assert.assertTrue("9.10.2. Refreshing Locks - MUST contain the new value for the DAV:lockdiscovery property",
                client.getResponseBody().contains(":lockdiscovery"));
        /* Depth: 0 */
        client.setRequest(new String[] {
                "LOCK " + mappedUrl + " HTTP/1.1" + SimpleHttpClient.CRLF + "Host: localhost:" + getPort() +
                        SimpleHttpClient.CRLF + "Connection: Close" + SimpleHttpClient.CRLF + "If: (<" + lockToken +
                        ">)" + SimpleHttpClient.CRLF + "Depth: 0" + SimpleHttpClient.CRLF + SimpleHttpClient.CRLF });

        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        /* Depth: 1 */
        client.setRequest(new String[] {
                "LOCK " + mappedUrl + " HTTP/1.1" + SimpleHttpClient.CRLF + "Host: localhost:" + getPort() +
                        SimpleHttpClient.CRLF + "Connection: Close" + SimpleHttpClient.CRLF + "If: (<" + lockToken +
                        ">)" + SimpleHttpClient.CRLF + "Depth: 1" + SimpleHttpClient.CRLF + SimpleHttpClient.CRLF });

        client.connect();
        client.processRequest(true);
        Assert.assertEquals("9.10.2. Refreshing Locks - Depth header `1` is invalid for LOCK.", WebdavStatus.SC_BAD_REQUEST, client.getStatusCode());
        /* Depth: 2 */
        client.setRequest(new String[] {
                "LOCK " + mappedUrl + " HTTP/1.1" + SimpleHttpClient.CRLF + "Host: localhost:" + getPort() +
                        SimpleHttpClient.CRLF + "Connection: Close" + SimpleHttpClient.CRLF + "If: (<" + lockToken +
                        ">)" + SimpleHttpClient.CRLF + "Depth: 2" + SimpleHttpClient.CRLF + SimpleHttpClient.CRLF });

        client.connect();
        client.processRequest(true);
        Assert.assertEquals("9.10.2. Refreshing Locks - Depth header `2` is invalid.", WebdavStatus.SC_BAD_REQUEST, client.getStatusCode());
        /* Depth: Hello, world */
        client.setRequest(new String[] { "LOCK " + mappedUrl + " HTTP/1.1" + SimpleHttpClient.CRLF +
                "Host: localhost:" + getPort() + SimpleHttpClient.CRLF + "Connection: Close" + SimpleHttpClient.CRLF +
                "If: (<" + lockToken + ">)" + SimpleHttpClient.CRLF + "Depth: Hello, world" + SimpleHttpClient.CRLF +
                SimpleHttpClient.CRLF });

        client.connect();
        client.processRequest(true);
        Assert.assertEquals("9.10.2. Refreshing Locks - Depth header `Hello, world` is invalid.", WebdavStatus.SC_BAD_REQUEST, client.getStatusCode());
    }

    /**
     * 9.10.3. Depth and Locking
     * <p>
     * The Depth header may be used with the LOCK method. Values other than 0 or infinity MUST NOT be used with the
     * Depth header on a LOCK method. All resources that support the LOCK method MUST support the Depth header.
     * <p>
     * A Depth header of value 0 means to just lock the resource specified by the Request-URI.
     * <p>
     * If the Depth header is set to infinity, then the resource specified in the Request-URI along with all its
     * members, all the way down the hierarchy, are to be locked. A successful result MUST return a single lock token.
     * Similarly, if an UNLOCK is successfully executed on this token, all associated resources are unlocked. Hence,
     * partial success is not an option for LOCK or UNLOCK. Either the entire hierarchy is locked or no resources are
     * locked.
     * <p>
     * If the lock cannot be granted to all resources, the server MUST return a Multi-Status response with a 'response'
     * element for at least one resource that prevented the lock from being granted, along with a suitable status code
     * for that failure (e.g., 403 (Forbidden) or 423 (Locked)). Additionally, if the resource causing the failure was
     * not the resource requested, then the server SHOULD include a 'response' element for the Request-URI as well, with
     * a 'status' element containing 424 Failed Dependency.
     * <p>
     * If no Depth header is submitted on a LOCK request, then the request MUST act as if a "Depth:infinity" had been
     * submitted.
     */
//    @Test
    public void testRfc_9_10_3() throws Exception {
        Context ctx = prepareContext("rfc_9_10_3");
        prepareWebdav(ctx, true, false);

        String dir1 = UUID.randomUUID().toString();
        Assert.assertTrue(new File(getWebappAbsolutePath(), dir1).mkdir());
        String dir11 = UUID.randomUUID().toString();
        Assert.assertTrue(new File(getWebappAbsolutePath() + File.separator + dir1, dir11).mkdir());
        String dir2 = UUID.randomUUID().toString();
        Assert.assertTrue(new File(getWebappAbsolutePath(), dir2).mkdir());
        String dir3 = UUID.randomUUID().toString();
        Assert.assertTrue(new File(getWebappAbsolutePath(), dir3).mkdir());

        getTomcatInstance().start();
        WebdavClient client = new WebdavClient();
        client.setPort(getPort());

        String mappedUrl1 = "/" + dir1;
        String mappedUrl11 = "/" + dir1 + "/" + dir11;
        String mapperUrl2 = "/" + dir2;
        String mapperUrl3 = "/" + dir3;

        String lockOwner = "Owner_Rfc_9_10_3";
        String lockToken1, lockToken11, lockToken2, lockToken3;
        /*
         * see rfc4918 - 10.2. Depth Header
         * 
         * Depth = "Depth" ":" ("0" | "1" | "infinity")
         */
        /*
         * Invalid Depth header request:Values other than 0 or infinity MUST NOT be used
         */
        lockToken1 = client.lockResource(mappedUrl1, lockOwner, true, "-1",
                "9.10.3. Depth and Locking - The Depth header may be used with the LOCK method. Values other than 0 or infinity MUST NOT used with the Depth header on a LOCK method. 4xx status coude expected",
                c -> c.getStatusCode() >= 400 && c.getStatusCode() < 500);
        Assert.assertNull(lockToken1);

        lockToken1 = client.lockResource(mappedUrl1, lockOwner, true, "1",
                "9.10.3. Depth and Locking - The Depth header may be used with the LOCK method. Values other than 0 or infinity MUST NOT used with the Depth header on a LOCK method. 4xx status coude expected",
                c -> c.getStatusCode() >= 400 && c.getStatusCode() < 500);
        Assert.assertNull(lockToken1);

        lockToken1 = client.lockResource(mappedUrl1, lockOwner, true, "2",
                "9.10.3. Depth and Locking - The Depth header may be used with the LOCK method. Values other than 0 or infinity MUST NOT used with the Depth header on a LOCK method. 4xx status coude expected",
                c -> c.getStatusCode() >= 400 && c.getStatusCode() < 500);
        Assert.assertNull(lockToken1);


        /*
         * If no Depth header is submitted on a LOCK request, then the request MUST act as if a "Depth:infinity" had
         * been submitted.
         */
        lockToken1 = client.lockResource(mappedUrl1, lockOwner, true, null, "2xx status coude expected",
                c -> c.getStatusCode() < 300);
        // Check sub dir is locked
        lockToken11 = client.lockResource(mappedUrl11, lockOwner, true, "0",
                WebdavStatus.SC_LOCKED + " status coude expected", c -> c.getStatusCode() == WebdavStatus.SC_LOCKED);
        Assert.assertNull("9.10.3. Depth and Locking - parent resource has been locked.", lockToken11);


        Assert.assertNotNull(lockToken1);
        lockToken2 = client.lockResource(mapperUrl2, lockOwner, true, "infinity", "2xx status coude expected",
                c -> c.getStatusCode() < 300);
        Assert.assertNotNull(lockToken2);
        lockToken3 = client.lockResource(mapperUrl3, lockOwner, true, "0", "2xx status coude expected",
                c -> c.getStatusCode() < 300);
        Assert.assertNotNull(lockToken3);

        client.unlockResource(mappedUrl1, lockToken1, "Unexpected", c -> c.getStatusCode() < 300);


        /* Lock response Multi-Status response */
        lockToken11 = client.lockResource(mappedUrl11, lockOwner, true, "0", "2xx status coude expected",
                c -> c.getStatusCode() < 300);
        Assert.assertNotNull(lockToken11);

        lockToken1 = client.lockResource(mappedUrl1, lockOwner + "-Copy", true, "infinity",
                "9.10.3. Depth and Locking - the server MUST return a Multi-Status response with a 'response element for at least one resource that prevented the lock from being granted",
                c -> {
                    String body = client.getResponseBody();
                    return c.getStatusCode() == 207 && body.contains(dir11) && body.contains(dir1) &&
                            body.contains(WebdavStatus.SC_FAILED_DEPENDENCY + "") &&
                            body.contains(WebdavStatus.SC_LOCKED + "");
                });

        Assert.assertNull(lockToken1);

    }

    /**
     * 9.10.4. Locking Unmapped URLs
     * <p>
     * A successful LOCK method MUST result in the creation of an empty resource that is locked (and that is not a
     * collection) when a resource did not previously exist at that URL. Later on, the lock may go away but the empty
     * resource remains. Empty resources MUST then appear in PROPFIND responses including that URL in the response
     * scope. A server MUST respond successfully to a GET request to an empty resource, either by using a 204 No Content
     * response, or by using 200 OK with a Content-Length header indicating zero length
     */
    @Test
    public void testRfc_9_10_4() throws Exception {
        // Locking Unmapped URLs
        Context ctx = prepareContext("rfc_9_10_4");
        prepareWebdav(ctx, true, false);

        getTomcatInstance().start();
        WebdavClient client = new WebdavClient();
        client.setPort(getPort());

        String unmappedUrl = "/" + UUID.randomUUID().toString();

        String lockOwner = "Owner_Rfc_9_10_4";

        /* Lock on a not exist file */
        /*
         * 7.3. Write Locks and Unmapped URLs o The response MUST indicate that a resource was created, by use of the
         * "201 Created" response code (a LOCK request to an existing resource instead will result in 200 OK). The body
         * must still include the DAV:lockdiscovery property, as with a LOCK request to an existing resource.
         */
        String lockToken = client.lockResource(unmappedUrl, lockOwner, true, "0", "Unsupported status code.",
                c -> c.getStatusCode() == WebdavStatus.SC_CREATED);

        Assert.assertNotNull(lockToken);
        Assert.assertTrue(client.getResponseBody().contains("opaquelocktoken:"));
        Assert.assertTrue(client.getResponseBody().contains("lockdiscovery"));

        /* lock go away - Unlock it */
        client.unlockResource(unmappedUrl, lockToken, "Unexpected status code.",
                c -> HttpServletResponse.SC_OK == c.getStatusCode() ||
                        HttpServletResponse.SC_NO_CONTENT == c.getStatusCode());

        client.setRequest(
                new String[] { "PROPFIND / HTTP/1.1" + SimpleHttpClient.CRLF + "Host: localhost:" + getPort() +
                        SimpleHttpClient.CRLF + "Connection: Close" + SimpleHttpClient.CRLF + SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        Assert.assertTrue(
                "9.10.4. Locking Unmapped URLs - Empty resources MUST then appear in PROPFIND responses including that URL in the response scope.",
                client.getResponseBody().contains(unmappedUrl));

        client.setRequest(new String[] {
                "GET " + unmappedUrl + " HTTP/1.1" + SimpleHttpClient.CRLF + "Host: localhost:" + getPort() +
                        SimpleHttpClient.CRLF + "Connection: Close" + SimpleHttpClient.CRLF + SimpleHttpClient.CRLF });
        client.connect();
        client.processRequest(true);
        int rc;
        rc = client.getStatusCode();
        Assert.assertTrue(
                "9.10.4. Locking Unmapped URLs - A server MUST respond successfully to a GET request to an empty resource, either by using a 204 No Content response, or by using 200 OK with a Content-Length header indicating zero length",
                WebdavStatus.SC_OK == rc || WebdavStatus.SC_NO_CONTENT == rc);
        if (rc == WebdavStatus.SC_OK) {
            // Check content length zero
            boolean zeroContentLengthHeaderFound = false;
            for (String header : client.getResponseHeaders()) {
                if (header.equalsIgnoreCase("Content-Length: 0")) {
                    zeroContentLengthHeaderFound = true;
                }
            }
            Assert.assertTrue(
                    "9.10.4. Locking Unmapped URLs - A server MUST respond successfully to a GET request to an empty resource, either by using a 204 No Content response, or by using 200 OK with a Content-Length header indicating zero length",
                    zeroContentLengthHeaderFound);
        }

    }

    /**
     * 9.10.5. Lock Compatibility Table
     * <p>
     * The table below describes the behavior that occurs when a lock request is made on a resource.
     * <p>
     * 
     * <pre>
     * +--------------------------+----------------+-------------------+
     * | Current State            | Shared Lock OK | Exclusive Lock OK |
     * +--------------------------+----------------+-------------------+
     * | None                     | True           | True              |
     * | Shared Lock              | True           | False             |
     * | Exclusive Lock           | False          | False*            |
     * +--------------------------+----------------+-------------------+
     * </pre>
     * 
     * Legend: True = lock may be granted. False = lock MUST NOT be granted. *=It is illegal for a principal to request
     * the same lock twice. The current lock state of a resource is given in the leftmost column, and lock requests are
     * listed in the first row. The intersection of a row and column gives the result of a lock request. For example, if
     * a shared lock is held on a resource, and an exclusive lock is requested, the table entry is "false", indicating
     * that the lock must not be granted.
     */
    @Test
    public void testRfc_9_10_5() throws Exception {
        Context ctx = prepareContext("rfc_9_10_5");
        prepareWebdav(ctx, true, false);

        String collection1 = UUID.randomUUID().toString();
        Assert.assertTrue(new File(getWebappAbsolutePath(), collection1).mkdir());
        String collection11 = UUID.randomUUID().toString();
        Assert.assertTrue("Failed to mkdir of ",
                new File(getWebappAbsolutePath() + File.separator + collection1, collection11).mkdir());

        String collection12 = UUID.randomUUID().toString();
        Assert.assertTrue("Failed to mkdir of ",
                new File(getWebappAbsolutePath() + File.separator + collection1, collection12).mkdir());

        String collection13 = UUID.randomUUID().toString();
        Assert.assertTrue("Failed to mkdir of ",
                new File(getWebappAbsolutePath() + File.separator + collection1, collection13).mkdir());

        getTomcatInstance().start();
        WebdavClient client = new WebdavClient();
        client.setPort(getPort());

        String lockOwner = "Owner_Rfc_9_10_5";
        String sharedLockToken11, sharedLockToken11c, sharedLockToken12;
        String exclusiveLockToken11, exclusiveLockToken12, exclusiveLockToken12c;

        String resourceUri = "/" + collection1 + "/" + collection11;

        sharedLockToken11 = client.lockResource(resourceUri, lockOwner + "_shared11", false, "infinity",
                "Unexpected response", r -> r.isResponse200() || r.isResponse201());

        // 9.10.5. Lock Compatibility Table - True = lock may be granted.
        // That is, second shared lock request on a Shared-Locked resource: may be or may be not granted.
        sharedLockToken11c =
                client.lockResource(resourceUri, lockOwner + "_shared11c", false, "infinity", "Unexpected response",
                        r -> r.isResponse200() || r.isResponse201() || WebdavStatus.SC_LOCKED == r.getStatusCode());
        if (Objects.isNull(sharedLockToken11c)) {
            Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());
        }

        // 9.10.5. Lock Compatibility Table - False = lock MUST NOT be granted.
        // Exclusive lock request on a Shared-Locked resource: MUST not be granted
        exclusiveLockToken11 = client.lockResource(resourceUri, lockOwner + "_exclusive11", true, "infinity",
                "Unexpected response", r -> WebdavStatus.SC_LOCKED == r.getStatusCode());
        Assert.assertNull(
                "9.10.5. Lock Compatibility Table - MUST NOT grant exclusive lock on a resource having shared lock.",
                exclusiveLockToken11);

        resourceUri = "/" + collection1 + "/" + collection12;
        exclusiveLockToken12 = client.lockResource(resourceUri, lockOwner + "_exclusive12", true, "infinity",
                "Unexpected response", r -> r.isResponse200() || r.isResponse201());
        sharedLockToken12 = client.lockResource(resourceUri, lockOwner + "_shared12", false, "infinity",
                "Unexpected response", r -> WebdavStatus.SC_LOCKED == r.getStatusCode());
        Assert.assertNull(
                "9.10.5. Lock Compatibility Table - MUST NOT grant shared lock on a resource having exclusive lock.",
                exclusiveLockToken11);
        exclusiveLockToken12c =
                client.lockResource(resourceUri, lockOwner + "_exclusive12", true, "infinity", "Unexpected response",
                        r -> r.isResponse200() || r.isResponse201() || WebdavStatus.SC_LOCKED == r.getStatusCode());
        if (Objects.isNull(exclusiveLockToken12c)) {
            Assert.assertEquals(WebdavStatus.SC_LOCKED, client.getStatusCode());
        }
    }

    /**
     * 9.10.6. LOCK Responses
     * <p>
     */
    @Test
    public void testRfc_9_10_6() throws Exception {
        Context ctx = prepareContext("rfc_9_10_6");
        prepareWebdav(ctx, true, false);

        String collection1 = UUID.randomUUID().toString();
        Assert.assertTrue(new File(getWebappAbsolutePath(), collection1).mkdir());
        String collection11 = UUID.randomUUID().toString();
        Assert.assertTrue("Failed to mkdir of ",
                new File(getWebappAbsolutePath() + File.separator + collection1, collection11).mkdir());

        String collection12 = UUID.randomUUID().toString();
        Assert.assertTrue("Failed to mkdir of ",
                new File(getWebappAbsolutePath() + File.separator + collection1, collection12).mkdir());

        String collection13 = UUID.randomUUID().toString();
        Assert.assertTrue("Failed to mkdir of ",
                new File(getWebappAbsolutePath() + File.separator + collection1, collection13).mkdir());

        getTomcatInstance().start();
        WebdavClient client = new WebdavClient();
        client.setPort(getPort());

        String lockOwner = "Owner_Rfc_9_10_6";

        String resourceUri = "/" + collection1 + "/" + collection11;

        /* 200 */
        String sharedLockToken11 = client.lockResource(resourceUri, lockOwner + "_shared11", false, "infinity",
                "9.10.6. LOCK Responses - 200 (OK)", r -> r.isResponse200());
        Assert.assertNotNull(sharedLockToken11);
        /* 201 */
        resourceUri = "/" + collection1 + "/" + collection12 + "/EMPTY_RESOURCE.html";// Empty resource
        String sharedLockToken121 = client.lockResource(resourceUri, lockOwner + "_shared11", false, "infinity",
                "9.10.6. LOCK Responses - 201 (Created)", r -> WebdavStatus.SC_CREATED == r.getStatusCode());
        Assert.assertNotNull("9.10.6. LOCK Responses - 201 (Created)", sharedLockToken11);

        /* 409 */
        resourceUri = "/" + collection1 + "/" + collection12 + "/409/EMPTY_RESOURCE.html";
        String sharedLockToken1221 = client.lockResource(resourceUri, lockOwner + "_shared11", false, "infinity",
                "9.10.6.  LOCK Responses - 409 (Conflict) - A resource cannot be created at the destination until one or more intermediate collections have been created. The server MUST NOT create those intermediate collections automatically.",
                r -> WebdavStatus.SC_CONFLICT == r.getStatusCode());
        Assert.assertNull(
                "9.10.6.  LOCK Responses - 409 (Conflict) - A resource cannot be created at the destination until one or more intermediate collections have been created. The server MUST NOT create those intermediate collections automatically.",
                sharedLockToken1221);

        /* 423:see rfc 9_10_5 */
        /* 412 */
        resourceUri = "/" + collection1 + "/" + collection13;
        client.refreshResourceLock(resourceUri, "(<" + sharedLockToken11 + ">)",
                "9.10.6. LOCK Responses - 412 (Precondition Failed)",
                r -> WebdavStatus.SC_PRECONDITION_FAILED == r.getStatusCode());
    }

}