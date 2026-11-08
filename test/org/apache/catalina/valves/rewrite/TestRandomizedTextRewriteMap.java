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
package org.apache.catalina.valves.rewrite;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class TestRandomizedTextRewriteMap {

    private String createMapFile(String content) throws IOException {
        File file = File.createTempFile("rewrite-map", ".txt");
        file.deleteOnExit();
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        return file.getAbsolutePath();
    }

    @Test
    public void testSingleSpaceSeparation() throws Exception {
        RandomizedTextRewriteMap map = new RandomizedTextRewriteMap(createMapFile("key value\n"), false);
        Assert.assertEquals("value", map.lookup("key"));
    }

    @Test
    public void testTabSeparation() throws Exception {
        // mod_rewrite separates key and value by any whitespace
        RandomizedTextRewriteMap map = new RandomizedTextRewriteMap(createMapFile("key\tvalue\n"), false);
        Assert.assertEquals("value", map.lookup("key"));
    }

    @Test
    public void testMultipleWhitespaceSeparation() throws Exception {
        // A single space was required before, which silently added the extra
        // whitespace to the start of the value
        RandomizedTextRewriteMap map = new RandomizedTextRewriteMap(createMapFile("key  \tvalue\n"), false);
        Assert.assertEquals("value", map.lookup("key"));
    }

    @Test
    public void testWhitespaceAroundEntry() throws Exception {
        RandomizedTextRewriteMap map = new RandomizedTextRewriteMap(createMapFile("  key value  \n"), false);
        Assert.assertEquals("value", map.lookup("key"));
    }

    @Test
    public void testIndentedCommentLine() throws Exception {
        // An indentation before the comment marker used to make the line
        // look like an entry without a value and fail the map start up
        RandomizedTextRewriteMap map = new RandomizedTextRewriteMap(createMapFile("  # comment\nkey value\n"), false);
        Assert.assertEquals("value", map.lookup("key"));
    }

    @Test
    public void testCommentAfterValue() throws Exception {
        RandomizedTextRewriteMap map = new RandomizedTextRewriteMap(createMapFile("key value # comment\n"), false);
        Assert.assertEquals("value", map.lookup("key"));
    }

    @Test
    public void testValueMayContainWhitespace() throws Exception {
        RandomizedTextRewriteMap map = new RandomizedTextRewriteMap(createMapFile("key one two\n"), false);
        Assert.assertEquals("one two", map.lookup("key"));
    }

    @Test
    public void testRandomValues() throws Exception {
        RandomizedTextRewriteMap map = new RandomizedTextRewriteMap(createMapFile("key one|two\n"), true);
        Set<String> possible = new HashSet<>();
        Set<String> seen = new HashSet<>();
        possible.add("one");
        possible.add("two");
        for (int i = 0; i < 100; i++) {
            String value = map.lookup("key");
            Assert.assertTrue(possible.contains(value));
            seen.add(value);
        }
        // Both values must show up. The probability of one of the two values
        // never being picked in 100 draws is below 10^-30.
        Assert.assertEquals(possible, seen);
    }

    @Test
    public void testUnknownKeyResolvesToNull() throws Exception {
        RandomizedTextRewriteMap map = new RandomizedTextRewriteMap(createMapFile("key value\n"), false);
        Assert.assertNull(map.lookup("other"));
    }

    @Test
    public void testLineWithoutValueThrows() throws Exception {
        try {
            @SuppressWarnings("unused")
            RandomizedTextRewriteMap map = new RandomizedTextRewriteMap(createMapFile("key\n"), false);
            Assert.fail("IAE expected.");
        } catch (IllegalArgumentException e) {
            // expected, an entry without a value is invalid
        }
    }
}
