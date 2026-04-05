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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Tests for {@link AccessLogValve} file I/O, rotation, encoding, and cleanup
 * operations. Pattern-based access log format tests are covered by
 * {@link TestAccessLogValve}.
 */
public class TestAccessLogValveFile extends TomcatBaseTest {


    @Test
    public void testLogWritesToFile() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "ok", new OkServlet());
        ctx.addServletMappingDecoded("/", "ok");

        File logDir = getLogDir();
        AccessLogValve valve = new AccessLogValve();
        valve.setDirectory(logDir.getAbsolutePath());
        valve.setPrefix("access");
        valve.setSuffix(".log");
        valve.setRotatable(false);
        valve.setBuffered(false);
        valve.setPattern("combined");
        tomcat.getHost().getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        res.setCharset(StandardCharsets.UTF_8);
        int rc = getUrl("http://localhost:" + getPort(), res, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        // Wait for log to be written
        Thread.sleep(500);

        File logFile = new File(logDir, "access.log");
        Assert.assertTrue("Log file should exist", logFile.exists());

        String content = new String(Files.readAllBytes(logFile.toPath()),
                StandardCharsets.UTF_8);
        Assert.assertTrue("Log file should contain HTTP status",
                content.contains("200"));
        Assert.assertTrue("Log file should contain request method",
                content.contains("GET"));
    }


    @Test
    public void testRotateWithNewFileName() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "ok", new OkServlet());
        ctx.addServletMappingDecoded("/", "ok");

        File logDir = getLogDir();
        AccessLogValve valve = new AccessLogValve();
        valve.setDirectory(logDir.getAbsolutePath());
        valve.setPrefix("access");
        valve.setSuffix(".log");
        valve.setRotatable(false);
        valve.setBuffered(false);
        valve.setPattern("%s");
        tomcat.getHost().getPipeline().addValve(valve);

        tomcat.start();

        // Generate a log entry
        ByteChunk res = new ByteChunk();
        getUrl("http://localhost:" + getPort(), res, null);
        Thread.sleep(500);

        // Rotate to a new file
        File rotatedFile = new File(logDir, "access_rotated.log");
        boolean result = valve.rotate(rotatedFile.getAbsolutePath());
        Assert.assertTrue("rotate() should return true", result);
        Assert.assertTrue("Rotated file should exist", rotatedFile.exists());

        // Generate another log entry — should go to a new file
        getUrl("http://localhost:" + getPort(), res, null);
        Thread.sleep(500);

        // The new log file should exist
        File[] logFiles = logDir.listFiles(
                (dir, name) -> name.startsWith("access") && name.endsWith(".log"));
        Assert.assertNotNull(logFiles);
        Assert.assertTrue("Should have at least 2 log files",
                logFiles.length >= 2);
    }


    @Test
    public void testRotateReturnsFalseWhenNoLogFile() throws Exception {
        AccessLogValve valve = new AccessLogValve();
        // Don't start the valve — currentLogFile will be null
        boolean result = valve.rotate("nonexistent.log");
        Assert.assertFalse("rotate() should return false when no log file",
                result);
    }


    @Test
    public void testRenameOnRotate() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "ok", new OkServlet());
        ctx.addServletMappingDecoded("/", "ok");

        File logDir = getLogDir();
        AccessLogValve valve = new AccessLogValve();
        valve.setDirectory(logDir.getAbsolutePath());
        valve.setPrefix("access");
        valve.setSuffix(".log");
        valve.setRotatable(true);
        valve.setRenameOnRotate(true);
        valve.setBuffered(false);
        valve.setPattern("%s");
        tomcat.getHost().getPipeline().addValve(valve);

        tomcat.start();

        // Generate a log entry
        ByteChunk res = new ByteChunk();
        getUrl("http://localhost:" + getPort(), res, null);
        Thread.sleep(500);

        // Verify the log file was created without a date stamp
        // (renameOnRotate defers the date stamp)
        File logFile = new File(logDir, "access.log");
        Assert.assertTrue("Log file without date stamp should exist",
                logFile.exists());
    }


    @Test
    public void testMaxDaysCleanup() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "ok", new OkServlet());
        ctx.addServletMappingDecoded("/", "ok");

        File logDir = getLogDir();

        // Create an "old" log file with an old last modified timestamp
        File oldLog = new File(logDir, "access.2020-01-01.log");
        Assert.assertTrue("Creating old log file", oldLog.createNewFile());
        Assert.assertTrue("Setting last modified",
                oldLog.setLastModified(
                        System.currentTimeMillis() - (10L * 24 * 60 * 60 * 1000)));

        AccessLogValve valve = new AccessLogValve();
        valve.setDirectory(logDir.getAbsolutePath());
        valve.setPrefix("access");
        valve.setSuffix(".log");
        valve.setRotatable(true);
        valve.setMaxDays(1);
        valve.setBuffered(false);
        valve.setPattern("%s");
        tomcat.getHost().getPipeline().addValve(valve);

        tomcat.start();

        // Generate a log entry to trigger open() which sets checkForOldLogs
        ByteChunk res = new ByteChunk();
        getUrl("http://localhost:" + getPort(), res, null);
        Thread.sleep(500);

        // backgroundProcess should delete old logs
        valve.backgroundProcess();

        Assert.assertFalse("Old log file should be deleted", oldLog.exists());
    }


    @Test
    public void testBufferedFlush() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "ok", new OkServlet());
        ctx.addServletMappingDecoded("/", "ok");

        File logDir = getLogDir();
        AccessLogValve valve = new AccessLogValve();
        valve.setDirectory(logDir.getAbsolutePath());
        valve.setPrefix("access_buffered");
        valve.setSuffix(".log");
        valve.setRotatable(false);
        valve.setBuffered(true);
        valve.setPattern("%s");
        tomcat.getHost().getPipeline().addValve(valve);

        tomcat.start();

        // Generate a log entry
        ByteChunk res = new ByteChunk();
        getUrl("http://localhost:" + getPort(), res, null);
        Thread.sleep(200);

        // The log might not be flushed yet since buffered=true
        // Call backgroundProcess to flush
        valve.backgroundProcess();

        File logFile = new File(logDir, "access_buffered.log");
        Assert.assertTrue("Log file should exist after flush",
                logFile.exists());
        String content = new String(Files.readAllBytes(logFile.toPath()),
                StandardCharsets.UTF_8);
        Assert.assertTrue("Log should contain status code after flush",
                content.contains("200"));
    }


    @Test
    public void testUnbufferedImmediateWrite() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "ok", new OkServlet());
        ctx.addServletMappingDecoded("/", "ok");

        File logDir = getLogDir();
        AccessLogValve valve = new AccessLogValve();
        valve.setDirectory(logDir.getAbsolutePath());
        valve.setPrefix("access_unbuf");
        valve.setSuffix(".log");
        valve.setRotatable(false);
        valve.setBuffered(false);
        valve.setPattern("%s");
        tomcat.getHost().getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        getUrl("http://localhost:" + getPort(), res, null);
        Thread.sleep(500);

        // With unbuffered, content should be available immediately
        File logFile = new File(logDir, "access_unbuf.log");
        Assert.assertTrue("Log file should exist", logFile.exists());
        String content = new String(Files.readAllBytes(logFile.toPath()),
                StandardCharsets.UTF_8);
        Assert.assertTrue("Unbuffered log should contain status",
                content.contains("200"));
    }


    @Test
    public void testCheckExists() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "ok", new OkServlet());
        ctx.addServletMappingDecoded("/", "ok");

        File logDir = getLogDir();
        AccessLogValve valve = new AccessLogValve();
        valve.setDirectory(logDir.getAbsolutePath());
        valve.setPrefix("access_check");
        valve.setSuffix(".log");
        valve.setRotatable(false);
        valve.setBuffered(false);
        valve.setCheckExists(true);
        valve.setPattern("%s");
        tomcat.getHost().getPipeline().addValve(valve);

        tomcat.start();

        // First request creates the log file
        ByteChunk res = new ByteChunk();
        getUrl("http://localhost:" + getPort(), res, null);
        Thread.sleep(500);

        File logFile = new File(logDir, "access_check.log");
        Assert.assertTrue("Log file should exist after first request",
                logFile.exists());

        // Delete the file externally
        Assert.assertTrue("Should be able to delete log file",
                logFile.delete());
        Assert.assertFalse("Log file should be deleted", logFile.exists());

        // Second request should trigger re-creation when checkExists=true
        getUrl("http://localhost:" + getPort(), res, null);
        Thread.sleep(500);

        Assert.assertTrue("Log file should be recreated", logFile.exists());
    }


    @Test
    public void testCustomEncoding() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "ok", new OkServlet());
        ctx.addServletMappingDecoded("/", "ok");

        File logDir = getLogDir();
        AccessLogValve valve = new AccessLogValve();
        valve.setDirectory(logDir.getAbsolutePath());
        valve.setPrefix("access_iso");
        valve.setSuffix(".log");
        valve.setRotatable(false);
        valve.setBuffered(false);
        valve.setEncoding("ISO-8859-1");
        valve.setPattern("%s");
        tomcat.getHost().getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        getUrl("http://localhost:" + getPort(), res, null);
        Thread.sleep(500);

        File logFile = new File(logDir, "access_iso.log");
        Assert.assertTrue("Log file with custom encoding should exist",
                logFile.exists());
        Assert.assertTrue("Log file should have content",
                logFile.length() > 0);
    }


    @Test
    public void testSetEncodingNull() throws Exception {
        AccessLogValve valve = new AccessLogValve();

        valve.setEncoding("ISO-8859-1");
        Assert.assertEquals("ISO-8859-1", valve.getEncoding());

        // Empty string resets to null
        valve.setEncoding("");
        Assert.assertNull(valve.getEncoding());

        // Null also sets to null
        valve.setEncoding("UTF-16");
        valve.setEncoding(null);
        Assert.assertNull(valve.getEncoding());
    }


    @Test
    public void testGetSetProperties() throws Exception {
        AccessLogValve valve = new AccessLogValve();

        // directory
        Assert.assertEquals("logs", valve.getDirectory());
        valve.setDirectory("/tmp/test-logs");
        Assert.assertEquals("/tmp/test-logs", valve.getDirectory());

        // prefix
        Assert.assertEquals("access_log", valve.getPrefix());
        valve.setPrefix("myapp");
        Assert.assertEquals("myapp", valve.getPrefix());

        // suffix
        Assert.assertEquals("", valve.getSuffix());
        valve.setSuffix(".txt");
        Assert.assertEquals(".txt", valve.getSuffix());

        // rotatable
        Assert.assertTrue(valve.isRotatable());
        valve.setRotatable(false);
        Assert.assertFalse(valve.isRotatable());

        // renameOnRotate
        Assert.assertFalse(valve.isRenameOnRotate());
        valve.setRenameOnRotate(true);
        Assert.assertTrue(valve.isRenameOnRotate());

        // buffered
        Assert.assertTrue(valve.isBuffered());
        valve.setBuffered(false);
        Assert.assertFalse(valve.isBuffered());

        // checkExists
        Assert.assertFalse(valve.isCheckExists());
        valve.setCheckExists(true);
        Assert.assertTrue(valve.isCheckExists());

        // fileDateFormat
        Assert.assertEquals(".yyyy-MM-dd", valve.getFileDateFormat());
        valve.setFileDateFormat(".yyyyMMdd");
        Assert.assertEquals(".yyyyMMdd", valve.getFileDateFormat());

        // maxDays
        Assert.assertEquals(-1, valve.getMaxDays());
        valve.setMaxDays(30);
        Assert.assertEquals(30, valve.getMaxDays());

        // encoding
        Assert.assertNull(valve.getEncoding());
        valve.setEncoding("UTF-16");
        Assert.assertEquals("UTF-16", valve.getEncoding());
    }


    private File getLogDir() throws IOException {
        File logDir = new File(getTemporaryDirectory(), "access-log-test");
        if (!logDir.mkdirs() && !logDir.isDirectory()) {
            throw new IOException("Failed to create log directory: " + logDir);
        }
        return logDir;
    }


    private static final class OkServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }
    }
}
