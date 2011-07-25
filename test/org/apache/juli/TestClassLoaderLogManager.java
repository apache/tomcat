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

package org.apache.juli;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test cases for {@link ClassLoaderLogManager}.
 */
public class TestClassLoaderLogManager {

    @Test
    public void testReplace() {
        ClassLoaderLogManager logManager = new ClassLoaderLogManager();
        assertEquals("", logManager.replace(""));
        assertEquals("${", logManager.replace("${"));
        assertEquals("${undefinedproperty}",
                logManager.replace("${undefinedproperty}"));
        assertEquals(
                System.getProperty("line.separator")
                        + System.getProperty("path.separator")
                        + System.getProperty("file.separator"),
                logManager
                        .replace("${line.separator}${path.separator}${file.separator}"));
        assertEquals(
                "foo" + System.getProperty("file.separator") + "bar"
                        + System.getProperty("line.separator")
                        + System.getProperty("path.separator") + "baz",
                logManager
                        .replace("foo${file.separator}bar${line.separator}${path.separator}baz"));
        // BZ 51249
        assertEquals(
                "%{file.separator}" + System.getProperty("file.separator"),
                logManager.replace("%{file.separator}${file.separator}"));
        assertEquals(
                System.getProperty("file.separator") + "${undefinedproperty}"
                        + System.getProperty("file.separator"),
                logManager
                        .replace("${file.separator}${undefinedproperty}${file.separator}"));
        assertEquals("${}" + System.getProperty("path.separator"),
                logManager.replace("${}${path.separator}"));
    }

}
