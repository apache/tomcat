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

import org.junit.Test;

import org.apache.catalina.LifecycleState;

/**
 * The purpose of this class is to test the automatic deployment features of the
 * {@link HostConfig} implementation.
 */
public class TestHostConfigAutomaticDeploymentAddition extends HostConfigAutomaticDeploymentBaseTest {

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
}
