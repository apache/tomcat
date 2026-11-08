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
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class TestFileHandler {

    private static final String PREFIX_1 = "localhost.";
    private static final String PREFIX_2 = "test.";
    private static final String PREFIX_3 = "";
    private static final String PREFIX_4 = "localhost1";
    private static final String SUFFIX_1 = ".log";
    private static final String SUFFIX_2 = ".txt";

    private File logsDir;

    @Before
    public void setUp() throws Exception {
        File logsBase = new File(System.getProperty("tomcat.test.temp", "output/tmp"));
        if (!logsBase.mkdirs() && !logsBase.isDirectory()) {
            Assert.fail("Unable to create logs directory.");
        }
        Path logsBasePath = FileSystems.getDefault().getPath(logsBase.getAbsolutePath());
        logsDir = Files.createTempDirectory(logsBasePath, "test").toFile();

        generateLogFiles(logsDir, PREFIX_1, SUFFIX_2, 3);
        generateLogFiles(logsDir, PREFIX_2, SUFFIX_1, 3);
        generateLogFiles(logsDir, PREFIX_3, SUFFIX_1, 3);
        generateLogFiles(logsDir, PREFIX_4, SUFFIX_1, 3);

        String date = LocalDateTime.now().minusDays(3).toString().replace(":", "-");
        File file = new File(logsDir, PREFIX_1 + date + SUFFIX_1);
        if (!file.createNewFile()) {
            Assert.fail("Unable to create " + file.getAbsolutePath());
        }
    }

    @After
    public void tearDown() {
        File[] files = logsDir.listFiles();
        if (files != null) {
            for (File file : files) {
                Assert.assertTrue("Failed to delete [" + file + "]", file.delete());
            }
            Assert.assertTrue("Failed to create [" + logsDir + "]", logsDir.delete());
        }
    }

    @Test
    public void testCleanOnInitOneHandler() throws Exception {
        generateLogFiles(logsDir, PREFIX_1, SUFFIX_1, 3);

        FileHandler fh1 = new FileHandler(logsDir.getAbsolutePath(), PREFIX_1, SUFFIX_1, Integer.valueOf(2));
        fh1.open();

        Thread.sleep(1000);

        Assert.assertTrue(logsDir.list().length == 16);

        fh1.close();
    }

    @Test
    public void testCleanOnInitMultipleHandlers() throws Exception {
        generateLogFiles(logsDir, PREFIX_1, SUFFIX_1, 3);

        FileHandler fh1 = new FileHandler(logsDir.getAbsolutePath(), PREFIX_1, SUFFIX_1, Integer.valueOf(2));
        FileHandler fh2 = new FileHandler(logsDir.getAbsolutePath(), PREFIX_1, SUFFIX_2, Integer.valueOf(2));
        FileHandler fh3 = new FileHandler(logsDir.getAbsolutePath(), PREFIX_2, SUFFIX_1, Integer.valueOf(2));
        FileHandler fh4 = new FileHandler(logsDir.getAbsolutePath(), PREFIX_3, SUFFIX_1, Integer.valueOf(2));
        fh1.open();
        fh2.open();
        fh3.open();
        fh4.open();

        Thread.sleep(1000);

        Assert.assertTrue(logsDir.list().length == 16);

        fh1.close();
        fh2.close();
        fh3.close();
        fh4.close();
    }

    @Test
    public void testCleanDisabled() throws Exception {
        generateLogFiles(logsDir, PREFIX_1, SUFFIX_1, 3);

        FileHandler fh1 = new FileHandler(logsDir.getAbsolutePath(), PREFIX_1, SUFFIX_1, null);
        fh1.open();

        Thread.sleep(1000);

        Assert.assertTrue(logsDir.list().length == 17);

        fh1.close();
    }

    /*
     * Opening a rotatable handler before any record has been published must open the log file for the current date,
     * rather than a file whose name contains the string representation of the unset date. The same applies when a
     * closed handler is opened again.
     */
    @Test
    public void testOpenUsesCurrentDate() throws Exception {
        String prefix = "opendate.";
        String suffix = ".log";

        FileHandler fh = new FileHandler(logsDir.getAbsolutePath(), prefix, suffix);
        fh.open();

        String today = LocalDate.now().toString();
        File expected = new File(logsDir, prefix + today + suffix);
        Assert.assertTrue("Expected file [" + expected + "] was not created", expected.isFile());

        // Publishing after the open must use the file that was opened, not rotate to a new one
        fh.publish(new LogRecord(Level.INFO, "hello"));
        Assert.assertTrue("The published record was not written to [" + expected + "]",
                Files.readString(expected.toPath()).contains("hello"));
        Assert.assertEquals(1, countFiles(prefix));

        // Reopening a closed handler must not create a file with an unset date either
        fh.close();
        fh.open();
        Assert.assertEquals(1, countFiles(prefix));

        fh.close();
    }

    private int countFiles(String prefix) {
        System.gc();
        int count = 0;
        for (File file : logsDir.listFiles()) {
            if (file.getName().startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    /*
     * A write failure must be reported, and the failed writer must be discarded so
     * that the next write retries opening the file rather than silently writing into
     * the void for the lifetime of the handler.
     */
    @Test
    public void testWriterDiscardedOnWriteFailure() throws Exception {
        Assume.assumeTrue("Requires /dev/full", new File("/dev/full").canWrite());

        // Buffer size below zero means: write through, no buffering
        FileHandler handler = new FileHandler("/dev/", "full", "", Integer.valueOf(-1),
                Boolean.FALSE, Integer.valueOf(-1));
        try {
            CountingErrorManager errorManager = new CountingErrorManager();
            handler.setErrorManager(errorManager);

            // Neither of these can succeed. PrintWriter swallows the resulting
            // IOExceptions, so a check is needed after the flush.
            handler.publish(createRecord("first"));
            handler.publish(createRecord("second"));

            // Opening /dev/full itself must succeed. Otherwise the records would
            // be reported as failures without the write path ever being exercised.
            Assert.assertEquals(0, errorManager.openFailures);

            // Before the fix the failures were swallowed and never reported (zero
            // here). After the fix each failed write is reported once.
            Assert.assertEquals(2, errorManager.writeFailures);

            // The failed writer must not be re-used: closing it must succeed (it is
            // a writer to /dev/full), so no further failures are reported.
            handler.close();
            Assert.assertEquals(0, errorManager.closeFailures);
        } finally {
            handler.close();
        }
    }

    /*
     * If the tail cannot be written or the writer cannot be closed, close() must
     * still discard the writer. Otherwise every subsequent close() repeats the
     * failure report for a writer that is never released.
     */
    @Test
    public void testWriterDiscardedOnTailFailure() throws Exception {
        FileHandler handler = new FileHandler(logsDir.getAbsolutePath(), "tail", "",
                Integer.valueOf(-1), Boolean.FALSE, Integer.valueOf(-1));
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + System.lineSeparator();
            }

            @Override
            public String getTail(Handler h) {
                throw new IllegalStateException("Test tail failure");
            }
        });
        CountingErrorManager errorManager = new CountingErrorManager();
        handler.setErrorManager(errorManager);

        handler.publish(createRecord("message"));
        handler.close();
        // Before the fix the writer was left open, so this reported a second time.
        handler.close();

        Assert.assertEquals(1, errorManager.closeFailures);
    }

    private static LogRecord createRecord(String message) {
        LogRecord record = new LogRecord(Level.INFO, message);
        record.setLoggerName("test");
        return record;
    }

    private static final class CountingErrorManager extends ErrorManager {
        private int closeFailures;
        private int writeFailures;
        private int openFailures;

        @Override
        public void error(String msg, Exception ex, int code) {
            if (code == ErrorManager.CLOSE_FAILURE) {
                closeFailures++;
            } else if (code == ErrorManager.WRITE_FAILURE) {
                writeFailures++;
            } else if (code == ErrorManager.OPEN_FAILURE) {
                openFailures++;
            }
        }
    }

    private void generateLogFiles(File dir, String prefix, String suffix, int amount)
            throws IOException {
        for (int i = 0; i < amount; i++) {
            String date = LocalDate.now().minusDays(i + 1).toString().substring(0, 10);
            File file = new File(dir, prefix + date + suffix);
            if (!file.createNewFile()) {
                Assert.fail("Unable to create " + file.getAbsolutePath());
            }
        }
    }
}
