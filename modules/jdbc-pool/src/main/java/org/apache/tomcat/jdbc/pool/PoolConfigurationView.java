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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.pool;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;

/**
 * The live configuration surface of a running pool.
 * <p>
 * Reads are answered from the mutable builder the bean and JMX surfaces have always written, so existing callers keep
 * seeing the values their setters stored. Writes are applied to that builder, then handed to the pool's single
 * {@link ConnectionPool#applyConfigurationChange(PoolConfiguration)} path, which validates the candidate and
 * publishes the immutable snapshot the connect and credential paths read. A mutable value handed out by a getter is
 * a copy: changing it does not reconfigure the pool.
 * <p>
 * Start-time properties are rejected by the bean and JMX setters; a direct write here changes only the reported
 * value, not the pool shape already fixed at creation.
 */
final class PoolConfigurationView {

    private PoolConfigurationView() {
        // Utility class
    }

    /**
     * Creates the live configuration view for a pool.
     *
     * @param pool the pool the view belongs to
     *
     * @return the view
     */
    static PoolConfiguration create(ConnectionPool pool) {
        return (PoolConfiguration) Proxy.newProxyInstance(
                PoolConfiguration.class.getClassLoader(),
                new Class<?>[] { PoolConfiguration.class }, new Handler(pool));
    }

    private static final class Handler implements InvocationHandler {

        private final ConnectionPool pool;

        Handler(ConnectionPool pool) {
            this.pool = pool;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("toString".equals(name)) {
                return pool.getCurrentConfiguration().toString();
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(proxy == args[0]);
            }

            if (name.startsWith("set")) {
                // A read-modify-write of the builder has to be serialized with
                // every other writer, and the pool's monitor is the one both
                // management surfaces reach.
                synchronized (pool) {
                    PoolConfiguration mutable = pool.getMutableConfiguration();
                    invokeTarget(method, mutable, args);
                    pool.applyConfigurationChange(mutable);
                }
                return defaultValue(method.getReturnType());
            }

            PoolConfiguration current = pool.getMutableConfiguration();
            Object result = invokeTarget(method, current, args);
            // A mutable value handed out by a getter would let its holder
            // bypass the apply path and mutate the snapshot behind the pool's
            // back. The configuration getter contract is still met: callers
            // see the values, not the pool's internal collection.
            if ("getDbProperties".equals(name) && result instanceof Properties) {
                return PoolUtilities.clone((Properties) result);
            }
            if ("getJdbcInterceptorsAsArray".equals(name)
                    && result instanceof PoolProperties.InterceptorDefinition[]) {
                // A shallow array clone would share the live definitions with
                // the builder, letting a caller add or replace an interceptor
                // property behind the apply path's back. Deep-copy the
                // definitions instead, keeping the configured class name and
                // property values but sharing no mutable state.
                PoolProperties.InterceptorDefinition[] source =
                        (PoolProperties.InterceptorDefinition[]) result;
                PoolProperties.InterceptorDefinition[] copies =
                        new PoolProperties.InterceptorDefinition[source.length];
                for (int i = 0; i < source.length; i++) {
                    copies[i] = source[i] == null ? null : source[i].copy();
                }
                return copies;
            }
            return result;
        }

        private static Object invokeTarget(Method method, Object target, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException x) {
                throw x.getCause();
            }
        }

        private static Object defaultValue(Class<?> type) {
            if (type == boolean.class) {
                return Boolean.FALSE;
            }
            if (type == int.class) {
                return Integer.valueOf(0);
            }
            if (type == long.class) {
                return Long.valueOf(0);
            }
            return null;
        }
    }
}
