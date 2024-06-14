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


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;


/**
 * <p>
 * Concrete implementation of {@link org.apache.catalina.Group} for a {@link UserDatabase}.
 * </p>
 *
 * @param <UD> The specific type of UserDase with which this group is associated
 *
 * @author Craig R. McClanahan
 */
public class GenericGroup<UD extends UserDatabase> extends AbstractGroup {


    // ----------------------------------------------------------- Constructors


    /**
     * Package-private constructor used by the factory method in {@link UserDatabase}.
     *
     * @param database    The {@link UserDatabase} that owns this group
     * @param groupname   Group name of this group
     * @param description Description of this group
     * @param roles       The roles of this group
     */
    GenericGroup(UD database, String groupname, String description, List<Role> roles) {

        super();
        this.database = database;
        this.groupname = groupname;
        this.description = description;
        if (roles != null) {
            this.roles.addAll(roles);
        }

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The {@link UserDatabase} that owns this group.
     */
    protected final UD database;


    /**
     * The set of {@link Role}s associated with this group.
     */
    protected final CopyOnWriteArrayList<Role> roles = new CopyOnWriteArrayList<>();


    // ------------------------------------------------------------- Properties


    @Override
    public Iterator<Role> getRoles() {
        return roles.iterator();
    }


    @Override
    public UserDatabase getUserDatabase() {
        return this.database;
    }


    @Override
    public Iterator<User> getUsers() {
        List<User> results = new ArrayList<>();
        Iterator<User> users = database.getUsers();
        while (users.hasNext()) {
            User user = users.next();
            if (user.isInGroup(this)) {
                results.add(user);
            }
        }
        return results.iterator();
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public void addRole(Role role) {
        if (roles.addIfAbsent(role)) {
            database.modifiedGroup(this);
        }
    }


    @Override
    public boolean isInRole(Role role) {
        return roles.contains(role);
    }


    @Override
    public void removeRole(Role role) {
        if (roles.remove(role)) {
            database.modifiedGroup(this);
        }
    }


    @Override
    public void removeRoles() {
        if (!roles.isEmpty()) {
            roles.clear();
            database.modifiedGroup(this);
        }
    }


    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GenericGroup) {
            GenericGroup<?> group = (GenericGroup<?>) obj;
            return group.database == database && groupname.equals(group.getGroupname());
        }
        return super.equals(obj);
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((database == null) ? 0 : database.hashCode());
        result = prime * result + ((groupname == null) ? 0 : groupname.hashCode());
        return result;
    }
}
