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
package org.apache.catalina.filters;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;

import java.io.File;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class TestRangedPutFilter extends TomcatBaseTest {

    private File tempDocBase;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        tempDocBase = Files.createTempDirectory(getTemporaryDirectory().toPath(), "put").toFile();
    }

    @Test
    public void testConfiguration() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", tempDocBase.getAbsolutePath());

        FilterDef filterDef = new FilterDef();
        long maxSize = 1024L;
        filterDef.addInitParameter("maxSize", String.valueOf(maxSize));

        filterDef.setFilter(new RangedPutRequestBoundsFilter());
        filterDef.setFilterClass(RangedPutRequestBoundsFilter.class.getName());
        filterDef.setFilterName("rangedPutFilter01");
        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("rangedPutFilter01");
        filterMap.addURLPatternDecoded("*");
        root.addFilterMap(filterMap);

        tomcat.start();

        RangedPutRequestBoundsFilter filter = (RangedPutRequestBoundsFilter) filterDef.getFilter();
        try {
            Assert.assertTrue(filter.getMaxSize() != -1);
            Assert.assertEquals(maxSize, filter.getMaxSize());
        } finally {
            tomcat.stop();
        }
    }

    @Test
    public void testConfigurationInvalidParameter() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", tempDocBase.getAbsolutePath());

        FilterDef filterDef = new FilterDef();
        long maxSize = -2;
        filterDef.addInitParameter("maxSize", String.valueOf(maxSize));

        filterDef.setFilterClass(RangedPutRequestBoundsFilter.class.getName());
        filterDef.setFilterName("rangedPutFilter01");
        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("rangedPutFilter01");
        filterMap.addURLPatternDecoded("*");
        root.addFilterMap(filterMap);

        try {
            tomcat.start();
            Assert.assertEquals("Context should be stopped due to filter configuration exception", LifecycleState.STOPPED, root.getState());
        } finally {
            tomcat.stop();
        }
    }


    @Test
    public void testConfigurationNoSuchParameter() throws Exception {

        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", tempDocBase.getAbsolutePath());

        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("maxPostSize", "1024");

        filterDef.setFilterClass(RangedPutRequestBoundsFilter.class.getName());
        filterDef.setFilterName("rangedPutFilter01");
        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("rangedPutFilter01");
        filterMap.addURLPatternDecoded("*");
        root.addFilterMap(filterMap);

        try {
            tomcat.start();
            Assert.assertEquals("Context should be stopped due to filter configuration exception", LifecycleState.STOPPED, root.getState());
        } finally {
            tomcat.stop();
        }
    }

    @Test
    public void testRangePutTooBig() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", tempDocBase.getAbsolutePath());

        FilterDef filterDef = new FilterDef();
        int maxSize = 1024;
        filterDef.addInitParameter("maxSize", String.valueOf(maxSize));

        filterDef.setFilterClass(RangedPutRequestBoundsFilter.class.getName());
        filterDef.setFilterName("rangedPutFilter01");
        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("rangedPutFilter01");
        filterMap.addURLPatternDecoded("*");
        root.addFilterMap(filterMap);

        Wrapper w = Tomcat.addServlet(root, "default", DefaultServlet.class.getName());
        w.addInitParameter("readonly", "false");
        w.addInitParameter("allowPartialPut", Boolean.toString(true));
        root.addServletMappingDecoded("/", "default");

        tomcat.start();


        String text = "a".repeat(maxSize+1);

        // Full PUT
        SimpleHttpClient putClient = new SimpleHttpClient() {

            @Override
            public boolean isResponseBodyOK() {
                // TODO Auto-generated method stub
                return false;
            }
        };
        putClient.setPort(getPort());

        putClient.setRequest(new String[] {
                "PUT /test.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + text.length() + CRLF +
                CRLF +
                text
        });
        putClient.connect();
        putClient.processRequest(false);
        Assert.assertTrue(putClient.isResponse201());
        putClient.disconnect();

        putClient.reset();

        // Partial PUT
        putClient.connect();
        putClient.setRequest(new String[] {
                "PUT /test.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Range: bytes 0-0/" +text.length()+CRLF+
                "Content-Length: " + 1 + CRLF +
                CRLF +
                "1"
        });
        putClient.processRequest(false);
        Assert.assertTrue(putClient.isResponse413());
        putClient.disconnect();
        putClient.reset();


        // Check for the final resource
        String path = "http://localhost:" + getPort() + "/test.txt";
        ByteChunk responseBody = new ByteChunk();

        int rc = getUrl(path, responseBody, null);

        Assert.assertEquals(200,  rc);
        Assert.assertEquals(text, responseBody.toString());
        tomcat.stop();
    }

    @Test
    public void testRangePutUnlimited() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", tempDocBase.getAbsolutePath());

        FilterDef filterDef = new FilterDef();
        int maxSize = -1;
        filterDef.addInitParameter("maxSize", String.valueOf(maxSize));

        filterDef.setFilterClass(RangedPutRequestBoundsFilter.class.getName());
        filterDef.setFilterName("rangedPutFilter01");
        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("rangedPutFilter01");
        filterMap.addURLPatternDecoded("*");
        root.addFilterMap(filterMap);

        Wrapper w = Tomcat.addServlet(root, "default", DefaultServlet.class.getName());
        w.addInitParameter("readonly", "false");
        w.addInitParameter("allowPartialPut", Boolean.toString(true));
        root.addServletMappingDecoded("/", "default");

        tomcat.start();

        String text = "a".repeat(1024);
        // Full PUT
        SimpleHttpClient putClient = new SimpleHttpClient() {

            @Override
            public boolean isResponseBodyOK() {
                // TODO Auto-generated method stub
                return false;
            }
        };
        putClient.setPort(getPort());

        putClient.setRequest(new String[] {
                "PUT /test.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + text.length() + CRLF +
                CRLF +
                text
        });
        putClient.connect();
        putClient.processRequest(false);
        Assert.assertTrue(putClient.isResponse201());
        putClient.disconnect();
        putClient.reset();

        // Partial PUT
        putClient.connect();
        putClient.setRequest(new String[] {
                "PUT /test.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Range: bytes 10241024-10241024/10241025"+CRLF+
                "Content-Length: " + 1 + CRLF +
                CRLF +
                "a"
        });
        putClient.processRequest(false);
        Assert.assertTrue(putClient.isResponse204());
        putClient.disconnect();
        putClient.reset();


        // Check for the final resource
        String path = "http://localhost:" + getPort() + "/test.txt";
        ByteChunk responseBody = new ByteChunk();

        int rc = getUrl(path, responseBody, null);

        Assert.assertEquals(200,  rc);
        Assert.assertEquals(10241025, responseBody.getLength());
        tomcat.stop();
    }

    @Test
    public void testRangePutLimited() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", tempDocBase.getAbsolutePath());

        FilterDef filterDef = new FilterDef();
        int maxSize = 1025;
        filterDef.addInitParameter("maxSize", String.valueOf(maxSize));

        filterDef.setFilterClass(RangedPutRequestBoundsFilter.class.getName());
        filterDef.setFilterName("rangedPutFilter01");
        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("rangedPutFilter01");
        filterMap.addURLPatternDecoded("*");
        root.addFilterMap(filterMap);

        Wrapper w = Tomcat.addServlet(root, "default", DefaultServlet.class.getName());
        w.addInitParameter("readonly", "false");
        w.addInitParameter("allowPartialPut", Boolean.toString(true));
        root.addServletMappingDecoded("/", "default");

        tomcat.start();

        String text = "a".repeat(maxSize-1);
        // Full PUT
        SimpleHttpClient putClient = new SimpleHttpClient() {

            @Override
            public boolean isResponseBodyOK() {
                // TODO Auto-generated method stub
                return false;
            }
        };
        putClient.setPort(getPort());

        putClient.setRequest(new String[] {
                "PUT /test.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Length: " + text.length() + CRLF +
                CRLF +
                text
        });
        putClient.connect();
        putClient.processRequest(false);
        Assert.assertTrue(putClient.isResponse201());
        putClient.disconnect();
        putClient.reset();

        // Partial PUT
        putClient.connect();
        putClient.setRequest(new String[] {
                "PUT /test.txt HTTP/1.1" + CRLF +
                "Host: localhost:" + getPort() + CRLF +
                "Content-Range: bytes "+text.length()+"-"+text.length()+"/"+(text.length()+1)+CRLF+
                "Content-Length: " + 1 + CRLF +
                CRLF +
                "a"
        });
        putClient.processRequest(false);
        Assert.assertTrue(putClient.isResponse204());
        putClient.disconnect();
        putClient.reset();


        // Check for the final resource
        String path = "http://localhost:" + getPort() + "/test.txt";
        ByteChunk responseBody = new ByteChunk();

        int rc = getUrl(path, responseBody, null);

        Assert.assertEquals(200,  rc);
        Assert.assertEquals(text+"a", responseBody.toString());
        tomcat.stop();
    }
}
