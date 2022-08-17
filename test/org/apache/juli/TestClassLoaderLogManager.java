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
package org.apache.juli;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for {@link ClassLoaderLogManager}.
 */
public class TestClassLoaderLogManager {

    private static final byte[] EMPTY_BYTES = {};

    @Test
    public void testReplace() {
        ClassLoaderLogManager logManager = new ClassLoaderLogManager();
        Assert.assertEquals("", logManager.replace(""));
        Assert.assertEquals("${", logManager.replace("${"));
        Assert.assertEquals("${undefinedproperty}", logManager.replace("${undefinedproperty}"));
        Assert.assertEquals(
                System.lineSeparator() + File.pathSeparator + File.separator,
                logManager.replace("${line.separator}${path.separator}${file.separator}"));
        Assert.assertEquals(
                "foo" + File.separator + "bar" + System.lineSeparator() + File.pathSeparator + "baz",
                logManager.replace("foo${file.separator}bar${line.separator}${path.separator}baz"));
        // BZ 51249
        Assert.assertEquals(
                "%{file.separator}" + File.separator,
                logManager.replace("%{file.separator}${file.separator}"));
        Assert.assertEquals(
                File.separator + "${undefinedproperty}" + File.separator,
                logManager.replace("${file.separator}${undefinedproperty}${file.separator}"));
        Assert.assertEquals("${}" + File.pathSeparator, logManager.replace("${}${path.separator}"));
    }

    @Test
    public void testBug56082() {
        ClassLoaderLogManager logManager = new ClassLoaderLogManager();

        LoggerCreateThread[] createThreads = new LoggerCreateThread[10];
        for (int i = 0; i < createThreads.length; i ++) {
            createThreads[i] = new LoggerCreateThread(logManager);
            createThreads[i].setName("LoggerCreate-" + i);
            createThreads[i].start();
        }

        LoggerListThread listThread = new LoggerListThread(logManager);
        listThread.setName("LoggerList");
        listThread.start();

        try {
            listThread.join(2000);
        } catch (InterruptedException e) {
            // Ignore
        }

        for (LoggerCreateThread createThread : createThreads) {
            createThread.setRunning(false);
        }

        Assert.assertTrue(listThread.isRunning());
        listThread.setRunning(false);
    }

    /*
     * Tests if a per-app root logger has a not {@code null} level.
     */
    @Test
    public void testBug66184() throws IOException {
        final ClassLoader cl = new TestClassLoader();
        final ClassLoader oldCL = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(cl);
            final ClassLoaderLogManager logManager = new ClassLoaderLogManager();
            logManager.readConfiguration();
            final Logger rootLogger = logManager.getLogger("");
            Assert.assertNotNull("root logger is null", rootLogger);
            Assert.assertNull("root logger has a parent", rootLogger.getParent());
            Assert.assertEquals(Level.INFO, rootLogger.getLevel());
        } finally {
            Thread.currentThread().setContextClassLoader(oldCL);
        }
    }

    private static class LoggerCreateThread extends Thread {

        private final LogManager logManager;
        private volatile boolean running = true;

        public LoggerCreateThread(LogManager logManager) {
            this.logManager = logManager;
        }

        @Override
        public void run() {
            Random r = new Random();
            while (running) {
                Logger logger = Logger.getLogger("Bug56082-" + r.nextInt(100000));
                logManager.addLogger(logger);
            }
        }

        public void setRunning(boolean running) {
            this.running = running;
        }
    }

    private static class LoggerListThread extends Thread {

        private final LogManager logManager;
        private volatile boolean running = true;

        public LoggerListThread(LogManager logManager) {
            this.logManager = logManager;
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Collections.list(logManager.getLoggerNames());
                } catch (Exception e) {
                    e.printStackTrace();
                    running = false;
                }
            }
        }

        public boolean isRunning() {
            return running;
        }

        public void setRunning(boolean running) {
            this.running = running;
        }
    }

    private static class TestClassLoader extends ClassLoader implements WebappProperties {

        @Override
        public String getWebappName() {
            return "webapp";
        }

        @Override
        public String getHostName() {
            return "localhost";
        }

        @Override
        public String getServiceName() {
            return "Catalina";
        }

        @Override
        public boolean hasLoggingConfig() {
            return true;
        }

        @Override
        public InputStream getResourceAsStream(final String resource) {
            if ("logging.properties".equals(resource)) {
                return new ByteArrayInputStream(EMPTY_BYTES);
            }
            return null;
        }
    }
}
