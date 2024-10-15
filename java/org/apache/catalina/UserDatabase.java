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
package org.apache.catalina;

import java.util.Iterator;

/**
 * Abstract representation of a database of {@link User}s and {@link Group}s that can be maintained by an application,
 * along with definitions of corresponding {@link Role}s, and referenced by a {@link Realm} for authentication and
 * access control.
 *
 * @author Craig R. McClanahan
 *
 * @since 4.1
 */
public interface UserDatabase {

    // ------------------------------------------------------------- Properties

    /**
     * @return the set of {@link Group}s defined in this user database.
     */
    Iterator<Group> getGroups();


    /**
     * @return the unique global identifier of this user database.
     */
    String getId();


    /**
     * @return the set of {@link Role}s defined in this user database.
     */
    Iterator<Role> getRoles();


    /**
     * @return the set of {@link User}s defined in this user database.
     */
    Iterator<User> getUsers();


    // --------------------------------------------------------- Public Methods

    /**
     * Finalize access to this user database.
     *
     * @exception Exception if any exception is thrown during closing
     */
    void close() throws Exception;


    /**
     * Create and return a new {@link Group} defined in this user database.
     *
     * @param groupname   The group name of the new group (must be unique)
     * @param description The description of this group
     *
     * @return The new group
     */
    Group createGroup(String groupname, String description);


    /**
     * Create and return a new {@link Role} defined in this user database.
     *
     * @param rolename    The role name of the new role (must be unique)
     * @param description The description of this role
     *
     * @return The new role
     */
    Role createRole(String rolename, String description);


    /**
     * Create and return a new {@link User} defined in this user database.
     *
     * @param username The logon username of the new user (must be unique)
     * @param password The logon password of the new user
     * @param fullName The full name of the new user
     *
     * @return The new user
     */
    User createUser(String username, String password, String fullName);


    /**
     * @return the {@link Group} with the specified group name, if any; otherwise return <code>null</code>.
     *
     * @param groupname Name of the group to return
     */
    Group findGroup(String groupname);


    /**
     * @return the {@link Role} with the specified role name, if any; otherwise return <code>null</code>.
     *
     * @param rolename Name of the role to return
     */
    Role findRole(String rolename);


    /**
     * @return the {@link User} with the specified user name, if any; otherwise return <code>null</code>.
     *
     * @param username Name of the user to return
     */
    User findUser(String username);


    /**
     * Initialize access to this user database.
     *
     * @exception Exception if any exception is thrown during opening
     */
    void open() throws Exception;


    /**
     * Remove the specified {@link Group} from this user database.
     *
     * @param group The group to be removed
     */
    void removeGroup(Group group);


    /**
     * Remove the specified {@link Role} from this user database.
     *
     * @param role The role to be removed
     */
    void removeRole(Role role);


    /**
     * Remove the specified {@link User} from this user database.
     *
     * @param user The user to be removed
     */
    void removeUser(User user);


    /**
     * Signal the specified {@link Group} from this user database has been modified.
     *
     * @param group The group that has been modified
     */
    default void modifiedGroup(Group group) {
    }


    /**
     * Signal the specified {@link Role} from this user database has been modified.
     *
     * @param role The role that has been modified
     */
    default void modifiedRole(Role role) {
    }


    /**
     * Signal the specified {@link User} from this user database has been modified.
     *
     * @param user The user that has been modified
     */
    default void modifiedUser(User user) {
    }


    /**
     * Save any updated information to the persistent storage location for this user database.
     *
     * @exception Exception if any exception is thrown during saving
     */
    void save() throws Exception;


    /**
     * Perform any background processing (e.g. checking for changes in persisted storage) required for the user
     * database.
     */
    default void backgroundProcess() {
        // NO-OP by default
    }


    /**
     * Is the database available.
     *
     * @return true
     */
    default boolean isAvailable() {
        return true;
    }


    /**
     * Is the database data loaded on demand. This is used to avoid eager loading of the full database data, for example
     * for JMX registration of all objects.
     *
     * @return false
     */
    default boolean isSparse() {
        return false;
    }
}
