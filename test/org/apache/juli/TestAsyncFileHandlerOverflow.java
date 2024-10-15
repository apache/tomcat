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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.apache.juli.AsyncFileHandler.LoggerExecutorService;

@RunWith(Parameterized.class)
public class TestAsyncFileHandlerOverflow {

    private static final String PREFIX = "TestAsyncFileHandler.";
    private static final String SUFFIX = ".log";
    private static final Logger logger = Logger.getLogger(TestAsyncFileHandlerOverflow.class.getName());
    {
        logger.setUseParentHandlers(false);
    }

    @Parameters(name = "{index}: overflowDropType[{0}]")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
                { Integer.valueOf(AsyncFileHandler.OVERFLOW_DROP_LAST), "START\n1\n3\n" },
                { Integer.valueOf(AsyncFileHandler.OVERFLOW_DROP_FIRST), "START\n2\n3\n" },
                { Integer.valueOf(AsyncFileHandler.OVERFLOW_DROP_FLUSH), "START\n1\n2\n3\n" },
                { Integer.valueOf(AsyncFileHandler.OVERFLOW_DROP_CURRENT), "START\n1\n2\n" } });
    }

    private final CountDownLatch latch = new CountDownLatch(1);
    private Path logsDir;
    private LoggerExecutorService loggerService;
    private AsyncFileHandler handler;

    private final int overflowDropType;
    private final String expected;

    public TestAsyncFileHandlerOverflow(final int overflowDropType, final String expected) {
        this.overflowDropType = overflowDropType;
        this.expected = expected;
    }

    @Before
    public void setUp() throws IOException {
        final Path logsBase = Paths.get(System.getProperty("tomcat.test.temp", "output/tmp"));
        Files.createDirectories(logsBase);
        this.logsDir = Files.createTempDirectory(logsBase, "test");
        final Formatter formatter = new Formatter() {

            @Override
            public String format(LogRecord record) {
                return record.getMessage() + "\n";
            }
        };
        // Setup an executor that blocks until the first rejection
        this.loggerService = new LoggerExecutorService(overflowDropType, 2) {

            @Override
            protected void beforeExecute(Thread t, Runnable r) {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                super.beforeExecute(t, r);
            }
        };
        final RejectedExecutionHandler rejectionHandler = loggerService.getRejectedExecutionHandler();
        loggerService.setRejectedExecutionHandler(new RejectedExecutionHandler() {

            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                // Generally, the latch needs to be released after the
                // RejectedExecutionHandler has completed but for the flush case
                // the latch needs to be released first (else the test loops)
                if (overflowDropType == AsyncFileHandler.OVERFLOW_DROP_FLUSH) {
                    latch.countDown();
                }
                rejectionHandler.rejectedExecution(r, executor);
                if (overflowDropType != AsyncFileHandler.OVERFLOW_DROP_FLUSH) {
                    latch.countDown();
                }
            }
        });
        this.handler = new AsyncFileHandler(logsDir.toString(), PREFIX, SUFFIX, Integer.valueOf(1), loggerService);
        handler.setFormatter(formatter);
        logger.addHandler(handler);
        handler.open();
    }

    @After
    public void cleanUp() {
        handler.close();
        logger.removeHandler(handler);
    }

    @Test
    public void testOverFlow() throws IOException, InterruptedException {
        handler.open();
        logger.warning("START"); // blocks async thread
        // these are queued
        logger.warning("1");
        logger.warning("2");
        logger.warning("3"); // overflows executor and unblocks aync thread
        loggerService.shutdown();
        // after shutdown was issued
        logger.warning("IGNORE");

        loggerService.awaitTermination(1, TimeUnit.SECONDS);
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        Files.copy(logsDir.resolve(PREFIX + LocalDate.now() + SUFFIX), os);
        final String actual = new String(os.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertEquals(expected, actual);
        handler.close();
    }
}