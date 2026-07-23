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

import java.util.HashSet;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.modeler.Registry;

/**
 * Tests for {@link MBeanDumper}.
 */
public class TestMBeanDumper {

    @Test
    public void testDumpBeansEmpty() {
        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();
        Set<ObjectName> emptySet = new HashSet<>();

        String result = MBeanDumper.dumpBeans(mbeanServer, emptySet);
        Assert.assertNotNull(result);
        Assert.assertEquals("", result);
    }


    @Test
    public void testDumpBeansWithExisting() throws Exception {
        MBeanServer mbeanServer = Registry.getRegistry(null).getMBeanServer();

        // Query for any existing MBeans in the JVM
        Set<ObjectName> names = mbeanServer.queryNames(
                new ObjectName("java.lang:type=Runtime"), null);

        if (!names.isEmpty()) {
            String result = MBeanDumper.dumpBeans(mbeanServer, names);
            Assert.assertNotNull(result);
            Assert.assertTrue("Should contain Name: prefix",
                    result.contains("Name: "));
        }
    }


    @Test
    public void testEscapeNoNewline() {
        String input = "simple string without newlines";
        Assert.assertEquals(input, MBeanDumper.escape(input));
    }


    @Test
    public void testEscapeWithNewline() {
        String input = "line1\nline2";
        String result = MBeanDumper.escape(input);

        Assert.assertNotNull(result);
        Assert.assertTrue("Should contain escaped newline",
                result.contains("\\n"));
    }


    @Test
    public void testEscapeWithMultipleNewlines() {
        String input = "line1\nline2\nline3";
        String result = MBeanDumper.escape(input);

        Assert.assertNotNull(result);
    }


    @Test
    public void testEscapeWithTrailingNewline() {
        String input = "content\n";
        String result = MBeanDumper.escape(input);

        Assert.assertNotNull(result);
        Assert.assertTrue("Should contain escaped newline",
                result.contains("\\n"));
    }


    @Test
    public void testEscapeLongLine() {
        // Create a string longer than 78 chars to test line wrapping
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append('a');
        }
        sb.append('\n');
        sb.append("second line");

        String result = MBeanDumper.escape(sb.toString());
        Assert.assertNotNull(result);
    }


    @Test
    public void testEscapeEmpty() {
        Assert.assertEquals("", MBeanDumper.escape(""));
    }
}
