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

import java.time.Instant;

/**
 * Allows pooled objects to make information available about when and how they were used available to the object pool.
 * The object pool may, but is not required, to use this information to make more informed decisions when determining
 * the state of a pooled object - for instance whether or not the object has been abandoned.
 *
 * @since 2.0
 */
public interface TrackedUse {

    /**
     * Gets the last time this object was used in milliseconds.
     *
     * @return the last time this object was used in milliseconds.
     * @deprecated Use {@link #getLastUsedInstant()} which offers the best precision.
     */
    @Deprecated
    long getLastUsed();

    /**
     * Gets the last Instant this object was used.
     * <p>
     * Starting with Java 9, the JRE {@code SystemClock} precision is increased usually down to microseconds, or tenth
     * of microseconds, depending on the OS, Hardware, and JVM implementation.
     * </p>
     *
     * @return the last Instant this object was used.
     * @since 2.11.0
     */
    default Instant getLastUsedInstant() {
        return Instant.ofEpochMilli(getLastUsed());
    }
}
