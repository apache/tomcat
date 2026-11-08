/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.pool.interceptor;

/**
 * MBean interface for the ResetAbandonedTimer connection pool interceptor.
 */
public interface ResetAbandonedTimerMBean {
    /**
     * Reset the abandoned timer of the pooled connection this interceptor is
     * bound to, while that connection is checked out. For a connection sitting
     * idle in the pool the operation resets nothing - the abandoned timer is a
     * property of a borrow, and the timestamp it moves is the age the idle check
     * releases a connection by
     * ({@link org.apache.tomcat.jdbc.pool.PoolConfiguration#setMinEvictableIdleTimeMillis(int)}),
     * so the operation cannot postpone that release either.
     *
     * @return True if the timer was reset, false if the connection bound to this
     *             interceptor is not currently checked out - including a
     *             connection idle in the pool and one this interceptor has been
     *             unbound from
     */
    boolean resetTimer();
}
