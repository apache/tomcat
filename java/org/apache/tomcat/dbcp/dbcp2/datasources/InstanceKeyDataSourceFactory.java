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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.tomcat.dbcp.dbcp2.ListException;
import org.apache.tomcat.dbcp.dbcp2.Utils;

/**
 * A JNDI ObjectFactory which creates {@code SharedPoolDataSource}s or {@code PerUserPoolDataSource}s
 *
 * @since 2.0
 */
abstract class InstanceKeyDataSourceFactory implements ObjectFactory {

    private static final Map<String, InstanceKeyDataSource> INSTANCE_MAP = new ConcurrentHashMap<>();

    /**
     * Closes all pools associated with this class.
     *
     * @throws ListException
     *             a {@link ListException} containing all exceptions thrown by {@link InstanceKeyDataSource#close()}
     * @see InstanceKeyDataSource#close()
     * @since 2.4.0 throws a {@link ListException} instead of, in 2.3.0 and before, the first exception thrown by
     *        {@link InstanceKeyDataSource#close()}.
     */
    public static void closeAll() throws ListException {
        // Get iterator to loop over all instances of this data source.
        final List<Throwable> exceptionList = new ArrayList<>(INSTANCE_MAP.size());
        INSTANCE_MAP.entrySet().forEach(entry -> {
            // Bullet-proof to avoid anything else but problems from InstanceKeyDataSource#close().
            if (entry != null) {
                final InstanceKeyDataSource value = entry.getValue();
                Utils.close(value, exceptionList::add);
            }
        });
        INSTANCE_MAP.clear();
        if (!exceptionList.isEmpty()) {
            throw new ListException("Could not close all InstanceKeyDataSource instances.", exceptionList);
        }
    }

    /**
     * Deserializes the provided byte array to create an object.
     *
     * @param data
     *            Data to deserialize to create the configuration parameter.
     *
     * @return The Object created by deserializing the data.
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
            Utils.closeQuietly(in);
        }
    }

    static synchronized String registerNewInstance(final InstanceKeyDataSource ds) {
        int max = 0;
        for (final String s : INSTANCE_MAP.keySet()) {
            if (s != null) {
                try {
                    max = Math.max(max, Integer.parseInt(s));
                } catch (final NumberFormatException ignored) {
                    // no sweat, ignore those keys
                }
            }
        }
        final String instanceKey = String.valueOf(max + 1);
        // Put a placeholder here for now, so other instances will not
        // take our key. We will replace with a pool when ready.
        INSTANCE_MAP.put(instanceKey, ds);
        return instanceKey;
    }

    static void removeInstance(final String key) {
        if (key != null) {
            INSTANCE_MAP.remove(key);
        }
    }

    private Boolean booleanValueOf(RefAddr refAddr) {
        return Boolean.valueOf(toString(refAddr));
    }

    /**
     * Creates an instance of the subclass and sets any properties contained in the Reference.
     *
     * @param ref
     *            The properties to be set on the created DataSource
     *
     * @return A configured DataSource of the appropriate type.
     * @throws ClassNotFoundException
     *            If a class cannot be found during the deserialization of a configuration parameter.
     * @throws IOException
     *            If an I/O error occurs during the deserialization of a configuration parameter.
     */
    protected abstract InstanceKeyDataSource getNewInstance(Reference ref) throws IOException, ClassNotFoundException;

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
                if (hasContent(refAddr)) {
                    // object was bound to JNDI via Referenceable API.
                    obj = INSTANCE_MAP.get(refAddr.getContent());
                } else {
                    // Tomcat JNDI creates a Reference out of server.xml
                    // <ResourceParam> configuration and passes it to an
                    // instance of the factory given in server.xml.
                    String key = null;
                    if (name != null) {
                        key = name.toString();
                        obj = INSTANCE_MAP.get(key);
                    }
                    if (obj == null) {
                        final InstanceKeyDataSource ds = getNewInstance(ref);
                        setCommonProperties(ref, ds);
                        obj = ds;
                        if (key != null) {
                            INSTANCE_MAP.put(key, ds);
                        }
                    }
                }
            }
        }
        return obj;
    }

    private boolean hasContent(final RefAddr refAddr) {
        return refAddr != null && refAddr.getContent() != null;
    }

    /**
     * Tests if className is the value returned from getClass().getName().toString().
     *
     * @param className
     *            The class name to test.
     *
     * @return true if and only if className is the value returned from getClass().getName().toString()
     */
    protected abstract boolean isCorrectClass(String className);

    boolean parseBoolean(final RefAddr refAddr) {
        return Boolean.parseBoolean(toString(refAddr));
    }

    int parseInt(final RefAddr refAddr) {
        return Integer.parseInt(toString(refAddr));
    }

    long parseLong(final RefAddr refAddr) {
        return Long.parseLong(toString(refAddr));
    }

    private void setCommonProperties(final Reference ref, final InstanceKeyDataSource ikds)
            throws IOException, ClassNotFoundException {

        RefAddr refAddr = ref.get("dataSourceName");
        if (hasContent(refAddr)) {
            ikds.setDataSourceName(toString(refAddr));
        }

        refAddr = ref.get("description");
        if (hasContent(refAddr)) {
            ikds.setDescription(toString(refAddr));
        }

        refAddr = ref.get("jndiEnvironment");
        if (hasContent(refAddr)) {
            final byte[] serialized = (byte[]) refAddr.getContent();
            ikds.setJndiEnvironment((Properties) deserialize(serialized));
        }

        refAddr = ref.get("loginTimeout");
        if (hasContent(refAddr)) {
            ikds.setLoginTimeout(toDurationFromSeconds(refAddr));
        }

        // Pool properties
        refAddr = ref.get("blockWhenExhausted");
        if (hasContent(refAddr)) {
            ikds.setDefaultBlockWhenExhausted(parseBoolean(refAddr));
        }

        refAddr = ref.get("evictionPolicyClassName");
        if (hasContent(refAddr)) {
            ikds.setDefaultEvictionPolicyClassName(toString(refAddr));
        }

        // Pool properties
        refAddr = ref.get("lifo");
        if (hasContent(refAddr)) {
            ikds.setDefaultLifo(parseBoolean(refAddr));
        }

        refAddr = ref.get("maxIdlePerKey");
        if (hasContent(refAddr)) {
            ikds.setDefaultMaxIdle(parseInt(refAddr));
        }

        refAddr = ref.get("maxTotalPerKey");
        if (hasContent(refAddr)) {
            ikds.setDefaultMaxTotal(parseInt(refAddr));
        }

        refAddr = ref.get("maxWaitMillis");
        if (hasContent(refAddr)) {
            ikds.setDefaultMaxWait(toDurationFromMillis(refAddr));
        }

        refAddr = ref.get("minEvictableIdleTimeMillis");
        if (hasContent(refAddr)) {
            ikds.setDefaultMinEvictableIdle(toDurationFromMillis(refAddr));
        }

        refAddr = ref.get("minIdlePerKey");
        if (hasContent(refAddr)) {
            ikds.setDefaultMinIdle(parseInt(refAddr));
        }

        refAddr = ref.get("numTestsPerEvictionRun");
        if (hasContent(refAddr)) {
            ikds.setDefaultNumTestsPerEvictionRun(parseInt(refAddr));
        }

        refAddr = ref.get("softMinEvictableIdleTimeMillis");
        if (hasContent(refAddr)) {
            ikds.setDefaultSoftMinEvictableIdle(toDurationFromMillis(refAddr));
        }

        refAddr = ref.get("testOnCreate");
        if (hasContent(refAddr)) {
            ikds.setDefaultTestOnCreate(parseBoolean(refAddr));
        }

        refAddr = ref.get("testOnBorrow");
        if (hasContent(refAddr)) {
            ikds.setDefaultTestOnBorrow(parseBoolean(refAddr));
        }

        refAddr = ref.get("testOnReturn");
        if (hasContent(refAddr)) {
            ikds.setDefaultTestOnReturn(parseBoolean(refAddr));
        }

        refAddr = ref.get("testWhileIdle");
        if (hasContent(refAddr)) {
            ikds.setDefaultTestWhileIdle(parseBoolean(refAddr));
        }

        refAddr = ref.get("timeBetweenEvictionRunsMillis");
        if (hasContent(refAddr)) {
            ikds.setDefaultDurationBetweenEvictionRuns(toDurationFromMillis(refAddr));
        }

        // Connection factory properties

        refAddr = ref.get("validationQuery");
        if (hasContent(refAddr)) {
            ikds.setValidationQuery(toString(refAddr));
        }

        refAddr = ref.get("validationQueryTimeout");
        if (hasContent(refAddr)) {
            ikds.setValidationQueryTimeout(toDurationFromSeconds(refAddr));
        }

        refAddr = ref.get("rollbackAfterValidation");
        if (hasContent(refAddr)) {
            ikds.setRollbackAfterValidation(parseBoolean(refAddr));
        }

        refAddr = ref.get("maxConnLifetimeMillis");
        if (hasContent(refAddr)) {
            ikds.setMaxConnLifetime(toDurationFromMillis(refAddr));
        }

        // Connection properties

        refAddr = ref.get("defaultAutoCommit");
        if (hasContent(refAddr)) {
            ikds.setDefaultAutoCommit(booleanValueOf(refAddr));
        }

        refAddr = ref.get("defaultTransactionIsolation");
        if (hasContent(refAddr)) {
            ikds.setDefaultTransactionIsolation(parseInt(refAddr));
        }

        refAddr = ref.get("defaultReadOnly");
        if (hasContent(refAddr)) {
            ikds.setDefaultReadOnly(booleanValueOf(refAddr));
        }
    }

    private Duration toDurationFromMillis(RefAddr refAddr) {
        return Duration.ofMillis(parseLong(refAddr));
    }

    private Duration toDurationFromSeconds(RefAddr refAddr) {
        return Duration.ofSeconds(parseInt(refAddr));
    }

    String toString(final RefAddr refAddr) {
        return refAddr.getContent().toString();
    }
}
