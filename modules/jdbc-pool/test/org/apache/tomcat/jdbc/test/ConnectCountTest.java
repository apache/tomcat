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

import org.apache.tomcat.jdbc.pool.DataSourceProxy;
import org.apache.tomcat.jdbc.test.driver.Driver;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public class ConnectCountTest extends DefaultTestCase {
    public ConnectCountTest(String name) {
        super(name);
    }
    
    protected boolean run = true;
    protected long sleep = Long.getLong("sleep", 10);
    protected long complete = Long.getLong("complete",20000);
    protected boolean printthread = Boolean.getBoolean("printthread");
    CountDownLatch latch = null;


    @Override
    public org.apache.tomcat.jdbc.pool.DataSource createDefaultDataSource() {
        // TODO Auto-generated method stub
        org.apache.tomcat.jdbc.pool.DataSource ds = super.createDefaultDataSource();
        ds.getPoolProperties().setDriverClassName(Driver.class.getName());
        ds.getPoolProperties().setUrl(Driver.url);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxIdle(10);
        ds.getPoolProperties().setMinIdle(10);
        ds.getPoolProperties().setMaxActive(10);
        return ds;
    }


    @Override
    protected void tearDown() throws Exception {
        Driver.reset();
        super.tearDown();
    }


    protected void printThreadResults(TestThread[] threads, String name, int active, int expected) {
        long minfetch = Long.MAX_VALUE, maxfetch = Long.MIN_VALUE, totalfetch = 0;
        long maxwait = 0, minwait = Long.MAX_VALUE, averagewait = 0, totalwait = 0;
        float avgfetch = 0;
        for (int i=0; i<threads.length; i++) {
            TestThread t = threads[i];
            totalfetch += t.nroffetch;
            totalwait  += t.totalwait;
            maxwait = Math.max(maxwait,t.maxwait);
            minwait = Math.min(minwait, t.minwait);
            minfetch = Math.min(minfetch, t.nroffetch);
            maxfetch = Math.max(maxfetch, t.nroffetch);
            if (ConnectCountTest.this.printthread)
                System.out.println(t.getName()+" : Nr-of-fetch:"+t.nroffetch+ " Max fetch Time:"+(((float)t.maxwait)/1000000f)+"ms. :Max close time:"+(((float)t.cmax)/1000000f)+"ms.");
        }
        System.out.println("["+name+"] Max fetch:"+(maxfetch)+" Min fetch:"+(minfetch)+" Average fetch:"+
                           (((float)totalfetch))/(float)threads.length);
        System.out.println("["+name+"] Max wait:"+(((float)maxwait)/1000000f)+"ms. Min wait:"+(((float)minwait)/1000000f)+"ms. Average wait:"+(((((float)totalwait))/(float)totalfetch)/1000000f)+" ms.");
        System.out.println("["+name+"] Max active:"+active+" Expected Active:"+expected);
        
        
    }
    
    public void testDBCPThreads20Connections10() throws Exception {
        System.out.println("[testDBCPThreads20Connections10] Starting fairness - DBCP");
        this.threadcount = 20;
        init();
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
        printThreadResults(threads,"testDBCPThreads20Connections10",Driver.connectCount.get(),10);
        tearDown();
    }

    public void testPoolThreads20Connections10() throws Exception {
        System.out.println("[testPoolThreads20Connections10] Starting fairness - Tomcat JDBC - Non Fair");
        init();
        this.threadcount = 20;
        this.transferProperties();
        this.datasource.getConnection().close();
        latch = new CountDownLatch(threadcount);
        long start = System.currentTimeMillis();
        TestThread[] threads = new TestThread[threadcount];
        for (int i=0; i<threadcount; i++) {
            threads[i] = new TestThread();
            threads[i].setName("tomcat-pool-"+i);
            threads[i].d = this.datasource;
            
        }
        for (int i=0; i<threadcount; i++) {
            threads[i].start();
        }
        if (!latch.await(complete+1000,TimeUnit.MILLISECONDS)) {
            System.out.println("Latch timed out.");
        }
        this.run = false;
        long delta = System.currentTimeMillis() - start;
        printThreadResults(threads,"testPoolThreads20Connections10",Driver.connectCount.get(),10);
        tearDown();

    }

    public void testPoolThreads20Connections10Fair() throws Exception {
        System.out.println("[testPoolThreads20Connections10Fair] Starting fairness - Tomcat JDBC - Fair");
        init();
        this.threadcount = 20;
        this.datasource.getPoolProperties().setFairQueue(true);
        this.transferProperties();
        this.datasource.getConnection().close();
        latch = new CountDownLatch(threadcount);
        long start = System.currentTimeMillis();
        TestThread[] threads = new TestThread[threadcount];
        for (int i=0; i<threadcount; i++) {
            threads[i] = new TestThread();
            threads[i].setName("tomcat-pool-"+i);
            threads[i].d = this.datasource;
            
        }
        for (int i=0; i<threadcount; i++) {
            threads[i].start();
        }
        if (!latch.await(complete+1000,TimeUnit.MILLISECONDS)) {
            System.out.println("Latch timed out.");
        }
        this.run = false;
        long delta = System.currentTimeMillis() - start;
        printThreadResults(threads,"testPoolThreads20Connections10Fair",Driver.connectCount.get(),10);
        tearDown();
    }
 
    public void testPoolThreads20Connections10FairAsync() throws Exception {
        System.out.println("[testPoolThreads20Connections10FairAsync] Starting fairness - Tomcat JDBC - Fair - Async");
        init();
        this.threadcount = 20;
        this.datasource.getPoolProperties().setFairQueue(true);
        this.datasource.getPoolProperties().setInitialSize(this.datasource.getPoolProperties().getMaxActive());
        this.transferProperties();
        this.datasource.getConnection().close();
        latch = new CountDownLatch(threadcount);
        long start = System.currentTimeMillis();
        TestThread[] threads = new TestThread[threadcount];
        for (int i=0; i<threadcount; i++) {
            threads[i] = new TestThread();
            threads[i].setName("tomcat-pool-"+i);
            threads[i].async = true;
            threads[i].d = this.datasource;
            
        }
        for (int i=0; i<threadcount; i++) {
            threads[i].start();
        }
        if (!latch.await(complete+1000,TimeUnit.MILLISECONDS)) {
            System.out.println("Latch timed out.");
        }
        this.run = false;
        long delta = System.currentTimeMillis() - start;
        printThreadResults(threads,"testPoolThreads20Connections10FairAsync",Driver.connectCount.get(),10);
        tearDown();
    }
    
//    public void testC3P0Threads20Connections10() throws Exception {
//        System.out.println("[testC3P0Threads20Connections10] Starting fairness - C3P0");
//        init();
//        this.threadcount = 20;
//        this.transferPropertiesToC3P0();
//        this.datasource.getConnection().close();
//        latch = new CountDownLatch(threadcount);
//        long start = System.currentTimeMillis();
//        TestThread[] threads = new TestThread[threadcount];
//        for (int i=0; i<threadcount; i++) {
//            threads[i] = new TestThread();
//            threads[i].setName("tomcat-pool-"+i);
//            threads[i].d = this.c3p0Datasource;
//            
//        }
//        for (int i=0; i<threadcount; i++) {
//            threads[i].start();
//        }
//        if (!latch.await(complete+1000,TimeUnit.MILLISECONDS)) {
//            System.out.println("Latch timed out.");
//        }
//        this.run = false;
//        long delta = System.currentTimeMillis() - start;
//        printThreadResults(threads,"testC3P0Threads20Connections10",Driver.connectCount.get(),10);
//        tearDown();
//
//    }

    
    public class TestThread extends Thread {
        protected DataSource d;
        protected String query = null;
        protected long sleep = 10;
        protected boolean async = false;
        long minwait = Long.MAX_VALUE, maxwait = -1, totalwait=0, totalcmax=0, cmax = -1, nroffetch = 0, totalruntime = 0;
        @Override
        public void run() {
            try {
                long now = System.currentTimeMillis();
                while (ConnectCountTest.this.run) {
                    if ((System.currentTimeMillis()-now)>=ConnectCountTest.this.complete) break;
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
                        totalwait += delta;
                        maxwait = Math.max(delta, maxwait);
                        minwait = Math.min(delta, minwait);
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
                            if (ConnectCountTest.this.sleep>0) sleep(ConnectCountTest.this.sleep); 
                        } catch (InterruptedException x) {
                            interrupted();
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
                ConnectCountTest.this.latch.countDown();
            }
            if (System.getProperty("print-thread-stats")!=null) {
                System.out.println("["+getName()+"] "+
                    "\n\tMax time to retrieve connection:"+(((float)maxwait)/1000f/1000f)+" ms."+
                    "\n\tTotal time to retrieve connection:"+(((float)totalwait)/1000f/1000f)+" ms."+
                    "\n\tAverage time to retrieve connection:"+(((float)totalwait)/1000f/1000f)/(float)nroffetch+" ms."+
                    "\n\tMax time to close connection:"+(((float)cmax)/1000f/1000f)+" ms."+
                    "\n\tTotal time to close connection:"+(((float)totalcmax)/1000f/1000f)+" ms."+
                    "\n\tAverage time to close connection:"+(((float)totalcmax)/1000f/1000f)/(float)nroffetch+" ms."+
                    "\n\tRun time:"+(((float)totalruntime)/1000f/1000f)+" ms."+
                    "\n\tNr of fetch:"+nroffetch);
            }
        }
    }
}

