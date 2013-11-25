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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.IOTools;
import org.apache.tomcat.util.buf.B2CConverter;

/**
 * The purpose of this class is to test the automatic deployment features of the
 * {@link HostConfig} implementation.
 */
public class TestHostConfigAutomaticDeployment extends TomcatBaseTest {

    private static final ContextName  APP_NAME = new ContextName("myapp");
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

    private static final int XML = 1;
    private static final int EXT = 2;
    private static final int WAR = 3;
    private static final int DIR = 4;

    private static final int NONE = 1;
    private static final int RELOAD = 2;
    private static final int REDEPLOY = 3;

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
        initTestDeploymentXml();
        doTestDeployment(false, false, false,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlFFT() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(false, false, true,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlFTF() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(false, true, false,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlFTT() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(false, true, true,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlTFF() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(true, false, false,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlTFT() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(true, false, true,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlTTF() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(true, true, false,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlTTT() throws Exception {
        initTestDeploymentXml();
        doTestDeployment(true, true, true,
                LifecycleState.FAILED, XML_COOKIE_NAME, true, false, false);
    }

    private void initTestDeploymentXml() throws IOException {
        File xml = new File(getConfigBaseFile(getTomcatInstance().getHost()),
                APP_NAME + ".xml");
        File parent = xml.getParentFile();
        if (!parent.isDirectory()) {
            Assert.assertTrue(parent.mkdirs());
        }

        copy(XML_SOURCE, xml);
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
        initTestDeploymentXmlExternalWarXml();
        doTestDeployment(false, false, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlFFT() throws Exception {
        initTestDeploymentXmlExternalWarXml();
        doTestDeployment(false, false, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, true);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlFTF() throws Exception {
        initTestDeploymentXmlExternalWarXml();
        doTestDeployment(false, true, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlFTT() throws Exception {
        initTestDeploymentXmlExternalWarXml();
        doTestDeployment(false, true, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, true);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlTFF() throws Exception {
        initTestDeploymentXmlExternalWarXml();
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlTFT() throws Exception {
        initTestDeploymentXmlExternalWarXml();
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, true);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlTTF() throws Exception {
        initTestDeploymentXmlExternalWarXml();
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalWarXmlTTT() throws Exception {
        initTestDeploymentXmlExternalWarXml();
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, true);
    }

    private void initTestDeploymentXmlExternalWarXml() throws IOException {
        // Copy the test WAR file to the external directory
        File war = new File(external, "external" + ".war");
        copy(WAR_XML_SOURCE, war);

        // Create the XML file
        File xml = new File(getConfigBaseFile(getTomcatInstance().getHost()),
                APP_NAME + ".xml");
        File parent = xml.getParentFile();
        if (!parent.isDirectory()) {
            Assert.assertTrue(parent.mkdirs());
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(xml);
            fos.write(("<Context sessionCookieName=\"" + XML_COOKIE_NAME +
                    "\" docBase=\"" + war.getAbsolutePath() + "\" />").getBytes(
                    B2CConverter.ISO_8859_1));
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
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
        initTestDeploymentXmlExternalDirXml();
        doTestDeployment(false, false, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlFFT() throws Exception {
        initTestDeploymentXmlExternalDirXml();
        doTestDeployment(false, false, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlFTF() throws Exception {
        initTestDeploymentXmlExternalDirXml();
        doTestDeployment(false, true, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlFTT() throws Exception {
        initTestDeploymentXmlExternalDirXml();
        doTestDeployment(false, true, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlTFF() throws Exception {
        initTestDeploymentXmlExternalDirXml();
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlTFT() throws Exception {
        initTestDeploymentXmlExternalDirXml();
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlTTF() throws Exception {
        initTestDeploymentXmlExternalDirXml();
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    @Test
    public void testDeploymentXmlExternalDirXmlTTT() throws Exception {
        initTestDeploymentXmlExternalDirXml();
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, XML_COOKIE_NAME, true, false, false);
    }

    private void initTestDeploymentXmlExternalDirXml() throws IOException {
        // Copy the test DIR file to the external directory
        File dir = new File(external, "external");
        recurrsiveCopy(DIR_XML_SOURCE, dir);

        // Create the XML file
        File xml = new File(getConfigBaseFile(getTomcatInstance().getHost()),
                APP_NAME + ".xml");
        File parent = xml.getParentFile();
        if (!parent.isDirectory()) {
            Assert.assertTrue(parent.mkdirs());
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(xml);
            fos.write(("<Context sessionCookieName=\"" + XML_COOKIE_NAME +
                    "\" docBase=\"" + dir.getAbsolutePath() + "\" />").getBytes(
                    B2CConverter.ISO_8859_1));
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
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
        initTestDeploymentWarXml();
        doTestDeployment(false, false, false,
                LifecycleState.STARTED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarXmlFFT() throws Exception {
        initTestDeploymentWarXml();
        doTestDeployment(false, false, true,
                LifecycleState.STARTED, null, false, true, true);
    }

    @Test
    public void testDeploymentWarXmlFTF() throws Exception {
        initTestDeploymentWarXml();
        doTestDeployment(false, true, false,
                LifecycleState.STARTED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarXmlFTT() throws Exception {
        initTestDeploymentWarXml();
        doTestDeployment(false, true, true,
                LifecycleState.STARTED, null, false, true, true);
    }

    @Test
    public void testDeploymentWarXmlTFF() throws Exception {
        initTestDeploymentWarXml();
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, WAR_COOKIE_NAME, false, true, false);
    }

    @Test
    public void testDeploymentWarXmlTFT() throws Exception {
        initTestDeploymentWarXml();
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, WAR_COOKIE_NAME, false, true, true);
    }

    @Test
    public void testDeploymentWarXmlTTF() throws Exception {
        initTestDeploymentWarXml();
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, WAR_COOKIE_NAME, true, true, false);
    }

    @Test
    public void testDeploymentWarXmlTTT() throws Exception {
        initTestDeploymentWarXml();
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, WAR_COOKIE_NAME, true, true, true);
    }

    private void initTestDeploymentWarXml() throws IOException {
        // Copy the test WAR file to the appBase
        File dest = new File(getAppBaseFile(getTomcatInstance().getHost()),
                APP_NAME.getBaseName() + ".war");
        copy(WAR_XML_SOURCE, dest);
    }


    /*
     * Expected behaviour for deployment of a WAR without an embedded XML file.
     * deployXML  copyXML  unpackWARs      XML  WAR  DIR
     *    Y/N       Y/N        N            N    Y    N
     *    Y/N       Y/N        Y            N    Y    Y
     */
    @Test
    public void testDeploymentWarFFF() throws Exception {
        initTestDeploymentWar();
        doTestDeployment(false, false, false,
                LifecycleState.STARTED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarFFT() throws Exception {
        initTestDeploymentWar();
        doTestDeployment(false, false, true,
                LifecycleState.STARTED, null, false, true, true);
    }

    @Test
    public void testDeploymentWarFTF() throws Exception {
        initTestDeploymentWar();
        doTestDeployment(false, true, false,
                LifecycleState.STARTED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarFTT() throws Exception {
        initTestDeploymentWar();
        doTestDeployment(false, true, true,
                LifecycleState.STARTED, null, false, true, true);
    }

    @Test
    public void testDeploymentWarTFF() throws Exception {
        initTestDeploymentWar();
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarTFT() throws Exception {
        initTestDeploymentWar();
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, null, false, true, true);
    }

    @Test
    public void testDeploymentWarTTF() throws Exception {
        initTestDeploymentWar();
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarTTT() throws Exception {
        initTestDeploymentWar();
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, null, false, true, true);
    }

    private void initTestDeploymentWar() throws IOException {
        // Copy the test WAR file to the appBase
        File dest = new File(getAppBaseFile(getTomcatInstance().getHost()),
                APP_NAME.getBaseName() + ".war");
        copy(WAR_SOURCE, dest);
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
        initTestDeploymentDirXml();
        doTestDeployment(false, false, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlFFT() throws Exception {
        initTestDeploymentDirXml();
        doTestDeployment(false, false, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlFTF() throws Exception {
        initTestDeploymentDirXml();
        doTestDeployment(false, true, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlFTT() throws Exception {
        initTestDeploymentDirXml();
        doTestDeployment(false, true, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlTFF() throws Exception {
        initTestDeploymentDirXml();
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, DIR_COOKIE_NAME, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlTFT() throws Exception {
        initTestDeploymentDirXml();
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, DIR_COOKIE_NAME, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlTTF() throws Exception {
        initTestDeploymentDirXml();
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, DIR_COOKIE_NAME, true, false, true);
    }

    @Test
    public void testDeploymentDirXmlTTT() throws Exception {
        initTestDeploymentDirXml();
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, DIR_COOKIE_NAME, true, false, true);
    }

    private void initTestDeploymentDirXml() throws IOException {
        // Copy the test DIR file to the appBase
        File dest = new File(getAppBaseFile(getTomcatInstance().getHost()),
                APP_NAME.getBaseName());
        recurrsiveCopy(DIR_XML_SOURCE, dest);
    }


    /*
     * Expected behaviour for deployment of a DIR without an embedded XML file.
     * deployXML  copyXML  unpackWARs      XML  WAR  DIR
     *    Y/N       Y/N       Y/N           N    N    Y
     */
    @Test
    public void testDeploymentDirFFF() throws Exception {
        initTestDeploymentDir();
        doTestDeployment(false, false, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirFFT() throws Exception {
        initTestDeploymentDir();
        doTestDeployment(false, false, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirFTF() throws Exception {
        initTestDeploymentDir();
        doTestDeployment(false, true, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirFTT() throws Exception {
        initTestDeploymentDir();
        doTestDeployment(false, true, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirTFF() throws Exception {
        initTestDeploymentDir();
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirTFT() throws Exception {
        initTestDeploymentDir();
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirTTF() throws Exception {
        initTestDeploymentDir();
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirTTT() throws Exception {
        initTestDeploymentDir();
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    private void initTestDeploymentDir() throws IOException {
        // Copy the test DIR file to the appBase
        File dest = new File(getAppBaseFile(getTomcatInstance().getHost()),
                APP_NAME.getBaseName());
        recurrsiveCopy(DIR_SOURCE, dest);
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
                getConfigBaseFile(host), APP_NAME.getBaseName() + ".xml");
        Assert.assertEquals(
                Boolean.valueOf(resultXml), Boolean.valueOf(xml.isFile()));

        File war = new File(
                getAppBaseFile(host), APP_NAME.getBaseName() + ".war");
        Assert.assertEquals(
                Boolean.valueOf(resultWar), Boolean.valueOf(war.isFile()));

        File dir = new File(host.getAppBase(), APP_NAME.getBaseName());
        Assert.assertEquals(
                Boolean.valueOf(resultDir), Boolean.valueOf(dir.isDirectory()));
    }


    /*
     * Expected behaviour for the deletion of files.
     *
     * Artifacts present     Artifact     Artifacts remaining
     *  XML  WAR  EXT  DIR    Removed     XML  WAR  EXT DIR    Notes
     *   N    N    N    Y       DIR        -    -    -   N
     *   N    Y    N    N       WAR        -    N    -   -
     *   N    Y    N    Y       DIR        -    Y    -   R     1
     *   N    Y    N    Y       WAR        -    N    -   N
     *   Y    N    N    N       XML        N    -    -   -
     *   Y    N    N    Y       DIR        N    -    -   N
     *   Y    N    N    Y       XML        R    -    -   Y     2
     *   Y    N    Y    N       EXT        Y    -    N   -
     *   Y    N    Y    N       XML        N    -    Y   -
     *   Y    N    Y    Y       DIR        R    -    Y   R     1,2
     *   Y    N    Y    Y       EXT        Y    -    N   N
     *   Y    N    Y    Y       XML        N    -    Y   N
     *   Y    Y    N    N       WAR        N    N    -   -
     *   Y    Y    N    N       XML        N    N    -   -
     *   Y    Y    N    Y       DIR        R    Y    -   R     1,2
     *   Y    Y    N    Y       WAR        N    N    -   -
     *   Y    Y    N    Y       XML        R    Y    -   Y
     *
     * Notes: 1. The DIR will be re-created since unpackWARs is true.
     *        2. The XML will be extracted from the WAR/DIR if deployXML and
     *           copyXML are true.
     */
    @Test
    public void testDeleteDirRemoveDir() throws Exception {
        doTestDelete(false, false, false, false, true, DIR, false, false, false,
                null);
    }

    @Test
    public void testDeleteWarRemoveWar() throws Exception {
        doTestDelete(false, false, false, true, false, WAR, false, false, false,
                null);
    }

    @Test
    public void testDeleteWarDirRemoveDir() throws Exception {
        doTestDelete(false, false, false, true, true, DIR, false, true, true,
                WAR_COOKIE_NAME);
    }

    @Test
    public void testDeleteWarDirRemoveWar() throws Exception {
        doTestDelete(false, false, false, true, true, WAR, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlRemoveXml() throws Exception {
        doTestDelete(true, false, false, false, false, XML, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlDirRemoveDir() throws Exception {
        doTestDelete(true, false, false, false, true, DIR, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlDirRemoveXml() throws Exception {
        doTestDelete(true, false, false, false, true, XML, false, false, true,
                DIR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlDirRemoveXmlCopyXml() throws Exception {
        ((StandardHost) getTomcatInstance().getHost()).setCopyXML(true);
        doTestDelete(true, false, false, false, true, XML, true, false, true,
                DIR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlExtwarRemoveExt() throws Exception {
        doTestDelete(true, true, false, false, false, EXT, true, false, false,
                XML_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlExtdirRemoveExt() throws Exception {
        doTestDelete(true, false, true, false, false, EXT, true, false, false,
                XML_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlExtwarRemoveXml() throws Exception {
        doTestDelete(true, true, false, false, false, XML, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlExtdirRemoveXml() throws Exception {
        doTestDelete(true, false, true, false, false, XML, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlExtwarDirRemoveDir() throws Exception {
        doTestDelete(true, true, false, false, true, DIR, true, false, true,
                XML_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlExtwarDirRemoveExt() throws Exception {
        doTestDelete(true, true, false, false, true, EXT, true, false, false,
                XML_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlExtwarDirRemoveXml() throws Exception {
        doTestDelete(true, true, false, false, true, XML, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlWarRemoveWar() throws Exception {
        doTestDelete(true, false, false, true, false, WAR, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlWarRemoveXml() throws Exception {
        doTestDelete(true, false, false, true, false, XML, false, true, false,
                WAR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlWarRemoveXmlCopyXml() throws Exception {
        ((StandardHost) getTomcatInstance().getHost()).setCopyXML(true);
        doTestDelete(true, false, false, true, false, XML, true, true, false,
                WAR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlWarDirRemoveDir() throws Exception {
        doTestDelete(true, false, false, true, true, DIR, false, true, true,
                WAR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlWarDirRemoveDirCopyXml() throws Exception {
        ((StandardHost) getTomcatInstance().getHost()).setCopyXML(true);
        doTestDelete(true, false, false, true, true, DIR, true, true, true,
                WAR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlWarDirRemoveWar() throws Exception {
        doTestDelete(true, false, false, true, true, WAR, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlWarDirRemoveWarCopyXml() throws Exception {
        ((StandardHost) getTomcatInstance().getHost()).setCopyXML(true);
        doTestDelete(true, false, false, true, true, WAR, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlWarDirRemoveXml() throws Exception {
        doTestDelete(true, false, false, true, true, XML, false, true, true,
                DIR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlWarDirRemoveXmlCopyXml() throws Exception {
        ((StandardHost) getTomcatInstance().getHost()).setCopyXML(true);
        doTestDelete(true, false, false, true, true, XML, true, true, true,
                WAR_COOKIE_NAME);
    }

    private void doTestDelete(boolean startXml, boolean startExternalWar,
            boolean startExternalDir, boolean startWar, boolean startDir,
            int toDelete, boolean resultXml, boolean resultWar,
            boolean resultDir, String resultCookieName) throws Exception {

        Tomcat tomcat = getTomcatInstance();
        StandardHost host = (StandardHost) tomcat.getHost();

        // Init
        File xml = null;
        File ext = null;
        File war = null;
        File dir = null;

        if (startXml && !startExternalWar && !startExternalDir) {
            xml = new File(getConfigBaseFile(host), APP_NAME + ".xml");
            File parent = xml.getParentFile();
            if (!parent.isDirectory()) {
                Assert.assertTrue(parent.mkdirs());
            }
            copy(XML_SOURCE, xml);
        }
        if (startExternalWar) {
            // Copy the test WAR file to the external directory
            ext = new File(external, "external" + ".war");
            copy(WAR_XML_SOURCE, ext);

            // Create the XML file
            xml = new File(getConfigBaseFile(host), APP_NAME + ".xml");
            File parent = xml.getParentFile();
            if (!parent.isDirectory()) {
                Assert.assertTrue(parent.mkdirs());
            }
            
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(xml);
                fos.write(("<Context sessionCookieName=\"" + XML_COOKIE_NAME +
                        "\" docBase=\"" + ext.getAbsolutePath() +
                        "\" />").getBytes(B2CConverter.ISO_8859_1));
            } finally {
                if (fos != null) {
                    fos.close();
                }
            }
        }
        if (startExternalDir) {
            // Copy the test DIR file to the external directory
            ext = new File(external, "external");
            recurrsiveCopy(DIR_XML_SOURCE, ext);

            // Create the XML file
            xml = new File(getConfigBaseFile(getTomcatInstance().getHost()),
                    APP_NAME + ".xml");
            File parent = xml.getParentFile();
            if (!parent.isDirectory()) {
                Assert.assertTrue(parent.mkdirs());
            }

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(xml);
                fos.write(("<Context sessionCookieName=\"" + XML_COOKIE_NAME +
                        "\" docBase=\"" + ext.getAbsolutePath() +
                        "\" />").getBytes(B2CConverter.ISO_8859_1));
            } finally {
                if (fos != null) {
                    fos.close();
                }
            }
        }
        if (startWar) {
            // Copy the test WAR file to the appBase
            war = new File(getAppBaseFile(getTomcatInstance().getHost()),
                    APP_NAME.getBaseName() + ".war");
            copy(WAR_XML_SOURCE, war);
        }
        if (startDir) {
            // Copy the test DIR file to the appBase
            dir = new File(getAppBaseFile(getTomcatInstance().getHost()),
                    APP_NAME.getBaseName());
            recurrsiveCopy(DIR_XML_SOURCE, dir);
        }

        if ((startWar || startExternalWar) && !startDir) {
            host.setUnpackWARs(false);
            // WARDirContext always locks the WAR file so need to use
            // anti-resource locking to enable the WAR to be deleted
            host.setContextClass(AntiResourceLockingContext.class.getName());
        }

        // Deploy the files we copied
        tomcat.start();
        host.backgroundProcess();

        // Remove the specified file
        switch (toDelete) {
            case XML:
                ExpandWar.delete(xml);
                break;
            case EXT:
                ExpandWar.delete(ext);
                break;
            case WAR:
                ExpandWar.delete(war);
                break;
            case DIR:
                ExpandWar.delete(dir);
                break;
            default:
                Assert.fail();
        }

        // Trigger an auto-deployment cycle
        host.backgroundProcess();

        Context ctxt = (Context) host.findChild(APP_NAME.getName());

        // Check the results
        // External WAR and DIR should only be deleted if the test is testing
        // behaviour when the external resource is deleted
        if (toDelete == EXT) {
            if (ext == null) {
                Assert.fail();
            } else {
                Assert.assertFalse(ext.exists());
            }
        } else {
            if (startExternalWar) {
                if (ext == null) {
                    Assert.fail();
                } else {
                    Assert.assertTrue(ext.isFile());
                }
            }
            if (startExternalDir) {
                if (ext == null) {
                    Assert.fail();
                } else {
                    Assert.assertTrue(ext.isDirectory());
                }
            }
        }

        if (resultXml) {
            if (xml == null) {
                Assert.fail();
            } else {
                Assert.assertTrue(xml.isFile());
            }
        }
        if (resultWar) {
            if (war == null) {
                Assert.fail();
            } else {
                Assert.assertTrue(war.isFile());
            }
        }
        if (resultDir) {
            if (dir == null) {
                Assert.fail();
            } else {
                Assert.assertTrue(dir.isDirectory());
            }
        }

        if (!resultXml && (startExternalWar || startExternalDir)) {
            Assert.assertNull(ctxt);
        }
        if (!resultWar && !resultDir) {
            if (resultXml) {
                Assert.assertEquals(LifecycleState.FAILED, ctxt.getState());
            } else {
                Assert.assertNull(ctxt);
            }
        }

        if (ctxt != null) {
            Assert.assertEquals(resultCookieName, ctxt.getSessionCookieName());
        }
    }


    /*
     * Expected behaviour for modification of files.
     *
     * Artifacts present      Artifact   Artifacts remaining
     * XML  WAR  EXT  DIR    Modified    XML  WAR  EXT DIR   Action
     *  N    N    N    Y       DIR        -    -    -   M     None
     *  N    Y    N    N       WAR        -    M    -   -   Redeploy
     *  N    Y    N    Y       DIR        -    Y    -   M     None
     *  N    Y    N    Y       WAR        -    M    -   R   Redeploy
     *  Y    N    N    N       XML        M    -    -   -   Redeploy
     *  Y    N    N    Y       DIR        Y    -    -   M     None
     *  Y    N    N    Y       XML        M    -    -   Y   Redeploy
     *  Y    N    Y    N       EXT        Y    -    M   -   Reload if WAR
     *  Y    N    Y    N       XML        M    -    Y   -   Redeploy
     *  Y    N    Y    Y       DIR        Y    -    Y   M     None
     *  Y    N    Y    Y       EXT        Y    -    M   R    Reload
     *  Y    N    Y    Y       XML        M    -    Y   Y   Redeploy
     *  Y    Y    N    N       WAR        Y    M    -   -    Reload
     *  Y    Y    N    N       XML        M    Y    -   -   Redeploy
     *  Y    Y    N    Y       DIR        Y    Y    -   M     None
     *  Y    Y    N    Y       WAR        Y    M    -   -    Reload
     *  Y    Y    N    Y       XML        M    Y    -   Y   Redeploy
     */
    @Test
    public void testModifyDirUpdateDir() throws Exception {
        doTestModify(false, false, false, false, true, DIR,
                false, false, true, DIR_COOKIE_NAME, NONE);
    }

    @Test
    public void testModifyWarUpdateWar() throws Exception {
        doTestModify(false, false, false, true, false, WAR,
                false, true, false, WAR_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testModifyWarDirUpdateDir() throws Exception {
        // DIR_COOKIE_NAME since Tomcat is going to assume DIR is expanded WAR
        doTestModify(false, false, false, true, true, DIR,
                false, true, true, DIR_COOKIE_NAME, NONE);
    }

    @Test
    public void testModifyWarDirUpdateWar() throws Exception {
        doTestModify(false, false, false, true, true, WAR,
                false, true, true, WAR_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testModifyXmlUpdateXml() throws Exception {
        doTestModify(true, false, false, false, false, XML,
                true, false, false, XML_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testModifyXmlDirUpdateDir() throws Exception {
        doTestModify(true, false, false, false, true, DIR,
                true, false, true, XML_COOKIE_NAME, NONE);
    }

    @Test
    public void testModifyXmlDirUpdateXml() throws Exception {
        doTestModify(true, false, false, false, true, XML,
                true, false, true, XML_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testModifyXmlExtwarUpdateExtwar() throws Exception {
        doTestModify(true, true, false, false, false, EXT,
                true, false, false, XML_COOKIE_NAME, RELOAD);
    }

    @Test
    public void testModifyXmlExtdirUpdateExtdir() throws Exception {
        doTestModify(true, false, true, false, false, EXT,
                true, false, false, XML_COOKIE_NAME, NONE);
    }

    @Test
    public void testModifyXmlExtwarUpdateXml() throws Exception {
        doTestModify(true, true, false, false, false, XML,
                true, false, false, XML_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testModifyXmlExtdirUpdateXml() throws Exception {
        doTestModify(true, false, true, false, false, XML,
                true, false, false, XML_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testModifyXmlExtwarDirUpdateDir() throws Exception {
        doTestModify(true, true, false, false, true, DIR,
                true, false, false, XML_COOKIE_NAME, NONE);
    }

    @Test
    public void testModifyXmlExtwarDirUpdateExt() throws Exception {
        doTestModify(true, true, false, false, true, EXT,
                true, false, true, XML_COOKIE_NAME, RELOAD);
    }

    @Test
    public void testModifyXmlExtwarDirUpdateXml() throws Exception {
        doTestModify(true, true, false, false, true, XML,
                true, false, false, XML_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testModifyXmlWarUpdateWar() throws Exception {
        doTestModify(true, false, false, true, false, WAR,
                true, true, false, XML_COOKIE_NAME, RELOAD);
    }

    @Test
    public void testModifyXmlWarUpdateXml() throws Exception {
        doTestModify(true, false, false, true, false, XML,
                true, true, false, XML_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testModifyXmlWarDirUpdateDir() throws Exception {
        doTestModify(true, false, false, true, true, DIR,
                true, true, true, XML_COOKIE_NAME, NONE);
    }

    @Test
    public void testModifyXmlWarDirUpdateWar() throws Exception {
        doTestModify(true, false, false, true, true, WAR,
                true, true, true, XML_COOKIE_NAME, RELOAD);
    }

    @Test
    public void testModifyXmlWarDirUpdateXml() throws Exception {
        doTestModify(true, false, false, true, true, XML,
                true, true, true, XML_COOKIE_NAME, REDEPLOY);
    }

    private void doTestModify(boolean startXml, boolean startExternalWar,
            boolean startExternalDir, boolean startWar, boolean startDir,
            int toModify, boolean resultXml, boolean resultWar,
            boolean resultDir, String resultCookieName, int resultAction)
            throws Exception {

        Tomcat tomcat = getTomcatInstance();
        StandardHost host = (StandardHost) tomcat.getHost();

        // Init
        File xml = null;
        File ext = null;
        File war = null;
        File dir = null;

        if (startXml && !startExternalWar && !startExternalDir) {
            xml = new File(getConfigBaseFile(host), APP_NAME + ".xml");
            File parent = xml.getParentFile();
            if (!parent.isDirectory()) {
                Assert.assertTrue(parent.mkdirs());
            }
            copy(XML_SOURCE, xml);
        }
        if (startExternalWar) {
            // Copy the test WAR file to the external directory
            ext = new File(external, "external" + ".war");
            copy(WAR_XML_SOURCE, ext);

            // Create the XML file
            xml = new File(getConfigBaseFile(host), APP_NAME + ".xml");
            File parent = xml.getParentFile();
            if (!parent.isDirectory()) {
                Assert.assertTrue(parent.mkdirs());
            }

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(xml);
                fos.write(("<Context sessionCookieName=\"" + XML_COOKIE_NAME +
                        "\" docBase=\"" + ext.getAbsolutePath() +
                        "\" />").getBytes(B2CConverter.ISO_8859_1));
            } finally {
                if (fos != null) {
                    fos.close();
                }
            }
        }
        if (startExternalDir) {
            // Copy the test DIR file to the external directory
            ext = new File(external, "external");
            recurrsiveCopy(DIR_XML_SOURCE, ext);

            // Create the XML file
            xml = new File(getConfigBaseFile(getTomcatInstance().getHost()),
                    APP_NAME + ".xml");
            File parent = xml.getParentFile();
            if (!parent.isDirectory()) {
                Assert.assertTrue(parent.mkdirs());
            }

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(xml);
                fos.write(("<Context sessionCookieName=\"" + XML_COOKIE_NAME +
                        "\" docBase=\"" + ext.getAbsolutePath() +
                        "\" />").getBytes(B2CConverter.ISO_8859_1));
            } finally {
                if (fos != null) {
                    fos.close();
                }
            }
        }
        if (startWar) {
            // Copy the test WAR file to the appBase
            war = new File(getAppBaseFile(getTomcatInstance().getHost()),
                    APP_NAME.getBaseName() + ".war");
            copy(WAR_XML_SOURCE, war);
        }
        if (startDir) {
            // Copy the test DIR file to the appBase
            dir = new File(getAppBaseFile(getTomcatInstance().getHost()),
                    APP_NAME.getBaseName());
            recurrsiveCopy(DIR_XML_SOURCE, dir);
        }

        if ((startWar || startExternalWar) && !startDir) {
            host.setUnpackWARs(false);
        }

        // Deploy the files we copied
        tomcat.start();
        host.backgroundProcess();

        // Change the specified file
        switch (toModify) {
            case XML:
                if (xml == null) {
                    Assert.fail();
                } else {
                    xml.setLastModified(System.currentTimeMillis());
                }
                break;
            case EXT:
                if (ext == null) {
                    Assert.fail();
                } else {
                    ext.setLastModified(System.currentTimeMillis());
                }
                break;
            case WAR:
                if (war == null) {
                    Assert.fail();
                } else {
                    war.setLastModified(System.currentTimeMillis());
                }
                break;
            case DIR:
                if (dir == null) {
                    Assert.fail();
                } else {
                    dir.setLastModified(System.currentTimeMillis());
                }
                break;
            default:
                Assert.fail();
        }

        Context oldContext = (Context) host.findChild(APP_NAME.getName());
        StateTracker tracker = new StateTracker();
        oldContext.addLifecycleListener(tracker);

        // Trigger an auto-deployment cycle
        host.backgroundProcess();

        Context newContext = (Context) host.findChild(APP_NAME.getName());

        // Check the results
        if (resultXml) {
            if (xml == null) {
                Assert.fail();
            } else {
                Assert.assertTrue(xml.isFile());
            }
        }
        if (resultWar) {
            if (war == null) {
                Assert.fail();
            } else {
                Assert.assertTrue(war.isFile());
            }
        }
        if (resultDir) {
            if (dir == null) {
                Assert.fail();
            } else {
                Assert.assertTrue(dir.isDirectory());
            }
        }

        if (!resultXml && (startExternalWar || startExternalDir)) {
            Assert.assertNull(newContext);
        }
        if (!resultWar && !resultDir) {
            if (resultXml) {
                if (!startExternalWar && !startExternalDir) {
                    Assert.assertEquals(LifecycleState.FAILED,
                            newContext.getState());
                } else {
                    Assert.assertEquals(LifecycleState.STARTED,
                            newContext.getState());
                }
            } else {
                Assert.assertNull(newContext);
            }
        }

        if (newContext != null) {
            Assert.assertEquals(resultCookieName,
                    newContext.getSessionCookieName());
        }

        if (resultAction == NONE) {
            Assert.assertSame(oldContext, newContext);
            Assert.assertEquals("", tracker.getHistory());
        } else if (resultAction == RELOAD) {
            Assert.assertSame(oldContext, newContext);
            Assert.assertEquals("stopstart", tracker.getHistory());
        } else if (resultAction == REDEPLOY) {
            Assert.assertNotSame(oldContext, newContext);
            // No init or start as that will be in a new context object
            Assert.assertEquals("stopafter_destroy", tracker.getHistory());
        } else {
            Assert.fail();
        }
    }


    @Test
    public void testSetContextClassName() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            StandardHost standardHost = (StandardHost) host;
            standardHost.setContextClass(TesterContext.class.getName());
        }

        // Copy the WAR file
        File war = new File(getAppBaseFile(host),
                APP_NAME.getBaseName() + ".war");
        copy(WAR_XML_SOURCE, war);

        // Deploy the copied war
        tomcat.start();
        host.backgroundProcess();

        // Check the Context class
        Context ctxt = (Context) host.findChild(APP_NAME.getName());

        Assert.assertTrue(ctxt instanceof TesterContext);
    }

    
    public static class AntiResourceLockingContext extends StandardContext {

        @Override
        public boolean getAntiResourceLocking() {
            return true;
        }
    }


    public static class TesterContext extends StandardContext {
        // No functional change
    }


    private static class StateTracker implements LifecycleListener {

        private StringBuilder stateHistory = new StringBuilder();

        @Override
        public void lifecycleEvent(LifecycleEvent event) {

            String type = event.getType();

            if (type.equals(Lifecycle.START_EVENT) ||
                    type.equals(Lifecycle.STOP_EVENT) ||
                    type.equals(Lifecycle.AFTER_DESTROY_EVENT)) {
                stateHistory.append(type);
            }
        }


        public String getHistory() {
            return stateHistory.toString();
        }
    }

    
    // Static methods to compensate for methods that are present in 8.0.x but
    // not in 7.0.x
    
    private static File getConfigBaseFile(Host host) {
        String path = null;
        if (host.getXmlBase() != null) {
            path = host.getXmlBase();
        } else {
            StringBuilder xmlDir = new StringBuilder("conf");
            Container parent = host.getParent();
            if (parent instanceof Engine) {
                xmlDir.append('/');
                xmlDir.append(parent.getName());
            }
            xmlDir.append('/');
            xmlDir.append(host.getName());
            path = xmlDir.toString();
        }
        
        return getCanonicalPath(path);
    }
    
    private static File getAppBaseFile(Host host) {
        return getCanonicalPath(host.getAppBase());
    }
 
    private static File getCanonicalPath(String path) {
        File file = new File(path);
        File base = new File(System.getProperty(Globals.CATALINA_BASE_PROP));
        if (!file.isAbsolute())
            file = new File(base,path);
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file;
        }
    }
    
    
    // Static methods to replace the java.nio.file.Files methods used in Tomcat
    // 8 that aren't available in Tomcat 7. These methods are not intended to be
    // 100% robust - just good enough for the unit tests to pass.

    private static void copy(File src, File dest) throws IOException {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dest);
            IOTools.flow(in, out);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    
    private static void recurrsiveCopy(File src, File dest) throws IOException {
        dest.mkdirs();
        File[] files = src.listFiles();
        
        for (File file : files) {
            File newFile = new File(dest, file.getName());
            if (file.isDirectory()) {
                recurrsiveCopy(file, newFile);
            } else {
                copy(file, newFile);
            }
        }
    }
}
