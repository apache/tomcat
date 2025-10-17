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
package org.apache.catalina.startup;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogManager;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;

import org.apache.juli.ClassLoaderLogManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Base class that provides logging support for test cases that respects the
 * standard conf/logging.properties configuration file.
 *
 * <p>
 * It also provides support for cleaning up temporary files after shutdown. See
 * {@link #addDeleteOnTearDown(File)}.
 *
 * <p>
 * <em>Note</em> that the logging configuration uses
 * <code>${catalina.base}</code> value and thus we take care about that property
 * even if the tests do not use Tomcat.
 */
public abstract class LoggingBaseTest {

    private static List<File> deleteOnClassTearDown = new ArrayList<>();

    protected Log log;

    private static File tempDir;

    private List<File> deleteOnTearDown = new ArrayList<>();

    protected boolean ignoreTearDown = false;

    /**
     * Provides name of the currently executing test method.
     */
    @Rule
    public final TestName testName = new TestName();

    /*
     * Helper method that returns the directory where Tomcat build resides. It
     * is used to access resources that are part of default Tomcat deployment.
     * E.g. the examples webapp.
     */
    public static File getBuildDirectory() {
        return new File(System.getProperty("tomcat.test.tomcatbuild",
                "output/build"));
    }

    /*
     * Helper method that returns the path of the temporary directory used by
     * the test runs. The directory is configured during {@link #setUp()}.
     *
     * <p>
     * It is used as <code>${catalina.base}</code> for the instance of Tomcat
     * that is being started, but can be used to store other temporary files as
     * well. Its <code>work</code> and <code>webapps</code> subdirectories are
     * deleted at {@link #tearDown()}. If you have other files or directories
     * that have to be deleted on cleanup, register them with
     * {@link #addDeleteOnTearDown(File)}.
     */
    public File getTemporaryDirectory() {
        return tempDir;
    }

    /**
     * Schedule the given file or directory to be deleted during after-test
     * cleanup.
     *
     * @param file
     *            File or directory
     */
    public void addDeleteOnTearDown(File file) {
        deleteOnTearDown.add(file);
    }

    @BeforeClass
    public static void setUpPerTestClass() throws Exception {
        // Create catalina.base directory
        File tempBase = new File(System.getProperty("tomcat.test.temp", "output/tmp"));
        if (!tempBase.mkdirs() && !tempBase.isDirectory()) {
            Assert.fail("Unable to create base temporary directory for tests");
        }
        Path tempBasePath = FileSystems.getDefault().getPath(tempBase.getAbsolutePath());
        tempDir = Files.createTempDirectory(tempBasePath, "test").toFile();

        System.setProperty(Constants.CATALINA_BASE_PROP, tempDir.getAbsolutePath());
        System.setProperty("derby.system.home", tempDir.getAbsolutePath());

        // Configure logging
        System.setProperty("java.util.logging.manager",
                "org.apache.juli.ClassLoaderLogManager");
        System.setProperty("java.util.logging.config.file",
                new File(System.getProperty("tomcat.test.basedir"),
                        "conf/logging.properties").toString());

        // tempDir contains log files which will be open until JULI shuts down
        deleteOnClassTearDown.add(tempDir);
    }

    @Before
    public void setUp() throws Exception {
        log = LogFactory.getLog(getClass());
        log.info("Starting test case [" + testName.getMethodName() + "]");
    }

    @After
    public void tearDown() throws Exception {
        boolean deleted = true;
        for (File file : deleteOnTearDown) {
            boolean result = ExpandWar.delete(file);
            if (!result) {
                log.info("Failed to delete [" + file.getAbsolutePath() + "]");
            }
            deleted = deleted & result;
        }
        deleteOnTearDown.clear();

        Assert.assertTrue("Failed to delete at least one file", ignoreTearDown || deleted);
    }

    @AfterClass
    public static void tearDownPerTestClass() throws Exception {
        LogManager logManager = LogManager.getLogManager();
        if (logManager instanceof ClassLoaderLogManager) {
            ((ClassLoaderLogManager) logManager).shutdown();
        } else {
            logManager.reset();
        }
        for (File file : deleteOnClassTearDown) {
            ExpandWar.delete(file);
        }
        deleteOnClassTearDown.clear();
    }
}
