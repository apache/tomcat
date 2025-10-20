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
package org.apache.catalina.storeconfig;

import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.SAXParserFactory;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.Catalina;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.IOTools;
import org.xml.sax.InputSource;

public class TestStoreConfig extends TomcatBaseTest {

    @Test
    public void testListener() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.enableNaming();
        StoreConfigLifecycleListener storeConfigListener = new StoreConfigLifecycleListener();
        tomcat.getServer().addLifecycleListener(storeConfigListener);

        // Create various components (from generated embedded code; startup errors must not be fatal)

        org.apache.catalina.deploy.NamingResourcesImpl tc_NamingResourcesImpl_11 = new org.apache.catalina.deploy.NamingResourcesImpl();
        org.apache.tomcat.util.descriptor.web.ContextResource tc_ContextResource_12 = new org.apache.tomcat.util.descriptor.web.ContextResource();
        tc_ContextResource_12.setName("UserDatabase");
        tc_ContextResource_12.setAuth("Container");
        tc_ContextResource_12.setType("org.apache.catalina.UserDatabase");
        tc_ContextResource_12.setDescription("User database that can be updated and saved");
        tc_ContextResource_12.setProperty("factory", "org.apache.catalina.users.MemoryUserDatabaseFactory");
        tc_ContextResource_12.setProperty("pathname", "conf/tomcat-users.xml");
        tc_NamingResourcesImpl_11.addResource(tc_ContextResource_12);
        tomcat.getServer().setGlobalNamingResources(tc_NamingResourcesImpl_11);

        org.apache.catalina.core.StandardThreadExecutor tc_StandardThreadExecutor_14 = new org.apache.catalina.core.StandardThreadExecutor();
        tc_StandardThreadExecutor_14.setName("tomcatThreadPool");
        tc_StandardThreadExecutor_14.setNamePrefix("catalina-exec-");
        tc_StandardThreadExecutor_14.setMaxThreads(Integer.parseInt("150"));
        tc_StandardThreadExecutor_14.setMinSpareThreads(Integer.parseInt("4"));
        tomcat.getService().addExecutor(tc_StandardThreadExecutor_14);

        org.apache.coyote.http2.Http2Protocol tc_Http2Protocol_17 = new org.apache.coyote.http2.Http2Protocol();
        tomcat.getConnector().addUpgradeProtocol(tc_Http2Protocol_17);
        tomcat.getConnector().setProperty("SSLEnabled", "true");
        tomcat.getConnector().setScheme("https");
        tomcat.getConnector().setSecure(Boolean.parseBoolean("true"));
        tomcat.getConnector().setThrowOnFailure(false);
        tomcat.getConnector().getProtocolHandler().setExecutor(tomcat.getService().getExecutor("tomcatThreadPool"));

        org.apache.tomcat.util.net.SSLHostConfig tc_SSLHostConfig_22 = new org.apache.tomcat.util.net.SSLHostConfig();
        tc_SSLHostConfig_22.setProtocols("TLSv1.1+TLSv1.2");
        tc_SSLHostConfig_22.setCertificateVerification("optionalNoCA");
        tc_SSLHostConfig_22.setCertificateVerificationDepth(Integer.parseInt("3"));
        org.apache.tomcat.util.net.SSLHostConfigCertificate tc_SSLHostConfigCertificate_23 =
                new org.apache.tomcat.util.net.SSLHostConfigCertificate(tc_SSLHostConfig_22,
                        org.apache.tomcat.util.net.SSLHostConfigCertificate.Type.RSA);
        tc_SSLHostConfigCertificate_23.setCertificateKeystoreFile("conf/localhost-rsa.jks");
        tc_SSLHostConfigCertificate_23.setCertificateKeystorePassword("mypassword");
        tc_SSLHostConfig_22.addCertificate(tc_SSLHostConfigCertificate_23);
        tomcat.getConnector().addSslHostConfig(tc_SSLHostConfig_22);

        org.apache.catalina.ha.tcp.SimpleTcpCluster tc_SimpleTcpCluster_51 = new org.apache.catalina.ha.tcp.SimpleTcpCluster();

        org.apache.catalina.tribes.group.GroupChannel tc_GroupChannel_52 = new org.apache.catalina.tribes.group.GroupChannel();
        ((org.apache.catalina.tribes.transport.ReceiverBase) tc_GroupChannel_52.getChannelReceiver()).setHost("localhost");

        org.apache.catalina.tribes.membership.cloud.CloudMembershipService tc_CloudMembershipService_53 =
                new org.apache.catalina.tribes.membership.cloud.CloudMembershipService();
        tc_CloudMembershipService_53.setMembershipProviderClassName("org.apache.catalina.tribes.membership.cloud.KubernetesMembershipProvider");
        tc_GroupChannel_52.setMembershipService(tc_CloudMembershipService_53);
        tc_SimpleTcpCluster_51.setChannel(tc_GroupChannel_52);
        tomcat.getEngine().setCluster(tc_SimpleTcpCluster_51);

        org.apache.catalina.realm.LockOutRealm tc_LockOutRealm_55 = new org.apache.catalina.realm.LockOutRealm();

        org.apache.catalina.realm.UserDatabaseRealm tc_UserDatabaseRealm_56 =
                new org.apache.catalina.realm.UserDatabaseRealm();
        tc_UserDatabaseRealm_56.setResourceName("UserDatabase");

        org.apache.catalina.realm.NestedCredentialHandler tc_NestedCredentialHandler_57 =
                new org.apache.catalina.realm.NestedCredentialHandler();

        org.apache.catalina.realm.MessageDigestCredentialHandler tc_MessageDigestCredentialHandler_58 =
                new org.apache.catalina.realm.MessageDigestCredentialHandler();
        tc_NestedCredentialHandler_57.addCredentialHandler(tc_MessageDigestCredentialHandler_58);

        org.apache.catalina.realm.SecretKeyCredentialHandler tc_SecretKeyCredentialHandler_59 =
                new org.apache.catalina.realm.SecretKeyCredentialHandler();
        tc_NestedCredentialHandler_57.addCredentialHandler(tc_SecretKeyCredentialHandler_59);
        tc_UserDatabaseRealm_56.setCredentialHandler(tc_NestedCredentialHandler_57);
        tc_LockOutRealm_55.addRealm(tc_UserDatabaseRealm_56);
        tomcat.getEngine().setRealm(tc_LockOutRealm_55);

        org.apache.catalina.valves.AccessLogValve tc_AccessLogValve_57 = new org.apache.catalina.valves.AccessLogValve();
        tc_AccessLogValve_57.setDirectory("logs");
        tc_AccessLogValve_57.setPrefix("localhost_access_log");
        tc_AccessLogValve_57.setSuffix(".txt");
        tc_AccessLogValve_57.setPattern("%h %l %u %t \"%r\" %s %b");
        tomcat.getHost().getPipeline().addValve(tc_AccessLogValve_57);

        tomcat.start();

        // Save configuration
        storeConfigListener.getStoreConfig().storeConfig();

        // Read written configuration
        File serverXml = new File(tomcat.getServer().getCatalinaBase(), Catalina.SERVER_XML);
        Assert.assertTrue(serverXml.canRead());
        addDeleteOnTearDown(serverXml);
        String serverXmlDump = "";
        try (FileReader reader = new FileReader(serverXml);
                StringWriter writer = new StringWriter()) {
            IOTools.flow(reader, writer);
            serverXmlDump = writer.toString();
        }
        Assert.assertTrue(serverXmlDump.contains("StoreConfigLifecycleListener"));
        Assert.assertTrue(serverXmlDump.contains("UserDatabaseRealm"));
        Assert.assertTrue(serverXmlDump.contains("SecretKeyCredentialHandler"));
        Assert.assertTrue(serverXmlDump.contains("certificateKeystorePassword="));
        Assert.assertTrue(serverXmlDump.contains("+TLSv1.1"));
        SAXParserFactory.newInstance().newSAXParser().getXMLReader().parse(new InputSource(new StringReader(serverXmlDump)));

        tomcat.stop();
    }

}
