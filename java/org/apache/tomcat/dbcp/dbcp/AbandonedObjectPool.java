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

package org.apache.tomcat.dbcp.dbcp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.tomcat.dbcp.pool.PoolableObjectFactory;
import org.apache.tomcat.dbcp.pool.impl.GenericObjectPool;

/**
 * <p>An implementation of a Jakarta-Commons ObjectPool which
 * tracks JDBC connections and can recover abandoned db connections.
 * If logAbandoned=true, a stack trace will be printed for any
 * abandoned db connections recovered.
 *
 * @author Glenn L. Nielsen
 */
public class AbandonedObjectPool<T extends AbandonedTrace> extends GenericObjectPool<T> {

    /**
     * DBCP AbandonedConfig
     */
    private final AbandonedConfig config;

    /**
     * A list of connections in use
     */
    private final List<T> trace = new ArrayList<T>();

    /**
     * Create an ObjectPool which tracks db connections.
     *
     * @param factory PoolableObjectFactory used to create this
     * @param config configuration for abandoned db connections
     */
    public AbandonedObjectPool(PoolableObjectFactory<T> factory,
                               AbandonedConfig config) {
        super(factory);
        this.config = config;
    }

    /**
     * Get a db connection from the pool.
     *
     * If removeAbandoned=true, recovers db connections which
     * have been idle &gt; removeAbandonedTimeout and
     * getNumActive() &gt; getMaxActive() - 3 and
     * getNumIdle() &lt; 2
     *
     * @return Object JDBC Connection
     * @throws Exception if an exception occurs retrieving a
     * connection from the pool
     */
    @Override
    public T borrowObject() throws Exception {
        if (config != null
                && config.getRemoveAbandoned()
                && (getNumIdle() < 2)
                && (getNumActive() > getMaxActive() - 3) ) {
            removeAbandoned();
        }
        T obj = super.borrowObject();
        if (obj != null) {
            obj.setStackTrace();
        }
        if (obj != null && config != null && config.getRemoveAbandoned()) {
            synchronized (trace) {
                trace.add(obj);
            }
        }
        return obj;
    }

    /**
     * Return a db connection to the pool.
     *
     * @param obj db Connection to return
     * @throws Exception if an exception occurs returning the connection
     * to the pool
     */
    @Override
    public void returnObject(T obj) throws Exception {
        if (config != null && config.getRemoveAbandoned()) {
            synchronized (trace) {
                boolean foundObject = trace.remove(obj);
                if (!foundObject) {
                    return; // This connection has already been invalidated.  Stop now.
                }
            }
        }
        super.returnObject(obj);
    }

    /**
     * Invalidates an object from the pool.
     *
     * @param obj object to be returned
     * @throws Exception if an exception occurs invalidating the object
     */
    @Override
    public void invalidateObject(T obj) throws Exception {
        if (config != null && config.getRemoveAbandoned()) {
            synchronized (trace) {
                boolean foundObject = trace.remove(obj);
                if (!foundObject) {
                    return; // This connection has already been invalidated.  Stop now.
                }
            }
        }
        super.invalidateObject(obj);
    }

    /**
     * Recover abandoned db connections which have been idle
     * greater than the removeAbandonedTimeout.
     */
    private void removeAbandoned() {
        // Generate a list of abandoned connections to remove
        long now = System.currentTimeMillis();
        long timeout = now - (config.getRemoveAbandonedTimeout() * 1000);
        ArrayList<T> remove = new ArrayList<T>();
        synchronized (trace) {
            Iterator<T> it = trace.iterator();
            while (it.hasNext()) {
                T pc = it.next();
                if (pc.getLastUsed() > timeout) {
                    continue;
                }
                if (pc.getLastUsed() > 0) {
                    remove.add(pc);
                }
            }
        }

        // Now remove the abandoned connections
        Iterator<T> it = remove.iterator();
        while (it.hasNext()) {
            T pc = it.next();
            if (config.getLogAbandoned()) {
                pc.printStackTrace();
            }
            try {
                invalidateObject(pc);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}

