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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public class PooledConnection {

    public static final int VALIDATE_BORROW = 1;
    public static final int VALIDATE_RETURN = 2;
    public static final int VALIDATE_IDLE = 3;
    public static final int VALIDATE_INIT = 4;

    protected static Log log = LogFactory.getLog(PooledConnection.class);
    protected static AtomicInteger counter = new AtomicInteger(01);

    protected PoolProperties poolProperties;
    protected java.sql.Connection connection;
    protected String abandonTrace = null;
    protected long timestamp;
    protected ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false);
    protected boolean discarded = false;
    protected long lastValidated = System.currentTimeMillis();
    protected int instanceCount = 0;
    protected ConnectionPool parent;

    protected WeakReference<JdbcInterceptor> handler = null;

    public PooledConnection(PoolProperties prop, ConnectionPool parent) throws SQLException {
        instanceCount = counter.addAndGet(1);
        poolProperties = prop;
        this.parent = parent;
    }

    protected void connect() throws SQLException {
        if (connection != null) {
            try {
                this.disconnect(false);
            } catch (Exception x) {
                log.error("Unable to disconnect previous connection.", x);
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
        //set up the default state, unless we expect the interceptor to do it
        if (poolProperties.getJdbcInterceptors()==null || poolProperties.getJdbcInterceptors().indexOf(ConnectionState.class.getName())<0) {
            if (poolProperties.getDefaultReadOnly()!=null) connection.setReadOnly(poolProperties.getDefaultReadOnly().booleanValue());
            if (poolProperties.getDefaultAutoCommit()!=null) connection.setAutoCommit(poolProperties.getDefaultAutoCommit().booleanValue());
            if (poolProperties.getDefaultCatalog()!=null) connection.setCatalog(poolProperties.getDefaultCatalog());
            if (poolProperties.getDefaultTransactionIsolation()!=DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION) connection.setTransactionIsolation(poolProperties.getDefaultTransactionIsolation());
        }        
        this.discarded = false;
    }

    protected void reconnect() throws SQLException {
        this.disconnect(false);
        this.connect();
    } //reconnect

    protected synchronized void disconnect(boolean finalize) {
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
        if (finalize) parent.finalize(this);
    }


//============================================================================
//             
//============================================================================

    public long getAbandonTimeout() {
        if (poolProperties.getRemoveAbandonedTimeout() <= 0) {
            return Long.MAX_VALUE;
        } else {
            return poolProperties.getRemoveAbandonedTimeout()*1000;
        } //end if
    }

    public boolean abandon() {
        try {
            disconnect(true);
        } catch (Exception x) {
            log.error("", x);
        } //catch
        return false;
    }

    protected boolean doValidate(int action) {
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
    public void release() {
        try {
            disconnect(true);
        } catch (Exception x) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to close SQL connection",x);
            }
        }

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

    public void setPoolProperties(PoolProperties poolProperties) {
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

    public PoolProperties getPoolProperties() {
        return poolProperties;
    }

    public void lock() {
        if (this.poolProperties.isPoolSweeperEnabled()) {
            //optimized, only use a lock when there is concurrency
            lock.writeLock().lock();
        }
    }

    public void unlock() {
        if (this.poolProperties.isPoolSweeperEnabled()) {
          //optimized, only use a lock when there is concurrency
            lock.writeLock().unlock();
        }
    }

    public java.sql.Connection getConnection() {
        return this.connection;
    }

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

}
