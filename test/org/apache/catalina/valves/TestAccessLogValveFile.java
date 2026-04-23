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
package org.apache.catalina.valves;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

/**
 * Tests for {@link AccessLogValve} file I/O, rotation, encoding, and cleanup
 * operations. Pattern-based access log format tests are covered by
 * {@link TestAccessLogValve}.
 */
public class TestAccessLogValveFile extends TomcatBaseTest {

    private File logDir;


    @Test
    public void testLogWritesToFile() throws Exception {
        createValve("access", Constants.AccessLog.COMBINED_ALIAS);
        getTomcatInstance().start();

        getUrl("http://localhost:" + getPort());

        File logFile = new File(logDir, "access.log");
        awaitFile(logFile);

        String content = new String(Files.readAllBytes(logFile.toPath()));
        Assert.assertTrue(content.contains("200"));
        Assert.assertTrue(content.contains("GET"));
    }


    @Test
    public void testRotateWithNewFileName() throws Exception {
        AccessLogValve valve = createValve("access", "%s");
        getTomcatInstance().start();

        getUrl("http://localhost:" + getPort());
        File logFile = new File(logDir, "access.log");
        awaitFile(logFile);

        File rotatedFile = new File(logDir, "access_rotated.log");
        Assert.assertTrue(valve.rotate(rotatedFile.getAbsolutePath()));
        Assert.assertTrue(rotatedFile.exists());

        getUrl("http://localhost:" + getPort());
        awaitFile(logFile);

        File[] logFiles = logDir.listFiles((dir, name) -> name.startsWith("access") && name.endsWith(".log"));
        Assert.assertNotNull(logFiles);
        Assert.assertEquals(2, logFiles.length);
    }


    @Test
    public void testRotateReturnsFalseWhenNoLogFile() {
        AccessLogValve valve = new AccessLogValve();
        // currentLogFile will be null because the valve was never started
        boolean result = valve.rotate("nonexistent.log");
        Assert.assertFalse("rotate() should return false when no log file", result);
    }


    @Test
    public void testRenameOnRotate() throws Exception {
        AccessLogValve valve = createValve("access", "%s");
        valve.setRotatable(true);
        valve.setRenameOnRotate(true);
        getTomcatInstance().start();

        getUrl("http://localhost:" + getPort());

        // With renameOnRotate, the active log has no date stamp
        File logFile = new File(logDir, "access.log");
        awaitFile(logFile);
        Assert.assertTrue(logFile.exists());

        File[] datedFiles = logDir.listFiles(
                (dir, name) -> name.startsWith("access.") && name.endsWith(".log")
                    && name.length() > "access.log".length());
        Assert.assertTrue("No dated log file should exist before rotation",
                datedFiles == null || datedFiles.length == 0);
    }


    @Test
    public void testMaxDaysCleanup() throws Exception {
        AccessLogValve valve = createValve("access", "%s");
        valve.setRotatable(true);
        valve.setMaxDays(1);

        File oldLog = new File(logDir, "access.old.log");
        Assert.assertTrue(oldLog.createNewFile());
        Assert.assertTrue(oldLog.setLastModified(System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000));

        getTomcatInstance().start();

        valve.backgroundProcess();

        Assert.assertFalse(oldLog.exists());
    }


    @Test
    public void testBufferedFlush() throws Exception {
        AccessLogValve valve = createValve("access_buffered", "%s");
        valve.setBuffered(true);
        getTomcatInstance().start();

        getUrl("http://localhost:" + getPort());

        // Flush via backgroundProcess since buffered=true
        valve.backgroundProcess();

        File logFile = new File(logDir, "access_buffered.log");
        awaitFile(logFile);
        String content = new String(Files.readAllBytes(logFile.toPath()));
        Assert.assertTrue(content.contains("200"));
    }


    @Test
    public void testCheckExists() throws Exception {
        AccessLogValve valve = createValve("access_check", "%s");
        valve.setCheckExists(true);
        getTomcatInstance().start();

        getUrl("http://localhost:" + getPort());
        File logFile = new File(logDir, "access_check.log");
        awaitFile(logFile);

        Assert.assertTrue(logFile.delete());
        Assert.assertFalse(logFile.exists());

        getUrl("http://localhost:" + getPort());
        awaitFile(logFile);
        Assert.assertTrue(logFile.exists());
    }


    @Test
    public void testCustomEncoding() throws Exception {
        AccessLogValve valve = createValve("access_iso", "%s");
        valve.setEncoding("ISO-8859-1");
        getTomcatInstance().start();

        getUrl("http://localhost:" + getPort());

        File logFile = new File(logDir, "access_iso.log");
        awaitFile(logFile);

        String content = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.ISO_8859_1);
        Assert.assertTrue(content.contains("200"));
    }

    @Test
    public void testGetSetProperties() {
        AccessLogValve valve = new AccessLogValve();

        Assert.assertEquals("logs", valve.getDirectory());
        valve.setDirectory("custom-dir");
        Assert.assertEquals("custom-dir", valve.getDirectory());

        Assert.assertEquals("access_log", valve.getPrefix());
        valve.setPrefix("myapp");
        Assert.assertEquals("myapp", valve.getPrefix());

        Assert.assertEquals("", valve.getSuffix());
        valve.setSuffix(".txt");
        Assert.assertEquals(".txt", valve.getSuffix());

        Assert.assertTrue(valve.isRotatable());
        valve.setRotatable(false);
        Assert.assertFalse(valve.isRotatable());

        Assert.assertFalse(valve.isRenameOnRotate());
        valve.setRenameOnRotate(true);
        Assert.assertTrue(valve.isRenameOnRotate());

        Assert.assertTrue(valve.isBuffered());
        valve.setBuffered(false);
        Assert.assertFalse(valve.isBuffered());

        Assert.assertFalse(valve.isCheckExists());
        valve.setCheckExists(true);
        Assert.assertTrue(valve.isCheckExists());

        Assert.assertEquals(".yyyy-MM-dd", valve.getFileDateFormat());
        valve.setFileDateFormat(".yyyyMMdd");
        Assert.assertEquals(".yyyyMMdd", valve.getFileDateFormat());

        Assert.assertEquals(-1, valve.getMaxDays());
        valve.setMaxDays(30);
        Assert.assertEquals(30, valve.getMaxDays());

        Assert.assertNull(valve.getEncoding());
        valve.setEncoding("UTF-16");
        Assert.assertEquals("UTF-16", valve.getEncoding());
    }


    /**
     * Creates an {@link AccessLogValve} with common defaults (non-rotatable,
     * unbuffered) attached to a Tomcat instance ready to start.
     */
    private AccessLogValve createValve(String prefix, String pattern)
            throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();
        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        logDir = getLogDir();
        AccessLogValve valve = new AccessLogValve();
        valve.setDirectory(logDir.getAbsolutePath());
        valve.setPrefix(prefix);
        valve.setSuffix(".log");
        valve.setRotatable(false);
        valve.setBuffered(false);
        valve.setPattern(pattern);
        tomcat.getHost().getPipeline().addValve(valve);
        return valve;
    }


    private File getLogDir() throws IOException {
        File dir = new File(getTemporaryDirectory(), "access-log-test");
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Failed to create log directory: " + dir);
        }
        addDeleteOnTearDown(dir);
        return dir;
    }

    @SuppressWarnings("BusyWait")
    private static void awaitFile(File file) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (!file.exists() || file.length() == 0) {
            if (System.currentTimeMillis() > deadline) {
                Assert.fail("Timed out waiting for " + file.getName());
            }
            Thread.sleep(2);
        }
    }
}
