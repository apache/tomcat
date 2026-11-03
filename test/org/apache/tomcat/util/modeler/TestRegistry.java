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
package org.apache.tomcat.util.modeler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public class TestRegistry {

    private static final String PACKAGE = TestRegistry.class.getPackageName();
    private static final String TYPE = TesterBean.class.getName();
    private static final byte[] XML = ("<mbeans-descriptors><mbean name=\"ConcurrentBean\" type=\"" + TYPE +
            "\" description=\"XML descriptor\"/></mbeans-descriptors>").getBytes(StandardCharsets.UTF_8);

    public static class TesterBean {
        public String getValue() {
            return "value";
        }
    }

    @Test
    public void testConcurrentDescriptorLoadWaitsForCompletion() throws Exception {
        doTestConcurrentDescriptorLoad(false);
    }

    @Test
    public void testFindManagedBeanWaitsForDescriptorLoad() throws Exception {
        doTestConcurrentDescriptorLoad(true);
    }

    private void doTestConcurrentDescriptorLoad(boolean lookup) throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch allowRead = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        URL url = new URL(null, "memory:/mbeans-descriptors.xml", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL resource) {
                return new URLConnection(resource) {
                    @Override
                    public void connect() {
                        // NO-OP
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        loads.incrementAndGet();
                        reading.countDown();
                        try {
                            if (!allowRead.await(10, TimeUnit.SECONDS)) {
                                throw new IOException("Timed out waiting to read descriptors");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException(e);
                        }
                        return new ByteArrayInputStream(XML);
                    }
                };
            }
        });
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public URL getResource(String name) {
                if ((PACKAGE.replace('.', '/') + "/mbeans-descriptors.xml").equals(name)) {
                    return url;
                }
                return super.getResource(name);
            }
        };
        Registry registry = new Registry() {
            @Override
            public void loadDescriptors(String packageName, ClassLoader classLoader) {
                super.loadDescriptors(packageName, PACKAGE.equals(packageName) ? loader : classLoader);
            }
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ManagedBean> first = executor.submit(() -> {
                registry.loadDescriptors(PACKAGE, loader);
                return registry.findManagedBean(TYPE);
            });
            Assert.assertTrue("Descriptor loading did not start", reading.await(10, TimeUnit.SECONDS));

            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<ManagedBean> second = executor.submit(() -> {
                secondStarted.countDown();
                if (lookup) {
                    return registry.findManagedBean(null, TesterBean.class, TYPE);
                }
                registry.loadDescriptors(PACKAGE, loader);
                return registry.findManagedBean(TYPE);
            });
            Assert.assertTrue("Second caller did not start", secondStarted.await(10, TimeUnit.SECONDS));
            // Neither entry point may return or fall back to introspection before the XML has been read.
            Assert.assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));

            allowRead.countDown();
            ManagedBean descriptor = first.get(10, TimeUnit.SECONDS);
            Assert.assertNotNull(descriptor);
            Assert.assertEquals("XML descriptor", descriptor.getDescription());
            Assert.assertSame(descriptor, second.get(10, TimeUnit.SECONDS));
            Assert.assertEquals("The descriptor file should only be loaded once", 1, loads.get());
        } finally {
            allowRead.countDown();
            executor.shutdownNow();
            Assert.assertTrue("Descriptor loading threads did not stop", executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
