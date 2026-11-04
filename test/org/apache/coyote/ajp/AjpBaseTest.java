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
package org.apache.coyote.ajp;

import org.junit.Before;

import org.apache.catalina.startup.TomcatBaseTest;

/**
 * Base class for tests that need an AJP connector rather than the default HTTP connector.
 */
public abstract class AjpBaseTest extends TomcatBaseTest {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        getTomcatInstance().getConnector().setProperty("secretRequired", "false");
    }


    @Override
    protected String getProtocol() {
        /*
         * The tests are all setup for HTTP so need to convert the protocol values to AJP.
         */
        // Has a protocol been specified
        String protocol = System.getProperty("tomcat.test.protocol");

        // Use NIO by default
        if (protocol == null) {
            protocol = "org.apache.coyote.ajp.AjpNioProtocol";
        } else if (protocol.contains("Nio2")) {
            protocol = "org.apache.coyote.ajp.AjpNio2Protocol";
        } else {
            protocol = "org.apache.coyote.ajp.AjpNioProtocol";
        }

        return protocol;
    }
}
