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
package org.apache.juli;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;

public class TestThreadNameCache {
    private Integer threadId;

    @Test
    public void testCache() throws Exception {
        final String THREAD_NAME = "t-TestThreadNameCache";
        final CountDownLatch threadIdLatch = new CountDownLatch(1);
        final CountDownLatch cacheLatch = new CountDownLatch(1);

        OneLineFormatter olf = new OneLineFormatter();
        Method getThreadName = olf.getClass().getDeclaredMethod("getThreadName", int.class);
        getThreadName.setAccessible(true);
        Thread thread = new Thread() {
            @Override
            public void run() {
                setName(THREAD_NAME);
                threadId = Integer.valueOf((int) getId());
                threadIdLatch.countDown();
                try {
                    cacheLatch.await();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        };

        thread.start();
        threadIdLatch.await();
        Object name = getThreadName.invoke(olf, threadId);
        cacheLatch.countDown();
        Assert.assertEquals(THREAD_NAME, name);

        thread.join();
        name = getThreadName.invoke(olf, threadId);
        Assert.assertEquals(THREAD_NAME, name);
    }
}
