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
public class TestHostConfigAutomaticDeploymentCopyXML extends HostConfigAutomaticDeploymentBaseTest {

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
}
