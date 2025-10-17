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

/**
 * The purpose of this class is to test the automatic deployment features of the
 * {@link HostConfig} implementation.
 */
public class TestHostConfigAutomaticDeploymentUpdateWarOffline extends HostConfigAutomaticDeploymentBaseTest {

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
