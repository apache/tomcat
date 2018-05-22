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

import org.junit.Test;

/**
 * Compares date/time format implementations. The current implementation ({@link
 * DateFormatCache} is one to two orders of magnitude faster than
 * {@link String#format(String, Object...)}
 */
public class TestOneLineFormatterPerformance {

    @Test
    public void testDateFormat() throws Exception {

        int iters = 1000000;
        DateFormat[] dfs = new DateFormat[] { new StringFormatImpl(), new DateFormatCacheImpl() };

        for (DateFormat df : dfs) {
            long start = System.nanoTime();
            for (int i = 0; i < iters; i++) {
                df.format(System.nanoTime());
            }
            long end = System.nanoTime();
            System.out.println(
                    "Impl: [" + df.getClass().getName() + "] took [" + (end-start) + "] ns");
        }

    }


    private interface DateFormat {
        String format(long timestamp);
    }


    private static class StringFormatImpl implements DateFormat {

        @Override
        public String format(long timestamp) {
            return String.format("%1$td-%1$tb-%1$tY %1$tH:%1$tM:%1$tS", Long.valueOf(timestamp));
        }
    }


    private static class DateFormatCacheImpl implements DateFormat {

        private final DateFormatCache cache;

        public DateFormatCacheImpl() {
            cache = new DateFormatCache(5, "dd-MMM-yyyy HH:mm:ss",  null);
        }

        @Override
        public String format(long timestamp) {
            return cache.getFormat(timestamp);
        }
    }
}
