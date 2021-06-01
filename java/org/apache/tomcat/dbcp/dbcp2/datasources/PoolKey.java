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

/**
 * @since 2.0
 */
class PoolKey implements Serializable {
    private static final long serialVersionUID = 2252771047542484533L;

    private final String dataSourceName;
    private final String userName;

    PoolKey(final String dataSourceName, final String userName) {
        this.dataSourceName = dataSourceName;
        this.userName = userName;
    }

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
        final PoolKey other = (PoolKey) obj;
        if (!Objects.equals(dataSourceName, other.dataSourceName)) {
            return false;
        }
        return Objects.equals(userName, other.userName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSourceName, userName);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(50);
        sb.append("PoolKey(");
        sb.append("UserName").append(", ").append(dataSourceName);
        sb.append(')');
        return sb.toString();
    }
}
