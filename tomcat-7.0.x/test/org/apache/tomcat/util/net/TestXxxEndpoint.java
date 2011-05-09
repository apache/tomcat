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

package org.apache.tomcat.util.net;

import java.io.File;
import java.net.ServerSocket;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

/**
 * Test case for the Endpoint implementations. The testing framework will ensure
 * that each implementation is tested.
 */
public class TestXxxEndpoint extends TomcatBaseTest {

    public void testStartStopBindOnInit() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File(getBuildDirectory(), "webapps/examples");
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        
        int port = getPort();

        tomcat.start();
        
        tomcat.getConnector().stop();
        // This should throw an Exception
        Exception e = null;
        ServerSocket s = null;
        try {
            s = new ServerSocket(port);
        } catch (Exception e1) {
            e = e1;
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (Exception e2) { /* Ignore */ }
            }
        }
        assertNotNull(e);
        tomcat.getConnector().start();
    }

    public void testStartStopBindOnStart() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Connector c = tomcat.getConnector();
        c.setProperty("bindOnInit", "false");
        
        File appDir = new File(getBuildDirectory(), "webapps/examples");
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());
        
        int port = getPort();

        tomcat.start();
        
        tomcat.getConnector().stop();
        // This should not throw an Exception
        Exception e = null;
        ServerSocket s = null;
        try {
            s = new ServerSocket(port);
        } catch (Exception e1) {
            e = e1;
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (Exception e2) { /* Ignore */ }
            }
        }
        assertNull(e);
        tomcat.getConnector().start();
    }
}
