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

package org.apache.juli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestPerWebappJuliIntegration extends TomcatBaseTest {
    @Before
    public void assumeJuliIsUsed() {
        Assume.assumeTrue(LogManager.getLogManager().getClass().getName().equals(ClassLoaderLogManager.class.getName()));
    }
    private static final String APP_ID_A = "A";
    private static final String APP_ID_B = "B";
    private static final String HANDLER_ISOLATION_LOGGER = "handlerIsolationLogger";
    @Test
    public void testPerWebappRootLogLevelIsolation() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        constructAppForLogLevelIsolationTest(tomcat, APP_ID_A, Level.FINE);
        constructAppForLogLevelIsolationTest(tomcat, APP_ID_B, Level.INFO);
        tomcat.start();
        ByteChunk res = getUrl("http://localhost:" + getPort() + "/juli" + APP_ID_A + "/log_level");
        ByteChunk res2 = getUrl("http://localhost:" + getPort() + "/juli" + APP_ID_B + "/log_level");
        Assert.assertEquals(Level.FINE.toString(), res.toString().trim());
        Assert.assertEquals(Level.INFO.toString(), res2.toString().trim());
        tomcat.stop();
    }
    @Test
    public void testPerWebappHandlersIsolation() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        ConstructAppResult resultA = constructAppForHandlerIsolationTest(tomcat, APP_ID_A, Level.INFO);
        ConstructAppResult resultB = constructAppForHandlerIsolationTest(tomcat, APP_ID_B, Level.WARNING);
        try(LogCapture logCaptureA = TomcatBaseTest.attachWebappLogCapture(resultA.context, null, HANDLER_ISOLATION_LOGGER);
                LogCapture logCaptureB = TomcatBaseTest.attachWebappLogCapture(resultB.context, null, HANDLER_ISOLATION_LOGGER)) {
            tomcat.start();
            Assert.assertEquals(200, getUrl("http://localhost:" + getPort() + "/juli" + APP_ID_A + "/test", new ByteChunk(), null));
            Assert.assertEquals(200, getUrl("http://localhost:" + getPort() + "/juli" + APP_ID_B + "/test", new ByteChunk(), null));
            Assert.assertTrue(logCaptureA.containsText("JULI-" + APP_ID_A + "-INFO"));
            Assert.assertTrue(logCaptureB.containsText("JULI-" + APP_ID_B + "-INFO"));

            File logFileA = findLogFile(resultA.logsDir, "juli" + APP_ID_A + ".");
            File logFileB = findLogFile(resultB.logsDir, "juli" + APP_ID_B + ".");

            Assert.assertNotNull(logFileA);
            Assert.assertTrue("App " + APP_ID_A + " log file should contain the INFO message", Files.readString(logFileA.toPath()).contains("JULI-" + APP_ID_A + "-INFO"));
            Assert.assertNull("App " + APP_ID_B + " log file should not exist", logFileB);

            tomcat.stop();
        }
    }

    private void constructAppForLogLevelIsolationTest(Tomcat tomcat, String appId, Level logLevel) throws FileNotFoundException {
        File appDir = new File(getTemporaryDirectory(), "juli" + appId);
        addDeleteOnTearDown(appDir);
        Assert.assertTrue(appDir.mkdirs() && appDir.isDirectory());
        File webInfClassesDir = new File(appDir, "WEB-INF/classes");
        Assert.assertTrue(webInfClassesDir.mkdirs() && webInfClassesDir.isDirectory());

        File loggingPropertiesFile = new File(webInfClassesDir, "logging.properties");
        try (PrintWriter writer = new PrintWriter(loggingPropertiesFile)) {
            writer.write(".level = " + logLevel);
        }

        Context context = tomcat.addContext("/juli" + appId, appDir.getAbsolutePath());
        Tomcat.addServlet(context, "log_level", new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.getWriter().print(Logger.getLogger("").getLevel());
            }
        });
        context.addServletMappingDecoded("/log_level", "log_level");
    }
    private ConstructAppResult constructAppForHandlerIsolationTest(Tomcat tomcat, String appId, Level logLevel) throws FileNotFoundException {
        File appDir = new File(getTemporaryDirectory(), "juliHandler" + appId);
        addDeleteOnTearDown(appDir);
        Assert.assertTrue(appDir.mkdirs() && appDir.isDirectory());
        File webInfClassesDir = new File(appDir, "WEB-INF/classes");
        Assert.assertTrue(webInfClassesDir.mkdirs() && webInfClassesDir.isDirectory());
        File logsDir = new File(appDir, "logs");
        Assert.assertTrue(logsDir.mkdirs() && logsDir.isDirectory());

        File loggingProperties = new File(webInfClassesDir, "logging.properties");
        try (PrintWriter writer = new PrintWriter(loggingProperties)) {
            writer.write("handlers = org.apache.juli.FileHandler\r\n" +
                    "org.apache.juli.FileHandler.level = " + logLevel + "\r\n" +
                    "org.apache.juli.FileHandler.directory = " + logsDir.getAbsolutePath().replace("\\", "\\\\") + "\r\n" +
                    "org.apache.juli.FileHandler.prefix = juli" + appId + ".\r\n"
            );
        }

        Context context = tomcat.addContext("/juli" + appId, appDir.getAbsolutePath());
        Tomcat.addServlet(context, "test", new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                Logger root = Logger.getLogger(HANDLER_ISOLATION_LOGGER);
                root.info("JULI-" + appId + "-INFO");
            }
        });
        context.addServletMappingDecoded("/test", "test");
        return new ConstructAppResult(logsDir, context);
    }
    private static class ConstructAppResult {
        private final File logsDir;
        private final Context context;
        ConstructAppResult(File logsDir, Context context) {
            this.logsDir = logsDir;
            this.context = context;
        }
    }
    private static File findLogFile(File dir, String prefix) throws Exception {
        List<File> files;
        int deadlineCounter = 0;
        do {
            try (Stream<Path> paths = Files.walk(dir.toPath())) {
                files = paths.map(Path::toFile).filter(f -> f.isFile() && f.getName().startsWith(prefix)).collect(Collectors.toList());
            }
            if (deadlineCounter > 0) {
                Thread.sleep(100);
            }
        } while (++deadlineCounter < 3);
        return files.isEmpty() ? null : files.get(0);
    }
}
