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
import java.nio.file.Files;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardHost;

/**
 * The purpose of this class is to test the automatic deployment features of the
 * {@link HostConfig} implementation.
 */
public class TestHostConfigAutomaticDeploymentC8 extends HostConfigAutomaticDeploymentBaseTest {

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
                true, false, false, XML_COOKIE_NAME, REDEPLOY,
                LifecycleState.FAILED);
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

    /*
     * Expected behaviour for the addition of files.
     *
     * Artifacts present   copyXML  deployXML  Artifact   Artifacts remaining
     * XML  WAR  EXT  DIR                       Added      XML  WAR  EXT DIR   Action
     *  N    Y    N    N      N        Y         DIR        -    Y    -   A     None
     *  N    N    N    Y      N        Y         WAR        -    A    -   R   Redeploy
     *  Y    N    N    N      N        Y         DIR        Y    -    -   A     None
     *  N    N    N    Y      N        Y         XML        A    -    -   Y   Redeploy
     *  Y    N    N    N      N        Y         WAR        Y    A    -   -    Reload
     *  N    Y    N    N      N        Y         XML        A    Y    -   -   Redeploy
     *  Y    Y    N    N      N        Y         DIR        Y    Y    -   A     None
     *  Y    N    N    Y      N        Y         WAR        Y    A    -   N    Reload
     *  N    Y    N    Y      N        Y         XML        A    Y    -   Y   Redeploy
     *  Y    N    Y    N      N        Y         DIR        Y    -    Y   A     None
     *  Y    N    Y    N      N        Y         WAR        Y    A    Y   -     None
     *  N    N    N    Y      N        Y         EXT        A    -    A   R   Redeploy
     *  N    Y    N    N      N        Y         EXT        A    Y    A   -   Redeploy
     *
     *  N    N    N    Y     Y/N       N       DIR+XML      -    -    -   Y   Redeploy (failed)
     *  N    N    N    Y      Y        Y       DIR+XML      A    -    -   Y   Redeploy
     *  N    N    N    Y      N        Y       DIR+XML      -    -    -   Y   Redeploy
     *
     * Addition of a file  is treated as if the added file has been modified
     * with the following additional actions:
     * - If a WAR is added, any DIR is removed and may be recreated depending on
     *   unpackWARs.
     * - If an XML file is added that refers to an external docBase any WAR or
     *   DIR in the appBase will be removed. The DIR may be recreated if the
     *   external resource is a WAR and unpackWARs is true.
     * - If a DIR is added when a WAR already exists and unpackWARs is false,
     *   the DIR will be ignored but a warning will be logged when the DIR is
     *   first detected. If the WAR is removed, the DIR will be left and may be
     *   deployed via automatic deployment.
     * - If a WAR is added when an external WAR already exists for the same
     *   context, the WAR will be treated the same way as a DIR is treated in
     *   the previous bullet point.
     */
    @Test
    public void testAdditionWarAddDir() throws Exception {
        doTestAddition(false, false, false, true, false, DIR,
                false, true, true, WAR_COOKIE_NAME, NONE);
    }

    @Test
    public void testAdditionDirAddWar() throws Exception {
        doTestAddition(false, false, false, false, true, WAR,
                false, true, true, WAR_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testAdditionXmlAddDir() throws Exception {
        doTestAddition(true, false, false, false, false, DIR,
                true, false, true, XML_COOKIE_NAME, NONE);
    }

    @Test
    public void testAdditionDirAddXml() throws Exception {
        doTestAddition(false, false, false, false, true, XML,
                true, false, true, XML_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testAdditionXmlAddWar() throws Exception {
        doTestAddition(true, false, false, false, false, WAR,
                true, true, false, XML_COOKIE_NAME, RELOAD);
    }

    @Test
    public void testAdditionWarAddXml() throws Exception {
        doTestAddition(false, false, false, true, false, XML,
                true, true, false, XML_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testAdditionXmlWarAddDir() throws Exception {
        doTestAddition(true, false, false, true, false, DIR,
                true, true, true, XML_COOKIE_NAME, NONE);
    }

    @Test
    public void testAdditionXmlDirAddWar() throws Exception {
        doTestAddition(true, false, false, false, true, WAR,
                true, true, false, XML_COOKIE_NAME, RELOAD);
    }

    @Test
    public void testAdditionWarDirAddXml() throws Exception {
        doTestAddition(false, false, false, true, true, XML,
                true, true, true, XML_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testAdditionXmlExtwarAddDir() throws Exception {
        doTestAddition(true, true, false, false, false, DIR,
                true, false, true, XML_COOKIE_NAME, NONE);
    }

    @Test
    public void testAdditionXmlExtdirAddDir() throws Exception {
        doTestAddition(true, false, true, false, false, DIR,
                true, false, true, XML_COOKIE_NAME, NONE);
    }

    @Test
    public void testAdditionXmlExtwarAddWar() throws Exception {
        doTestAddition(true, true, false, false, false, WAR,
                true, true, false, XML_COOKIE_NAME, NONE);
    }

    @Test
    public void testAdditionXmlExtdirAddWar() throws Exception {
        doTestAddition(true, false, true, false, false, WAR,
                true, true, false, XML_COOKIE_NAME, NONE);
    }

    @Test
    public void testAdditionDirAddXmlExtwar() throws Exception {
        doTestAddition(false, false, false, false, true, EXT,
                true, false, true, XML_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testAdditionWarAddXmlExtwar() throws Exception {
        doTestAddition(false, false, false, true, false, EXT,
                true, true, false, XML_COOKIE_NAME, REDEPLOY);
    }

    @Test
    public void testAdditionDirAddDirXmlTF() throws Exception {
        doTestAddition(false, false, false, false, true, true, false, DIR_XML,
                false, false, true, null, REDEPLOY, LifecycleState.FAILED);
    }

    @Test
    public void testAdditionDirAddDirXmlFF() throws Exception {
        doTestAddition(false, false, false, false, true, false, false, DIR_XML,
                false, false, true, null, REDEPLOY, LifecycleState.FAILED);
    }

    @Test
    public void testAdditionDirAddDirXmlTT() throws Exception {
        doTestAddition(false, false, false, false, true, true, true, DIR_XML,
                true, false, true, DIR_COOKIE_NAME, REDEPLOY,
                LifecycleState.STARTED);
    }

    @Test
    public void testAdditionDirAddDirXmlFT() throws Exception {
        doTestAddition(false, false, false, false, true, false, true, DIR_XML,
                false, false, true, DIR_COOKIE_NAME, REDEPLOY,
                LifecycleState.STARTED);
    }

    /*
     * Test context unpackWAR setting.
     * If context.getUnpackWAR != Host.getUnpackWARs the Host wins.
     */
    @Test
    public void testUnpackWARFFF() throws Exception  {
        doTestUnpackWAR(false, false, false, false);
    }

    @Test
    public void testUnpackWARFFT() throws Exception  {
        doTestUnpackWAR(false, false, true, false);
    }

    @Test
    public void testUnpackWARFTF() throws Exception  {
        doTestUnpackWAR(false, true, false, false);
    }

    @Test
    public void testUnpackWARFTT() throws Exception  {
        doTestUnpackWAR(false, true, true, false);
    }

    @Test
    public void testUnpackWARTFF() throws Exception  {
        doTestUnpackWAR(true, false, false, false);
    }

    @Test
    public void testUnpackWARTFT() throws Exception  {
        // External WAR - therefore XML in WAR will be ignored
        doTestUnpackWAR(true, false, true, true);
    }

    @Test
    public void testUnpackWARTTF() throws Exception  {
        doTestUnpackWAR(true, true, false, true);
    }

    @Test
    public void testUnpackWARTTT() throws Exception  {
        doTestUnpackWAR(true, true, true, true);
    }

    @Test
    public void testBrokenAppWithAntiLockingF() throws Exception {
        doTestBrokenAppWithAntiLocking(false);
    }

    @Test
    public void testBrokenAppWithAntiLockingT() throws Exception {
        doTestBrokenAppWithAntiLocking(true);
    }

    /*
     * Test context copyXML setting.
     * If context.copyXML != Host.copyXML the Host wins.
     * For external WARs, a context.xml must always already exist
     */
    @Test
    public void testCopyXMLFFF() throws Exception  {
        doTestCopyXML(false, false, false, false);
    }

    @Test
    public void testCopyXMLFFT() throws Exception  {
        doTestCopyXML(false, false, true, true);
    }

    @Test
    public void testCopyXMLFTF() throws Exception  {
        doTestCopyXML(false, true, false, true);
    }

    @Test
    public void testCopyXMLFTT() throws Exception  {
        doTestCopyXML(false, true, true, true);
    }

    @Test
    public void testCopyXMLTFF() throws Exception  {
        doTestCopyXML(true, false, false, true);
    }

    @Test
    public void testCopyXMLTFT() throws Exception  {
        doTestCopyXML(true, false, true, true);
    }

    @Test
    public void testCopyXMLTTF() throws Exception  {
        doTestCopyXML(true, true, false, true);
    }

    @Test
    public void testCopyXMLTTT() throws Exception  {
        doTestCopyXML(true, true, true, true);
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
        File war = new File(host.getAppBaseFile(),
                APP_NAME.getBaseName() + ".war");
        Files.copy(WAR_XML_SOURCE.toPath(), war.toPath());

        // Deploy the copied war
        tomcat.start();
        host.backgroundProcess();

        // Check the Context class
        Context ctxt = (Context) host.findChild(APP_NAME.getName());

        assertThat(ctxt, instanceOf(TesterContext.class));
    }


    @Test
    public void testUpdateWarOfflineNoContextFF() throws Exception {
        doTestUpdateWarOffline(WAR_SOURCE, false, false);
    }


    @Test
    public void testUpdateWarOfflineNoContextTF() throws Exception {
        doTestUpdateWarOffline(WAR_SOURCE, true, false);
    }


    @Test
    public void testUpdateWarOfflineNoContextFT() throws Exception {
        doTestUpdateWarOffline(WAR_SOURCE, false, true);
    }


    @Test
    public void testUpdateWarOfflineNoContextTT() throws Exception {
        doTestUpdateWarOffline(WAR_SOURCE, true, true);
    }


    @Test
    public void testUpdateWarOfflineContextFF() throws Exception {
        doTestUpdateWarOffline(WAR_XML_SOURCE, false, false);
    }


    @Test
    public void testUpdateWarOfflineContextTF() throws Exception {
        doTestUpdateWarOffline(WAR_XML_SOURCE, true, false);
    }


    @Test
    public void testUpdateWarOfflineContextFT() throws Exception {
        doTestUpdateWarOffline(WAR_XML_SOURCE, false, true);
    }


    @Test
    public void testUpdateWarOfflineContextTT() throws Exception {
        doTestUpdateWarOffline(WAR_XML_SOURCE, true, true);
    }
}
