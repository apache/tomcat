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
package org.apache.tomcat.util.net;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.threads.VirtualThreadExecutor;

public class TestAbstractEndpoint {

    @Test
    public void testUtilityExecutorIsCreatedOnce() throws Exception {
        NioEndpoint endpoint = new NioEndpoint();
        try {
            ScheduledExecutorService executor = endpoint.getUtilityExecutor();
            Assert.assertNotNull(executor);
            Assert.assertSame(executor, endpoint.getUtilityExecutor());
        } finally {
            endpoint.destroy();
        }
    }

    @Test
    public void testCreatedUtilityExecutorIsShutDownOnDestroy() throws Exception {
        NioEndpoint endpoint = new NioEndpoint();
        ScheduledExecutorService executor = endpoint.getUtilityExecutor();
        Assert.assertFalse(executor.isShutdown());

        endpoint.destroy();

        Assert.assertTrue(executor.isShutdown());
        // A later request must not resurrect an unmanaged pool
        Assert.assertSame(executor, endpoint.getUtilityExecutor());
    }

    @Test
    public void testExternalUtilityExecutorIsNotShutDownOnDestroy() throws Exception {
        NioEndpoint endpoint = new NioEndpoint();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        endpoint.setUtilityExecutor(executor);
        try {
            Assert.assertSame(executor, endpoint.getUtilityExecutor());

            endpoint.destroy();

            // Externally provided executors are owned by the caller
            Assert.assertFalse(executor.isShutdown());
        } finally {
            executor.shutdown();
        }
    }

    /*
     * The virtual thread executor accepts tasks until it is shut down. A reference captured before the endpoint
     * stopped must be rejected, as it is when the executor is a thread pool.
     */
    @Test
    public void testCreatedVirtualThreadExecutorIsShutDownOnStop() throws Exception {
        NioEndpoint endpoint = new NioEndpoint();
        endpoint.setUseVirtualThreads(true);
        endpoint.createExecutor();
        Executor executor = endpoint.getExecutor();
        Assert.assertTrue(executor instanceof VirtualThreadExecutor);

        endpoint.shutdownExecutor();

        Assert.assertTrue(((ExecutorService) executor).isShutdown());
        try {
            executor.execute(() -> {
            });
            Assert.fail("Expected RejectedExecutionException");
        } catch (RejectedExecutionException e) {
            // Expected.
        }
    }

    @Test
    public void testExternalVirtualThreadExecutorIsNotShutDownOnStop() throws Exception {
        NioEndpoint endpoint = new NioEndpoint();
        VirtualThreadExecutor executor = new VirtualThreadExecutor("test-external-");
        endpoint.setExecutor(executor);
        try {
            endpoint.shutdownExecutor();

            // Externally provided executors are owned by the caller
            Assert.assertFalse(executor.isShutdown());
        } finally {
            executor.shutdownNow();
        }
    }
}
