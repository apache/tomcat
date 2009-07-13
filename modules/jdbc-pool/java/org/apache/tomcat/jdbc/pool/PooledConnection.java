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


import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.interceptor.ConnectionState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a pooled connection
 * and holds a reference to the java.sql.Connection object
 * @author Filip Hanik
 * @version 1.0
 */
public class PooledConnection {
    /**
     * Logger
     */
    protected static Log log = LogFactory.getLog(PooledConnection.class);
    /**
     * Instance counter
     */
    protected static AtomicInteger counter = new AtomicInteger(01);

    /**
     * Validate when connection is borrowed flag
     */
    public static final int VALIDATE_BORROW = 1;
    /**
     * Validate when connection is returned flag
     */
    public static final int VALIDATE_RETURN = 2;
    /**
     * Validate when connection is idle flag
     */
    public static final int VALIDATE_IDLE = 3;
    /**
     * Validate when connection is initialized flag
     */
    public static final int VALIDATE_INIT = 4;

    /**
     * The properties for the connection pool
     */
    protected PoolConfiguration poolProperties;
    /**
     * The underlying database connection
     */
    private java.sql.Connection connection;
    /**
     * When we track abandon traces, this string holds the thread dump
     */
    private String abandonTrace = null;
    /**
     * Timestamp the connection was last 'touched' by the pool
     */
    private volatile long timestamp;
    /**
     * Lock for this connection only
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false);
    /**
     * Set to true if this connection has been discarded by the pool
     */
    private volatile boolean discarded = false;
    /**
     * The Timestamp when the last time the connect() method was called successfully
     */
    private volatile long lastConnected = -1;
    /**
     * timestamp to keep track of validation intervals
     */
    private volatile long lastValidated = System.currentTimeMillis();
    /**
     * The instance number for this connection
     */
    private int instanceCount = 0;
    /**
     * The parent
     */
    protected ConnectionPool parent;

    /**
     * Weak reference to cache the list of interceptors for this connection
     * so that we don't create a new list of interceptors each time we borrow
     * the connection
     */
    private WeakReference<JdbcInterceptor> handler = null;
    
    private AtomicBoolean released = new AtomicBoolean(false);
    
    public PooledConnection(PoolConfiguration prop, ConnectionPool parent) {
        instanceCount = counter.addAndGet(1);
        poolProperties = prop;
        this.parent = parent;
    }

    public void connect() throws SQLException {
        if (released.get()) throw new SQLException("A connection once released, can't be reestablished.");
        if (connection != null) {
            try {
                this.disconnect(false);
            } catch (Exception x) {
                log.debug("Unable to disconnect previous connection.", x);
            } //catch
        } //end if
        java.sql.Driver driver = null;
        try {
            driver = (java.sql.Driver) Class.forName(poolProperties.getDriverClassName(),
                                                     true, PooledConnection.class.getClassLoader()).newInstance();
        } catch (java.lang.Exception cn) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to instantiate JDBC driver.", cn);
            }
            SQLException ex = new SQLException(cn.getMessage());
            ex.initCause(cn);
            throw ex;
        }
        String driverURL = poolProperties.getUrl();
        String usr = poolProperties.getUsername();
        String pwd = poolProperties.getPassword();
        poolProperties.getDbProperties().setProperty("user", usr);
        poolProperties.getDbProperties().setProperty("password", pwd);
        try {
            connection = driver.connect(driverURL, poolProperties.getDbProperties());
        } catch (Exception x) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to connect to database.", x);
            }
            if (parent.jmxPool!=null) {
                parent.jmxPool.notify(org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.NOTIFY_CONNECT,
                        ConnectionPool.getStackTrace(x));
            }
            if (x instanceof SQLException) {
                throw (SQLException)x;
            } else {
                SQLException ex = new SQLException(x.getMessage());
                ex.initCause(x);
                throw ex;
            }
        }
        if (connection==null) {
            throw new SQLException("Driver:"+driver+" returned null for URL:"+driverURL);
        }
        
        //set up the default state, unless we expect the interceptor to do it
        if (poolProperties.getJdbcInterceptors()==null || poolProperties.getJdbcInterceptors().indexOf(ConnectionState.class.getName())<0) {
            if (poolProperties.getDefaultReadOnly()!=null) connection.setReadOnly(poolProperties.getDefaultReadOnly().booleanValue());
            if (poolProperties.getDefaultAutoCommit()!=null) connection.setAutoCommit(poolProperties.getDefaultAutoCommit().booleanValue());
            if (poolProperties.getDefaultCatalog()!=null) connection.setCatalog(poolProperties.getDefaultCatalog());
            if (poolProperties.getDefaultTransactionIsolation()!=DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION) connection.setTransactionIsolation(poolProperties.getDefaultTransactionIsolation());
        }        
        this.discarded = false;
        this.lastConnected = System.currentTimeMillis();
    }
    
    /**
     * 
     * @return true if connect() was called successfully and disconnect has not yet been called
     */
    public boolean isInitialized() {
        return connection!=null;
    }

    public void reconnect() throws SQLException {
        this.disconnect(false);
        this.connect();
    } //reconnect

    private void disconnect(boolean finalize) {
        if (isDiscarded()) {
            return;
        }
        setDiscarded(true);
        if (connection != null) {
            try {
                connection.close();
            }catch (Exception ignore) {
                if (log.isDebugEnabled()) {
                    log.debug("Unable to close underlying SQL connection",ignore);
                }
            }
        }
        connection = null;
        lastConnected = -1;
        if (finalize) parent.finalize(this);
    }


//============================================================================
//             
//============================================================================

    /**
     * Returns abandon timeout in milliseconds
     * @return abandon timeout in milliseconds
     */
    public long getAbandonTimeout() {
        if (poolProperties.getRemoveAbandonedTimeout() <= 0) {
            return Long.MAX_VALUE;
        } else {
            return poolProperties.getRemoveAbandonedTimeout()*1000;
        } //end if
    }

    private boolean doValidate(int action) {
        if (action == PooledConnection.VALIDATE_BORROW &&
            poolProperties.isTestOnBorrow())
            return true;
        else if (action == PooledConnection.VALIDATE_RETURN &&
                 poolProperties.isTestOnReturn())
            return true;
        else if (action == PooledConnection.VALIDATE_IDLE &&
                 poolProperties.isTestWhileIdle())
            return true;
        else if (action == PooledConnection.VALIDATE_INIT &&
                 poolProperties.isTestOnConnect())
            return true;
        else if (action == PooledConnection.VALIDATE_INIT &&
                 poolProperties.getInitSQL()!=null)
           return true;
        else
            return false;
    }

    /**Returns true if the object is still valid. if not
     * the pool will call the getExpiredAction() and follow up with one
     * of the four expired methods
     */
    public boolean validate(int validateAction) {
        return validate(validateAction,null);
    }

    public boolean validate(int validateAction,String sql) {
        if (this.isDiscarded()) {
            return false;
        }
        
        if (!doValidate(validateAction)) {
            //no validation required, no init sql and props not set
            return true;
        }

        String query = (VALIDATE_INIT==validateAction && (poolProperties.getInitSQL()!=null))?poolProperties.getInitSQL():sql;

        if (query==null) query = poolProperties.getValidationQuery();

        if (query == null) {
            //no validation possible
            return true;
        }
        long now = System.currentTimeMillis();
        if (this.poolProperties.getValidationInterval() > 0 &&
            (validateAction!=VALIDATE_INIT) &&    
            (now - this.lastValidated) <
            this.poolProperties.getValidationInterval()) {
            return true;
        }
        try {
            Statement stmt = connection.createStatement();
            boolean exec = stmt.execute(query);
            stmt.close();
            this.lastValidated = now;
            return true;
        } catch (Exception ignore) {
            if (log.isDebugEnabled())
                log.debug("Unable to validate object:",ignore);
        }
        return false;
    } //validate

    /**
     * The time limit for how long the object
     * can remain unused before it is released
     */
    public long getReleaseTime() {
        return this.poolProperties.getMinEvictableIdleTimeMillis();
    }

    /**
     * This method is called if (Now - timeCheckedIn > getReleaseTime())
     */
    public boolean release() {
        try {
            disconnect(true);
        } catch (Exception x) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to close SQL connection",x);
            }
        }
        return released.compareAndSet(false, true);

    }

    /**
     * The pool will set the stack trace when it is check out and
     * checked in
     */

    public void setStackTrace(String trace) {
        abandonTrace = trace;
    }

    public String getStackTrace() {
        return abandonTrace;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setDiscarded(boolean discarded) {
        if (this.discarded && !discarded) throw new IllegalStateException("Unable to change the state once the connection has been discarded");
        this.discarded = discarded;
    }

    public void setLastValidated(long lastValidated) {
        this.lastValidated = lastValidated;
    }

    public void setPoolProperties(PoolConfiguration poolProperties) {
        this.poolProperties = poolProperties;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isDiscarded() {
        return discarded;
    }

    public long getLastValidated() {
        return lastValidated;
    }

    public PoolConfiguration getPoolProperties() {
        return poolProperties;
    }

    /**
     * Locks the connection only if the sweeper thread is enabled
     * Otherwise this is a noop for performance
     */
    public void lock() {
        if (poolProperties.getUseLock() || this.poolProperties.isPoolSweeperEnabled()) {
            //optimized, only use a lock when there is concurrency
            lock.writeLock().lock();
        }
    }

    /**
     * Unlocks the connection only if the sweeper is enabled
     * Otherwise this is a noop for performance
     */
    public void unlock() {
        if (poolProperties.getUseLock() || this.poolProperties.isPoolSweeperEnabled()) {
          //optimized, only use a lock when there is concurrency
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the underlying connection
     * @return the underlying JDBC connection as it was returned from the JDBC driver
     */
    public java.sql.Connection getConnection() {
        return this.connection;
    }
    
    

    public long getLastConnected() {
        return lastConnected;
    }

    /**
     * Returns the first handler in the interceptor chain
     * @return the first interceptor for this connection
     */
    public JdbcInterceptor getHandler() {
        return (handler!=null)?handler.get():null;
    }

    public void setHandler(JdbcInterceptor handler) {
        if (handler==null) {
            if (this.handler!=null) this.handler.clear();
        } else if (this.handler==null) {
            this.handler = new WeakReference<JdbcInterceptor>(handler);
        } else if (this.handler.get()==null) {
            this.handler.clear();
            this.handler = new WeakReference<JdbcInterceptor>(handler);
        } else if (this.handler.get()!=handler) {
            this.handler.clear();
            this.handler = new WeakReference<JdbcInterceptor>(handler);
        }
    }
    
    public String toString() {
        return "PooledConnection["+(connection!=null?connection.toString():"null")+"]";
    }
    
    public boolean isReleased() {
        return released.get();
    }

}
