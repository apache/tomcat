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

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.util.ContextName;

public class HostConfigAutomaticDeploymentBaseTest extends TomcatBaseTest {

    protected static final ContextName APP_NAME = new ContextName("myapp", false);
    protected static final File XML_SOURCE = new File("test/deployment/context.xml");
    protected static final File WAR_XML_SOURCE = new File("test/deployment/context.war");
    protected static final File WAR_XML_COPYXML_FALSE_SOURCE = new File("test/deployment/contextCopyXMLFalse.war");
    protected static final File WAR_XML_COPYXML_TRUE_SOURCE = new File("test/deployment/contextCopyXMLTrue.war");
    protected static final File WAR_XML_UNPACKWAR_FALSE_SOURCE = new File("test/deployment/contextUnpackWARFalse.war");
    protected static final File WAR_XML_UNPACKWAR_TRUE_SOURCE = new File("test/deployment/contextUnpackWARTrue.war");
    protected static final File WAR_SOURCE = new File("test/deployment/noContext.war");
    protected static final File WAR_BROKEN_SOURCE = new File("test/deployment/broken.war");
    protected static final File DIR_XML_SOURCE = new File("test/deployment/dirContext");
    protected static final File DIR_XML_SOURCE_META_INF = new File("test/deployment/dirContext/META-INF");
    protected static final File DIR_SOURCE = new File("test/deployment/dirNoContext");

    protected static final int XML = 1;
    protected static final int EXT = 2;
    protected static final int WAR = 3;
    protected static final int DIR = 4;
    protected static final int DIR_XML = 5;

    protected static final int NONE = 1;
    protected static final int RELOAD = 2;
    protected static final int REDEPLOY = 3;

    protected static final String XML_COOKIE_NAME = "XML_CONTEXT";
    protected static final String WAR_COOKIE_NAME = "WAR_CONTEXT";
    protected static final String DIR_COOKIE_NAME = "DIR_CONTEXT";

    protected File external;


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


    protected void doTestDelete(boolean startXml, boolean startExternalWar,
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


    protected void doTestDeployment(boolean deployXML, boolean copyXML,
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


    protected void doTestModify(boolean startXml, boolean startExternalWar,
            boolean startExternalDir, boolean startWar, boolean startDir,
            int toModify, boolean resultXml, boolean resultWar,
            boolean resultDir, String resultCookieName, int resultAction)
            throws Exception {
        doTestModify(startXml, startExternalWar, startExternalDir, startWar,
                startDir, toModify, resultXml, resultWar, resultDir,
                resultCookieName, resultAction, LifecycleState.STARTED);
    }


    protected void doTestModify(boolean startXml, boolean startExternalWar,
            boolean startExternalDir, boolean startWar, boolean startDir,
            int toModify, boolean resultXml, boolean resultWar,
            boolean resultDir, String resultCookieName, int resultAction,
            LifecycleState resultState) throws Exception {

        Tomcat tomcat = getTomcatInstance();
        StandardHost host = (StandardHost) tomcat.getHost();

        // Init
        File xml = null;
        File ext = null;
        File war = null;
        File dir = null;

        long testStartTime = System.currentTimeMillis();

        if (startXml && !startExternalWar && !startExternalDir) {
            xml = createXmlInConfigBaseForAppbase();
        }
        if (startExternalWar) {
            ext = createWar(WAR_XML_SOURCE, false);
            xml = createXmlInConfigBaseForExternal(ext);
        }
        if (startExternalDir) {
            ext = createDirInAppbase(true);
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

        // Update the last modified time. Make sure that the OS reports a change
        // in modification time that HostConfig can detect. Change is made
        // relative to test start time to ensure new modification times are
        // sufficiently different.
        switch (toModify) {
            case XML:
                if (xml == null) {
                    Assert.fail();
                } else {
                    Assert.assertTrue("Failed to set last modified for [" + xml + "]", xml.setLastModified(
                            testStartTime - 10 * HostConfig.FILE_MODIFICATION_RESOLUTION_MS));
                }
                break;
            case EXT:
                if (ext == null) {
                    Assert.fail();
                } else {
                    Assert.assertTrue("Failed to set last modified for [" + ext + "]", ext.setLastModified(
                            testStartTime - 10 * HostConfig.FILE_MODIFICATION_RESOLUTION_MS));
                }
                break;
            case WAR:
                if (war == null) {
                    Assert.fail();
                } else {
                    Assert.assertTrue("Failed to set last modified for [" + war + "]", war.setLastModified(
                            testStartTime - 10 * HostConfig.FILE_MODIFICATION_RESOLUTION_MS));
                }
                break;
            case DIR:
                if (dir == null) {
                    Assert.fail();
                } else {
                    Assert.assertTrue("Failed to set last modified for [" + dir + "]", dir.setLastModified(
                            testStartTime - 10 * HostConfig.FILE_MODIFICATION_RESOLUTION_MS));
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
                Assert.assertNotNull(newContext);
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
            Assert.assertEquals(resultState, newContext.getState());
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


    protected void doTestAddition(boolean startXml, boolean startExternalWar,
            boolean startExternalDir, boolean startWar, boolean startDir,
            int toAdd, boolean resultXml, boolean resultWar,
            boolean resultDir, String resultCookieName, int resultAction)
            throws Exception {

        doTestAddition(startXml, startExternalWar, startExternalDir, startWar,
                startDir, false, true, toAdd, resultXml, resultWar, resultDir,
                resultCookieName, resultAction, LifecycleState.STARTED);
    }


    protected void doTestAddition(boolean startXml, boolean startExternalWar,
            boolean startExternalDir, boolean startWar, boolean startDir,
            boolean copyXML, boolean deployXML, int toAdd, boolean resultXml,
            boolean resultWar, boolean resultDir, String resultCookieName,
            int resultAction, LifecycleState state)
            throws Exception {

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
            dir = createDirInAppbase(toAdd != DIR_XML);
        }

        if ((startWar || startExternalWar) && !startDir) {
            host.setUnpackWARs(false);
        }

        host.setCopyXML(copyXML);
        host.setDeployXML(deployXML);

        // Deploy the files we copied
        tomcat.start();
        host.backgroundProcess();

        // Change the specified file
        switch (toAdd) {
            case XML:
                if (xml == null) {
                    xml = createXmlInConfigBaseForAppbase();
                } else {
                    Assert.fail();
                }
                break;
            case EXT:
                if (ext == null && xml == null) {
                    ext = createWar(WAR_XML_SOURCE, false);
                    xml = createXmlInConfigBaseForExternal(ext);
                } else {
                    Assert.fail();
                }
                break;
            case WAR:
                if (war == null) {
                    war = createWar(WAR_XML_SOURCE, true);
                } else {
                    Assert.fail();
                }
                break;
            case DIR:
                if (dir == null) {
                    dir = createDirInAppbase(true);
                } else {
                    Assert.fail();
                }
                break;
            case DIR_XML:
                dir = createDirXmlInAppbase();
                xml = getXmlInConfigBaseForAppbase();
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
                Assert.assertNotNull(newContext);
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
            if (newContext == null) {
                Assert.fail();
            } else {
                Assert.assertEquals(state, newContext.getState());
            }
            Assert.assertNotSame(oldContext, newContext);
            // No init or start as that will be in a new context object
            Assert.assertEquals("stopafter_destroy", tracker.getHistory());
        } else {
            Assert.fail();
        }
    }


    protected void doTestBrokenAppWithAntiLocking(boolean unpackWARs)
            throws Exception {

        Tomcat tomcat = getTomcatInstance();
        StandardHost host = (StandardHost) tomcat.getHost();

        host.setUnpackWARs(unpackWARs);

        File war = createWar(WAR_BROKEN_SOURCE, false);
        createXmlInConfigBaseForExternal(war, true);

        File dir = new File(host.getAppBaseFile(), APP_NAME.getBaseName());

        tomcat.start();

        // Simulate deploy on start-up
        tomcat.getHost().backgroundProcess();

        Assert.assertTrue(war.isFile());
        if (unpackWARs) {
            Assert.assertTrue(dir.isDirectory());
        }
    }


    protected void doTestUnpackWAR(boolean unpackWARs, boolean unpackWAR,
            boolean external, boolean resultDir) throws Exception {

        Tomcat tomcat = getTomcatInstance();
        StandardHost host = (StandardHost) tomcat.getHost();

        host.setUnpackWARs(unpackWARs);

        tomcat.start();

        File war;
        if (unpackWAR) {
            war = createWar(WAR_XML_UNPACKWAR_TRUE_SOURCE, !external);
        } else {
            war = createWar(WAR_XML_UNPACKWAR_FALSE_SOURCE, !external);
        }
        if (external) {
            createXmlInConfigBaseForExternal(war);
        }

        host.backgroundProcess();

        File dir = new File(host.getAppBase(), APP_NAME.getBaseName());
        Assert.assertEquals(
                Boolean.valueOf(resultDir), Boolean.valueOf(dir.isDirectory()));
    }


    protected void doTestCopyXML(boolean copyXmlHost, boolean copyXmlWar,
            boolean external, boolean resultXml) throws Exception {

        Tomcat tomcat = getTomcatInstance();
        StandardHost host = (StandardHost) tomcat.getHost();

        host.setCopyXML(copyXmlHost);

        tomcat.start();

        File war;
        if (copyXmlWar) {
            war = createWar(WAR_XML_COPYXML_TRUE_SOURCE, !external);
        } else {
            war = createWar(WAR_XML_COPYXML_FALSE_SOURCE, !external);
        }
        if (external) {
            createXmlInConfigBaseForExternal(war);
        }

        host.backgroundProcess();

        File xml = new File(host.getConfigBaseFile(),
                APP_NAME.getBaseName() + ".xml");
        Assert.assertEquals(
                Boolean.valueOf(resultXml), Boolean.valueOf(xml.isFile()));

        Context context = (Context) host.findChild(APP_NAME.getName());
        if (external) {
            Assert.assertEquals(XML_COOKIE_NAME,
                    context.getSessionCookieName());
        } else {
            Assert.assertEquals(WAR_COOKIE_NAME,
                    context.getSessionCookieName());
        }
    }


    protected void doTestUpdateWarOffline(File srcWar, boolean deployOnStartUp, boolean autoDeploy)
            throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StandardHost host = (StandardHost) tomcat.getHost();
        host.setDeployOnStartup(deployOnStartUp);

        File war = createWar(srcWar, true);
        // Make the WAR appear to have been created earlier
        Assert.assertTrue("Failed to set last modified for [" + war + "]", war.setLastModified(
                war.lastModified() - 2 * HostConfig.FILE_MODIFICATION_RESOLUTION_MS));

        tomcat.addWebapp(APP_NAME.getPath(), war.getAbsolutePath());
        tomcat.start();

        // Get the last modified timestamp for the expanded dir
        File dir = new File(host.getAppBase(), APP_NAME.getBaseName());
        // Make the DIR appear to have been created earlier
        long lastModified = war.lastModified() - 2 * HostConfig.FILE_MODIFICATION_RESOLUTION_MS;
        Assert.assertTrue("Failed to set last modified for [" + dir + "]",
                dir.setLastModified(lastModified));

        host.stop();
        Assert.assertTrue("Failed to set last modified for [" + war + "]",
                war.setLastModified(System.currentTimeMillis()));
        host.start();
        if (autoDeploy) {
            host.backgroundProcess();
        }

        long newLastModified = dir.lastModified();

        Assert.assertNotEquals("Timestamp hasn't changed", lastModified,  newLastModified);
    }


    protected File createDirInAppbase(boolean withXml) throws IOException {
        File dir = new File(getTomcatInstance().getHost().getAppBaseFile(),
                APP_NAME.getBaseName());
        if (withXml) {
            recursiveCopy(DIR_XML_SOURCE.toPath(), dir.toPath());
        } else {
            recursiveCopy(DIR_SOURCE.toPath(), dir.toPath());
        }
        return dir;
    }


    protected File createDirXmlInAppbase() throws IOException {
        File dir = new File(getTomcatInstance().getHost().getAppBaseFile(),
                APP_NAME.getBaseName() + "/META-INF");
        recursiveCopy(DIR_XML_SOURCE_META_INF.toPath(), dir.toPath());
        return dir;
    }


    protected File createDirInExternal(boolean withXml) throws IOException {
        File ext = new File(external, "external" + ".war");
        if (withXml) {
            recursiveCopy(DIR_XML_SOURCE.toPath(), ext.toPath());
        } else {
            recursiveCopy(DIR_SOURCE.toPath(), ext.toPath());
        }
        return ext;
    }


    protected File createWar(File src, boolean useAppbase) throws IOException {
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


    protected File createXmlInConfigBaseForAppbase() throws IOException {
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


    protected File getXmlInConfigBaseForAppbase() {
        Host host = getTomcatInstance().getHost();
        return new File(host.getConfigBaseFile(), APP_NAME + ".xml");
    }


    protected File createXmlInConfigBaseForExternal(File ext) throws IOException {
        return createXmlInConfigBaseForExternal(ext, false);
    }


    protected File createXmlInConfigBaseForExternal(File ext, boolean antiLocking)
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


    public static class TesterContext extends StandardContext {
        // No functional change
    }
}
