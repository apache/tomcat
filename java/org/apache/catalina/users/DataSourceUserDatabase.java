/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.users;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.sql.DataSource;

import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * UserDatabase backed by a data source.
 */
public class DataSourceUserDatabase extends SparseUserDatabase {

    private static final Log log = LogFactory.getLog(DataSourceUserDatabase.class);
    private static final StringManager sm = StringManager.getManager(DataSourceUserDatabase.class);

    public DataSourceUserDatabase(DataSource dataSource, String id) {
        this.dataSource = dataSource;
        this.id = id;
    }


    /**
     * DataSource to use.
     */
    protected final DataSource dataSource;


    /**
     * The unique global identifier of this user database.
     */
    protected final String id;

    protected final ConcurrentHashMap<String, User> createdUsers = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<String, User> modifiedUsers = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<String, User> removedUsers = new ConcurrentHashMap<>();

    protected final ConcurrentHashMap<String, Group> createdGroups = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<String, Group> modifiedGroups = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<String, Group> removedGroups = new ConcurrentHashMap<>();

    protected final ConcurrentHashMap<String, Role> createdRoles = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<String, Role> modifiedRoles = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<String, Role> removedRoles = new ConcurrentHashMap<>();


    // ----------------------------------------------------- Instance Variables


    /**
     * The generated string for the all users PreparedStatement
     */
    private String preparedAllUsers = null;


    /**
     * The generated string for the all groups PreparedStatement
     */
    private String preparedAllGroups = null;


    /**
     * The generated string for the all roles PreparedStatement
     */
    private String preparedAllRoles = null;


    /**
     * The generated string for the group PreparedStatement
     */
    private String preparedGroup = null;


    /**
     * The generated string for the role PreparedStatement
     */
    private String preparedRole = null;


    /**
     * The generated string for the roles PreparedStatement
     */
    private String preparedUserRoles = null;


    /**
     * The generated string for the user PreparedStatement
     */
    private String preparedUser = null;


    /**
     * The generated string for the groups PreparedStatement
     */
    private String preparedUserGroups = null;


    /**
     * The generated string for the groups PreparedStatement
     */
    private String preparedGroupRoles = null;


    /**
     * The name of the JNDI JDBC DataSource
     */
    protected String dataSourceName = null;


    /**
     * The column in the user role table that names a role
     */
    protected String roleNameCol = null;


    /**
     * The column in the role and group tables for the description
     */
    protected String roleAndGroupDescriptionCol = null;


    /**
     * The column in the user group table that names a group
     */
    protected String groupNameCol = null;


    /**
     * The column in the user table that holds the user's credentials
     */
    protected String userCredCol = null;


    /**
     * The column in the user table that holds the user's full name
     */
    protected String userFullNameCol = null;


    /**
     * The column in the user table that holds the user's name
     */
    protected String userNameCol = null;


    /**
     * The table that holds the relation between users and roles
     */
    protected String userRoleTable = null;


    /**
     * The table that holds the relation between users and groups
     */
    protected String userGroupTable = null;


    /**
     * The table that holds the relation between groups and roles
     */
    protected String groupRoleTable = null;


    /**
     * The table that holds user data.
     */
    protected String userTable = null;


    /**
     * The table that holds user data.
     */
    protected String groupTable = null;


    /**
     * The table that holds user data.
     */
    protected String roleTable = null;


    /**
     * Last connection attempt.
     */
    private volatile boolean connectionSuccess = true;


    /**
     * A flag, indicating if the user database is read only.
     */
    protected boolean readonly = true;

    // The write lock on the database is assumed to include the write locks
    // for groups, users and roles.
    private final ReentrantReadWriteLock dbLock = new ReentrantReadWriteLock();
    private final Lock dbReadLock = dbLock.readLock();
    private final Lock dbWriteLock = dbLock.writeLock();

    private final ReentrantReadWriteLock groupsLock = new ReentrantReadWriteLock();
    private final Lock groupsReadLock = groupsLock.readLock();
    private final Lock groupsWriteLock = groupsLock.writeLock();

    private final ReentrantReadWriteLock usersLock = new ReentrantReadWriteLock();
    private final Lock usersReadLock = usersLock.readLock();
    private final Lock usersWriteLock = usersLock.writeLock();

    private final ReentrantReadWriteLock rolesLock = new ReentrantReadWriteLock();
    private final Lock rolesReadLock = rolesLock.readLock();
    private final Lock rolesWriteLock = rolesLock.writeLock();


    // ------------------------------------------------------------- Properties

    /**
     * @return the name of the JNDI JDBC DataSource.
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * Set the name of the JNDI JDBC DataSource.
     *
     * @param dataSourceName the name of the JNDI JDBC DataSource
     */
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    /**
     * @return the column in the user role table that names a role.
     */
    public String getRoleNameCol() {
        return roleNameCol;
    }

    /**
     * Set the column in the user role table that names a role.
     *
     * @param roleNameCol The column name
     */
    public void setRoleNameCol( String roleNameCol ) {
        this.roleNameCol = roleNameCol;
    }

    /**
     * @return the column in the user table that holds the user's credentials.
     */
    public String getUserCredCol() {
        return userCredCol;
    }

    /**
     * Set the column in the user table that holds the user's credentials.
     *
     * @param userCredCol The column name
     */
    public void setUserCredCol( String userCredCol ) {
       this.userCredCol = userCredCol;
    }

    /**
     * @return the column in the user table that holds the user's name.
     */
    public String getUserNameCol() {
        return userNameCol;
    }

    /**
     * Set the column in the user table that holds the user's name.
     *
     * @param userNameCol The column name
     */
    public void setUserNameCol( String userNameCol ) {
       this.userNameCol = userNameCol;
    }

    /**
     * @return the table that holds the relation between user's and roles.
     */
    public String getUserRoleTable() {
        return userRoleTable;
    }

    /**
     * Set the table that holds the relation between user's and roles.
     *
     * @param userRoleTable The table name
     */
    public void setUserRoleTable( String userRoleTable ) {
        this.userRoleTable = userRoleTable;
    }

    /**
     * @return the table that holds user data..
     */
    public String getUserTable() {
        return userTable;
    }

    /**
     * Set the table that holds user data.
     *
     * @param userTable The table name
     */
    public void setUserTable( String userTable ) {
      this.userTable = userTable;
    }


    /**
     * @return the roleAndGroupDescriptionCol
     */
    public String getRoleAndGroupDescriptionCol() {
        return this.roleAndGroupDescriptionCol;
    }

    /**
     * @param roleAndGroupDescriptionCol the roleAndGroupDescriptionCol to set
     */
    public void setRoleAndGroupDescriptionCol(String roleAndGroupDescriptionCol) {
        this.roleAndGroupDescriptionCol = roleAndGroupDescriptionCol;
    }

    /**
     * @return the groupNameCol
     */
    public String getGroupNameCol() {
        return this.groupNameCol;
    }

    /**
     * @param groupNameCol the groupNameCol to set
     */
    public void setGroupNameCol(String groupNameCol) {
        this.groupNameCol = groupNameCol;
    }

    /**
     * @return the userFullNameCol
     */
    public String getUserFullNameCol() {
        return this.userFullNameCol;
    }

    /**
     * @param userFullNameCol the userFullNameCol to set
     */
    public void setUserFullNameCol(String userFullNameCol) {
        this.userFullNameCol = userFullNameCol;
    }

    /**
     * @return the userGroupTable
     */
    public String getUserGroupTable() {
        return this.userGroupTable;
    }

    /**
     * @param userGroupTable the userGroupTable to set
     */
    public void setUserGroupTable(String userGroupTable) {
        this.userGroupTable = userGroupTable;
    }

    /**
     * @return the groupRoleTable
     */
    public String getGroupRoleTable() {
        return this.groupRoleTable;
    }

    /**
     * @param groupRoleTable the groupRoleTable to set
     */
    public void setGroupRoleTable(String groupRoleTable) {
        this.groupRoleTable = groupRoleTable;
    }

    /**
     * @return the groupTable
     */
    public String getGroupTable() {
        return this.groupTable;
    }

    /**
     * @param groupTable the groupTable to set
     */
    public void setGroupTable(String groupTable) {
        this.groupTable = groupTable;
    }

    /**
     * @return the roleTable
     */
    public String getRoleTable() {
        return this.roleTable;
    }

    /**
     * @param roleTable the roleTable to set
     */
    public void setRoleTable(String roleTable) {
        this.roleTable = roleTable;
    }

    /**
     * @return the readonly
     */
    public boolean getReadonly() {
        return this.readonly;
    }

    /**
     * @param readonly the readonly to set
     */
    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Iterator<Group> getGroups() {
        dbReadLock.lock();
        try {
            groupsReadLock.lock();
            try {
                HashMap<String, Group> groups = new HashMap<>();
                groups.putAll(createdGroups);
                groups.putAll(modifiedGroups);

                Connection dbConnection = openConnection();
                if (dbConnection != null && preparedAllGroups != null) {
                    try (PreparedStatement stmt = dbConnection.prepareStatement(preparedAllGroups)) {
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                String groupName = rs.getString(1);
                                if (groupName != null) {
                                    if (!groups.containsKey(groupName) && !removedGroups.containsKey(groupName)) {
                                        Group group = findGroupInternal(dbConnection, groupName);
                                        if (group != null) {
                                            groups.put(groupName, group);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    } finally {
                        closeConnection(dbConnection);
                    }
                }
                return groups.values().iterator();
            } finally {
                groupsReadLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    @Override
    public Iterator<Role> getRoles() {
        dbReadLock.lock();
        try {
            rolesReadLock.lock();
            try {
                HashMap<String, Role> roles = new HashMap<>();
                roles.putAll(createdRoles);
                roles.putAll(modifiedRoles);

                Connection dbConnection = openConnection();
                if (dbConnection != null && preparedAllRoles != null) {
                    try (PreparedStatement stmt = dbConnection.prepareStatement(preparedAllRoles)) {
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                String roleName = rs.getString(1);
                                if (roleName != null) {
                                    if (!roles.containsKey(roleName) && !removedRoles.containsKey(roleName)) {
                                        Role role = findRoleInternal(dbConnection, roleName);
                                        if (role != null) {
                                            roles.put(roleName, role);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    } finally {
                        closeConnection(dbConnection);
                    }
                }
                return roles.values().iterator();
            } finally {
                rolesReadLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    @Override
    public Iterator<User> getUsers() {
        dbReadLock.lock();
        try {
            usersReadLock.lock();
            try {
                HashMap<String, User> users = new HashMap<>();
                users.putAll(createdUsers);
                users.putAll(modifiedUsers);

                Connection dbConnection = openConnection();
                if (dbConnection != null) {
                    try (PreparedStatement stmt = dbConnection.prepareStatement(preparedAllUsers)) {
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                String userName = rs.getString(1);
                                if (userName != null) {
                                    if (!users.containsKey(userName) && !removedUsers.containsKey(userName)) {
                                        User user = findUserInternal(dbConnection, userName);
                                        if (user != null) {
                                            users.put(userName, user);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    } finally {
                        closeConnection(dbConnection);
                    }
                }
                return users.values().iterator();
            } finally {
                usersReadLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public Group createGroup(String groupname, String description) {
        dbReadLock.lock();
        try {
            groupsWriteLock.lock();
            try {
                Group group = new GenericGroup<>(this, groupname, description, null);
                createdGroups.put(groupname, group);
                modifiedGroups.remove(groupname);
                return group;
            } finally {
                groupsWriteLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    @Override
    public Role createRole(String rolename, String description) {
        dbReadLock.lock();
        try {
            rolesWriteLock.lock();
            try {
                Role role = new GenericRole<>(this, rolename, description);
                createdRoles.put(rolename, role);
                modifiedRoles.remove(rolename);
                return role;
            } finally {
                rolesWriteLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    @Override
    public User createUser(String username, String password, String fullName) {
        dbReadLock.lock();
        try {
            usersWriteLock.lock();
            try {
                User user = new GenericUser<>(this, username, password, fullName, null, null);
                createdUsers.put(username, user);
                modifiedUsers.remove(username);
                return user;
            } finally {
                usersWriteLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    @Override
    public Group findGroup(String groupname) {
        dbReadLock.lock();
        try {
            groupsReadLock.lock();
            try {
                // Check local changes first
                Group group = createdGroups.get(groupname);
                if (group != null) {
                    return group;
                }
                group = modifiedGroups.get(groupname);
                if (group != null) {
                    return group;
                }
                group = removedGroups.get(groupname);
                if (group != null) {
                    return null;
                }

                if (isGroupStoreDefined()) {
                    Connection dbConnection = openConnection();
                    if (dbConnection == null) {
                        return null;
                    }
                    try {
                        return findGroupInternal(dbConnection, groupname);
                    } finally {
                        closeConnection(dbConnection);
                    }
                } else {
                    return null;
                }
            } finally {
                groupsReadLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    public Group findGroupInternal(Connection dbConnection, String groupName) {
        Group group = null;
        try (PreparedStatement stmt = dbConnection.prepareStatement(preparedGroup)) {
            stmt.setString(1, groupName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (rs.getString(1) != null) {
                        String description = (roleAndGroupDescriptionCol != null) ? rs.getString(2) : null;
                        ArrayList<Role> groupRoles = new ArrayList<>();
                        if (groupName != null) {
                            groupName = groupName.trim();
                            try (PreparedStatement stmt2 = dbConnection.prepareStatement(preparedGroupRoles)) {
                                stmt2.setString(1, groupName);
                                try (ResultSet rs2 = stmt2.executeQuery()) {
                                    while (rs2.next()) {
                                        String roleName = rs2.getString(1);
                                        if (roleName != null) {
                                            Role groupRole = findRoleInternal(dbConnection, roleName);
                                            if (groupRole != null) {
                                                groupRoles.add(groupRole);
                                            }
                                        }
                                    }
                                }
                            } catch (SQLException e) {
                                log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                            }
                        }
                        group = new GenericGroup<>(this, groupName, description, groupRoles);
                    }
                }
            }
        } catch (SQLException e) {
            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
        }
        return group;
    }

    @Override
    public Role findRole(String rolename) {
        dbReadLock.lock();
        try {
            rolesReadLock.lock();
            try {
                // Check local changes first
                Role role = createdRoles.get(rolename);
                if (role != null) {
                    return role;
                }
                role = modifiedRoles.get(rolename);
                if (role != null) {
                    return role;
                }
                role = removedRoles.get(rolename);
                if (role != null) {
                    return null;
                }

                if (userRoleTable != null && roleNameCol != null) {
                    Connection dbConnection = openConnection();
                    if (dbConnection == null) {
                        return null;
                    }
                    try {
                        return findRoleInternal(dbConnection, rolename);
                    } finally {
                        closeConnection(dbConnection);
                    }
                } else {
                    return null;
                }
            } finally {
                rolesReadLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    public Role findRoleInternal(Connection dbConnection, String roleName) {
        Role role = null;
        try (PreparedStatement stmt = dbConnection.prepareStatement(preparedRole)) {
            stmt.setString(1, roleName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (rs.getString(1) != null) {
                        String description = (roleAndGroupDescriptionCol != null) ? rs.getString(2) : null;
                        role = new GenericRole<>(this, roleName, description);
                    }
                }
            }
        } catch (SQLException e) {
            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
        }
        return role;
    }

    @Override
    public User findUser(String username) {
        dbReadLock.lock();
        try {
            usersReadLock.lock();
            try {
                // Check local changes first
                User user = createdUsers.get(username);
                if (user != null) {
                    return user;
                }
                user = modifiedUsers.get(username);
                if (user != null) {
                    return user;
                }
                user = removedUsers.get(username);
                if (user != null) {
                    return null;
                }

                Connection dbConnection = openConnection();
                if (dbConnection == null) {
                    return null;
                }
                try {
                    return findUserInternal(dbConnection, username);
                } finally {
                    closeConnection(dbConnection);
                }
            } finally {
                usersReadLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    public User findUserInternal(Connection dbConnection, String userName) {
        String dbCredentials = null;
        String fullName = null;

        try (PreparedStatement stmt = dbConnection.prepareStatement(preparedUser)) {
            stmt.setString(1, userName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    dbCredentials = rs.getString(1);
                    if (userFullNameCol != null) {
                        fullName = rs.getString(2);
                    }
                }

                dbCredentials = (dbCredentials != null) ? dbCredentials.trim() : null;
            }
        } catch (SQLException e) {
            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
        }

        // Lookup groups
        ArrayList<Group> groups = new ArrayList<>();
        if (isGroupStoreDefined()) {
            try (PreparedStatement stmt = dbConnection.prepareStatement(preparedUserGroups)) {
                stmt.setString(1, userName);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String groupName = rs.getString(1);
                        if (groupName != null) {
                            Group group = findGroupInternal(dbConnection, groupName);
                            if (group != null) {
                                groups.add(group);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                log.error(sm.getString("dataSourceUserDatabase.exception"), e);
            }
        }

        ArrayList<Role> roles = new ArrayList<>();
        if (userRoleTable != null && roleNameCol != null) {
            try (PreparedStatement stmt = dbConnection.prepareStatement(preparedUserRoles)) {
                stmt.setString(1, userName);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String roleName = rs.getString(1);
                        if (roleName != null) {
                            Role role = findRoleInternal(dbConnection, roleName);
                            if (role != null) {
                                roles.add(role);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                log.error(sm.getString("dataSourceUserDatabase.exception"), e);
            }
        }

        User user = new GenericUser<>(this, userName, dbCredentials, fullName, groups, roles);
        return user;
    }

    @Override
    public void modifiedGroup(Group group) {
        dbReadLock.lock();
        try {
            groupsWriteLock.lock();
            try {
                String name = group.getName();
                if (!createdGroups.containsKey(name) && !removedGroups.containsKey(name)) {
                    modifiedGroups.put(name, group);
                }
            } finally {
                groupsWriteLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    @Override
    public void modifiedRole(Role role) {
        dbReadLock.lock();
        try {
            rolesWriteLock.lock();
            try {
                String name = role.getName();
                if (!createdRoles.containsKey(name) && !removedRoles.containsKey(name)) {
                    modifiedRoles.put(name, role);
                }
            } finally {
                rolesWriteLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    @Override
    public void modifiedUser(User user) {
        dbReadLock.lock();
        try {
            usersWriteLock.lock();
            try {
                String name = user.getName();
                if (!createdUsers.containsKey(name) && !removedUsers.containsKey(name)) {
                    modifiedUsers.put(name, user);
                }
            } finally {
                usersWriteLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    @Override
    public void open() throws Exception {

        if (log.isDebugEnabled()) {
            // As there are lots of parameters to configure, log some debug to help out
            log.debug("DataSource UserDatabase features: User<->Role ["
                    + Boolean.toString(userRoleTable != null && roleNameCol != null)
                    + "], Roles [" + Boolean.toString(isRoleStoreDefined())
                    + "], Groups [" + Boolean.toString(isRoleStoreDefined()) + "]");
        }

        dbWriteLock.lock();
        try {

            StringBuilder temp = new StringBuilder("SELECT ");
            temp.append(userCredCol);
            if (userFullNameCol != null) {
                temp.append(",").append(userFullNameCol);
            }
            temp.append(" FROM ");
            temp.append(userTable);
            temp.append(" WHERE ");
            temp.append(userNameCol);
            temp.append(" = ?");
            preparedUser = temp.toString();

            temp = new StringBuilder("SELECT ");
            temp.append(userNameCol);
            temp.append(" FROM ");
            temp.append(userTable);
            preparedAllUsers = temp.toString();

            temp = new StringBuilder("SELECT ");
            temp.append(roleNameCol);
            temp.append(" FROM ");
            temp.append(userRoleTable);
            temp.append(" WHERE ");
            temp.append(userNameCol);
            temp.append(" = ?");
            preparedUserRoles = temp.toString();

            if (isGroupStoreDefined()) {
                temp = new StringBuilder("SELECT ");
                temp.append(groupNameCol);
                temp.append(" FROM ");
                temp.append(userGroupTable);
                temp.append(" WHERE ");
                temp.append(userNameCol);
                temp.append(" = ?");
                preparedUserGroups = temp.toString();

                temp = new StringBuilder("SELECT ");
                temp.append(roleNameCol);
                temp.append(" FROM ");
                temp.append(groupRoleTable);
                temp.append(" WHERE ");
                temp.append(groupNameCol);
                temp.append(" = ?");
                preparedGroupRoles = temp.toString();

                temp = new StringBuilder("SELECT ");
                temp.append(groupNameCol);
                if (roleAndGroupDescriptionCol != null) {
                    temp.append(",").append(roleAndGroupDescriptionCol);
                }
                temp.append(" FROM ");
                temp.append(groupTable);
                temp.append(" WHERE ");
                temp.append(groupNameCol);
                temp.append(" = ?");
                preparedGroup = temp.toString();

                temp = new StringBuilder("SELECT ");
                temp.append(groupNameCol);
                temp.append(" FROM ");
                temp.append(groupTable);
                preparedAllGroups = temp.toString();
            }

            if (isRoleStoreDefined()) {
                // Create the role PreparedStatement string
                temp = new StringBuilder("SELECT ");
                temp.append(roleNameCol);
                if (roleAndGroupDescriptionCol != null) {
                    temp.append(",").append(roleAndGroupDescriptionCol);
                }
                temp.append(" FROM ");
                temp.append(roleTable);
                temp.append(" WHERE ");
                temp.append(roleNameCol);
                temp.append(" = ?");
                preparedRole = temp.toString();

                temp = new StringBuilder("SELECT ");
                temp.append(roleNameCol);
                temp.append(" FROM ");
                temp.append(roleTable);
                preparedAllRoles = temp.toString();
            } else if (userRoleTable != null && roleNameCol != null) {
                // Validate roles existence from the user <-> roles table
                temp = new StringBuilder("SELECT ");
                temp.append(roleNameCol);
                temp.append(" FROM ");
                temp.append(userRoleTable);
                temp.append(" WHERE ");
                temp.append(roleNameCol);
                temp.append(" = ?");
                preparedRole = temp.toString();
            }

        } finally {
            dbWriteLock.unlock();
        }

    }

    @Override
    public void removeGroup(Group group) {
        dbReadLock.lock();
        try {
            groupsWriteLock.lock();
            try {
                String name = group.getName();
                createdGroups.remove(name);
                modifiedGroups.remove(name);
                removedGroups.put(name, group);
            } finally {
                groupsWriteLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    @Override
    public void removeRole(Role role) {
        dbReadLock.lock();
        try {
            rolesWriteLock.lock();
            try {
                String name = role.getName();
                createdRoles.remove(name);
                modifiedRoles.remove(name);
                removedRoles.put(name, role);
            } finally {
                rolesWriteLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    @Override
    public void removeUser(User user) {
        dbReadLock.lock();
        try {
            usersWriteLock.lock();
            try {
                String name = user.getName();
                createdUsers.remove(name);
                modifiedUsers.remove(name);
                removedUsers.put(name, user);
            } finally {
                usersWriteLock.unlock();
            }
        } finally {
            dbReadLock.unlock();
        }
    }

    @Override
    public void save() throws Exception {
        if (readonly) {
            return;
        }

        Connection dbConnection = openConnection();
        if (dbConnection == null) {
            return;
        }

        dbWriteLock.lock();
        try {
            try {
                saveInternal(dbConnection);
            } finally {
                closeConnection(dbConnection);
            }
        } finally {
            dbWriteLock.unlock();
        }
    }

    protected void saveInternal(Connection dbConnection) {

        StringBuilder temp = null;
        StringBuilder tempRelation = null;
        StringBuilder tempRelationDelete = null;

        if (isRoleStoreDefined()) {

            // Removed roles
            if (!removedRoles.isEmpty()) {
                temp = new StringBuilder("DELETE FROM ");
                temp.append(roleTable);
                temp.append(" WHERE ").append(roleNameCol);
                temp.append(" = ?");
                if (groupRoleTable != null) {
                    tempRelationDelete = new StringBuilder("DELETE FROM ");
                    tempRelationDelete.append(groupRoleTable);
                    tempRelationDelete.append(" WHERE ");
                    tempRelationDelete.append(roleNameCol);
                    tempRelationDelete.append(" = ?");
                }
                StringBuilder tempRelationDelete2 = new StringBuilder("DELETE FROM ");
                tempRelationDelete2.append(userRoleTable);
                tempRelationDelete2.append(" WHERE ");
                tempRelationDelete2.append(roleNameCol);
                tempRelationDelete2.append(" = ?");
                for (Role role : removedRoles.values()) {
                    if (tempRelationDelete != null) {
                        try (PreparedStatement stmt = dbConnection.prepareStatement(tempRelationDelete.toString())) {
                            stmt.setString(1, role.getRolename());
                            stmt.executeUpdate();
                        } catch (SQLException e) {
                            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                        }
                    }
                    try (PreparedStatement stmt = dbConnection.prepareStatement(tempRelationDelete2.toString())) {
                        stmt.setString(1, role.getRolename());
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                    try (PreparedStatement stmt = dbConnection.prepareStatement(temp.toString())) {
                        stmt.setString(1, role.getRolename());
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                }
                removedRoles.clear();
            }

            // Created roles
            if (!createdRoles.isEmpty()) {
                temp = new StringBuilder("INSERT INTO ");
                temp.append(roleTable);
                temp.append('(').append(roleNameCol);
                if (roleAndGroupDescriptionCol != null) {
                    temp.append(',').append(roleAndGroupDescriptionCol);
                }
                temp.append(") VALUES (?");
                if (roleAndGroupDescriptionCol != null) {
                    temp.append(", ?");
                }
                temp.append(')');
                for (Role role : createdRoles.values()) {
                    try (PreparedStatement stmt = dbConnection.prepareStatement(temp.toString())) {
                        stmt.setString(1, role.getRolename());
                        if (roleAndGroupDescriptionCol != null) {
                            stmt.setString(2, role.getDescription());
                        }
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                }
                createdRoles.clear();
            }

            // Modified roles
            if (!modifiedRoles.isEmpty() && roleAndGroupDescriptionCol != null) {
                temp = new StringBuilder("UPDATE ");
                temp.append(roleTable);
                temp.append(" SET ").append(roleAndGroupDescriptionCol);
                temp.append(" = ? WHERE ").append(roleNameCol);
                temp.append(" = ?");
                for (Role role : modifiedRoles.values()) {
                    try (PreparedStatement stmt = dbConnection.prepareStatement(temp.toString())) {
                        stmt.setString(1, role.getDescription());
                        stmt.setString(2, role.getRolename());
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                }
                modifiedRoles.clear();
            }

        } else if (userRoleTable != null && roleNameCol != null) {
            // Only remove role from users
            tempRelationDelete = new StringBuilder("DELETE FROM ");
            tempRelationDelete.append(userRoleTable);
            tempRelationDelete.append(" WHERE ");
            tempRelationDelete.append(roleNameCol);
            tempRelationDelete.append(" = ?");
            for (Role role : removedRoles.values()) {
                try (PreparedStatement stmt = dbConnection.prepareStatement(tempRelationDelete.toString())) {
                    stmt.setString(1, role.getRolename());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                }
            }
            removedRoles.clear();
        }

        if (isGroupStoreDefined()) {

            tempRelation = new StringBuilder("INSERT INTO ");
            tempRelation.append(groupRoleTable);
            tempRelation.append('(').append(groupNameCol).append(", ");
            tempRelation.append(roleNameCol);
            tempRelation.append(") VALUES (?, ?)");
            String groupRoleRelation = tempRelation.toString();
            // Always drop and recreate all group <-> role relations
            tempRelationDelete = new StringBuilder("DELETE FROM ");
            tempRelationDelete.append(groupRoleTable);
            tempRelationDelete.append(" WHERE ");
            tempRelationDelete.append(groupNameCol);
            tempRelationDelete.append(" = ?");
            String groupRoleRelationDelete = tempRelationDelete.toString();

            // Removed groups
            if (!removedGroups.isEmpty()) {
                temp = new StringBuilder("DELETE FROM ");
                temp.append(groupTable);
                temp.append(" WHERE ").append(groupNameCol);
                temp.append(" = ?");
                StringBuilder tempRelationDelete2 = new StringBuilder("DELETE FROM ");
                tempRelationDelete2.append(userGroupTable);
                tempRelationDelete2.append(" WHERE ");
                tempRelationDelete2.append(groupNameCol);
                tempRelationDelete2.append(" = ?");
                for (Group group : removedGroups.values()) {
                    try (PreparedStatement stmt = dbConnection.prepareStatement(groupRoleRelationDelete)) {
                        stmt.setString(1, group.getGroupname());
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                    try (PreparedStatement stmt = dbConnection.prepareStatement(tempRelationDelete2.toString())) {
                        stmt.setString(1, group.getGroupname());
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                    try (PreparedStatement stmt = dbConnection.prepareStatement(temp.toString())) {
                        stmt.setString(1, group.getGroupname());
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                }
                removedGroups.clear();
            }

            // Created groups
            if (!createdGroups.isEmpty()) {
                temp = new StringBuilder("INSERT INTO ");
                temp.append(groupTable);
                temp.append('(').append(groupNameCol);
                if (roleAndGroupDescriptionCol != null) {
                    temp.append(',').append(roleAndGroupDescriptionCol);
                }
                temp.append(") VALUES (?");
                if (roleAndGroupDescriptionCol != null) {
                    temp.append(", ?");
                }
                temp.append(')');
                for (Group group : createdGroups.values()) {
                    try (PreparedStatement stmt = dbConnection.prepareStatement(temp.toString())) {
                        stmt.setString(1, group.getGroupname());
                        if (roleAndGroupDescriptionCol != null) {
                            stmt.setString(2, group.getDescription());
                        }
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                    Iterator<Role> roles = group.getRoles();
                    while (roles.hasNext()) {
                        Role role = roles.next();
                        try (PreparedStatement stmt = dbConnection.prepareStatement(groupRoleRelation)) {
                            stmt.setString(1, group.getGroupname());
                            stmt.setString(2, role.getRolename());
                            stmt.executeUpdate();
                        } catch (SQLException e) {
                            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                        }
                    }
                }
                createdGroups.clear();
            }

            // Modified groups
            if (!modifiedGroups.isEmpty()) {
                if (roleAndGroupDescriptionCol != null) {
                    temp = new StringBuilder("UPDATE ");
                    temp.append(groupTable);
                    temp.append(" SET ").append(roleAndGroupDescriptionCol);
                    temp.append(" = ? WHERE ").append(groupNameCol);
                    temp.append(" = ?");
                }
                for (Group group : modifiedGroups.values()) {
                    if (temp != null) {
                        try (PreparedStatement stmt = dbConnection.prepareStatement(temp.toString())) {
                            stmt.setString(1, group.getDescription());
                            stmt.setString(2, group.getGroupname());
                            stmt.executeUpdate();
                        } catch (SQLException e) {
                            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                        }
                    }
                    try (PreparedStatement stmt = dbConnection.prepareStatement(groupRoleRelationDelete)) {
                        stmt.setString(1, group.getGroupname());
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                    Iterator<Role> roles = group.getRoles();
                    while (roles.hasNext()) {
                        Role role = roles.next();
                        try (PreparedStatement stmt = dbConnection.prepareStatement(groupRoleRelation)) {
                            stmt.setString(1, group.getGroupname());
                            stmt.setString(2, role.getRolename());
                            stmt.executeUpdate();
                        } catch (SQLException e) {
                            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                        }
                    }
                }
                modifiedGroups.clear();
            }

        }

        String userRoleRelation = null;
        String userRoleRelationDelete = null;
        if (userRoleTable != null && roleNameCol != null) {
            tempRelation = new StringBuilder("INSERT INTO ");
            tempRelation.append(userRoleTable);
            tempRelation.append('(').append(userNameCol).append(", ");
            tempRelation.append(roleNameCol);
            tempRelation.append(") VALUES (?, ?)");
            userRoleRelation = tempRelation.toString();
            // Always drop and recreate all user <-> role relations
            tempRelationDelete = new StringBuilder("DELETE FROM ");
            tempRelationDelete.append(userRoleTable);
            tempRelationDelete.append(" WHERE ");
            tempRelationDelete.append(userNameCol);
            tempRelationDelete.append(" = ?");
            userRoleRelationDelete = tempRelationDelete.toString();
        }
        String userGroupRelation = null;
        String userGroupRelationDelete = null;
        if (isGroupStoreDefined()) {
            tempRelation = new StringBuilder("INSERT INTO ");
            tempRelation.append(userGroupTable);
            tempRelation.append('(').append(userNameCol).append(", ");
            tempRelation.append(groupNameCol);
            tempRelation.append(") VALUES (?, ?)");
            userGroupRelation = tempRelation.toString();
            // Always drop and recreate all user <-> group relations
            tempRelationDelete = new StringBuilder("DELETE FROM ");
            tempRelationDelete.append(userGroupTable);
            tempRelationDelete.append(" WHERE ");
            tempRelationDelete.append(userNameCol);
            tempRelationDelete.append(" = ?");
            userGroupRelationDelete = tempRelationDelete.toString();
        }

        // Removed users
        if (!removedUsers.isEmpty()) {
            temp = new StringBuilder("DELETE FROM ");
            temp.append(userTable);
            temp.append(" WHERE ").append(userNameCol);
            temp.append(" = ?");
            for (User user : removedUsers.values()) {
                if (userRoleRelationDelete != null) {
                    try (PreparedStatement stmt = dbConnection.prepareStatement(userRoleRelationDelete)) {
                        stmt.setString(1, user.getUsername());
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                }
                if (userGroupRelationDelete != null) {
                    try (PreparedStatement stmt = dbConnection.prepareStatement(userGroupRelationDelete)) {
                        stmt.setString(1, user.getUsername());
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                }
                try (PreparedStatement stmt = dbConnection.prepareStatement(temp.toString())) {
                    stmt.setString(1, user.getUsername());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                }
            }
            removedUsers.clear();
        }

        // Created users
        if (!createdUsers.isEmpty()) {
            temp = new StringBuilder("INSERT INTO ");
            temp.append(userTable);
            temp.append('(').append(userNameCol);
            temp.append(", ").append(userCredCol);
            if (userFullNameCol != null) {
                temp.append(',').append(userFullNameCol);
            }
            temp.append(") VALUES (?, ?");
            if (userFullNameCol != null) {
                temp.append(", ?");
            }
            temp.append(')');
            for (User user : createdUsers.values()) {
                try (PreparedStatement stmt = dbConnection.prepareStatement(temp.toString())) {
                    stmt.setString(1, user.getUsername());
                    stmt.setString(2, user.getPassword());
                    if (userFullNameCol != null) {
                        stmt.setString(3, user.getFullName());
                    }
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                }
                if (userRoleRelation != null) {
                    Iterator<Role> roles = user.getRoles();
                    while (roles.hasNext()) {
                        Role role = roles.next();
                        try (PreparedStatement stmt = dbConnection.prepareStatement(userRoleRelation)) {
                            stmt.setString(1, user.getUsername());
                            stmt.setString(2, role.getRolename());
                            stmt.executeUpdate();
                        } catch (SQLException e) {
                            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                        }
                    }
                }
                if (userGroupRelation != null) {
                    Iterator<Group> groups = user.getGroups();
                    while (groups.hasNext()) {
                        Group group = groups.next();
                        try (PreparedStatement stmt = dbConnection.prepareStatement(userGroupRelation)) {
                            stmt.setString(1, user.getUsername());
                            stmt.setString(2, group.getGroupname());
                            stmt.executeUpdate();
                        } catch (SQLException e) {
                            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                        }
                    }
                }
            }
            createdUsers.clear();
        }

        // Modified users
        if (!modifiedUsers.isEmpty()) {
            temp = new StringBuilder("UPDATE ");
            temp.append(userTable);
            temp.append(" SET ").append(userCredCol);
            temp.append(" = ?");
            if (userFullNameCol != null) {
                temp.append(", ").append(userFullNameCol).append(" = ?");
            }
            temp.append(" WHERE ").append(userNameCol);
            temp.append(" = ?");
            for (User user : modifiedUsers.values()) {
                try (PreparedStatement stmt = dbConnection.prepareStatement(temp.toString())) {
                    stmt.setString(1, user.getPassword());
                    if (userFullNameCol != null) {
                        stmt.setString(2, user.getFullName());
                        stmt.setString(3, user.getUsername());
                    } else {
                        stmt.setString(2, user.getUsername());
                    }
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                }
                if (userRoleRelationDelete != null) {
                    try (PreparedStatement stmt = dbConnection.prepareStatement(userRoleRelationDelete)) {
                        stmt.setString(1, user.getUsername());
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                }
                if (userGroupRelationDelete != null) {
                    try (PreparedStatement stmt = dbConnection.prepareStatement(userGroupRelationDelete)) {
                        stmt.setString(1, user.getUsername());
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                    }
                }
                if (userRoleRelation != null) {
                    Iterator<Role> roles = user.getRoles();
                    while (roles.hasNext()) {
                        Role role = roles.next();
                        try (PreparedStatement stmt = dbConnection.prepareStatement(userRoleRelation)) {
                            stmt.setString(1, user.getUsername());
                            stmt.setString(2, role.getRolename());
                            stmt.executeUpdate();
                        } catch (SQLException e) {
                            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                        }
                    }
                }
                if (userGroupRelation != null) {
                    Iterator<Group> groups = user.getGroups();
                    while (groups.hasNext()) {
                        Group group = groups.next();
                        try (PreparedStatement stmt = dbConnection.prepareStatement(userGroupRelation)) {
                            stmt.setString(1, user.getUsername());
                            stmt.setString(2, group.getGroupname());
                            stmt.executeUpdate();
                        } catch (SQLException e) {
                            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
                        }
                    }
                }
            }
            modifiedGroups.clear();
        }

    }

    @Override
    public boolean isAvailable() {
        return connectionSuccess;
    }

    /**
     * Only use groups if the tables are fully defined.
     * @return true when groups are used
     */
    protected boolean isGroupStoreDefined() {
        return groupTable != null && userGroupTable != null && groupNameCol != null
                && groupRoleTable != null && isRoleStoreDefined();
    }


    /**
     * Only use roles if the tables are fully defined.
     * @return true when roles are used
     */
    protected boolean isRoleStoreDefined() {
        return roleTable != null && userRoleTable != null && roleNameCol != null;
    }


    /**
     * Open the specified database connection.
     *
     * @return Connection to the database
     */
    protected Connection openConnection() {
        if (dataSource == null) {
            return null;
        }
        try {
            Connection connection = dataSource.getConnection();
            connectionSuccess = true;
            return connection;
        } catch (Exception e) {
            connectionSuccess = false;
            // Log the problem for posterity
            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
        }
        return null;
    }

    /**
     * Close the specified database connection.
     *
     * @param dbConnection The connection to be closed
     */
    protected void closeConnection(Connection dbConnection) {

        // Do nothing if the database connection is already closed
        if (dbConnection == null) {
            return;
        }

        // Commit if not auto committed
        try {
            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
        }

        // Close this database connection, and log any errors
        try {
            dbConnection.close();
        } catch (SQLException e) {
            log.error(sm.getString("dataSourceUserDatabase.exception"), e);
        }

    }


}
