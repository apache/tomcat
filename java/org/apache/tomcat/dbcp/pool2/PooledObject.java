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
package org.apache.tomcat.dbcp.pool2;

import java.io.PrintWriter;
import java.util.Deque;

/**
 * Defines the wrapper that is used to track the additional information, such as
 * state, for the pooled objects.
 * <p>
 * Implementations of this class are required to be thread-safe.
 *
 * @param <T> the type of object in the pool
 *
 * @since 2.0
 */
public interface PooledObject<T> extends Comparable<PooledObject<T>> {

    /**
     * Obtains the underlying object that is wrapped by this instance of
     * {@link PooledObject}.
     *
     * @return The wrapped object
     */
    T getObject();

    /**
     * Obtains the time (using the same basis as
     * {@link System#currentTimeMillis()}) that this object was created.
     *
     * @return The creation time for the wrapped object
     */
    long getCreateTime();

    /**
     * Obtains the time in milliseconds that this object last spent in the
     * active state (it may still be active in which case subsequent calls will
     * return an increased value).
     *
     * @return The time in milliseconds last spent in the active state
     */
    long getActiveTimeMillis();

    /**
     * Gets the number of times this object has been borrowed.
     *
     * @return The number of times this object has been borrowed.
     * @since 2.7.0
     */
    long getBorrowedCount();

    /**
     * Obtains the time in milliseconds that this object last spend in the
     * idle state (it may still be idle in which case subsequent calls will
     * return an increased value).
     *
     * @return The time in milliseconds last spent in the idle state
     */
    long getIdleTimeMillis();

    /**
     * Obtains the time the wrapped object was last borrowed.
     *
     * @return The time the object was last borrowed
     */
    long getLastBorrowTime();

    /**
     * Obtains the time the wrapped object was last returned.
     *
     * @return The time the object was last returned
     */
    long getLastReturnTime();

    /**
     * Returns an estimate of the last time this object was used.  If the class
     * of the pooled object implements {@link TrackedUse}, what is returned is
     * the maximum of {@link TrackedUse#getLastUsed()} and
     * {@link #getLastBorrowTime()}; otherwise this method gives the same
     * value as {@link #getLastBorrowTime()}.
     *
     * @return the last time this object was used
     */
    long getLastUsedTime();

    /**
     * Orders instances based on idle time - i.e. the length of time since the
     * instance was returned to the pool. Used by the GKOP idle object evictor.
     *<p>
     * Note: This class has a natural ordering that is inconsistent with
     *       equals if distinct objects have the same identity hash code.
     * </p>
     * <p>
     * {@inheritDoc}
     * </p>
     */
    @Override
    int compareTo(PooledObject<T> other);

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    /**
     * Provides a String form of the wrapper for debug purposes. The format is
     * not fixed and may change at any time.
     * <p>
     * {@inheritDoc}
     */
    @Override
    String toString();

    /**
     * Attempts to place the pooled object in the
     * {@link PooledObjectState#EVICTION} state.
     *
     * @return <code>true</code> if the object was placed in the
     *         {@link PooledObjectState#EVICTION} state otherwise
     *         <code>false</code>
     */
    boolean startEvictionTest();

    /**
     * Called to inform the object that the eviction test has ended.
     *
     * @param idleQueue The queue of idle objects to which the object should be
     *                  returned
     *
     * @return  Currently not used
     */
    boolean endEvictionTest(Deque<PooledObject<T>> idleQueue);

    /**
     * Allocates the object.
     *
     * @return {@code true} if the original state was {@link PooledObjectState#IDLE IDLE}
     */
    boolean allocate();

    /**
     * Deallocates the object and sets it {@link PooledObjectState#IDLE IDLE}
     * if it is currently {@link PooledObjectState#ALLOCATED ALLOCATED}.
     *
     * @return {@code true} if the state was {@link PooledObjectState#ALLOCATED ALLOCATED}
     */
    boolean deallocate();

    /**
     * Sets the state to {@link PooledObjectState#INVALID INVALID}
     */
    void invalidate();

    /**
     * Is abandoned object tracking being used? If this is true the
     * implementation will need to record the stack trace of the last caller to
     * borrow this object.
     *
     * @param   logAbandoned    The new configuration setting for abandoned
     *                          object tracking
     */
    void setLogAbandoned(boolean logAbandoned);

    /**
     * Configures the stack trace generation strategy based on whether or not fully detailed stack traces are required.
     * When set to false, abandoned logs may only include caller class information rather than method names, line
     * numbers, and other normal metadata available in a full stack trace.
     *
     * @param requireFullStackTrace the new configuration setting for abandoned object logging
     * @since 2.7.0
     */
    void setRequireFullStackTrace(boolean requireFullStackTrace);

    /**
     * Record the current stack trace as the last time the object was used.
     */
    void use();

    /**
     * Prints the stack trace of the code that borrowed this pooled object and
     * the stack trace of the last code to use this object (if available) to
     * the supplied writer.
     *
     * @param   writer  The destination for the debug output
     */
    void printStackTrace(PrintWriter writer);

    /**
     * Returns the state of this object.
     * @return state
     */
    PooledObjectState getState();

    /**
     * Marks the pooled object as abandoned.
     */
    void markAbandoned();

    /**
     * Marks the object as returning to the pool.
     */
    void markReturning();
}
