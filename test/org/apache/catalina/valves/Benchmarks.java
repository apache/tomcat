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

package org.apache.catalina.valves;

import java.text.SimpleDateFormat;
import java.util.Date;

import junit.framework.TestCase;

/**
 * Some simple micro-benchmarks to help determine best approach for thread
 * safety in valves, particularly the {@link AccessLogValve}. Implemented as
 * JUnit tests to make the simple to execute but does not used Test* as the
 * class name to avoid being included in the automated unit tests.
 */
public class Benchmarks extends TestCase {
    public void testAccessLogGetDate() throws Exception {
        // Is it better to use a sync or a thread local here?
        BenchmarkTest getDate = new GetDateBenchmarkTest();
        getDate.doTest(5);
    }

    private static class GetDateBenchmarkTest extends BenchmarkTest {
        private long currentMillis = 0;
        private Date currentDate = null;

        private ThreadLocal<Long> currentMillisLocal = new ThreadLocal<Long>() {
            protected Long initialValue() {
                return Long.valueOf(0);
            }
        };
        
        private ThreadLocal<Date> currentDateLocal = new ThreadLocal<Date>();

        public Object doSync() {
            long systime = System.currentTimeMillis();
            if ((systime - currentMillis) > 1000) {
                synchronized (this) {
                    if ((systime - currentMillis) > 1000) {
                        currentDate = new Date(systime);
                        currentMillis = systime;
                    }
                }
            }
            return currentDate; 
        }
        
        public Object doLocal() {
            long systime = System.currentTimeMillis();
            if ((systime - currentMillisLocal.get().longValue()) > 1000) {
                currentDateLocal.set(new Date(systime));
                currentMillisLocal.set(Long.valueOf(systime));
            }
            return currentDateLocal.get();      
        }
    }

    
    public void testAccessLogTimeDateElement() throws Exception {
        // Is it better to use a sync or a thread local here?
        BenchmarkTest timeDateElement = new TimeDateElementBenchmarkTest();
        timeDateElement.doTest(5);
    }

    private static class TimeDateElementBenchmarkTest extends BenchmarkTest {
        private long currentMillis = 0;
        private Date currentDate = null;
        private String currentDateString = null;
        private SimpleDateFormat dayFormatter = new SimpleDateFormat("dd");
        private SimpleDateFormat monthFormatter = new SimpleDateFormat("MM");
        private SimpleDateFormat yearFormatter = new SimpleDateFormat("yyyy");
        private SimpleDateFormat timeFormatter = new SimpleDateFormat("hh:mm:ss");
        
        private ThreadLocal<Long> currentMillisLocal = new ThreadLocal<Long>() {
            protected Long initialValue() {
                return Long.valueOf(0);
            }
        };
        private ThreadLocal<Date> currentDateLocal = new ThreadLocal<Date>();
        private ThreadLocal<SimpleDateFormat> dayFormatterLocal =
            new ThreadLocal<SimpleDateFormat>() {
            protected SimpleDateFormat initialValue() {
                return new SimpleDateFormat("dd");
            }
        };
        private ThreadLocal<SimpleDateFormat> monthFormatterLocal =
            new ThreadLocal<SimpleDateFormat>() {
            protected SimpleDateFormat initialValue() {
                return new SimpleDateFormat("MM");
            }
        };
        private ThreadLocal<SimpleDateFormat> yearFormatterLocal =
            new ThreadLocal<SimpleDateFormat>() {
            protected SimpleDateFormat initialValue() {
                return new SimpleDateFormat("yyyy");
            }
        };
        private ThreadLocal<SimpleDateFormat> timeFormatterLocal =
            new ThreadLocal<SimpleDateFormat>() {
            protected SimpleDateFormat initialValue() {
                return new SimpleDateFormat("hh:mm:ss");
            }
        };
        
        public Object doSync() {
            StringBuffer buf = new StringBuffer();
            Date date = getDateSync();
            if (currentDate != date) {
                synchronized (this) {
                    if (currentDate != date) {
                        StringBuffer current = new StringBuffer(32);
                        current.append('[');
                        current.append(dayFormatter.format(date)); // Day
                        current.append('/');
                        current.append(lookup(monthFormatter.format(date))); // Month
                        current.append('/');
                        current.append(yearFormatter.format(date)); // Year
                        current.append(':');
                        current.append(timeFormatter.format(date)); // Time
                        current.append(']');
                        currentDateString = current.toString();
                        currentDate = date;
                    }
                }
            }
            buf.append(currentDateString);
            return buf;
        }
        
        protected static final String months[] =
        { "Jan", "Feb", "Mar", "Apr", "May", "Jun",
          "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

        private String lookup(String month) {
            int index;
            try {
                index = Integer.parseInt(month) - 1;
            } catch (Throwable t) {
                index = 0;  // Can not happen, in theory
            }
            return (months[index]);
        }
        
        private Date getDateSync() {
            long systime = System.currentTimeMillis();
            if ((systime - currentMillis) > 1000) {
                synchronized (this) {
                    if ((systime - currentMillis) > 1000) {
                        currentDate = new Date(systime);
                        currentMillis = systime;
                    }
                }
            }
            return currentDate; 
        }
        
        public Object doLocal() {
            StringBuffer buf = new StringBuffer();
            Date date = getDateLocal();
            if (currentDate != date) {
                StringBuffer current = new StringBuffer(32);
                current.append('[');
                current.append(dayFormatterLocal.get().format(date)); // Day
                current.append('/');
                current.append(lookup(monthFormatterLocal.get().format(date))); // Month
                current.append('/');
                current.append(yearFormatterLocal.get().format(date)); // Year
                current.append(':');
                current.append(timeFormatterLocal.get().format(date)); // Time
                current.append(']');
                currentDateString = current.toString();
                currentDate = date;
            }
            buf.append(currentDateString);
            return buf;
        }
        
        private Date getDateLocal() {
            long systime = System.currentTimeMillis();
            if ((systime - currentMillisLocal.get().longValue()) > 1000) {
                currentDateLocal.set(new Date(systime));
                currentMillisLocal.set(Long.valueOf(systime));
            }
            return currentDateLocal.get();      
        }
    }


    private static abstract class BenchmarkTest {
        public abstract Object doSync();
        public abstract Object doLocal();

        public void doTest(int threadCount) throws Exception {
            for (int iterations = 1000000; iterations < 10000001; iterations+=1000000) {
                doTestInternal(threadCount, iterations, true);
                doTestInternal(threadCount, iterations, false);
            }
        }
        
        private void doTestInternal(int threadCount, int iterations,
                boolean useSyncs) throws Exception {
            long start = System.currentTimeMillis();
            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(new TestThread(iterations, useSyncs, this));
            }
            for (int i = 0; i < threadCount; i++) {
                threads[i].start();
            }
            for (int i = 0; i < threadCount; i++) {
                threads[i].join();
            }
            long end = System.currentTimeMillis();
            
            System.out.println("testAccessLogGetDate: " + threadCount +
                    " threads and " + iterations + " iterations " +
                    (useSyncs?"using Syncs":"using ThreadLocals") +
                    " took " + (end-start) + "ms");
        }

    }

    private static class TestThread implements Runnable {
        private int count;
        private boolean useSync;
        private BenchmarkTest benchmarkTest;
        
        public TestThread(int count, boolean useSync, BenchmarkTest benchmarkTest) {
            this.count = count;
            this.useSync = useSync;
            this.benchmarkTest = benchmarkTest;
        }
        
        public void run() {
            for (int i = 0; i < count; i++) {
                if (useSync) {
                    benchmarkTest.doSync();
                } else {
                    benchmarkTest.doLocal();
                }
            }
        }
        
    }
}
