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

import java.security.AccessControlException;

/**
 * Utility methods for {@link CallStack}.
 *
 * @since 2.4.3
 */
public final class CallStackUtils {

    /**
     * Returns whether the caller can create a security manager in the current environment.
     *
     * @return {@code true} if it is able to create a security manager in the current environment, {@code false}
     *         otherwise.
     */
    private static boolean canCreateSecurityManager() {
        final SecurityManager manager = System.getSecurityManager();
        if (manager == null) {
            return true;
        }
        try {
            manager.checkPermission(new RuntimePermission("createSecurityManager"));
            return true;
        } catch (final AccessControlException ignored) {
            return false;
        }
    }

    /**
     * Constructs a new {@link CallStack} using the fastest allowed strategy.
     *
     * @param messageFormat message (or format) to print first in stack traces
     * @param useTimestamp  if true, interpret message as a SimpleDateFormat and print the created timestamp; otherwise,
     *                      print message format literally
     * @return a new CallStack
     * @deprecated use {@link #newCallStack(String, boolean, boolean)}
     */
    @Deprecated
    public static CallStack newCallStack(final String messageFormat, final boolean useTimestamp) {
        return newCallStack(messageFormat, useTimestamp, false);
    }

    /**
     * Constructs a new {@link CallStack} using the fasted allowed strategy.
     *
     * @param messageFormat         message (or format) to print first in stack traces
     * @param useTimestamp          if true, interpret message as a SimpleDateFormat and print the created timestamp;
     *                              otherwise, print message format literally
     * @param requireFullStackTrace if true, forces the use of a stack walking mechanism that includes full stack trace
     *                              information; otherwise, uses a faster implementation if possible
     * @return a new CallStack
     * @since 2.5
     */
    public static CallStack newCallStack(final String messageFormat,
                                         final boolean useTimestamp,
                                         final boolean requireFullStackTrace) {
        return canCreateSecurityManager() && !requireFullStackTrace ?
            new SecurityManagerCallStack(messageFormat, useTimestamp) :
            new ThrowableCallStack(messageFormat, useTimestamp);
    }

    /**
     * Hidden constructor.
     */
    private CallStackUtils() {
    }
}
