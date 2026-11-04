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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.naming.Binding;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;

import org.junit.Assert;
import org.junit.Test;

/*
 * The bindings of a NamingContext are read by the threads performing lookups while admin
 * threads bind and unbind resources, as NamingContextListener does on a JMX notification.
 * Before the map accesses were synchronized, this regularly led to ConcurrentModification-
 * Exception in list() and listBindings() and could lose updates to the underlying HashMap.
 */
public class TestNamingContextConcurrency {

    @Test
    public void testConcurrentBindUnbindAndListing() throws Exception {
        // A context that is not registered with the ContextAccessController is writable
        NamingContext context = new NamingContext(null, "test");
        // The context returned for the empty name shares the bindings map and its lock
        NamingContext view = (NamingContext) context.lookup("");

        int stableEntries = 200;
        for (int i = 0; i < stableEntries; i++) {
            context.bind("stable" + i, "value" + i);
        }

        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();

        Thread writer = new Thread(() -> {
            int i = 0;
            try {
                while (!stop.get()) {
                    String name = "dynamic" + (i % 50);
                    context.bind(name, "value" + i);
                    context.unbind(name);
                    writes.incrementAndGet();
                    i++;
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        });

        List<Thread> readers = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            readers.add(new Thread(() -> {
                try {
                    while (!stop.get()) {
                        NamingEnumeration<NameClassPair> names = context.list("");
                        int count = 0;
                        while (names.hasMore()) {
                            names.next();
                            count++;
                        }
                        // The stable entries are never removed, so they must all be there
                        Assert.assertTrue("Missing stable entries in list()", count >= stableEntries);
                        NamingEnumeration<Binding> bindings = context.listBindings("");
                        while (bindings.hasMore()) {
                            bindings.next();
                        }
                        view.list("");
                        view.lookup("stable0");
                        context.lookup("stable0");
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }));
        }

        writer.start();
        for (Thread reader : readers) {
            reader.start();
        }
        Thread.sleep(2000);
        stop.set(true);
        writer.join(30000);
        for (Thread reader : readers) {
            reader.join(30000);
        }

        Assert.assertFalse("The writer did not terminate.", writer.isAlive());
        for (Thread reader : readers) {
            Assert.assertFalse("A reader did not terminate.", reader.isAlive());
        }
        Throwable seen = failure.get();
        Assert.assertNull("Concurrent access failed: " + seen, seen);
        Assert.assertTrue("The writer made no progress", writes.get() > 0);

        // The map must still be intact: all stable entries present, no dynamic leftovers
        NamingEnumeration<Binding> remaining = context.listBindings("");
        int stableCount = 0;
        int dynamicCount = 0;
        while (remaining.hasMore()) {
            Binding binding = remaining.next();
            if (binding.getName().startsWith("stable")) {
                stableCount++;
            } else if (binding.getName().startsWith("dynamic")) {
                dynamicCount++;
            }
        }
        Assert.assertEquals(stableEntries, stableCount);
        Assert.assertEquals("Leftover dynamic entries", 0, dynamicCount);
    }
}
