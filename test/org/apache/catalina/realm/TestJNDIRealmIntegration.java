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
package org.apache.catalina.realm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.juli.logging.LogFactory;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.AddRequest;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.ResultCode;

@RunWith(Parameterized.class)
public class TestJNDIRealmIntegration {

    private static InMemoryDirectoryServer ldapServer;

    @Parameterized.Parameters(name = "{index}: in[{0}], out[{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { "test", "test", new String[] {"TestGroup"} });

        return parameterSets;
    }


    @Parameter(0)
    public String username;
    @Parameter(1)
    public String credentials;
    @Parameter(2)
    public String[] groups;

    @Test
    public void testAuthenication() throws Exception {
        JNDIRealm realm = new JNDIRealm();
        realm.containerLog = LogFactory.getLog(TestJNDIRealmIntegration.class);

        realm.setConnectionURL("ldap://localhost:" + ldapServer.getListenPort());
        realm.setUserPattern("cn={0},ou=people,dc=example,dc=com");
        realm.setRoleName("cn");
        realm.setRoleBase("ou=people,dc=example,dc=com");
        realm.setRoleSearch("member={0}");

        GenericPrincipal p = (GenericPrincipal) realm.authenticate(username, credentials);

        Assert.assertNotNull(p);
        Assert.assertEquals(username, p.name);

        Set<String> actualGroups = new HashSet<>(Arrays.asList(p.getRoles()));
        Set<String> expectedGroups  = new HashSet<>(Arrays.asList(groups));

        Assert.assertEquals(expectedGroups.size(), actualGroups.size());
        Set<String> tmp = new HashSet<>();
        tmp.addAll(expectedGroups);
        tmp.removeAll(actualGroups);
        Assert.assertEquals(0, tmp.size());
    }


    @BeforeClass
    public static void createLDAP() throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=example,dc=com");
        config.addAdditionalBindCredentials("cn=admin", "password");
        ldapServer = new InMemoryDirectoryServer(config);

        ldapServer.startListening();

        try (LDAPConnection conn =  ldapServer.getConnection()) {

            AddRequest addBase = new AddRequest(
                    "dn: dc=example,dc=com",
                    "objectClass: top",
                    "objectClass: domain",
                    "dc: example");
            LDAPResult result = conn.processOperation(addBase);
            Assert.assertEquals(ResultCode.SUCCESS, result.getResultCode());

            AddRequest addPeople = new AddRequest(
                    "dn: ou=people,dc=example,dc=com",
                    "objectClass: top",
                    "objectClass: organizationalUnit");
            result = conn.processOperation(addPeople);
            Assert.assertEquals(ResultCode.SUCCESS, result.getResultCode());

            AddRequest addUserTest = new AddRequest(
                    "dn: cn=test,ou=people,dc=example,dc=com",
                    "objectClass: top",
                    "objectClass: person",
                    "objectClass: organizationalPerson",
                    "cn: test",
                    "sn: Test",
                    "userPassword: test");
            result = conn.processOperation(addUserTest);
            Assert.assertEquals(ResultCode.SUCCESS, result.getResultCode());

            AddRequest addGroupTest = new AddRequest(
                    "dn: cn=TestGroup,ou=people,dc=example,dc=com",
                    "objectClass: top",
                    "objectClass: groupOfNames",
                    "cn: TestGroup",
                    "member: cn=test,ou=people,dc=example,dc=com");
            result = conn.processOperation(addGroupTest);
            Assert.assertEquals(ResultCode.SUCCESS, result.getResultCode());
        }
    }


    @AfterClass
    public static void destroyLDAP() {
        ldapServer.shutDown(true);
    }
}
