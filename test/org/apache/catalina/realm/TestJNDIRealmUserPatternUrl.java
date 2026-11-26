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

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.juli.logging.LogFactory;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSimpleBindRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPConnection;

/*
 * If the user pattern starts with the user name, the user controls the start of the DN. JNDI treats a name that starts
 * with a URL scheme as a URL and connects to the host in the URL using the credentials of the realm. Make sure that
 * can't be used to authenticate against a directory the user controls.
 */
public class TestJNDIRealmUserPatternUrl {

    private static final String SERVICE_DN = "cn=svc,dc=example,dc=com";
    private static final String SERVICE_PASSWORD = "s3cret";

    private static final List<String> attackerBinds = new CopyOnWriteArrayList<>();

    private static InMemoryDirectoryServer legitimateServer;
    private static InMemoryDirectoryServer attackerServer;


    @BeforeClass
    public static void createLDAP() throws Exception {
        legitimateServer = createServer(null);
        try (LDAPConnection conn = legitimateServer.getConnection()) {
            conn.add(new Entry("dn: uid=alice,ou=people,dc=example,dc=com", "objectClass: top",
                    "objectClass: inetOrgPerson", "uid: alice", "cn: alice", "sn: alice", "userPassword: alicepw",
                    "description: user"));
        }

        attackerServer = createServer(new InMemoryOperationInterceptor() {
            @Override
            public void processSimpleBindRequest(InMemoryInterceptedSimpleBindRequest request) {
                attackerBinds.add(request.getRequest().getBindDN() + " / " +
                        request.getRequest().getPassword().stringValue());
            }
        });
        try (LDAPConnection conn = attackerServer.getConnection()) {
            conn.add(new Entry("dn: uid=evil,ou=people,dc=example,dc=com", "objectClass: top",
                    "objectClass: inetOrgPerson", "uid: evil", "cn: evil", "sn: evil", "userPassword: evilpw",
                    "description: admin"));
        }
    }


    private static InMemoryDirectoryServer createServer(InMemoryOperationInterceptor interceptor) throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=example,dc=com");
        config.setListenerConfigs(
                new InMemoryListenerConfig("localListener", InetAddress.getByName("localhost"), 0, null, null, null));
        config.addAdditionalBindCredentials(SERVICE_DN, SERVICE_PASSWORD);
        if (interceptor != null) {
            config.addInMemoryOperationInterceptor(interceptor);
        }
        InMemoryDirectoryServer server = new InMemoryDirectoryServer(config);
        server.startListening();
        try (LDAPConnection conn = server.getConnection()) {
            conn.add(new Entry("dn: dc=example,dc=com", "objectClass: top", "objectClass: domain", "dc: example"));
            conn.add(new Entry("dn: ou=people,dc=example,dc=com", "objectClass: top",
                    "objectClass: organizationalUnit", "ou: people"));
        }
        return server;
    }


    @AfterClass
    public static void destroyLDAP() {
        legitimateServer.shutDown(true);
        attackerServer.shutDown(true);
    }


    @Before
    public void clearBinds() {
        attackerBinds.clear();
    }


    @Test
    public void testRdnWithLeadingPlaceholder() {
        GenericPrincipal principal =
                (GenericPrincipal) createRealm("{0},ou=people,dc=example,dc=com").authenticate("uid=alice", "alicepw");

        Assert.assertNotNull(principal);
        Assert.assertEquals("uid=alice", principal.getName());
        Assert.assertArrayEquals(new String[] { "user" }, principal.getRoles());
    }


    @Test
    public void testLdapUrlLeadingPlaceholder() {
        doTestUrl("{0},ou=people,dc=example,dc=com", "ldap://localhost:" + attackerServer.getListenPort() + "/uid=evil");
    }


    @Test
    public void testLdapUrlPlaceholderOnly() {
        doTestUrl("{0}", "ldap://localhost:" + attackerServer.getListenPort() +
                "/uid=evil,ou=people,dc=example,dc=com");
    }


    @Test
    public void testLdapUrlUpperCase() {
        doTestUrl("{0},ou=people,dc=example,dc=com", "LDAP://localhost:" + attackerServer.getListenPort() + "/uid=evil");
    }


    @Test
    public void testOtherUrlScheme() {
        doTestUrl("{0},ou=people,dc=example,dc=com", "ldaps://localhost:" + attackerServer.getListenPort() +
                "/uid=evil");
        doTestUrl("{0},ou=people,dc=example,dc=com", "rmi://localhost:1/uid=evil");
    }


    /*
     * The user name is not at the start of the DN so JNDI will not treat the DN as a URL.
     */
    @Test
    public void testLdapUrlNotAtStart() {
        doTestUrl("uid={0},ou=people,dc=example,dc=com", "ldap://localhost:" + attackerServer.getListenPort() +
                "/uid=evil");
    }


    private void doTestUrl(String userPattern, String username) {
        Assert.assertNull(createRealm(userPattern).authenticate(username, "evilpw"));
        // The directory the user controls must not have been contacted using the credentials of the realm
        Assert.assertTrue(attackerBinds.toString(), attackerBinds.isEmpty());
    }


    private JNDIRealm createRealm(String userPattern) {
        JNDIRealm realm = new JNDIRealm();
        realm.containerLog = LogFactory.getLog(TestJNDIRealmUserPatternUrl.class);
        realm.setConnectionURL("ldap://localhost:" + legitimateServer.getListenPort());
        realm.setConnectionName(SERVICE_DN);
        realm.setConnectionPassword(SERVICE_PASSWORD);
        realm.setUserPattern(userPattern);
        realm.setUserPassword("userPassword");
        realm.setUserRoleName("description");
        realm.setCredentialHandler(new MessageDigestCredentialHandler());
        return realm;
    }
}
