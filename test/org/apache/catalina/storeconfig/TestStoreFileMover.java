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
package org.apache.catalina.storeconfig;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.TomcatBaseTest;

/**
 * Tests for {@link StoreFileMover}.
 */
public class TestStoreFileMover extends TomcatBaseTest {

    @Test
    public void testConstructorWithParameters() {
        File tmpDir = getTemporaryDirectory();
        String basename = tmpDir.getAbsolutePath();

        StoreFileMover mover = new StoreFileMover(basename, "conf/server.xml", "UTF-8");

        Assert.assertEquals(basename, mover.getBasename());
        Assert.assertEquals("conf/server.xml", mover.getFilename());
        Assert.assertEquals("UTF-8", mover.getEncoding());

        Assert.assertNotNull(mover.getConfigOld());
        Assert.assertNotNull(mover.getConfigNew());
        Assert.assertNotNull(mover.getConfigSave());
    }


    @Test
    public void testGetSetProperties() {
        File tmpDir = getTemporaryDirectory();
        String basename = tmpDir.getAbsolutePath();

        StoreFileMover mover = new StoreFileMover(basename, "conf/server.xml", "UTF-8");

        mover.setEncoding("ISO-8859-1");
        Assert.assertEquals("ISO-8859-1", mover.getEncoding());

        mover.setFilename("conf/context.xml");
        Assert.assertEquals("conf/context.xml", mover.getFilename());

        mover.setBasename("/new/base");
        Assert.assertEquals("/new/base", mover.getBasename());
    }


    @Test
    public void testConfigFileObjects() {
        File tmpDir = getTemporaryDirectory();
        String basename = tmpDir.getAbsolutePath();

        StoreFileMover mover = new StoreFileMover(basename, "conf/server.xml", "UTF-8");

        File configOld = mover.getConfigOld();
        File configNew = mover.getConfigNew();
        File configSave = mover.getConfigSave();

        Assert.assertTrue("configOld should be under basename",
                configOld.getAbsolutePath().contains("conf" + File.separator + "server.xml"));
        Assert.assertTrue("configNew should have .new suffix",
                configNew.getName().endsWith(".new"));
        Assert.assertNotNull("configSave should not be null", configSave);
    }


    @Test
    public void testGetWriter() throws Exception {
        File tmpDir = getTemporaryDirectory();
        String basename = tmpDir.getAbsolutePath();

        StoreFileMover mover = new StoreFileMover(basename, "conf/server.xml", "UTF-8");

        // Ensure the .new file's parent directory exists for the writer
        File configNew = mover.getConfigNew();
        configNew.getParentFile().mkdirs();

        try (PrintWriter writer = mover.getWriter()) {
            Assert.assertNotNull(writer);
            writer.print("test content");
        }

        // Cleanup
        configNew.delete();
    }


    @Test
    public void testMoveNewToOld() throws Exception {
        File tmpDir = getTemporaryDirectory();
        String basename = tmpDir.getAbsolutePath();

        // Create a fresh config file pair
        String configPath = "test-server.xml";
        StoreFileMover mover = new StoreFileMover(basename, configPath, "UTF-8");

        File configNew = mover.getConfigNew();
        configNew.getParentFile().mkdirs();

        // Write content to new file
        try (PrintWriter pw = mover.getWriter()) {
            pw.print("<Server/>");
        }

        Assert.assertTrue("configNew should exist before move", configNew.exists());

        // Move: since configOld doesn't exist, new->old
        mover.move();

        File configOld = mover.getConfigOld();
        Assert.assertTrue("configOld should exist after move", configOld.exists());
        Assert.assertFalse("configNew should not exist after move", configNew.exists());

        // Cleanup
        configOld.delete();
    }


    @Test
    public void testMoveWithExistingOld() throws Exception {
        File tmpDir = getTemporaryDirectory();
        String basename = tmpDir.getAbsolutePath();

        // Create config file pair
        String configPath = "test-server2.xml";
        StoreFileMover mover = new StoreFileMover(basename, configPath, "UTF-8");

        File configOld = mover.getConfigOld();
        File configNew = mover.getConfigNew();

        configOld.getParentFile().mkdirs();

        // Create the old file
        try (PrintWriter pw = new PrintWriter(configOld)) {
            pw.print("<Server>old</Server>");
        }

        // Create the new file
        try (PrintWriter pw = mover.getWriter()) {
            pw.print("<Server>new</Server>");
        }

        Assert.assertTrue(configOld.exists());
        Assert.assertTrue(configNew.exists());

        mover.move();

        Assert.assertTrue("configOld should still exist (renamed from new)",
                configOld.exists());
        Assert.assertFalse("configNew should not exist after move",
                configNew.exists());

        File configSave = mover.getConfigSave();
        Assert.assertTrue("configSave should exist (backup of old)",
                configSave.exists());

        // Cleanup
        configOld.delete();
        configSave.delete();
    }


    @Test
    public void testMoveFailsWhenNewCannotRename() throws Exception {
        File tmpDir = getTemporaryDirectory();
        String basename = tmpDir.getAbsolutePath();

        StoreFileMover mover = new StoreFileMover(basename, "nonexistent-move.xml", "UTF-8");

        // Neither old nor new exists — should throw IOException
        try {
            mover.move();
            Assert.fail("Expected IOException");
        } catch (IOException e) {
            // Expected
            Assert.assertNotNull(e.getMessage());
        }
    }
}
