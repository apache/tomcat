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

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogRecord;

/**
 * A {@link FileHandler} implementation that uses a queue of log entries.
 *
 * <p>Configuration properties are inherited from the {@link FileHandler}
 * class. This class does not add its own configuration properties for the
 * logging configuration, but relies on the following system properties
 * instead:</p>
 *
 * <ul>
 *   <li><code>org.apache.juli.AsyncOverflowDropType</code>
 *    Default value: <code>1</code></li>
 *   <li><code>org.apache.juli.AsyncMaxRecordCount</code>
 *    Default value: <code>10000</code></li>
 * </ul>
 *
 * <p>See the System Properties page in the configuration reference of Tomcat.</p>
 */
public class AsyncFileHandler extends FileHandler {

    static final String THREAD_PREFIX = "AsyncFileHandlerWriter-";

    public static final int OVERFLOW_DROP_LAST    = 1;
    public static final int OVERFLOW_DROP_FIRST   = 2;
    public static final int OVERFLOW_DROP_FLUSH   = 3;
    public static final int OVERFLOW_DROP_CURRENT = 4;

    public static final int DEFAULT_OVERFLOW_DROP_TYPE = 1;
    public static final int DEFAULT_MAX_RECORDS        = 10000;

    public static final int OVERFLOW_DROP_TYPE = Integer.parseInt(
            System.getProperty("org.apache.juli.AsyncOverflowDropType",
                               Integer.toString(DEFAULT_OVERFLOW_DROP_TYPE)));
    public static final int MAX_RECORDS = Integer.parseInt(
            System.getProperty("org.apache.juli.AsyncMaxRecordCount",
                               Integer.toString(DEFAULT_MAX_RECORDS)));

    private static final LoggerExecutorService LOGGER_SERVICE =
            new LoggerExecutorService(OVERFLOW_DROP_TYPE, MAX_RECORDS);

    private final Object closeLock = new Object();
    protected volatile boolean closed = false;
    private final LoggerExecutorService loggerService;

    public AsyncFileHandler() {
        this(null, null, null);
    }

    public AsyncFileHandler(String directory, String prefix, String suffix) {
        this(directory, prefix, suffix, null);
    }

    public AsyncFileHandler(String directory, String prefix, String suffix, Integer maxDays) {
        this(directory, prefix, suffix, maxDays, LOGGER_SERVICE);
    }

    AsyncFileHandler(String directory, String prefix, String suffix, Integer maxDays,
            LoggerExecutorService loggerService) {
        super(directory, prefix, suffix, maxDays);
        loggerService.registerHandler();
        this.loggerService = loggerService;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
        }
        loggerService.deregisterHandler();
        super.close();
    }

    @Override
    public void open() {
        if (!closed) {
            return;
        }
        synchronized (closeLock) {
            if (!closed) {
                return;
            }
            closed = false;
        }
        loggerService.registerHandler();
        super.open();
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        // fill source entries, before we hand the record over to another
        // thread with another class loader
        record.getSourceMethodName();
        loggerService.execute(new Runnable() {

            @Override
            public void run() {
                /*
                 * During Tomcat shutdown, the Handlers are closed before the
                 * executor queue is flushed therefore the closed flag is
                 * ignored if the executor is shutting down.
                 */
                if (!closed || loggerService.isTerminating()) {
                    publishInternal(record);
                }
            }
        });
    }

    protected void publishInternal(LogRecord record) {
        super.publish(record);
    }


    static class LoggerExecutorService extends ThreadPoolExecutor {

        private static final ThreadFactory THREAD_FACTORY = new ThreadFactory(THREAD_PREFIX);

        /*
         * Implementation note: Use of this count could be extended to
         * start/stop the LoggerExecutorService but that would require careful
         * locking as the current size of the queue also needs to be taken into
         * account and there are lost of edge cases when rapidly starting and
         * stopping handlers.
         */
        private final AtomicInteger handlerCount = new AtomicInteger();

        public LoggerExecutorService(final int overflowDropType, final int maxRecords) {
            super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(maxRecords), THREAD_FACTORY);
            switch (overflowDropType) {
                case OVERFLOW_DROP_LAST:
                default:
                    setRejectedExecutionHandler(new DropLastPolicy());
                    break;
                case OVERFLOW_DROP_FIRST:
                    setRejectedExecutionHandler(new DiscardOldestPolicy());
                    break;
                case OVERFLOW_DROP_FLUSH:
                    setRejectedExecutionHandler(new DropFlushPolicy());
                    break;
                case OVERFLOW_DROP_CURRENT:
                    setRejectedExecutionHandler(new DiscardPolicy());
            }
        }

        @Override
        public LinkedBlockingDeque<Runnable> getQueue() {
            return (LinkedBlockingDeque<Runnable>) super.getQueue();
        }

        public void registerHandler() {
            handlerCount.incrementAndGet();
        }

        public void deregisterHandler() {
            int newCount = handlerCount.decrementAndGet();
            if (newCount == 0) {
                try {
                    Thread dummyHook = new Thread();
                    Runtime.getRuntime().addShutdownHook(dummyHook);
                    Runtime.getRuntime().removeShutdownHook(dummyHook);
                } catch (IllegalStateException ise) {
                    // JVM is shutting down.
                    // Allow up to 10s for for the queue to be emptied
                    shutdown();
                    try {
                        awaitTermination(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    shutdownNow();
                }
            }
        }
    }


    private static class DropFlushPolicy implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            while (true) {
                if (executor.isShutdown()) {
                    break;
                }
                try {
                    if (executor.getQueue().offer(r, 1000, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RejectedExecutionException("Interrupted", e);
                }
            }
        }
    }

    private static class DropLastPolicy implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                ((LoggerExecutorService) executor).getQueue().pollLast();
                executor.execute(r);
            }
        }
    }
}
