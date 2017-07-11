package org.apache.juli;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.catalina.startup.LoggingBaseTest;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestFileHandlerNonRotatable extends LoggingBaseTest {
    private FileHandler testHandler;

    @BeforeClass
    public static void setUpPerTestClass() throws Exception {
        System.setProperty("java.util.logging.manager",
                "org.apache.juli.ClassLoaderLogManager");
        System.setProperty("java.util.logging.config.file",
                TestFileHandlerNonRotatable.class
                        .getResource("logging-non-rotatable.properties")
                        .getFile());
    }

    @After
    public void tearDown() throws Exception {
        if (testHandler != null) {
            testHandler.close();
        }
        super.tearDown();
    }

    @Test
    public void testBug61232() throws Exception {
        testHandler = new FileHandler(this.getTemporaryDirectory().toString(),
                "juli.", ".log");

        File logFile = new File(this.getTemporaryDirectory(), "juli.log");
        assertTrue(logFile.exists());
    }

    @Test
    public void testCustomSuffixWithoutSeparator() throws Exception {
        testHandler = new FileHandler(this.getTemporaryDirectory().toString(),
                "juli.", "log");

        File logFile = new File(this.getTemporaryDirectory(), "juli.log");
        assertTrue(logFile.exists());
    }

    @Test
    public void testCustomPrefixWithoutSeparator() throws Exception {
        testHandler = new FileHandler(this.getTemporaryDirectory().toString(),
                "juli", ".log");

        File logFile = new File(this.getTemporaryDirectory(), "juli.log");
        assertTrue(logFile.exists());
    }
}
