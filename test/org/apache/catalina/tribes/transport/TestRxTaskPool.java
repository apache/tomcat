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

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.transport.nio.NioReceiver;
import org.apache.catalina.tribes.transport.nio.NioReplicationTask;

public class TestRxTaskPool {

    private static final long TIMEOUT = 10000;


    private static class NoOpTask extends AbstractRxTask {
        NoOpTask() {
            super(null);
        }

        @Override
        public void run() {
            // NO-OP
        }
    }


    /*
     * A thread parked in getRxTask() because the pool is exhausted must be woken, and told there is no task, when the
     * pool is stopped.
     */
    @Test(timeout = 60000)
    public void testStopWakesWaitingThread() throws Exception {
        RxTaskPool pool = new RxTaskPool(1, 1, NoOpTask::new);
        Assert.assertNotNull(pool.getRxTask());

        AtomicReference<AbstractRxTask> result = new AtomicReference<>(new NoOpTask());
        Thread waiter = new Thread(() -> result.set(pool.getRxTask()));
        waiter.start();
        // Give the waiter time to park
        Thread.sleep(500);
        Assert.assertTrue(waiter.isAlive());

        pool.stop();
        waiter.join(TIMEOUT);
        Assert.assertFalse("getRxTask() was not woken by stop()", waiter.isAlive());
        Assert.assertNull(result.get());
    }


    /*
     * Stopping the receiver must stop its task pool before waiting for the listener thread, since that thread may be
     * parked in getRxTask() when the pool is exhausted.
     */
    @Test(timeout = 60000)
    public void testReceiverStopWakesWaitingThread() throws Exception {
        NioReceiver receiver = new NioReceiver();
        RxTaskPool pool = new RxTaskPool(1, 1, NoOpTask::new);
        receiver.setPool(pool);
        Assert.assertNotNull(pool.getRxTask());

        AtomicReference<AbstractRxTask> result = new AtomicReference<>(new NoOpTask());
        Thread waiter = new Thread(() -> result.set(pool.getRxTask()));
        waiter.start();
        Thread.sleep(500);
        Assert.assertTrue(waiter.isAlive());

        receiver.stop();
        waiter.join(TIMEOUT);
        Assert.assertFalse("receiver stop did not wake getRxTask()", waiter.isAlive());
        Assert.assertNull(result.get());
    }


    /*
     * A worker that started to return before stop() acquired the pool monitor must still observe that the pool was
     * stopped and must not be added back to its idle list.
     */
    @Test(timeout = 60000)
    public void testWorkerIsNotReturnedAfterStop() throws Exception {
        RxTaskPool pool = new RxTaskPool(1, 1, NoOpTask::new);
        AbstractRxTask worker = pool.getRxTask();
        Thread returner = new Thread(() -> pool.returnWorker(worker));

        synchronized (pool.mutex) {
            returner.start();
            long deadline = System.currentTimeMillis() + TIMEOUT;
            while (returner.getState() != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
                Thread.yield();
            }
            Assert.assertEquals(Thread.State.BLOCKED, returner.getState());
            pool.stop();
        }

        returner.join(TIMEOUT);
        Assert.assertFalse(returner.isAlive());
        Assert.assertEquals(0, pool.available());
    }


    /*
     * An interrupted waiter must give up, with its interrupt status restored, rather than spin.
     */
    @Test(timeout = 60000)
    public void testInterruptEndsWait() throws Exception {
        RxTaskPool pool = new RxTaskPool(1, 1, NoOpTask::new);
        Assert.assertNotNull(pool.getRxTask());

        AtomicReference<AbstractRxTask> result = new AtomicReference<>(new NoOpTask());
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            result.set(pool.getRxTask());
            interrupted.set(Boolean.valueOf(Thread.currentThread().isInterrupted()));
        });
        waiter.start();
        Thread.sleep(500);
        waiter.interrupt();
        waiter.join(TIMEOUT);
        Assert.assertFalse("getRxTask() ignored the interrupt", waiter.isAlive());
        Assert.assertNull(result.get());
        Assert.assertEquals(Boolean.TRUE, interrupted.get());
    }


    /*
     * A worker that is run without a key must still be returned to the pool.
     */
    @Test(timeout = 60000)
    public void testWorkerWithoutKeyIsReturned() throws Exception {
        NioReceiver receiver = new NioReceiver();
        receiver.setChannel(new GroupChannel());
        RxTaskPool pool = new RxTaskPool(1, 1, receiver::createRxTask);
        NioReplicationTask task = (NioReplicationTask) pool.getRxTask();
        Assert.assertNotNull(task);
        Assert.assertEquals(0, pool.available());

        task.run();

        Assert.assertEquals(1, pool.available());
        pool.stop();
    }
}
