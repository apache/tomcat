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
package org.apache.catalina.ant.jmx;

import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

public class TestJMXAccessorTask {

    @Test
    public void testCreatePropertyForTabularDataSupport() throws Exception {
        JMXAccessorTask task = new JMXAccessorTask();

        task.createProperty("tabular", createTabularData());

        Assert.assertEquals("0", task.getProperty("tabular.0.id"));
        Assert.assertEquals("alpha", task.getProperty("tabular.0.details.name"));
        Assert.assertEquals("7", task.getProperty("tabular.0.count"));
        Assert.assertEquals("1", task.getProperty("tabular.1.id"));
        Assert.assertEquals("beta", task.getProperty("tabular.1.details.name"));
        Assert.assertEquals("11", task.getProperty("tabular.1.count"));
    }


    @Test
    public void testConvertStringToType() {
        JMXAccessorTask task = new JMXAccessorTask();

        Assert.assertEquals("x", task.convertStringToType("x", "java.lang.String"));
        Assert.assertEquals(Integer.valueOf(7), task.convertStringToType("7", "int"));
        Assert.assertEquals(Long.valueOf(7), task.convertStringToType("7", "java.lang.Long"));
        Assert.assertEquals(Boolean.TRUE, task.convertStringToType("true", "boolean"));
        Assert.assertEquals(Double.valueOf(1.5), task.convertStringToType("1.5", "double"));
    }


    @Test
    public void testConvertStringToTypeInvalid() {
        JMXAccessorTask task = new JMXAccessorTask();

        assertConvertFails(task, "x", "int");
        assertConvertFails(task, "x", "java.lang.Long");
        assertConvertFails(task, "1.5", "java.lang.Integer");
        assertConvertFails(task, "x", "javax.management.ObjectName");
    }


    @Test
    public void testConvertStringToTypeUnsupportedType() {
        JMXAccessorTask task = new JMXAccessorTask();

        assertConvertFails(task, "x", "java.lang.Int");
        assertConvertFails(task, "x", "java.util.List");
        assertConvertFails(task, "x", null);
    }


    private static void assertConvertFails(JMXAccessorTask task, String value, String type) {
        try {
            task.convertStringToType(value, type);
            Assert.fail("Expected a BuildException for value '" + value + "' and type '" + type + "'");
        } catch (BuildException expected) {
            // Expected
        }
    }


    /*
     * A cached connection reference is reused. If the caller explicitly specifies a different target than the one the
     * reference was opened to, a BuildException is raised instead of the explicit parameters being silently ignored.
     */
    @Test
    public void testAccessJMXConnectionReuseAndMismatch() throws Exception {
        int port = getAvailablePort();
        Registry registry = LocateRegistry.createRegistry(port);
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        JMXServiceURL serviceUrl = new JMXServiceURL(
                JMXAccessorTask.JMX_SERVICE_PREFIX + "127.0.0.1:" + port + JMXAccessorTask.JMX_SERVICE_SUFFIX);
        JMXConnectorServer connectorServer =
                JMXConnectorServerFactory.newJMXConnectorServer(serviceUrl, null, mbeanServer);
        connectorServer.start();
        try {
            Project project = new Project();
            String ref = "jmx.server.test";
            String host = "127.0.0.1";
            String openPort = Integer.toString(port);

            // First call opens the connection and stores it under the reference.
            MBeanServerConnection first =
                    JMXAccessorTask.accessJMXConnection(project, null, host, openPort, null, null, ref);
            Assert.assertNotNull(first);

            // Reuse with no explicit target: the cached connection is returned.
            MBeanServerConnection reused =
                    JMXAccessorTask.accessJMXConnection(project, null, null, null, null, null, ref);
            Assert.assertSame(first, reused);

            // Explicitly specifying a different port than the open connection must fail rather than be ignored.
            try {
                JMXAccessorTask.accessJMXConnection(project, null, host, "1", null, null, ref);
                Assert.fail("Expected a BuildException for an explicit conflicting target");
            } catch (BuildException expected) {
                // Expected
            }
        } finally {
            connectorServer.stop();
            UnicastRemoteObject.unexportObject(registry, true);
        }
    }


    private static int getAvailablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }


    private static TabularDataSupport createTabularData() throws OpenDataException {
        CompositeType detailsType = new CompositeType("details", "details", new String[] { "name" },
                new String[] { "name" }, new OpenType<?>[] { SimpleType.STRING });
        CompositeType rowType = new CompositeType("row", "row", new String[] { "id", "details", "count" },
                new String[] { "id", "details", "count" },
                new OpenType<?>[] { SimpleType.STRING, detailsType, SimpleType.INTEGER });
        TabularDataSupport tabularData = new TabularDataSupport(new TabularType("table", "table", rowType,
                new String[] { "id" }));

        tabularData.put(createRow(rowType, detailsType, "0", "alpha", Integer.valueOf(7)));
        tabularData.put(createRow(rowType, detailsType, "1", "beta", Integer.valueOf(11)));

        return tabularData;
    }


    private static CompositeDataSupport createRow(CompositeType rowType, CompositeType detailsType, String id,
            String detailName, Integer count) throws OpenDataException {
        CompositeDataSupport details = new CompositeDataSupport(detailsType, new String[] { "name" },
                new Object[] { detailName });

        return new CompositeDataSupport(rowType, new String[] { "id", "details", "count" },
                new Object[] { id, details, count });
    }
}
