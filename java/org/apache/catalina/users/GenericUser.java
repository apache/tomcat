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


import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.UserDatabase;

/**
 * <p>Concrete implementation of {@link org.apache.catalina.User} for a
 * {@link UserDatabase}.</p>
 *
 * @param <UD> The specific type of UserDase with which this role is associated
 *
 * @author Craig R. McClanahan
 */
public class GenericUser<UD extends UserDatabase> extends AbstractUser {


    // ----------------------------------------------------------- Constructors


    /**
     * Package-private constructor used by the factory method in
     * {@link UserDatabase}.
     *
     * @param database The {@link UserDatabase} that owns this user
     * @param username Logon username of the new user
     * @param password Logon password of the new user
     * @param fullName Full name of the new user
     * @param groups The groups of this user
     * @param roles The roles of this user
     */
    GenericUser(UD database, String username,
               String password, String fullName, List<Group> groups,
               List<Role> roles) {

        super();
        this.database = database;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        if (groups != null) {
            this.groups.addAll(groups);
        }
        if (roles != null) {
            this.roles.addAll(roles);
        }

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The {@link UserDatabase} that owns this user.
     */
    protected final UD database;


    /**
     * The set of {@link Group}s that this user is a member of.
     */
    protected final CopyOnWriteArrayList<Group> groups = new CopyOnWriteArrayList<>();


    /**
     * The set of {@link Role}s associated with this user.
     */
    protected final CopyOnWriteArrayList<Role> roles = new CopyOnWriteArrayList<>();


    // ------------------------------------------------------------- Properties


    /**
     * Return the set of {@link Group}s to which this user belongs.
     */
    @Override
    public Iterator<Group> getGroups() {
        return groups.iterator();
    }


    /**
     * Return the set of {@link Role}s assigned specifically to this user.
     */
    @Override
    public Iterator<Role> getRoles() {
        return roles.iterator();
    }


    /**
     * Return the {@link UserDatabase} within which this User is defined.
     */
    @Override
    public UserDatabase getUserDatabase() {
        return this.database;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add a new {@link Group} to those this user belongs to.
     *
     * @param group The new group
     */
    @Override
    public void addGroup(Group group) {
        if (groups.addIfAbsent(group)) {
            database.modifiedUser(this);
        }
    }


    /**
     * Add a new {@link Role} to those assigned specifically to this user.
     *
     * @param role The new role
     */
    @Override
    public void addRole(Role role) {
        if (roles.addIfAbsent(role)) {
            database.modifiedUser(this);
        }
    }


    /**
     * Is this user in the specified group?
     *
     * @param group The group to check
     */
    @Override
    public boolean isInGroup(Group group) {
        return groups.contains(group);
    }


    /**
     * Is this user specifically assigned the specified {@link Role}?  This
     * method does <strong>NOT</strong> check for roles inherited based on
     * {@link Group} membership.
     *
     * @param role The role to check
     */
    @Override
    public boolean isInRole(Role role) {
        return roles.contains(role);
    }


    /**
     * Remove a {@link Group} from those this user belongs to.
     *
     * @param group The old group
     */
    @Override
    public void removeGroup(Group group) {
        if (groups.remove(group)) {
            database.modifiedUser(this);
        }
    }


    /**
     * Remove all {@link Group}s from those this user belongs to.
     */
    @Override
    public void removeGroups() {
        if (!groups.isEmpty()) {
            groups.clear();
            database.modifiedUser(this);
        }
    }


    /**
     * Remove a {@link Role} from those assigned to this user.
     *
     * @param role The old role
     */
    @Override
    public void removeRole(Role role) {
        if (roles.remove(role)) {
            database.modifiedUser(this);
        }
    }


    /**
     * Remove all {@link Role}s from those assigned to this user.
     */
    @Override
    public void removeRoles() {
        if (!roles.isEmpty()) {
            database.modifiedUser(this);
        }
        roles.clear();
    }


    @Override
    public void setFullName(String fullName) {
        database.modifiedUser(this);
        super.setFullName(fullName);
    }


    @Override
    public void setPassword(String password) {
        database.modifiedUser(this);
        super.setPassword(password);
    }


    @Override
    public void setUsername(String username) {
        database.modifiedUser(this);
        // Note: changing the user name is a problem ...
        super.setUsername(username);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GenericUser) {
            GenericUser<?> user = (GenericUser<?>) obj;
            return user.database == database && username.equals(user.getUsername());
        }
        return super.equals(obj);
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((database == null) ? 0 : database.hashCode());
        result = prime * result + ((username == null) ? 0 : username.hashCode());
        return result;
    }
}
