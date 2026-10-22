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
package org.apache.naming;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.naming.Context;

import org.junit.Assert;
import org.junit.Test;

/*
 * The context which the initial context of a container uses is created when it
 * is first needed and is bound to a name built from the name of the object the
 * current thread is bound to. Every thread of the container which needs that
 * context at the same moment may find that no context is bound yet and create
 * one of its own, and only one of those contexts can be bound. Before the
 * context was bound with a single atomic step, the bindings made on the
 * contexts which were not bound were lost, which showed up as names which were
 * bound successfully and were then not bound.
 */
public class TestSelectorContextConcurrency {

    private static final int THREAD_COUNT = 4;

    private static final String CONTAINER_NAME = "container=/concurrency";


    @Test
    public void testConcurrentInitialContextCreation() throws Exception {
        ContextBindings.bindContext(CONTAINER_NAME, new NamingContext(null, CONTAINER_NAME));

        try {
            CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> bindingFailure = new AtomicReference<>();
            List<Thread> binders = new ArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                final String name = "binding" + i;
                Thread binder = new Thread(() -> {
                    try {
                        ContextBindings.bindThread(CONTAINER_NAME, null);
                        ready.countDown();
                        start.await();
                        new SelectorContext(new SlowEnvironment(), true).bind(name, name);
                    } catch (Throwable t) {
                        bindingFailure.compareAndSet(null, t);
                        ready.countDown();
                    } finally {
                        ContextBindings.unbindThread(CONTAINER_NAME, null);
                    }
                });
                binders.add(binder);
                binder.start();
            }

            ready.await();
            start.countDown();
            for (Thread binder : binders) {
                binder.join(30000);
            }

            Assert.assertNull("A thread failed to bind: " + bindingFailure.get(), bindingFailure.get());
            for (Thread binder : binders) {
                Assert.assertFalse("A thread did not terminate.", binder.isAlive());
            }

            // Every thread bound a name on the initial context of the container,
            // so all of them must be visible from another thread which is bound
            // to the same container. The bindings which were made on a context
            // which was not bound are not visible. A thread is needed because a
            // naming context is bound to the thread which uses it.
            AtomicReference<Throwable> lookupFailure = new AtomicReference<>();
            Thread reader = new Thread(() -> {
                try {
                    ContextBindings.bindThread(CONTAINER_NAME, null);
                    Context initialContext = new SelectorContext(new Hashtable<>(), true);
                    for (int i = 0; i < THREAD_COUNT; i++) {
                        String name = "binding" + i;
                        Assert.assertEquals("The initial context does not have the binding of thread " + i, name,
                                initialContext.lookup(name));
                    }
                } catch (Throwable t) {
                    lookupFailure.compareAndSet(null, t);
                } finally {
                    ContextBindings.unbindThread(CONTAINER_NAME, null);
                }
            });
            reader.start();
            reader.join(30000);

            Assert.assertFalse("The reader did not terminate.", reader.isAlive());
            Assert.assertNull("The initial context does not have all the bindings: " + lookupFailure.get(),
                    lookupFailure.get());
        } finally {
            ContextBindings.unbindContext(SelectorContext.IC_PREFIX + CONTAINER_NAME, null);
            ContextBindings.unbindContext(CONTAINER_NAME, null);
        }
    }


    /**
     * An environment which is slow to read, so that the threads are still
     * copying it into the context they created when the first of them binds
     * that context. Without the delay, the moment in which two threads can both
     * find that no context is bound is too short for the test to reach it
     * reliably.
     */
    private static class SlowEnvironment extends Hashtable<String,Object> {

        private static final long serialVersionUID = 1L;

        @Override
        public synchronized Enumeration<String> keys() {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.keys();
        }
    }
}
