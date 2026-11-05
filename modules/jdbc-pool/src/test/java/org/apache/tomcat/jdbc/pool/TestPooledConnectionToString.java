/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
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

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A pooled connection is interpolated into pool warnings on the abandon,
 * suspect, reconnect and return paths. Its rendering must not use the driver's
 * own {@code toString()}, whose text can carry a credential or a newline.
 */
class TestPooledConnectionToString {

    @Test
    void theRenderingDoesNotUseTheDriverText() throws Exception {
        PoolProperties properties = new PoolProperties();
        properties.setInitialSize(0);
        properties.setJmxEnabled(false);
        ConnectionPool pool = new ConnectionPool(properties);
        try {
            PooledConnection pooled = new PooledConnection(properties, pool);
            Connection driverText = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class[] { Connection.class },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                        case "toString":
                            return "jdbc:h2:mem:x;password=s3cret\nforged log line";
                        case "hashCode":
                            return Integer.valueOf(System.identityHashCode(proxy));
                        case "equals":
                            return Boolean.valueOf(proxy == args[0]);
                        default:
                            return null;
                        }
                    });
            Field connection = PooledConnection.class.getDeclaredField("connection");
            connection.setAccessible(true);
            connection.set(pooled, driverText);

            String rendering = pooled.toString();

            Assertions.assertFalse(rendering.contains("s3cret"),
                    "the rendering must not carry a credential from the driver's toString()");
            Assertions.assertFalse(rendering.contains("\n"),
                    "the rendering must not carry a newline that could forge a log line");
            Assertions.assertTrue(rendering.startsWith("PooledConnection@"),
                    "the rendering must identify the pooled connection itself, was: " + rendering);
        } finally {
            pool.close(true);
        }
    }
}
