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
package org.apache.coyote;

import org.junit.Assert;
import org.junit.Test;

public class TestCompressionConfigSetters {

    @Test
    public void testSetCompressionStandardValues() {
        CompressionConfig compressionConfig = new CompressionConfig();

        compressionConfig.setCompression("off");
        Assert.assertEquals("off", compressionConfig.getCompression());

        compressionConfig.setCompression("on");
        Assert.assertEquals("on", compressionConfig.getCompression());

        compressionConfig.setCompression("force");
        Assert.assertEquals("force", compressionConfig.getCompression());
    }

    @Test
    public void testSetCompressionMinSize() {
        CompressionConfig compressionConfig = new CompressionConfig();

        compressionConfig.setCompression("1024");
        Assert.assertEquals("on", compressionConfig.getCompression());
        Assert.assertEquals(1024, compressionConfig.getCompressionMinSize());
    }

    @Test
    public void testSetCompressionTrimmed() {
        CompressionConfig compressionConfig = new CompressionConfig();

        compressionConfig.setCompression(" on ");
        Assert.assertEquals("on", compressionConfig.getCompression());

        compressionConfig.setCompression(" force ");
        Assert.assertEquals("force", compressionConfig.getCompression());

        compressionConfig.setCompression(" 1024 ");
        Assert.assertEquals("on", compressionConfig.getCompression());
        Assert.assertEquals(1024, compressionConfig.getCompressionMinSize());
    }

    @Test
    public void testSetCompressionInvalid() {
        CompressionConfig compressionConfig = new CompressionConfig();

        compressionConfig.setCompression("on2");
        Assert.assertEquals("off", compressionConfig.getCompression());

        compressionConfig.setCompression("");
        Assert.assertEquals("off", compressionConfig.getCompression());

        compressionConfig.setCompression("   ");
        Assert.assertEquals("off", compressionConfig.getCompression());

        compressionConfig.setCompression(null);
        Assert.assertEquals("off", compressionConfig.getCompression());
    }

    @Test
    public void testSetCompressibleMimeType() {
        CompressionConfig compressionConfig = new CompressionConfig();

        compressionConfig.setCompressibleMimeType("text/html, application/json");
        Assert.assertArrayEquals(new String[] { "text/html", "application/json" },
                compressionConfig.getCompressibleMimeTypes());
    }

    @Test
    public void testSetCompressibleMimeTypeNull() {
        CompressionConfig compressionConfig = new CompressionConfig();

        try {
            compressionConfig.setCompressibleMimeType(null);
            Assert.fail("Expected an IllegalArgumentException for a null value");
        } catch (IllegalArgumentException iae) {
            // Expected
        }

        // The previously configured value must be unchanged
        Assert.assertNotNull(compressionConfig.getCompressibleMimeType());
        Assert.assertEquals("text/html", compressionConfig.getCompressibleMimeTypes()[0]);
    }
}
