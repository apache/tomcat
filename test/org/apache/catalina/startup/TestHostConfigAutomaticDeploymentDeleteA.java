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
public class TestHostConfigAutomaticDeploymentDeleteA extends HostConfigAutomaticDeploymentBaseTest {

    /*
     * Expected behaviour for the deletion of files.
     *
     * Artifacts present     Artifact     Artifacts remaining
     *  XML  WAR  EXT  DIR    Removed     XML  WAR  EXT DIR    Notes
     *   N    N    N    Y       DIR        -    -    -   N
     *   N    Y    N    N       WAR        -    N    -   -
     *   N    Y    N    Y       DIR        -    Y    -   R     1
     *   N    Y    N    Y       WAR        -    N    -   N
     *
     * Notes: 1. The DIR will be re-created since unpackWARs is true.
     */
    @Test
    public void testDeleteDirRemoveDir() throws Exception {
        doTestDelete(false, false, false, false, true, DIR, false, false, false,
                null);
    }

    @Test
    public void testDeleteWarRemoveWar() throws Exception {
        doTestDelete(false, false, false, true, false, WAR, false, false, false,
                null);
    }

    @Test
    public void testDeleteWarDirRemoveDir() throws Exception {
        doTestDelete(false, false, false, true, true, DIR, false, true, true,
                WAR_COOKIE_NAME);
    }

    @Test
    public void testDeleteWarDirRemoveWar() throws Exception {
        doTestDelete(false, false, false, true, true, WAR, false, false, false,
                null);
    }
}
