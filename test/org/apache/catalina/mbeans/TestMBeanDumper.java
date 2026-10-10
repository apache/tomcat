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

import java.util.Collections;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.modeler.Registry;

public class TestMBeanDumper extends TomcatBaseTest {

    @Test
    public void testDumpResolvesDefaultModelerType() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.start();

        MBeanServer server = Registry.getRegistry(null).getMBeanServer();
        ObjectName oname = new ObjectName("Tomcat:type=MBeanFactory");
        Set<ObjectName> names = Collections.singleton(oname);
        Assert.assertTrue(server.isRegistered(oname));

        String dump = MBeanDumper.dumpBeans(server, names);

        Assert.assertTrue(dump.contains("modelerType: org.apache.catalina.mbeans.MBeanFactory"));
    }
}
