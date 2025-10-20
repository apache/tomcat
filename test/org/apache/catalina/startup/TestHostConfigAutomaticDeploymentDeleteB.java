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

import org.apache.catalina.core.StandardHost;

/**
 * The purpose of this class is to test the automatic deployment features of the
 * {@link HostConfig} implementation.
 */
public class TestHostConfigAutomaticDeploymentDeleteB extends HostConfigAutomaticDeploymentBaseTest {

    /*
     * Expected behaviour for the deletion of files.
     *
     * Artifacts present     Artifact     Artifacts remaining
     *  XML  WAR  EXT  DIR    Removed     XML  WAR  EXT DIR    Notes
     *   Y    N    N    N       XML        N    -    -   -
     *   Y    N    N    Y       DIR        N    -    -   N
     *   Y    N    N    Y       XML        R    -    -   Y     2
     *   Y    N    Y    N       EXT        Y    -    N   -
     *   Y    N    Y    N       XML        N    -    Y   -
     *   Y    N    Y    Y       DIR        R    -    Y   R     1,2
     *   Y    N    Y    Y       EXT        Y    -    N   N
     *   Y    N    Y    Y       XML        N    -    Y   N
     *
     * Notes: 1. The DIR will be re-created since unpackWARs is true.
     *        2. The XML will be extracted from the WAR/DIR if deployXML and
     *           copyXML are true.
     */
    @Test
    public void testDeleteXmlRemoveXml() throws Exception {
        doTestDelete(true, false, false, false, false, XML, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlDirRemoveDir() throws Exception {
        doTestDelete(true, false, false, false, true, DIR, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlDirRemoveXml() throws Exception {
        doTestDelete(true, false, false, false, true, XML, false, false, true,
                DIR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlDirRemoveXmlCopyXml() throws Exception {
        ((StandardHost) getTomcatInstance().getHost()).setCopyXML(true);
        doTestDelete(true, false, false, false, true, XML, true, false, true,
                DIR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlExtwarRemoveExt() throws Exception {
        doTestDelete(true, true, false, false, false, EXT, true, false, false,
                XML_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlExtdirRemoveExt() throws Exception {
        doTestDelete(true, false, true, false, false, EXT, true, false, false,
                XML_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlExtwarRemoveXml() throws Exception {
        doTestDelete(true, true, false, false, false, XML, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlExtdirRemoveXml() throws Exception {
        doTestDelete(true, false, true, false, false, XML, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlExtwarDirRemoveDir() throws Exception {
        doTestDelete(true, true, false, false, true, DIR, true, false, true,
                XML_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlExtwarDirRemoveExt() throws Exception {
        doTestDelete(true, true, false, false, true, EXT, true, false, false,
                XML_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlExtwarDirRemoveXml() throws Exception {
        doTestDelete(true, true, false, false, true, XML, false, false, false,
                null);
    }
}
