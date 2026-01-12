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

import org.junit.Test;

import org.apache.catalina.LifecycleState;

/**
 * The purpose of this class is to test the automatic deployment features of the
 * {@link HostConfig} implementation.
 */
public class TestHostConfigAutomaticDeploymentXmlExternalWarXml extends HostConfigAutomaticDeploymentBaseTest {

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
}
