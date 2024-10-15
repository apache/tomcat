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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.util.ContextName;

/**
 * The purpose of this class is to test the automatic deployment features of the
 * {@link HostConfig} implementation.
 */
public class TestHostConfigAutomaticDeploymentB extends TomcatBaseTest {

    private static final ContextName  APP_NAME = new ContextName("myapp", false);
    private static final File XML_SOURCE =
            new File("test/deployment/context.xml");
    private static final File WAR_XML_SOURCE =
            new File("test/deployment/context.war");
    private static final File WAR_SOURCE =
            new File("test/deployment/noContext.war");
    private static final File DIR_XML_SOURCE =
            new File("test/deployment/dirContext");
    private static final File DIR_SOURCE =
            new File("test/deployment/dirNoContext");

    private static final String XML_COOKIE_NAME = "XML_CONTEXT";
    private static final String WAR_COOKIE_NAME = "WAR_CONTEXT";
    private static final String DIR_COOKIE_NAME = "DIR_CONTEXT";
    // private static final String DEFAULT_COOKIE_NAME = "JSESSIONID";

    private File external;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();

        external = new File(getTemporaryDirectory(), "external");
        if (!external.exists() && !external.mkdir()) {
            Assert.fail("Unable to create external for test");
        }

        // Disable background thread
        tomcat.getEngine().setBackgroundProcessorDelay(-1);

        // Enable deployer
        tomcat.getHost().addLifecycleListener(new HostConfig());

        // Disable deployment on start up
        tomcat.getHost().setDeployOnStartup(false);

        // Clean-up after test
        addDeleteOnTearDown(new File(tomcat.basedir, "/conf"));
        addDeleteOnTearDown(external);
    }


    /*
     * Expected behaviour for deployment of an XML file.
     * deployXML  copyXML  unpackWARs      XML  WAR  DIR
     *    Y/N       Y/N       Y/N           Y    N    N
     *
     * Note: Context will fail to start because no valid docBase is present.
     */
    @Test
    public void testDeploymentXmlFFF() throws Exception {
        createXmlInConfigBaseForAppbase();
        doTestDeployment(false, false, false,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlFFT() throws Exception {
        createXmlInConfigBaseForAppbase();
        doTestDeployment(false, false, true,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlFTF() throws Exception {
        createXmlInConfigBaseForAppbase();
        doTestDeployment(false, true, false,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlFTT() throws Exception {
        createXmlInConfigBaseForAppbase();
        doTestDeployment(false, true, true,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlTFF() throws Exception {
        createXmlInConfigBaseForAppbase();
        doTestDeployment(true, false, false,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlTFT() throws Exception {
        createXmlInConfigBaseForAppbase();
        doTestDeployment(true, false, true,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlTTF() throws Exception {
        createXmlInConfigBaseForAppbase();
        doTestDeployment(true, true, false,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlTTT() throws Exception {
        createXmlInConfigBaseForAppbase();
        doTestDeployment(true, true, true,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }


    /*
     * Expected behaviour for deployment of an XML file that points to an
     * external WAR.
     * deployXML  copyXML  unpackWARs      XML  WAR  DIR
     *    Y/N       Y/N        Y            Y    N    Y
     *    Y/N       Y/N        N            Y    N    N
     *
     * Notes: No WAR file is present in the appBase because it is an external
     *        WAR.
     *        Any context.xml file embedded in the external WAR file is ignored.
     */
    @Test
    public void testDeploymentXmlExternalWarXmlFFF() throws Exception {
        File war = createWar(WAR_XML_SOURCE, false);
        createXmlInConfigBaseForExternal(war);
        doTestDeployment(false, false, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlFFT() throws Exception {
        File war = createWar(WAR_XML_SOURCE, false);
        createXmlInConfigBaseForExternal(war);
        doTestDeployment(false, false, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, true);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlFTF() throws Exception {
        File war = createWar(WAR_XML_SOURCE, false);
        createXmlInConfigBaseForExternal(war);
        doTestDeployment(false, true, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlFTT() throws Exception {
        File war = createWar(WAR_XML_SOURCE, false);
        createXmlInConfigBaseForExternal(war);
        doTestDeployment(false, true, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, true);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlTFF() throws Exception {
        File war = createWar(WAR_XML_SOURCE, false);
        createXmlInConfigBaseForExternal(war);
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlTFT() throws Exception {
        File war = createWar(WAR_XML_SOURCE, false);
        createXmlInConfigBaseForExternal(war);
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, true);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlTTF() throws Exception {
        File war = createWar(WAR_XML_SOURCE, false);
        createXmlInConfigBaseForExternal(war);
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlTTT() throws Exception {
        File war = createWar(WAR_XML_SOURCE, false);
        createXmlInConfigBaseForExternal(war);
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, true);
    }


    /*
     * Expected behaviour for deployment of an XML file that points to an
     * external DIR.
     * deployXML  copyXML  unpackWARs      XML  WAR  DIR
     *    Y/N       Y/N       Y/N           Y    N    N
     *
     * Notes: Any context.xml file embedded in the external DIR file is ignored.
     */
    @Test
    public void testDeploymentXmlExternalDirXmlFFF() throws Exception {
        File dir = createDirInExternal(true);
        createXmlInConfigBaseForExternal(dir);
        doTestDeployment(false, false, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlFFT() throws Exception {
        File dir = createDirInExternal(true);
        createXmlInConfigBaseForExternal(dir);
        doTestDeployment(false, false, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlFTF() throws Exception {
        File dir = createDirInExternal(true);
        createXmlInConfigBaseForExternal(dir);
        doTestDeployment(false, true, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlFTT() throws Exception {
        File dir = createDirInExternal(true);
        createXmlInConfigBaseForExternal(dir);
        doTestDeployment(false, true, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlTFF() throws Exception {
        File dir = createDirInExternal(true);
        createXmlInConfigBaseForExternal(dir);
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlTFT() throws Exception {
        File dir = createDirInExternal(true);
        createXmlInConfigBaseForExternal(dir);
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlTTF() throws Exception {
        File dir = createDirInExternal(true);
        createXmlInConfigBaseForExternal(dir);
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlTTT() throws Exception {
        File dir = createDirInExternal(true);
        createXmlInConfigBaseForExternal(dir);
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }


    /*
     * Expected behaviour for deployment of a WAR with an embedded XML file.
     * deployXML  copyXML  unpackWARs      XML  WAR  DIR
     *     N        Y/N        N            N    Y    N
     *     N        Y/N        Y            N    Y    Y
     *     Y         N         N            N    Y    N
     *     Y         N         Y            N    Y    Y
     *     Y         Y         N            Y    Y    N
     *     Y         Y         Y            Y    Y    Y
     */
    @Test
    public void testDeploymentWarXmlFFF() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(false, false, false,
                LifecycleState.FAILED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarXmlFFT() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(false, false, true,
                LifecycleState.FAILED, null, false, true, true);
    }

    @Test
    public void testDeploymentWarXmlFTF() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(false, true, false,
                LifecycleState.FAILED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarXmlFTT() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(false, true, true,
                LifecycleState.FAILED, null, false, true, true);
    }

    @Test
    public void testDeploymentWarXmlTFF() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, WAR_COOKIE_NAME, false, true, false);
    }

    @Test
    public void testDeploymentWarXmlTFT() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, WAR_COOKIE_NAME, false, true, true);
    }

    @Test
    public void testDeploymentWarXmlTTF() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, WAR_COOKIE_NAME, true, true, false);
    }

    @Test
    public void testDeploymentWarXmlTTT() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, WAR_COOKIE_NAME, true, true, true);
    }


    /*
     * Expected behaviour for deployment of a WAR without an embedded XML file.
     * deployXML  copyXML  unpackWARs      XML  WAR  DIR
     *    Y/N       Y/N        N            N    Y    N
     *    Y/N       Y/N        Y            N    Y    Y
     */
    @Test
    public void testDeploymentWarFFF() throws Exception {
        createWar(WAR_SOURCE, true);
        doTestDeployment(false, false, false,
                LifecycleState.STARTED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarFFT() throws Exception {
        createWar(WAR_SOURCE, true);
        doTestDeployment(false, false, true,
                LifecycleState.STARTED, null, false, true, true);
    }

    @Test
    public void testDeploymentWarFTF() throws Exception {
        createWar(WAR_SOURCE, true);
        doTestDeployment(false, true, false,
                LifecycleState.STARTED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarFTT() throws Exception {
        createWar(WAR_SOURCE, true);
        doTestDeployment(false, true, true,
                LifecycleState.STARTED, null, false, true, true);
    }

    @Test
    public void testDeploymentWarTFF() throws Exception {
        createWar(WAR_SOURCE, true);
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarTFT() throws Exception {
        createWar(WAR_SOURCE, true);
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, null, false, true, true);
    }

    @Test
    public void testDeploymentWarTTF() throws Exception {
        createWar(WAR_SOURCE, true);
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarTTT() throws Exception {
        createWar(WAR_SOURCE, true);
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, null, false, true, true);
    }


    /*
     * Expected behaviour for deployment of a DIR with an embedded XML file.
     * deployXML  copyXML  unpackWARs      XML  WAR  DIR
     *     N        Y/N       Y/N           N    N    Y
     *     Y         N        Y/N           N    N    Y
     *     Y         Y        Y/N           Y    N    Y
     */
    @Test
    public void testDeploymentDirXmlFFF() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(false, false, false,
                LifecycleState.FAILED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlFFT() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(false, false, true,
                LifecycleState.FAILED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlFTF() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(false, true, false,
                LifecycleState.FAILED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlFTT() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(false, true, true,
                LifecycleState.FAILED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlTFF() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, DIR_COOKIE_NAME, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlTFT() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, DIR_COOKIE_NAME, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlTTF() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, DIR_COOKIE_NAME, true, false, true);
    }

    @Test
    public void testDeploymentDirXmlTTT() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, DIR_COOKIE_NAME, true, false, true);
    }


    /*
     * Expected behaviour for deployment of a DIR without an embedded XML file.
     * deployXML  copyXML  unpackWARs      XML  WAR  DIR
     *    Y/N       Y/N       Y/N           N    N    Y
     */
    @Test
    public void testDeploymentDirFFF() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(false, false, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirFFT() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(false, false, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirFTF() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(false, true, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirFTT() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(false, true, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirTFF() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirTFT() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirTTF() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirTTT() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    private void doTestDeployment(boolean deployXML, boolean copyXML,
            boolean unpackWARs, LifecycleState resultState, String cookieName,
            boolean resultXml, boolean resultWar, boolean resultDir)
            throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // Start the instance
        tomcat.start();

        // Set the attributes
        StandardHost host = (StandardHost) tomcat.getHost();
        host.setDeployXML(deployXML);
        host.setCopyXML(copyXML);
        host.setUnpackWARs(unpackWARs);

        // Trigger automatic deployment
        host.backgroundProcess();

        // Test the results
        Context ctxt = (Context) tomcat.getHost().findChild(APP_NAME.getPath());
        if (resultState == null) {
            Assert.assertNull(ctxt);
        } else {
            Assert.assertNotNull(ctxt);
            Assert.assertEquals(resultState, ctxt.getState());
            Assert.assertEquals(cookieName, ctxt.getSessionCookieName());
        }

        File xml = new File(
                host.getConfigBaseFile(), APP_NAME.getBaseName() + ".xml");
        Assert.assertEquals(
                Boolean.valueOf(resultXml), Boolean.valueOf(xml.isFile()));

        File war = new File(
                host.getAppBaseFile(), APP_NAME.getBaseName() + ".war");
        Assert.assertEquals(
                Boolean.valueOf(resultWar), Boolean.valueOf(war.isFile()));

        File dir = new File(host.getAppBase(), APP_NAME.getBaseName());
        Assert.assertEquals(
                Boolean.valueOf(resultDir), Boolean.valueOf(dir.isDirectory()));
    }


    private File createDirInAppbase(boolean withXml) throws IOException {
        File dir = new File(getTomcatInstance().getHost().getAppBaseFile(),
                APP_NAME.getBaseName());
        if (withXml) {
            recursiveCopy(DIR_XML_SOURCE.toPath(), dir.toPath());
        } else {
            recursiveCopy(DIR_SOURCE.toPath(), dir.toPath());
        }
        return dir;
    }

    private File createDirInExternal(boolean withXml) throws IOException {
        File ext = new File(external, "external" + ".war");
        if (withXml) {
            recursiveCopy(DIR_XML_SOURCE.toPath(), ext.toPath());
        } else {
            recursiveCopy(DIR_SOURCE.toPath(), ext.toPath());
        }
        return ext;
    }

    private File createWar(File src, boolean useAppbase) throws IOException {
        File dest;
        if (useAppbase) {
            dest = new File(getTomcatInstance().getHost().getAppBaseFile(),
                APP_NAME.getBaseName() + ".war");
        } else {
            dest = new File(external, "external" + ".war");
        }
        Files.copy(src.toPath(), dest.toPath());
        // Make sure that HostConfig thinks the WAR has been modified.
        Assert.assertTrue("Failed to set last modified for [" + dest + "]", dest.setLastModified(
                System.currentTimeMillis() - 2 * HostConfig.FILE_MODIFICATION_RESOLUTION_MS));
        return dest;
    }

    private File createXmlInConfigBaseForAppbase() throws IOException {
        File xml = getXmlInConfigBaseForAppbase();
        File parent = xml.getParentFile();
        if (!parent.isDirectory()) {
            Assert.assertTrue(parent.mkdirs());
        }
        Files.copy(XML_SOURCE.toPath(), xml.toPath());
        // Make sure that HostConfig thinks the xml has been modified.
        Assert.assertTrue("Failed to set last modified for [" + xml + "]", xml.setLastModified(
                System.currentTimeMillis() - 2 * HostConfig.FILE_MODIFICATION_RESOLUTION_MS));
        return xml;
    }

    private File getXmlInConfigBaseForAppbase() {
        Host host = getTomcatInstance().getHost();
        return new File(host.getConfigBaseFile(), APP_NAME + ".xml");
    }

    private File createXmlInConfigBaseForExternal(File ext) throws IOException {
        return createXmlInConfigBaseForExternal(ext, false);
    }

    private File createXmlInConfigBaseForExternal(File ext, boolean antiLocking)
            throws IOException {
        File xml = new File(getTomcatInstance().getHost().getConfigBaseFile(),
                APP_NAME + ".xml");
        File parent = xml.getParentFile();
        if (!parent.isDirectory()) {
            Assert.assertTrue(parent.mkdirs());
        }

        try (FileOutputStream fos = new FileOutputStream(xml)) {
            StringBuilder context = new StringBuilder();
            context.append("<Context sessionCookieName=\"");
            context.append(XML_COOKIE_NAME);
            context.append("\" docBase=\"");
            context.append(ext.getAbsolutePath());
            if (antiLocking) {
                context.append("\" antiResourceLocking=\"true");
            }
            context.append("\" />");
            fos.write(context.toString().getBytes(StandardCharsets.ISO_8859_1));
        }
        // Make sure that HostConfig thinks the xml has been modified.
        Assert.assertTrue("Failed to set last modified for [" + xml + "]", xml.setLastModified(
                System.currentTimeMillis() - 2 * HostConfig.FILE_MODIFICATION_RESOLUTION_MS));
        return xml;
    }

    public static class TesterContext extends StandardContext {
        // No functional change
    }
}
