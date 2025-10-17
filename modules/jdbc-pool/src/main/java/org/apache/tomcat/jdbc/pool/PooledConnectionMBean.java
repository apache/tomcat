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
package org.apache.tomcat.jdbc.pool;

import java.sql.SQLException;

public interface PooledConnectionMBean {
    // PooledConnection
    long getConnectionVersion();
    boolean isInitialized();
    boolean isMaxAgeExpired();
    boolean isSuspect();
    long getTimestamp();
    boolean isDiscarded();
    long getLastValidated();
    long getLastConnected();
    boolean isReleased();

    // java.sql.Connection
    void clearWarnings();
    boolean isClosed() throws SQLException;
    boolean getAutoCommit() throws SQLException;
    String getCatalog() throws SQLException;
    int getHoldability() throws SQLException;
    boolean isReadOnly() throws SQLException;
    String getSchema() throws SQLException;
    int getTransactionIsolation() throws SQLException;
}
