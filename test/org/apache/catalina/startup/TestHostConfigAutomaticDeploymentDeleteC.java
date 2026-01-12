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
public class TestHostConfigAutomaticDeploymentDeleteC extends HostConfigAutomaticDeploymentBaseTest {

    /*
     * Expected behaviour for the deletion of files.
     *
     * Artifacts present     Artifact     Artifacts remaining
     *  XML  WAR  EXT  DIR    Removed     XML  WAR  EXT DIR    Notes
     *   Y    Y    N    N       WAR        N    N    -   -
     *   Y    Y    N    N       XML        N    N    -   -
     *   Y    Y    N    Y       DIR        R    Y    -   R     1,2
     *   Y    Y    N    Y       WAR        N    N    -   -
     *   Y    Y    N    Y       XML        R    Y    -   Y
     *
     * Notes: 1. The DIR will be re-created since unpackWARs is true.
     *        2. The XML will be extracted from the WAR/DIR if deployXML and
     *           copyXML are true.
     */
    @Test
    public void testDeleteXmlWarRemoveWar() throws Exception {
        doTestDelete(true, false, false, true, false, WAR, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlWarRemoveXml() throws Exception {
        doTestDelete(true, false, false, true, false, XML, false, true, false,
                WAR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlWarRemoveXmlCopyXml() throws Exception {
        ((StandardHost) getTomcatInstance().getHost()).setCopyXML(true);
        doTestDelete(true, false, false, true, false, XML, true, true, false,
                WAR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlWarDirRemoveDir() throws Exception {
        doTestDelete(true, false, false, true, true, DIR, false, true, true,
                WAR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlWarDirRemoveDirCopyXml() throws Exception {
        ((StandardHost) getTomcatInstance().getHost()).setCopyXML(true);
        doTestDelete(true, false, false, true, true, DIR, true, true, true,
                WAR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlWarDirRemoveWar() throws Exception {
        doTestDelete(true, false, false, true, true, WAR, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlWarDirRemoveWarCopyXml() throws Exception {
        ((StandardHost) getTomcatInstance().getHost()).setCopyXML(true);
        doTestDelete(true, false, false, true, true, WAR, false, false, false,
                null);
    }

    @Test
    public void testDeleteXmlWarDirRemoveXml() throws Exception {
        doTestDelete(true, false, false, true, true, XML, false, true, true,
                DIR_COOKIE_NAME);
    }

    @Test
    public void testDeleteXmlWarDirRemoveXmlCopyXml() throws Exception {
        ((StandardHost) getTomcatInstance().getHost()).setCopyXML(true);
        doTestDelete(true, false, false, true, true, XML, true, true, true,
                WAR_COOKIE_NAME);
    }
}
