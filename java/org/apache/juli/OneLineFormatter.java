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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * Provides same information as default log format but on a single line to make
 * it easier to grep the logs. The only exception is stacktraces which are
 * always preceded by whitespace to make it simple to skip them.
 */
/*
 * Date processing based on AccessLogValve.
 */
public class OneLineFormatter extends Formatter {

    private static final String ST_SEP = System.lineSeparator() + " ";
    private static final String UNKNOWN_THREAD_NAME = "Unknown thread with ID ";
    private static final Object threadMxBeanLock = new Object();
    private static volatile ThreadMXBean threadMxBean = null;
    private static final int THREAD_NAME_CACHE_SIZE = 10000;
    private static ThreadLocal<ThreadNameCache> threadNameCache = new ThreadLocal<ThreadNameCache>() {
        @Override
        protected ThreadNameCache initialValue() {
            return new ThreadNameCache(THREAD_NAME_CACHE_SIZE);
        }
    };

    /* Timestamp format */
    private static final String DEFAULT_TIME_FORMAT = "dd-MMM-yyyy HH:mm:ss";

    /**
     * The size of our global date format cache
     */
    private static final int globalCacheSize = 30;

    /**
     * The size of our thread local date format cache
     */
    private static final int localCacheSize = 5;

    /**
     * Thread local date format cache.
     */
    private ThreadLocal<DateFormatCache> localDateCache;


    public OneLineFormatter() {
        String timeFormat = LogManager.getLogManager().getProperty(
                OneLineFormatter.class.getName() + ".timeFormat");
        if (timeFormat == null) {
            timeFormat = DEFAULT_TIME_FORMAT;
        }
        setTimeFormat(timeFormat);
    }


    /**
     * Specify the time format to use for time stamps in log messages.
     *
     * @param timeFormat The format to use using the
     *                   {@link java.text.SimpleDateFormat} syntax
     */
    public void setTimeFormat(String timeFormat) {
        DateFormatCache globalDateCache = new DateFormatCache(globalCacheSize, timeFormat, null);
        localDateCache = new ThreadLocal<DateFormatCache>() {
            @Override
            protected DateFormatCache initialValue() {
                return new DateFormatCache(localCacheSize, timeFormat, globalDateCache);
            }
        };
    }


    /**
     * Obtain the format currently being used for time stamps in log messages.
     *
     * @return The current format in {@link java.text.SimpleDateFormat} syntax
     */
    public String getTimeFormat() {
        return localDateCache.get().getTimeFormat();
    }


    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();

        // Timestamp
        addTimestamp(sb, record.getMillis());

        // Severity
        sb.append(' ');
        sb.append(record.getLevel().getLocalizedName());

        // Thread
        sb.append(' ');
        sb.append('[');
        if (Thread.currentThread() instanceof AsyncFileHandler.LoggerThread) {
            // If using the async handler can't get the thread name from the
            // current thread.
            sb.append(getThreadName(record.getThreadID()));
        } else {
            sb.append(Thread.currentThread().getName());
        }
        sb.append(']');

        // Source
        sb.append(' ');
        sb.append(record.getSourceClassName());
        sb.append('.');
        sb.append(record.getSourceMethodName());

        // Message
        sb.append(' ');
        sb.append(formatMessage(record));

        // Stack trace
        if (record.getThrown() != null) {
            sb.append(ST_SEP);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            record.getThrown().printStackTrace(pw);
            pw.close();
            sb.append(sw.getBuffer());
        }

        // New line for next record
        sb.append(System.lineSeparator());

        return sb.toString();
    }

    protected void addTimestamp(StringBuilder buf, long timestamp) {
        buf.append(localDateCache.get().getFormat(timestamp));
        long frac = timestamp % 1000;
        buf.append('.');
        if (frac < 100) {
            if (frac < 10) {
                buf.append('0');
                buf.append('0');
            } else {
                buf.append('0');
            }
        }
        buf.append(frac);
    }


    /**
     * LogRecord has threadID but no thread name.
     * LogRecord uses an int for thread ID but thread IDs are longs.
     * If the real thread ID > (Integer.MAXVALUE / 2) LogRecord uses it's own
     * ID in an effort to avoid clashes due to overflow.
     * <p>
     * Words fail me to describe what I think of the design decision to use an
     * int in LogRecord for a long value and the resulting mess that follows.
     */
    private static String getThreadName(int logRecordThreadId) {
        Map<Integer,String> cache = threadNameCache.get();
        String result = null;

        if (logRecordThreadId > (Integer.MAX_VALUE / 2)) {
            result = cache.get(Integer.valueOf(logRecordThreadId));
        }

        if (result != null) {
            return result;
        }

        if (logRecordThreadId > Integer.MAX_VALUE / 2) {
            result = UNKNOWN_THREAD_NAME + logRecordThreadId;
        } else {
            // Double checked locking OK as threadMxBean is volatile
            if (threadMxBean == null) {
                synchronized (threadMxBeanLock) {
                    if (threadMxBean == null) {
                        threadMxBean = ManagementFactory.getThreadMXBean();
                    }
                }
            }
            ThreadInfo threadInfo =
                    threadMxBean.getThreadInfo(logRecordThreadId);
            if (threadInfo == null) {
                return Long.toString(logRecordThreadId);
            }
            result = threadInfo.getThreadName();
        }

        cache.put(Integer.valueOf(logRecordThreadId), result);

        return result;
    }


    private static class ThreadNameCache extends LinkedHashMap<Integer,String> {

        private static final long serialVersionUID = 1L;

        private final int cacheSize;

        public ThreadNameCache(int cacheSize) {
            this.cacheSize = cacheSize;
        }

        @Override
        protected boolean removeEldestEntry(Entry<Integer, String> eldest) {
            return (size() > cacheSize);
        }
    }
}
