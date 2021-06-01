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

import java.io.IOException;
import java.util.Map;

import javax.naming.RefAddr;
import javax.naming.Reference;

/**
 * A JNDI ObjectFactory which creates <code>SharedPoolDataSource</code>s
 *
 * @since 2.0
 */
public class PerUserPoolDataSourceFactory extends InstanceKeyDataSourceFactory {
    private static final String PER_USER_POOL_CLASSNAME = PerUserPoolDataSource.class.getName();

    @Override
    protected boolean isCorrectClass(final String className) {
        return PER_USER_POOL_CLASSNAME.equals(className);
    }

    @SuppressWarnings("unchecked") // Avoid warnings on deserialization
    @Override
    protected InstanceKeyDataSource getNewInstance(final Reference ref) throws IOException, ClassNotFoundException {
        final PerUserPoolDataSource pupds = new PerUserPoolDataSource();
        RefAddr ra = ref.get("defaultMaxTotal");
        if (ra != null && ra.getContent() != null) {
            pupds.setDefaultMaxTotal(Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("defaultMaxIdle");
        if (ra != null && ra.getContent() != null) {
            pupds.setDefaultMaxIdle(Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("defaultMaxWaitMillis");
        if (ra != null && ra.getContent() != null) {
            pupds.setDefaultMaxWaitMillis(Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("perUserDefaultAutoCommit");
        if (ra != null && ra.getContent() != null) {
            final byte[] serialized = (byte[]) ra.getContent();
            pupds.setPerUserDefaultAutoCommit((Map<String, Boolean>) deserialize(serialized));
        }

        ra = ref.get("perUserDefaultTransactionIsolation");
        if (ra != null && ra.getContent() != null) {
            final byte[] serialized = (byte[]) ra.getContent();
            pupds.setPerUserDefaultTransactionIsolation((Map<String, Integer>) deserialize(serialized));
        }

        ra = ref.get("perUserMaxTotal");
        if (ra != null && ra.getContent() != null) {
            final byte[] serialized = (byte[]) ra.getContent();
            pupds.setPerUserMaxTotal((Map<String, Integer>) deserialize(serialized));
        }

        ra = ref.get("perUserMaxIdle");
        if (ra != null && ra.getContent() != null) {
            final byte[] serialized = (byte[]) ra.getContent();
            pupds.setPerUserMaxIdle((Map<String, Integer>) deserialize(serialized));
        }

        ra = ref.get("perUserMaxWaitMillis");
        if (ra != null && ra.getContent() != null) {
            final byte[] serialized = (byte[]) ra.getContent();
            pupds.setPerUserMaxWaitMillis((Map<String, Long>) deserialize(serialized));
        }

        ra = ref.get("perUserDefaultReadOnly");
        if (ra != null && ra.getContent() != null) {
            final byte[] serialized = (byte[]) ra.getContent();
            pupds.setPerUserDefaultReadOnly((Map<String, Boolean>) deserialize(serialized));
        }
        return pupds;
    }
}
