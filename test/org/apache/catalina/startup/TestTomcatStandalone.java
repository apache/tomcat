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
package org.apache.catalina.startup;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.TestTomcat.HelloWorld;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestTomcatStandalone extends LoggingBaseTest {

    private static final String TEST_WEBAPP_CONTEXT_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<Context docBase=\"${user.dir}/test/webapp-2.2\" ignoreAnnotations=\"true\">\n" +
            "</Context>";

    private static final long LAST_MODIFIED = System.currentTimeMillis();
    protected class ServerXml extends CatalinaBaseConfigurationSource {

        class MemoryResource extends Resource {
            MemoryResource(InputStream inputStream, URI uri) {
                super(inputStream, uri);
            }
            @Override
            public long getLastModified() throws MalformedURLException, IOException {
                return LAST_MODIFIED;
            }
        }

        public ServerXml() {
            super(getTemporaryDirectory(), null);
        }

        private static final String SERVER_XML =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Server port=\"8005\" shutdown=\"SHUTDOWN\">\n" +
                "  <Listener className=\"org.apache.catalina.startup.VersionLoggerListener\" />\n" +
                "  <Listener className=\"org.apache.catalina.core.AprLifecycleListener\" SSLEngine=\"on\" />\n" +
                "  <Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" />\n" +
                "  <Listener className=\"org.apache.catalina.mbeans.GlobalResourcesLifecycleListener\" />\n" +
                "  <Listener className=\"org.apache.catalina.core.ThreadLocalLeakPreventionListener\" />\n" +
                "  <GlobalNamingResources>\n" +
                "    <Resource name=\"UserDatabase\" auth=\"Container\"\n" +
                "              type=\"org.apache.catalina.UserDatabase\"\n" +
                "              description=\"User database that can be updated and saved\"\n" +
                "              factory=\"org.apache.catalina.users.MemoryUserDatabaseFactory\"\n" +
                "              pathname=\"conf/tomcat-users.xml\" />\n" +
                "  </GlobalNamingResources>\n" + "\n" +
                "  <Service name=\"Catalina\">\n" +
                "    <Connector port=\"0\" protocol=\"HTTP/1.1\"\n" +
                "               connectionTimeout=\"20000\"\n" +
                "               redirectPort=\"8443\" />\n" +
                "    <Engine name=\"Catalina\" defaultHost=\"localhost\">\n" + "\n" +
                "      <Realm className=\"org.apache.catalina.realm.LockOutRealm\">\n" +
                "        <Realm className=\"org.apache.catalina.realm.UserDatabaseRealm\"\n" +
                "               resourceName=\"UserDatabase\"/>\n" +
                "      </Realm>\n" + "\n" +
                "      <Host name=\"localhost\" appBase=\"webapps\"\n" +
                "            unpackWARs=\"true\" autoDeploy=\"true\">\n" + "\n" +
                "        <Valve className=\"org.apache.catalina.valves.AccessLogValve\" directory=\"logs\"\n" +
                "               prefix=\"localhost_access_log\" suffix=\".txt\"\n" +
                "               pattern=\"%h %l %u %t &quot;%r&quot; %s %b\" />\n" +
                "      </Host>\n" +
                "    </Engine>\n" +
                "  </Service>\n" +
                "</Server>";

        private static final String CONTEXT_XML =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Context>\n" +
                "    <JarScanner scanClassPath=\"false\" />\n" +
                "    <WatchedResource>WEB-INF/web.xml</WatchedResource>\n" +
                "    <WatchedResource>WEB-INF/tomcat-web.xml</WatchedResource>\n" +
                "    <WatchedResource>${catalina.base}/conf/web.xml</WatchedResource>\n" +
                "    <Manager pathname=\"SESSIONS.ser\" />\n" +
                "</Context>";

        private static final String TOMCAT_USERS_XML =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<tomcat-users xmlns=\"http://tomcat.apache.org/xml\"\n" +
                "              xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "              xsi:schemaLocation=\"http://tomcat.apache.org/xml tomcat-users.xsd\"\n" +
                "              version=\"1.0\">\n" +
                "  <user username=\"tomcat\" password=\"tomcat\" roles=\"tomcat,manager-status\"/>\n" +
                "</tomcat-users>";

        @Override
        public Resource getServerXml() throws IOException {
            Resource resource;
            try {
                resource = new MemoryResource(new ByteArrayInputStream(SERVER_XML.getBytes(StandardCharsets.UTF_8)),
                        new URI("file:conf/server.xml"));
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
            return resource;
        }

        @Override
        public Resource getResource(String name) throws IOException {
            if (Constants.DefaultContextXml.equals(name)) {
                Resource resource;
                try {
                    resource = new MemoryResource(new ByteArrayInputStream(CONTEXT_XML.getBytes(StandardCharsets.UTF_8)),
                            new URI("file:conf/context.xml"));
                } catch (URISyntaxException e) {
                    throw new IOException(e);
                }
                return resource;
            } else if ("conf/tomcat-users.xml".equals(name)) {
                Resource resource;
                try {
                    resource = new MemoryResource(new ByteArrayInputStream(TOMCAT_USERS_XML.getBytes(StandardCharsets.UTF_8)),
                            new URI("file:conf/tomcat-users.xml"));
                } catch (URISyntaxException e) {
                    throw new IOException(e);
                }
                return resource;
            }
            return super.getResource(name);
        }

    }

    @Test
    public void testStandalone() throws Exception {

        // Test embedded with pseudo standalone

        Tomcat tomcat = new Tomcat();
        tomcat.init(new ServerXml());

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "myServlet", new HelloWorld());
        ctx.addServletMappingDecoded("/", "myServlet");

        tomcat.start();
        // Emulate Tomcat main thread
        new Thread() {
            @Override
            public void run() {
                tomcat.getServer().await();
                try {
                    tomcat.stop();
                } catch (LifecycleException e) {
                }
            }
        }.start();
        InetAddress localAddress = null;
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface ni = networkInterfaces.nextElement();
            if (!ni.isLoopback() && ni.isUp()) {
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        localAddress = address;
                    }
                }
            }
        }

        ByteChunk res = TomcatBaseTest.getUrl("http://localhost:" + tomcat.getConnector().getLocalPort() + "/");
        Assert.assertEquals("Hello world", res.toString());

        // Use the shutdown command
        if (localAddress != null) {
            // Don't listen to non loopback
            Exception ex = null;
            try (Socket s = new Socket(localAddress, 8005)) {
                s.getOutputStream().write("GOAWAY".getBytes(StandardCharsets.ISO_8859_1));
            } catch (Exception e) {
                ex = e;
            }
            Assert.assertNotNull(ex);
        }

        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), 8005)) {
            // Bad command
            s.getOutputStream().write("GOAWAY".getBytes(StandardCharsets.ISO_8859_1));
        }
        Thread.sleep(100);
        Assert.assertEquals(LifecycleState.STARTED, tomcat.getService().getState());

        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), 8005)) {
            s.getOutputStream().write("SHUTDOWN".getBytes(StandardCharsets.ISO_8859_1));
        }
        Thread.sleep(100);
        Assert.assertNotEquals(LifecycleState.STARTED, tomcat.getService().getState());

        // Second separate test, real standalone using Catalina
        // This is done in one single test to avoid possible problems with the shutdown port

        // Add descriptor to deploy
        File descriptorsFolder = new File(getTemporaryDirectory(), "conf/Catalina/localhost");
        descriptorsFolder.mkdirs();
        try (FileOutputStream os = new FileOutputStream(new File(descriptorsFolder, "test.xml"))) {
            os.write(TEST_WEBAPP_CONTEXT_XML.getBytes(StandardCharsets.UTF_8));
        }

        Catalina catalina = new Catalina();
        catalina.setAwait(true);
        // Embedded code generation uses Catalina, so it is the best spot to test it as well
        File generatedCodeLocation = new File(getTemporaryDirectory(), "generated");
        new Thread() {
            @Override
            public void run() {
                String[] args = { "start", "-generateCode", generatedCodeLocation.getAbsolutePath() };
                catalina.load(args);
                catalina.start();
            }
        }.start();

        Service service = null;
        int i = 0;
        while (i < 500 && (service == null || service.getState() != LifecycleState.STARTED)) {
            Server server = catalina.getServer();
            if (server != null && catalina.getServer().findServices().length > 0) {
                service = catalina.getServer().findServices()[0];
            }
            Thread.sleep(10);
            i++;
        }
        Assert.assertNotNull(service);

        Connector connector = service.findConnectors()[0];
        res = TomcatBaseTest.getUrl("http://localhost:" + connector.getLocalPort() + "/");
        Assert.assertTrue(res.toString().contains("404"));

        File codeFolder = new File(generatedCodeLocation, "catalinaembedded");
        File generatedLoader = new File(codeFolder, "DigesterGeneratedCodeLoader.java");
        File generatedServerXml = new File(codeFolder, "ServerXml.java");
        Assert.assertTrue(generatedLoader.exists());
        Assert.assertTrue(generatedServerXml.exists());

        (new Catalina()).stopServer();
        i = 0;
        while (true) {
            Assert.assertTrue(i++ < 100);
            if (service.getState() != LifecycleState.STARTED) {
                break;
            }
            Thread.sleep(10);
        }

    }

}
