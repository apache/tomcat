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
package org.apache.tomcat.jdbc.test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.apache.tomcat.jdbc.pool.DataSourceProxy;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public class FairnessTest extends DefaultTestCase {
    public FairnessTest(String name) {
        super(name);
    }
    
    protected boolean run = true;
    protected long sleep = 10;
    protected long complete = 20000;
    CountDownLatch latch = null;
    protected void printThreadResults(TestThread[] threads, String name) {
        long minfetch = Long.MAX_VALUE, maxfetch = Long.MIN_VALUE, totalfetch = 0;
        float avgfetch = 0;
        for (int i=0; i<threads.length; i++) {
            TestThread t = threads[i];
            totalfetch += t.nroffetch;
            minfetch = Math.min(minfetch, t.nroffetch);
            maxfetch = Math.max(maxfetch, t.nroffetch);
            System.out.println(t.getName()+" : Nr-of-fetch:"+t.nroffetch+ " Max fetch Time:"+(((float)t.max)/1000000f)+"ms. :Max close time:"+(((float)t.cmax)/1000000f)+"ms.");
        }
        System.out.println("["+name+"] Max fetch:"+(maxfetch)+" Min fetch:"+(minfetch)+" Average fetch:"+
                           (((float)totalfetch))/(float)threads.length);
    }
    
    public void testDBCPThreads20Connections10() throws Exception {
        System.out.println("Starting fairness - DBCP");
        init();
        this.datasource.getPoolProperties().setMaxActive(10);
        this.threadcount = 20;
        this.transferProperties();
        this.tDatasource.getConnection().close();
        latch = new CountDownLatch(threadcount);
        long start = System.currentTimeMillis();
        TestThread[] threads = new TestThread[threadcount];
        for (int i=0; i<threadcount; i++) {
            threads[i] = new TestThread();
            threads[i].setName("tomcat-dbcp-"+i);
            threads[i].d = this.tDatasource;
            
        }
        for (int i=0; i<threadcount; i++) {
            threads[i].start();
        }
        if (!latch.await(complete+1000,TimeUnit.MILLISECONDS)) {
            System.out.println("Latch timed out.");
        }
        this.run = false;
        long delta = System.currentTimeMillis() - start;
        printThreadResults(threads,"testDBCPThreads20Connections10");
        System.out.println("Completed fairness - DBCP");
        tearDown();
    }

    public void testPoolThreads20Connections10() throws Exception {
        System.out.println("Starting fairness - Tomcat JDBC - Non Fair");
        init();
        this.datasource.getPoolProperties().setMaxActive(10);
        this.threadcount = 20;
        this.transferProperties();
        this.datasource.getConnection().close();
        latch = new CountDownLatch(threadcount);
        long start = System.currentTimeMillis();
        TestThread[] threads = new TestThread[threadcount];
        for (int i=0; i<threadcount; i++) {
            threads[i] = new TestThread();
            threads[i].setName("tomcat-pool-"+i);
            threads[i].d = DataSourceFactory.getDataSource(this.datasource);
            
        }
        for (int i=0; i<threadcount; i++) {
            threads[i].start();
        }
        if (!latch.await(complete+1000,TimeUnit.MILLISECONDS)) {
            System.out.println("Latch timed out.");
        }
        this.run = false;
        long delta = System.currentTimeMillis() - start;
        printThreadResults(threads,"testPoolThreads20Connections10");
        System.out.println("Completed fairness - Tomcat JDBC - Non Fair");
        tearDown();

    }

    public void testPoolThreads20Connections10Fair() throws Exception {
        System.out.println("Starting fairness - Tomcat JDBC - Fair");
        init();
        this.datasource.getPoolProperties().setMaxActive(10);
        this.datasource.getPoolProperties().setFairQueue(true);
        this.threadcount = 20;
        this.transferProperties();
        this.datasource.getConnection().close();
        latch = new CountDownLatch(threadcount);
        long start = System.currentTimeMillis();
        TestThread[] threads = new TestThread[threadcount];
        for (int i=0; i<threadcount; i++) {
            threads[i] = new TestThread();
            threads[i].setName("tomcat-pool-"+i);
            threads[i].d = DataSourceFactory.getDataSource(this.datasource);
            
        }
        for (int i=0; i<threadcount; i++) {
            threads[i].start();
        }
        if (!latch.await(complete+1000,TimeUnit.MILLISECONDS)) {
            System.out.println("Latch timed out.");
        }
        this.run = false;
        long delta = System.currentTimeMillis() - start;
        printThreadResults(threads,"testPoolThreads20Connections10Fair");
        System.out.println("Completed fairness - Tomcat JDBC - Fair");
        tearDown();
    }

    public void testPoolThreads20Connections10FairAsync() throws Exception {
        System.out.println("Starting fairness - Tomcat JDBC - Fair - Async");
        init();
        this.datasource.getPoolProperties().setMaxActive(10);
        this.datasource.getPoolProperties().setFairQueue(true);
        this.threadcount = 20;
        this.transferProperties();
        this.datasource.getConnection().close();
        latch = new CountDownLatch(threadcount);
        long start = System.currentTimeMillis();
        TestThread[] threads = new TestThread[threadcount];
        for (int i=0; i<threadcount; i++) {
            threads[i] = new TestThread();
            threads[i].setName("tomcat-pool-"+i);
            threads[i].async = true;
            threads[i].d = DataSourceFactory.getDataSource(this.datasource);
            
        }
        for (int i=0; i<threadcount; i++) {
            threads[i].start();
        }
        if (!latch.await(complete+1000,TimeUnit.MILLISECONDS)) {
            System.out.println("Latch timed out.");
        }
        this.run = false;
        long delta = System.currentTimeMillis() - start;
        printThreadResults(threads,"testPoolThreads20Connections10FairAsync");
        System.out.println("Completed fairness - Tomcat JDBC - Fair - Async");
        tearDown();
    }
    
    public class TestThread extends Thread {
        protected DataSource d;
        protected String query = null;
        protected long sleep = 10;
        protected boolean async = false;
        long max = -1, totalmax=0, totalcmax=0, cmax = -1, nroffetch = 0, totalruntime = 0;
        public void run() {
            try {
                long now = System.currentTimeMillis();
                while (FairnessTest.this.run) {
                    if ((System.currentTimeMillis()-now)>=FairnessTest.this.complete) break;
                    long start = System.nanoTime();
                    Connection con = null;
                    try {
                        if (async) {
                            Future<Connection> cf = ((DataSourceProxy)d).getConnectionAsync();
                            con  = cf.get();
                        } else {
                            con = d.getConnection();
                        }
                        long delta = System.nanoTime() - start;
                        totalmax += delta;
                        max = Math.max(delta, max);
                        nroffetch++;
                        if (query!=null) {
                            Statement st = con.createStatement();
                            ResultSet rs = st.executeQuery(query);
                            while (rs.next()) {
                            }
                            rs.close();
                            st.close();
                        }
                        try { 
                            this.sleep(FairnessTest.this.sleep); 
                        } catch (InterruptedException x) {
                            this.interrupted();
                        }
                    } finally {
                        long cstart = System.nanoTime();
                        if (con!=null) try {con.close();}catch(Exception x) {x.printStackTrace();}
                        long cdelta = System.nanoTime() - cstart;
                        totalcmax += cdelta;
                        cmax = Math.max(cdelta, cmax);
                    }
                    totalruntime+=(System.nanoTime()-start);
                }

            } catch (Exception x) {
                x.printStackTrace();
            } finally {
                FairnessTest.this.latch.countDown();
            }
            if (System.getProperty("print-thread-stats")!=null) {
                System.out.println("["+getName()+"] "+
                    "\n\tMax time to retrieve connection:"+(((float)max)/1000f/1000f)+" ms."+
                    "\n\tTotal time to retrieve connection:"+(((float)totalmax)/1000f/1000f)+" ms."+
                    "\n\tAverage time to retrieve connection:"+(((float)totalmax)/1000f/1000f)/(float)nroffetch+" ms."+
                    "\n\tMax time to close connection:"+(((float)cmax)/1000f/1000f)+" ms."+
                    "\n\tTotal time to close connection:"+(((float)totalcmax)/1000f/1000f)+" ms."+
                    "\n\tAverage time to close connection:"+(((float)totalcmax)/1000f/1000f)/(float)nroffetch+" ms."+
                    "\n\tRun time:"+(((float)totalruntime)/1000f/1000f)+" ms."+
                    "\n\tNr of fetch:"+nroffetch);
            }
        }
    }
}
