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
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link CallStack} strategy using a {@link SecurityManager}. Obtaining the current call stack is much faster via a
 * SecurityManger, but access to the underlying method may be restricted by the current SecurityManager. In environments
 * where a SecurityManager cannot be created, {@link ThrowableCallStack} should be used instead.
 *
 * @see RuntimePermission
 * @see SecurityManager#getClassContext()
 * @since 2.4.3
 */
public class SecurityManagerCallStack implements CallStack {

    /**
     * A custom security manager.
     */
    private static class PrivateSecurityManager extends SecurityManager {

        /**
         * Gets the class stack.
         *
         * @return class stack
         */
        private List<WeakReference<Class<?>>> getCallStack() {
            final Stream<WeakReference<Class<?>>> map = Stream.of(getClassContext()).map(WeakReference::new);
            return map.collect(Collectors.toList());
        }
    }

    /**
     * A snapshot of a class stack.
     */
    private static class Snapshot {
        private final long timestampMillis = System.currentTimeMillis();
        private final List<WeakReference<Class<?>>> stack;

        /**
         * Constructs a new snapshot with a class stack.
         *
         * @param stack class stack
         */
        private Snapshot(final List<WeakReference<Class<?>>> stack) {
            this.stack = stack;
        }
    }

    private final String messageFormat;

    //@GuardedBy("dateFormat")
    private final DateFormat dateFormat;

    private final PrivateSecurityManager securityManager;

    private volatile Snapshot snapshot;

    /**
     * Creates a new instance.
     *
     * @param messageFormat message format
     * @param useTimestamp whether to format the dates in the output message or not
     */
    public SecurityManagerCallStack(final String messageFormat, final boolean useTimestamp) {
        this.messageFormat = messageFormat;
        this.dateFormat = useTimestamp ? new SimpleDateFormat(messageFormat) : null;
        this.securityManager = AccessController.doPrivileged((PrivilegedAction<PrivateSecurityManager>) PrivateSecurityManager::new);
    }

    @Override
    public void clear() {
        snapshot = null;
    }

    @Override
    public void fillInStackTrace() {
        snapshot = new Snapshot(securityManager.getCallStack());
    }

    @Override
    public boolean printStackTrace(final PrintWriter writer) {
        final Snapshot snapshotRef = this.snapshot;
        if (snapshotRef == null) {
            return false;
        }
        final String message;
        if (dateFormat == null) {
            message = messageFormat;
        } else {
            synchronized (dateFormat) {
                message = dateFormat.format(Long.valueOf(snapshotRef.timestampMillis));
            }
        }
        writer.println(message);
        snapshotRef.stack.forEach(reference -> writer.println(reference.get()));
        return true;
    }
}
