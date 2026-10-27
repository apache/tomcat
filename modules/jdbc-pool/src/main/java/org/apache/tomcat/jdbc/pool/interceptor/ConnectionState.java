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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSourceFactory;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * Interceptor that keep track of connection state to avoid roundtrips to the database.
 * The {@link org.apache.tomcat.jdbc.pool.ConnectionPool} is optimized to do as little work as possible.
 * The pool itself doesn't remember settings like {@link java.sql.Connection#setAutoCommit(boolean)},
 * {@link java.sql.Connection#setReadOnly(boolean)}, {@link java.sql.Connection#setCatalog(String)} or
 * {@link java.sql.Connection#setTransactionIsolation(int)}. It relies on the application to remember how and when
 * these settings have been applied.
 * In the cases where the application code doesn't know or want to keep track of the state, this interceptor helps cache the
 * state, and it also avoids roundtrips to the database asking for it.
 *
 */

public class ConnectionState extends JdbcInterceptor  {
    private static final Log log = LogFactory.getLog(ConnectionState.class);

    /**
     * Creates a new ConnectionState interceptor instance.
     */
    public ConnectionState() {
        super();
    }

    /**
     * Method names for reading connection state.
     */
    protected final String[] readState = {"getAutoCommit","getTransactionIsolation","isReadOnly","getCatalog"};

    /**
     * Method names for writing connection state.
     */
    protected final String[] writeState = {"setAutoCommit","setTransactionIsolation","setReadOnly","setCatalog"};

    /**
     * Cached auto-commit state of the connection.
     */
    protected Boolean autoCommit = null;

    /**
     * Cached transaction isolation level of the connection.
     */
    protected Integer transactionIsolation = null;

    /**
     * Cached read-only state of the connection.
     */
    protected Boolean readOnly = null;

    /**
     * Cached catalog (database) name of the connection.
     */
    protected String catalog = null;


    /**
     * Restores the connection state the pool was configured to enforce.
     * The pool invokes this on every borrow, before the connection is
     * handed to its next borrower. If the previous borrower returned the
     * connection while still in a transaction, that transaction is
     * terminated first: the isolation level and the read only state are
     * transaction scoped on many drivers, and an open transaction would
     * otherwise absorb the restores below (or, when auto-commit is
     * restored, silently commit the previous borrower's uncommitted work).
     * If terminating that transaction fails, the borrow fails and the pool
     * retires the connection. Nothing is terminated when there is nothing
     * to restore - no default is configured, or the cache already shows
     * every configured default in place - so a pool that uses this
     * interceptor only to cache the state does not pay for the
     * termination.
     *
     * @param parent the pool the connection belongs to, or {@code null}
     *               when the interceptor chain is being torn down
     * @param con    the pooled connection being set up for its next
     *               borrower
     */
    @SuppressWarnings("null") // Suppress false positive warnings
    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        if (parent==null || con==null) {
            //we are resetting, reset our defaults
            autoCommit = null;
            transactionIsolation = null;
            readOnly = null;
            catalog = null;
            return;
        }
        PoolConfiguration poolProperties = parent.getPoolProperties();
        final int defaultTransactionIsolation = poolProperties.getDefaultTransactionIsolation();
        final Boolean defaultReadOnly = poolProperties.getDefaultReadOnly();
        final Boolean defaultAutoCommit = poolProperties.getDefaultAutoCommit();
        final String defaultCatalog = poolProperties.getDefaultCatalog();
        boolean resetTransactionIsolation =
                defaultTransactionIsolation != DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION
                && (transactionIsolation == null
                        || transactionIsolation.intValue() != defaultTransactionIsolation);
        boolean resetReadOnly = defaultReadOnly != null
                && (readOnly == null || readOnly.booleanValue() != defaultReadOnly.booleanValue());
        boolean resetAutoCommit = defaultAutoCommit != null
                && (autoCommit == null || autoCommit.booleanValue() != defaultAutoCommit.booleanValue());
        boolean resetCatalog = defaultCatalog != null
                && (catalog == null || !catalog.equals(defaultCatalog));
        if (resetTransactionIsolation || resetReadOnly || resetAutoCommit || resetCatalog) {
            // The previous borrower may have returned this connection while
            // its transaction was still open, unless the pool was configured
            // to terminate transactions on return. Transaction isolation and
            // read only state are transaction scoped on many drivers (the
            // change is refused or deferred until the next transaction) and
            // restoring auto-commit to true would commit whatever the
            // previous borrower left pending. Terminate that transaction
            // first so the restores below are actually applied.
            try {
                Connection raw = con.getConnection();
                if (!raw.getAutoCommit()) {
                    raw.rollback();
                }
            } catch (SQLException x) {
                // Whether the previous borrower's work survived is unknown,
                // so the connection must not go to the next borrower.
                // ConnectionPool.setupConnection() catches this, discards the
                // connection and fails the borrow.
                throw new IllegalStateException(
                        "Unable to check for, or terminate, the pending transaction before restoring the configured connection state.", x);
            }
        }
        if (resetTransactionIsolation) {
            try {
                if (transactionIsolation==null || transactionIsolation.intValue()!=defaultTransactionIsolation) {
                    con.getConnection().setTransactionIsolation(defaultTransactionIsolation);
                    transactionIsolation = Integer.valueOf(defaultTransactionIsolation);
                }
            }catch (SQLException x) {
                transactionIsolation = null;
                log.error("Unable to reset transaction isolation state to connection.",x);
            }
        }
        if (resetReadOnly) {
            try {
                if (readOnly==null || readOnly.booleanValue()!=defaultReadOnly.booleanValue()) {
                    con.getConnection().setReadOnly(defaultReadOnly.booleanValue());
                    readOnly = defaultReadOnly;
                }
            }catch (SQLException x) {
                readOnly = null;
                log.error("Unable to reset readonly state to connection.",x);
            }
        }
        if (resetAutoCommit) {
            try {
                if (autoCommit==null || autoCommit.booleanValue()!=defaultAutoCommit.booleanValue()) {
                    con.getConnection().setAutoCommit(defaultAutoCommit.booleanValue());
                    autoCommit = defaultAutoCommit;
                }
            }catch (SQLException x) {
                autoCommit = null;
                log.error("Unable to reset autocommit state to connection.",x);
            }
        }
        if (resetCatalog) {
            try {
                if (catalog==null || (!catalog.equals(defaultCatalog))) {
                    con.getConnection().setCatalog(defaultCatalog);
                    catalog = defaultCatalog;
                }
            }catch (SQLException x) {
                catalog = null;
                log.error("Unable to reset default catalog state to connection.",x);
            }
        }

    }


    @Override
    public void disconnected(ConnectionPool parent, PooledConnection con, boolean finalizing) {
        //we are resetting, reset our defaults
        autoCommit = null;
        transactionIsolation = null;
        readOnly = null;
        catalog = null;
        super.disconnected(parent, con, finalizing);
    }



    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        boolean read = false;
        int index = -1;
        for (int i=0; (!read) && i<readState.length; i++) {
            read = compare(name,readState[i]);
            if (read) {
                index = i;
            }
        }
        boolean write = false;
        for (int i=0; (!write) && (!read) && i<writeState.length; i++) {
            write = compare(name,writeState[i]);
            if (write) {
                index = i;
            }
        }
        Object result = null;
        if (read) {
            switch (index) {
                case 0:{result = autoCommit; break;}
                case 1:{result = transactionIsolation; break;}
                case 2:{result = readOnly; break;}
                case 3:{result = catalog; break;}
                default: // NOOP
            }
            //return cached result, if we have it
            if (result!=null) {
                return result;
            }
        }

        try {
            result = super.invoke(proxy, method, args);
            if (read || write) {
                switch (index) {
                    case 0:{autoCommit = (Boolean) (read?result:args[0]); break;}
                    case 1:{transactionIsolation = (Integer)(read?result:args[0]); break;}
                    case 2:{readOnly = (Boolean)(read?result:args[0]); break;}
                    case 3:{catalog = (String)(read?result:args[0]); break;}
                }
            }
        } catch (Throwable e) {
            if (write) {
                log.warn("Reset state to null as an exception occurred while calling method[" + name + "].", e);
                switch (index) {
                    case 0:{autoCommit = null; break;}
                    case 1:{transactionIsolation = null; break;}
                    case 2:{readOnly = null; break;}
                    case 3:{catalog = null; break;}
                }
            }
            throw e;
        }
        return result;
    }

}
