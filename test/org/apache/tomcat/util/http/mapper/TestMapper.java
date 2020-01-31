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
package org.apache.tomcat.util.http.mapper;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.startup.LoggingBaseTest;
import org.apache.tomcat.util.buf.MessageBytes;

public class TestMapper extends LoggingBaseTest {

    private Mapper mapper;
    private Mapper mapperForContext1;
    private Mapper mapperForContext2;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mapper = new Mapper();

        mapper.addHost("sjbjdvwsbvhrb", new String[0], "blah1");
        mapper.addHost("sjbjdvwsbvhr/", new String[0], "blah1");
        mapper.addHost("wekhfewuifweuibf", new String[0], "blah2");
        mapper.addHost("ylwrehirkuewh", new String[0], "blah3");
        mapper.addHost("iohgeoihro", new String[0], "blah4");
        mapper.addHost("fwehoihoihwfeo", new String[0], "blah5");
        mapper.addHost("owefojiwefoi", new String[0], "blah6");
        mapper.addHost("iowejoiejfoiew", new String[0], "blah7");
        mapper.addHost("ohewoihfewoih", new String[0], "blah8");
        mapper.addHost("fewohfoweoih", new String[0], "blah9");
        mapper.addHost("ttthtiuhwoih", new String[0], "blah10");
        mapper.addHost("lkwefjwojweffewoih", new String[0], "blah11");
        mapper.addHost("zzzuyopjvewpovewjhfewoih", new String[0], "blah12");
        mapper.addHost("xxxxgqwiwoih", new String[0], "blah13");
        mapper.addHost("qwigqwiwoih", new String[0], "blah14");
        mapper.addHostAlias("iowejoiejfoiew", "iowejoiejfoiew_alias");

        mapper.setDefaultHostName("ylwrehirkuewh");

        String[] welcomes = new String[2];
        welcomes[0] = "boo/baba";
        welcomes[1] = "bobou";

        mapper.addContextVersion("iowejoiejfoiew", "blah7", "",
                "0", "context0", new String[0], null, null, false, false);
        mapper.addContextVersion("iowejoiejfoiew", "blah7", "/foo",
                "0", "context1", new String[0], null, null, false, false);
        mapper.addContextVersion("iowejoiejfoiew", "blah7", "/foo/bar",
                "0", "context2", welcomes, null, null, false, false);

        Collection<WrapperMappingInfo> wrappersForContext1 = Arrays
                .asList(new WrapperMappingInfo[] { new WrapperMappingInfo("/",
                        "context1-defaultWrapper", false, false) });
        Collection<WrapperMappingInfo> wrappersForContext2 = Arrays
                .asList(new WrapperMappingInfo[] {
                        new WrapperMappingInfo("/fo/*", "wrapper0", false,
                                false),
                        new WrapperMappingInfo("/", "wrapper1", false, false),
                        new WrapperMappingInfo("/blh", "wrapper2", false, false),
                        new WrapperMappingInfo("*.jsp", "wrapper3", false,
                                false),
                        new WrapperMappingInfo("/blah/bou/*", "wrapper4",
                                false, false),
                        new WrapperMappingInfo("/blah/bobou/*", "wrapper5",
                                false, false),
                        new WrapperMappingInfo("*.htm", "wrapper6", false,
                                false) });

        mapper.addWrappers("iowejoiejfoiew", "/foo", "0", wrappersForContext1);
        mapperForContext1 = new Mapper();
        mapperForContext1.setContext("/foo", new String[0], null);
        for (WrapperMappingInfo wrapper : wrappersForContext1) {
            mapperForContext1.addWrapper(wrapper.getMapping(),
                    wrapper.getWrapper(), wrapper.isJspWildCard(),
                    wrapper.isResourceOnly());
        }

        mapper.addWrappers("iowejoiejfoiew", "/foo/bar", "0",
                wrappersForContext2);
        mapperForContext2 = new Mapper();
        mapperForContext2.setContext("/foo/bar", new String[0], null);
        for (WrapperMappingInfo wrapper : wrappersForContext2) {
            mapperForContext2.addWrapper(wrapper.getMapping(),
                    wrapper.getWrapper(), wrapper.isJspWildCard(),
                    wrapper.isResourceOnly());
        }

        mapper.addContextVersion(
                "iowejoiejfoiew",
                "blah7",
                "/foo/bar/bla",
                "0",
                "context3",
                new String[0],
                null,
                Arrays.asList(new WrapperMappingInfo[] { new WrapperMappingInfo(
                        "/bobou/*", "wrapper7", false, false) }),
                false,
                false);
    }

    @Test
    public void testAddHost() throws Exception {
        // Try to add duplicates
        // Duplicate Host name
        mapper.addHost("iowejoiejfoiew", new String[0], "blah17");
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
        // (added 16 including one host alias. Three duplicates do not increase the count.)
        Assert.assertEquals(16, mapper.hosts.length);

        // Make sure adding a duplicate *does not* overwrite
        final int iowPos = 3;
        Assert.assertEquals("blah7", mapper.hosts[iowPos].object);

        final int qwigPos = 8;
        Assert.assertEquals("blah14", mapper.hosts[qwigPos].object);

        // Check for alphabetical order of host names
        String previous;
        String current = mapper.hosts[0].name;
        for (int i = 1; i < mapper.hosts.length; i++) {
            previous = current;
            current = mapper.hosts[i].name;
            Assert.assertTrue(previous.compareTo(current) < 0);
        }

        // Check that host alias has the same data
        Mapper.Host host = mapper.hosts[iowPos];
        Mapper.Host alias = mapper.hosts[iowPos + 1];
        Assert.assertEquals("iowejoiejfoiew", host.name);
        Assert.assertEquals("iowejoiejfoiew_alias", alias.name);
        Assert.assertFalse(host.isAlias());
        Assert.assertTrue(alias.isAlias());
        Assert.assertEquals(host.object, alias.object);

        // Test addContextVersion() followed by addHost()
        Object hostZ = "zzzz";
        Object contextZ = "contextZ";

        Assert.assertEquals(16, mapper.hosts.length);
        mapper.addContextVersion("zzzz", hostZ, "/", "", contextZ, null, null,
                null, false, false);
        Assert.assertEquals(17, mapper.hosts.length);

        mapper.addHost("zzzz", new String[] { "zzzz_alias1", "zzzz_alias2" },
                hostZ);
        Assert.assertEquals(19, mapper.hosts.length);

        Assert.assertEquals("zzzz", mapper.hosts[16].name);
        Assert.assertEquals("zzzz_alias1", mapper.hosts[17].name);
        Assert.assertEquals("zzzz_alias2", mapper.hosts[18].name);
        Assert.assertEquals(2, mapper.hosts[16].getAliases().size());
        Assert.assertSame(contextZ,
                mapper.hosts[16].contextList.contexts[0].versions[0].object);
        Assert.assertSame(contextZ,
                mapper.hosts[18].contextList.contexts[0].versions[0].object);
    }

    @Test
    public void testRemoveHost() {
        Assert.assertEquals(16, mapper.hosts.length);
        mapper.removeHostAlias("iowejoiejfoiew");
        mapper.removeHost("iowejoiejfoiew_alias");
        Assert.assertEquals(16, mapper.hosts.length); // No change
        mapper.removeHostAlias("iowejoiejfoiew_alias");
        Assert.assertEquals(15, mapper.hosts.length); // Removed

        mapper.addHostAlias("iowejoiejfoiew", "iowejoiejfoiew_alias");
        Assert.assertEquals(16, mapper.hosts.length);

        final int iowPos = 3;
        Mapper.Host hostMapping = mapper.hosts[iowPos];
        Mapper.Host aliasMapping = mapper.hosts[iowPos + 1];
        Assert.assertEquals("iowejoiejfoiew_alias", aliasMapping.name);
        Assert.assertTrue(aliasMapping.isAlias());
        Assert.assertSame(hostMapping.object, aliasMapping.object);

        Assert.assertEquals("iowejoiejfoiew", hostMapping.getRealHostName());
        Assert.assertEquals("iowejoiejfoiew", aliasMapping.getRealHostName());
        Assert.assertSame(hostMapping, hostMapping.getRealHost());
        Assert.assertSame(hostMapping, aliasMapping.getRealHost());

        mapper.removeHost("iowejoiejfoiew");
        Assert.assertEquals(14, mapper.hosts.length); // Both host and alias removed
        for (Mapper.Host host : mapper.hosts) {
            Assert.assertTrue(host.name, !host.name.startsWith("iowejoiejfoiew"));
        }
    }

    @Test
    @SuppressWarnings("deprecation") // contextPath
    public void testMap() throws Exception {
        MappingData mappingData = new MappingData();
        MessageBytes host = MessageBytes.newInstance();
        host.setString("iowejoiejfoiew");
        MessageBytes alias = MessageBytes.newInstance();
        alias.setString("iowejoiejfoiew_alias");
        MessageBytes uri = MessageBytes.newInstance();
        uri.setString("/foo/bar/blah/bobou/foo");
        uri.toChars();
        uri.getCharChunk().setLimit(-1);

        mapper.map(host, uri, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host);
        Assert.assertEquals("context2", mappingData.context);
        Assert.assertEquals("wrapper5", mappingData.wrapper);
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
        Assert.assertEquals("blah7", mappingData.host);
        Assert.assertEquals("context3", mappingData.context);
        Assert.assertEquals("wrapper7", mappingData.wrapper);
        Assert.assertEquals("/foo/bar/bla", mappingData.contextPath.toString());
        Assert.assertEquals("/bobou", mappingData.wrapperPath.toString());
        Assert.assertEquals("/foo", mappingData.pathInfo.toString());
        Assert.assertTrue(mappingData.redirectPath.isNull());

        mappingData.recycle();
        uri.setString("/foo/bar/bla/bobou/foo");
        uri.toChars();
        uri.getCharChunk().setLimit(-1);
        mapper.map(alias, uri, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host);
        Assert.assertEquals("context3", mappingData.context);
        Assert.assertEquals("wrapper7", mappingData.wrapper);
        Assert.assertEquals("/foo/bar/bla", mappingData.contextPath.toString());
        Assert.assertEquals("/bobou", mappingData.wrapperPath.toString());
        Assert.assertEquals("/foo", mappingData.pathInfo.toString());
        Assert.assertTrue(mappingData.redirectPath.isNull());
    }

    @Test
    public void testAddRemoveContextVersion() throws Exception {
        final String hostName = "iowejoiejfoiew";
        final int iowPos = 3;
        final String contextPath = "/foo/bar";
        final int contextPos = 2;

        MappingData mappingData = new MappingData();
        MessageBytes hostMB = MessageBytes.newInstance();
        MessageBytes uriMB = MessageBytes.newInstance();
        hostMB.setString(hostName);
        uriMB.setString("/foo/bar/blah/bobou/foo");

        // Verifying configuration created by setUp()
        Mapper.Host mappedHost = mapper.hosts[iowPos];
        Assert.assertEquals(hostName, mappedHost.name);
        Mapper.Context mappedContext = mappedHost.contextList.contexts[contextPos];
        Assert.assertEquals(contextPath, mappedContext.name);
        Assert.assertEquals(1, mappedContext.versions.length);
        Assert.assertEquals("0", mappedContext.versions[0].name);
        Object oldHost = mappedHost.object;
        Object oldContext = mappedContext.versions[0].object;
        Assert.assertEquals("context2", oldContext.toString());

        Object oldContext1 = mappedHost.contextList.contexts[contextPos - 1].versions[0].object;
        Assert.assertEquals("context1", oldContext1.toString());

        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host.toString());
        Assert.assertEquals("context2", mappingData.context.toString());
        Assert.assertEquals("wrapper5", mappingData.wrapper.toString());
        mappingData.recycle();
        mapperForContext2.map(uriMB, mappingData);
        Assert.assertEquals("wrapper5", mappingData.wrapper.toString());

        Object newContext = "newContext";
        mapper.addContextVersion(
                hostName,
                oldHost,
                contextPath,
                "1",
                newContext,
                null,
                null,
                Arrays.asList(new WrapperMappingInfo[] { new WrapperMappingInfo(
                        "/", "newContext-default", false, false) }),
                false,
                false);

        Assert.assertEquals(2, mappedContext.versions.length);
        Assert.assertEquals("0", mappedContext.versions[0].name);
        Assert.assertEquals("1", mappedContext.versions[1].name);
        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("newContext", mappingData.context.toString());
        Assert.assertEquals("newContext-default", mappingData.wrapper.toString());

        mapper.removeContextVersion(hostName, contextPath, "0");

        Assert.assertEquals(1, mappedContext.versions.length);
        Assert.assertEquals("1", mappedContext.versions[0].name);
        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("newContext", mappingData.context.toString());
        Assert.assertEquals("newContext-default", mappingData.wrapper.toString());

        mapper.removeContextVersion(hostName, contextPath, "1");

        Assert.assertNotSame(mappedContext, mappedHost.contextList.contexts[contextPos]);
        Assert.assertEquals("/foo/bar/bla", mappedHost.contextList.contexts[contextPos].name);
        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("context1", mappingData.context.toString());
        Assert.assertEquals("context1-defaultWrapper", mappingData.wrapper.toString());
        mappingData.recycle();
        mapperForContext1.map(uriMB, mappingData);
        Assert.assertEquals("context1-defaultWrapper", mappingData.wrapper.toString());

        mapper.addContextVersion(
                hostName,
                oldHost,
                contextPath,
                "0",
                newContext,
                null,
                null,
                Arrays.asList(new WrapperMappingInfo[] { new WrapperMappingInfo(
                        "/", "newContext-defaultWrapper2", false, false) }),
                false,
                false);
        mappedContext = mappedHost.contextList.contexts[contextPos];

        Assert.assertEquals(contextPath, mappedContext.name);
        Assert.assertEquals(1, mappedContext.versions.length);
        Assert.assertEquals("0", mappedContext.versions[0].name);
        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("newContext", mappingData.context.toString());
        Assert.assertEquals("newContext-defaultWrapper2", mappingData.wrapper.toString());
    }

    @Test
    public void testReloadContextVersion() throws Exception {
        final String hostName = "iowejoiejfoiew";
        final int iowPos = 3;
        final String contextPath = "/foo/bar";
        final int contextPos = 2;

        MappingData mappingData = new MappingData();
        MessageBytes hostMB = MessageBytes.newInstance();
        MessageBytes uriMB = MessageBytes.newInstance();
        hostMB.setString(hostName);
        uriMB.setString("/foo/bar/blah/bobou/foo");

        // Verifying configuration created by setUp()
        Mapper.Host mappedHost = mapper.hosts[iowPos];
        Assert.assertEquals(hostName, mappedHost.name);
        Mapper.Context mappedContext = mappedHost.contextList.contexts[contextPos];
        Assert.assertEquals(contextPath, mappedContext.name);
        Assert.assertEquals(1, mappedContext.versions.length);
        Assert.assertEquals("0", mappedContext.versions[0].name);
        Object oldHost = mappedHost.object;
        Object oldContext = mappedContext.versions[0].object;
        Assert.assertEquals("context2", oldContext.toString());

        Object oldContext1 = mappedHost.contextList.contexts[contextPos - 1].versions[0].object;
        Assert.assertEquals("context1", oldContext1.toString());

        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host.toString());
        Assert.assertEquals("context2", mappingData.context.toString());
        Assert.assertEquals("wrapper5", mappingData.wrapper.toString());
        mappingData.recycle();
        mapperForContext2.map(uriMB, mappingData);
        Assert.assertEquals("wrapper5", mappingData.wrapper.toString());

        // Mark context as paused
        // This is what happens when context reload starts
        mapper.pauseContextVersion(oldContext, hostName, contextPath, "0");

        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host.toString());
        Assert.assertEquals("context2", mappingData.context.toString());
        // Wrapper is not mapped for incoming requests if context is paused
        Assert.assertNull(mappingData.wrapper);

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
                        "/", "newDefaultWrapper", false, false) }),
                false,
                false);

        mappedContext = mappedHost.contextList.contexts[contextPos];
        Assert.assertEquals(contextPath, mappedContext.name);
        Assert.assertEquals(1, mappedContext.versions.length);
        Assert.assertEquals("0", mappedContext.versions[0].name);

        mappingData.recycle();
        mapper.map(hostMB, uriMB, null, mappingData);
        Assert.assertEquals("blah7", mappingData.host.toString());
        Assert.assertEquals("context2", mappingData.context.toString());
        Assert.assertEquals("newDefaultWrapper", mappingData.wrapper.toString());
    }

    @Test
    @SuppressWarnings("deprecation") // contextPath
    public void testContextListConcurrencyBug56653() throws Exception {
        final Object host = new Object(); // "localhost";
        final Object contextRoot = new Object(); // "ROOT";
        final Object context1 = new Object(); // "foo";
        final Object context2 = new Object(); // "foo#bar";
        final Object context3 = new Object(); // "foo#bar#bla";
        final Object context4 = new Object(); // "foo#bar#bla#baz";

        mapper.addHost("localhost", new String[] { "alias" }, host);
        mapper.setDefaultHostName("localhost");

        mapper.addContextVersion("localhost", host, "", "0", contextRoot,
                new String[0], null, null, false, false);
        mapper.addContextVersion("localhost", host, "/foo", "0", context1,
                new String[0], null, null, false, false);
        mapper.addContextVersion("localhost", host, "/foo/bar", "0", context2,
                new String[0], null, null, false, false);
        mapper.addContextVersion("localhost", host, "/foo/bar/bla", "0",
                context3, new String[0], null, null, false, false);
        mapper.addContextVersion("localhost", host, "/foo/bar/bla/baz", "0",
                context4, new String[0], null, null, false, false);

        final AtomicBoolean running = new AtomicBoolean(true);
        Thread t = new Thread() {
            @Override
            public void run() {
                for (int i = 0; i < 100000; i++) {
                    mapper.removeContextVersion("localhost",
                            "/foo/bar/bla/baz", "0");
                    mapper.addContextVersion("localhost", host,
                            "/foo/bar/bla/baz", "0", context4, new String[0],
                            null, null, false, false);
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

    @Test
    public void testPerformance() throws Exception {
        // Takes ~1s on markt's laptop. If this takes more than 5s something
        // probably needs looking at. If this fails repeatedly then we may need
        // to increase this limit.
        final long maxTime = 5000;
        long time = testPerformanceImpl();
        if (time >= maxTime) {
            // Rerun to reject occasional failures, e.g. because of gc
            log.warn("testPerformance() test completed in " + time + " ms");
            time = testPerformanceImpl();
            log.warn("testPerformance() test rerun completed in " + time + " ms");
        }
        Assert.assertTrue(String.valueOf(time), time < maxTime);
    }

    private long testPerformanceImpl() throws Exception {
        MappingData mappingData = new MappingData();
        MessageBytes host = MessageBytes.newInstance();
        host.setString("iowejoiejfoiew");
        MessageBytes uri = MessageBytes.newInstance();
        uri.setString("/foo/bar/blah/bobou/foo");
        uri.toChars();
        uri.getCharChunk().setLimit(-1);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            mappingData.recycle();
            mapper.map(host, uri, null, mappingData);
        }
        long time = System.currentTimeMillis() - start;
        return time;
    }
}
