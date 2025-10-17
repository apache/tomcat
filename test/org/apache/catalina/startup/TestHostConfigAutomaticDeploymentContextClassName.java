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
import java.nio.file.Files;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.core.StandardHost;

/**
 * The purpose of this class is to test the automatic deployment features of the
 * {@link HostConfig} implementation.
 */
public class TestHostConfigAutomaticDeploymentContextClassName extends HostConfigAutomaticDeploymentBaseTest {

    @Test
    public void testSetContextClassName() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        Host host = tomcat.getHost();
        if (host instanceof StandardHost) {
            StandardHost standardHost = (StandardHost) host;
            standardHost.setContextClass(TesterContext.class.getName());
        }

        // Copy the WAR file
        File war = new File(host.getAppBaseFile(),
                APP_NAME.getBaseName() + ".war");
        Files.copy(WAR_XML_SOURCE.toPath(), war.toPath());

        // Deploy the copied war
        tomcat.start();
        host.backgroundProcess();

        // Check the Context class
        Context ctxt = (Context) host.findChild(APP_NAME.getName());

        assertThat(ctxt, instanceOf(TesterContext.class));
    }
}
