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
import java.io.Writer;
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
 * Provides same information as default log format but on a single line to make it easier to grep the logs. The only
 * exception is stacktraces which are always preceded by whitespace to make it simple to skip them.
 */
/*
 * Date processing based on AccessLogValve.
 */
public class OneLineFormatter extends Formatter {

    private static final Object threadMxBeanLock = new Object();
    private static volatile ThreadMXBean threadMxBean = null;
    private static final int THREAD_NAME_CACHE_SIZE = 10000;
    private static final ThreadLocal<ThreadNameCache> threadNameCache = ThreadLocal
            .withInitial(() -> new ThreadNameCache(THREAD_NAME_CACHE_SIZE));

    /* Timestamp format */
    private static final String DEFAULT_TIME_FORMAT = "dd-MMM-yyyy HH:mm:ss.SSS";

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

    private volatile MillisHandling millisHandling = MillisHandling.APPEND;


    public OneLineFormatter() {
        String timeFormat = LogManager.getLogManager().getProperty(OneLineFormatter.class.getName() + ".timeFormat");
        if (timeFormat == null) {
            timeFormat = DEFAULT_TIME_FORMAT;
        }
        setTimeFormat(timeFormat);
    }


    /**
     * Specify the time format to use for time stamps in log messages.
     *
     * @param timeFormat The format to use using the {@link java.text.SimpleDateFormat} syntax
     */
    public void setTimeFormat(final String timeFormat) {
        final String cachedTimeFormat;

        if (timeFormat.endsWith(".SSS")) {
            cachedTimeFormat = timeFormat.substring(0, timeFormat.length() - 4);
            millisHandling = MillisHandling.APPEND;
        } else if (timeFormat.contains("SSS")) {
            millisHandling = MillisHandling.REPLACE_SSS;
            cachedTimeFormat = timeFormat;
        } else if (timeFormat.contains("SS")) {
            millisHandling = MillisHandling.REPLACE_SS;
            cachedTimeFormat = timeFormat;
        } else if (timeFormat.contains("S")) {
            millisHandling = MillisHandling.REPLACE_S;
            cachedTimeFormat = timeFormat;
        } else {
            millisHandling = MillisHandling.NONE;
            cachedTimeFormat = timeFormat;
        }

        final DateFormatCache globalDateCache = new DateFormatCache(globalCacheSize, cachedTimeFormat, null);
        localDateCache = ThreadLocal
                .withInitial(() -> new DateFormatCache(localCacheSize, cachedTimeFormat, globalDateCache));
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
        final String threadName = Thread.currentThread().getName();
        if (threadName != null && threadName.startsWith(AsyncFileHandler.THREAD_PREFIX)) {
            // If using the async handler can't get the thread name from the
            // current thread.
            sb.append(getThreadName(record.getLongThreadID()));
        } else {
            sb.append(threadName);
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

        // New line for next record
        sb.append(System.lineSeparator());

        // Stack trace
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new IndentingPrintWriter(sw);
            record.getThrown().printStackTrace(pw);
            pw.close();
            sb.append(sw.getBuffer());
        }

        return sb.toString();
    }

    protected void addTimestamp(StringBuilder buf, long timestamp) {
        String cachedTimeStamp = localDateCache.get().getFormat(timestamp);
        if (millisHandling == MillisHandling.NONE) {
            buf.append(cachedTimeStamp);
        } else if (millisHandling == MillisHandling.APPEND) {
            buf.append(cachedTimeStamp);
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
        } else {
            // Some version of replace
            long frac = timestamp % 1000;
            // Formatted string may vary in length so the insert point may vary
            int insertStart = cachedTimeStamp.indexOf(DateFormatCache.MSEC_PATTERN);
            buf.append(cachedTimeStamp.subSequence(0, insertStart));
            if (frac < 100 && millisHandling == MillisHandling.REPLACE_SSS) {
                buf.append('0');
                if (frac < 10) {
                    buf.append('0');
                }
            } else if (frac < 10 && millisHandling == MillisHandling.REPLACE_SS) {
                buf.append('0');
            }
            buf.append(frac);
            if (millisHandling == MillisHandling.REPLACE_SSS) {
                buf.append(cachedTimeStamp.substring(insertStart + 3));
            } else if (millisHandling == MillisHandling.REPLACE_SS) {
                buf.append(cachedTimeStamp.substring(insertStart + 2));
            } else {
                buf.append(cachedTimeStamp.substring(insertStart + 1));
            }
        }
    }


    /**
     * LogRecord has threadID but no thread name.
     */
    private static String getThreadName(long logRecordThreadId) {
        Map<Long, String> cache = threadNameCache.get();
        String result = cache.get(Long.valueOf(logRecordThreadId));

        if (result != null) {
            return result;
        }

        // Double checked locking OK as threadMxBean is volatile
        if (threadMxBean == null) {
            synchronized (threadMxBeanLock) {
                if (threadMxBean == null) {
                    threadMxBean = ManagementFactory.getThreadMXBean();
                }
            }
        }
        ThreadInfo threadInfo = threadMxBean.getThreadInfo(logRecordThreadId);
        if (threadInfo == null) {
            return Long.toString(logRecordThreadId);
        }
        result = threadInfo.getThreadName();

        cache.put(Long.valueOf(logRecordThreadId), result);

        return result;
    }


    /*
     * This is an LRU cache.
     */
    private static class ThreadNameCache extends LinkedHashMap<Long, String> {

        private static final long serialVersionUID = 1L;

        private final int cacheSize;

        ThreadNameCache(int cacheSize) {
            super(cacheSize, 0.75f, true);
            this.cacheSize = cacheSize;
        }

        @Override
        protected boolean removeEldestEntry(Entry<Long, String> eldest) {
            return (size() > cacheSize);
        }
    }


    /*
     * Minimal implementation to indent the printing of stack traces. This implementation depends on Throwable using
     * WrappedPrintWriter.
     */
    private static class IndentingPrintWriter extends PrintWriter {

        IndentingPrintWriter(Writer out) {
            super(out);
        }

        @Override
        public void println(Object x) {
            super.print('\t');
            super.println(x);
        }
    }


    private enum MillisHandling {
        NONE, APPEND, REPLACE_S, REPLACE_SS, REPLACE_SSS,
    }
}
