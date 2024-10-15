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
package org.apache.tomcat.dbcp.dbcp2;

import java.sql.SQLException;

/**
 * Defines the attributes and methods that will be exposed via JMX for {@link PoolableConnection} instances.
 *
 * @since 2.0
 */
public interface PoolableConnectionMXBean {

    void clearCachedState();

    void clearWarnings() throws SQLException;

    void close() throws SQLException;

    boolean getAutoCommit() throws SQLException;

    boolean getCacheState();

    String getCatalog() throws SQLException;

    int getHoldability() throws SQLException;

    String getSchema() throws SQLException;

    String getToString();

    int getTransactionIsolation() throws SQLException;

    boolean isClosed() throws SQLException;

    boolean isReadOnly() throws SQLException;

    void reallyClose() throws SQLException;

    void setAutoCommit(boolean autoCommit) throws SQLException;

    void setCacheState(boolean cacheState);

    void setCatalog(String catalog) throws SQLException;

    void setHoldability(int holdability) throws SQLException;

    void setReadOnly(boolean readOnly) throws SQLException;

    void setSchema(String schema) throws SQLException;

    void setTransactionIsolation(int level) throws SQLException;
}
