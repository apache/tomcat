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
package org.apache.tomcat.util.descriptor.tld;

import java.io.FileInputStream;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.xml.sax.InputSource;

public class TestTldParser {
    private static final String WEBAPP = "test/webapp-3.1/WEB-INF/";
    private TldParser parser;

    @Before
    public void init() {
        parser = new TldParser(true, true);
    }

    @Test
    public void testParseTld21() throws Exception {
        try (FileInputStream is = new FileInputStream(WEBAPP + "tags21.tld")) {
            InputSource source = new InputSource(is);
            TaglibXml xml = parser.parse(source);
            Assert.assertEquals("1.0", xml.getTlibVersion());
            Assert.assertEquals("2.1", xml.getJspVersion());
            Assert.assertEquals("Tags21", xml.getShortName());
            Assert.assertEquals("http://tomcat.apache.org/tags21", xml.getUri());
            verifyTags(xml.getTags());
        }
    }

    @Test
    public void testParseTld20() throws Exception {
        try (FileInputStream is = new FileInputStream(WEBAPP + "tags20.tld")) {
            InputSource source = new InputSource(is);
            TaglibXml xml = parser.parse(source);
            Assert.assertEquals("1.0", xml.getTlibVersion());
            Assert.assertEquals("2.0", xml.getJspVersion());
            Assert.assertEquals("Tags20", xml.getShortName());
            Assert.assertEquals("http://tomcat.apache.org/tags20", xml.getUri());
            verifyTags(xml.getTags());
        }
    }

    @Test
    public void testParseTld12() throws Exception {
        try (FileInputStream is = new FileInputStream(WEBAPP + "tags12.tld")) {
            InputSource source = new InputSource(is);
            TaglibXml xml = parser.parse(source);
            Assert.assertEquals("1.0", xml.getTlibVersion());
            Assert.assertEquals("1.2", xml.getJspVersion());
            Assert.assertEquals("Tags12", xml.getShortName());
            Assert.assertEquals("http://tomcat.apache.org/tags12", xml.getUri());
            verifyTags(xml.getTags());
        }
    }

    @Test
    public void testParseTld11() throws Exception {
        try (FileInputStream is = new FileInputStream(WEBAPP + "tags11.tld")) {
            InputSource source = new InputSource(is);
            TaglibXml xml = parser.parse(source);
            Assert.assertEquals("1.0", xml.getTlibVersion());
            Assert.assertEquals("1.1", xml.getJspVersion());
            Assert.assertEquals("Tags11", xml.getShortName());
            Assert.assertEquals("http://tomcat.apache.org/tags11", xml.getUri());
            verifyTags(xml.getTags());
        }
    }

    private void verifyTags(List<Tag> tags) {
        Assert.assertEquals(1, tags.size());
        Tag tag = tags.get(0);
        Assert.assertEquals("Echo", tag.getName());
        Assert.assertEquals("org.apache.jasper.compiler.TestValidator$Echo",
                tag.getTagClass());
        Assert.assertEquals("empty", tag.getBodyContent());
    }

    @Test
    public void testListener() throws Exception {
        try (FileInputStream is = new FileInputStream("test/webapp-3.0/WEB-INF/listener.tld")) {
            InputSource source = new InputSource(is);
            TaglibXml xml = parser.parse(source);
            Assert.assertEquals("1.0", xml.getTlibVersion());
            List<String> listeners = xml.getListeners();
            Assert.assertEquals(1, listeners.size());
            Assert.assertEquals("org.apache.catalina.core.TesterTldListener", listeners.get(0));
        }
    }

}
