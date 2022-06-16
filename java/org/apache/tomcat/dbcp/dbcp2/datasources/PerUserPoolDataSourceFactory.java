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
import java.time.Duration;
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

    @SuppressWarnings("unchecked") // Avoid warnings on deserialization
    @Override
    protected InstanceKeyDataSource getNewInstance(final Reference ref) throws IOException, ClassNotFoundException {
        final PerUserPoolDataSource pupds = new PerUserPoolDataSource();
        RefAddr refAddr = ref.get("defaultMaxTotal");
        if (refAddr != null && refAddr.getContent() != null) {
            pupds.setDefaultMaxTotal(parseInt(refAddr));
        }

        refAddr = ref.get("defaultMaxIdle");
        if (refAddr != null && refAddr.getContent() != null) {
            pupds.setDefaultMaxIdle(parseInt(refAddr));
        }

        refAddr = ref.get("defaultMaxWaitMillis");
        if (refAddr != null && refAddr.getContent() != null) {
            pupds.setDefaultMaxWait(Duration.ofMillis(parseInt(refAddr)));
        }

        refAddr = ref.get("perUserDefaultAutoCommit");
        if (refAddr != null && refAddr.getContent() != null) {
            final byte[] serialized = (byte[]) refAddr.getContent();
            pupds.setPerUserDefaultAutoCommit((Map<String, Boolean>) deserialize(serialized));
        }

        refAddr = ref.get("perUserDefaultTransactionIsolation");
        if (refAddr != null && refAddr.getContent() != null) {
            final byte[] serialized = (byte[]) refAddr.getContent();
            pupds.setPerUserDefaultTransactionIsolation((Map<String, Integer>) deserialize(serialized));
        }

        refAddr = ref.get("perUserMaxTotal");
        if (refAddr != null && refAddr.getContent() != null) {
            final byte[] serialized = (byte[]) refAddr.getContent();
            pupds.setPerUserMaxTotal((Map<String, Integer>) deserialize(serialized));
        }

        refAddr = ref.get("perUserMaxIdle");
        if (refAddr != null && refAddr.getContent() != null) {
            final byte[] serialized = (byte[]) refAddr.getContent();
            pupds.setPerUserMaxIdle((Map<String, Integer>) deserialize(serialized));
        }

        refAddr = ref.get("perUserMaxWaitMillis");
        if (refAddr != null && refAddr.getContent() != null) {
            final byte[] serialized = (byte[]) refAddr.getContent();
            pupds.setPerUserMaxWaitMillis((Map<String, Long>) deserialize(serialized));
        }

        refAddr = ref.get("perUserDefaultReadOnly");
        if (refAddr != null && refAddr.getContent() != null) {
            final byte[] serialized = (byte[]) refAddr.getContent();
            pupds.setPerUserDefaultReadOnly((Map<String, Boolean>) deserialize(serialized));
        }
        return pupds;
    }

    @Override
    protected boolean isCorrectClass(final String className) {
        return PER_USER_POOL_CLASSNAME.equals(className);
    }
}
