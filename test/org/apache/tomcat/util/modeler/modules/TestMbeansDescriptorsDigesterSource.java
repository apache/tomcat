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
package org.apache.tomcat.util.modeler.modules;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.modeler.Registry;

public class TestMbeansDescriptorsDigesterSource {

    private static final String XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<mbeans-descriptors>\n" +
                    "  <mbean name=\"TestDigesterSourceBean\" description=\"Tester bean\" domain=\"tester\">\n" +
                    "    <attribute name=\"value\" description=\"Tester value\" type=\"java.lang.String\"/>\n" +
                    "  </mbean>\n</mbeans-descriptors>\n";

    private static final String BAD_XML = "<mbeans-descriptors><invalid>";

    private static InputStream createTrackingStream(String content, AtomicBoolean closed) {
        InputStream delegate = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        return new FilterInputStream(delegate) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
    }

    @Test
    public void testLoadClosesStreamAfterSuccessfulParse() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        Registry registry = Registry.getRegistry(null);

        List<ObjectName> result = registry.load(null, createTrackingStream(XML, closed), null);

        // Descriptors are registered with the registry, no object names are
        // returned
        Assert.assertTrue(result.isEmpty());
        Assert.assertTrue(closed.get());
        Assert.assertNotNull(registry.findManagedBean("TestDigesterSourceBean"));
    }

    @Test
    public void testLoadClosesStreamAfterParseError() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        Registry registry = Registry.getRegistry(null);

        try {
            registry.load(null, createTrackingStream(BAD_XML, closed), null);
            Assert.fail("Expected a parse error");
        } catch (Exception e) {
            // Expected
        }

        Assert.assertTrue(closed.get());
    }

    @Test
    public void testExecuteRequiresInputStreamSource() throws Exception {
        Registry registry = Registry.getRegistry(null);
        ModelerSource source = new MbeansDescriptorsDigesterSource();

        try {
            source.loadDescriptors(registry, "tester", "not-an-input-stream");
            Assert.fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
}
