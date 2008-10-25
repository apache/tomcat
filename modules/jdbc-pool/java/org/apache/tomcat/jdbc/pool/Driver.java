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


import java.sql.Connection;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Properties;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
/**
 * @author Filip Hanik
 * @version 1.0
 */
public class Driver implements java.sql.Driver {

    protected static Log log = LogFactory.getLog(Driver.class);

    protected static HashMap pooltable = new HashMap(11);

    public Driver() throws SQLException {
    }

    public Driver(PoolProperties properties) throws SQLException {
        init(properties);
    } //Driver

    public void init(PoolProperties properties) throws SQLException {
        if (pooltable.get(properties.getPoolName()) != null)
            throw new SQLException("Pool identified by:" + properties.getPoolName() + " already exists.");
        ConnectionPool pool = new ConnectionPool(properties);
        pooltable.put(properties.getPoolName(), pool);
    }

    public void closePool(String url, boolean all) throws SQLException {
        ConnectionPool pool = (ConnectionPool) pooltable.get(url);
        if (pool == null) {
            throw new SQLException("No connection pool established for URL:" + url);
        } else {
            pool.close(all);
        }
        pooltable.remove(url);
    }

    /**
     * {@inheritDoc}
     */
    public Connection connect(String url, Properties info) throws SQLException {
        ConnectionPool pool = (ConnectionPool) pooltable.get(url);
        if (pool == null) {
            throw new SQLException("No connection pool established for URL:" + url);
        } else {
            try {
                return pool.getConnection();
            } catch (SQLException forward) {
                throw forward;
            } catch (Exception e) {
                throw new SQLException("Unknow pool exception:" + ConnectionPool.getStackTrace(e));
            } //catch
        } //end if
    } //connect

    /**
     * {@inheritDoc}
     */
    public boolean acceptsURL(String url) throws SQLException {
        /* check if the driver has a connection pool with that name */
        return (pooltable.get(url) != null ? true : false);
    } //acceptsUrl

    /**
     * {@inheritDoc}
     */
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws
        SQLException {
        return new DriverPropertyInfo[0];
    } //getPropertyInfo

    /**
     * {@inheritDoc}
     */
    public int getMajorVersion() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    public int getMinorVersion() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public boolean jdbcCompliant() {
        return true;
    }

    public ConnectionPool getPool(String url) throws SQLException {
        return (ConnectionPool) pooltable.get(url);
    }

} //class
