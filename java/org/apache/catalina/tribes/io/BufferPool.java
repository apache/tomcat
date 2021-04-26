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
package org.apache.catalina.tribes.io;


import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class BufferPool {
    private static final Log log = LogFactory.getLog(BufferPool.class);

    public static final int DEFAULT_POOL_SIZE =
            Integer.getInteger("org.apache.catalina.tribes.io.BufferPool.DEFAULT_POOL_SIZE", 100*1024*1024).intValue(); //100MB

    protected static final StringManager sm = StringManager.getManager(BufferPool.class);

    protected static volatile BufferPool instance = null;

    public static BufferPool getBufferPool() {
        if (instance == null) {
            synchronized (BufferPool.class) {
                if (instance == null) {
                    BufferPool pool = new BufferPool();
                    pool.setMaxSize(DEFAULT_POOL_SIZE);
                    log.info(sm.getString("bufferPool.created",
                            Integer.toString(DEFAULT_POOL_SIZE),
                            pool.getClass().getName()));
                    instance = pool;
                }
            }
        }
        return instance;
    }

    private BufferPool() {
    }

    public XByteBuffer getBuffer(int minSize, boolean discard) {
        XByteBuffer buffer = queue.poll();
        if ( buffer != null ) size.addAndGet(-buffer.getCapacity());
        if ( buffer == null ) buffer = new XByteBuffer(minSize,discard);
        else if ( buffer.getCapacity() <= minSize ) buffer.expand(minSize);
        buffer.setDiscard(discard);
        buffer.reset();
        return buffer;
    }

    public void returnBuffer(XByteBuffer buffer) {
        if ( (size.get() + buffer.getCapacity()) <= maxSize ) {
            size.addAndGet(buffer.getCapacity());
            queue.offer(buffer);
        }
    }

    public void clear() {
        queue.clear();
        size.set(0);
    }

    protected int maxSize;
    protected final AtomicInteger size = new AtomicInteger(0);
    protected final ConcurrentLinkedQueue<XByteBuffer> queue =
            new ConcurrentLinkedQueue<>();

    public void setMaxSize(int bytes) {
        this.maxSize = bytes;
    }

    public int getMaxSize() {
        return maxSize;
    }

}
