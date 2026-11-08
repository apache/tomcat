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
package org.apache.tomcat.util.descriptor.tagplugin;

import java.io.File;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.unittest.TesterServletContext;

/**
 * Test cases for {@link TagPluginParser}.
 */
public class TestTagPluginParser {

    @Test
    public void testAddPluginValid() {
        TagPluginParser parser = new TagPluginParser(new TesterServletContext(), true);

        parser.addPlugin("com.example.Tag", "com.example.Plugin");

        Map<String,String> plugins = parser.getPlugins();
        Assert.assertEquals(1, plugins.size());
        Assert.assertEquals("com.example.Plugin", plugins.get("com.example.Tag"));
    }


    @Test
    public void testAddPluginIncompleteIgnored() {
        TagPluginParser parser = new TagPluginParser(new TesterServletContext(), true);

        // Missing parameters arrive as null or empty values from the parser
        parser.addPlugin(null, "com.example.Plugin");
        parser.addPlugin("com.example.Tag", null);
        parser.addPlugin("", "com.example.Plugin");
        parser.addPlugin("com.example.Tag", "");

        Assert.assertTrue(parser.getPlugins().isEmpty());
    }


    /*
     * A <tag-plugin> element that omits <tag-class> or <plugin-class> results
     * in addPlugin() being called with a null parameter. Such incomplete
     * entries must be ignored rather than stored, while complete entries are
     * still registered.
     */
    @Test
    public void testParseMalformedDescriptor() throws Exception {
        TagPluginParser parser = new TagPluginParser(new TesterServletContext(), true);

        File file = new File("test/org/apache/tomcat/util/descriptor/"
                + "tagplugin/TestTagPluginParser-malformed.xml");
        parser.parse(file.toURI().toURL());

        Map<String,String> plugins = parser.getPlugins();
        Assert.assertEquals(1, plugins.size());
        Assert.assertFalse(plugins.containsKey(null));
        Assert.assertFalse(plugins.containsKey(""));
        Assert.assertFalse(plugins.containsValue(null));
        Assert.assertEquals("com.example.Plugin4",
                plugins.get("com.example.Tag4"));
    }
}
