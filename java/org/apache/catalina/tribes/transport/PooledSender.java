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
package org.apache.catalina.tribes.transport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * An abstract pooled sender that manages a pool of {@link DataSender} instances.
 */
public abstract class PooledSender extends AbstractSender implements MultiPointSender {

    private static final Log log = LogFactory.getLog(PooledSender.class);
    /** StringManager for internationalized log messages. */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    private final SenderQueue queue;
    private int poolSize = 25;
    private long maxWait = 3000;

    /**
     * Creates a new PooledSender with the default pool size.
     */
    public PooledSender() {
        queue = new SenderQueue(this, poolSize);
    }

    /**
     * Creates and returns a new DataSender instance for the pool.
     * @return a new DataSender
     */
    public abstract DataSender getNewDataSender();

    /**
     * Retrieves a sender from the pool, waiting up to maxWait milliseconds.
     * @return a DataSender from the pool, or null if timeout expires
     */
    public DataSender getSender() {
        return queue.getSender(getMaxWait());
    }

    /**
     * Returns a sender back to the pool after use.
     * @param sender the sender to return
     */
    public void returnSender(DataSender sender) {
        sender.keepalive();
        queue.returnSender(sender);
    }

    @Override
    public synchronized void connect() throws IOException {
        // do nothing, happens in the socket sender itself
        queue.open();
        setConnected(true);
    }

    @Override
    public synchronized void disconnect() {
        queue.close();
        setConnected(false);
    }


    /**
     * Returns the number of senders currently available in the pool.
     * @return the number of senders in the pool
     */
    public int getInPoolSize() {
        return queue.getInPoolSize();
    }

    /**
     * Returns the number of senders currently in use.
     * @return the number of senders in use
     */
    public int getInUsePoolSize() {
        return queue.getInUsePoolSize();
    }


    /**
     * Sets the maximum number of senders in the pool.
     * @param poolSize the pool size
     */
    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
        queue.setLimit(poolSize);
    }

    /**
     * Returns the maximum number of senders in the pool.
     * @return the pool size
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Returns the maximum time to wait for a sender from the pool, in milliseconds.
     * @return the maximum wait time
     */
    public long getMaxWait() {
        return maxWait;
    }

    /**
     * Sets the maximum time to wait for a sender from the pool, in milliseconds.
     * @param maxWait the maximum wait time
     */
    public void setMaxWait(long maxWait) {
        this.maxWait = maxWait;
    }

    @Override
    public boolean keepalive() {
        // do nothing, the pool checks on every return
        return queue != null && queue.checkIdleKeepAlive();
    }

    @Override
    public void add(Member member) {
        // no op, senders created upon demands
    }

    @Override
    public void remove(Member member) {
        // no op for now, should not cancel out any keys
        // can create serious sync issues
        // all TCP connections are cleared out through keepalive
        // and if remote node disappears
    }
    // ----------------------------------------------------- Inner Class

    private static class SenderQueue {
        private int limit;

        PooledSender parent;

        private final List<DataSender> notinuse;

        private final List<DataSender> inuse;

        private boolean isOpen = true;

        SenderQueue(PooledSender parent, int limit) {
            this.limit = limit;
            this.parent = parent;
            notinuse = new ArrayList<>();
            inuse = new ArrayList<>();
        }

        /**
         * @return Returns the limit.
         */
        public int getLimit() {
            return limit;
        }

        /**
         * @param limit The limit to set.
         */
        public void setLimit(int limit) {
            this.limit = limit;
        }

        public synchronized int getInUsePoolSize() {
            return inuse.size();
        }

        public synchronized int getInPoolSize() {
            return notinuse.size();
        }

        public synchronized boolean checkIdleKeepAlive() {
            DataSender[] list = notinuse.toArray(new DataSender[0]);
            boolean result = false;
            for (DataSender dataSender : list) {
                result = result | dataSender.keepalive();
            }
            return result;
        }

        public synchronized DataSender getSender(long timeout) {
            long start = System.currentTimeMillis();
            while (true) {
                if (!isOpen) {
                    throw new IllegalStateException(sm.getString("pooledSender.closed.queue"));
                }
                DataSender sender = null;
                if (notinuse.isEmpty() && inuse.size() < limit) {
                    sender = parent.getNewDataSender();
                } else if (!notinuse.isEmpty()) {
                    sender = notinuse.removeFirst();
                }
                if (sender != null) {
                    inuse.add(sender);
                    return sender;
                }
                long delta = System.currentTimeMillis() - start;
                if (delta > timeout && timeout > 0) {
                    return null;
                } else {
                    try {
                        wait(Math.max(timeout - delta, 1));
                    } catch (InterruptedException x) {
                        // Ignore
                    }
                }
            }
        }

        public synchronized void returnSender(DataSender sender) {
            if (!isOpen) {
                sender.disconnect();
                return;
            }
            // to do
            inuse.remove(sender);
            // just in case the limit has changed
            if (notinuse.size() < this.getLimit()) {
                notinuse.add(sender);
            } else {
                try {
                    sender.disconnect();
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("PooledSender.senderDisconnectFail"), e);
                    }
                }
            }
            notifyAll();
        }

        public synchronized void close() {
            isOpen = false;
            Object[] unused = notinuse.toArray();
            Object[] used = inuse.toArray();
            for (Object value : unused) {
                DataSender sender = (DataSender) value;
                sender.disconnect();
            }
            for (Object o : used) {
                DataSender sender = (DataSender) o;
                sender.disconnect();
            }
            notinuse.clear();
            inuse.clear();
            notifyAll();


        }

        public synchronized void open() {
            isOpen = true;
            notifyAll();
        }
    }
}