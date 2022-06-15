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
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;

/**
 * Defines the wrapper that is used to track the additional information, such as
 * state, for the pooled objects.
 * <p>
 * Implementations of this class are required to be thread-safe.
 * </p>
 *
 * @param <T> the type of object in the pool
 *
 * @since 2.0
 */
public interface PooledObject<T> extends Comparable<PooledObject<T>> {

    /**
     * Allocates the object.
     *
     * @return {@code true} if the original state was {@link PooledObjectState#IDLE IDLE}
     */
    boolean allocate();

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

    /**
     * Deallocates the object and sets it {@link PooledObjectState#IDLE IDLE}
     * if it is currently {@link PooledObjectState#ALLOCATED ALLOCATED}.
     *
     * @return {@code true} if the state was {@link PooledObjectState#ALLOCATED ALLOCATED}.
     */
    boolean deallocate();

    /**
     * Notifies the object that the eviction test has ended.
     *
     * @param idleQueue The queue of idle objects to which the object should be
     *                  returned.
     *
     * @return  Currently not used.
     */
    boolean endEvictionTest(Deque<PooledObject<T>> idleQueue);

    @Override
    boolean equals(Object obj);

    /**
     * Gets the amount of time this object last spent in the active state (it may still be active in which case
     * subsequent calls will return an increased value).
     *
     * @return The duration last spent in the active state.
     * @since 2.11.0
     */
    default Duration getActiveDuration() {
        // Take copies to avoid threading issues
        final Instant lastReturnInstant = getLastReturnInstant();
        final Instant lastBorrowInstant = getLastBorrowInstant();
        // @formatter:off
        return lastReturnInstant.isAfter(lastBorrowInstant) ?
                Duration.between(lastBorrowInstant, lastReturnInstant) :
                Duration.between(lastBorrowInstant, Instant.now());
        // @formatter:on
    }

    /**
     * Gets the amount of time this object last spent in the active state (it may still be active in which case
     * subsequent calls will return an increased value).
     *
     * @return The duration last spent in the active state.
     * @since 2.10.0
     * @deprecated Use {@link #getActiveDuration()}.
     */
    @Deprecated
    default Duration getActiveTime() {
        return getActiveDuration();
    }

    /**
     * Gets the amount of time in milliseconds this object last spent in the
     * active state (it may still be active in which case subsequent calls will
     * return an increased value).
     *
     * @return The time in milliseconds last spent in the active state.
     * @deprecated Use {@link #getActiveTime()} which offers the best precision.
     */
    @Deprecated
    long getActiveTimeMillis();

    /**
     * Gets the number of times this object has been borrowed.
     *
     * @return -1 by default for implementations prior to release 2.7.0.
     * @since 2.7.0
     */
    default long getBorrowedCount() {
        return -1;
    }

    /**
     * Gets the time (using the same basis as {@link Instant#now()}) that this object was created.
     *
     * @return The creation time for the wrapped object.
     * @since 2.11.0
     */
    default Instant getCreateInstant() {
        return Instant.ofEpochMilli(getCreateTime());
    }

    /**
     * Gets the time (using the same basis as
     * {@link System#currentTimeMillis()}) that this object was created.
     *
     * @return The creation time for the wrapped object.
     * @deprecated Use {@link #getCreateInstant()} which offers the best precision.
     */
    @Deprecated
    long getCreateTime();

    /**
     * Computes the duration since this object was created (using {@link Instant#now()}).
     *
     * @return The duration since this object was created.
     * @since 2.12.0
     */
    default Duration getFullDuration() {
        return Duration.between(getCreateInstant(), Instant.now());
    }

    /**
     * Gets the amount of time that this object last spend in the
     * idle state (it may still be idle in which case subsequent calls will
     * return an increased value).
     *
     * @return The amount of time in last spent in the idle state.
     * @since 2.11.0
     */
    default Duration getIdleDuration() {
        return Duration.ofMillis(getIdleTimeMillis());
    }

    /**
     * Gets the amount of time that this object last spend in the
     * idle state (it may still be idle in which case subsequent calls will
     * return an increased value).
     *
     * @return The amount of time in last spent in the idle state.
     * @since 2.10.0
     * @deprecated Use {@link #getIdleDuration()}.
     */
    @Deprecated
    default Duration getIdleTime() {
        return Duration.ofMillis(getIdleTimeMillis());
    }

    /**
     * Gets the amount of time in milliseconds that this object last spend in the
     * idle state (it may still be idle in which case subsequent calls will
     * return an increased value).
     *
     * @return The time in milliseconds last spent in the idle state.
     * @deprecated Use {@link #getIdleTime()} which offers the best precision.
     */
    @Deprecated
    long getIdleTimeMillis();

    /**
     * Gets the time the wrapped object was last borrowed.
     *
     * @return The time the object was last borrowed.
     * @since 2.11.0
     */
    default Instant getLastBorrowInstant() {
        return Instant.ofEpochMilli(getLastBorrowTime());
    }

    /**
     * Gets the time the wrapped object was last borrowed.
     *
     * @return The time the object was last borrowed.
     * @deprecated Use {@link #getLastBorrowInstant()} which offers the best precision.
     */
    @Deprecated
    long getLastBorrowTime();

    /**
     * Gets the time the wrapped object was last borrowed.
     *
     * @return The time the object was last borrowed.
     * @since 2.11.0
     */
    default Instant getLastReturnInstant() {
        return Instant.ofEpochMilli(getLastReturnTime());
    }

    /**
     * Gets the time the wrapped object was last returned.
     *
     * @return The time the object was last returned.
     * @deprecated Use {@link #getLastReturnInstant()} which offers the best precision.
     */
    @Deprecated
    long getLastReturnTime();

    /**
     * Gets an estimate of the last time this object was used. If the class of the pooled object implements
     * {@link TrackedUse}, what is returned is the maximum of {@link TrackedUse#getLastUsedInstant()} and
     * {@link #getLastBorrowTime()}; otherwise this method gives the same value as {@link #getLastBorrowTime()}.
     *
     * @return the last time this object was used
     * @since 2.11.0
     */
    default Instant getLastUsedInstant() {
        return Instant.ofEpochMilli(getLastUsedTime());
    }

    /**
     * Gets an estimate of the last time this object was used.  If the class
     * of the pooled object implements {@link TrackedUse}, what is returned is
     * the maximum of {@link TrackedUse#getLastUsedInstant()} and
     * {@link #getLastBorrowTime()}; otherwise this method gives the same
     * value as {@link #getLastBorrowTime()}.
     *
     * @return the last time this object was used.
     * @deprecated Use {@link #getLastUsedInstant()} which offers the best precision.
     */
    @Deprecated
    long getLastUsedTime();

    /**
     * Gets the underlying object that is wrapped by this instance of
     * {@link PooledObject}.
     *
     * @return The wrapped object.
     */
    T getObject();

    /**
     * Gets the state of this object.
     * @return state
     */
    PooledObjectState getState();

    @Override
    int hashCode();

    /**
     * Sets the state to {@link PooledObjectState#INVALID INVALID}.
     */
    void invalidate();

    /**
     * Marks the pooled object as abandoned.
     */
    void markAbandoned();

    /**
     * Marks the object as returning to the pool.
     */
    void markReturning();

    /**
     * Prints the stack trace of the code that borrowed this pooled object and
     * the stack trace of the last code to use this object (if available) to
     * the supplied writer.
     *
     * @param   writer  The destination for the debug output.
     */
    void printStackTrace(PrintWriter writer);

    /**
     * Sets whether to use abandoned object tracking. If this is true the
     * implementation will need to record the stack trace of the last caller to
     * borrow this object.
     *
     * @param   logAbandoned    The new configuration setting for abandoned
     *                          object tracking.
     */
    void setLogAbandoned(boolean logAbandoned);

    /**
     * Sets the stack trace generation strategy based on whether or not fully detailed stack traces are required.
     * When set to false, abandoned logs may only include caller class information rather than method names, line
     * numbers, and other normal metadata available in a full stack trace.
     *
     * @param requireFullStackTrace the new configuration setting for abandoned object logging.
     * @since 2.7.0
     */
    default void setRequireFullStackTrace(final boolean requireFullStackTrace) {
        // noop
    }

    /**
     * Attempts to place the pooled object in the
     * {@link PooledObjectState#EVICTION} state.
     *
     * @return {@code true} if the object was placed in the
     *         {@link PooledObjectState#EVICTION} state otherwise
     *         {@code false}.
     */
    boolean startEvictionTest();

    /**
     * Gets a String form of the wrapper for debug purposes. The format is
     * not fixed and may change at any time.
     *
     * {@inheritDoc}
     */
    @Override
    String toString();

    /**
     * Records the current stack trace as the last time the object was used.
     */
    void use();

}
