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

import org.apache.tomcat.dbcp.dbcp2.Utils;

/**
 * <p>
 * Holds a user name and password pair. Serves as a poolable object key for the KeyedObjectPool backing a
 * SharedPoolDataSource. Two instances with the same user name are considered equal. This ensures that there will be
 * only one keyed pool for each user in the pool. The password is used (along with the user name) by the
 * KeyedCPDSConnectionFactory when creating new connections.
 * </p>
 *
 * <p>
 * {@link InstanceKeyDataSource#getConnection(String, String)} validates that the password used to create a connection
 * matches the password provided by the client.
 * </p>
 *
 * @since 2.0
 */
class UserPassKey implements Serializable {
    private static final long serialVersionUID = 5142970911626584817L;
    private final String userName;
    private final char[] userPassword;

    /**
     * @since 2.4.0
     */
    UserPassKey(final String userName) {
        this(userName, (char[]) null);
    }

    /**
     * @since 2.4.0
     */
    UserPassKey(final String userName, final char[] password) {
        this.userName = userName;
        this.userPassword = password;
    }

    UserPassKey(final String userName, final String userPassword) {
        this(userName, Utils.toCharArray(userPassword));
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
        if (userName == null) {
            if (other.userName != null) {
                return false;
            }
        } else if (!userName.equals(other.userName)) {
            return false;
        }
        return true;
    }

    /**
     * Gets the value of password.
     *
     * @return value of password.
     */
    public String getPassword() {
        return Utils.toString(userPassword);
    }

    /**
     * Gets the value of password.
     *
     * @return value of password.
     */
    public char[] getPasswordCharArray() {
        return userPassword;
    }

    /**
     * Gets the value of user name.
     *
     * @return value of user name.
     */
    public String getUsername() {
        return userName;
    }

    /**
     * Only takes the user name into account.
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((userName == null) ? 0 : userName.hashCode());
        return result;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer(50);
        sb.append("UserPassKey(");
        sb.append(userName).append(", ").append(userPassword).append(')');
        return sb.toString();
    }
}
