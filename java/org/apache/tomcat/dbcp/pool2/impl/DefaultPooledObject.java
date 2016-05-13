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
package org.apache.tomcat.dbcp.pool2.impl;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Deque;

import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectState;
import org.apache.tomcat.dbcp.pool2.TrackedUse;

/**
 * This wrapper is used to track the additional information, such as state, for
 * the pooled objects.
 * <p>
 * This class is intended to be thread-safe.
 *
 * @param <T> the type of object in the pool
 *
 * @since 2.0
 */
public class DefaultPooledObject<T> implements PooledObject<T> {

    private final T object;
    private PooledObjectState state = PooledObjectState.IDLE; // @GuardedBy("this") to ensure transitions are valid
    private final long createTime = System.currentTimeMillis();
    private volatile long lastBorrowTime = createTime;
    private volatile long lastUseTime = createTime;
    private volatile long lastReturnTime = createTime;
    private volatile boolean logAbandoned = false;
    private volatile Exception borrowedBy = null;
    private volatile Exception usedBy = null;
    private volatile long borrowedCount = 0;

    /**
     * Create a new instance that wraps the provided object so that the pool can
     * track the state of the pooled object.
     *
     * @param object The object to wrap
     */
    public DefaultPooledObject(final T object) {
        this.object = object;
    }

    @Override
    public T getObject() {
        return object;
    }

    @Override
    public long getCreateTime() {
        return createTime;
    }

    @Override
    public long getActiveTimeMillis() {
        // Take copies to avoid threading issues
        final long rTime = lastReturnTime;
        final long bTime = lastBorrowTime;

        if (rTime > bTime) {
            return rTime - bTime;
        }
        return System.currentTimeMillis() - bTime;
    }

    @Override
    public long getIdleTimeMillis() {
        final long elapsed = System.currentTimeMillis() - lastReturnTime;
     // elapsed may be negative if:
     // - another thread updates lastReturnTime during the calculation window
     // - System.currentTimeMillis() is not monotonic (e.g. system time is set back)
     return elapsed >= 0 ? elapsed : 0;
    }

    @Override
    public long getLastBorrowTime() {
        return lastBorrowTime;
    }

    @Override
    public long getLastReturnTime() {
        return lastReturnTime;
    }

    /**
     * Get the number of times this object has been borrowed.
     * @return The number of times this object has been borrowed.
     * @since 2.1
     */
    public long getBorrowedCount() {
        return borrowedCount;
    }

    /**
     * Return an estimate of the last time this object was used.  If the class
     * of the pooled object implements {@link TrackedUse}, what is returned is
     * the maximum of {@link TrackedUse#getLastUsed()} and
     * {@link #getLastBorrowTime()}; otherwise this method gives the same
     * value as {@link #getLastBorrowTime()}.
     *
     * @return the last time this object was used
     */
    @Override
    public long getLastUsedTime() {
        if (object instanceof TrackedUse) {
            return Math.max(((TrackedUse) object).getLastUsed(), lastUseTime);
        }
        return lastUseTime;
    }

    @Override
    public int compareTo(final PooledObject<T> other) {
        final long lastActiveDiff = this.getLastReturnTime() - other.getLastReturnTime();
        if (lastActiveDiff == 0) {
            // Make sure the natural ordering is broadly consistent with equals
            // although this will break down if distinct objects have the same
            // identity hash code.
            // see java.lang.Comparable Javadocs
            return System.identityHashCode(this) - System.identityHashCode(other);
        }
        // handle int overflow
        return (int)Math.min(Math.max(lastActiveDiff, Integer.MIN_VALUE), Integer.MAX_VALUE);
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("Object: ");
        result.append(object.toString());
        result.append(", State: ");
        synchronized (this) {
            result.append(state.toString());
        }
        return result.toString();
        // TODO add other attributes
    }

    @Override
    public synchronized boolean startEvictionTest() {
        if (state == PooledObjectState.IDLE) {
            state = PooledObjectState.EVICTION;
            return true;
        }

        return false;
    }

    @Override
    public synchronized boolean endEvictionTest(
            final Deque<PooledObject<T>> idleQueue) {
        if (state == PooledObjectState.EVICTION) {
            state = PooledObjectState.IDLE;
            return true;
        } else if (state == PooledObjectState.EVICTION_RETURN_TO_HEAD) {
            state = PooledObjectState.IDLE;
            if (!idleQueue.offerFirst(this)) {
                // TODO - Should never happen
            }
        }

        return false;
    }

    /**
     * Allocates the object.
     *
     * @return {@code true} if the original state was {@link PooledObjectState#IDLE IDLE}
     */
    @Override
    public synchronized boolean allocate() {
        if (state == PooledObjectState.IDLE) {
            state = PooledObjectState.ALLOCATED;
            lastBorrowTime = System.currentTimeMillis();
            lastUseTime = lastBorrowTime;
            borrowedCount++;
            if (logAbandoned) {
                borrowedBy = new AbandonedObjectCreatedException();
            }
            return true;
        } else if (state == PooledObjectState.EVICTION) {
            // TODO Allocate anyway and ignore eviction test
            state = PooledObjectState.EVICTION_RETURN_TO_HEAD;
            return false;
        }
        // TODO if validating and testOnBorrow == true then pre-allocate for
        // performance
        return false;
    }

    /**
     * Deallocates the object and sets it {@link PooledObjectState#IDLE IDLE}
     * if it is currently {@link PooledObjectState#ALLOCATED ALLOCATED}.
     *
     * @return {@code true} if the state was {@link PooledObjectState#ALLOCATED ALLOCATED}
     */
    @Override
    public synchronized boolean deallocate() {
        if (state == PooledObjectState.ALLOCATED ||
                state == PooledObjectState.RETURNING) {
            state = PooledObjectState.IDLE;
            lastReturnTime = System.currentTimeMillis();
            borrowedBy = null;
            return true;
        }

        return false;
    }

    /**
     * Sets the state to {@link PooledObjectState#INVALID INVALID}
     */
    @Override
    public synchronized void invalidate() {
        state = PooledObjectState.INVALID;
    }

    @Override
    public void use() {
        lastUseTime = System.currentTimeMillis();
        usedBy = new Exception("The last code to use this object was:");
    }

    @Override
    public void printStackTrace(final PrintWriter writer) {
        boolean written = false;
        final Exception borrowedByCopy = this.borrowedBy;
        if (borrowedByCopy != null) {
            borrowedByCopy.printStackTrace(writer);
            written = true;
        }
        final Exception usedByCopy = this.usedBy;
        if (usedByCopy != null) {
            usedByCopy.printStackTrace(writer);
            written = true;
        }
        if (written) {
            writer.flush();
        }
    }

    /**
     * Returns the state of this object.
     * @return state
     */
    @Override
    public synchronized PooledObjectState getState() {
        return state;
    }

    /**
     * Marks the pooled object as abandoned.
     */
    @Override
    public synchronized void markAbandoned() {
        state = PooledObjectState.ABANDONED;
    }

    /**
     * Marks the object as returning to the pool.
     */
    @Override
    public synchronized void markReturning() {
        state = PooledObjectState.RETURNING;
    }

    @Override
    public void setLogAbandoned(final boolean logAbandoned) {
        this.logAbandoned = logAbandoned;
    }

    /**
     * Used to track how an object was obtained from the pool (the stack trace
     * of the exception will show which code borrowed the object) and when the
     * object was borrowed.
     */
    static class AbandonedObjectCreatedException extends Exception {

        private static final long serialVersionUID = 7398692158058772916L;

        /** Date format */
        //@GuardedBy("format")
        private static final SimpleDateFormat format = new SimpleDateFormat
            ("'Pooled object created' yyyy-MM-dd HH:mm:ss Z " +
             "'by the following code has not been returned to the pool:'");

        private final long _createdTime;

        /**
         * Create a new instance.
         * <p>
         * @see Exception#Exception()
         */
        public AbandonedObjectCreatedException() {
            super();
            _createdTime = System.currentTimeMillis();
        }

        // Override getMessage to avoid creating objects and formatting
        // dates unless the log message will actually be used.
        @Override
        public String getMessage() {
            String msg;
            synchronized(format) {
                msg = format.format(new Date(_createdTime));
            }
            return msg;
        }
    }
}
