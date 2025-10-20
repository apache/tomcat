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
public class TestHostConfigAutomaticDeploymentWarXml extends HostConfigAutomaticDeploymentBaseTest {

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
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(false, false, false,
                LifecycleState.FAILED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarXmlFFT() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(false, false, true,
                LifecycleState.FAILED, null, false, true, true);
    }

    @Test
    public void testDeploymentWarXmlFTF() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(false, true, false,
                LifecycleState.FAILED, null, false, true, false);
    }

    @Test
    public void testDeploymentWarXmlFTT() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(false, true, true,
                LifecycleState.FAILED, null, false, true, true);
    }

    @Test
    public void testDeploymentWarXmlTFF() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, WAR_COOKIE_NAME, false, true, false);
    }

    @Test
    public void testDeploymentWarXmlTFT() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, WAR_COOKIE_NAME, false, true, true);
    }

    @Test
    public void testDeploymentWarXmlTTF() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, WAR_COOKIE_NAME, true, true, false);
    }

    @Test
    public void testDeploymentWarXmlTTT() throws Exception {
        createWar(WAR_XML_SOURCE, true);
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, WAR_COOKIE_NAME, true, true, true);
    }
}
