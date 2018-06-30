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
package org.apache.naming.resources;

import java.io.File;

import javax.naming.NamingException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.LoggingBaseTest;

public class TestVirtualDirContext {

    @Test
    public void testBug62498() throws NamingException {
        VirtualDirContext vdc = new VirtualDirContext();
        // No docBase
        vdc.setExtraResourcePaths("/=" + LoggingBaseTest.getBuildDirectory().getAbsolutePath());

        vdc.allocate();

        File f1 = vdc.file("");
        Assert.assertNotNull(f1);
        File f2 = vdc.file("/");
        Assert.assertNotNull(f2);
        Assert.assertEquals(f1.getAbsolutePath(), f2.getAbsolutePath());

        Object obj1 = vdc.lookup("");
        Assert.assertTrue(obj1 instanceof FileDirContext);
        Object obj2 = vdc.lookup("/");
        Assert.assertTrue(obj2 instanceof FileDirContext);
        Assert.assertEquals(((FileDirContext) obj1).absoluteBase, ((FileDirContext) obj2).absoluteBase);
    }


    @Test
    public void testBug62498a() {
        VirtualDirContext vdc = new VirtualDirContext();
        // No docBase
        vdc.setExtraResourcePaths("/=" + LoggingBaseTest.getBuildDirectory().getAbsolutePath());

        vdc.allocate();

        File f1 = vdc.file("");
        Assert.assertNotNull(f1);
    }


    @Test
    public void testBug62498b() {
        VirtualDirContext vdc = new VirtualDirContext();
        // No docBase
        vdc.setExtraResourcePaths("/=" + LoggingBaseTest.getBuildDirectory().getAbsolutePath());

        vdc.allocate();

        File f2 = vdc.file("/");
        Assert.assertNotNull(f2);
    }


    @Test
    public void testBug62498c() throws NamingException {
        VirtualDirContext vdc = new VirtualDirContext();
        // No docBase
        vdc.setExtraResourcePaths("/=" + LoggingBaseTest.getBuildDirectory().getAbsolutePath());

        vdc.allocate();

        Object obj1 = vdc.lookup("");
        Assert.assertTrue(obj1 instanceof FileDirContext);
    }


    @Test
    public void testBug62498d() throws NamingException {
        VirtualDirContext vdc = new VirtualDirContext();
        // No docBase
        vdc.setExtraResourcePaths("/=" + LoggingBaseTest.getBuildDirectory().getAbsolutePath());

        vdc.allocate();

        Object obj2 = vdc.lookup("/");
        Assert.assertTrue(obj2 instanceof FileDirContext);
    }
}
