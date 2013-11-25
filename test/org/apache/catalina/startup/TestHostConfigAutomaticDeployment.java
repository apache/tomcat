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
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.util.ContextName;
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

    private static final String XML_COOKIE_NAME = "XML_CONTEXT";
    private static final String WAR_COOKIE_NAME = "WAR_CONTEXT";
    private static final String DIR_COOKIE_NAME = "DIR_CONTEXT";

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

        Files.copy(XML_SOURCE.toPath(), xml.toPath());
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
        Files.copy(WAR_XML_SOURCE.toPath(), war.toPath());

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
        recurrsiveCopy(DIR_XML_SOURCE.toPath(), dir.toPath());

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
        Files.copy(WAR_XML_SOURCE.toPath(), dest.toPath());
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
        Files.copy(WAR_SOURCE.toPath(), dest.toPath());
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
        recurrsiveCopy(DIR_XML_SOURCE.toPath(), dest.toPath());
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
        recurrsiveCopy(DIR_SOURCE.toPath(), dest.toPath());
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


    private static void recurrsiveCopy(final Path src, final Path dest)
            throws IOException {

        Files.walkFileTree(src, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                    BasicFileAttributes attrs) throws IOException {
                Files.copy(dir, dest.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                Files.copy(file, dest.resolve(src.relativize(file)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException ioe)
                    throws IOException {
                throw ioe;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException ioe)
                    throws IOException {
                // NO-OP
                return FileVisitResult.CONTINUE;
            }});
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
}
