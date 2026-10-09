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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

public class TestStoreFileMover {

    /**
     * Verify the regular swap: the new content is installed, the old content is preserved in the backup and the
     * intermediate file is consumed.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testMoveWithBackup() throws Exception {
        Path dir = Files.createTempDirectory("storeFileMover");
        try {
            Files.writeString(dir.resolve("server.xml"), "old");
            StoreFileMover mover = new StoreFileMover(dir.toString(), "server.xml", "UTF-8");
            try (PrintWriter writer = mover.getWriter()) {
                writer.print("new");
            }
            mover.move();
            Assert.assertEquals("new", Files.readString(dir.resolve("server.xml")));
            Assert.assertFalse(Files.exists(dir.resolve("server.xml.new")));
            Path backup = mover.getConfigSave().toPath();
            Assert.assertTrue(Files.exists(backup));
            Assert.assertEquals("old", Files.readString(backup));
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Verify that without an existing configuration file, the new file is installed directly without creating a
     * backup.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testMoveWithoutExistingConfiguration() throws Exception {
        Path dir = Files.createTempDirectory("storeFileMover");
        try {
            StoreFileMover mover = new StoreFileMover(dir.toString(), "server.xml", "UTF-8");
            try (PrintWriter writer = mover.getWriter()) {
                writer.print("new");
            }
            mover.move();
            Assert.assertEquals("new", Files.readString(dir.resolve("server.xml")));
            Assert.assertFalse(Files.exists(mover.getConfigSave().toPath()));
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Verify that a failure to create the backup leaves the active configuration file in place and unchanged. The
     * backup is copied rather than renamed away, so no failure during the swap can leave the configuration file
     * missing.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testBackupFailureLeavesConfigurationInPlace() throws Exception {
        Path dir = Files.createTempDirectory("storeFileMover");
        try {
            Files.writeString(dir.resolve("server.xml"), "old");
            StoreFileMover mover = new StoreFileMover(dir.toString(), "server.xml", "UTF-8");
            try (PrintWriter writer = mover.getWriter()) {
                writer.print("new");
            }
            // Block the backup location so that the backup copy fails
            Files.createDirectory(mover.getConfigSave().toPath());
            try {
                mover.move();
                Assert.fail("move() should have failed");
            } catch (IOException ioe) {
                // Expected, and it must report the failed backup copy
                Assert.assertTrue(ioe.getMessage(), ioe.getMessage().contains("Failed to copy"));
            }
            Assert.assertEquals("old", Files.readString(dir.resolve("server.xml")));
            Assert.assertTrue(Files.exists(dir.resolve("server.xml.new")));
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(path);
            }
        }
    }
}
