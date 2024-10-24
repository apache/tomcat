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

public class TestWebdavServletRfcSection extends WebdavServletRfcSectionBase {

    @Test
    public void testCopyAndWiteFile() throws Exception {
        Context ctx = prepareContext("testCopyAndWiteFile");
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

        String file111 = "file111.txt";
        try (FileWriter fw = new FileWriter(new File(
                getWebappAbsolutePath() + File.separator + collection1 + File.separator + collection11, file111))) {
            fw.write("file111.txt-v1");
        }

        getTomcatInstance().start();

        WebdavClient client = new WebdavClient();
        client.setPort(getPort());
        String srcUri = "/" + collection1 + "/" + collection11 + "/" + file111;
        String file121 = "file121.txt";
        String destUri = "/" + collection1 + "/" + collection12 + "/" + file121;

        String lockOwner = "Owner_Rfc_testCopyAndWiteFile";
        /*
         * copy single file
         */
        String exclusiveLockToken01 = client.lockResource("/" + collection1 + "/" + collection12, lockOwner, true,
                "infinity", "Unexpected response", null);
        String srcContent = client.getResource(srcUri, "Unexpected response",
                r -> r.getStatusCode() >= 200 && r.getStatusCode() < 300);
        client.copyResource(srcUri, destUri, true, exclusiveLockToken01, "Unexpected response",
                r -> r.getStatusCode() >= 200 && r.getStatusCode() < 300);
        String destContentV0 = client.getResource(destUri, "Unexpected response",
                r -> r.getStatusCode() >= 200 && r.getStatusCode() < 300);
        Assert.assertEquals("dest file content check.", srcContent, destContentV0);

        String etagValue = matchHeaders(client.getResponseHeaders(), h -> h.toLowerCase().startsWith("etag")).get(0)
                .substring("etag: ".length());

        /* write content with token and ETag */
        String dstContent = srcContent + "\r\nfile121.txt-v2";
        client.putResource(destUri, "text/plain", dstContent, "(<" + exclusiveLockToken01 + "> [" + etagValue + "])",
                "Unexpected response", null);
        Assert.assertEquals("dest file content check.", dstContent,
                client.getResource(destUri, "Unexpected response", null));
        /* write content with token (and absence of ETag) */
        dstContent += "\r\nnfile121.txt-v3";
        client.putResource(destUri, "text/plain", dstContent, "(<" + exclusiveLockToken01 + ">)", "Unexpected response",
                null);
        Assert.assertEquals("dest file content check.", dstContent,
                client.getResource(destUri, "Unexpected response", null));

        client.unlockResource(destUri, exclusiveLockToken01, "Unexpected response", null);

        /*
         * copy collection, and modify file content.
         */
        srcUri = "/" + collection1 + "/" + collection11;
        String colleciton13 = UUID.randomUUID().toString();
        destUri = "/" + collection1 + "/" + colleciton13;
        client.copyResource(srcUri, destUri, true, null, "Unexpected response",
                r -> r.getStatusCode() >= 200 && r.getStatusCode() < 300);
        dstContent = client.getResource(destUri + "/" + file111, "Unexpected response", null);
        Assert.assertEquals("dest file content check.", srcContent, dstContent);
        etagValue = matchHeaders(client.getResponseHeaders(), h -> h.toLowerCase().startsWith("etag")).get(0)
                .substring("etag: ".length());
        dstContent += "\r\nv2";
        Assert.assertNotNull(etagValue);

        destUri = "/" + collection1 + "/" + colleciton13 + "/" + file111;
        exclusiveLockToken01 = client.lockResource(destUri, lockOwner, true, "infinity", "Unexpected response", null);
        client.putResource(destUri, "text/plain", dstContent, "(<" + exclusiveLockToken01 + "> [" + etagValue + "])",
                "Unexpected response", null);
        Assert.assertEquals("dest file content check.", dstContent,
                client.getResource(destUri, "Unexpected response", null));
    }

    /**
     * WebDAV clients can be good citizens by using a lock / retrieve / write /unlock sequence of operations (at least
     * by default) whenever they interact with a WebDAV server that supports locking.
     * 
     * @throws Exception
     */
    // @Test
    public void testWriteFileByLockCollection() throws Exception {
        Context ctx = prepareContext("testWriteFileByLockCollection");
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
        /*
         * 1. lock collection exclusively.
         */
        String exclusiveLockToken01 = client.lockResource("/" + collection1, "biz01_exclusive_01", true, "infinity",
                "Unexpected status code.", null);
        String file111 = "file111.txt";
        /* 1.1 Unable write without ifHeader of lock */
        client.putResource("/" + collection1 + "/" + collection11 + "/" + file111, "text/plain", "README_v1", null,
                "Unexpected status code.", c -> WebdavStatus.SC_LOCKED == c.getStatusCode());
        /* 1.2 Unable acquire for a shared lock on sub resource */
        String sharedLockToken01 = client.lockResource("/" + collection1 + "/" + collection11, "biz01_shared_01", false,
                "infinity", "Unexpected status code.", c -> WebdavStatus.SC_LOCKED == c.getStatusCode());
        Assert.assertNull(sharedLockToken01);

        /* 1.3 Write content with exclusive lock */
        client.putResource("/" + collection1 + "/" + collection11 + "/" + file111, "text/plain", "README_v1",
                "(<" + exclusiveLockToken01 + ">)", "Unexpected status code.", null);
        client.unlockResource("/" + collection1 + "/" + collection11 + "/" + file111, exclusiveLockToken01,
                "Unexpected status code.", c -> c.isResponse204());
        // verify lock removed entirely: try to acquire a shared lock
        sharedLockToken01 = client.lockResource("/" + collection1 + "/" + collection11, "biz01_shared_01", true,
                "infinity", "Unexpected status code.", null);
        Assert.assertNotNull("SharedLock successfully expected.", sharedLockToken01);
        /* Cleanup */
        client.unlockResource("/" + collection1 + "/" + collection11, sharedLockToken01, "Unexpected status code.",
                c -> c.isResponse204());
    }

    /**
     * WebDAV clients can be good citizens by using a lock / retrieve / write /unlock sequence of operations (at least
     * by default) whenever they interact with a WebDAV server that supports locking.
     * 
     * @throws Exception
     */
    // @Test
    public void testWriteFileByLockSingleResource() throws Exception {
        Context ctx = prepareContext("testWriteFileByLockSingleResource");
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

        /*
         * 2. lock a single file
         */
        String file112 = "file112.txt";
        String sharedLockToken01 = client.lockResource("/" + collection1 + "/" + collection11 + "/" + file112,
                "biz01_shared_01", false, "0", "Unexpected status code.", null);
        client.putResource("/" + collection1 + "/" + collection11 + "/" + file112, "text/plain", "README_v1",
                "(<" + sharedLockToken01 + ">)", "Unexpected status code.", null);
        String sharedLockToken02 = client.lockResource("/" + collection1 + "/" + collection11 + "/" + file112,
                "biz01_shared_02", false, "0", "Unexpected status code.", null);
        client.putResource("/" + collection1 + "/" + collection11 + "/" + file112, "text/plain", "README_v2",
                "(<" + sharedLockToken02 + ">)", "Unexpected status code.", null);
        // Check file content
        Assert.assertEquals("Content check", "README_v2", client
                .getResource("/" + collection1 + "/" + collection11 + "/" + file112, "2xx status expected.", null));
    }
}
