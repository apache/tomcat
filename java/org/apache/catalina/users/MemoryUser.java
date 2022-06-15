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
package org.apache.catalina.users;


import org.apache.catalina.UserDatabase;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.security.Escape;

/**
 * <p>Concrete implementation of {@link org.apache.catalina.User} for the
 * {@link MemoryUserDatabase} implementation of {@link UserDatabase}.</p>
 *
 * @author Craig R. McClanahan
 * @since 4.1
 */
public class MemoryUser extends GenericUser<MemoryUserDatabase> {


    /**
     * Package-private constructor used by the factory method in
     * {@link MemoryUserDatabase}.
     *
     * @param database The {@link MemoryUserDatabase} that owns this user
     * @param username Logon username of the new user
     * @param password Logon password of the new user
     * @param fullName Full name of the new user
     */
    MemoryUser(MemoryUserDatabase database, String username,
               String password, String fullName) {
        super(database, username, password, fullName, null, null);
    }


    /**
     * <p>Return a String representation of this user in XML format.</p>
     *
     * <p><strong>IMPLEMENTATION NOTE</strong> - For backwards compatibility,
     * the reader that processes this entry will accept either
     * <code>username</code> or <code>name</code> for the username
     * property.</p>
     * @return the XML representation
     */
    public String toXml() {

        StringBuilder sb = new StringBuilder("<user username=\"");
        sb.append(Escape.xml(username));
        sb.append("\" password=\"");
        sb.append(Escape.xml(password));
        sb.append("\"");
        if (fullName != null) {
            sb.append(" fullName=\"");
            sb.append(Escape.xml(fullName));
            sb.append("\"");
        }
        sb.append(" groups=\"");
        StringUtils.join(groups, ',', (x) -> Escape.xml(x.getGroupname()), sb);
        sb.append("\"");
        sb.append(" roles=\"");
        StringUtils.join(roles, ',', (x) -> Escape.xml(x.getRolename()), sb);
        sb.append("\"");
        sb.append("/>");
        return sb.toString();
    }


    /**
     * <p>Return a String representation of this user.</p>
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("User username=\"");
        sb.append(Escape.xml(username));
        sb.append("\"");
        if (fullName != null) {
            sb.append(", fullName=\"");
            sb.append(Escape.xml(fullName));
            sb.append("\"");
        }
        sb.append(", groups=\"");
        StringUtils.join(groups, ',', (x) -> Escape.xml(x.getGroupname()), sb);
        sb.append("\"");
        sb.append(", roles=\"");
        StringUtils.join(roles, ',', (x) -> Escape.xml(x.getRolename()), sb);
        sb.append("\"");
        return sb.toString();
    }
}
