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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.DynamicMBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;
/**
 * Publishes data to JMX and provides notifications 
 * when failures happen.
 * @author fhanik
 *
 */
public class SlowQueryReportJmx extends SlowQueryReport {
    public static final String SLOW_QUERY_NOTIFICATION = "Slow query";
    public static final String FAILED_QUERY_NOTIFICATION = "Failed query";

    protected static CompositeType SLOW_QUERY_TYPE; 
        
    protected static Log log = LogFactory.getLog(SlowQueryReportJmx.class);
    
    
    protected static ConcurrentHashMap<String,DynamicMBean> mbeans = 
        new ConcurrentHashMap<String,DynamicMBean>(); 
    
    protected String poolName = null;
    
    protected static AtomicLong notifySequence = new AtomicLong(0);
    
    protected static CompositeType getCompositeType() {
        if (SLOW_QUERY_TYPE==null) {
            try {
                SLOW_QUERY_TYPE = new CompositeType(
                        SlowQueryReportJmx.class.getName(),
                        "Composite data type for query statistics",
                        QueryStats.getFieldNames(),
                        QueryStats.getFieldDescriptions(),
                        QueryStats.getFieldTypes());
            }catch (OpenDataException x) {
                log.warn("Unable to initialize composite data type for JMX stats and notifications.",x);
            }
        }
        return SLOW_QUERY_TYPE;
    }
    
    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        // TODO Auto-generated method stub
        super.reset(parent, con);
        if (parent!=null) poolName = parent.getName(); 
    }



    @Override
    public void poolClosed(ConnectionPool pool) {
        this.poolName = pool.getName();
        deregisterJmx();
        super.poolClosed(pool);
    }

    @Override
    public void poolStarted(ConnectionPool pool) {
        super.poolStarted(pool);
        this.poolName = pool.getName();
        registerJmx();
    }

    @Override
    protected String reportFailedQuery(String query, Object[] args, String name, long start, Throwable t) {
        query = super.reportFailedQuery(query, args, name, start, t);
        notifyJmx(query,FAILED_QUERY_NOTIFICATION);
        return query;
    }

    protected void notifyJmx(String query, String type) {
        try {
            DynamicMBean mbean = mbeans.get(poolName);
            if (mbean!=null && mbean instanceof BaseModelMBean) {
                BaseModelMBean bmbean = (BaseModelMBean)mbean;
                long sequence = notifySequence.incrementAndGet();
                Notification notification = 
                    new Notification(type, 
                                     mbean, 
                                     sequence, 
                                     System.currentTimeMillis(),
                                     query);
                bmbean.sendNotification(notification);
            }
        } catch (RuntimeOperationsException e) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to send failed query notification.",e);
            }
        } catch (MBeanException e) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to send failed query notification.",e);
            }
        }
    }

    @Override
    protected String reportSlowQuery(String query, Object[] args, String name, long start, long delta) {
        query = super.reportSlowQuery(query, args, name, start, delta);
        notifyJmx(query,SLOW_QUERY_NOTIFICATION);
        return query;
    }

    /**
     * JMX operation - return the names of all the pools
     * @return
     */
    public String[] getPoolNames() {
        Set<String> keys = perPoolStats.keySet();
        return keys.toArray(new String[0]);
    }

    /**
     * JMX operation - return the name of the pool
     * @return
     */
    public String getPoolName() {
        return poolName;
    }
    
    /**
     * JMX operation - remove all stats for this connection pool
     */
    public void resetStats() {
        ConcurrentHashMap<String,QueryStats> queries = perPoolStats.get(poolName);
        if (queries!=null) {
            Iterator<String> it = queries.keySet().iterator();
            while (it.hasNext()) it.remove();
        }
    }
    
    /**
     * JMX operation - returns all the queries we have collected.
     * @return
     */
    public CompositeData[] getSlowQueriesCD() throws OpenDataException {
        CompositeDataSupport[] result = null;
        ConcurrentHashMap<String,QueryStats> queries = perPoolStats.get(poolName);
        if (queries!=null) {
            Set<Map.Entry<String,QueryStats>> stats = queries.entrySet();
            if (stats!=null) {
                result = new CompositeDataSupport[stats.size()];
                Iterator<Map.Entry<String,QueryStats>> it = stats.iterator();
                int pos = 0;
                while (it.hasNext()) {
                    Map.Entry<String,QueryStats> entry = it.next();
                    QueryStats qs = entry.getValue();
                    result[pos++] = qs.getCompositeData(getCompositeType());
                }
            }
        }
        return result;
    }
    
    protected void deregisterJmx() {
        try {
            DynamicMBean mbean = null;
            if ((mbean=mbeans.remove(poolName))!=null) {
                Registry registry = Registry.getRegistry(null, null);
                ManagedBean managed = registry.findManagedBean(this.getClass().getName());
                if (managed!=null) {
                    ObjectName oname = new ObjectName(ConnectionPool.POOL_JMX_TYPE_PREFIX+getClass().getName()+",name=" + poolName);
                    registry.unregisterComponent(oname);
                    registry.removeManagedBean(managed);
                }
                
            }
        } catch (MalformedObjectNameException e) {
            log.warn("Jmx deregistration failed.",e);
        } catch (RuntimeOperationsException e) {
            log.warn("Jmx deregistration failed.",e);
        }
        
    }
    
    protected void registerJmx() {
        try {
            if (getCompositeType()!=null) {
                ObjectName oname = new ObjectName(ConnectionPool.POOL_JMX_TYPE_PREFIX+getClass().getName()+",name=" + poolName);
                Registry registry = Registry.getRegistry(null, null);
                registry.loadDescriptors(getClass().getPackage().getName(),getClass().getClassLoader());
                ManagedBean managed = registry.findManagedBean(this.getClass().getName());
                DynamicMBean mbean = managed!=null?managed.createMBean(this):null;
                if (mbean!=null && mbeans.putIfAbsent(poolName, mbean)==null) {
                    registry.getMBeanServer().registerMBean( mbean, oname);
                } else if (mbean==null){
                    log.warn(SlowQueryReport.class.getName()+ "- No JMX support, unable to initiate Tomcat JMX.");
                }
            } else {
                log.warn(SlowQueryReport.class.getName()+ "- No JMX support, composite type was not found.");
            }
        } catch (MalformedObjectNameException e) {
            log.error("Jmx registration failed, no JMX data will be exposed for the query stats.",e);
        } catch (InstanceNotFoundException e) {
            log.error("Jmx registration failed, no JMX data will be exposed for the query stats.",e);
        } catch (RuntimeOperationsException e) {
            log.error("Jmx registration failed, no JMX data will be exposed for the query stats.",e);
        } catch (MBeanException e) {
            log.error("Jmx registration failed, no JMX data will be exposed for the query stats.",e);
        } catch (InstanceAlreadyExistsException e) {
            log.error("Jmx registration failed, no JMX data will be exposed for the query stats.",e);
        } catch (NotCompliantMBeanException e) {
            log.error("Jmx registration failed, no JMX data will be exposed for the query stats.",e);
        }
    }
}
