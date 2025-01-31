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
public class TestHostConfigAutomaticDeploymentDirXml extends HostConfigAutomaticDeploymentBaseTest {

    /*
     * Expected behaviour for deployment of a DIR with an embedded XML file.
     * deployXML  copyXML  unpackWARs      XML  WAR  DIR
     *     N        Y/N       Y/N           N    N    Y
     *     Y         N        Y/N           N    N    Y
     *     Y         Y        Y/N           Y    N    Y
     */
    @Test
    public void testDeploymentDirXmlFFF() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(false, false, false,
                LifecycleState.FAILED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlFFT() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(false, false, true,
                LifecycleState.FAILED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlFTF() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(false, true, false,
                LifecycleState.FAILED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlFTT() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(false, true, true,
                LifecycleState.FAILED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlTFF() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, DIR_COOKIE_NAME, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlTFT() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, DIR_COOKIE_NAME, false, false, true);
    }

    @Test
    public void testDeploymentDirXmlTTF() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, DIR_COOKIE_NAME, true, false, true);
    }

    @Test
    public void testDeploymentDirXmlTTT() throws Exception {
        createDirInAppbase(true);
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, DIR_COOKIE_NAME, true, false, true);
    }
}
