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
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.After;
import org.junit.Assert;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.ExpandWar;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.json.JSONParser;


/**
 * Shared base for the integration tests of the manager2 configuration API ({@code /api/config/*}). The tests deploy
 * the {@code manager2.war} built by this module (via the {@code deploy} target) into a throw-away Tomcat instance and
 * drive it over HTTP with {@link SimpleHttpClient}, exercising the component tree, attribute updates, structural
 * add/remove of child components, lifecycle operations (start / stop / restart), and persistence to
 * {@code server.xml} through storeconfig.
 *
 * <p>
 * The test methods are split over the concrete subclasses so that they can run in parallel: the Ant JUnit task
 * parallelizes at the granularity of a test class (the methods of one class always run sequentially in a single
 * thread), so one large class would serialize the whole configuration suite. The name of this base class deliberately
 * does not start with {@code Test}: the batch test of the module build only picks up {@code Test*.java} (the
 * {@code skipNonTests} option is a second line of defense).
 *
 * <p>
 * All mutable state lives under {@link #getTemporaryDirectory()} (unique per test class): the webapp is deployed from
 * a private copy so that the store tests can rewrite the manager's own context file without restarting the contexts
 * of the other test classes, and the {@code manager2.store.base} system property is set per method and cleared in the
 * {@code @After} tear down.
 */
public abstract class Manager2ConfigTestBase extends TomcatBaseTest {

    protected static final String MANAGER2 = "/manager2";

    private String storeBaseProp = null;

    protected File manager2DocBase = null;


    @After
    public void clearStoreBase() {
        if (storeBaseProp != null) {
            System.clearProperty("manager2.store.base");
            storeBaseProp = null;
        }
    }


    /**
     * Fetch the node detail of the component with the given id.
     *
     * @param client    The client to use
     * @param id        The component id
     *
     * @return The parsed node detail
     */
    protected Map<String, Object> fetchNode(SimpleHttpClient client, String id) throws Exception {
        request(client, "GET", MANAGER2 + "/api/config/node/" + id, null, null, 200);
        return parseObject(client.getResponseBody());
    }


    /**
     * Count the direct children of a node with the given type.
     *
     * @param node      The node
     * @param type      The child type to count
     *
     * @return The number of direct children of the given type
     */
    protected static long countChildrenOfType(Map<String, Object> node, String type) {
        List<Object> children = getList(node, "children");
        if (children == null) {
            return 0;
        }
        long count = 0;
        for (Object child : children) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cm = (Map<String, Object>) child;
            if (type.equals(cm.get("type"))) {
                count++;
            }
        }
        return count;
    }


    // -----------------------------------------------------------------------


    protected void setStoreBase(File storeBase) {
        System.setProperty("manager2.store.base", storeBase.getAbsolutePath());
        storeBaseProp = storeBase.getAbsolutePath();
    }


    /**
     * Best effort cleanup for the add/remove test if a removal failed midway: remove any components that are still
     * present so the shared instance and subsequent tests are not affected.
     *
     * @param client        The client to use
     * @param token         The CSRF token
     * @param serviceId     Id of the service to remove, if any
     * @param hostId        Id of the host to remove, if any
     * @param contextId     Id of the context to remove, if any
     * @param wrapperId     Id of the wrapper to remove, if any
     * @param valveId       Id of the valve to remove, if any
     * @param connectorId   Id of the connector to remove, if any
     * @param executorId    Id of the executor to remove, if any
     * @param aliasId       Id of the alias to remove, if any
     *
     * @throws Exception If the cleanup requests cannot be exchanged
     */
    protected void cleanup(SimpleHttpClient client, String token, String serviceId, String hostId, String contextId,
            String wrapperId, String valveId, String connectorId, String executorId, String aliasId) throws Exception {
        for (String id : new String[] { connectorId, executorId, wrapperId, valveId, aliasId, contextId, hostId,
                serviceId }) {
            if (id == null) {
                continue;
            }
            try {
                request(client, "DELETE", MANAGER2 + "/api/config/child", token,
                        "{\"id\":\"" + id + "\",\"confirm\":\"confirm\"}", 200);
            } catch (AssertionError e) {
                // Already removed (or never created): ignore.
            }
        }
    }


    protected static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }


    /**
     * Issue an HTTPS GET against the given port with a trust-all trust manager (the test certificate is self signed).
     * Returns the HTTP status code; any status proves that the TLS handshake succeeded and the connector served the
     * request.
     *
     * @param port      The port to connect to
     * @param path      The request path
     *
     * @return The HTTP status code
     *
     * @throws Exception If the request fails
     */
    protected static int httpsGet(int port, String path) throws Exception {
        TrustManager[] trustAll = new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new SecureRandom());
        HttpsURLConnection connection = (HttpsURLConnection) URI.create("https://localhost:" + port + path).toURL()
                .openConnection();
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        connection.setHostnameVerifier((hostname, session) -> true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }


    /**
     * Issue a plain (non-TLS) HTTP GET against the given port and return the HTTP status code (or a negative value when
     * no HTTP response was received at all).
     *
     * @param port      The port to connect to
     * @param path      The request path
     *
     * @return The HTTP status code, or a negative value if no response was received
     */
    protected static int httpGetPlain(int port, String path) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) URI.create(
                    "http://localhost:" + port + path).toURL().openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            try {
                return connection.getResponseCode();
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            return -1;
        }
    }


    /**
     * Create a PKCS12 keystore with a self signed RSA certificate (key alias "tomcat", passwords "changeit") using the
     * keytool of the JDK the tests run on.
     *
     * @param dir       The directory to create the keystore in
     *
     * @return The generated keystore file
     *
     * @throws Exception If keytool fails
     */
    protected File createKeystore(File dir) throws Exception {
        Assert.assertTrue(dir.isDirectory() || dir.mkdirs());
        File keytool = new File(new File(System.getProperty("java.home"), "bin"), "keytool");
        File keystore = new File(dir, "e2e-keystore.p12");
        ProcessBuilder builder = new ProcessBuilder(keytool.getAbsolutePath(), "-genkeypair", "-alias", "tomcat",
                "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12", "-keystore", keystore.getAbsolutePath(),
                "-storepass", "changeit", "-keypass", "changeit", "-dname", "CN=localhost", "-validity", "30");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        Assert.assertEquals("keytool failed: " + output, 0, exit);
        Assert.assertTrue(keystore.isFile());
        addDeleteOnTearDown(keystore);
        return keystore;
    }


    protected static String readFile(File file) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }


    // ------------------------------------------------------- JSON navigation


    protected static Map<String, Object> parseObject(String json) throws Exception {
        JSONParser parser = new JSONParser(json);
        parser.setNativeNumbers(true);
        return (Map<String, Object>) parser.parseObject();
    }


    @SuppressWarnings("unchecked")
    protected static Map<String, Object> getMap(Map<String, Object> map, String key) {
        return (Map<String, Object>) map.get(key);
    }


    @SuppressWarnings("unchecked")
    protected static String propertyDescription(Map<String, Object> node, String name) {
        for (Object entry : getList(node, "properties")) {
            Map<String, Object> property = (Map<String, Object>) entry;
            if (name.equals(property.get("name"))) {
                return (String) property.get("description");
            }
        }
        return null;
    }


    @SuppressWarnings("unchecked")
    protected static List<Object> getList(Map<String, Object> map, String key) {
        return (List<Object>) map.get(key);
    }


    protected static Number getInt(Object value) {
        return (Number) value;
    }


    /**
     * Find a direct child node (from a node's {@code children} list) by type and name.
     *
     * @param node      The parent node
     * @param type      The child type
     * @param name      The child name
     *
     * @return The child node, or <code>null</code>
     */
    protected static Map<String, Object> findChild(Map<String, Object> node, String type, String name) {
        List<Object> children = getList(node, "children");
        if (children == null) {
            return null;
        }
        for (Object child : children) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cm = (Map<String, Object>) child;
            if (type.equals(cm.get("type")) && name.equals(cm.get("name"))) {
                return cm;
            }
        }
        return null;
    }


    /**
     * Find a node with the given id anywhere in the tree.
     *
     * @param node      The node to search (subtrees included)
     * @param id        The id to look for
     *
     * @return The node with the given id, or <code>null</code>
     */
    protected static Map<String, Object> findChildById(Map<String, Object> node, String id) {
        if (id.equals(node.get("id"))) {
            return node;
        }
        List<Object> children = getList(node, "children");
        if (children != null) {
            for (Object child : children) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cm = (Map<String, Object>) child;
                Map<String, Object> found = findChildById(cm, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }


    /**
     * Find the first direct child node (from a node's {@code children} list) of the given type.
     *
     * @param node      The parent node
     * @param type      The child type
     *
     * @return The first child of the given type, or <code>null</code>
     */
    protected static Map<String, Object> firstChildOfType(Map<String, Object> node, String type) {
        List<Object> children = getList(node, "children");
        if (children == null) {
            return null;
        }
        for (Object child : children) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cm = (Map<String, Object>) child;
            if (type.equals(cm.get("type"))) {
                return cm;
            }
        }
        return null;
    }


    /**
     * Fetch the component tree.
     *
     * @param client    The client to use
     *
     * @return The parsed component tree root
     *
     * @throws Exception If the tree cannot be fetched
     */
    protected Map<String, Object> fetchTree(SimpleHttpClient client) throws Exception {
        request(client, "GET", MANAGER2 + "/api/config/tree", null, null, 200);
        return getMap(parseObject(client.getResponseBody()), "tree");
    }


    /**
     * Resolve the id of the manager2 (self) context from the tree. The service and engine names are not fixed (they
     * depend on how the Tomcat instance was created), so the path must not be hard-coded.
     *
     * @param tree      The component tree
     *
     * @return The id of the manager2 (self) context
     */
    protected static String selfContextId(Map<String, Object> tree) {
        Map<String, Object> service = firstChildOfType(tree, "service");
        Assert.assertNotNull("Expected a service", service);
        Map<String, Object> engine = firstChildOfType(service, "engine");
        Assert.assertNotNull("Expected an engine", engine);
        Map<String, Object> host = firstChildOfType(engine, "host");
        Assert.assertNotNull("Expected a host", host);
        Map<String, Object> context = firstChildOfType(host, "context");
        Assert.assertNotNull("Expected the manager2 context", context);
        Assert.assertEquals(Boolean.TRUE, context.get("self"));
        return (String) context.get("id");
    }


    /**
     * The id of the host that owns the context with the given id.
     *
     * @param contextId The id of the context
     *
     * @return The id of the host that owns the context
     */
    protected static String hostIdOf(String contextId) {
        int ix = contextId.lastIndexOf('/');
        return contextId.substring(0, contextId.lastIndexOf('/', ix - 1));
    }


    /**
     * Find a property entry (from a node's {@code properties} list) by name.
     *
     * @param node      The node
     * @param name      The property name
     *
     * @return The property entry, or <code>null</code>
     */
    protected static Map<String, Object> findProperty(Map<String, Object> node, String name) {
        List<Object> properties = getList(node, "properties");
        if (properties == null) {
            return null;
        }
        for (Object property : properties) {
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) property;
            if (name.equals(p.get("name"))) {
                return p;
            }
        }
        return null;
    }


    // -----------------------------------------------------------------------


    protected void setup() throws Exception {
        setup(false);
    }


    protected void setup(boolean withNaming) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.setAddDefaultWebXmlToWebapp(false);
        if (withNaming) {
            // Enable JNDI naming so that JNDI resources can be registered
            // in (and looked up from) the global naming context of the
            // server, like the file based one of the default server.xml.
            tomcat.enableNaming();
        }

        // A conf/tomcat-users.xml with the test users so that MemoryRealm
        // instances can be started, mirroring a real CATALINA_BASE layout.
        File conf = new File(getTemporaryDirectory(), "conf");
        Assert.assertTrue(conf.isDirectory() || conf.mkdirs());
        try (PrintWriter pw = new PrintWriter(new File(conf, "tomcat-users.xml"), StandardCharsets.UTF_8)) {
            pw.println("<tomcat-users>");
            pw.println("  <role rolename=\"manager-gui\"/>");
            pw.println("  <role rolename=\"manager-status\"/>");
            pw.println("  <user username=\"manager1\" password=\"secret\" roles=\"manager-gui\"/>");
            pw.println("  <user username=\"status1\" password=\"secret\" roles=\"manager-status\"/>");
            pw.println("</tomcat-users>");
        }

        // The programmatic Tomcat API installs a private internal realm that
        // storeconfig cannot serialise. Use the same realm type as the
        // production server.xml; it loads the users from the file above.
        ((StandardEngine) tomcat.getEngine()).setRealm(new MemoryRealm());

        File webapps = new File(getBuildDirectory(), "webapps");
        File appDir = new File(webapps, "manager2");
        File appWar = new File(webapps, "manager2.war");
        File source = appDir.exists() ? appDir : appWar;
        Assert.assertTrue("manager2 webapp missing - run 'ant deploy' first", source.exists());

        // Deploy from a private copy of the webapp under the per-test-class
        // temporary directory. The store test makes the manager rewrite its
        // own (watched) META-INF/context.xml; sharing the docBase in the
        // build directory would restart the contexts of the other test
        // classes that deploy from the same directory in parallel.
        manager2DocBase = new File(getTemporaryDirectory(),
                source.isDirectory() ? "manager2-docbase" : "manager2-docbase.war");
        ExpandWar.delete(manager2DocBase);
        Assert.assertTrue(manager2DocBase.getParentFile().isDirectory() ||
                manager2DocBase.getParentFile().mkdirs());
        recursiveCopy(source.toPath(), manager2DocBase.toPath());

        Context manager2 = tomcat.addWebapp(null, MANAGER2, manager2DocBase.getAbsolutePath());
        addDefaultServlet(manager2);
        manager2.addMimeMapping("css", "text/css");
        manager2.addMimeMapping("js", "text/javascript");
        manager2.addMimeMapping("html", "text/html");

        tomcat.start();
    }


    private void addDefaultServlet(Context context) {
        Tomcat.addServlet(context, "default", new DefaultServlet());
        context.addServletMapping("/", "default");
    }


    /**
     * Establish a login session for the given user via the FORM login.
     *
     * @param client    The client to log in
     * @param user      The user name (password is always "secret")
     *
     * @throws Exception If the login cannot be completed
     */
    protected void login(SimpleHttpClient client, String user) throws Exception {
        client.setRequest(new String[] { "GET " + MANAGER2 + "/api/info HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Connection: Close" + CRLF, CRLF });
        client.connect();
        client.processRequest(true);

        Assert.assertNotNull("Expected a session to be established", client.getSessionId());

        String body = "j_username=" + user + "&j_password=secret";
        client.setRequest(new String[] { "POST " + MANAGER2 + "/j_security_check HTTP/1.1" + CRLF,
                "Host: localhost:" + getPort() + CRLF, "Cookie: JSESSIONID=" + client.getSessionId() + CRLF,
                "Content-Type: application/x-www-form-urlencoded" + CRLF,
                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + CRLF, "Connection: Close" + CRLF,
                CRLF, body });
        client.connect();
        client.processRequest(true);

        Assert.assertEquals(303, client.getStatusCode());
    }


    protected String loginAndGetToken(SimpleHttpClient client, String user) throws Exception {
        login(client, user);
        request(client, "GET", MANAGER2 + "/api/info", null, null, 200);
        String token = getCsrfToken(client);
        Assert.assertNotNull("Expected an X-CSRF-Token header", token);
        return token;
    }


    protected void request(SimpleHttpClient client, String method, String path, String token, String body,
            int expectedStatus) throws Exception {
        StringBuilder request = new StringBuilder();
        request.append(method).append(' ').append(path).append(" HTTP/1.1").append(CRLF);
        request.append("Host: localhost:").append(getPort()).append(CRLF);
        if (client.getSessionId() != null) {
            request.append("Cookie: JSESSIONID=").append(client.getSessionId()).append(CRLF);
        }
        if (token != null) {
            request.append("X-CSRF-Token: ").append(token).append(CRLF);
        }
        if (body != null) {
            request.append("Content-Type: application/json").append(CRLF);
            request.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append(CRLF);
        }
        request.append("Connection: Close").append(CRLF);
        request.append(CRLF);
        if (body != null) {
            request.append(body);
        }
        client.setRequest(new String[] { request.toString() });
        client.connect();
        client.processRequest(true);
        if (client.getStatusCode() != expectedStatus) {
            System.out.println("DBG unexpected: " + method + " " + path + " status=" + client.getStatusCode() +
                    " body=" + client.getResponseBody().substring(0, Math.min(300, client.getResponseBody().length())));
        }
        Assert.assertEquals(expectedStatus, client.getStatusCode());
    }


    protected void requestRaw(SimpleHttpClient client, String method, String path, int expectedStatus) throws Exception {
        StringBuilder request = new StringBuilder();
        request.append(method).append(' ').append(path).append(" HTTP/1.1").append(CRLF);
        request.append("Host: localhost:").append(getPort()).append(CRLF);
        if (client.getSessionId() != null) {
            request.append("Cookie: JSESSIONID=").append(client.getSessionId()).append(CRLF);
        }
        request.append("Connection: Close").append(CRLF);
        request.append(CRLF);
        client.setRequest(new String[] { request.toString() });
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(expectedStatus, client.getStatusCode());
    }


    protected String getCsrfToken(SimpleHttpClient client) {
        for (String header : client.getResponseHeaders()) {
            if (header.toLowerCase().startsWith("x-csrf-token: ")) {
                return header.substring("X-CSRF-Token: ".length());
            }
        }
        return null;
    }


    protected static class TestClient extends SimpleHttpClient {

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }
    }
}
