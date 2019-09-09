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

package org.apache.tomcat.dbcp.dbcp;

import java.io.PrintWriter;

/**
 * Configuration settings for handling abandoned db connections.
 *
 * @author Glenn L. Nielsen
 */
public class AbandonedConfig {

    /**
     * Whether or not a connection is considered abandoned and eligible
     * for removal if it has been idle longer than the removeAbandonedTimeout
     */
    private boolean removeAbandoned = false;

    /**
     * <p>Flag to remove abandoned connections if they exceed the
     * removeAbandonedTimeout.</p>
     *
     * <p>The default value is false.</p>
     *
     * <p>If set to true a connection is considered abandoned and eligible
     * for removal if it has been idle longer than the
     * {@link #getRemoveAbandoned() removeAbandonedTimeout}.</p>
     *
     * @return true if abandoned connections are to be removed
     */
    public boolean getRemoveAbandoned() {
        return (this.removeAbandoned);
    }

    /**
     * <p>Flag to remove abandoned connections if they exceed the
     * removeAbandonedTimeout.</p>
     *
     * <p>If set to true a connection is considered abandoned and eligible
     * for removal if it has been idle longer than the
     * {@link #getRemoveAbandoned() removeAbandonedTimeout}.</p>
     *
     * @param removeAbandoned true means abandoned connections will be
     *   removed
     * @see #getRemoveAbandoned()
     */
    public void setRemoveAbandoned(boolean removeAbandoned) {
        this.removeAbandoned = removeAbandoned;
    }

    /**
     * Timeout in seconds before an abandoned connection can be removed
     */
    private int removeAbandonedTimeout = 300;

    /**
     * <p>Timeout in seconds before an abandoned connection can be removed.</p>
     *
     * <p>Creating a Statement, PreparedStatement or CallableStatement or using
     * one of these to execute a query (using one of the execute methods)
     * resets the lastUsed property of the parent connection.</p>
     *
     * <p>Abandoned connection cleanup happens when
     * <ul>
     * <li>{@link #getRemoveAbandoned() removeAbandoned} == true</li>
     * <li>{@link AbandonedObjectPool#getNumIdle() numIdle} &lt; 2</li>
     * <li>{@link AbandonedObjectPool#getNumActive() numActive} &gt;
     *     {@link AbandonedObjectPool#getMaxActive() maxActive} - 3</li>
     * </ul>
     *
     * <p>The default value is 300 seconds.</p>
     */
    public int getRemoveAbandonedTimeout() {
        return (this.removeAbandonedTimeout);
    }

    /**
     * <p>Sets the timeout in seconds before an abandoned connection can be
     * removed.</p>
     *
     * <p>Setting this property has no effect if
     * {@link #getRemoveAbandoned() removeAbandoned} is false.</p>
     *
     * @param removeAbandonedTimeout new abandoned timeout in seconds
     * @see #getRemoveAbandonedTimeout()
     * @see #getRemoveAbandoned()
     */
    public void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        this.removeAbandonedTimeout = removeAbandonedTimeout;
    }

    /**
     * Determines whether or not to log stack traces for application code
     * which abandoned a Statement or Connection.
     */
    private boolean logAbandoned = false;

    /**
     * Flag to log stack traces for application code which abandoned
     * a Statement or Connection.
     *
     * Defaults to false.
     * Logging of abandoned Statements and Connections adds overhead
     * for every Connection open or new Statement because a stack
     * trace has to be generated.
     *
     * @return boolean true if stack trace logging is turned on for abandoned
     *  Statements or Connections
     *
     */
    public boolean getLogAbandoned() {
        return (this.logAbandoned);
    }

    /**
     * Flag to log stack traces for application code which abandoned
     * a Statement or Connection.
     *
     * Defaults to false.
     * Logging of abandoned Statements and Connections adds overhead
     * for every Connection open or new Statement because a stack
     * trace has to be generated.
     * @param logAbandoned true turns on abandoned stack trace logging
     *
     */
    public void setLogAbandoned(boolean logAbandoned) {
        this.logAbandoned = logAbandoned;
    }

    /**
     * PrintWriter to use to log information on abandoned objects.
     */
    private PrintWriter logWriter = new PrintWriter(System.out);

    /**
     * Returns the log writer being used by this configuration to log
     * information on abandoned objects. If not set, a PrintWriter based on
     * System.out is used.
     *
     * @return log writer in use
     */
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    /**
     * Sets the log writer to be used by this configuration to log
     * information on abandoned objects.
     *
     * @param logWriter The new log writer
     */
    public void setLogWriter(PrintWriter logWriter) {
        this.logWriter = logWriter;
    }
}
