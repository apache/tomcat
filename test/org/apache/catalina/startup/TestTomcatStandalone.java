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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.TestTomcat.HelloWorld;
import org.apache.tomcat.util.buf.ByteChunk;

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
                + "               redirectPort=\"8443\" />\n"
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

        ByteChunk res = TomcatBaseTest.getUrl("http://localhost:" + tomcat.getConnector().getLocalPort() + "/");
        Assert.assertEquals("Hello world", res.toString());

        tomcat.stop();
        tomcat.destroy();
    }

}
