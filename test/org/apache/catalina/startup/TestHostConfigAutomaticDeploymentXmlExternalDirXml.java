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
public class TestHostConfigAutomaticDeploymentXmlExternalDirXml extends HostConfigAutomaticDeploymentBaseTest {

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
}
