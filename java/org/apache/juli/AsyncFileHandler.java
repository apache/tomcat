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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.LogRecord;
/**
 * 
 * @author Filip Hanik
 *
 */
public class AsyncFileHandler extends FileHandler {

    public static final int OVERFLOW_DROP_LAST = 1;
    public static final int OVERFLOW_DROP_FIRST = 2;
    public static final int DEFAULT_MAX_RECORDS = 1000;
    public static final int RECORD_BATCH_COUNT = Integer.parseInt(System.getProperty("org.apache.juli.AsyncRecordBatchCount","100"));
    
    protected static ConcurrentLinkedQueue<FileHandler> handlers = new ConcurrentLinkedQueue<FileHandler>();
    protected static SignalAtomicLong recordCounter = new SignalAtomicLong();
    protected static LoggerThread logger = new LoggerThread();
    
    static {
        logger.start();
    }
    
    protected LogQueue<LogRecord> queue = new LogQueue<LogRecord>();
    protected boolean closed = false;
    
    public AsyncFileHandler() {
        this(null,null,null);
    }

    public AsyncFileHandler(String directory, String prefix, String suffix) {
        super(directory, prefix, suffix);
        open();
    }

    @Override
    public void close() {
        closed = true;
        // TODO Auto-generated method stub
        super.close();
        handlers.remove(this);
    }
    
    @Override
    protected void open() {
        closed = false;
        // TODO Auto-generated method stub
        super.open();
        handlers.add(this);
    }
    

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        this.queue.offer(record);
    }
    
    protected void publishInternal(LogRecord record) {
        recordCounter.addAndGet(-1);
        super.publish(record);
    }

    @Override
    protected void finalize() throws Throwable {
        // TODO Auto-generated method stub
        super.finalize();
    }

    public int getMaxRecords() {
        return this.queue.max;
    }

    public void setMaxRecords(int maxRecords) {
        this.queue.max = maxRecords;
    }

    public int getOverflowAction() {
        return this.queue.type;
    }

    public void setOverflowAction(int type) {
        this.queue.type = type;
    }
    
    protected static class SignalAtomicLong {
        AtomicLong delegate = new AtomicLong(0);
        ReentrantLock lock = new ReentrantLock();
        Condition cond = lock.newCondition();
        
        public long addAndGet(long i) {
            long prevValue = delegate.getAndAdd(i);
            if (prevValue<=0 && i>0) {
                lock.lock();
                try {
                    cond.signalAll();
                } finally {
                    lock.unlock();
                }
            }
            return delegate.get();
        }
        
        public void sleepUntilPositive() throws InterruptedException {
            if (delegate.get()>0) return;
            lock.lock();
            try {
                if (delegate.get()>0) return;
                cond.await();
            } finally {
                lock.unlock();
            }
        }
        
        public long get() {
            return delegate.get();
        }
        
    }
    
    protected static class LoggerThread extends Thread {
        protected boolean run = true;
        public LoggerThread() {
            this.setDaemon(true);
            this.setName("AsyncFileHandlerWriter-"+System.identityHashCode(this));
        }
        
        public void run() {
            while (run) {
                try {
                    AsyncFileHandler.recordCounter.sleepUntilPositive();
                } catch (InterruptedException x) {
                    this.interrupted();
                    continue;
                }
                AsyncFileHandler[] handlers = AsyncFileHandler.handlers.toArray(new AsyncFileHandler[0]);
                for (int i=0; run && i<handlers.length; i++) {
                    int counter = 0;
                    while (run && (counter++)<RECORD_BATCH_COUNT) {
                        if (handlers[i].closed) break;
                        LogRecord record = handlers[i].queue.poll();
                        if (record==null) break;
                        handlers[i].publishInternal(record);
                    }//while
                }//for
            }//while
        }
    }
    
    protected static class LogQueue<E> {
        protected int max = DEFAULT_MAX_RECORDS;
        protected int type = OVERFLOW_DROP_LAST;
        protected ConcurrentLinkedQueue<E> delegate = new ConcurrentLinkedQueue<E>(); 
        
        public boolean offer(E e) {
            if (delegate.size()>=max) {
                switch (type) {
                    case OVERFLOW_DROP_LAST:
                        return false;
                    case OVERFLOW_DROP_FIRST: {
                        this.poll();
                        if (delegate.offer(e)) {
                            recordCounter.addAndGet(1);
                            return true;
                        } else {
                            return false;
                        }
                    }
                    default:
                        return false;
                }
            } else {
                if (delegate.offer(e)) {
                    recordCounter.addAndGet(1);
                    return true;
                } else {
                    return false;
                }

            }
        }

        public E peek() {
            return delegate.peek();
        }

        public E poll() {
            // TODO Auto-generated method stub
            return delegate.poll();
        }
        
    }

    
}
