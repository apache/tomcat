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

import static org.junit.Assert.assertEquals;

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
        cn11 = new ContextName("ROOT");
        cn12 = new ContextName("foo");
        cn13 = new ContextName("foo#bar");
        cn14 = new ContextName("ROOT##A");
        cn15 = new ContextName("foo##D");
        cn16 = new ContextName("foo#bar##E");
    }

    @Test
    public void testGetBaseName() {
        assertEquals("ROOT", cn1.getBaseName());
        assertEquals("ROOT", cn2.getBaseName());
        assertEquals("ROOT", cn3.getBaseName());
        assertEquals("foo", cn4.getBaseName());
        assertEquals("foo#bar", cn5.getBaseName());
        assertEquals("ROOT##A", cn6.getBaseName());
        assertEquals("ROOT##B", cn7.getBaseName());
        assertEquals("ROOT##C", cn8.getBaseName());
        assertEquals("foo##D", cn9.getBaseName());
        assertEquals("foo#bar##E", cn10.getBaseName());
        assertEquals("ROOT", cn11.getBaseName());
        assertEquals("foo", cn12.getBaseName());
        assertEquals("foo#bar", cn13.getBaseName());
        assertEquals("ROOT##A", cn14.getBaseName());
        assertEquals("foo##D", cn15.getBaseName());
        assertEquals("foo#bar##E", cn16.getBaseName());
    }

    @Test
    public void testGetPath() {
        assertEquals("", cn1.getPath());
        assertEquals("", cn2.getPath());
        assertEquals("", cn3.getPath());
        assertEquals("/foo", cn4.getPath());
        assertEquals("/foo/bar", cn5.getPath());
        assertEquals("", cn6.getPath());
        assertEquals("", cn7.getPath());
        assertEquals("", cn8.getPath());
        assertEquals("/foo", cn9.getPath());
        assertEquals("/foo/bar", cn10.getPath());
        assertEquals("", cn11.getPath());
        assertEquals("/foo", cn12.getPath());
        assertEquals("/foo/bar", cn13.getPath());
        assertEquals("", cn14.getPath());
        assertEquals("/foo", cn15.getPath());
        assertEquals("/foo/bar", cn16.getPath());
    }

    @Test
    public void testGetVersion() {
        assertEquals("", cn1.getVersion());
        assertEquals("", cn2.getVersion());
        assertEquals("", cn3.getVersion());
        assertEquals("", cn4.getVersion());
        assertEquals("", cn5.getVersion());
        assertEquals("A", cn6.getVersion());
        assertEquals("B", cn7.getVersion());
        assertEquals("C", cn8.getVersion());
        assertEquals("D", cn9.getVersion());
        assertEquals("E", cn10.getVersion());
        assertEquals("", cn11.getVersion());
        assertEquals("", cn12.getVersion());
        assertEquals("", cn13.getVersion());
        assertEquals("A", cn14.getVersion());
        assertEquals("D", cn15.getVersion());
        assertEquals("E", cn16.getVersion());
    }

    @Test
    public void testGetName() {
        assertEquals("", cn1.getName());
        assertEquals("", cn2.getName());
        assertEquals("", cn3.getName());
        assertEquals("/foo", cn4.getName());
        assertEquals("/foo/bar", cn5.getName());
        assertEquals("##A", cn6.getName());
        assertEquals("##B", cn7.getName());
        assertEquals("##C", cn8.getName());
        assertEquals("/foo##D", cn9.getName());
        assertEquals("/foo/bar##E", cn10.getName());
        assertEquals("", cn11.getName());
        assertEquals("/foo", cn12.getName());
        assertEquals("/foo/bar", cn13.getName());
        assertEquals("##A", cn14.getName());
        assertEquals("/foo##D", cn15.getName());
        assertEquals("/foo/bar##E", cn16.getName());
    }

    @Test
    public void testGetDisplayName() {
        assertEquals("/", cn1.getDisplayName());
        assertEquals("/", cn2.getDisplayName());
        assertEquals("/", cn3.getDisplayName());
        assertEquals("/foo", cn4.getDisplayName());
        assertEquals("/foo/bar", cn5.getDisplayName());
        assertEquals("/##A", cn6.getDisplayName());
        assertEquals("/##B", cn7.getDisplayName());
        assertEquals("/##C", cn8.getDisplayName());
        assertEquals("/foo##D", cn9.getDisplayName());
        assertEquals("/foo/bar##E", cn10.getDisplayName());
        assertEquals("/", cn11.getDisplayName());
        assertEquals("/foo", cn12.getDisplayName());
        assertEquals("/foo/bar", cn13.getDisplayName());
        assertEquals("/##A", cn14.getDisplayName());
        assertEquals("/foo##D", cn15.getDisplayName());
        assertEquals("/foo/bar##E", cn16.getDisplayName());
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
    }

    private void doTestConstructorString(ContextName src) {
        doCompare(src, new ContextName(src.getBaseName()));
        doCompare(src, new ContextName(src.getDisplayName()));
        doCompare(src, new ContextName(src.getName()));
    }

    private void doCompare(ContextName cn1, ContextName cn2) {
        assertEquals(cn1.getBaseName(), cn2.getBaseName());
        assertEquals(cn1.getDisplayName(), cn2.getDisplayName());
        assertEquals(cn1.getName(), cn2.getName());
        assertEquals(cn1.getPath(), cn2.getPath());
        assertEquals(cn1.getVersion(), cn2.getVersion());
    }
}
