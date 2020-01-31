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
package org.apache.catalina.mapper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardWrapper;
import org.apache.catalina.startup.LoggingBaseTest;
import org.apache.tomcat.util.buf.MessageBytes;

public class TestMapper extends LoggingBaseTest {

    protected Mapper mapper;

    private HashMap<String, Host> hostMap = new HashMap<>();

    private synchronized Host createHost(String name) {
        Host host = hostMap.get(name);
        if (host == null) {
            host = new StandardHost();
            host.setName(name);
            hostMap.put(name, host);
        }
        return host;
    }

    private Context createContext(String name) {
        Context context = new StandardContext();
        context.setName(name);
        return context;
    }

    private Wrapper createWrapper(String name) {
        Wrapper wrapper = new StandardWrapper();
        wrapper.setName(name);
        return wrapper;
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mapper = new Mapper();

        mapper.addHost("sjbjdvwsbvhrb", new String[0], createHost("blah1"));
        mapper.addHost("sjbjdvwsbvhr/", new String[0], createHost("blah1"));
        mapper.addHost("wekhfewuifweuibf", new String[0], createHost("blah2"));
        mapper.addHost("ylwrehirkuewh", new String[0], createHost("blah3"));
        mapper.addHost("iohgeoihro", new String[0], createHost("blah4"));
        mapper.addHost("fwehoihoihwfeo", new String[0], createHost("blah5"));
        mapper.addHost("owefojiwefoi", new String[0], createHost("blah6"));
        mapper.addHost("iowejoiejfoiew", new String[0], createHost("blah7"));
        mapper.addHost("ohewoihfewoih", new String[0], createHost("blah8"));
        mapper.addHost("fewohfoweoih", new String[0], createHost("blah9"));
        mapper.addHost("ttthtiuhwoih", new String[0], createHost("blah10"));
        mapper.addHost("lkwefjwojweffewoih", new String[0], createHost("blah11"));
        mapper.addHost("zzzuyopjvewpovewjhfewoih", new String[0], createHost("blah12"));
        mapper.addHost("xxxxgqwiwoih", new String[0], createHost("blah13"));
        mapper.addHost("qwigqwiwoih", new String[0], createHost("blah14"));
        mapper.addHost("qwerty.net", new String[0], createHost("blah15"));
        mapper.addHost("*.net", new String[0], createHost("blah16"));
        mapper.addHost("zzz.com", new String[0], createHost("blah17"));
        mapper.addHostAlias("iowejoiejfoiew", "iowejoiejfoiew_alias");

        mapper.setDefaultHostName("ylwrehirkuewh");

        String[] welcomes = new String[2];
        welcomes[0] = "boo/baba";
        welcomes[1] = "bobou";

        Host host = createHost("blah7");
        mapper.addContextVersion("iowejoiejfoiew", host, "",
                "0", createContext("context0"), new String[0], null, null);
        mapper.addContextVersion("iowejoiejfoiew", host, "/foo",
                "0", createContext("context1"), new String[0], null, null);
        mapper.addContextVersion("iowejoiejfoiew", host, "/foo/bar",
                "0", createContext("context2"), welcomes, null, null);

        mapper.addWrappers("iowejoiejfoiew", "/foo", "0", Arrays
                .asList(new WrapperMappingInfo[] {
                        new WrapperMappingInfo("/",
                                createWrapper("context1-defaultWrapper"), false, false) }));
        mapper.addWrappers("iowejoiejfoiew", "/foo/bar", "0", Arrays
                .asList(new WrapperMappingInfo[] {
                        new WrapperMappingInfo("/fo/*",
                                createWrapper("wrapper0"), false, false),
                        new WrapperMappingInfo("/", createWrapper("wrapper1"),
                                false, false),
                        new WrapperMappingInfo("/blh",
                                createWrapper("wrapper2"), false, false),
                        new WrapperMappingInfo("*.jsp",
                                createWrapper("wrapper3"), false, false),
                        new WrapperMappingInfo("/blah/bou/*",
                                createWrapper("wrapper4"), false, false),
                        new WrapperMappingInfo("/blah/bobou/*",
                                createWrapper("wrapper5"), false, false),
                        new WrapperMappingInfo("*.htm",
                                createWrapper("wrapper6"), false, false) }));

        mapper.addContextVersion(
                "iowejoiejfoiew",
                host,
                "/foo/bar/bla",
                "0",
                createContext("context3"),
                new String[0],
                null,
                Arrays.asList(new WrapperMappingInfo[] { new WrapperMappingInfo(
                        "/bobou/*", createWrapper("wrapper7"), false, false) }));

        host = createHost("blah16");
        mapper.addContextVersion("*.net", host, "", "0", createContext("context4"),
                new String[0], null, null);
        mapper.addWrappers("*.net", "", "0", Arrays
                .asList(new WrapperMappingInfo[] {
                        new WrapperMappingInfo("/",
                                createWrapper("context4-defaultWrapper"), false, false) }));
    }

    @Test
    public void testAddHost() throws Exception {
        // Try to add duplicates
        // Duplicate Host name
        mapper.addHost("iowejoiejfoiew", new String[0], createHost("blah17"));
        // Alias conflicting with existing Host
        mapper.addHostAlias("iowejoiejfoiew", "qwigqwiwoih");
        // Alias conflicting with existing Alias
        mapper.addHostAlias("sjbjdvwsbvhrb", "iowejoiejfoiew_alias");
        // Redundancy. Alias name = Host name. No error here.
        mapper.addHostAlias("qwigqwiwoih", "qwigqwiwoih");
        // Redundancy. Duplicate Alias for the same Host name. No error here.
        mapper.addHostAlias("iowejoiejfoiew", "iowejoiejfoiew_alias");
        mapper.addHostAlias("iowejoiejfoiew", "iowejoiejfoiew_alias");

        // Check we have the right number
        // (added 17 including one host alias. Three duplicates do not increase the count.)
        Assert.assertEquals(19, mapper.hosts.length);

        // Make sure adding a duplicate *does not* overwrite
        final int iowPos = 4;
        Assert.assertEquals("blah7", mapper.hosts[iowPos].object.getName());

        final int qwigPos = 10;
        Assert.assertEquals("blah14", mapper.hosts[qwigPos].object.getName());

        // Check for alphabetical order of host names
        String previous;
        String current = mapper.hosts[0].name;
        for (int i = 1; i < mapper.hosts.length; i++) {
            previous = current;
            current = mapper.hosts[i].name;
            Assert.assertTrue(previous.compareTo(current) < 0);
        }

        // Check that host alias has the same data
        Mapper.MappedHost host = mapper.hosts[iowPos];
        Mapper.MappedHost alias = mapper.hosts[iowPos + 1];
        Assert.assertEquals("iowejoiejfoiew", host.name);
        Assert.assertEquals("iowejoiejfoiew_alias", alias.name);
        Assert.assertFalse(host.isAlias());
        Assert.assertTrue(alias.isAlias());
        Assert.assertEquals(host.object, alias.object);

        // Test addContextVersion() followed by addHost()
        Host hostZ = createHost("zzzz");
        Context contextZ = createContext("contextZ");

        Assert.assertEquals(19, mapper.hosts.length);
        mapper.addContextVersion("zzzz", hostZ, "/", "", contextZ, null, null,
                null);
        Assert.assertEquals(20, mapper.hosts.length);

        mapper.addHost("zzzz", new String[] { "zzzz_alias1", "zzzz_alias2" },
                hostZ);
        Assert.assertEquals(22, mapper.hosts.length);

        Assert.assertEquals("zzzz", mapper.hosts[19].name);
        Assert.assertEquals("zzzz_alias1", mapper.hosts[20].name);
        Assert.assertEquals("zzzz_alias2", mapper.hosts[21].name);
        Assert.assertEquals(2, mapper.hosts[19].getAliases().size());
        Assert.assertSame(contextZ,
                mapper.hosts[19].contextList.contexts[0].versions[0].object);
        Assert.assertSame(contextZ,
                mapper.hosts[21].contextList.contexts[0].versions[0].object);
    }

    @Test
    public void testRemoveHost() {
        Assert.assertEquals(19, mapper.hosts.length);
        mapper.removeHostAlias("iowejoiejfoiew");
        mapper.removeHost("iowejoiejfoiew_alias");
        Assert.assertEquals(19, mapper.hosts.length); // No change
        mapper.removeHostAlias("iowejoiejfoiew_alias");
        Assert.assertEquals(18, mapper.hosts.length); // Removed

        mapper.addHostAlias("iowejoiejfoiew", "iowejoiejfoiew_alias");
        Assert.assertEquals(19, mapper.hosts.length);

        final int iowPos = 4;
        Mapper.MappedHost hostMapping = mapper.hosts[iowPos];
        Mapper.MappedHost aliasMapping = mapper.hosts[iowPos + 1];
        Assert.assertEquals("iowejoiejfoiew_alias", aliasMapping.name);
        Assert.assertTrue(aliasMapping.isAlias());
        Assert.assertSame(hostMapping.object, aliasMapping.object);

        Assert.assertEquals("iowejoiejfoiew", hostMapping.getRealHostName());
        Assert.assertEquals("iowejoiejfoiew", aliasMapping.getRealHostName());
        Assert.assertSame(hostMapping, hostMapping.getRealHost());
        Assert.assertSame(hostMapping, aliasMapping.getRealHost());

        mapper.removeHost("iowejoiejfoiew");
        Assert.assertEquals(17, mapper.hosts.length); // Both host and alias removed
        for (Mapper.MappedHost host : mapper.hosts) {
            Assert.assertTrue(host.name, !host.name.startsWith("iowejoiejfoiew"));
        }
    }

    @Test
    @SuppressWarnings("deprecation") // contextPath
    public void testMap() throws Exception {
        MappingData mappingData = new MappingData();
        MessageBytes host = MessageBytes.newInstance();
        host.setString("iowejoiejfoiew");
        MessageBytes wildcard = MessageBytes.newInstance();
        wildcard.setString("foo.net");
        MessageBytes alias = MessageBytes.newInstance();
        alias.setString("iowejoiejfoiew_alias");
        MessageBytes uri = MessageBytes.newInstance();
        uri.setString("/foo/bar/blah/bobou/foo");
        uri.toChars();
        uri.getCharChunk().setLimit(-1);

        mapper.map(host, uri, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host.getName());
        Assert.assertEquals("context2", mappingData.context.getName());
        Assert.assertEquals("wrapper5", mappingData.wrapper.getName());
        Assert.assertEquals("/foo/bar", mappingData.contextPath.toString());
        Assert.assertEquals("/blah/bobou", mappingData.wrapperPath.toString());
        Assert.assertEquals("/foo", mappingData.pathInfo.toString());
        Assert.assertTrue(mappingData.redirectPath.isNull());

        mappingData.recycle();
        uri.recycle();
        uri.setString("/foo/bar/bla/bobou/foo");
        uri.toChars();
        uri.getCharChunk().setLimit(-1);
        mapper.map(host, uri, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host.getName());
        Assert.assertEquals("context3", mappingData.context.getName());
        Assert.assertEquals("wrapper7", mappingData.wrapper.getName());
        Assert.assertEquals("/foo/bar/bla", mappingData.contextPath.toString());
        Assert.assertEquals("/bobou", mappingData.wrapperPath.toString());
        Assert.assertEquals("/foo", mappingData.pathInfo.toString());
        Assert.assertTrue(mappingData.redirectPath.isNull());

        mappingData.recycle();
        uri.recycle();
        uri.setString("/foo/bar/bla/bobou/foo");
        uri.toChars();
        uri.getCharChunk().setLimit(-1);
        mapper.map(wildcard, uri, null, mappingData);
        Assert.assertEquals("blah16", mappingData.host.getName());
        Assert.assertEquals("context4", mappingData.context.getName());
        Assert.assertEquals("context4-defaultWrapper", mappingData.wrapper.getName());
        Assert.assertEquals("", mappingData.contextPath.toString());
        Assert.assertEquals("/foo/bar/bla/bobou/foo", mappingData.wrapperPath.toString());
        Assert.assertTrue(mappingData.pathInfo.isNull());
        Assert.assertTrue(mappingData.redirectPath.isNull());

        mappingData.recycle();
        uri.setString("/foo/bar/bla/bobou/foo");
        uri.toChars();
        uri.getCharChunk().setLimit(-1);
        mapper.map(alias, uri, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host.getName());
        Assert.assertEquals("context3", mappingData.context.getName());
        Assert.assertEquals("wrapper7", mappingData.wrapper.getName());
        Assert.assertEquals("/foo/bar/bla", mappingData.contextPath.toString());
        Assert.assertEquals("/bobou", mappingData.wrapperPath.toString());
        Assert.assertEquals("/foo", mappingData.pathInfo.toString());
        Assert.assertTrue(mappingData.redirectPath.isNull());
    }

    @Test
    public void testAddRemoveContextVersion() throws Exception {
        final String hostName = "iowejoiejfoiew";
        final int iowPos = 4;
        final String contextPath = "/foo/bar";
        final int contextPos = 2;

        MappingData mappingData = new MappingData();
        MessageBytes hostMB = MessageBytes.newInstance();
        MessageBytes uriMB = MessageBytes.newInstance();
        hostMB.setString(hostName);
        uriMB.setString("/foo/bar/blah/bobou/foo");

        // Verifying configuration created by setUp()
        Mapper.MappedHost mappedHost = mapper.hosts[iowPos];
        Assert.assertEquals(hostName, mappedHost.name);
        Mapper.MappedContext mappedContext = mappedHost.contextList.contexts[contextPos];
        Assert.assertEquals(contextPath, mappedContext.name);
        Assert.assertEquals(1, mappedContext.versions.length);
        Assert.assertEquals("0", mappedContext.versions[0].name);
        Host oldHost = mappedHost.object;
        Context oldContext = mappedContext.versions[0].object;
        Assert.assertEquals("context2", oldContext.getName());

        Context oldContext1 = mappedHost.contextList.contexts[contextPos - 1].versions[0].object;
        Assert.assertEquals("context1", oldContext1.getName());

        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host.getName());
        Assert.assertEquals("context2", mappingData.context.getName());
        Assert.assertEquals("wrapper5", mappingData.wrapper.getName());
        mappingData.recycle();
        mapper.map(oldContext, uriMB, mappingData);
        Assert.assertEquals("wrapper5", mappingData.wrapper.getName());

        Context newContext = createContext("newContext");
        mapper.addContextVersion(
                hostName,
                oldHost,
                contextPath,
                "1",
                newContext,
                null,
                null,
                Arrays.asList(new WrapperMappingInfo[] { new WrapperMappingInfo(
                        "/", createWrapper("newContext-default"), false, false) }));

        Assert.assertEquals(2, mappedContext.versions.length);
        Assert.assertEquals("0", mappedContext.versions[0].name);
        Assert.assertEquals("1", mappedContext.versions[1].name);
        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("newContext", mappingData.context.getName());
        Assert.assertEquals("newContext-default", mappingData.wrapper.getName());
        mappingData.recycle();
        mapper.map(newContext, uriMB, mappingData);
        Assert.assertEquals("newContext-default", mappingData.wrapper.getName());

        mapper.removeContextVersion(oldContext, hostName, contextPath, "0");

        Assert.assertEquals(1, mappedContext.versions.length);
        Assert.assertEquals("1", mappedContext.versions[0].name);
        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("newContext", mappingData.context.getName());
        Assert.assertEquals("newContext-default", mappingData.wrapper.getName());
        mappingData.recycle();
        mapper.map(newContext, uriMB, mappingData);
        Assert.assertEquals("newContext-default", mappingData.wrapper.getName());

        mapper.removeContextVersion(oldContext, hostName, contextPath, "1");

        Assert.assertNotSame(mappedContext, mappedHost.contextList.contexts[contextPos]);
        Assert.assertEquals("/foo/bar/bla", mappedHost.contextList.contexts[contextPos].name);
        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("context1", mappingData.context.getName());
        Assert.assertEquals("context1-defaultWrapper", mappingData.wrapper.getName());
        mappingData.recycle();
        mapper.map(oldContext1, uriMB, mappingData);
        Assert.assertEquals("context1-defaultWrapper", mappingData.wrapper.getName());

        mapper.addContextVersion(
                hostName,
                oldHost,
                contextPath,
                "0",
                newContext,
                null,
                null,
                Arrays.asList(new WrapperMappingInfo[] { new WrapperMappingInfo(
                        "/", createWrapper("newContext-defaultWrapper2"), false, false) }));
        mappedContext = mappedHost.contextList.contexts[contextPos];

        Assert.assertEquals(contextPath, mappedContext.name);
        Assert.assertEquals(1, mappedContext.versions.length);
        Assert.assertEquals("0", mappedContext.versions[0].name);
        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("newContext", mappingData.context.getName());
        Assert.assertEquals("newContext-defaultWrapper2", mappingData.wrapper.getName());
        mappingData.recycle();
        mapper.map(newContext, uriMB, mappingData);
        Assert.assertEquals("newContext-defaultWrapper2", mappingData.wrapper.getName());
    }

    @Test
    public void testReloadContextVersion() throws Exception {
        final String hostName = "iowejoiejfoiew";
        final int iowPos = 4;
        final String contextPath = "/foo/bar";
        final int contextPos = 2;

        MappingData mappingData = new MappingData();
        MessageBytes hostMB = MessageBytes.newInstance();
        MessageBytes uriMB = MessageBytes.newInstance();
        hostMB.setString(hostName);
        uriMB.setString("/foo/bar/blah/bobou/foo");

        // Verifying configuration created by setUp()
        Mapper.MappedHost mappedHost = mapper.hosts[iowPos];
        Assert.assertEquals(hostName, mappedHost.name);
        Mapper.MappedContext mappedContext = mappedHost.contextList.contexts[contextPos];
        Assert.assertEquals(contextPath, mappedContext.name);
        Assert.assertEquals(1, mappedContext.versions.length);
        Assert.assertEquals("0", mappedContext.versions[0].name);
        Host oldHost = mappedHost.object;
        Context oldContext = mappedContext.versions[0].object;
        Assert.assertEquals("context2", oldContext.getName());

        Context oldContext1 = mappedHost.contextList.contexts[contextPos - 1].versions[0].object;
        Assert.assertEquals("context1", oldContext1.getName());

        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host.getName());
        Assert.assertEquals("context2", mappingData.context.getName());
        Assert.assertEquals("wrapper5", mappingData.wrapper.getName());
        mappingData.recycle();
        mapper.map(oldContext, uriMB, mappingData);
        Assert.assertEquals("wrapper5", mappingData.wrapper.getName());

        // Mark context as paused
        // This is what happens when context reload starts
        mapper.pauseContextVersion(oldContext, hostName, contextPath, "0");

        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host.getName());
        Assert.assertEquals("context2", mappingData.context.getName());
        // Wrapper is not mapped for incoming requests if context is paused
        Assert.assertNull(mappingData.wrapper);
        mappingData.recycle();
        mapper.map(oldContext, uriMB, mappingData);
        // Wrapper is mapped for mapping method used by forward or include dispatch
        Assert.assertEquals("wrapper5", mappingData.wrapper.getName());

        // Re-add the same context, but different list of wrappers
        // This is what happens when context reload completes
        mapper.addContextVersion(
                hostName,
                oldHost,
                contextPath,
                "0",
                oldContext,
                null,
                null,
                Arrays.asList(new WrapperMappingInfo[] { new WrapperMappingInfo(
                        "/", createWrapper("newDefaultWrapper"), false, false) }));

        mappedContext = mappedHost.contextList.contexts[contextPos];
        Assert.assertEquals(contextPath, mappedContext.name);
        Assert.assertEquals(1, mappedContext.versions.length);
        Assert.assertEquals("0", mappedContext.versions[0].name);

        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host.getName());
        Assert.assertEquals("context2", mappingData.context.getName());
        Assert.assertEquals("newDefaultWrapper", mappingData.wrapper.getName());
        mappingData.recycle();
        mapper.map(oldContext, uriMB, mappingData);
        Assert.assertEquals("newDefaultWrapper", mappingData.wrapper.getName());
    }

    @Test
    @SuppressWarnings("deprecation") // contextPath
    public void testContextListConcurrencyBug56653() throws Exception {
        final Host host = createHost("localhost");
        final Context contextRoot = createContext("ROOT");
        final Context context1 = createContext("foo");
        final Context context2 = createContext("foo#bar");
        final Context context3 = createContext("foo#bar#bla");
        final Context context4 = createContext("foo#bar#bla#baz");

        mapper.addHost("localhost", new String[] { "alias" }, host);
        mapper.setDefaultHostName("localhost");

        mapper.addContextVersion("localhost", host, "", "0", contextRoot,
                new String[0], null, null);
        mapper.addContextVersion("localhost", host, "/foo", "0", context1,
                new String[0], null, null);
        mapper.addContextVersion("localhost", host, "/foo/bar", "0", context2,
                new String[0], null, null);
        mapper.addContextVersion("localhost", host, "/foo/bar/bla", "0",
                context3, new String[0], null, null);
        mapper.addContextVersion("localhost", host, "/foo/bar/bla/baz", "0",
                context4, new String[0], null, null);

        final AtomicBoolean running = new AtomicBoolean(true);
        Thread t = new Thread() {
            @Override
            public void run() {
                for (int i = 0; i < 100000; i++) {
                    mapper.removeContextVersion(context4, "localhost",
                            "/foo/bar/bla/baz", "0");
                    mapper.addContextVersion("localhost", host,
                            "/foo/bar/bla/baz", "0", context4, new String[0],
                            null, null);
                }
                running.set(false);
            }
        };

        MappingData mappingData = new MappingData();
        MessageBytes hostMB = MessageBytes.newInstance();
        hostMB.setString("localhost");
        MessageBytes aliasMB = MessageBytes.newInstance();
        aliasMB.setString("alias");
        MessageBytes uriMB = MessageBytes.newInstance();
        char[] uri = "/foo/bar/bla/bobou/foo".toCharArray();
        uriMB.setChars(uri, 0, uri.length);

        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("/foo/bar/bla", mappingData.contextPath.toString());

        mappingData.recycle();
        uriMB.setChars(uri, 0, uri.length);
        mapper.map(aliasMB, uriMB, null, mappingData);
        Assert.assertEquals("/foo/bar/bla", mappingData.contextPath.toString());

        t.start();
        while (running.get()) {
            mappingData.recycle();
            uriMB.setChars(uri, 0, uri.length);
            mapper.map(hostMB, uriMB, null, mappingData);
            Assert.assertEquals("/foo/bar/bla", mappingData.contextPath.toString());

            mappingData.recycle();
            uriMB.setChars(uri, 0, uri.length);
            mapper.map(aliasMB, uriMB, null, mappingData);
            Assert.assertEquals("/foo/bar/bla", mappingData.contextPath.toString());
        }
    }
}
