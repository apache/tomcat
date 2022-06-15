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

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Iterator;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.startup.LoggingBaseTest;

public class DataSourceUserDatabaseTests extends LoggingBaseTest {

    public static final String SIMPLE_SCHEMA =
            "create table users (\n"
            + "  user_name         varchar(15) not null primary key,\n"
            + "  user_pass         varchar(15) not null\n"
            + ");\n"
            + "create table user_roles (\n"
            + "  user_name         varchar(15) not null,\n"
            + "  role_name         varchar(15) not null,\n"
            + "  primary key (user_name, role_name)\n"
            + ");";

    public static final String FULL_SCHEMA =
            "create table users (\n"
            + "  user_name         varchar(15) not null primary key,\n"
            + "  user_pass         varchar(15) not null,\n"
            + "  user_fullname     varchar(128)\n"
            + "  -- Add more attributes as needed\n"
            + ");\n"
            + "create table roles (\n"
            + "  role_name         varchar(15) not null primary key,\n"
            + "  role_description  varchar(128)\n"
            + ");\n"
            + "create table groups (\n"
            + "  group_name        varchar(15) not null primary key,\n"
            + "  group_description varchar(128)\n"
            + ");\n"
            + "create table user_roles (\n"
            + "  user_name         varchar(15) references users(user_name),\n"
            + "  role_name         varchar(15) references roles(role_name),\n"
            + "  primary key (user_name, role_name)\n"
            + ");\n"
            + "create table user_groups (\n"
            + "  user_name         varchar(15) references users(user_name),\n"
            + "  group_name        varchar(15) references groups(group_name),\n"
            + "  primary key (user_name, group_name)\n"
            + ");\n"
            + "create table group_roles (\n"
            + "  group_name        varchar(15) references groups(group_name),\n"
            + "  role_name         varchar(15) references roles(role_name),\n"
            + "  primary key (group_name, role_name)\n"
            + ");";

    protected class DerbyUserDatabase extends DataSourceUserDatabase {
        protected final String name;
        protected Connection connection = null;
        public DerbyUserDatabase(String name) {
            super(null, "tomcat");
            this.name = "/" + name;
        }
        @Override
        protected Connection openConnection() {
            // Replace DataSource use and JNDI access with direct Derby
            // connection
            return connection;
        }
        @Override
        protected void closeConnection(Connection dbConnection) {
        }
        @Override
        public void close() throws Exception {
            if (connection != null) {
                connection.close();
            }
        }
        @Override
        public void open() throws Exception {
            super.open();
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            connection = DriverManager.getConnection("jdbc:derby:" + getTemporaryDirectory().getAbsolutePath()
                    + name + ";create=true");
        }
        public Connection getConnection() {
            return connection;
        }
    }

    private DerbyUserDatabase db;

    @AfterClass
    public static void derbyCleanup() {
        System.out.println("Deleted derby.log: " + (new File("derby.log")).delete());
    }

    @Test
    public void testBasicUserRoleDatabase()
        throws Exception {
        // Test functionality with the DataSourceRealm schema

        db = new DerbyUserDatabase("simple");
        db.setReadonly(false);
        db.setUserTable("users");
        db.setUserNameCol("user_name");
        db.setUserCredCol("user_pass");
        db.setUserRoleTable("user_roles");
        db.setRoleNameCol("role_name");
        db.open();
        // First create the DB tables
        Connection connection = db.getConnection();
        for (String sql: SIMPLE_SCHEMA.split(";")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        Iterator<User> users = db.getUsers();
        Assert.assertFalse("Some users found", users.hasNext());

        User tomcatUser = db.createUser("tomcat", "password", "A new user");
        Role adminRole = db.createRole("admin", "Admin role");
        Role managerRole = db.createRole("manager", "Manager role");
        Role userRole = db.createRole("user", "User role");
        tomcatUser.addRole(adminRole);
        tomcatUser.addRole(userRole);
        db.save();

        users = db.getUsers();
        Assert.assertTrue("No users found", users.hasNext());
        tomcatUser = users.next();
        Assert.assertTrue("Wrong user", tomcatUser.getUsername().equals("tomcat"));
        Assert.assertTrue("Wrong password", tomcatUser.getPassword().equals("password"));
        // Cannot save the user full name
        Assert.assertNull("Wrong user fullname", tomcatUser.getFullName());
        adminRole = db.findRole("admin");
        Assert.assertNotNull("No admin role", adminRole);
        Assert.assertTrue("No role for user", tomcatUser.isInRole(adminRole));
        // Manager role cannot be saved, but remains valid in memory
        managerRole = db.findRole("manager");
        Assert.assertFalse("Unexpected role for user", tomcatUser.isInRole(managerRole));

        db.close();

    }

    @Test
    public void testUserDatabase()
        throws Exception {

        db = new DerbyUserDatabase("full");
        db.setReadonly(false);
        db.setUserTable("users");
        db.setUserNameCol("user_name");
        db.setUserCredCol("user_pass");
        db.setUserRoleTable("user_roles");
        db.setUserGroupTable("user_groups");
        db.setRoleTable("roles");
        db.setRoleNameCol("role_name");
        db.setGroupTable("groups");
        db.setGroupNameCol("group_name");
        db.setGroupRoleTable("group_roles");
        // Not setting the description or full name since it allows checking persistence,
        // as any modification is kept in memory until save()
        db.open();
        // First create the DB tables
        Connection connection = db.getConnection();
        for (String sql: FULL_SCHEMA.split(";")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        Iterator<User> users = db.getUsers();
        Assert.assertFalse("Some users found", users.hasNext());

        User tomcatUser = db.createUser("tomcat", "password", "A new user");
        User randomUser = db.createUser("random", "password", "Another new user");
        Role adminRole = db.createRole("admin", "Admin role");
        Role managerRole = db.createRole("manager", "Manager role");
        Role userRole = db.createRole("user", "User role");
        Group userGroup = db.createGroup("users", "All users");
        userGroup.addRole(userRole);
        tomcatUser.addRole(adminRole);
        tomcatUser.addGroup(userGroup);
        randomUser.addGroup(userGroup);
        db.save();

        users = db.getUsers();
        Assert.assertTrue("No users found", users.hasNext());
        tomcatUser = users.next();
        if (!tomcatUser.getUsername().equals("tomcat")) {
            tomcatUser = users.next();
        }
        Assert.assertTrue("Wrong user", tomcatUser.getUsername().equals("tomcat"));
        Assert.assertTrue("Wrong password", tomcatUser.getPassword().equals("password"));
        // Cannot save the user full name
        Assert.assertNull("Wrong user fullname", tomcatUser.getFullName());
        adminRole = db.findRole("admin");
        Assert.assertNotNull("No admin role", adminRole);
        Assert.assertNull("Wrong admin role", adminRole.getDescription());
        Assert.assertTrue("No role for user", tomcatUser.isInRole(adminRole));
        managerRole = db.findRole("manager");
        Assert.assertFalse("Unexpected role for user", tomcatUser.isInRole(managerRole));
        userRole = db.findRole("user");
        userGroup = db.findGroup("users");
        Assert.assertNull("Wrong users group", userGroup.getDescription());
        Assert.assertTrue("No role for group", userGroup.isInRole(userRole));
        randomUser = db.findUser("random");
        Assert.assertTrue("No group for user", randomUser.isInGroup(userGroup));

        db.close();

    }
}
