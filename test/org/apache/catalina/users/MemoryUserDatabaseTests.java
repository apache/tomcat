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

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.catalina.User;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.realm.UserDatabaseRealm;

public class MemoryUserDatabaseTests {
    private static File TEST_FILE = new File(System.getProperty("java.io.tmpdir"), "tomcat-users.xml");

    private static MemoryUserDatabase db;

    @BeforeClass
    public static void createSampleDB()
        throws Exception {

        try(BufferedWriter out = new BufferedWriter(new FileWriter(TEST_FILE))) {
            out.write("<?xml version=\"1.0\" ?>"
                    + "<tomcat-users xmlns=\"http://tomcat.apache.org/xml\""
                    + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                    + " xsi:schemaLocation=\"http://tomcat.apache.org/xml/tomcat-users.xsd\""
                    + " version=\"1.0\">"
                    + "<role rolename=\"testrole\" />"
                    + "<group groupname=\"testgroup\" />"
                    + "<user username=\"admin\" password=\"sekr3t\" roles=\"testrole, otherrole\" groups=\"testgroup, othergroup\" />"
                    + "</tomcat-users>");
        }

        db = new MemoryUserDatabase();
        db.setPathname(TEST_FILE.toURI().toURL().toString());
        db.open();
    }

    @AfterClass
    public static void cleanup() {
        Assert.assertTrue(TEST_FILE.delete());
    }

    @Test
    public void testLoadUserDatabase()
        throws Exception {
        assertPrincipalNames(new String[] { "testrole", "otherrole"}, db.getRoles());
        assertPrincipalNames(new String[] { "testgroup", "othergroup"}, db.getGroups());

        Iterator<User> users = db.getUsers();

        Assert.assertTrue("No users found", users.hasNext());

        User user = users.next();

        Assert.assertEquals("admin", user.getName());
        Assert.assertNull(user.getFullName());
        Assert.assertEquals("sekr3t", user.getPassword());

        assertPrincipalNames(new String[] { "testrole", "otherrole"}, user.getRoles());
        assertPrincipalNames(new String[] { "testgroup", "othergroup"}, user.getGroups());
    }

    public void testReloadUserDatabase()
        throws Exception {
        // Change the database on the disk and reload

        try(BufferedWriter out = new BufferedWriter(new FileWriter(TEST_FILE))) {
            out.write("<?xml version=\"1.0\" ?>"
                    + "<tomcat-users xmlns=\"http://tomcat.apache.org/xml\""
                    + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                    + " xsi:schemaLocation=\"http://tomcat.apache.org/xml/tomcat-users.xsd\""
                    + " version=\"1.0\">"
                    + "<role rolename=\"foo\" />"
                    + "<group groupname=\"bar\" />"
                    + "<user username=\"root\" password=\"sup3Rsekr3t\" roles=\"foo, bar\" groups=\"bar, foo\" />"
                    + "</tomcat-users>");

            db.open();
        }

        assertPrincipalNames(new String[] { "foo", "bar"}, db.getRoles());
        assertPrincipalNames(new String[] { "bar", "foo"}, db.getGroups());

        Iterator<User> users = db.getUsers();

        Assert.assertTrue("No users found", users.hasNext());

        User user = users.next();

        Assert.assertEquals("root", user.getName());
        Assert.assertNull(user.getFullName());
        Assert.assertEquals("sup3Rsekr3t", user.getPassword());

        assertPrincipalNames(new String[] { "foo", "bar"}, user.getRoles());
        assertPrincipalNames(new String[] { "bar", "foo"}, user.getGroups());
    }

    @Test
    public void testMultithreadedMutateUserDatabase()
        throws Exception {
        // Generate lots of concurrent load on the user database
        Runnable job = new Runnable() {
            @Override
            public void run() {
                for(int i=0; i<10; ++i) {
                  db.createUser("newUser-" + Thread.currentThread().getName() + "-" + i, "x", null);
                }
            }
        };

        int numThreads = 100;
        Thread[] threads = new Thread[numThreads + 1];
        for(int i=0; i<numThreads; ++i) {
          threads[i] = new Thread(job);
        }

        // Let's
        threads[numThreads] = new Thread(new Runnable() {
            @Override
            public void run() {
                try { db.open(); }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        ++numThreads;

        for(int i=0; i<numThreads; ++i) {
          threads[i].start();
        }

        for(int i=0; i<numThreads; ++i) {
          threads[i].join();
        }

        // Remove all those extra users
        Iterator<User> users = db.getUsers();
        for(; users.hasNext();) {
            User user = users.next();
            if(user.getUsername().startsWith("newUser")) {
              db.removeUser(user);
            }
        }

        users = db.getUsers();

        Assert.assertTrue("No users found", users.hasNext());

        User user = users.next();

        Assert.assertEquals("admin", user.getName());
        Assert.assertNull(user.getFullName());
        Assert.assertEquals("sekr3t", user.getPassword());

        assertPrincipalNames(new String[] { "testrole", "otherrole"}, user.getRoles());
        assertPrincipalNames(new String[] { "testgroup", "othergroup"}, user.getGroups());
    }

    @Test
    public void testSerializePrincipal()
        throws Exception {
        User user = db.findUser("admin");
        GenericPrincipal gpIn = new UserDatabaseRealm.UserDatabasePrincipal(user, db);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(gpIn);

        byte[] data = bos.toByteArray();

        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        GenericPrincipal gpOut =  (GenericPrincipal) ois.readObject();

        Assert.assertEquals("admin", gpOut.getName());
        assertPrincipalNames(gpOut.getRoles(), user.getRoles());
    }

    private void assertPrincipalNames(String[] expectedNames, Iterator<? extends Principal> i) {
        HashSet<String> names = new HashSet<>(Arrays.asList(expectedNames));

        int j=0;
        while(i.hasNext()) {
            Assert.assertTrue(names.contains(i.next().getName()));
            j++;
        }

        Assert.assertEquals(expectedNames.length, j);
    }
}
