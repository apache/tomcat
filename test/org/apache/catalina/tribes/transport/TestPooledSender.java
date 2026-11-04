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
package org.apache.catalina.tribes.transport;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;

public class TestPooledSender {

    private static final long TIMEOUT = 10000;


    private static class Pool extends PooledSender {
        @Override
        public DataSender getNewDataSender() {
            return (DataSender) Proxy.newProxyInstance(DataSender.class.getClassLoader(),
                    new Class<?>[] { DataSender.class }, (proxy, method, args) -> {
                        if (method.getReturnType() == boolean.class) {
                            return Boolean.FALSE;
                        }
                        return null;
                    });
        }

        @Override
        public void sendMessage(Member[] destination, ChannelMessage msg) throws ChannelException {
            // NO-OP
        }
    }


    private static Pool createPool(int size, long maxWait) throws Exception {
        Pool pool = new Pool();
        pool.setPoolSize(size);
        pool.setMaxWait(maxWait);
        pool.connect();
        return pool;
    }


    private static Thread getSenderInThread(Pool pool, AtomicReference<DataSender> result,
            AtomicReference<Boolean> interrupted) {
        Thread thread = new Thread(() -> {
            result.set(pool.getSender());
            interrupted.set(Boolean.valueOf(Thread.currentThread().isInterrupted()));
        });
        thread.start();
        return thread;
    }


    @Test(timeout = 60000)
    public void testWaitEndsWhenSenderReturned() throws Exception {
        Pool pool = createPool(1, 0);
        DataSender first = pool.getSender();
        Assert.assertNotNull(first);

        AtomicReference<DataSender> result = new AtomicReference<>();
        Thread waiter = getSenderInThread(pool, result, new AtomicReference<>());
        Thread.sleep(500);
        Assert.assertTrue(waiter.isAlive());

        pool.returnSender(first);
        waiter.join(TIMEOUT);
        Assert.assertFalse(waiter.isAlive());
        Assert.assertSame(first, result.get());
    }


    @Test(timeout = 60000)
    public void testWaitTimesOut() throws Exception {
        Pool pool = createPool(1, 500);
        Assert.assertNotNull(pool.getSender());

        long start = System.nanoTime();
        Assert.assertNull(pool.getSender());
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        Assert.assertTrue("Returned after " + elapsed + "ms", elapsed >= 400);
    }


    @Test(timeout = 60000)
    public void testInterruptEndsWait() throws Exception {
        Pool pool = createPool(1, 0);
        Assert.assertNotNull(pool.getSender());

        AtomicReference<DataSender> result = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        Thread waiter = getSenderInThread(pool, result, interrupted);
        Thread.sleep(500);
        waiter.interrupt();
        waiter.join(TIMEOUT);
        Assert.assertFalse(waiter.isAlive());
        Assert.assertNull(result.get());
        Assert.assertEquals(Boolean.TRUE, interrupted.get());
    }


    @Test(timeout = 60000)
    public void testRaisingLimitEndsWait() throws Exception {
        Pool pool = createPool(1, 0);
        Assert.assertNotNull(pool.getSender());

        AtomicReference<DataSender> result = new AtomicReference<>();
        Thread waiter = getSenderInThread(pool, result, new AtomicReference<>());
        Thread.sleep(500);
        Assert.assertTrue(waiter.isAlive());

        pool.setPoolSize(2);
        waiter.join(TIMEOUT);
        Assert.assertFalse(waiter.isAlive());
        Assert.assertNotNull(result.get());
    }
}
