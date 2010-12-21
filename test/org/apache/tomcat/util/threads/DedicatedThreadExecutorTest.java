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
package org.apache.tomcat.util.threads;

import java.util.concurrent.Callable;

import junit.framework.TestCase;

public class DedicatedThreadExecutorTest extends TestCase {
    private Thread dedicatedThread;

    public void testExecute() {
        final Thread testingThread = Thread.currentThread();
        DedicatedThreadExecutor executor = new DedicatedThreadExecutor();
        Long result = executor.execute(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                dedicatedThread = Thread.currentThread();
                DedicatedThreadExecutorTest.assertNotSame(testingThread,
                    dedicatedThread);
                return 123L;
            }
        });
        assertEquals(123, result.longValue());

        //check that the same thread is reused
        executor.execute(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                DedicatedThreadExecutorTest.assertSame(dedicatedThread,
                    Thread.currentThread());
                return null;
            }
        });

        executor.shutdown();
        assertFalse(dedicatedThread.isAlive());
    }

    public void testExecuteInOwnThread() {
        final Thread testingThread = Thread.currentThread();
        Long result =
            DedicatedThreadExecutor.executeInOwnThread(new Callable<Long>() {
                @Override
                public Long call() throws Exception {
                    dedicatedThread = Thread.currentThread();
                    DedicatedThreadExecutorTest.assertNotSame(testingThread,
                        dedicatedThread);
                    return 456L;
                }
            });
        assertEquals(456, result.longValue());
        assertFalse(dedicatedThread.isAlive());
    }

}
