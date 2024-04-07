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
package org.apache.catalina.mbeans;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.tomcat.util.modeler.BaseModelMBean;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;

/**
 * <p>
 * A <strong>ModelMBean</strong> implementation for the <code>org.apache.catalina.users.DataSourceUserDatabase</code>
 * component.
 * </p>
 *
 * @author Craig R. McClanahan
 */
public class DataSourceUserDatabaseMBean extends BaseModelMBean {

    // ----------------------------------------------------- Instance Variables

    /**
     * The configuration information registry for our managed beans.
     */
    protected final Registry registry = MBeanUtils.createRegistry();


    /**
     * The <code>ManagedBean</code> information describing this MBean.
     */
    protected final ManagedBean managed = registry.findManagedBean("DataSourceUserDatabase");


    // ------------------------------------------------------------- Attributes

    /**
     * @return the names of all groups defined in this database.
     */
    public String[] getGroups() {
        UserDatabase database = (UserDatabase) this.resource;
        List<String> results = new ArrayList<>();
        Iterator<Group> groups = database.getGroups();
        while (groups.hasNext()) {
            Group group = groups.next();
            results.add(group.getGroupname());
        }
        return results.toArray(new String[0]);
    }


    /**
     * @return the names of all roles defined in this database.
     */
    public String[] getRoles() {
        UserDatabase database = (UserDatabase) this.resource;
        List<String> results = new ArrayList<>();
        Iterator<Role> roles = database.getRoles();
        while (roles.hasNext()) {
            Role role = roles.next();
            results.add(role.getRolename());
        }
        return results.toArray(new String[0]);
    }


    /**
     * @return the names of all users defined in this database.
     */
    public String[] getUsers() {
        UserDatabase database = (UserDatabase) this.resource;
        List<String> results = new ArrayList<>();
        Iterator<User> users = database.getUsers();
        while (users.hasNext()) {
            User user = users.next();
            results.add(user.getUsername());
        }
        return results.toArray(new String[0]);
    }


    // ------------------------------------------------------------- Operations

    /**
     * Create a new Group and return the corresponding name.
     *
     * @param groupname   Group name of the new group
     * @param description Description of the new group
     *
     * @return the new group name
     */
    public String createGroup(String groupname, String description) {
        UserDatabase database = (UserDatabase) this.resource;
        Group group = database.createGroup(groupname, description);
        return group.getGroupname();
    }


    /**
     * Create a new Role and return the corresponding name.
     *
     * @param rolename    Group name of the new group
     * @param description Description of the new group
     *
     * @return the new role name
     */
    public String createRole(String rolename, String description) {
        UserDatabase database = (UserDatabase) this.resource;
        Role role = database.createRole(rolename, description);
        return role.getRolename();
    }


    /**
     * Create a new User and return the corresponding name.
     *
     * @param username User name of the new user
     * @param password Password for the new user
     * @param fullName Full name for the new user
     *
     * @return the new user name
     */
    public String createUser(String username, String password, String fullName) {
        UserDatabase database = (UserDatabase) this.resource;
        User user = database.createUser(username, password, fullName);
        return user.getUsername();
    }


    /**
     * Remove an existing group.
     *
     * @param groupname Group name to remove
     */
    public void removeGroup(String groupname) {
        UserDatabase database = (UserDatabase) this.resource;
        Group group = database.findGroup(groupname);
        if (group == null) {
            return;
        }
        database.removeGroup(group);
    }


    /**
     * Remove an existing role.
     *
     * @param rolename Role name to remove
     */
    public void removeRole(String rolename) {
        UserDatabase database = (UserDatabase) this.resource;
        Role role = database.findRole(rolename);
        if (role == null) {
            return;
        }
        database.removeRole(role);
    }


    /**
     * Remove an existing user.
     *
     * @param username User name to remove
     */
    public void removeUser(String username) {
        UserDatabase database = (UserDatabase) this.resource;
        User user = database.findUser(username);
        if (user == null) {
            return;
        }
        database.removeUser(user);
    }


    /**
     * Change user credentials.
     *
     * @param username The user name
     * @param password The new credentials
     */
    public void changeUserPassword(String username, String password) {
        UserDatabase database = (UserDatabase) this.resource;
        User user = database.findUser(username);
        if (user != null) {
            user.setPassword(password);
        }
    }


    /**
     * Add specified role to the user.
     *
     * @param username The user name
     * @param rolename The role name
     */
    public void addUserRole(String username, String rolename) {
        UserDatabase database = (UserDatabase) this.resource;
        User user = database.findUser(username);
        Role role = database.findRole(rolename);
        if (user != null && role != null) {
            user.addRole(role);
        }
    }


    /**
     * Remove specified role from the user.
     *
     * @param username The user name
     * @param rolename The role name
     */
    public void removeUserRole(String username, String rolename) {
        UserDatabase database = (UserDatabase) this.resource;
        User user = database.findUser(username);
        Role role = database.findRole(rolename);
        if (user != null && role != null) {
            user.removeRole(role);
        }
    }


    /**
     * Get roles for a user.
     *
     * @param username The user name
     *
     * @return Array of role names
     */
    public String[] getUserRoles(String username) {
        UserDatabase database = (UserDatabase) this.resource;
        User user = database.findUser(username);
        if (user != null) {
            List<String> results = new ArrayList<>();
            Iterator<Role> roles = user.getRoles();
            while (roles.hasNext()) {
                Role role = roles.next();
                results.add(role.getRolename());
            }
            return results.toArray(new String[0]);
        } else {
            return null;
        }
    }


    /**
     * Add group to user.
     *
     * @param username  The user name
     * @param groupname The group name
     */
    public void addUserGroup(String username, String groupname) {
        UserDatabase database = (UserDatabase) this.resource;
        User user = database.findUser(username);
        Group group = database.findGroup(groupname);
        if (user != null && group != null) {
            user.addGroup(group);
        }
    }


    /**
     * Remove group from user.
     *
     * @param username  The user name
     * @param groupname The group name
     */
    public void removeUserGroup(String username, String groupname) {
        UserDatabase database = (UserDatabase) this.resource;
        User user = database.findUser(username);
        Group group = database.findGroup(groupname);
        if (user != null && group != null) {
            user.removeGroup(group);
        }
    }


    /**
     * Get groups for a user.
     *
     * @param username The user name
     *
     * @return Array of group names
     */
    public String[] getUserGroups(String username) {
        UserDatabase database = (UserDatabase) this.resource;
        User user = database.findUser(username);
        if (user != null) {
            List<String> results = new ArrayList<>();
            Iterator<Group> groups = user.getGroups();
            while (groups.hasNext()) {
                Group group = groups.next();
                results.add(group.getGroupname());
            }
            return results.toArray(new String[0]);
        } else {
            return null;
        }
    }


    /**
     * Add role to a group.
     *
     * @param groupname The group name
     * @param rolename  The role name
     */
    public void addGroupRole(String groupname, String rolename) {
        UserDatabase database = (UserDatabase) this.resource;
        Group group = database.findGroup(groupname);
        Role role = database.findRole(rolename);
        if (group != null && role != null) {
            group.addRole(role);
        }
    }


    /**
     * Remove role from a group.
     *
     * @param groupname The group name
     * @param rolename  The role name
     */
    public void removeGroupRole(String groupname, String rolename) {
        UserDatabase database = (UserDatabase) this.resource;
        Group group = database.findGroup(groupname);
        Role role = database.findRole(rolename);
        if (group != null && role != null) {
            group.removeRole(role);
        }
    }


    /**
     * Get roles for a group.
     *
     * @param groupname The group name
     *
     * @return Array of role names
     */
    public String[] getGroupRoles(String groupname) {
        UserDatabase database = (UserDatabase) this.resource;
        Group group = database.findGroup(groupname);
        if (group != null) {
            List<String> results = new ArrayList<>();
            Iterator<Role> roles = group.getRoles();
            while (roles.hasNext()) {
                Role role = roles.next();
                results.add(role.getRolename());
            }
            return results.toArray(new String[0]);
        } else {
            return null;
        }
    }


}
