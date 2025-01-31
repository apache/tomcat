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
public class TestHostConfigAutomaticDeploymentDir extends HostConfigAutomaticDeploymentBaseTest {

    /*
     * Expected behaviour for deployment of a DIR without an embedded XML file.
     * deployXML  copyXML  unpackWARs      XML  WAR  DIR
     *    Y/N       Y/N       Y/N           N    N    Y
     */
    @Test
    public void testDeploymentDirFFF() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(false, false, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirFFT() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(false, false, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirFTF() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(false, true, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirFTT() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(false, true, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirTFF() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(true, false, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirTFT() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(true, false, true,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirTTF() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(true, true, false,
                LifecycleState.STARTED, null, false, false, true);
    }

    @Test
    public void testDeploymentDirTTT() throws Exception {
        createDirInAppbase(false);
        doTestDeployment(true, true, true,
                LifecycleState.STARTED, null, false, false, true);
    }
}
