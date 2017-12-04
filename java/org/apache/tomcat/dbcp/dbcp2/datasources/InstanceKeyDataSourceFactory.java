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
package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

/**
 * A JNDI ObjectFactory which creates <code>SharedPoolDataSource</code>s
 * or <code>PerUserPoolDataSource</code>s
 *
 * @since 2.0
 */
abstract class InstanceKeyDataSourceFactory implements ObjectFactory {

    private static final Map<String, InstanceKeyDataSource> instanceMap =
            new ConcurrentHashMap<>();

    static synchronized String registerNewInstance(final InstanceKeyDataSource ds) {
        int max = 0;
        final Iterator<String> i = instanceMap.keySet().iterator();
        while (i.hasNext()) {
            final String s = i.next();
            if (s != null) {
                try {
                    max = Math.max(max, Integer.parseInt(s));
                } catch (final NumberFormatException e) {
                    // no sweat, ignore those keys
                }
            }
        }
        final String instanceKey = String.valueOf(max + 1);
        // put a placeholder here for now, so other instances will not
        // take our key.  we will replace with a pool when ready.
        instanceMap.put(instanceKey, ds);
        return instanceKey;
    }

    static void removeInstance(final String key) {
        if (key != null) {
            instanceMap.remove(key);
        }
    }

    /**
     * Close all pools associated with this class.
     * @throws Exception Close exception
     */
    public static void closeAll() throws Exception {
        //Get iterator to loop over all instances of this datasource.
        final Iterator<Entry<String,InstanceKeyDataSource>> instanceIterator =
            instanceMap.entrySet().iterator();
        while (instanceIterator.hasNext()) {
            instanceIterator.next().getValue().close();
        }
        instanceMap.clear();
    }


    /**
     * Implements ObjectFactory to create an instance of SharedPoolDataSource
     * or PerUserPoolDataSource.
     */
    @Override
    public Object getObjectInstance(final Object refObj, final Name name,
                                    final Context context, final Hashtable<?,?> env)
        throws IOException, ClassNotFoundException {
        // The spec says to return null if we can't create an instance
        // of the reference
        Object obj = null;
        if (refObj instanceof Reference) {
            final Reference ref = (Reference) refObj;
            if (isCorrectClass(ref.getClassName())) {
                final RefAddr ra = ref.get("instanceKey");
                if (ra != null && ra.getContent() != null) {
                    // object was bound to jndi via Referenceable api.
                    obj = instanceMap.get(ra.getContent());
                }
                else
                {
                    // tomcat jndi creates a Reference out of server.xml
                    // <ResourceParam> configuration and passes it to an
                    // instance of the factory given in server.xml.
                    String key = null;
                    if (name != null)
                    {
                        key = name.toString();
                        obj = instanceMap.get(key);
                    }
                    if (obj == null)
                    {
                        final InstanceKeyDataSource ds = getNewInstance(ref);
                        setCommonProperties(ref, ds);
                        obj = ds;
                        if (key != null)
                        {
                            instanceMap.put(key, ds);
                        }
                    }
                }
            }
        }
        return obj;
    }

    private void setCommonProperties(final Reference ref,
                                     final InstanceKeyDataSource ikds)
        throws IOException, ClassNotFoundException {

        RefAddr ra = ref.get("dataSourceName");
        if (ra != null && ra.getContent() != null) {
            ikds.setDataSourceName(ra.getContent().toString());
        }

        ra = ref.get("description");
        if (ra != null && ra.getContent() != null) {
            ikds.setDescription(ra.getContent().toString());
        }

        ra = ref.get("jndiEnvironment");
        if (ra != null  && ra.getContent() != null) {
            final byte[] serialized = (byte[]) ra.getContent();
            ikds.setJndiEnvironment((Properties) deserialize(serialized));
        }

        ra = ref.get("loginTimeout");
        if (ra != null && ra.getContent() != null) {
            ikds.setLoginTimeout(
                Integer.parseInt(ra.getContent().toString()));
        }

        // Pool properties
        ra = ref.get("blockWhenExhausted");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultBlockWhenExhausted(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("evictionPolicyClassName");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultEvictionPolicyClassName(ra.getContent().toString());
        }

        // Pool properties
        ra = ref.get("lifo");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultLifo(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("maxIdlePerKey");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultMaxIdle(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("maxTotalPerKey");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultMaxTotal(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("maxWaitMillis");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultMaxWaitMillis(
                Long.parseLong(ra.getContent().toString()));
        }

        ra = ref.get("minEvictableIdleTimeMillis");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultMinEvictableIdleTimeMillis(
                Long.parseLong(ra.getContent().toString()));
        }

        ra = ref.get("minIdlePerKey");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultMinIdle(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("numTestsPerEvictionRun");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultNumTestsPerEvictionRun(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("softMinEvictableIdleTimeMillis");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultSoftMinEvictableIdleTimeMillis(
                Long.parseLong(ra.getContent().toString()));
        }

        ra = ref.get("testOnCreate");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultTestOnCreate(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("testOnBorrow");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultTestOnBorrow(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("testOnReturn");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultTestOnReturn(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("testWhileIdle");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultTestWhileIdle(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("timeBetweenEvictionRunsMillis");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultTimeBetweenEvictionRunsMillis(
                Long.parseLong(ra.getContent().toString()));
        }


        // Connection factory properties

        ra = ref.get("validationQuery");
        if (ra != null && ra.getContent() != null) {
            ikds.setValidationQuery(ra.getContent().toString());
        }

        ra = ref.get("validationQueryTimeout");
        if (ra != null && ra.getContent() != null) {
            ikds.setValidationQueryTimeout(Integer.parseInt(
                    ra.getContent().toString()));
        }

        ra = ref.get("rollbackAfterValidation");
        if (ra != null && ra.getContent() != null) {
            ikds.setRollbackAfterValidation(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("maxConnLifetimeMillis");
        if (ra != null && ra.getContent() != null) {
            ikds.setMaxConnLifetimeMillis(
                Long.parseLong(ra.getContent().toString()));
        }


        // Connection properties

        ra = ref.get("defaultAutoCommit");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultAutoCommit(Boolean.valueOf(ra.getContent().toString()));
        }

        ra = ref.get("defaultTransactionIsolation");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultTransactionIsolation(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("defaultReadOnly");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultReadOnly(Boolean.valueOf(ra.getContent().toString()));
        }
    }


    /**
     * @param className The class name
     * @return true if and only if className is the value returned
     * from getClass().getName().toString()
     */
    protected abstract boolean isCorrectClass(String className);

    /**
     * Creates an instance of the subclass and sets any properties
     * contained in the Reference.
     * @param ref The reference
     * @return the data source
     * @throws IOException IO error
     * @throws ClassNotFoundException Couldn't load data source implementation
     */
    protected abstract InstanceKeyDataSource getNewInstance(Reference ref)
        throws IOException, ClassNotFoundException;

    /**
     * Used to set some properties saved within a Reference.
     * @param data Object data
     * @return the deserialized object
     * @throws IOException Stream error
     * @throws ClassNotFoundException Couldn't load object class
     */
    protected static final Object deserialize(final byte[] data)
        throws IOException, ClassNotFoundException {
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(new ByteArrayInputStream(data));
            return in.readObject();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (final IOException ex) {
                }
            }
        }
    }
}

