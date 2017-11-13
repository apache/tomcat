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
package org.apache.catalina.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class TestContextName {

    private ContextName cn1;
    private ContextName cn2;
    private ContextName cn3;
    private ContextName cn4;
    private ContextName cn5;
    private ContextName cn6;
    private ContextName cn7;
    private ContextName cn8;
    private ContextName cn9;
    private ContextName cn10;
    private ContextName cn11;
    private ContextName cn12;
    private ContextName cn13;
    private ContextName cn14;
    private ContextName cn15;
    private ContextName cn16;
    private ContextName cn17;
    private ContextName cn18;
    private ContextName cn19;
    private ContextName cn20;
    private ContextName cn21;
    private ContextName cn22;

    @Before
    public void setUp() throws Exception {
        cn1 = new ContextName(null, null);
        cn2 = new ContextName("", null);
        cn3 = new ContextName("/", null);
        cn4 = new ContextName("/foo", null);
        cn5 = new ContextName("/foo/bar", null);
        cn6 = new ContextName(null, "A");
        cn7 = new ContextName("", "B");
        cn8 = new ContextName("/", "C");
        cn9 = new ContextName("/foo", "D");
        cn10 = new ContextName("/foo/bar", "E");
        cn11 = new ContextName("ROOT", false);
        cn12 = new ContextName("foo", false);
        cn13 = new ContextName("foo#bar", false);
        cn14 = new ContextName("ROOT##A", false);
        cn15 = new ContextName("foo##D", false);
        cn16 = new ContextName("foo#bar##E", false);
        cn17 = new ContextName("/ROOT", null);
        cn18 = new ContextName("/ROOT#bar", false);
        cn19 = new ContextName("/ROOT#bar##A", false);
        cn20 = new ContextName("/ROOT##A", false);
        cn21 = new ContextName("foo.war", false);
        cn22 = new ContextName("foo.war", true);
    }

    @Test
    public void testGetBaseName() {
        Assert.assertEquals("ROOT", cn1.getBaseName());
        Assert.assertEquals("ROOT", cn2.getBaseName());
        Assert.assertEquals("ROOT", cn3.getBaseName());
        Assert.assertEquals("foo", cn4.getBaseName());
        Assert.assertEquals("foo#bar", cn5.getBaseName());
        Assert.assertEquals("ROOT##A", cn6.getBaseName());
        Assert.assertEquals("ROOT##B", cn7.getBaseName());
        Assert.assertEquals("ROOT##C", cn8.getBaseName());
        Assert.assertEquals("foo##D", cn9.getBaseName());
        Assert.assertEquals("foo#bar##E", cn10.getBaseName());
        Assert.assertEquals("ROOT", cn11.getBaseName());
        Assert.assertEquals("foo", cn12.getBaseName());
        Assert.assertEquals("foo#bar", cn13.getBaseName());
        Assert.assertEquals("ROOT##A", cn14.getBaseName());
        Assert.assertEquals("foo##D", cn15.getBaseName());
        Assert.assertEquals("foo#bar##E", cn16.getBaseName());
        Assert.assertEquals("ROOT", cn17.getBaseName());
        Assert.assertEquals("ROOT#bar", cn18.getBaseName());
        Assert.assertEquals("ROOT#bar##A", cn19.getBaseName());
        Assert.assertEquals("ROOT##A", cn20.getBaseName());
        Assert.assertEquals("foo.war", cn21.getBaseName());
        Assert.assertEquals("foo", cn22.getBaseName());
    }

    @Test
    public void testGetPath() {
        Assert.assertEquals("", cn1.getPath());
        Assert.assertEquals("", cn2.getPath());
        Assert.assertEquals("", cn3.getPath());
        Assert.assertEquals("/foo", cn4.getPath());
        Assert.assertEquals("/foo/bar", cn5.getPath());
        Assert.assertEquals("", cn6.getPath());
        Assert.assertEquals("", cn7.getPath());
        Assert.assertEquals("", cn8.getPath());
        Assert.assertEquals("/foo", cn9.getPath());
        Assert.assertEquals("/foo/bar", cn10.getPath());
        Assert.assertEquals("", cn11.getPath());
        Assert.assertEquals("/foo", cn12.getPath());
        Assert.assertEquals("/foo/bar", cn13.getPath());
        Assert.assertEquals("", cn14.getPath());
        Assert.assertEquals("/foo", cn15.getPath());
        Assert.assertEquals("/foo/bar", cn16.getPath());
        Assert.assertEquals("", cn17.getPath());
        Assert.assertEquals("/ROOT/bar", cn18.getPath());
        Assert.assertEquals("/ROOT/bar", cn19.getPath());
        Assert.assertEquals("", cn20.getPath());
        Assert.assertEquals("/foo.war", cn21.getPath());
        Assert.assertEquals("/foo", cn22.getPath());
    }

    @Test
    public void testGetVersion() {
        Assert.assertEquals("", cn1.getVersion());
        Assert.assertEquals("", cn2.getVersion());
        Assert.assertEquals("", cn3.getVersion());
        Assert.assertEquals("", cn4.getVersion());
        Assert.assertEquals("", cn5.getVersion());
        Assert.assertEquals("A", cn6.getVersion());
        Assert.assertEquals("B", cn7.getVersion());
        Assert.assertEquals("C", cn8.getVersion());
        Assert.assertEquals("D", cn9.getVersion());
        Assert.assertEquals("E", cn10.getVersion());
        Assert.assertEquals("", cn11.getVersion());
        Assert.assertEquals("", cn12.getVersion());
        Assert.assertEquals("", cn13.getVersion());
        Assert.assertEquals("A", cn14.getVersion());
        Assert.assertEquals("D", cn15.getVersion());
        Assert.assertEquals("E", cn16.getVersion());
        Assert.assertEquals("", cn17.getVersion());
        Assert.assertEquals("", cn18.getVersion());
        Assert.assertEquals("A", cn19.getVersion());
        Assert.assertEquals("A", cn20.getVersion());
        Assert.assertEquals("", cn21.getVersion());
        Assert.assertEquals("", cn22.getVersion());
    }

    @Test
    public void testGetName() {
        Assert.assertEquals("", cn1.getName());
        Assert.assertEquals("", cn2.getName());
        Assert.assertEquals("", cn3.getName());
        Assert.assertEquals("/foo", cn4.getName());
        Assert.assertEquals("/foo/bar", cn5.getName());
        Assert.assertEquals("##A", cn6.getName());
        Assert.assertEquals("##B", cn7.getName());
        Assert.assertEquals("##C", cn8.getName());
        Assert.assertEquals("/foo##D", cn9.getName());
        Assert.assertEquals("/foo/bar##E", cn10.getName());
        Assert.assertEquals("", cn11.getName());
        Assert.assertEquals("/foo", cn12.getName());
        Assert.assertEquals("/foo/bar", cn13.getName());
        Assert.assertEquals("##A", cn14.getName());
        Assert.assertEquals("/foo##D", cn15.getName());
        Assert.assertEquals("/foo/bar##E", cn16.getName());
        Assert.assertEquals("", cn17.getName());
        Assert.assertEquals("/ROOT/bar", cn18.getName());
        Assert.assertEquals("/ROOT/bar##A", cn19.getName());
        Assert.assertEquals("##A", cn20.getName());
        Assert.assertEquals("/foo.war", cn21.getName());
        Assert.assertEquals("/foo", cn22.getName());
    }

    @Test
    public void testGetDisplayName() {
        Assert.assertEquals("/", cn1.getDisplayName());
        Assert.assertEquals("/", cn2.getDisplayName());
        Assert.assertEquals("/", cn3.getDisplayName());
        Assert.assertEquals("/foo", cn4.getDisplayName());
        Assert.assertEquals("/foo/bar", cn5.getDisplayName());
        Assert.assertEquals("/##A", cn6.getDisplayName());
        Assert.assertEquals("/##B", cn7.getDisplayName());
        Assert.assertEquals("/##C", cn8.getDisplayName());
        Assert.assertEquals("/foo##D", cn9.getDisplayName());
        Assert.assertEquals("/foo/bar##E", cn10.getDisplayName());
        Assert.assertEquals("/", cn11.getDisplayName());
        Assert.assertEquals("/foo", cn12.getDisplayName());
        Assert.assertEquals("/foo/bar", cn13.getDisplayName());
        Assert.assertEquals("/##A", cn14.getDisplayName());
        Assert.assertEquals("/foo##D", cn15.getDisplayName());
        Assert.assertEquals("/foo/bar##E", cn16.getDisplayName());
        Assert.assertEquals("/", cn17.getDisplayName());
        Assert.assertEquals("/ROOT/bar", cn18.getDisplayName());
        Assert.assertEquals("/ROOT/bar##A", cn19.getDisplayName());
        Assert.assertEquals("/##A", cn20.getDisplayName());
        Assert.assertEquals("/foo.war", cn21.getDisplayName());
        Assert.assertEquals("/foo", cn22.getDisplayName());
    }

    @Test
    public void testConstructorString() {
        doTestConstructorString(cn1);
        doTestConstructorString(cn2);
        doTestConstructorString(cn3);
        doTestConstructorString(cn4);
        doTestConstructorString(cn5);
        doTestConstructorString(cn6);
        doTestConstructorString(cn7);
        doTestConstructorString(cn8);
        doTestConstructorString(cn9);
        doTestConstructorString(cn10);
        doTestConstructorString(cn11);
        doTestConstructorString(cn12);
        doTestConstructorString(cn13);
        doTestConstructorString(cn14);
        doTestConstructorString(cn15);
        doTestConstructorString(cn16);
        doTestConstructorString(cn17);
        doTestConstructorString(cn18);
        doTestConstructorString(cn19);
        doTestConstructorString(cn20);
        doTestConstructorString(cn21);
        doTestConstructorString(cn22);
    }

    private void doTestConstructorString(ContextName src) {
        doCompare(src, new ContextName(src.getBaseName(), false));
        doCompare(src, new ContextName(src.getDisplayName(), false));
        doCompare(src, new ContextName(src.getName(), false));
    }

    private void doCompare(ContextName cn1, ContextName cn2) {
        Assert.assertEquals(cn1.getBaseName(), cn2.getBaseName());
        Assert.assertEquals(cn1.getDisplayName(), cn2.getDisplayName());
        Assert.assertEquals(cn1.getName(), cn2.getName());
        Assert.assertEquals(cn1.getPath(), cn2.getPath());
        Assert.assertEquals(cn1.getVersion(), cn2.getVersion());
    }
}
