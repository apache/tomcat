/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.core;

import java.net.InetAddress;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestStandardService extends TomcatBaseTest {

    @Test(expected = IllegalArgumentException.class)
    public void testAddInvalidConnectorThrow() throws Exception {
        doTestAddInvalidConnector(true);
    }


    @Test
    public void testAddInvalidConnectorNoThrow() throws Exception {
        doTestAddInvalidConnector(false);
    }


    private void doTestAddInvalidConnector(boolean throwOnFailure) throws Exception {

        Tomcat tomcat = getTomcatInstance();

        Connector connector = tomcat.getConnector();

        tomcat.start();

        Connector c2 = new Connector("HTTP/1.1");
        c2.setThrowOnFailure(throwOnFailure);

        Assert.assertTrue(c2.setProperty("address", ((InetAddress) connector.getProperty("address")).getHostAddress()));
        c2.setPort(connector.getLocalPort());

        tomcat.getService().addConnector(c2);
    }
}
