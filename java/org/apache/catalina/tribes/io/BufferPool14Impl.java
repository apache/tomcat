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

import java.util.LinkedList;


/**
 *
 * @author Filip Hanik
 * @version 1.0
 */
class BufferPool14Impl implements BufferPool.BufferPoolAPI {
    protected int maxSize;
    protected int size = 0;
    protected LinkedList queue = new LinkedList();

    public void setMaxSize(int bytes) {
        this.maxSize = bytes;
    }
    
    public synchronized int addAndGet(int val) {
        size = size + (val);
        return size;
    }
    
    

    public synchronized XByteBuffer getBuffer(int minSize, boolean discard) {
        XByteBuffer buffer = (XByteBuffer)(queue.size()>0?queue.remove(0):null);
        if ( buffer != null ) addAndGet(-buffer.getCapacity());
        if ( buffer == null ) buffer = new XByteBuffer(minSize,discard);
        else if ( buffer.getCapacity() <= minSize ) buffer.expand(minSize);
        buffer.setDiscard(discard);
        buffer.reset();
        return buffer;
    }

    public synchronized void returnBuffer(XByteBuffer buffer) {
        if ( (size + buffer.getCapacity()) <= maxSize ) {
            addAndGet(buffer.getCapacity());
            queue.add(buffer);
        }
    }

    public synchronized void clear() {
        queue.clear();
        size = 0;
    }

    public int getMaxSize() {
        return maxSize;
    }

}
