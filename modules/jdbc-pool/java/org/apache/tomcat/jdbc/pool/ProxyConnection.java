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

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
/**
 * @author Filip Hanik
 */
public class ProxyConnection extends JdbcInterceptor {

    protected PooledConnection connection = null;

    protected ConnectionPool pool = null;

    public PooledConnection getConnection() {
        return connection;
    }

    public void setConnection(PooledConnection connection) {
        this.connection = connection;
    }

    public ConnectionPool getPool() {
        return pool;
    }

    public void setPool(ConnectionPool pool) {
        this.pool = pool;
    }

    protected ProxyConnection(ConnectionPool parent, PooledConnection con) throws SQLException {
        pool = parent;
        connection = con;
    }

    public void reset(ConnectionPool parent, PooledConnection con) {
        this.pool = parent;
        this.connection = con;
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return (iface.isInstance(connection.getConnection()));
    }


    public Object unwrap(Class iface) throws SQLException {
        if (isWrapperFor(iface)) {
            return connection.getConnection();
        } else {
            throw new SQLException("Not a wrapper of "+iface.getName());
        }
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (ISCLOSED_VAL==method.getName()) {
            return isClosed();
        }
        if (isClosed()) throw new SQLException("Connection has already been closed.");
        if (CLOSE_VAL==method.getName()) {
            PooledConnection poolc = this.connection;
            this.connection = null;
            pool.returnConnection(poolc);
            return null;
        } else if (TOSTRING_VAL==method.getName()) {
            return this.toString();
        }
        return method.invoke(connection.getConnection(),args);
    }

    public boolean isClosed() {
        return connection==null || connection.isDiscarded();
    }

    public PooledConnection getDelegateConnection() {
        return connection;
    }

    public ConnectionPool getParentPool() {
        return pool;
    }
    
    public String toString() {
        return "ProxyConnection["+(connection!=null?connection.toString():"null")+"]";
    }

}
