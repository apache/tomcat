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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Random;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
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
        final Thread currentThread = Thread.currentThread();
        final ClassLoader oldCL = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(cl);
            final ClassLoaderLogManager logManager = new ClassLoaderLogManager();
            logManager.readConfiguration();
            final Logger rootLogger = logManager.getLogger("");
            Assert.assertNotNull("root logger is null", rootLogger);
            Assert.assertNull("root logger has a parent", rootLogger.getParent());
            Assert.assertEquals(Level.INFO, rootLogger.getLevel());
        } finally {
            currentThread.setContextClassLoader(oldCL);
        }
    }

    /*
     * reconfigure() must replace the configuration and apply it to the loggers that already exist (which a container
     * class holds a static reference to), unlike readConfiguration() which only configures loggers created afterwards.
     */
    @Test
    public void testReconfigureReappliesToExistingLoggers() throws IOException {
        final ClassLoader cl = new TestClassLoader();
        final Thread currentThread = Thread.currentThread();
        final ClassLoader oldCL = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(cl);
            final ClassLoaderLogManager logManager = new ClassLoaderLogManager();

            final String config1 = "handlers = " + StubHandler.class.getName() + System.lineSeparator()
                    + "foo.level = INFO" + System.lineSeparator() + "bar.level = FINE" + System.lineSeparator();
            logManager.reconfigure(config(config1), cl);

            final Logger foo = new TesterLogger("foo");
            Assert.assertTrue(logManager.addLogger(foo));
            final Logger bar = new TesterLogger("bar");
            Assert.assertTrue(logManager.addLogger(bar));
            Assert.assertEquals(Level.INFO, foo.getLevel());
            Assert.assertEquals(Level.FINE, bar.getLevel());

            final Logger root = logManager.getLogger("");
            Assert.assertNotNull("root logger is null", root);
            final Handler[] before = root.getHandlers();
            Assert.assertEquals(1, before.length);
            final StubHandler oldHandler = (StubHandler) before[0];

            // New configuration: foo changes level, bar's level property is removed entirely.
            final String config2 = "handlers = " + StubHandler.class.getName() + System.lineSeparator()
                    + "foo.level = FINE" + System.lineSeparator();
            logManager.reconfigure(config(config2), cl);

            // The level of an existing logger is updated ...
            Assert.assertEquals(Level.FINE, foo.getLevel());
            // ... and a property that has been removed no longer applies (the logger inherits again).
            Assert.assertNull(bar.getLevel());

            // The handler is recreated and the previous one closed.
            final Handler[] after = root.getHandlers();
            Assert.assertEquals(1, after.length);
            Assert.assertNotSame(oldHandler, after[0]);
            Assert.assertTrue("the previous handler was not closed", oldHandler.isClosed());
            Assert.assertFalse(((StubHandler) after[0]).isClosed());
        } finally {
            currentThread.setContextClassLoader(oldCL);
        }
    }

    /*
     * refreshLoggers() re-applies the resolved configuration to the existing loggers, discarding levels and handlers
     * that were set programmatically.
     */
    @Test
    public void testRefreshLoggersReappliesConfiguration() throws IOException {
        final ClassLoader cl = new TestClassLoader();
        final Thread currentThread = Thread.currentThread();
        final ClassLoader oldCL = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(cl);
            final ClassLoaderLogManager logManager = new ClassLoaderLogManager();

            final String config = "handlers = " + StubHandler.class.getName() + System.lineSeparator()
                    + "foo.level = FINE" + System.lineSeparator();
            logManager.reconfigure(config(config), cl);

            final Logger foo = new TesterLogger("foo");
            Assert.assertTrue(logManager.addLogger(foo));
            Assert.assertEquals(Level.FINE, foo.getLevel());

            // A level and a handler set programmatically, off-configuration.
            foo.setLevel(Level.OFF);
            final StubHandler extra = new StubHandler();
            foo.addHandler(extra);

            logManager.refreshLoggers();

            // Back to the configured state: the configured level, and only the configuration knows the handlers.
            Assert.assertEquals(Level.FINE, foo.getLevel());
            Assert.assertEquals(0, foo.getHandlers().length);
            Assert.assertFalse("a programmatic handler must not be closed by a refresh", extra.isClosed());
        } finally {
            currentThread.setContextClassLoader(oldCL);
        }
    }

    private static InputStream config(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    // A logger that can be instantiated directly so it can be registered on a ClassLoaderLogManager
    // instance that is not the JVM's global log manager.
    private static class TesterLogger extends Logger {
        TesterLogger(String name) {
            super(name, null);
        }
    }

    // A no-op handler that records whether close() was called, so handler replacement can be observed.
    public static class StubHandler extends Handler {

        private volatile boolean closed = false;

        public boolean isClosed() {
            return closed;
        }

        @Override
        public void publish(LogRecord record) {
            // No output
        }

        @Override
        public void flush() {
            // Nothing to flush
        }

        @Override
        public void close() throws SecurityException {
            closed = true;
        }
    }

    private static class LoggerCreateThread extends Thread {

        private final LogManager logManager;
        private volatile boolean running = true;

        LoggerCreateThread(LogManager logManager) {
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

        LoggerListThread(LogManager logManager) {
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

    private static class TestClassLoader extends URLClassLoader implements WebappProperties {

        TestClassLoader() {
            super(new URL[0]);
        }


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
        public URL findResource(String name) {
            if ("logging.properties".equals(name)) {
                try {
                    return URI.create("file:///path/does/not/exist").toURL();
                } catch (MalformedURLException e) {
                    // Should never happen
                    throw new IllegalArgumentException(e);
                }
            }
            return null;
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
