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
public class TestHostConfigAutomaticDeploymentModification extends HostConfigAutomaticDeploymentBaseTest {

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
}
