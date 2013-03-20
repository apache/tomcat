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

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    private Mapper mapper;

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
        mapper.addHost("iowejoiejfoiew", new String[0], createHost("blah17"));
        mapper.addHost("ohewoihfewoih", new String[0], createHost("blah8"));
        mapper.addHost("fewohfoweoih", new String[0], createHost("blah9"));
        mapper.addHost("ttthtiuhwoih", new String[0], createHost("blah10"));
        mapper.addHost("lkwefjwojweffewoih", new String[0], createHost("blah11"));
        mapper.addHost("zzzuyopjvewpovewjhfewoih", new String[0], createHost("blah12"));
        mapper.addHost("xxxxgqwiwoih", new String[0], createHost("blah13"));
        mapper.addHost("qwigqwiwoih", new String[0], createHost("blah14"));
        mapper.addHostAlias("iowejoiejfoiew", "iowejoiejfoiew_alias");

        mapper.setDefaultHostName("ylwrehirkuewh");

        String[] welcomes = new String[2];
        welcomes[0] = "boo/baba";
        welcomes[1] = "bobou";

        Host host = createHost("blah7");
        mapper.addContextVersion("iowejoiejfoiew", host, "",
                "0", createContext("context0"), new String[0], null);
        mapper.addContextVersion("iowejoiejfoiew", host, "/foo",
                "0", createContext("context1"), new String[0], null);
        mapper.addContextVersion("iowejoiejfoiew", host, "/foo/bar",
                "0", createContext("context2"), welcomes, null);
        mapper.addContextVersion("iowejoiejfoiew", host, "/foo/bar/bla",
                "0", createContext("context3"), new String[0], null);

        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "0", "/fo/*",
                createWrapper("wrapper0"), false, false);
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "0", "/",
                createWrapper("wrapper1"), false, false);
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "0", "/blh",
                createWrapper("wrapper2"), false, false);
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "0", "*.jsp",
                createWrapper("wrapper3"), false, false);
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "0", "/blah/bou/*",
                createWrapper("wrapper4"), false, false);
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "0", "/blah/bobou/*",
                createWrapper("wrapper5"), false, false);
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar", "0", "*.htm",
                createWrapper("wrapper6"), false, false);
        mapper.addWrapper("iowejoiejfoiew", "/foo/bar/bla", "0", "/bobou/*",
                createWrapper("wrapper7"), false, false);
    }

    @Test
    public void testAddHost() throws Exception {
        // Check we have the right number
        // (added 17 including one host alias but one is a duplicate)
        assertEquals(16, mapper.hosts.length);

        // Make sure adding a duplicate *does not* overwrite
        final int iowPos = 3;
        assertEquals("blah7", mapper.hosts[iowPos].object.getName());

        // Check for alphabetical order of host names
        String previous;
        String current = mapper.hosts[0].name;
        for (int i = 1; i < mapper.hosts.length; i++) {
            previous = current;
            current = mapper.hosts[i].name;
            assertTrue(previous.compareTo(current) < 0);
        }

        // Check that host alias has the same data
        Mapper.MappedHost host = mapper.hosts[iowPos];
        Mapper.MappedHost alias = mapper.hosts[iowPos + 1];
        assertEquals("iowejoiejfoiew", host.name);
        assertEquals("iowejoiejfoiew_alias", alias.name);
        assertEquals(host.contextList, alias.contextList);
        assertEquals(host.object, alias.object);
    }

    @Test
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
        assertEquals("blah7", mappingData.host.getName());
        assertEquals("context2", mappingData.context.getName());
        assertEquals("wrapper5", mappingData.wrapper.getName());
        assertEquals("/foo/bar", mappingData.contextPath.toString());
        assertEquals("/blah/bobou", mappingData.wrapperPath.toString());
        assertEquals("/foo", mappingData.pathInfo.toString());
        assertTrue(mappingData.redirectPath.isNull());

        mappingData.recycle();
        uri.recycle();
        uri.setString("/foo/bar/bla/bobou/foo");
        uri.toChars();
        uri.getCharChunk().setLimit(-1);
        mapper.map(host, uri, null, mappingData);
        assertEquals("blah7", mappingData.host.getName());
        assertEquals("context3", mappingData.context.getName());
        assertEquals("wrapper7", mappingData.wrapper.getName());
        assertEquals("/foo/bar/bla", mappingData.contextPath.toString());
        assertEquals("/bobou", mappingData.wrapperPath.toString());
        assertEquals("/foo", mappingData.pathInfo.toString());
        assertTrue(mappingData.redirectPath.isNull());

        mappingData.recycle();
        uri.setString("/foo/bar/bla/bobou/foo");
        uri.toChars();
        uri.getCharChunk().setLimit(-1);
        mapper.map(alias, uri, null, mappingData);
        assertEquals("blah7", mappingData.host.getName());
        assertEquals("context3", mappingData.context.getName());
        assertEquals("wrapper7", mappingData.wrapper.getName());
        assertEquals("/foo/bar/bla", mappingData.contextPath.toString());
        assertEquals("/bobou", mappingData.wrapperPath.toString());
        assertEquals("/foo", mappingData.pathInfo.toString());
        assertTrue(mappingData.redirectPath.isNull());
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
        assertTrue(String.valueOf(time), time < maxTime);
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
