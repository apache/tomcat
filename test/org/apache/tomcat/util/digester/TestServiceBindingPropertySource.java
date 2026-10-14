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
package org.apache.tomcat.util.digester;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestServiceBindingPropertySource {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();


    private File writeBinding(String name, String key, String content) throws Exception {
        File bindingFolder = new File(temporaryFolder.getRoot(), name);
        Assert.assertTrue(bindingFolder.mkdirs() || bindingFolder.isDirectory());
        File file = new File(bindingFolder, key);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }


    @Test
    public void testPropertyValueIsDecodedAsUtf8() throws Exception {
        String value = "p\u00e4ssw\u00f6rd-\u00fcnicode-\u2603";
        writeBinding("my-binding", "keyPassword", value);
        writeBinding("my-binding", "withNewline", value + "\n");

        ServiceBindingPropertySource source =
                new ServiceBindingPropertySource(temporaryFolder.getRoot().getAbsolutePath());

        Assert.assertEquals(value, source.getProperty("my-binding.keyPassword"));
        Assert.assertEquals(value, source.getProperty("chomp:my-binding.withNewline"));
    }


    @Test
    public void testChompTrimsSingleTrailingLineBreak() throws Exception {
        writeBinding("my-binding", "lf", "value\n");
        writeBinding("my-binding", "crlf", "value\r\n");
        writeBinding("my-binding", "cr", "value\r");
        writeBinding("my-binding", "none", "value");
        writeBinding("my-binding", "blankline", "value\n\n");
        writeBinding("my-binding", "trailingspace", "value \n");

        ServiceBindingPropertySource source =
                new ServiceBindingPropertySource(temporaryFolder.getRoot().getAbsolutePath());

        Assert.assertEquals("value", source.getProperty("chomp:my-binding.lf"));
        Assert.assertEquals("value", source.getProperty("chomp:my-binding.crlf"));
        Assert.assertEquals("value", source.getProperty("chomp:my-binding.cr"));
        Assert.assertEquals("value", source.getProperty("chomp:my-binding.none"));
        Assert.assertEquals("value\n", source.getProperty("chomp:my-binding.blankline"));
        Assert.assertEquals("value ", source.getProperty("chomp:my-binding.trailingspace"));
    }


    @Test
    public void testUnknownBindingsResolveToNull() throws Exception {
        writeBinding("my-binding", "known", "value");

        ServiceBindingPropertySource source =
                new ServiceBindingPropertySource(temporaryFolder.getRoot().getAbsolutePath());

        Assert.assertNull(source.getProperty("my-binding.unknown"));
        Assert.assertNull(source.getProperty("unknown-binding.known"));
        // A key without exactly one dot is outside the <binding-name>.<key> contract
        Assert.assertNull(source.getProperty("no-dot"));
        Assert.assertNull(source.getProperty("too.many.dots"));
    }
}
