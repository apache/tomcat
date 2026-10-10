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
package org.apache.catalina.mbeans;

import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.modeler.Registry;

public class TestConnectorMBean extends TomcatBaseTest {

    @Test
    public void testAttributeFailuresAreReported() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.start();

        MBeanServer server = Registry.getRegistry(null).getMBeanServer();

        Set<ObjectInstance> connectors = server.queryMBeans(new ObjectName("*:type=Connector,*"), null);
        Assert.assertEquals(1, connectors.size());
        ObjectName oname = connectors.iterator().next().getObjectName();

        // A value that cannot be converted. This used to be reported as a
        // successful update that did nothing.
        Object port = server.getAttribute(oname, "port");

        try {
            server.setAttribute(oname, new Attribute("port", "not-a-number"));
            Assert.fail("Setting an invalid port value must fail");
        } catch (MBeanException e) {
            Assert.assertTrue(String.valueOf(e.getCause()), e.getCause() instanceof IllegalArgumentException);
        }

        // The failed update must not have changed the value
        Assert.assertEquals(port, server.getAttribute(oname, "port"));

        // An unknown attribute. Reading used to return null and writing used
        // to do nothing, both without an error.
        try {
            server.getAttribute(oname, "notAnAttribute");
            Assert.fail("Reading an unknown attribute must fail");
        } catch (AttributeNotFoundException e) {
            // Expected
        }

        try {
            server.setAttribute(oname, new Attribute("notAnAttribute", "x"));
            Assert.fail("Writing an unknown attribute must fail");
        } catch (MBeanException e) {
            // Expected
        }

        // A protocol handler property that is simply unset reads as null
        Assert.assertNull(server.getAttribute(oname, "relaxedPathChars"));

        // A valid property can still be written and read
        server.setAttribute(oname, new Attribute("useBodyEncodingForURI", "true"));
        Assert.assertEquals(Boolean.TRUE, server.getAttribute(oname, "useBodyEncodingForURI"));
    }
}
