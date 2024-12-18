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
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.TestTomcat.HelloWorld;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestPropertySources extends LoggingBaseTest {

    public static class MyPropertySource1 implements org.apache.tomcat.util.IntrospectionUtils.PropertySource {
        @Override
        public String getProperty(String key) {
            if ("connector.port".equals(key)) {
                return "0";
            }
            return null;
        }
    }

    public static class MyPropertySource2 implements org.apache.tomcat.util.IntrospectionUtils.PropertySource {
        @Override
        public String getProperty(String key) {
            if ("connector.timeout".equals(key)) {
                return "10000";
            }
            return null;
        }
    }

    protected class ServerXml extends CatalinaBaseConfigurationSource {
        public ServerXml() {
            super(getTemporaryDirectory(), null);
        }

        private static final String SERVER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Server port=\"8005\" shutdown=\"SHUTDOWN\">\n"
                + "  <Service name=\"Catalina\">\n" + "\n"
                + "    <Connector port=\"${mysystemproperty}\" protocol=\"HTTP/1.1\"\n"
                + "               connectionTimeout=\"${connector.timeout}\"\n"
                + "               redirectPort=\"8443\" />\n"
                + "    <Engine name=\"Catalina\" defaultHost=\"localhost\">\n"
                + "      <Host name=\"localhost\" />\n"
                + "    </Engine>\n"
                + "  </Service>\n"
                + "</Server>";

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

    @BeforeClass
    public static void setupSystemProperties() {
        System.setProperty("org.apache.tomcat.util.digester.PROPERTY_SOURCE",
                "org.apache.catalina.startup.TestPropertySources$MyPropertySource1,org.apache.catalina.startup.TestPropertySources$MyPropertySource2");
        System.setProperty("org.apache.tomcat.util.digester.REPLACE_SYSTEM_PROPERTIES", "true");
        System.setProperty("mysystemproperty", "${connector.port}");
    }

    @Test
    public void testPropertyReplacement() throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.init(new ServerXml());

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "myServlet", new HelloWorld());
        ctx.addServletMappingDecoded("/", "myServlet");

        tomcat.start();

        Assert.assertEquals(System.getProperty("mysystemproperty"), "0");
        Assert.assertEquals(tomcat.getConnector().getPort(), 0);
        Assert.assertEquals(tomcat.getConnector().getProperty("connectionTimeout"), Integer.valueOf(10000));

        ByteChunk res = TomcatBaseTest.getUrl("http://localhost:" + tomcat.getConnector().getLocalPort() + "/");
        Assert.assertEquals("Hello world", res.toString());

        tomcat.stop();
        tomcat.destroy();
    }

}
