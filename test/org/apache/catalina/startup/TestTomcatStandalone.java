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
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
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
import org.apache.tomcat.util.file.ConfigFileLoader;

public class TestTomcatStandalone extends LoggingBaseTest {

    protected class ServerXml extends CatalinaBaseConfigurationSource {
        public ServerXml() {
            super(getTemporaryDirectory(), null);
        }

        private static final String SERVER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Server port=\"8005\" shutdown=\"SHUTDOWN\">\n"
                + "  <Listener className=\"org.apache.catalina.startup.VersionLoggerListener\" />\n"
                + "  <Listener className=\"org.apache.catalina.core.AprLifecycleListener\" SSLEngine=\"on\" />\n"
                + "  <Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" />\n"
                + "  <Listener className=\"org.apache.catalina.mbeans.GlobalResourcesLifecycleListener\" />\n"
                + "  <Listener className=\"org.apache.catalina.core.ThreadLocalLeakPreventionListener\" />\n"
                + "\n" + "  <GlobalNamingResources>\n"
                + "    <Resource name=\"UserDatabase\" auth=\"Container\"\n"
                + "              type=\"org.apache.catalina.UserDatabase\"\n"
                + "              description=\"User database that can be updated and saved\"\n"
                + "              factory=\"org.apache.catalina.users.MemoryUserDatabaseFactory\"\n"
                + "              pathname=\"conf/tomcat-users.xml\" />\n"
                + "  </GlobalNamingResources>\n" + "\n"
                + "  <Service name=\"Catalina\">\n" + "\n"
                + "    <Connector port=\"0\" protocol=\"HTTP/1.1\"\n"
                + "               connectionTimeout=\"20000\"\n"
                + "               redirectPort=\"8443\"\n"
                + "               maxParameterCount=\"1000\"\n"
                + "               />\n"
                + "    <Engine name=\"Catalina\" defaultHost=\"localhost\">\n"
                + "\n"
                + "      <Realm className=\"org.apache.catalina.realm.LockOutRealm\">\n"
                + "        <Realm className=\"org.apache.catalina.realm.UserDatabaseRealm\"\n"
                + "               resourceName=\"UserDatabase\"/>\n"
                + "      </Realm>\n" + "\n"
                + "      <Host name=\"localhost\"  appBase=\"webapps\"\n"
                + "            unpackWARs=\"true\" autoDeploy=\"true\">\n"
                + "\n"
                + "        <Valve className=\"org.apache.catalina.valves.AccessLogValve\" directory=\"logs\"\n"
                + "               prefix=\"localhost_access_log\" suffix=\".txt\"\n"
                + "               pattern=\"%h %l %u %t &quot;%r&quot; %s %b\" />\n"
                + "\n" + "      </Host>\n" + "    </Engine>\n"
                + "  </Service>\n" + "</Server>";

        @Override
        public Resource getServerXml() throws IOException {
            Resource resource;
            try {
                resource = new Resource(new ByteArrayInputStream(SERVER_XML.getBytes(StandardCharsets.ISO_8859_1)),
                        new URI("file:server.xml"));
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
            return resource;
        }
    }

    @Test
    public void testServerXml() throws Exception {
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

    }

    @Test
    public void testStandalone() throws Exception {
        ConfigFileLoader.setSource(new ServerXml());

        Catalina catalina = new Catalina();
        catalina.setAwait(true);
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
        while (true) {
            Assert.assertTrue(i++ < 500);
            if (service == null) {
                Server server = catalina.getServer();
                if (server != null && catalina.getServer().findServices().length > 0) {
                    service = catalina.getServer().findServices()[0];
                }
            }
            if (service != null && service.getState() == LifecycleState.STARTED) {
                break;
            }
            Thread.sleep(10);
        }

        Connector connector = service.findConnectors()[0];
        ByteChunk res = TomcatBaseTest.getUrl("http://localhost:" + connector.getLocalPort() + "/");
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
