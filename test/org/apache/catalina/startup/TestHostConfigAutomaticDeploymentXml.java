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
public class TestHostConfigAutomaticDeploymentXml extends HostConfigAutomaticDeploymentBaseTest {

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
}
