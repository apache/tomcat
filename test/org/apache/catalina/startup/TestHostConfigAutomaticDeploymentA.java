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
public class TestHostConfigAutomaticDeploymentA extends TomcatBaseTest {

    private static final ContextName  APP_NAME = new ContextName("myapp", false);
    private static final File XML_SOURCE =
            new File("test/deployment/context.xml");
    private static final File WAR_XML_SOURCE =
            new File("test/deployment/context.war");
    private static final File DIR_XML_SOURCE =
            new File("test/deployment/dirContext");
    private static final File DIR_SOURCE =
            new File("test/deployment/dirNoContext");

    private static final int XML = 1;
    private static final int EXT = 2;
    private static final int WAR = 3;
    private static final int DIR = 4;

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
            xml = createXmlInConfigBaseForAppbase();
        }
        if (startExternalWar) {
            ext = createWar(WAR_XML_SOURCE, false);
            xml = createXmlInConfigBaseForExternal(ext);
        }
        if (startExternalDir) {
            ext = createDirInExternal(true);
            xml = createXmlInConfigBaseForExternal(ext);
        }
        if (startWar) {
            war = createWar(WAR_XML_SOURCE, true);
        }
        if (startDir) {
            dir = createDirInAppbase(true);
        }

        if ((startWar || startExternalWar) && !startDir) {
            host.setUnpackWARs(false);
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
                Assert.assertNotNull(ctxt);
                Assert.assertEquals(LifecycleState.FAILED, ctxt.getState());
            } else {
                Assert.assertNull(ctxt);
            }
        }

        if (ctxt != null) {
            Assert.assertEquals(resultCookieName, ctxt.getSessionCookieName());
        }
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
