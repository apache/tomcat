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

public class TestJAASRealm extends TomcatBaseTest {

    private static final String CONFIG =
            "CustomLogin {\n" +
            "    org.apache.catalina.realm.TesterLoginModule\n" +
            "    sufficient;\n" +
            "};";

    @Test
    public void testRealm() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // Write login config to the temp path
        File loginConfFile = new File(getTemporaryDirectory(), "customLoginConfig.conf");
        try (PrintWriter writer = new PrintWriter(loginConfFile)) {
            writer.write(CONFIG);
        }

        JAASRealm jaasRealm = new JAASRealm();
        jaasRealm.setAppName("CustomLogin");
        jaasRealm.setCredentialHandler(new MessageDigestCredentialHandler());
        jaasRealm.setUserClassNames(TesterPrincipal.class.getName());
        jaasRealm.setRoleClassNames(TesterRolePrincipal.class.getName());
        jaasRealm.setConfigFile(loginConfFile.getAbsolutePath());
        Context context = tomcat.addContext("/jaastest", null);
        context.setRealm(jaasRealm);

        tomcat.start();

        Principal p = jaasRealm.authenticate("foo", "bar");
        Assert.assertNull(p);
        p = jaasRealm.authenticate("tomcatuser", "pass");
        Assert.assertNotNull(p);
        Assert.assertTrue(p instanceof GenericPrincipal);
        GenericPrincipal gp = (GenericPrincipal) p;
        Assert.assertTrue(gp.hasRole("role1"));
    }

}
