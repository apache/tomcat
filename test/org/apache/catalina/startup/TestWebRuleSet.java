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
package org.apache.catalina.startup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;

import org.apache.catalina.deploy.WebXml;
import org.apache.tomcat.util.digester.Digester;
import org.junit.Test;

public class TestWebRuleSet {

    private Digester fragmentDigester = new Digester();
    private WebRuleSet fragmentRuleSet = new WebRuleSet(true);

    private Digester webDigester = new Digester();
    private WebRuleSet webRuleSet = new WebRuleSet(false);

    public TestWebRuleSet() {
        fragmentDigester.addRuleSet(fragmentRuleSet);
        webDigester.addRuleSet(webRuleSet);
    }


    @Test
    public void testSingleNameInWebFragmentXml() throws Exception {

        WebXml webXml = new WebXml();

        assertTrue(parse(webXml, "web-fragment-1name.xml", true));
        assertEquals("name1", webXml.getName());
    }


    @Test
    public void testMultipleNameInWebFragmentXml() throws Exception {
        assertFalse(parse(new WebXml(), "web-fragment-2name.xml", true));
    }


    @Test
    public void testSingleOrderingInWebFragmentXml() throws Exception {

        WebXml webXml = new WebXml();

        assertTrue(parse(webXml, "web-fragment-1ordering.xml", true));
        assertEquals(1, webXml.getBeforeOrdering().size());
        assertTrue(webXml.getBeforeOrdering().contains("bar"));
    }


    @Test
    public void testMultipleOrderingInWebFragmentXml() throws Exception {
        assertFalse(parse(new WebXml(), "web-fragment-2ordering.xml", true));
    }


    @Test
    public void testSingleOrderingInWebXml() throws Exception {

        WebXml webXml = new WebXml();

        assertTrue(parse(webXml, "web-1ordering.xml", false));
        assertEquals(1, webXml.getAbsoluteOrdering().size());
        assertTrue(webXml.getAbsoluteOrdering().contains("bar"));
    }


    @Test
    public void testMultipleOrderingInWebXml() throws Exception {
        assertFalse(parse(new WebXml(), "web-2ordering.xml", false));
    }


    @Test
    public void testRecycle() throws Exception {
        // Name
        assertFalse(parse(new WebXml(), "web-fragment-2name.xml", true));
        assertTrue(parse(new WebXml(), "web-fragment-1name.xml", true));
        assertFalse(parse(new WebXml(), "web-fragment-2name.xml", true));
        assertTrue(parse(new WebXml(), "web-fragment-1name.xml", true));

        // Relative ordering
        assertFalse(parse(new WebXml(), "web-fragment-2ordering.xml", true));
        assertTrue(parse(new WebXml(), "web-fragment-1ordering.xml", true));
        assertFalse(parse(new WebXml(), "web-fragment-2ordering.xml", true));
        assertTrue(parse(new WebXml(), "web-fragment-1ordering.xml", true));

        // Absolute ordering
        assertFalse(parse(new WebXml(), "web-2ordering.xml", false));
        assertTrue(parse(new WebXml(), "web-1ordering.xml", false));
        assertFalse(parse(new WebXml(), "web-2ordering.xml", false));
        assertTrue(parse(new WebXml(), "web-1ordering.xml", false));
}


    private synchronized boolean parse(WebXml webXml, String target,
            boolean fragment) {

        Digester d;
        if (fragment) {
            d = fragmentDigester;
            fragmentRuleSet.recycle();
        } else {
            d = webDigester;
            webRuleSet.recycle();
        }

        d.push(webXml);

        InputStream is = this.getClass().getClassLoader().getResourceAsStream(
                "org/apache/catalina/startup/" + target);

        boolean result = true;

        try {
            d.parse(is);
        } catch (Exception e) {
            result = false;
        }

        return result;
    }
}
