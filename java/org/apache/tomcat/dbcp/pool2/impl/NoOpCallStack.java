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

/**
 * CallStack strategy using no-op implementations of all functionality. Can be used by default when abandoned object
 * logging is disabled.
 *
 * @since 2.5
 */
public class NoOpCallStack implements CallStack {

    /**
     * Singleton instance.
     */
    public static final CallStack INSTANCE = new NoOpCallStack();

    /**
     * Constructs the singleton instance.
     */
    private NoOpCallStack() {
    }

    @Override
    public void clear() {
        // no-op
    }

    @Override
    public void fillInStackTrace() {
        // no-op
    }

    @Override
    public boolean printStackTrace(final PrintWriter writer) {
        return false;
    }
}
