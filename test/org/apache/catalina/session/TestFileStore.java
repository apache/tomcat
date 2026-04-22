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
package org.apache.catalina.session;

import java.io.File;
import java.io.IOException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.startup.ExpandWar;
import org.apache.tomcat.unittest.TesterContext;
import org.apache.tomcat.unittest.TesterServletContext;

public class TestFileStore {

    private static final String SESS_TEMPPATH = "SESS_TEMP";
    private static final File dir = new File(SESS_TEMPPATH);
    private static FileStore fileStore;
    private static final File file1 = new File(SESS_TEMPPATH + "/tmp1.session");
    private static final File file2 = new File(SESS_TEMPPATH + "/tmp2.session");
    private static final Manager manager = new StandardManager();


    @BeforeClass
    public static void setup() {
        TesterContext testerContext = new TesterContext();
        testerContext.setServletContext(new TesterServletContext());
        manager.setContext(testerContext);
        fileStore = new FileStore();
        fileStore.setManager(manager);
    }


    @AfterClass
    public static void cleanup() {
        ExpandWar.delete(dir);
    }


    @Before
    public void beforeEachTest() throws IOException {
        fileStore.setDirectory(SESS_TEMPPATH);
        if (!dir.exists() && !dir.mkdir()) {
            Assert.fail(dir.getAbsolutePath());
        }
        if (!file1.exists() && !file1.createNewFile()) {
            Assert.fail();
        }
        if (!file2.exists() && !file2.createNewFile()) {
            Assert.fail();
        }
    }


    @Test
    public void getSize() throws Exception {
        Assert.assertEquals(2, fileStore.getSize());
    }


    @Test
    public void clear() throws Exception {
        fileStore.clear();
        Assert.assertEquals(0, fileStore.getSize());
    }


    @Test
    public void keys() throws Exception {
        Assert.assertArrayEquals(new String[]{"tmp1", "tmp2"}, fileStore.keys());
        fileStore.clear();
        Assert.assertArrayEquals(new String[]{}, fileStore.keys());
    }


    @Test
    public void removeTest() throws Exception {
        fileStore.remove("tmp1");
        Assert.assertEquals(1, fileStore.getSize());
    }

    @Test
    public void pathTraversalSessionId() throws Exception {
        File storageDir = dir.getAbsoluteFile();
        File outsideFile = new File(storageDir.getParentFile(), "conf" + File.separator + "test.session");
        File outsideDir = outsideFile.getParentFile();
        boolean createdOutsideDir = false;
        if (!outsideDir.exists()) {
            Assert.assertTrue(outsideDir.mkdirs());
            createdOutsideDir = true;
        }
        Assert.assertTrue(outsideFile.createNewFile());

        try {
            Session session = fileStore.load("./../conf/test");
            Assert.assertNull(session);

            fileStore.remove("./../conf/test");
            Assert.assertTrue(outsideFile.exists());
        } finally {
            Assert.assertTrue(outsideFile.delete());
            if (createdOutsideDir) {
                Assert.assertTrue(outsideDir.delete());
            }
        }
    }
}