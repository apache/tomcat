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

package org.apache.tomcat.dbcp.dbcp.datasources;

import java.io.Serializable;

/**
 */
class PoolKey implements Serializable {
    private static final long serialVersionUID = 2252771047542484533L;

    private final String datasourceName;
    private final String username;

    PoolKey(String datasourceName, String username) {
        this.datasourceName = datasourceName;
        this.username = username;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PoolKey) {
            PoolKey pk = (PoolKey)obj;
            return (null == datasourceName ? null == pk.datasourceName : datasourceName.equals(pk.datasourceName)) &&
                (null == username ? null == pk.username : username.equals(pk.username));
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int h = 0;
        if (datasourceName != null) {
            h += datasourceName.hashCode();
        }
        if (username != null) {
            h = 29 * h + username.hashCode();
        }
        return h;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer(50);
        sb.append("PoolKey(");
        sb.append(username).append(", ").append(datasourceName);
        sb.append(')');
        return sb.toString();
    }
}
