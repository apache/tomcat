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
package org.apache.catalina.core;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class TestExecutorAwaitTermination {

    @Test
    public void testThreadExecutorAwaitTerminationNotStarted() throws Exception {
        try (StandardThreadExecutor executor = new StandardThreadExecutor()) {
            try {
                executor.awaitTermination(0, TimeUnit.MILLISECONDS);
                Assert.fail("Expected an IllegalStateException before the executor is started");
            } catch (IllegalStateException e) {
                // Expected
            }
        }
    }


    @Test
    public void testThreadExecutorAwaitTerminationDelegates() throws Exception {
        try (StandardThreadExecutor executor = new StandardThreadExecutor()) {
            executor.start();
            try {
                // The pool is running so termination has not been reached
                Assert.assertFalse(executor.awaitTermination(0, TimeUnit.MILLISECONDS));
            } finally {
                executor.stop();
            }
        }
    }


    @Test
    public void testVirtualThreadExecutorAwaitTerminationNotStarted() throws Exception {
        try (StandardThreadExecutor executor = new StandardThreadExecutor()) {
            try {
                executor.awaitTermination(0, TimeUnit.MILLISECONDS);
                Assert.fail("Expected an IllegalStateException before the executor is started");
            } catch (IllegalStateException e) {
                // Expected
            }
        }
    }


    @Test
    public void testVirtualThreadExecutorAwaitTerminationDelegates() throws Exception {
        try (StandardThreadExecutor executor = new StandardThreadExecutor()) {
            executor.start();
            try {
                // The executor has not been shut down so termination has not been
                // reached; the call must delegate rather than block indefinitely
                Assert.assertFalse(executor.awaitTermination(0, TimeUnit.MILLISECONDS));
            } finally {
                executor.stop();
            }
        }
    }
}
