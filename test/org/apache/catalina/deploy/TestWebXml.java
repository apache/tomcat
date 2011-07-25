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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Test case for {@link WebXml}.
 */
public class TestWebXml {

    @Test
    public void testParseVersion() {
        
        WebXml webxml = new WebXml();
        
        // Defaults
        assertEquals(3, webxml.getMajorVersion());
        assertEquals(0, webxml.getMinorVersion());
        
        // Both get changed
        webxml.setVersion("2.5");
        assertEquals(2, webxml.getMajorVersion());
        assertEquals(5, webxml.getMinorVersion());
        
        // Reset
        webxml.setVersion("0.0");
        assertEquals(0, webxml.getMajorVersion());
        assertEquals(0, webxml.getMinorVersion());
        
        // null input should be ignored
        webxml.setVersion(null);
        assertEquals(0, webxml.getMajorVersion());
        assertEquals(0, webxml.getMinorVersion());
        
        // major only
        webxml.setVersion("3");
        assertEquals(3, webxml.getMajorVersion());
        assertEquals(0, webxml.getMinorVersion());
        
        // no minor digit
        webxml.setVersion("0.0");   // reset
        webxml.setVersion("3.");
        assertEquals(3, webxml.getMajorVersion());
        assertEquals(0, webxml.getMinorVersion());
        
        // minor only
        webxml.setVersion("0.0");   // reset
        webxml.setVersion(".5");
        assertEquals(0, webxml.getMajorVersion());
        assertEquals(5, webxml.getMinorVersion());
        
        // leading & training zeros
        webxml.setVersion("0.0");   // reset
        webxml.setVersion("002.500");
        assertEquals(2, webxml.getMajorVersion());
        assertEquals(500, webxml.getMinorVersion());
    }

    @Test
    public void testParsePublicIdVersion22() {
        
        WebXml webxml = new WebXml();
        
        webxml.setPublicId(
                org.apache.catalina.startup.Constants.WebDtdPublicId_22);
        assertEquals(2, webxml.getMajorVersion());
        assertEquals(2, webxml.getMinorVersion());
        assertEquals("2.2", webxml.getVersion());
    }

    @Test
    public void testParsePublicIdVersion23() {
        
        WebXml webxml = new WebXml();
        
        webxml.setPublicId(
                org.apache.catalina.startup.Constants.WebDtdPublicId_23);
        assertEquals(2, webxml.getMajorVersion());
        assertEquals(3, webxml.getMinorVersion());
        assertEquals("2.3", webxml.getVersion());
    }

    @Test
    public void testParsePublicIdVersion24() {
        
        WebXml webxml = new WebXml();
        
        webxml.setPublicId(
                org.apache.catalina.startup.Constants.WebSchemaPublicId_24);
        assertEquals(2, webxml.getMajorVersion());
        assertEquals(4, webxml.getMinorVersion());
        assertEquals("2.4", webxml.getVersion());
    }

    @Test
    public void testParsePublicIdVersion25() {
        
        WebXml webxml = new WebXml();
        
        webxml.setPublicId(
                org.apache.catalina.startup.Constants.WebSchemaPublicId_25);
        assertEquals(2, webxml.getMajorVersion());
        assertEquals(5, webxml.getMinorVersion());
        assertEquals("2.5", webxml.getVersion());
    }

    @Test
    public void testParsePublicIdVersion30() {
        
        WebXml webxml = new WebXml();
        
        webxml.setPublicId(
                org.apache.catalina.startup.Constants.WebSchemaPublicId_30);
        assertEquals(3, webxml.getMajorVersion());
        assertEquals(0, webxml.getMinorVersion());
        assertEquals("3.0", webxml.getVersion());
    }
}
