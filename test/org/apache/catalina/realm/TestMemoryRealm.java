/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.realm;

import java.io.File;
import java.io.PrintWriter;
import java.security.Principal;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestMemoryRealm extends TomcatBaseTest {

    public static final String CONFIG = "<?xml version=\"1.0\" ?>"
            + "<tomcat-users xmlns=\"http://tomcat.apache.org/xml\""
            + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + " xsi:schemaLocation=\"http://tomcat.apache.org/xml/tomcat-users.xsd\""
            + " version=\"1.0\">"
            + "<role rolename=\"testrole\" />"
            + "<group groupname=\"testgroup\" />"
            + "<user username=\"admin\" password=\"sekr3t\" roles=\"testrole, otherrole\" groups=\"testgroup, othergroup\" />"
            + "</tomcat-users>";

    @Test
    public void testRealmWithLockout() throws Exception {

        File configFile = new File(getTemporaryDirectory(), "tomcat-users-mr.xml");
        try (PrintWriter writer = new PrintWriter(configFile)) {
            writer.write(CONFIG);
        }
        addDeleteOnTearDown(configFile);

        MemoryRealm memoryRealm = new MemoryRealm();
        memoryRealm.setCredentialHandler(new MessageDigestCredentialHandler());
        memoryRealm.setPathname(configFile.getAbsolutePath());
        LockOutRealm lockout = new LockOutRealm();
        lockout.addRealm(memoryRealm);

        // LockOutRealm needs full lifecycle
        Tomcat tomcat = getTomcatInstance();
        Context context = tomcat.addContext("/realmtest", null);
        context.setRealm(lockout);
        tomcat.start();

        Principal p = lockout.authenticate("foo", "bar");
        Assert.assertNull(p);
        p = lockout.authenticate("admin", "sekr3t");
        Assert.assertNotNull(p);
        p = lockout.authenticate("admin", "bla");
        Assert.assertNull(p);
        p = lockout.authenticate("admin", "bla");
        p = lockout.authenticate("admin", "bla");
        p = lockout.authenticate("admin", "bla");
        p = lockout.authenticate("admin", "bla");
        // Verify that lockout is now in place after 5 failures
        p = lockout.authenticate("admin", "sekr3t");
        Assert.assertNull(p);

    }

}
