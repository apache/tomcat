/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.buildutil.translate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestUtils {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testFormatValue01() {
        // Import from POE
        Assert.assertEquals("line1\\n\\\nline2\\n\\\nline3", Utils.formatValueImport("line1\nline2\nline3"));
    }

    @Test
    public void testFormatValue02() {
        // Import from POE
        Assert.assertEquals("\\n\\\nline2\\n\\\nline3", Utils.formatValueImport(Utils.PADDING + "\nline2\nline3"));
    }

    @Test
    public void testFormatValue03() {
        // Import from POE
        Assert.assertEquals("line1\\n\\\n\\tline2\\n\\\n\\tline3", Utils.formatValueImport("line1\n\tline2\n\tline3"));
    }


    @Test
    public void testLoadValid() throws IOException {
        File f = temp.newFile("valid.properties");
        Files.write(f.toPath(), "key=caf\u00e9\n".getBytes(StandardCharsets.UTF_8));
        Properties props = Utils.load(f);
        Assert.assertEquals("caf\u00e9", props.getProperty("key"));
    }

    @Test
    public void testLoadInvalidUtf8() throws IOException {
        File f = temp.newFile("invalid-utf8.properties");
        Files.write(f.toPath(), new byte[] { 'k', '=', (byte) 0xC3, (byte) 0x28, '\n' });
        try {
            Utils.load(f);
            Assert.fail("Invalid UTF-8 was accepted");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("invalid-utf8.properties"));
        }
    }

    @Test
    public void testLoadMalformedEscape() throws IOException {
        File f = temp.newFile("bad-escape.properties");
        Files.write(f.toPath(), "key=\\u12\n".getBytes(StandardCharsets.UTF_8));
        try {
            Utils.load(f);
            Assert.fail("The malformed escape was accepted");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("bad-escape.properties"));
            Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }
}
