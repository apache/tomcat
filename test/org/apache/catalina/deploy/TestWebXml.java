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

package org.apache.catalina.deploy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test case for {@link WebXml}.
 */
public class TestWebXml {

    @Test
    public void testParseVersion() {

        WebXml webxml = new WebXml();

        // Defaults
        Assert.assertEquals(3, webxml.getMajorVersion());
        Assert.assertEquals(0, webxml.getMinorVersion());

        // Both get changed
        webxml.setVersion("2.5");
        Assert.assertEquals(2, webxml.getMajorVersion());
        Assert.assertEquals(5, webxml.getMinorVersion());

        // Reset
        webxml.setVersion("0.0");
        Assert.assertEquals(0, webxml.getMajorVersion());
        Assert.assertEquals(0, webxml.getMinorVersion());

        // null input should be ignored
        webxml.setVersion(null);
        Assert.assertEquals(0, webxml.getMajorVersion());
        Assert.assertEquals(0, webxml.getMinorVersion());

        // major only
        webxml.setVersion("3");
        Assert.assertEquals(3, webxml.getMajorVersion());
        Assert.assertEquals(0, webxml.getMinorVersion());

        // no minor digit
        webxml.setVersion("0.0");   // reset
        webxml.setVersion("3.");
        Assert.assertEquals(3, webxml.getMajorVersion());
        Assert.assertEquals(0, webxml.getMinorVersion());

        // minor only
        webxml.setVersion("0.0");   // reset
        webxml.setVersion(".5");
        Assert.assertEquals(0, webxml.getMajorVersion());
        Assert.assertEquals(5, webxml.getMinorVersion());

        // leading & training zeros
        webxml.setVersion("0.0");   // reset
        webxml.setVersion("002.500");
        Assert.assertEquals(2, webxml.getMajorVersion());
        Assert.assertEquals(500, webxml.getMinorVersion());
    }

    @Test
    public void testParsePublicIdVersion22() {

        WebXml webxml = new WebXml();

        webxml.setPublicId(
                org.apache.catalina.startup.Constants.WebDtdPublicId_22);
        Assert.assertEquals(2, webxml.getMajorVersion());
        Assert.assertEquals(2, webxml.getMinorVersion());
        Assert.assertEquals("2.2", webxml.getVersion());
    }

    @Test
    public void testParsePublicIdVersion23() {

        WebXml webxml = new WebXml();

        webxml.setPublicId(
                org.apache.catalina.startup.Constants.WebDtdPublicId_23);
        Assert.assertEquals(2, webxml.getMajorVersion());
        Assert.assertEquals(3, webxml.getMinorVersion());
        Assert.assertEquals("2.3", webxml.getVersion());
    }

    @Test
    public void testParsePublicIdVersion24() {

        WebXml webxml = new WebXml();

        webxml.setPublicId(
                org.apache.catalina.startup.Constants.WebSchemaPublicId_24);
        Assert.assertEquals(2, webxml.getMajorVersion());
        Assert.assertEquals(4, webxml.getMinorVersion());
        Assert.assertEquals("2.4", webxml.getVersion());
    }

    @Test
    public void testParsePublicIdVersion25() {

        WebXml webxml = new WebXml();

        webxml.setPublicId(
                org.apache.catalina.startup.Constants.WebSchemaPublicId_25);
        Assert.assertEquals(2, webxml.getMajorVersion());
        Assert.assertEquals(5, webxml.getMinorVersion());
        Assert.assertEquals("2.5", webxml.getVersion());
    }

    @Test
    public void testParsePublicIdVersion30() {

        WebXml webxml = new WebXml();

        webxml.setPublicId(
                org.apache.catalina.startup.Constants.WebSchemaPublicId_30);
        Assert.assertEquals(3, webxml.getMajorVersion());
        Assert.assertEquals(0, webxml.getMinorVersion());
        Assert.assertEquals("3.0", webxml.getVersion());
    }

    @Test
    public void testLifecycleMethodsWebXml() {
        WebXml webxml = new WebXml();
        webxml.addPostConstructMethods("a", "a");
        webxml.addPreDestroyMethods("b", "b");

        WebXml fragment = new WebXml();
        fragment.addPostConstructMethods("c", "c");
        fragment.addPreDestroyMethods("d", "d");

        Set<WebXml> fragments = new HashSet<WebXml>();
        fragments.add(fragment);

        webxml.merge(fragments);

        Map<String, String> postConstructMethods = webxml.getPostConstructMethods();
        Map<String, String> preDestroyMethods = webxml.getPreDestroyMethods();
        Assert.assertEquals(1, postConstructMethods.size());
        Assert.assertEquals(1, preDestroyMethods.size());

        Assert.assertEquals("a", postConstructMethods.get("a"));
        Assert.assertEquals("b", preDestroyMethods.get("b"));
    }

    @Test
    public void testLifecycleMethodsWebFragments() {
        WebXml webxml = new WebXml();

        WebXml fragment1 = new WebXml();
        fragment1.addPostConstructMethods("a", "a");
        fragment1.addPreDestroyMethods("b", "b");

        WebXml fragment2 = new WebXml();
        fragment2.addPostConstructMethods("c", "c");
        fragment2.addPreDestroyMethods("d", "d");

        Set<WebXml> fragments = new HashSet<WebXml>();
        fragments.add(fragment1);
        fragments.add(fragment2);

        webxml.merge(fragments);

        Map<String, String> postConstructMethods = webxml.getPostConstructMethods();
        Map<String, String> preDestroyMethods = webxml.getPreDestroyMethods();
        Assert.assertEquals(2, postConstructMethods.size());
        Assert.assertEquals(2, preDestroyMethods.size());

        Assert.assertEquals("a", postConstructMethods.get("a"));
        Assert.assertEquals("c", postConstructMethods.get("c"));
        Assert.assertEquals("b", preDestroyMethods.get("b"));
        Assert.assertEquals("d", preDestroyMethods.get("d"));
    }

    @Test
    public void testLifecycleMethodsWebFragmentsWithConflicts() {
        WebXml webxml = new WebXml();

        WebXml fragment1 = new WebXml();
        fragment1.addPostConstructMethods("a", "a");
        fragment1.addPreDestroyMethods("b", "a");

        WebXml fragment2 = new WebXml();
        fragment2.addPostConstructMethods("a", "b");

        Set<WebXml> fragments = new HashSet<WebXml>();
        fragments.add(fragment1);
        fragments.add(fragment2);

        Assert.assertFalse(webxml.merge(fragments));

        Assert.assertEquals(0, webxml.getPostConstructMethods().size());

        WebXml fragment3 = new WebXml();
        fragment3.addPreDestroyMethods("b", "b");

        fragments.remove(fragment2);
        fragments.add(fragment3);

        Assert.assertFalse(webxml.merge(fragments));

        Assert.assertEquals(0, webxml.getPreDestroyMethods().size());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testBug54387a() {
        // Multiple servlets may not be mapped to the same url-pattern
        WebXml webxml = new WebXml();
        webxml.addServletMapping("/foo", "a");
        webxml.addServletMapping("/foo", "b");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testBug54387b() {
        // Multiple servlets may not be mapped to the same url-pattern
        WebXml webxml = new WebXml();
        WebXml f1 = new WebXml();
        WebXml f2 = new WebXml();

        HashSet<WebXml> fragments = new HashSet<WebXml>();
        fragments.add(f1);
        fragments.add(f2);

        f1.addServletMapping("/foo", "a");
        f2.addServletMapping("/foo", "b");

        webxml.merge(fragments);
    }

    @Test
    public void testBug54387c() {
        // Multiple servlets may not be mapped to the same url-pattern but main
        // web.xml takes priority
        WebXml webxml = new WebXml();
        WebXml f1 = new WebXml();
        WebXml f2 = new WebXml();

        HashSet<WebXml> fragments = new HashSet<WebXml>();
        fragments.add(f1);
        fragments.add(f2);

        f1.addServletMapping("/foo", "a");
        f2.addServletMapping("/foo", "b");
        webxml.addServletMapping("/foo", "main");

        webxml.merge(fragments);
    }
}
