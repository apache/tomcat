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
package org.apache.coyote.http11.filters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

import org.junit.Test;

import org.apache.catalina.util.IOTools;

/**
 * Reproduces what current appears to be a JVM bug. Note: This test case is not
 * part of the Standard test suite that is execute by <code>ant test</code>.
 */
public class TestFlushableGZIPOutputStream {

    @Test
    public void testBug52121() throws Exception {

        ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        OutputStream output = new FlushableGZIPOutputStream(byteOutStream);

        File sourcesDir = new File("test/org/apache/coyote/http11/filters/");
        InputStream input;

        input = new FileInputStream(new File(sourcesDir, "bug52121-part1"));
        try {
            IOTools.flow(input, output);
        } finally {
            input.close();
        }
        output.flush();

        input = new FileInputStream(new File(sourcesDir, "bug52121-part2"));
        try {
            IOTools.flow(input, output);
        } finally {
            input.close();
        }
        output.flush();

        output.close();

        ByteArrayInputStream byteInStream =
                new ByteArrayInputStream(byteOutStream.toByteArray());

        GZIPInputStream inflaterStream = new GZIPInputStream(byteInStream);
        IOTools.flow(inflaterStream, sink);
    }
}
