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
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.tomcat.dbcp.dbcp2.ListException;

/**
 * A JNDI ObjectFactory which creates <code>SharedPoolDataSource</code>s or <code>PerUserPoolDataSource</code>s
 *
 * @since 2.0
 */
abstract class InstanceKeyDataSourceFactory implements ObjectFactory {

    private static final Map<String, InstanceKeyDataSource> instanceMap = new ConcurrentHashMap<>();

    static synchronized String registerNewInstance(final InstanceKeyDataSource ds) {
        int max = 0;
        for (String s : instanceMap.keySet()) {
            if (s != null) {
                try {
                    max = Math.max(max, Integer.parseInt(s));
                } catch (final NumberFormatException e) {
                    // no sweat, ignore those keys
                }
            }
        }
        final String instanceKey = String.valueOf(max + 1);
        // Put a placeholder here for now, so other instances will not
        // take our key. We will replace with a pool when ready.
        instanceMap.put(instanceKey, ds);
        return instanceKey;
    }

    static void removeInstance(final String key) {
        if (key != null) {
            instanceMap.remove(key);
        }
    }

    /**
     * Closes all pools associated with this class.
     *
     * @throws Exception
     *             a {@link ListException} containing all exceptions thrown by {@link InstanceKeyDataSource#close()}
     * @see InstanceKeyDataSource#close()
     * @see ListException
     * @since 2.4.0 throws a {@link ListException} instead of, in 2.3.0 and before, the first exception thrown by
     *        {@link InstanceKeyDataSource#close()}.
     */
    public static void closeAll() throws Exception {
        // Get iterator to loop over all instances of this data source.
        final List<Throwable> exceptionList = new ArrayList<>(instanceMap.size());
        for (Entry<String, InstanceKeyDataSource> next : instanceMap.entrySet()) {
            // Bullet-proof to avoid anything else but problems from InstanceKeyDataSource#close().
            if (next != null) {
                final InstanceKeyDataSource value = next.getValue();
                if (value != null) {
                    try {
                        value.close();
                    } catch (final Exception e) {
                        exceptionList.add(e);
                    }
                }
            }
        }
        instanceMap.clear();
        if (!exceptionList.isEmpty()) {
            throw new ListException("Could not close all InstanceKeyDataSource instances.", exceptionList);
        }
    }

    /**
     * Implements ObjectFactory to create an instance of SharedPoolDataSource or PerUserPoolDataSource
     */
    @Override
    public Object getObjectInstance(final Object refObj, final Name name, final Context context,
            final Hashtable<?, ?> env) throws IOException, ClassNotFoundException {
        // The spec says to return null if we can't create an instance
        // of the reference
        Object obj = null;
        if (refObj instanceof Reference) {
            final Reference ref = (Reference) refObj;
            if (isCorrectClass(ref.getClassName())) {
                final RefAddr refAddr = ref.get("instanceKey");
                if (refAddr != null && refAddr.getContent() != null) {
                    // object was bound to JNDI via Referenceable API.
                    obj = instanceMap.get(refAddr.getContent());
                } else {
                    // Tomcat JNDI creates a Reference out of server.xml
                    // <ResourceParam> configuration and passes it to an
                    // instance of the factory given in server.xml.
                    String key = null;
                    if (name != null) {
                        key = name.toString();
                        obj = instanceMap.get(key);
                    }
                    if (obj == null) {
                        final InstanceKeyDataSource ds = getNewInstance(ref);
                        setCommonProperties(ref, ds);
                        obj = ds;
                        if (key != null) {
                            instanceMap.put(key, ds);
                        }
                    }
                }
            }
        }
        return obj;
    }

    private void setCommonProperties(final Reference ref, final InstanceKeyDataSource ikds)
            throws IOException, ClassNotFoundException {

        RefAddr refAddr = ref.get("dataSourceName");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDataSourceName(refAddr.getContent().toString());
        }

        refAddr = ref.get("description");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDescription(refAddr.getContent().toString());
        }

        refAddr = ref.get("jndiEnvironment");
        if (refAddr != null && refAddr.getContent() != null) {
            final byte[] serialized = (byte[]) refAddr.getContent();
            ikds.setJndiEnvironment((Properties) deserialize(serialized));
        }

        refAddr = ref.get("loginTimeout");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setLoginTimeout(Integer.parseInt(refAddr.getContent().toString()));
        }

        // Pool properties
        refAddr = ref.get("blockWhenExhausted");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultBlockWhenExhausted(Boolean.valueOf(refAddr.getContent().toString()).booleanValue());
        }

        refAddr = ref.get("evictionPolicyClassName");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultEvictionPolicyClassName(refAddr.getContent().toString());
        }

        // Pool properties
        refAddr = ref.get("lifo");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultLifo(Boolean.valueOf(refAddr.getContent().toString()).booleanValue());
        }

        refAddr = ref.get("maxIdlePerKey");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultMaxIdle(Integer.parseInt(refAddr.getContent().toString()));
        }

        refAddr = ref.get("maxTotalPerKey");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultMaxTotal(Integer.parseInt(refAddr.getContent().toString()));
        }

        refAddr = ref.get("maxWaitMillis");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultMaxWaitMillis(Long.parseLong(refAddr.getContent().toString()));
        }

        refAddr = ref.get("minEvictableIdleTimeMillis");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultMinEvictableIdleTimeMillis(Long.parseLong(refAddr.getContent().toString()));
        }

        refAddr = ref.get("minIdlePerKey");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultMinIdle(Integer.parseInt(refAddr.getContent().toString()));
        }

        refAddr = ref.get("numTestsPerEvictionRun");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultNumTestsPerEvictionRun(Integer.parseInt(refAddr.getContent().toString()));
        }

        refAddr = ref.get("softMinEvictableIdleTimeMillis");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultSoftMinEvictableIdleTimeMillis(Long.parseLong(refAddr.getContent().toString()));
        }

        refAddr = ref.get("testOnCreate");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultTestOnCreate(Boolean.valueOf(refAddr.getContent().toString()).booleanValue());
        }

        refAddr = ref.get("testOnBorrow");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultTestOnBorrow(Boolean.valueOf(refAddr.getContent().toString()).booleanValue());
        }

        refAddr = ref.get("testOnReturn");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultTestOnReturn(Boolean.valueOf(refAddr.getContent().toString()).booleanValue());
        }

        refAddr = ref.get("testWhileIdle");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultTestWhileIdle(Boolean.valueOf(refAddr.getContent().toString()).booleanValue());
        }

        refAddr = ref.get("timeBetweenEvictionRunsMillis");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultTimeBetweenEvictionRunsMillis(Long.parseLong(refAddr.getContent().toString()));
        }

        // Connection factory properties

        refAddr = ref.get("validationQuery");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setValidationQuery(refAddr.getContent().toString());
        }

        refAddr = ref.get("validationQueryTimeout");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setValidationQueryTimeout(Integer.parseInt(refAddr.getContent().toString()));
        }

        refAddr = ref.get("rollbackAfterValidation");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setRollbackAfterValidation(Boolean.valueOf(refAddr.getContent().toString()).booleanValue());
        }

        refAddr = ref.get("maxConnLifetimeMillis");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setMaxConnLifetimeMillis(Long.parseLong(refAddr.getContent().toString()));
        }

        // Connection properties

        refAddr = ref.get("defaultAutoCommit");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultAutoCommit(Boolean.valueOf(refAddr.getContent().toString()));
        }

        refAddr = ref.get("defaultTransactionIsolation");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultTransactionIsolation(Integer.parseInt(refAddr.getContent().toString()));
        }

        refAddr = ref.get("defaultReadOnly");
        if (refAddr != null && refAddr.getContent() != null) {
            ikds.setDefaultReadOnly(Boolean.valueOf(refAddr.getContent().toString()));
        }
    }

    /**
     * @param className
     *            The class name to test.
     *
     * @return true if and only if className is the value returned from getClass().getName().toString()
     */
    protected abstract boolean isCorrectClass(String className);

    /**
     * Creates an instance of the subclass and sets any properties contained in the Reference.
     *
     * @param ref
     *            The properties to be set on the created DataSource
     *
     * @return A configured DataSource of the appropriate type.
     *
     * @throws ClassNotFoundException
     *            If a class cannot be found during the deserialization of a configuration parameter.
     * @throws IOException
     *            If an I/O error occurs during the deserialization of a configuration parameter.
     */
    protected abstract InstanceKeyDataSource getNewInstance(Reference ref) throws IOException, ClassNotFoundException;

    /**
     * Deserializes the provided byte array to create an object.
     *
     * @param data
     *            Data to deserialize to create the configuration parameter.
     *
     * @return The Object created by deserializing the data.
     *
     * @throws ClassNotFoundException
     *            If a class cannot be found during the deserialization of a configuration parameter.
     * @throws IOException
     *            If an I/O error occurs during the deserialization of a configuration parameter.
     */
    protected static final Object deserialize(final byte[] data) throws IOException, ClassNotFoundException {
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(new ByteArrayInputStream(data));
            return in.readObject();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (final IOException ex) {
                    // ignore
                }
            }
        }
    }
}
