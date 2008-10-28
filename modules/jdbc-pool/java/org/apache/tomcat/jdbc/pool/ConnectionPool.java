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

import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import org.apache.tomcat.jdbc.pool.jmx.ConnectionPoolMBean;

import java.util.concurrent.atomic.AtomicInteger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

/**
 * @author Filip Hanik
 * @version 1.0
 */

public class ConnectionPool {

    //logger
    protected static Log log = LogFactory.getLog(ConnectionPool.class);

    //===============================================================================
    //         INSTANCE/QUICK ACCESS VARIABLE
    //===============================================================================

    /**
     * All the information about the connection pool
     */
    protected PoolProperties poolProperties;

    /**
     * Contains all the connections that are in use
     * TODO - this shouldn't be a blocking queue, simply a list to hold our objects
     */
    protected BlockingQueue<PooledConnection> busy;

    /**
     * Contains all the idle connections
     */
    protected BlockingQueue<PooledConnection> idle;

    /**
     * The thread that is responsible for checking abandoned and idle threads
     */
    protected PoolCleaner poolCleaner;

    /**
     * Pool closed flag
     */
    protected boolean closed = false;

    /**
     * Size of the pool
     */
    protected AtomicInteger size = new AtomicInteger(0);

    /**
     * Since newProxyInstance performs the same operation, over and over
     * again, it is much more optimized if we simply store the constructor ourselves.
     */
    protected Constructor proxyClassConstructor;


    //===============================================================================
    //         PUBLIC METHODS
    //===============================================================================

    /**
     * Instantiate a connection pool. This will create connections if initialSize is larger than 0
     * @param prop PoolProperties - all the properties for this connection pool
     * @throws SQLException
     */
    public ConnectionPool(PoolProperties prop) throws SQLException {
        //setup quick access variables and pools
        init(prop);
    }

    /**
     * Borrows a connection from the pool
     * @return Connection - a java.sql.Connection reflection proxy, wrapping the underlying object.
     * @throws SQLException
     */
    public Connection getConnection() throws SQLException {
        //check out a connection
        PooledConnection con = (PooledConnection)borrowConnection();
        JdbcInterceptor handler = con.getHandler();
        if (handler==null) {
            //build the proxy handler
            handler = new ProxyConnection(this,con);
            //set up the interceptor chain
            String[] proxies = getPoolProperties().getJdbcInterceptorsAsArray();
            for (int i=proxies.length-1; i>=0; i--) {
                try {
                    JdbcInterceptor interceptor =
                        (JdbcInterceptor) Class.forName(proxies[i], true,
                                Thread.currentThread().getContextClassLoader()).newInstance();
                    interceptor.setNext(handler);
                    handler = interceptor;
                }catch(Exception x) {
                    SQLException sx = new SQLException("Unable to instantiate interceptor chain.");
                    sx.initCause(x);
                    throw sx;
                }
            }
            //cache handler for the next iteration
            con.setHandler(handler);
        } else {
            JdbcInterceptor next = handler;
            //we have a cached handler, reset it
            while (next!=null) {
                next.reset(this, con);
                next = next.getNext();
            }
        }

        try {
            //cache the constructor
            if (proxyClassConstructor == null ) {
                Class proxyClass = Proxy.getProxyClass(ConnectionPool.class.getClassLoader(), new Class[] {java.sql.Connection.class});
                proxyClassConstructor = proxyClass.getConstructor(new Class[] { InvocationHandler.class });
            }
            //create the proxy
            //TODO possible optimization, keep track if this connection was returned properly, and don't generate a new facade
            Connection connection = (Connection)proxyClassConstructor.newInstance(new Object[] { handler });
            //return the connection
            return connection;
        }catch (Exception x) {
            throw new SQLException();
        }
    }

    /**
     * Returns the name of this pool
     * @return String
     */
    public String getName() {
        return getPoolProperties().getPoolName();
    }

    /**
     * Returns the pool properties associated with this connection pool
     * @return PoolProperties
     */
    public PoolProperties getPoolProperties() {
        return this.poolProperties;
    }

    /**
     * Returns the total size of this pool, this includes both busy and idle connections
     * @return int
     */
    public int getSize() {
        return idle.size()+busy.size();
    }

    /**
     * Returns the number of connections that are in use
     * @return int
     */
    public int getActive() {
        return busy.size();
    }

    public int getIdle() {
        return idle.size();
    }

    /**
     * Returns true if {@link #close close} has been called, and the connection pool is unusable
     * @return boolean
     */
    public  boolean isClosed() {
        return this.closed;
    }

    @Override
    protected void finalize() throws Throwable {
        close(true);
    }

    /**
     * Closes the pool and all disconnects all idle connections
     * Active connections will be closed upon the {@link java.sql.Connection#close close} method is called
     * on the underlying connection instead of being returned to the pool
     * @param force - true to even close the active connections
     */
    protected void close(boolean force) {
        //are we already closed
        if (this.closed) return;
        //prevent other threads from entering
        this.closed = true;
        //stop background thread
        if (poolCleaner!=null) {
            poolCleaner.stopRunning();
        }

        /* release all idle connections */
        BlockingQueue<PooledConnection> pool = (idle.size()>0)?idle:(force?busy:idle);
        while (pool.size()>0) {
            try {
                //retrieve the next connection
                PooledConnection con = pool.poll(1000, TimeUnit.MILLISECONDS);
                //close it and retrieve the next one, if one is available
                while (con != null) {
                    //close the connection
                    if (pool==idle)
                        release(con);
                    else
                        abandon(con);
                    con = pool.poll(1000, TimeUnit.MILLISECONDS);
                } //while
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupted();
            }
            if (pool.size()==0 && force && pool!=busy) pool = busy;
        }
        size.set(0);
        if (this.getPoolProperties().isJmxEnabled()) stopJmx();
    } //closePool


    //===============================================================================
    //         PROTECTED METHODS
    //===============================================================================
    /**
     * Initialize the connection pool - called from the constructor
     * @param properties PoolProperties - properties used to initialize the pool with
     * @throws SQLException
     */
    protected void init (PoolProperties properties) throws SQLException {
        poolProperties = properties;
        //make space for 10 extra in case we flow over a bit
        busy = new ArrayBlockingQueue<PooledConnection>(properties.getMaxActive(),false);
        //busy = new FairBlockingQueue<PooledConnection>();
        //make space for 10 extra in case we flow over a bit
        if (properties.isFairQueue()) {
            idle = new FairBlockingQueue<PooledConnection>();
        } else {
            idle = new ArrayBlockingQueue<PooledConnection>(properties.getMaxActive(),properties.isFairQueue());
        }

        //if the evictor thread is supposed to run, start it now
        if (properties.isPoolSweeperEnabled()) {
            poolCleaner = new PoolCleaner("[Pool-Cleaner]:" + properties.getName(), this, properties.getTimeBetweenEvictionRunsMillis());
            poolCleaner.start();
        } //end if

        if (properties.getMaxActive()<properties.getInitialSize()) {
            log.warn("initialSize is larger than maxActive, setting initialSize to: "+properties.getMaxActive());
            properties.setInitialSize(properties.getMaxActive());
        }
        if (properties.getMinIdle()>properties.getMaxActive()) {
            log.warn("minIdle is larger than maxActive, setting minIdle to: "+properties.getMaxActive());
            properties.setMinIdle(properties.getMaxActive());
        }
        if (properties.getMaxIdle()>properties.getMaxActive()) {
            log.warn("maxIdle is larger than maxActive, setting maxIdle to: "+properties.getMaxActive());
            properties.setMaxIdle(properties.getMaxActive());
        }
        if (properties.getMaxIdle()<properties.getMinIdle()) {
            log.warn("maxIdle is smaller than minIdle, setting maxIdle to: "+properties.getMinIdle());
            properties.setMaxIdle(properties.getMinIdle());
        }


        //initialize the pool with its initial set of members
        PooledConnection[] initialPool = new PooledConnection[poolProperties.getInitialSize()];
        try {
            for (int i = 0; i < initialPool.length; i++) {
                initialPool[i] = this.borrowConnection();
            } //for

        } catch (SQLException x) {
            close(true);
            throw x;
        } finally {
            //return the members as idle to the pool
            for (int i = 0; i < initialPool.length; i++) {
                if (initialPool[i] != null) {
                    try {this.returnConnection(initialPool[i]);}catch(Exception x){}
                } //end if
            } //for
        } //catch
        if (this.getPoolProperties().isJmxEnabled()) startJmx();
        closed = false;
    }


//===============================================================================
//         CONNECTION POOLING IMPL
//===============================================================================

    /**
     * thread safe way to abandon a connection
     * signals a connection to be abandoned.
     * this will disconnect the connection, and log the stack trace if logAbanded=true
     * @param con PooledConnection
     */
    protected void abandon(PooledConnection con) {
        if (con == null)
            return;
        try {
            con.lock();
            if (getPoolProperties().isLogAbandoned()) {
                log.warn("Connection has been abandoned " + con + ":" +con.getStackTrace());
            }
            con.abandon();
        } finally {
            con.unlock();
        }
    }

    /**
     * thread safe way to release a connection
     * @param con PooledConnection
     */
    protected void release(PooledConnection con) {
        if (con == null)
            return;
        try {
            con.lock();
            con.release();
        } finally {
            con.unlock();
        }
    }

    /**
     * Thread safe way to retrieve a connection from the pool
     * @return PooledConnection
     * @throws SQLException
     */
    protected PooledConnection borrowConnection() throws SQLException {

        if (isClosed()) {
            throw new SQLException("Connection pool closed.");
        } //end if

        //get the current time stamp
        long now = System.currentTimeMillis();
        //see if there is one available immediately
        PooledConnection con = idle.poll();

        while (true) {
            if (con!=null) {
                PooledConnection result = borrowConnection(now, con);
                //validation might have failed, in which case null is returned
                if (result!=null) return result;
            }
            if (size.get() < getPoolProperties().getMaxActive()) {
                if (size.addAndGet(1) <= getPoolProperties().getMaxActive()) {
                    return createConnection(now, con);
                } else {
                    size.addAndGet(-1); //restore the value, we didn't create a connection
                }
            } //end if

            //calculate wait time for this iteration
            long maxWait = (getPoolProperties().getMaxWait()<=0)?Long.MAX_VALUE:getPoolProperties().getMaxWait();
            long timetowait = Math.max(1, maxWait - (System.currentTimeMillis() - now));
            try {
                //retrieve an existing connection
                con = idle.poll(timetowait, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupted();
            }
            //we didn't get a connection, lets see if we timed out
            if (con == null) {
                if ((System.currentTimeMillis() - now) >= maxWait) {
                    throw new SQLException(
                        "Pool empty. Unable to fetch a connection in " + (maxWait / 1000) +
                        " seconds, none available["+busy.size()+" in use].");
                } else {
                    //no timeout, lets try again
                    continue;
                }
            }
        } //while
    }

    protected PooledConnection createConnection(long now, PooledConnection con) {
        //no connections where available we'll create one
        boolean error = false;
        try {
            //connect and validate the connection
            con = create();
            con.lock();
            con.connect();
            if (con.validate(PooledConnection.VALIDATE_INIT)) {
                //no need to lock a new one, its not contented
                con.setTimestamp(now);
                if (getPoolProperties().isLogAbandoned()) {
                    con.setStackTrace(getThreadDump());
                }
                if (!busy.offer(con)) {
                    log.debug("Connection doesn't fit into busy array, connection will not be traceable.");
                }
                return con;
            } else {
                //validation failed, make sure we disconnect
                //and clean up
                error =true;
            } //end if
        } catch (Exception e) {
            error = true;
            log.error("Unable to create a new JDBC connection.", e);
        } finally {
            if (error ) {
                release(con);
            }
            con.unlock();
        }//catch
        return null;
    }

    protected PooledConnection borrowConnection(long now, PooledConnection con) throws SQLException {
        //we have a connection, lets set it up
        boolean setToNull = false;
        try {
            con.lock();
            if ((!con.isDiscarded()) && con.validate(PooledConnection.VALIDATE_BORROW)) {
                //set the timestamp
                con.setTimestamp(now);
                if (getPoolProperties().isLogAbandoned()) {
                    //set the stack trace for this pool
                    con.setStackTrace(getThreadDump());
                }
                if (!busy.offer(con)) {
                    log.debug("Connection doesn't fit into busy array, connection will not be traceable.");
                }
                return con;
            }
            //if we reached here, that means the connection
            //is either discarded or validation failed.
            //we will make one more attempt
            //in order to guarantee that the thread that just acquired
            //the connection shouldn't have to poll again.
            try {
                con.reconnect();
                if (con.validate(PooledConnection.VALIDATE_INIT)) {
                    //set the timestamp
                    con.setTimestamp(now);
                    if (getPoolProperties().isLogAbandoned()) {
                        //set the stack trace for this pool
                        con.setStackTrace(getThreadDump());
                    }
                    if (!busy.offer(con)) {
                        log.debug("Connection doesn't fit into busy array, connection will not be traceable.");
                    }
                    return con;
                } else {
                    //validation failed.
                    release(con);
                    setToNull = true;
                    throw new SQLException("Failed to validate a newly established connection.");
                }
            } catch (Exception x) {
                release(con);                
                setToNull = true;
                if (x instanceof SQLException) {
                    throw (SQLException)x;
                } else {
                    throw new SQLException(x);
                }
            }
        } finally {
            con.unlock();
            if (setToNull) {
                con = null;
            }
        }
    }

    /**
     * Returns a connection to the pool
     * @param con PooledConnection
     */
    protected void returnConnection(PooledConnection con) {
        if (isClosed()) {
            //if the connection pool is closed
            //close the connection instead of returning it
            release(con);
            return; 
        } //end if

        if (con != null) {
            try {
                con.lock();

                if (busy.remove(con)) {
                    if ((!con.isDiscarded()) && (!isClosed()) &&
                            con.validate(PooledConnection.VALIDATE_RETURN)) {
                        con.setStackTrace(null);
                        con.setTimestamp(System.currentTimeMillis());
                        if (!idle.offer(con)) {
                            if (log.isDebugEnabled()) {
                                log.debug("Connection ["+con+"] will be closed and not returned to the pool, idle.offer failed.");
                            }
                            release(con);
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Connection ["+con+"] will be closed and not returned to the pool.");
                        }
                        release(con);
                    } //end if
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Connection ["+con+"] will be closed and not returned to the pool, busy.remove failed.");
                    }
                    release(con);
                }
            } finally {
                con.unlock();
            }
        } //end if
    } //checkIn

    public void checkAbandoned() {
        try {
            if (busy.size()==0) return;
            Iterator<PooledConnection> locked = busy.iterator();
            while (locked.hasNext()) {
                PooledConnection con = locked.next();
                boolean setToNull = false;
                try {
                    con.lock();
                    //the con has been returned to the pool
                    //ignore it
                    if (idle.contains(con))
                        continue;
                    long time = con.getTimestamp();
                    long now = System.currentTimeMillis();
                    if ((now - time) > con.getAbandonTimeout()) {
                        busy.remove(con);
                        abandon(con);
                        release(con);
                        setToNull = true;
                    } else {
                        //do nothing
                    } //end if
                } finally {
                    con.unlock();
                    if (setToNull)
                        con = null;
                }
            } //while
        } catch (ConcurrentModificationException e) {
            log.debug("checkAbandoned failed." ,e);
        } catch (Exception e) {
            log.warn("checkAbandoned failed, it will be retried.",e);
        }
    }

    public void checkIdle() {
        try {
            if (idle.size()==0) return;
            long now = System.currentTimeMillis();
            Iterator<PooledConnection> unlocked = idle.iterator();
            while ( (idle.size()>=getPoolProperties().getMinIdle()) && unlocked.hasNext()) {
                PooledConnection con = unlocked.next();
                boolean setToNull = false;
                try {
                    con.lock();
                    //the con been taken out, we can't clean it up
                    if (busy.contains(con))
                        continue;
                    long time = con.getTimestamp();
                    if (((now - time) > con.getReleaseTime()) && (getSize()>getPoolProperties().getMinIdle())) {
                        release(con);
                        idle.remove(con);
                        setToNull = true;
                    } else {
                        //do nothing
                    } //end if
                } finally {
                    con.unlock();
                    if (setToNull)
                        con = null;
                }
            } //while
        } catch (ConcurrentModificationException e) {
            log.debug("checkIdle failed." ,e);
        } catch (Exception e) {
            log.warn("checkIdle failed, it will be retried.",e);
        }

    }

    public void testAllIdle() {
        try {
            if (idle.size()==0) return;
            Iterator<PooledConnection> unlocked = idle.iterator();
            while (unlocked.hasNext()) {
                PooledConnection con = unlocked.next();
                try {
                    con.lock();
                    //the con been taken out, we can't clean it up
                    if (busy.contains(con))
                        continue;
                    if (!con.validate(PooledConnection.VALIDATE_IDLE)) {
                        idle.remove(con);
                        con.release();
                    }
                } finally {
                    con.unlock();
                }
            } //while
        } catch (ConcurrentModificationException e) {
            log.debug("testAllIdle failed." ,e);
        } catch (Exception e) {
            log.warn("testAllIdle failed, it will be retried.",e);
        }

    }


    protected static String getThreadDump() {
        Exception x = new Exception();
        x.fillInStackTrace();
        return getStackTrace(x);
    }

    protected static String getStackTrace(Exception x) {
        if (x == null) {
            return null;
        } else {
            java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
            java.io.PrintStream writer = new java.io.PrintStream(bout);
            x.printStackTrace(writer);
            String result = bout.toString();
            return result;
        } //end if
    }


    protected PooledConnection create() throws java.lang.Exception {
        PooledConnection con = new PooledConnection(getPoolProperties(), this);
        return con;
    }

    protected void finalize(PooledConnection con) {
        size.addAndGet(-1);
    }

    public void startJmx() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("org.apache.tomcat.jdbc.pool.jmx:type=ConnectionPool,name="+getName());
            mbs.registerMBean(new org.apache.tomcat.jdbc.pool.jmx.ConnectionPool(this), name);
        } catch (Exception x) {
            log.warn("Unable to start JMX integration for connection pool. Instance["+getName()+"] can't be monitored.",x);
        }
    }

    public void stopJmx() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("org.apache.tomcat.jdbc.pool.jmx:type=ConnectionPool,name="+getName());
            mbs.unregisterMBean(name);
        }catch (Exception x) {
            log.warn("Unable to stop JMX integration for connection pool. Instance["+getName()+"].",x);
        }
    }


    protected class PoolCleaner extends Thread {
        protected ConnectionPool pool;
        protected long sleepTime;
        protected boolean run = true;
        PoolCleaner(String name, ConnectionPool pool, long sleepTime) {
            super(name);
            this.setDaemon(true);
            this.pool = pool;
            this.sleepTime = sleepTime;
            if (sleepTime <= 0) {
                pool.log.warn("Database connection pool evicter thread interval is set to 0, defaulting to 30 seconds");
                this.sleepTime = 1000 * 30;
            } else if (sleepTime < 1000) {
                pool.log.warn("Database connection pool evicter thread interval is set to lower than 1 second.");
            }
        }

        public void run() {
            while (run) {
                try {
                    sleep(sleepTime);
                } catch (InterruptedException e) {
                    // ignore it
                    Thread.currentThread().interrupted();
                    continue;
                } //catch

                if (pool.isClosed()) {
                    if (pool.getSize() <= 0) {
                        run = false;
                    }
                } else {
                    try {
                        if (pool.getPoolProperties().isRemoveAbandoned())
                            pool.checkAbandoned();
                        if (pool.getPoolProperties().getMaxIdle()<pool.idle.size())
                            pool.checkIdle();
                        if (pool.getPoolProperties().isTestWhileIdle())
                            pool.testAllIdle();
                    } catch (Exception x) {
                        pool.log.error("", x);
                    } //catch
                } //end if
            } //while
        } //run

        public void stopRunning() {
            run = false;
            interrupt();
        }
    }
}
