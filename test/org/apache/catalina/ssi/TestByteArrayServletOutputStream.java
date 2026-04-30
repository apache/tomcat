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
package org.apache.catalina.ssi;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link ByteArrayServletOutputStream}.
 */
public class TestByteArrayServletOutputStream {

    @Test
    public void testWriteAndToByteArray() {
        ByteArrayServletOutputStream basos = new ByteArrayServletOutputStream();

        basos.write('H');
        basos.write('i');

        byte[] result = basos.toByteArray();
        Assert.assertEquals(2, result.length);
        Assert.assertEquals('H', result[0]);
        Assert.assertEquals('i', result[1]);
    }


    @Test
    public void testEmptyStream() {
        ByteArrayServletOutputStream basos = new ByteArrayServletOutputStream();

        byte[] result = basos.toByteArray();
        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.length);
    }


    @Test
    public void testMultipleWrites() {
        ByteArrayServletOutputStream basos = new ByteArrayServletOutputStream();

        String text = "Hello, World!";
        for (char c : text.toCharArray()) {
            basos.write(c);
        }

        byte[] result = basos.toByteArray();
        Assert.assertEquals(text.length(), result.length);
        Assert.assertEquals(text, new String(result));
    }


    @Test
    public void testIsReady() {
        ByteArrayServletOutputStream basos = new ByteArrayServletOutputStream();
        // Default returns false (as per TODO in source)
        Assert.assertFalse(basos.isReady());
    }


    @Test
    public void testSetWriteListener() {
        ByteArrayServletOutputStream basos = new ByteArrayServletOutputStream();
        // Should not throw — listener is a no-op
        basos.setWriteListener(null);
    }
}
