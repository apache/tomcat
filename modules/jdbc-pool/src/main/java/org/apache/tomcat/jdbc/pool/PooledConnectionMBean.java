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
    public long getConnectionVersion();
    public boolean isInitialized();
    public boolean isMaxAgeExpired();
    public boolean isSuspect();
    public long getTimestamp();
    public boolean isDiscarded();
    public long getLastValidated();
    public long getLastConnected();
    public boolean isReleased();

    // java.sql.Connection
    public boolean isClosed() throws SQLException;
    public boolean getAutoCommit() throws SQLException;
    public String getCatalog() throws SQLException;
    public int getHoldability() throws SQLException;
    public boolean isReadOnly() throws SQLException;
    public String getSchema() throws SQLException;
    public int getTransactionIsolation() throws SQLException;
}
