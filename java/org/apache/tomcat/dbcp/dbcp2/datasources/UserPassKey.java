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

import java.io.Serializable;
import java.util.Objects;

import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;

/**
 * <p>
 * Holds a user name and password pair. Serves as a poolable object key for the {@link KeyedObjectPool} backing a
 * {@link SharedPoolDataSource}. Two instances with the same user name are considered equal. This ensures that there
 * will be only one keyed pool for each user in the pool. The password is used (along with the user name) by the
 * {@code KeyedCPDSConnectionFactory} when creating new connections.
 * </p>
 *
 * <p>
 * {@link InstanceKeyDataSource#getConnection(String, String)} validates that the password used to create a connection
 * matches the password provided by the client.
 * </p>
 *
 * @since 2.0
 */
final class UserPassKey implements Serializable {
    private static final long serialVersionUID = 5142970911626584817L;

    private final CharArray name;
    private final CharArray password;

    UserPassKey(final CharArray userName, final CharArray userPassword) {
        this.name = userName;
        this.password = userPassword;
    }

    UserPassKey(final String userName) {
        this(new CharArray(userName), CharArray.NULL);
    }

    UserPassKey(final String userName, final char[] password) {
        this(new CharArray(userName), new CharArray(password));
    }

    UserPassKey(final String userName, final String userPassword) {
        this(new CharArray(userName), new CharArray(userPassword));
    }

    /**
     * Only takes the user name into account.
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final UserPassKey other = (UserPassKey) obj;
        return Objects.equals(name, other.name);
    }

    /**
     * Gets the value of password.
     *
     * @return value of password.
     */
    String getPassword() {
        return password.asString();
    }

    /**
     * Gets the value of password.
     *
     * @return value of password.
     */
    char[] getPasswordCharArray() {
        return password.get();
    }

    /**
     * Gets the value of user name.
     *
     * @return value of user name.
     */
    String getUserName() {
        return name.asString();
    }

    /**
     * Only takes the user name into account.
     */
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

}
