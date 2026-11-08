/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.manager2;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.UserDatabase;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.SimpleHttpClient;

/**
 * Persistence through the manager2 configuration API: the global naming resources round trip and the store
 * preview and save of {@code server.xml} (with backups) to the redirected store base.
 */
public class TestManager2ConfigStore extends Manager2ConfigTestBase {

    @Test
    public void testNamingResources() throws Exception {
        setup(true);

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        String serverNrId = "server/namingResources/0";
        File usersXml = new File(getTemporaryDirectory(), "conf/tomcat-users.xml");
        Assert.assertTrue("Expected the test user file", usersXml.isFile());

        String ctxId = null;
        try {
            // --------------------------- Server level ----------------------

            // The server exposes a single, global naming resources node.
            Map<String, Object> tree = fetchTree(client);
            Map<String, Object> serverNr = findChild(tree, "namingResources", "NamingResourcesImpl");
            Assert.assertNotNull("Expected the global naming resources", serverNr);
            Assert.assertEquals(serverNrId, serverNr.get("id"));
            request(client, "GET", MANAGER2 + "/api/config/node/" + serverNrId, null, null, 200);
            Map<String, Object> nrDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("namingResources", nrDetail.get("type"));
            Assert.assertEquals(Boolean.TRUE, nrDetail.get("global"));

            // Add a global UserDatabase with a first party factory.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"resource\"," +
                            "\"name\":\"UserDatabaseTest\",\"jndiType\":\"org.apache.catalina.UserDatabase\"," +
                            "\"factory\":\"org.apache.catalina.users.MemoryUserDatabaseFactory\"," +
                            "\"params\":{\"pathname\":\"" + usersXml.getAbsolutePath() + "\",\"readonly\":\"true\"}}",
                    200);
            Assert.assertTrue(client.getResponseBody().contains("\"ok\":true"));

            // The resource is bound in the live global naming context and
            // loaded the configured users.
            Object lookedUp = getTomcatInstance().getServer().getGlobalNamingContext().lookup("UserDatabaseTest");
            Assert.assertTrue("Expected a UserDatabase to be bound", lookedUp instanceof UserDatabase);
            Assert.assertNotNull(((UserDatabase) lookedUp).findUser("manager1"));

            // The tree shows the resource and its node detail exposes the
            // closed factory options (with the set values) as parameters.
            tree = fetchTree(client);
            Map<String, Object> dbRes = findChild(findChild(tree, "namingResources", "NamingResourcesImpl"), "resource",
                    "UserDatabaseTest");
            Assert.assertNotNull("Expected the resource in the tree", dbRes);
            Assert.assertEquals(serverNrId + "/resource/UserDatabaseTest", dbRes.get("id"));
            String dbId = (String) dbRes.get("id");
            request(client, "GET", MANAGER2 + "/api/config/node/" + dbId, null, null, 200);
            Map<String, Object> dbDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("resource", dbDetail.get("type"));
            Assert.assertEquals(Boolean.TRUE, findProperty(dbDetail, "name").get("writable"));
            Assert.assertEquals(Boolean.TRUE, findProperty(dbDetail, "pathname").get("param"));
            Assert.assertEquals(usersXml.getAbsolutePath(), findProperty(dbDetail, "pathname").get("value"));
            Assert.assertEquals("true", findProperty(dbDetail, "readonly").get("value"));
            // A closed factory option that is not set is still listed.
            Assert.assertNotNull(findProperty(dbDetail, "watchSource"));

            // Add the other global entry types.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"environment\"," +
                            "\"name\":\"genv\",\"jndiType\":\"java.lang.Integer\",\"value\":\"42\"}",
                    200);
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"ejb\"," +
                            "\"name\":\"gejb\",\"jndiType\":\"org.example.Home\"," + "\"home\":\"org.example.Home\"}",
                    200);
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"localEjb\"," +
                            "\"name\":\"glejb\",\"jndiType\":\"org.example.Local\"," +
                            "\"local\":\"org.example.Local\"}",
                    200);
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"serviceRef\"," +
                            "\"name\":\"gservice\",\"jndiType\":\"org.example.Svc\"," +
                            "\"interface\":\"org.example.Svc\",\"displayname\":\"test\"}",
                    200);
            tree = fetchTree(client);
            Map<String, Object> serverNr2 = findChild(tree, "namingResources", "NamingResourcesImpl");
            Assert.assertNotNull(findChild(serverNr2, "environment", "genv"));
            Assert.assertNotNull(findChild(serverNr2, "ejb", "gejb"));
            Assert.assertNotNull(findChild(serverNr2, "localEjb", "glejb"));
            Assert.assertNotNull(findChild(serverNr2, "serviceRef", "gservice"));

            // The generic string parameters of every JNDI entry type (the
            // ResourceBase property map) are shown in the entry detail and
            // can be added, edited and removed there.
            String genvId = serverNrId + "/environment/genv";
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + genvId + "\",\"name\":\"envKey\",\"value\":\"envValue\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + genvId, null, null, 200);
            Map<String, Object> genvDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("envValue", findProperty(genvDetail, "envKey").get("value"));
            Assert.assertEquals(Boolean.TRUE, findProperty(genvDetail, "envKey").get("param"));
            // An ejb and a web service reference expose their parameters too.
            String gejbId = serverNrId + "/ejb/gejb";
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + gejbId + "\",\"name\":\"ejbKey\",\"value\":\"ejbValue\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + gejbId, null, null, 200);
            Assert.assertEquals("ejbValue", findProperty(parseObject(client.getResponseBody()), "ejbKey").get("value"));
            String gsvcId = serverNrId + "/serviceRef/gservice";
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + gsvcId + "\",\"name\":\"svcKey\",\"value\":\"svcValue\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + gsvcId, null, null, 200);
            Assert.assertEquals("svcValue", findProperty(parseObject(client.getResponseBody()), "svcKey").get("value"));
            // A parameter can be edited and then removed (by clearing it).
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + genvId + "\",\"name\":\"envKey\",\"value\":\"envValue2\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + genvId, null, null, 200);
            Assert.assertEquals("envValue2",
                    findProperty(parseObject(client.getResponseBody()), "envKey").get("value"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + genvId + "\",\"name\":\"envKey\",\"value\":\"\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + genvId, null, null, 200);
            Assert.assertNull(findProperty(parseObject(client.getResponseBody()), "envKey"));

            // Guards (server level).
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + serverNrId +
                    "\",\"type\":\"resource\"," + "\"name\":\"UserDatabaseTest\",\"jndiType\":\"java.lang.String\"}",
                    409);
            Assert.assertTrue(client.getResponseBody().contains("DUPLICATE"));
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"resource\",\"name\":\"noType\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("MISSING_FIELD"));
            // Resource links are not part of the global naming environment.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"resourceLink\"," +
                            "\"name\":\"glink\",\"jndiType\":\"java.lang.String\",\"global\":\"x\"}",
                    400);
            Assert.assertTrue(client.getResponseBody().contains("BAD_PARENT"));
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + serverNrId + "\",\"type\":\"resource\"," +
                            "\"name\":\"badfactory\",\"jndiType\":\"javax.sql.DataSource\"," +
                            "\"factory\":\"org.example.NoSuchFactory\"}",
                    400);
            Assert.assertTrue(client.getResponseBody().contains("INVALID_CLASS"));

            // A parameter update re-registers the entry, so the live JNDI
            // environment reflects the change (and stays bound).
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dbId + "\",\"name\":\"readonly\",\"value\":\"false\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + dbId, null, null, 200);
            Assert.assertEquals("false", findProperty(parseObject(client.getResponseBody()), "readonly").get("value"));
            Assert.assertNotNull(getTomcatInstance().getServer().getGlobalNamingContext().lookup("UserDatabaseTest"));

            // A boolean factory option also accepts the JSON boolean the
            // checkbox of the form sends (it is stored as its string
            // form), and a value that does not fit the declared type of
            // the option is rejected (the stored value is unchanged).
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dbId + "\",\"name\":\"readonly\",\"value\":true}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + dbId, null, null, 200);
            Assert.assertEquals("true", findProperty(parseObject(client.getResponseBody()), "readonly").get("value"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dbId + "\",\"name\":\"readonly\",\"value\":\"not-a-boolean\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("SET_FAILED"));
            request(client, "GET", MANAGER2 + "/api/config/node/" + dbId, null, null, 200);
            Assert.assertEquals("true", findProperty(parseObject(client.getResponseBody()), "readonly").get("value"));

            // Removing a typed factory option clears the parameter (it
            // must not be stored as the string "false"): the option is
            // listed again with no value and can be set again.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dbId + "\",\"name\":\"readonly\",\"value\":\"\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + dbId, null, null, 200);
            Assert.assertNull(findProperty(parseObject(client.getResponseBody()), "readonly").get("value"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dbId + "\",\"name\":\"readonly\",\"value\":\"true\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + dbId, null, null, 200);
            Assert.assertEquals("true", findProperty(parseObject(client.getResponseBody()), "readonly").get("value"));

            // Renaming requires a type-to-confirm and rebinds the resource.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dbId + "\",\"name\":\"name\"," + "\"value\":\"UserDatabaseRenamed\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("CONFIRM_REQUIRED"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token, "{\"id\":\"" + dbId +
                    "\",\"name\":\"name\"," + "\"value\":\"UserDatabaseRenamed\",\"confirm\":\"UserDatabaseTest\"}",
                    200);
            Assert.assertNotNull(
                    getTomcatInstance().getServer().getGlobalNamingContext().lookup("UserDatabaseRenamed"));
            try {
                getTomcatInstance().getServer().getGlobalNamingContext().lookup("UserDatabaseTest");
                Assert.fail("Expected the old name to be unbound");
            } catch (Exception e) {
                Assert.assertTrue(e instanceof javax.naming.NameNotFoundException);
            }
            String dbId2 = serverNrId + "/resource/UserDatabaseRenamed";

            // A free form parameter can be added and cleared.
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dbId2 + "\",\"name\":\"extraKey\",\"value\":\"hello\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + dbId2, null, null, 200);
            Assert.assertEquals("hello", findProperty(parseObject(client.getResponseBody()), "extraKey").get("value"));
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dbId2 + "\",\"name\":\"extraKey\",\"value\":\"\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + dbId2, null, null, 200);
            Assert.assertNull(findProperty(parseObject(client.getResponseBody()), "extraKey"));

            // The global naming resources node is required (cannot be removed).
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + serverNrId + "\",\"confirm\":\"x\"}", 400);
            Assert.assertTrue(client.getResponseBody().contains("REQUIRED_COMPONENT"));

            // The global naming resources round trip to server.xml,
            // including the web service reference (ServiceRef).
            File storeBase = new File(getTemporaryDirectory(), "store-naming-base");
            File storeConf = new File(storeBase, "conf");
            Assert.assertTrue(storeConf.mkdirs());
            addDeleteOnTearDown(storeBase);
            setStoreBase(storeBase);
            request(client, "GET", MANAGER2 + "/api/config/store/preview", null, null, 200);
            String xml = (String) parseObject(client.getResponseBody()).get("xml");
            Assert.assertTrue(xml.contains("<GlobalNamingResources"));
            Assert.assertTrue(xml.contains("UserDatabaseRenamed"));
            Assert.assertTrue(xml.contains("<ServiceRef"));
            Assert.assertTrue(xml.contains("<EJB"));
            Assert.assertTrue(xml.contains("<Environment"));
            // The generic parameters of the entries round trip too.
            Assert.assertTrue(xml.contains("ejbKey=\"ejbValue\""));
            Assert.assertTrue(xml.contains("svcKey=\"svcValue\""));

            // --------------------------- Context level ---------------------

            Map<String, Object> engine = firstChildOfType(firstChildOfType(tree, "service"), "engine");
            String hostId = (String) findChild(engine, "host", "localhost").get("id");
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + hostId + "\",\"type\":\"context\",\"path\":\"/comptest\"}", 200);
            tree = fetchTree(client);
            Map<String, Object> engine2 = firstChildOfType(firstChildOfType(tree, "service"), "engine");
            Map<String, Object> ctxEntry = findChild(findChild(engine2, "host", "localhost"), "context", "/comptest");
            Assert.assertNotNull("Expected the new context", ctxEntry);
            ctxId = (String) ctxEntry.get("id");
            String ctxNrId = ctxId + "/namingResources/0";

            // A context naming resources node is not global.
            request(client, "GET", MANAGER2 + "/api/config/node/" + ctxNrId, null, null, 200);
            Assert.assertEquals(Boolean.FALSE, parseObject(client.getResponseBody()).get("global"));

            // A context may hold resource links (unlike the server).
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + ctxNrId + "\",\"type\":\"resourceLink\"," +
                            "\"name\":\"link/db\",\"jndiType\":\"org.apache.catalina.UserDatabase\"," +
                            "\"global\":\"UserDatabaseRenamed\"}",
                    200);
            // A context resource that resolves to the default (first party)
            // data source factory from its type.
            request(client, "POST", MANAGER2 + "/api/config/child", token,
                    "{\"parent\":\"" + ctxNrId + "\",\"type\":\"resource\"," +
                            "\"name\":\"jdbc/ctxds\",\"jndiType\":\"javax.sql.DataSource\"," +
                            "\"params\":{\"url\":\"jdbc:h2:mem:ctx\",\"username\":\"sa\"," + "\"maxTotal\":\"10\"}}",
                    200);

            StandardContext context = (StandardContext) getTomcatInstance().getHost().findChild("/comptest");
            javax.naming.Context envCtx = context.getNamingContextListener().getEnvContext();
            // The link is configured to point to the global resource.
            request(client, "GET", MANAGER2 + "/api/config/node/" + ctxNrId + "/resourceLink/link+db", null, null, 200);
            Assert.assertEquals("UserDatabaseRenamed",
                    findProperty(parseObject(client.getResponseBody()), "global").get("value"));
            // The resource is bound in the live context environment.
            Object ds = envCtx.lookup("jdbc/ctxds");
            Assert.assertTrue("Expected a DataSource to be bound", ds instanceof javax.sql.DataSource);

            tree = fetchTree(client);
            Map<String, Object> ctxNr = findChild(findChildById(tree, ctxId), "namingResources", "NamingResourcesImpl");
            Assert.assertNotNull(findChild(ctxNr, "resourceLink", "link/db"));
            Assert.assertNotNull(findChild(ctxNr, "resource", "jdbc/ctxds"));
            String dsId = ctxNrId + "/resource/jdbc+ctxds";
            request(client, "GET", MANAGER2 + "/api/config/node/" + dsId, null, null, 200);
            Map<String, Object> dsDetail = parseObject(client.getResponseBody());
            Assert.assertEquals("jdbc:h2:mem:ctx", findProperty(dsDetail, "url").get("value"));
            Assert.assertEquals("10", findProperty(dsDetail, "maxTotal").get("value"));

            // A parameter update re-registers the entry (the live re-lookup
            // still resolves).
            request(client, "POST", MANAGER2 + "/api/config/attribute", token,
                    "{\"id\":\"" + dsId + "\",\"name\":\"maxTotal\",\"value\":\"5\"}", 200);
            request(client, "GET", MANAGER2 + "/api/config/node/" + dsId, null, null, 200);
            Assert.assertEquals("5", findProperty(parseObject(client.getResponseBody()), "maxTotal").get("value"));
            Assert.assertTrue(envCtx.lookup("jdbc/ctxds") instanceof javax.sql.DataSource);

            // Context guard: duplicate name.
            request(client, "POST", MANAGER2 + "/api/config/child", token, "{\"parent\":\"" + ctxNrId +
                    "\",\"type\":\"resource\"," + "\"name\":\"jdbc/ctxds\",\"jndiType\":\"java.lang.String\"}", 409);
            Assert.assertTrue(client.getResponseBody().contains("DUPLICATE"));

            // Removing an entry (type-to-confirm) unbinds it.
            request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                    "{\"id\":\"" + dsId + "\",\"confirm\":\"jdbc/ctxds\"}", 200);
            try {
                envCtx.lookup("jdbc/ctxds");
                Assert.fail("Expected the resource to be unbound");
            } catch (Exception e) {
                Assert.assertTrue(e instanceof javax.naming.NameNotFoundException);
            }
        } finally {
            // Best effort cleanup (ignore failures: the test failure itself
            // is reported).
            try {
                if (ctxId != null) {
                    request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                            "{\"id\":\"" + ctxId + "\",\"confirm\":\"/comptest\"}", 200);
                }
            } catch (Throwable e) {
                // Best effort.
            }
            String[][] globals = { { "resource", "UserDatabaseRenamed" }, { "environment", "genv" }, { "ejb", "gejb" },
                    { "localEjb", "glejb" }, { "serviceRef", "gservice" } };
            for (String[] g : globals) {
                try {
                    request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                            "{\"id\":\"" + serverNrId + "/" + g[0] + "/" + g[1] + "\",\"confirm\":\"" + g[1] + "\"}",
                            200);
                } catch (Throwable e) {
                    // Best effort.
                }
            }
        }

        client.disconnect();
    }


    @Test
    public void testStorePreviewDoesNotWrite() throws Exception {
        setup();

        File storeBase = new File(getTemporaryDirectory(), "store-preview-base");
        File conf = new File(storeBase, "conf");
        Assert.assertTrue(conf.mkdirs());
        addDeleteOnTearDown(storeBase);
        setStoreBase(storeBase);

        int before = conf.list().length;

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        login(client, "manager1");

        request(client, "GET", MANAGER2 + "/api/config/store/preview", null, null, 200);
        Map<String, Object> res = parseObject(client.getResponseBody());
        String xml = (String) res.get("xml");
        Assert.assertNotNull(xml);
        Assert.assertTrue(xml.contains("<Server"));
        // The live state (the default host) is part of the preview.
        Assert.assertTrue(xml.contains("localhost"));

        // The preview reports the external context files that would be
        // rewritten (the manager runs from its own context.xml) and that the
        // save restarts the manager.
        @SuppressWarnings("unchecked")
        List<Object> files = (List<Object>) res.get("files");
        Assert.assertNotNull(files);
        Assert.assertTrue(files.stream().anyMatch(f -> String.valueOf(f).endsWith("context.xml")));
        Assert.assertEquals(Boolean.TRUE, res.get("restartsManager"));

        // The preview must not have written anything to the conf directory.
        Assert.assertEquals(before, conf.list().length);

        client.disconnect();
    }


    @Test
    public void testStoreWritesFileAndBackup() throws Exception {
        setup();

        File storeBase = new File(getTemporaryDirectory(), "store-base");
        File conf = new File(storeBase, "conf");
        Assert.assertTrue(conf.mkdirs());
        addDeleteOnTearDown(storeBase);
        setStoreBase(storeBase);
        // A pre-existing server.xml so that a backup can be created.
        try (PrintWriter pw = new PrintWriter(new File(conf, "server.xml"), StandardCharsets.UTF_8)) {
            pw.println("<Server port=\"8005\" shutdown=\"SHUTDOWN\"></Server>");
        }

        SimpleHttpClient client = new TestClient();
        client.setPort(getPort());
        client.connect();
        String token = loginAndGetToken(client, "manager1");

        String ctxId = selfContextId(fetchTree(client));
        String hostId = hostIdOf(ctxId);

        // Add a recognizable component that must end up in the stored file.
        request(client, "POST", MANAGER2 + "/api/config/child", token,
                "{\"parent\":\"" + hostId + "\",\"type\":\"alias\",\"alias\":\"store-test-alias\"}", 200);

        try {
            request(client, "POST", MANAGER2 + "/api/config/store", token, "{}", 200);
            Map<String, Object> res = parseObject(client.getResponseBody());
            Assert.assertEquals(Boolean.TRUE, res.get("ok"));
            Assert.assertEquals("conf/server.xml", res.get("file"));
            String backup = (String) res.get("backup");
            Assert.assertNotNull("Expected a backup file name", backup);

            // The live state (including the added alias) was written.
            String written = readFile(new File(conf, "server.xml"));
            Assert.assertTrue(written.contains("store-test-alias"));
            Assert.assertTrue(written.contains("<Server"));

            // A timestamped backup of the previous file was kept.
            Assert.assertTrue(new File(conf, backup).isFile());

            // Contexts are kept in their current location: the manager runs
            // from its own context.xml, so the store rewrote that (external)
            // file as well - creating a timestamped backup next to it -
            // which is why the save resets the admin session.
            File selfCtxDir = new File(manager2DocBase, "META-INF");
            String[] selfCtxFiles = selfCtxDir.list();
            boolean selfCtxBackup = false;
            if (selfCtxFiles != null) {
                for (String name : selfCtxFiles) {
                    if (name.startsWith("context.xml.")) {
                        selfCtxBackup = true;
                        break;
                    }
                }
            }
            Assert.assertTrue(
                    "Expected the manager context file to be rewritten " + "(a timestamped backup was created)",
                    selfCtxBackup);
        } finally {
            // The store rewrote the manager's own (watched) context file,
            // which restarts the context and resets the session, so clean up
            // with a fresh login.
            try {
                String token2 = loginAndGetToken(client, "manager1");
                request(client, "DELETE", MANAGER2 + "/api/config/child", token2,
                        "{\"id\":\"" + hostId + "/alias/store-test-alias\"}", 200);
            } catch (Throwable e) {
                // Best effort: the context may still be restarting.
            }
        }

        client.disconnect();
    }
}
