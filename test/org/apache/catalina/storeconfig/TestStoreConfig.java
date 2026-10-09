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
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.xml.parsers.SAXParserFactory;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.realm.LockOutRealm;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.startup.CatalinaBaseConfigurationSource;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.util.IOTools;
import org.apache.catalina.util.SessionIdGeneratorBase;
import org.apache.catalina.valves.AccessLogValve;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.openssl.OpenSSLConf;
import org.apache.tomcat.util.net.openssl.OpenSSLConfCmd;
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
        String serverXmlDump;
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

        tomcat.init(new CatalinaBaseConfigurationSource(getTemporaryDirectory(), Catalina.SERVER_XML));
        // If the persisted server.xml is malformed, start() will fail
        tomcat.start();
        Connector[] connectors = tomcat.getService().findConnectors();
        boolean foundSsl = false;
        for (Connector c : connectors) {
            if (c.getSecure()) {
                foundSsl = true;
                break;
            }
        }
        Assert.assertTrue("SSL connector not found after round-trip", foundSsl);
        tomcat.stop();
    }

    /**
     * Verify that StoreConfig preserves the comments of the existing server.xml when it rewrites the file.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testStorePreservesComments() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StoreConfigLifecycleListener storeConfigListener = new StoreConfigLifecycleListener();
        tomcat.getServer().addLifecycleListener(storeConfigListener);

        // Use a storable realm. The default embedded realm (Tomcat.SimpleRealm) is an inner class that the store
        // path cannot instantiate a default instance of.
        tomcat.getEngine().setRealm(new LockOutRealm());

        // Add a component so that the stored configuration differs from the original file
        AccessLogValve accessLogValve = new AccessLogValve();
        accessLogValve.setDirectory("logs");
        accessLogValve.setPrefix("localhost_access_log");
        accessLogValve.setSuffix(".txt");
        accessLogValve.setPattern("%h %l %u %t \"%r\" %s %b");
        tomcat.getHost().getPipeline().addValve(accessLogValve);

        // Write a server.xml with comments that StoreConfig must preserve
        File conf = new File(getTemporaryDirectory(), "conf");
        if (!conf.mkdirs()) {
            Assert.fail("Unable to create conf directory");
        }
        // Delete the whole conf directory (including any timestamped backup) after the test
        addDeleteOnTearDown(conf);
        File serverXml = new File(conf, "server.xml");
        Files.write(serverXml.toPath(), String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<!--",
                "  Header comment line",
                "-->",
                "<Server port=\"8005\" shutdown=\"SHUTDOWN\">",
                "    <!-- Service comment -->",
                "    <Service name=\"Catalina\">",
                "        <!-- Commented out connector",
                "        <Connector port=\"8009\"/>",
                "        -->",
                "    </Service>",
                "</Server>",
                "").getBytes(StandardCharsets.UTF_8));

        tomcat.start();

        // Save configuration
        storeConfigListener.getStoreConfig().storeConfig();

        // Read written configuration
        String serverXmlDump;
        try (FileReader reader = new FileReader(serverXml);
                StringWriter writer = new StringWriter()) {
            IOTools.flow(reader, writer);
            serverXmlDump = writer.toString();
        }
        Assert.assertTrue(serverXmlDump.contains("Header comment line"));
        Assert.assertTrue(serverXmlDump.contains("Service comment"));
        Assert.assertTrue(serverXmlDump.contains("Commented out connector"));
        Assert.assertTrue(serverXmlDump.contains("AccessLogValve"));
        // The stored configuration must remain well-formed
        SAXParserFactory.newInstance().newSAXParser().getXMLReader()
                .parse(new InputSource(new StringReader(serverXmlDump)));
    }

    /**
     * Verify that StoreConfig preserves the comments of the existing context.xml when the context is stored to its
     * configuration file without a backup. The output stream must not truncate the file before XMLFormatPreserver
     * has read the layout of the previous version from it.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testStoreContextSeparatePreservesComments() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StoreConfigLifecycleListener storeConfigListener = new StoreConfigLifecycleListener();
        tomcat.getServer().addLifecycleListener(storeConfigListener);

        // Use a storable realm. The default embedded realm (Tomcat.SimpleRealm) is an inner class that the store
        // path cannot instantiate a default instance of.
        tomcat.getEngine().setRealm(new LockOutRealm());

        File appDir = new File(getTemporaryDirectory(), "webapps/test");
        if (!appDir.mkdirs()) {
            Assert.fail("Unable to create the webapp directory");
        }
        Context context = tomcat.addContext("/test", appDir.getAbsolutePath());
        // WatchedResource values pointing at WEB-INF/web.xml are filtered out when storing, so use a resource that
        // survives the filtering
        ((StandardContext) context).addWatchedResource("conf/test.txt");

        // Write a context.xml with comments that StoreConfig must preserve
        File conf = new File(getTemporaryDirectory(), "conf");
        if (!conf.mkdirs()) {
            Assert.fail("Unable to create conf directory");
        }
        addDeleteOnTearDown(conf);
        File contextXml = new File(conf, "test.xml");
        Files.write(contextXml.toPath(), String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<!-- Context comment -->",
                "<Context reloadable=\"true\">",
                "    <!-- watched resources -->",
                "    <WatchedResource>conf/test.txt</WatchedResource>",
                "</Context>",
                "").getBytes(StandardCharsets.UTF_8));

        tomcat.start();

        context.setConfigFile(contextXml.toURI().toURL());

        // Store the context to its configuration file without a backup (the storeContextSeparate path)
        IStoreConfig storeConfig = storeConfigListener.getStoreConfig();
        StoreDescription desc = storeConfig.getRegistry().findDescription(StandardContext.class);
        Assert.assertNotNull(desc);
        boolean oldSeparate = desc.isStoreSeparate();
        boolean oldBackup = desc.isBackup();
        boolean oldExternalAllowed = desc.isExternalAllowed();
        try {
            desc.setStoreSeparate(true);
            desc.setBackup(false);
            desc.setExternalAllowed(true);
            desc.getStoreFactory().store(null, -2, context);
        } finally {
            desc.setStoreSeparate(oldSeparate);
            desc.setBackup(oldBackup);
            desc.setExternalAllowed(oldExternalAllowed);
        }

        // Read written configuration
        String contextXmlDump;
        try (FileReader reader = new FileReader(contextXml);
                StringWriter writer = new StringWriter()) {
            IOTools.flow(reader, writer);
            contextXmlDump = writer.toString();
        }
        Assert.assertTrue(contextXmlDump, contextXmlDump.contains("Context comment"));
        Assert.assertTrue(contextXmlDump, contextXmlDump.contains("watched resources"));
        Assert.assertTrue(contextXmlDump, contextXmlDump.contains("conf/test.txt"));
        // The stored configuration must remain well-formed
        SAXParserFactory.newInstance().newSAXParser().getXMLReader()
                .parse(new InputSource(new StringReader(contextXmlDump)));
    }

    /**
     * Verify that the &lt;Manager&gt; element is not stored for a running context using the default StandardManager
     * configuration. The jvmRoute of the session ID generator is runtime state that the manager propagates from the
     * Engine when it starts and must not defeat the default detection.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testDefaultManagerNotStored() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StoreConfigLifecycleListener storeConfigListener = new StoreConfigLifecycleListener();
        tomcat.getServer().addLifecycleListener(storeConfigListener);

        // Use a storable realm. The default embedded realm (Tomcat.SimpleRealm) is an inner class that the store
        // path cannot instantiate a default instance of.
        tomcat.getEngine().setRealm(new LockOutRealm());

        File appDir = new File(getTemporaryDirectory(), "webapps/defmgr");
        if (!appDir.mkdirs()) {
            Assert.fail("Unable to create the webapp directory");
        }
        Context context = tomcat.addContext("/defmgr", appDir.getAbsolutePath());
        ((StandardContext) context).setDeployedFromServerXml(true);

        File conf = new File(getTemporaryDirectory(), "conf");
        if (!conf.isDirectory() && !conf.mkdirs()) {
            Assert.fail("Unable to create conf directory");
        }
        addDeleteOnTearDown(conf);

        tomcat.start();

        // Undo the FastNonSecureRandom configuration the test base applies to every manager, so the started manager
        // is in the same (default) state as it would be on a regular server
        StandardManager manager = (StandardManager) ((StandardContext) context).getManager();
        manager.setSecureRandomClass(null);
        ((SessionIdGeneratorBase) manager.getSessionIdGenerator()).setSecureRandomClass(null);

        IStoreConfig storeConfig = storeConfigListener.getStoreConfig();
        StoreDescription desc = storeConfig.getRegistry().findDescription(StandardContext.class);
        Assert.assertNotNull(desc);
        boolean oldSeparate = desc.isStoreSeparate();
        boolean oldExternalAllowed = desc.isExternalAllowed();
        boolean oldExternalOnly = desc.isExternalOnly();
        String serverXmlDump;
        try {
            desc.setStoreSeparate(true);
            desc.setExternalAllowed(true);
            desc.setExternalOnly(false);
            StringWriter buffer = new StringWriter();
            storeConfig.store(new PrintWriter(buffer), -2, tomcat.getServer());
            serverXmlDump = buffer.toString();
        } finally {
            desc.setStoreSeparate(oldSeparate);
            desc.setExternalAllowed(oldExternalAllowed);
            desc.setExternalOnly(oldExternalOnly);
        }

        Assert.assertTrue(serverXmlDump, serverXmlDump.contains("path=\"/defmgr\""));
        // The manager of the started context is at its default configuration, so no <Manager> element may be stored
        Assert.assertFalse(serverXmlDump, serverXmlDump.contains("<Manager"));
        Assert.assertFalse(serverXmlDump, serverXmlDump.contains("StandardManager"));
        Assert.assertFalse(serverXmlDump, serverXmlDump.contains("SessionIdGenerator"));
    }

    /**
     * Verify that a &lt;Manager&gt; element is still stored for a context whose StandardManager deviates from the
     * default configuration.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testNonDefaultManagerStored() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StoreConfigLifecycleListener storeConfigListener = new StoreConfigLifecycleListener();
        tomcat.getServer().addLifecycleListener(storeConfigListener);

        // Use a storable realm. The default embedded realm (Tomcat.SimpleRealm) is an inner class that the store
        // path cannot instantiate a default instance of.
        tomcat.getEngine().setRealm(new LockOutRealm());

        File appDir = new File(getTemporaryDirectory(), "webapps/nondflmgr");
        if (!appDir.mkdirs()) {
            Assert.fail("Unable to create the webapp directory");
        }
        Context context = tomcat.addContext("/nondflmgr", appDir.getAbsolutePath());
        ((StandardContext) context).setDeployedFromServerXml(true);
        StandardManager manager = new StandardManager();
        manager.setMaxActiveSessions(100);
        ((StandardContext) context).setManager(manager);

        File conf = new File(getTemporaryDirectory(), "conf");
        if (!conf.isDirectory() && !conf.mkdirs()) {
            Assert.fail("Unable to create conf directory");
        }
        addDeleteOnTearDown(conf);

        tomcat.start();

        IStoreConfig storeConfig = storeConfigListener.getStoreConfig();
        StoreDescription desc = storeConfig.getRegistry().findDescription(StandardContext.class);
        Assert.assertNotNull(desc);
        boolean oldSeparate = desc.isStoreSeparate();
        boolean oldExternalAllowed = desc.isExternalAllowed();
        boolean oldExternalOnly = desc.isExternalOnly();
        String serverXmlDump;
        try {
            desc.setStoreSeparate(true);
            desc.setExternalAllowed(true);
            desc.setExternalOnly(false);
            StringWriter buffer = new StringWriter();
            storeConfig.store(new PrintWriter(buffer), -2, tomcat.getServer());
            serverXmlDump = buffer.toString();
        } finally {
            desc.setStoreSeparate(oldSeparate);
            desc.setExternalAllowed(oldExternalAllowed);
            desc.setExternalOnly(oldExternalOnly);
        }

        Assert.assertTrue(serverXmlDump, serverXmlDump.contains("<Manager"));
        Assert.assertTrue(serverXmlDump, serverXmlDump.contains("maxActiveSessions=\"100\""));
    }

    /**
     * Verify that a &lt;Manager&gt; element with a StandardManager sub-class is still stored, since the sub-class is
     * configured through its class name even when all inherited properties are at their defaults.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testSubClassManagerStored() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StoreConfigLifecycleListener storeConfigListener = new StoreConfigLifecycleListener();
        tomcat.getServer().addLifecycleListener(storeConfigListener);

        // Use a storable realm. The default embedded realm (Tomcat.SimpleRealm) is an inner class that the store
        // path cannot instantiate a default instance of.
        tomcat.getEngine().setRealm(new LockOutRealm());

        File appDir = new File(getTemporaryDirectory(), "webapps/subcmgr");
        if (!appDir.mkdirs()) {
            Assert.fail("Unable to create the webapp directory");
        }
        Context context = tomcat.addContext("/subcmgr", appDir.getAbsolutePath());
        ((StandardContext) context).setDeployedFromServerXml(true);
        ((StandardContext) context).setManager(new CustomTestManager());

        File conf = new File(getTemporaryDirectory(), "conf");
        if (!conf.isDirectory() && !conf.mkdirs()) {
            Assert.fail("Unable to create conf directory");
        }
        addDeleteOnTearDown(conf);

        tomcat.start();

        IStoreConfig storeConfig = storeConfigListener.getStoreConfig();
        StoreDescription desc = storeConfig.getRegistry().findDescription(StandardContext.class);
        Assert.assertNotNull(desc);
        boolean oldSeparate = desc.isStoreSeparate();
        boolean oldExternalAllowed = desc.isExternalAllowed();
        boolean oldExternalOnly = desc.isExternalOnly();
        String serverXmlDump;
        try {
            desc.setStoreSeparate(true);
            desc.setExternalAllowed(true);
            desc.setExternalOnly(false);
            StringWriter buffer = new StringWriter();
            storeConfig.store(new PrintWriter(buffer), -2, tomcat.getServer());
            serverXmlDump = buffer.toString();
        } finally {
            desc.setStoreSeparate(oldSeparate);
            desc.setExternalAllowed(oldExternalAllowed);
            desc.setExternalOnly(oldExternalOnly);
        }

        Assert.assertTrue(serverXmlDump, serverXmlDump.contains("<Manager"));
        Assert.assertTrue(serverXmlDump, serverXmlDump.contains(CustomTestManager.class.getName()));
    }

    /**
     * Verify that a &lt;Manager&gt; element is stored when the only deviation from the default configuration is a
     * non-default <code>throwOnFailure</code> value, which is a persistable property that must not be lost.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testManagerThrowOnFailureStored() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StoreConfigLifecycleListener storeConfigListener = new StoreConfigLifecycleListener();
        tomcat.getServer().addLifecycleListener(storeConfigListener);

        // Use a storable realm. The default embedded realm (Tomcat.SimpleRealm) is an inner class that the store
        // path cannot instantiate a default instance of.
        tomcat.getEngine().setRealm(new LockOutRealm());

        File appDir = new File(getTemporaryDirectory(), "webapps/tofmgr");
        if (!appDir.mkdirs()) {
            Assert.fail("Unable to create the webapp directory");
        }
        Context context = tomcat.addContext("/tofmgr", appDir.getAbsolutePath());
        ((StandardContext) context).setDeployedFromServerXml(true);

        File conf = new File(getTemporaryDirectory(), "conf");
        if (!conf.isDirectory() && !conf.mkdirs()) {
            Assert.fail("Unable to create conf directory");
        }
        addDeleteOnTearDown(conf);

        tomcat.start();

        // Undo the FastNonSecureRandom configuration the test base applies to every manager, so throwOnFailure is the
        // only deviation from the default manager configuration
        StandardManager manager = (StandardManager) ((StandardContext) context).getManager();
        manager.setSecureRandomClass(null);
        ((SessionIdGeneratorBase) manager.getSessionIdGenerator()).setSecureRandomClass(null);
        manager.setThrowOnFailure(false);

        IStoreConfig storeConfig = storeConfigListener.getStoreConfig();
        StoreDescription desc = storeConfig.getRegistry().findDescription(StandardContext.class);
        Assert.assertNotNull(desc);
        boolean oldSeparate = desc.isStoreSeparate();
        boolean oldExternalAllowed = desc.isExternalAllowed();
        boolean oldExternalOnly = desc.isExternalOnly();
        String serverXmlDump;
        try {
            desc.setStoreSeparate(true);
            desc.setExternalAllowed(true);
            desc.setExternalOnly(false);
            StringWriter buffer = new StringWriter();
            storeConfig.store(new PrintWriter(buffer), -2, tomcat.getServer());
            serverXmlDump = buffer.toString();
        } finally {
            desc.setStoreSeparate(oldSeparate);
            desc.setExternalAllowed(oldExternalAllowed);
            desc.setExternalOnly(oldExternalOnly);
        }

        Assert.assertTrue(serverXmlDump, serverXmlDump.contains("<Manager"));
        Assert.assertTrue(serverXmlDump, serverXmlDump.contains("throwOnFailure=\"false\""));
    }

    /**
     * Verify that the "groups" OpenSSLConfCmd that the OpenSSL implementation derives at runtime from the
     * SSLHostConfig "groups" attribute is not stored, since a stored command would shadow later changes of the
     * attribute. A user-written groups command must be kept.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testOpenSSLConfGroupsCommandHandling() throws Exception {
        StoreLoader loader = new StoreLoader();
        loader.load(null);
        StoreRegistry registry = loader.getRegistry();
        StoreDescription desc = registry.findDescription(SSLHostConfig.class);
        Assert.assertNotNull(desc);

        // The runtime command that the OpenSSL implementation adds from the attribute must not be stored
        SSLHostConfig derived = new SSLHostConfig();
        derived.setGroups("secp256r1,secp384r1");
        derived.setOpenSslConf(new OpenSSLConf());
        derived.getOpenSslConf().addCmd(new OpenSSLConfCmd(OpenSSLConfCmd.GROUPS, "secp256r1:secp384r1"));
        String dump = storeToString(desc, derived);
        Assert.assertTrue(dump, dump.contains("groups=\"secp256r1,secp384r1\""));
        Assert.assertFalse(dump, dump.contains("OpenSSLConfCmd"));

        // A user-written command with a value different from the derived one must be kept
        SSLHostConfig userCommand = new SSLHostConfig();
        userCommand.setGroups("secp256r1,secp384r1");
        userCommand.setOpenSslConf(new OpenSSLConf());
        userCommand.getOpenSslConf().addCmd(new OpenSSLConfCmd(OpenSSLConfCmd.GROUPS, "X25519"));
        dump = storeToString(desc, userCommand);
        Assert.assertTrue(dump, dump.contains("OpenSSLConfCmd"));
        Assert.assertTrue(dump, dump.contains("name=\"groups\""));
        Assert.assertTrue(dump, dump.contains("value=\"X25519\""));

        // Without the attribute, a groups command can only be user-written and must be kept
        SSLHostConfig noAttribute = new SSLHostConfig();
        noAttribute.setGroups(null);
        noAttribute.setOpenSslConf(new OpenSSLConf());
        noAttribute.getOpenSslConf().addCmd(new OpenSSLConfCmd(OpenSSLConfCmd.GROUPS, "secp256r1"));
        dump = storeToString(desc, noAttribute);
        Assert.assertTrue(dump, dump.contains("OpenSSLConfCmd"));
        Assert.assertTrue(dump, dump.contains("name=\"groups\""));
    }

    /**
     * Verify that the placeholder certificate of type UNDEFINED that is created at runtime when no certificate is
     * configured is not stored. The placeholder would fail the start of a server when a typed certificate is added to
     * the stored configuration later. An explicitly configured certificate of type UNDEFINED must be kept since the
     * filter matches the runtime registered instance.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testDefaultCertificatePlaceholderNotStored() throws Exception {
        StoreLoader loader = new StoreLoader();
        loader.load(null);
        StoreRegistry registry = loader.getRegistry();
        StoreDescription desc = registry.findDescription(SSLHostConfig.class);
        Assert.assertNotNull(desc);

        // The placeholder registered at runtime when the endpoint creates the SSL context
        SSLHostConfig placeholder = new SSLHostConfig();
        placeholder.getCertificates(true);
        String dump = storeToString(desc, placeholder);
        Assert.assertTrue(dump, dump.contains("<SSLHostConfig"));
        Assert.assertFalse(dump, dump.contains("<Certificate"));

        // An explicitly configured, type-less certificate must be kept
        SSLHostConfig explicit = new SSLHostConfig();
        explicit.addCertificate(new SSLHostConfigCertificate(explicit, SSLHostConfigCertificate.Type.UNDEFINED));
        dump = storeToString(desc, explicit);
        Assert.assertTrue(dump, dump.contains("<Certificate"));
        Assert.assertTrue(dump, dump.contains("type=\"UNDEFINED\""));

        // Typed certificates are unaffected
        SSLHostConfig typed = new SSLHostConfig();
        typed.addCertificate(new SSLHostConfigCertificate(typed, SSLHostConfigCertificate.Type.EC));
        dump = storeToString(desc, typed);
        Assert.assertTrue(dump, dump.contains("<Certificate"));
        Assert.assertTrue(dump, dump.contains("type=\"EC\""));
    }

    /**
     * Verify that storing a Context whose class has no store description is reported as a failure rather than as a
     * success, since nothing is persisted for it.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testStoreContextWithoutDescriptionFails() throws Exception {
        StoreLoader loader = new StoreLoader();
        loader.load(null);
        StoreConfig storeConfig = new StoreConfig();
        storeConfig.setRegistry(loader.getRegistry());

        Context context = new StandardContext() {
            // Sub-class that the store registry does not know
        };
        Assert.assertNull(loader.getRegistry().findDescription(context.getClass()));
        Assert.assertFalse(storeConfig.store(context));
    }

    private static String storeToString(StoreDescription desc, Object element) throws Exception {
        StringWriter buffer = new StringWriter();
        desc.getStoreFactory().store(new PrintWriter(buffer), -2, element);
        return buffer.toString();
    }

    public static class CustomTestManager extends StandardManager {

        public CustomTestManager() {
        }
    }

    /**
     * Verify that a Context parsed from a Context element in server.xml is flagged as deployed from server.xml, so
     * storeconfig can detect it. The flag must also be set when server.xml is processed through the generated code
     * path, so the generated code is checked as well.
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testContextFromServerXmlIsFlagged() throws Exception {
        File appDir = new File(getTemporaryDirectory(), "webapps/inline");
        if (!appDir.mkdirs()) {
            Assert.fail("Unable to create the webapp directory");
        }

        File conf = new File(getTemporaryDirectory(), "conf");
        if (!conf.mkdirs()) {
            Assert.fail("Unable to create conf directory");
        }
        addDeleteOnTearDown(conf);

        File serverXml = new File(conf, "server.xml");
        Files.write(serverXml.toPath(), String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<Server port=\"-1\" shutdown=\"SHUTDOWN\">",
                "    <Service name=\"Catalina\">",
                "        <Engine name=\"Catalina\" defaultHost=\"localhost\">",
                "            <Host name=\"localhost\" appBase=\"webapps\"",
                "                  deployOnStartup=\"false\" autoDeploy=\"false\">",
                "                <Context path=\"/inline\" docBase=\"inline\"/>",
                "            </Host>",
                "        </Engine>",
                "    </Service>",
                "</Server>",
                "").getBytes(StandardCharsets.UTF_8));

        // Parse server.xml (with code generation enabled) without starting the server
        File generatedCodeLocation = new File(getTemporaryDirectory(), "generated");
        Catalina catalina = new Catalina();
        catalina.load(new String[] { "start", "-generateCode", generatedCodeLocation.getAbsolutePath() });

        Context inline = (Context) catalina.getServer().findServices()[0].getContainer().findChildren()[0]
                .findChild("/inline");
        Assert.assertNotNull("Context element from server.xml not found", inline);
        Assert.assertTrue("Context from server.xml must be flagged as deployed from server.xml",
                ((StandardContext) inline).getDeployedFromServerXml());

        // The generated code must set the flag as well, so that contexts parsed through the generated code path are
        // also flagged
        File generatedClass = new File(generatedCodeLocation, "catalinaembedded/ServerXml.java");
        Assert.assertTrue("Generated code was not created: " + generatedClass, generatedClass.exists());
        String generatedCode;
        try (FileReader reader = new FileReader(generatedClass);
                StringWriter writer = new StringWriter()) {
            IOTools.flow(reader, writer);
            generatedCode = writer.toString();
        }
        Assert.assertTrue(generatedCode, generatedCode.contains(".setDeployedFromServerXml(true);"));
    }

    /**
     * Verify that a context flagged as deployed from server.xml is stored back inline to the server.xml writer (and
     * not moved to a new context configuration file) when inline storage is allowed (externalOnly is false).
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testContextFromServerXmlStoredInline() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StoreConfigLifecycleListener storeConfigListener = new StoreConfigLifecycleListener();
        tomcat.getServer().addLifecycleListener(storeConfigListener);

        // Use a storable realm. The default embedded realm (Tomcat.SimpleRealm) is an inner class that the store
        // path cannot instantiate a default instance of.
        tomcat.getEngine().setRealm(new LockOutRealm());

        File appDir = new File(getTemporaryDirectory(), "webapps/inline");
        if (!appDir.mkdirs()) {
            Assert.fail("Unable to create the webapp directory");
        }
        Context context = tomcat.addContext("/inline", appDir.getAbsolutePath());
        ((StandardContext) context).setDeployedFromServerXml(true);

        File conf = new File(getTemporaryDirectory(), "conf");
        if (!conf.mkdirs()) {
            Assert.fail("Unable to create conf directory");
        }
        addDeleteOnTearDown(conf);

        tomcat.start();

        IStoreConfig storeConfig = storeConfigListener.getStoreConfig();
        StoreDescription desc = storeConfig.getRegistry().findDescription(StandardContext.class);
        Assert.assertNotNull(desc);
        boolean oldSeparate = desc.isStoreSeparate();
        boolean oldExternalAllowed = desc.isExternalAllowed();
        boolean oldExternalOnly = desc.isExternalOnly();
        String serverXmlDump;
        try {
            desc.setStoreSeparate(true);
            desc.setExternalAllowed(true);
            desc.setExternalOnly(false);
            StringWriter buffer = new StringWriter();
            storeConfig.store(new PrintWriter(buffer), -2, tomcat.getServer());
            serverXmlDump = buffer.toString();
        } finally {
            desc.setStoreSeparate(oldSeparate);
            desc.setExternalAllowed(oldExternalAllowed);
            desc.setExternalOnly(oldExternalOnly);
        }

        // The context must be stored inline in server.xml, without the internal flag attribute
        Assert.assertTrue(serverXmlDump, serverXmlDump.contains("<Context"));
        Assert.assertTrue(serverXmlDump, serverXmlDump.contains("path=\"/inline\""));
        Assert.assertFalse(serverXmlDump, serverXmlDump.contains("deployedFromServerXml"));
        // The stored configuration must remain well-formed
        SAXParserFactory.newInstance().newSAXParser().getXMLReader()
                .parse(new InputSource(new StringReader(serverXmlDump)));

        // No new context configuration file must have been created
        Host host = tomcat.getHost();
        Assert.assertFalse(new File(host.getConfigBaseFile(), "inline.xml").exists());
    }

    /**
     * Verify that a context that was not deployed from server.xml and that has no configuration file is stored to a
     * new context configuration file even when inline storage is allowed (externalOnly is false).
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testContextNotFromServerXmlStoredToNewFile() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StoreConfigLifecycleListener storeConfigListener = new StoreConfigLifecycleListener();
        tomcat.getServer().addLifecycleListener(storeConfigListener);

        // Use a storable realm. The default embedded realm (Tomcat.SimpleRealm) is an inner class that the store
        // path cannot instantiate a default instance of.
        tomcat.getEngine().setRealm(new LockOutRealm());

        File appDir = new File(getTemporaryDirectory(), "webapps/standalone");
        if (!appDir.mkdirs()) {
            Assert.fail("Unable to create the webapp directory");
        }
        Context context = tomcat.addContext("/standalone", appDir.getAbsolutePath());
        Assert.assertFalse("Programmatic context must not be flagged as deployed from server.xml",
                ((StandardContext) context).getDeployedFromServerXml());

        File conf = new File(getTemporaryDirectory(), "conf");
        if (!conf.mkdirs()) {
            Assert.fail("Unable to create conf directory");
        }
        addDeleteOnTearDown(conf);

        tomcat.start();

        IStoreConfig storeConfig = storeConfigListener.getStoreConfig();
        StoreDescription desc = storeConfig.getRegistry().findDescription(StandardContext.class);
        Assert.assertNotNull(desc);
        boolean oldSeparate = desc.isStoreSeparate();
        boolean oldExternalAllowed = desc.isExternalAllowed();
        boolean oldExternalOnly = desc.isExternalOnly();
        String serverXmlDump;
        try {
            desc.setStoreSeparate(true);
            desc.setExternalAllowed(true);
            desc.setExternalOnly(false);
            StringWriter buffer = new StringWriter();
            storeConfig.store(new PrintWriter(buffer), -2, tomcat.getServer());
            serverXmlDump = buffer.toString();
        } finally {
            desc.setStoreSeparate(oldSeparate);
            desc.setExternalAllowed(oldExternalAllowed);
            desc.setExternalOnly(oldExternalOnly);
        }

        // The context must not be stored inline in server.xml
        Assert.assertFalse(serverXmlDump, serverXmlDump.contains("standalone"));

        // A new context configuration file must have been created instead
        File contextXml = new File(tomcat.getHost().getConfigBaseFile(), "standalone.xml");
        Assert.assertTrue("Context file was not created: " + contextXml, contextXml.exists());
        String contextXmlDump;
        try (FileReader reader = new FileReader(contextXml);
                StringWriter writer = new StringWriter()) {
            IOTools.flow(reader, writer);
            contextXmlDump = writer.toString();
        }
        Assert.assertTrue(contextXmlDump, contextXmlDump.contains("<Context"));
        // The stored configuration must remain well-formed
        SAXParserFactory.newInstance().newSAXParser().getXMLReader()
                .parse(new InputSource(new StringReader(contextXmlDump)));
    }

    /**
     * Verify that storing a context flagged as deployed from server.xml with no writer for server.xml is skipped
     * gracefully (no error, no new context configuration file).
     *
     * @throws Exception if the test experiences an unexpected error
     */
    @Test
    public void testContextFromServerXmlStoreWithoutWriterSkipped() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StoreConfigLifecycleListener storeConfigListener = new StoreConfigLifecycleListener();
        tomcat.getServer().addLifecycleListener(storeConfigListener);

        File appDir = new File(getTemporaryDirectory(), "webapps/skipped");
        if (!appDir.mkdirs()) {
            Assert.fail("Unable to create the webapp directory");
        }
        Context context = tomcat.addContext("/skipped", appDir.getAbsolutePath());
        ((StandardContext) context).setDeployedFromServerXml(true);

        File conf = new File(getTemporaryDirectory(), "conf");
        if (!conf.mkdirs()) {
            Assert.fail("Unable to create conf directory");
        }
        addDeleteOnTearDown(conf);

        tomcat.start();

        IStoreConfig storeConfig = storeConfigListener.getStoreConfig();
        StoreDescription desc = storeConfig.getRegistry().findDescription(StandardContext.class);
        Assert.assertNotNull(desc);
        boolean oldSeparate = desc.isStoreSeparate();
        boolean oldExternalAllowed = desc.isExternalAllowed();
        boolean oldExternalOnly = desc.isExternalOnly();
        try {
            desc.setStoreSeparate(true);
            desc.setExternalAllowed(true);
            desc.setExternalOnly(false);
            // No writer available for server.xml and no config file: storing must be skipped without an error
            desc.getStoreFactory().store(null, -2, context);
        } finally {
            desc.setStoreSeparate(oldSeparate);
            desc.setExternalAllowed(oldExternalAllowed);
            desc.setExternalOnly(oldExternalOnly);
        }

        Assert.assertFalse(new File(tomcat.getHost().getConfigBaseFile(), "skipped.xml").exists());
    }

}
