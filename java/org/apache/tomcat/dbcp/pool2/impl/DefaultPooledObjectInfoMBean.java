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

/**
 * The interface that defines the information about pooled objects that will be
 * exposed via JMX.
 *
 * NOTE: This interface exists only to define those attributes and methods that
 *       will be made available via JMX. It must not be implemented by clients
 *       as it is subject to change between major, minor and patch version
 *       releases of commons pool. Clients that implement this interface may
 *       not, therefore, be able to upgrade to a new minor or patch release
 *       without requiring code changes.
 *
 * @since 2.0
 */
public interface DefaultPooledObjectInfoMBean {
    /**
     * Obtain the time (using the same basis as
     * {@link System#currentTimeMillis()}) that pooled object was created.
     *
     * @return The creation time for the pooled object
     */
    long getCreateTime();

    /**
     * Obtain the time that pooled object was created.
     *
     * @return The creation time for the pooled object formatted as
     *         <code>yyyy-MM-dd HH:mm:ss Z</code>
     */
    String getCreateTimeFormatted();

    /**
     * Obtain the time (using the same basis as
     * {@link System#currentTimeMillis()}) the polled object was last borrowed.
     *
     * @return The time the pooled object was last borrowed
     */
    long getLastBorrowTime();

    /**
     * Obtain the time that pooled object was last borrowed.
     *
     * @return The last borrowed time for the pooled object formated as
     *         <code>yyyy-MM-dd HH:mm:ss Z</code>
     */
    String getLastBorrowTimeFormatted();

    /**
     * Obtain the stack trace recorded when the pooled object was last borrowed.
     *
     * @return The stack trace showing which code last borrowed the pooled
     *         object
     */
    String getLastBorrowTrace();


    /**
     * Obtain the time (using the same basis as
     * {@link System#currentTimeMillis()})the wrapped object was last returned.
     *
     * @return The time the object was last returned
     */
    long getLastReturnTime();

    /**
     * Obtain the time that pooled object was last returned.
     *
     * @return The last returned time for the pooled object formated as
     *         <code>yyyy-MM-dd HH:mm:ss Z</code>
     */
    String getLastReturnTimeFormatted();

    /**
     * Obtain the name of the class of the pooled object.
     *
     * @return The pooled object's class name
     *
     * @see Class#getName()
     */
    String getPooledObjectType();

    /**
     * Provides a String form of the wrapper for debug purposes. The format is
     * not fixed and may change at any time.
     *
     * @return A string representation of the pooled object
     *
     * @see Object#toString()
     */
    String getPooledObjectToString();

    /**
     * Get the number of times this object has been borrowed.
     * @return The number of times this object has been borrowed.
     * @since 2.1
     */
    long getBorrowedCount();
}
