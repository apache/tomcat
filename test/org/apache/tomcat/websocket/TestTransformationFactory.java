/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.websocket.Extension.Parameter;

import org.junit.Assert;
import org.junit.Test;

public class TestTransformationFactory {

    /* A single extension offer with no parameters (i.e. use the defaults). */
    private static final List<List<Parameter>> DEFAULT_OFFER = Collections.singletonList(Collections.emptyList());

    @Test
    public void testBuiltInExtensionIsAvailable() {
        TransformationFactory factory = TransformationFactory.getInstance();
        Assert.assertTrue(factory.getInstalledExtensionNames().contains(PerMessageDeflate.NAME));
        Assert.assertNotNull(factory.create(PerMessageDeflate.NAME, DEFAULT_OFFER, true));
    }


    @Test
    public void testCreateUnknownExtensionReturnsNull() {
        Assert.assertNull(
                TransformationFactory.getInstance().create("test-unknown-extension-" + System.nanoTime(), DEFAULT_OFFER,
                        true));
    }


    /*
     * registerExtension() is public API that may be called at any time, while create() and the getInstalled*() methods
     * are called concurrently by threads processing handshakes. Concurrent registration and reads must neither throw
     * nor lose entries.
     */
    @Test
    public void testConcurrentRegistrationAndReads() throws Exception {
        TransformationFactory factory = TransformationFactory.getInstance();

        int writerCount = 4;
        int readerCount = 4;
        int extensionsPerWriter = 50;
        int readerIterations = 2000;

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(writerCount + readerCount);

        String prefix = "test-ext-" + System.nanoTime() + "-";

        List<String> expected = new ArrayList<>();
        for (int w = 0; w < writerCount; w++) {
            for (int i = 0; i < extensionsPerWriter; i++) {
                expected.add(prefix + w + '-' + i);
            }
        }

        for (int w = 0; w < writerCount; w++) {
            final int writer = w;
            executor.execute(() -> {
                try {
                    startLine.await();
                    for (int i = 0; i < extensionsPerWriter; i++) {
                        String name = prefix + writer + '-' + i;
                        factory.registerExtension(name, PerMessageDeflate.BUILDER);
                    }
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
        }

        for (int r = 0; r < readerCount; r++) {
            executor.execute(() -> {
                try {
                    startLine.await();
                    for (int i = 0; i < readerIterations; i++) {
                        // Exercise each read path used by the handshake processing code.
                        factory.create(PerMessageDeflate.NAME, DEFAULT_OFFER, true);
                        factory.getInstalledExtensionNames();
                        factory.getInstalledExtensions();
                    }
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
        }

        startLine.countDown();
        executor.shutdown();
        Assert.assertTrue("Concurrent test did not complete in time",
                executor.awaitTermination(60, TimeUnit.SECONDS));

        Assert.assertTrue("Unexpected failures during concurrent access: " + failures, failures.isEmpty());

        // Every registered extension must be visible once registration has completed.
        for (String name : expected) {
            Assert.assertTrue("Registered extension not visible: " + name,
                    factory.getInstalledExtensionNames().contains(name));
            Assert.assertNotNull("Create failed for registered extension: " + name,
                    factory.create(name, DEFAULT_OFFER, true));
        }
    }
}
