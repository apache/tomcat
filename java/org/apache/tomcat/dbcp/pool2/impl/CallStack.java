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
 * Strategy for obtaining and printing the current call stack. This is primarily useful for
 * {@link org.apache.tomcat.dbcp.pool2.UsageTracking usage tracking} so
 * that different JVMs and configurations can use more efficient strategies
 * for obtaining the current call stack depending on metadata needs.
 *
 * @see CallStackUtils
 * @since 2.4.3
 */
public interface CallStack {

    /**
     * Prints the current stack trace if available to a PrintWriter. The format is undefined and is primarily useful
     * for debugging issues with {@link org.apache.tomcat.dbcp.pool2.PooledObject} usage in user code.
     *
     * @param writer a PrintWriter to write the current stack trace to if available
     * @return true if a stack trace was available to print or false if nothing was printed
     */
    boolean printStackTrace(final PrintWriter writer);

    /**
     * Takes a snapshot of the current call stack. Subsequent calls to {@link #printStackTrace(PrintWriter)} will print
     * out that stack trace until it is {@linkplain #clear() cleared}.
     */
    void fillInStackTrace();

    /**
     * Clears the current stack trace snapshot. Subsequent calls to {@link #printStackTrace(PrintWriter)} will be
     * no-ops until another call to {@link #fillInStackTrace()}.
     */
    void clear();
}
