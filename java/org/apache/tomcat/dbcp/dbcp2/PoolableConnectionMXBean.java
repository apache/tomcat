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

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Defines the attributes and methods that will be exposed via JMX for {@link PoolableConnection} instances.
 *
 * @since 2.0
 */
public interface PoolableConnectionMXBean {

    /**
     * Clears the cached state. Call when you know that the underlying connection may have been accessed directly.
     */
    void clearCachedState();

    /**
     * See {@link Connection#clearWarnings()}.
     *
     * @throws SQLException See {@link Connection#clearWarnings()}.
     */
    void clearWarnings() throws SQLException;

    /**
     * Returns this instance to my containing pool.
     *
     * @throws SQLException Throw if this instance cannot be returned.
     */
    void close() throws SQLException;

    /**
     * See {@link Connection#getAutoCommit()}.
     *
     * @return See {@link Connection#getAutoCommit()}.
     * @throws SQLException See {@link Connection#getAutoCommit()}.
     */
    boolean getAutoCommit() throws SQLException;

    /**
     * Gets whether to cache properties. The cached properties are:
     * <ul>
     * <li>auto-commit</li>
     * <li>catalog</li>
     * <li>schema</li>
     * <li>read-only</li>
     * </ul>
     *
     * @return The value for the state caching flag.
     */
    boolean getCacheState();

    /**
     * See {@link Connection#getCatalog()}.
     *
     * @return See {@link Connection#getCatalog()}.
     * @throws SQLException See {@link Connection#getCatalog()}.
     */
    String getCatalog() throws SQLException;

    /**
     * See {@link Connection#getHoldability()}.
     *
     * @return See {@link Connection#getHoldability()}.
     * @throws SQLException See {@link Connection#getHoldability()}.
     */
    int getHoldability() throws SQLException;

    /**
     * See {@link Connection#getSchema()}.
     *
     * @return See {@link Connection#getSchema()}.
     * @throws SQLException See {@link Connection#getSchema()}.
     */
    String getSchema() throws SQLException;

    /**
     * Gets the value of the {@link Object#toString()} method via a bean getter, so it can be read as a property via JMX.
     *
     * @return the value of the {@link Object#toString()}.
     */
    String getToString();

    /**
     * See {@link Connection#getTransactionIsolation()}.
     *
     * @return See {@link Connection#getTransactionIsolation()}.
     * @throws SQLException See {@link Connection#getTransactionIsolation()}.
     */
    int getTransactionIsolation() throws SQLException;

    /**
     * See {@link Connection#isClosed()}.
     *
     * @return See {@link Connection#isClosed()}.
     * @throws SQLException See {@link Connection#isClosed()}.
     */
    boolean isClosed() throws SQLException;

    /**
     * See {@link Connection#isReadOnly()}.
     *
     * @return See {@link Connection#isReadOnly()}.
     * @throws SQLException See {@link Connection#isReadOnly()}.
     */
    boolean isReadOnly() throws SQLException;

    /**
     * Closes the underlying {@link Connection}.
     *
     * @throws SQLException Thrown if the connection can be closed.
     */
    void reallyClose() throws SQLException;

    /**
     * See {@link Connection#setAutoCommit(boolean)}.
     *
     * @param autoCommit See {@link Connection#setAutoCommit(boolean)}.
     * @throws SQLException See {@link Connection#setAutoCommit(boolean)}.
     */
    void setAutoCommit(boolean autoCommit) throws SQLException;

    /**
     * Sets whether to cache properties. The cached properties are:
     * <ul>
     * <li>auto-commit</li>
     * <li>catalog</li>
     * <li>schema</li>
     * <li>read-only</li>
     * </ul>
     *
     * @param cacheState The new value for the state caching flag
     */
    void setCacheState(boolean cacheState);

    /**
     * See {@link Connection#setCatalog(String)}.
     *
     * @param catalog See {@link Connection#setCatalog(String)}.
     * @throws SQLException See {@link Connection#setCatalog(String)}.
     */
    void setCatalog(String catalog) throws SQLException;

    /**
     * See {@link Connection#setHoldability(int)}.
     *
     * @param holdability {@link Connection#setHoldability(int)}.
     * @throws SQLException See {@link Connection#setHoldability(int)}.
     */
    void setHoldability(int holdability) throws SQLException;

    /**
     * See {@link Connection#setReadOnly(boolean)}.
     *
     * @param readOnly See {@link Connection#setReadOnly(boolean)}.
     * @throws SQLException See {@link Connection#setReadOnly(boolean)}.
     */
    void setReadOnly(boolean readOnly) throws SQLException;

    /**
     * See {@link Connection#setSchema(String)}.
     *
     * @param schema See {@link Connection#setSchema(String)}.
     * @throws SQLException See {@link Connection#setSchema(String)}.
     */
    void setSchema(String schema) throws SQLException;

    /**
     * See {@link Connection#setTransactionIsolation(int)}.
     *
     * @param level See {@link Connection#setTransactionIsolation(int)}.
     * @throws SQLException See {@link Connection#setTransactionIsolation(int)}.
     */
    void setTransactionIsolation(int level) throws SQLException;
}
