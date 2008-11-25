package org.apache.tomcat.jdbc.test;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.tomcat.jdbc.pool.FairBlockingQueue;

import junit.framework.TestCase;

public class TestAsyncQueue extends TestCase {
    protected FairBlockingQueue<Object> queue = null;
    protected void setUp() throws Exception {
        super.setUp();
        this.queue = new FairBlockingQueue<Object>();
    }

    protected void tearDown() throws Exception {
        this.queue = null;
        super.tearDown();
    }
    
    
    public void testAsyncPoll1() throws Exception {
        Object item = new Object();
        queue.offer(item);
        Future<Object> future = queue.pollAsync();
        assertEquals(future.get(),item);
    }

    public void testAsyncPoll2() throws Exception {
        Object item = new Object();
        OfferThread thread = new OfferThread(item,5000);
        thread.start();
        Future<Object> future = queue.pollAsync();
        try {
            future.get(2000, TimeUnit.MILLISECONDS);
            this.assertFalse("Request should have timed out",true);
        }catch (TimeoutException x) {
            this.assertTrue("Request timed out properly",true);
        }catch (Exception x) {
            this.assertTrue("Request threw an error",false);
            x.printStackTrace();
        }
        assertEquals(future.get(),item);
    }

    protected class OfferThread extends Thread {
        Object item = null;
        long delay = 5000;
        volatile boolean offered = false;
        public OfferThread(Object i, long d) {
            this.item = i;
            this.delay = d;
            this.setDaemon(false);
            this.setName(TestAsyncQueue.class.getName()+"-OfferThread");
        }
        public void run() {
            try {
                this.sleep(delay);
            }catch (Exception ignore){}
            offered = true;
            TestAsyncQueue.this.queue.offer(item);
        }
    }
}
