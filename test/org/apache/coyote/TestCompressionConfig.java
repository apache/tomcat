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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.zip.Deflater;

import org.apache.coyote.http11.OutputFilter;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.coyote.http11.filters.GzipOutputFilterFactory;
import org.apache.coyote.http11.filters.OutputFilterFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class TestCompressionConfig {

    @Parameterized.Parameters(name = "{index}: accept-encoding[{0}], ETag [{1}], compress[{2}], useTE[{3}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { new String[] {}, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip" }, null, Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "xgzip" }, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "<>gzip" }, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "foo", "gzip" }, null, Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "<>", "gzip" }, null, Boolean.TRUE, Boolean.FALSE });

        parameterSets.add(new Object[] { new String[] { "gzip" }, null, Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip" }, "W/", Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip" }, "XX", Boolean.FALSE, Boolean.FALSE });

        parameterSets.add(new Object[] { new String[] {}, null, Boolean.FALSE, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "gzip" }, null, Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "xgzip" }, null, Boolean.FALSE, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "<>gzip" }, null, Boolean.FALSE, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "foo", "gzip" }, null, Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "<>", "gzip" }, null, Boolean.TRUE, Boolean.TRUE });

        parameterSets.add(new Object[] { new String[] { "gzip" }, null, Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "gzip" }, "W/", Boolean.TRUE, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "gzip" }, "XX", Boolean.TRUE, Boolean.TRUE });

        parameterSets.add(new Object[] { new String[] { "foobar;foo=bar, gzip;bla=\"quoted\"" }, "XX", Boolean.TRUE,
                Boolean.TRUE });

        return parameterSets;
    }

    @Parameter(0)
    public String[] headers;
    @Parameter(1)
    public String eTag;
    @Parameter(2)
    public Boolean compress;
    @Parameter(3)
    public Boolean useTE;

    @Test
    public void testUseCompression() throws Exception {

        CompressionConfig compressionConfig = new CompressionConfig();
        // Skip length and MIME type checks
        compressionConfig.setCompression("force");

        Request request = new Request();
        Response response = new Response();

        for (String header : headers) {
            if (useTE.booleanValue()) {
                request.getMimeHeaders().addValue("TE").setString(header);
            } else {
                request.getMimeHeaders().addValue("accept-encoding").setString(header);
            }
        }

        if (eTag != null) {
            response.getMimeHeaders().addValue("ETag").setString(eTag);
        }

        List<OutputFilterFactory> factories = new ArrayList<>();
        factories.add(new GzipOutputFilterFactory());
        OutputFilterFactory result = compressionConfig.useCompression(request, response, factories);
        Assert.assertEquals(compress.booleanValue(), result != null);

        if (useTE.booleanValue()) {
            Assert.assertNull(response.getMimeHeaders().getHeader("Content-Encoding"));
            if (result != null) {
                Assert.assertEquals("gzip", response.getMimeHeaders().getHeader("Transfer-Encoding"));
            } else {
                Assert.assertNull(response.getMimeHeaders().getHeader("Transfer-Encoding"));
            }
        } else {
            Assert.assertNull(response.getMimeHeaders().getHeader("Transfer-Encoding"));
            if (result != null) {
                Assert.assertEquals("gzip", response.getMimeHeaders().getHeader("Content-Encoding"));
            } else {
                Assert.assertNull(response.getMimeHeaders().getHeader("Content-Encoding"));
            }
        }
    }


    @Test
    public void testNoCompressionEncodings() {
        CompressionConfig config = new CompressionConfig();
        String encodings = config.getNoCompressionEncodings();
        Assert.assertTrue(Arrays.asList("br", "compress", "dcb", "dcz", "deflate", "gzip", "pack200-gzip", "zstd")
                .stream().anyMatch(encodings::contains));

        config.setNoCompressionEncodings("br");
        String newEncodings = config.getNoCompressionEncodings();
        Assert.assertTrue(newEncodings.contains("br"));
        Assert.assertFalse(newEncodings.contains("gzip"));
    }

    @Test
    public void testGzipOutputFilterFactoryJavaBeanProperties() {
        GzipOutputFilterFactory factory = new GzipOutputFilterFactory();

        Assert.assertEquals(-1, factory.getLevel());
        Assert.assertEquals(GzipOutputFilter.DEFAULT_BUFFER_SIZE, factory.getBufferSize());
        Assert.assertEquals("gzip", factory.getEncodingName());

        factory.setLevel(6);
        factory.setBufferSize(1024);

        Assert.assertEquals(6, factory.getLevel());
        Assert.assertEquals(1024, factory.getBufferSize());

        OutputFilter filter = factory.createFilter();
        Assert.assertNotNull(filter);
        Assert.assertTrue(filter instanceof GzipOutputFilter);
    }

    @Test
    public void testCompressionOff() {
        CompressionConfig config = new CompressionConfig();
        // Default compression is "off"

        Request request = new Request();
        Response response = new Response();
        request.getMimeHeaders().addValue("accept-encoding").setString("gzip");

        List<OutputFilterFactory> factories = new ArrayList<>();
        factories.add(new GzipOutputFilterFactory());
        OutputFilterFactory result = config.useCompression(request, response, factories);
        Assert.assertNull(result);
    }

    @Test
    public void testAlreadyCompressedContentEncoding() {
        CompressionConfig config = new CompressionConfig();
        config.setCompression("force");

        Request request = new Request();
        Response response = new Response();
        request.getMimeHeaders().addValue("accept-encoding").setString("gzip");
        // Response already has gzip Content-Encoding - should skip compression
        response.getMimeHeaders().addValue("content-encoding").setString("gzip");

        List<OutputFilterFactory> factories = new ArrayList<>();
        factories.add(new GzipOutputFilterFactory());
        OutputFilterFactory result = config.useCompression(request, response, factories);
        Assert.assertNull(result);
    }

    @Test
    public void testNegotiationViaAcceptEncoding() throws Exception {
        CompressionConfig config = new CompressionConfig();
        config.setCompression("force");

        // Factories: gzip then deflate (server priority)
        List<OutputFilterFactory> factories = new ArrayList<>();
        factories.add(new GzipOutputFilterFactory());
        OutputFilterFactory deflateFactory = new OutputFilterFactory() {
            @Override
            public OutputFilter createFilter() {
                return new GzipOutputFilter();
            }
            @Override
            public String getEncodingName() {
                return "deflate";
            }
        };
        factories.add(deflateFactory);

        // Client prefers deflate over gzip
        Request request = new Request();
        Response response = new Response();
        request.getMimeHeaders().addValue("accept-encoding").setString("deflate;q=1.0, gzip;q=0.5");

        OutputFilterFactory result = config.useCompression(request, response, factories);
        Assert.assertNotNull(result);
        Assert.assertEquals("deflate", result.getEncodingName());
        Assert.assertEquals("deflate", response.getMimeHeaders().getHeader("Content-Encoding"));
        Assert.assertNull(response.getMimeHeaders().getHeader("Transfer-Encoding"));
    }

    @Test
    public void testNegotiationViaTE() throws Exception {
        CompressionConfig config = new CompressionConfig();
        config.setCompression("force");

        // Factories: gzip then deflate (server priority)
        List<OutputFilterFactory> factories = new ArrayList<>();
        factories.add(new GzipOutputFilterFactory());
        OutputFilterFactory deflateFactory = new OutputFilterFactory() {
            @Override
            public OutputFilter createFilter() {
                return new GzipOutputFilter();
            }
            @Override
            public String getEncodingName() {
                return "deflate";
            }
        };
        factories.add(deflateFactory);

        // TE header should use Transfer-Encoding, not Content-Encoding
        Request request = new Request();
        Response response = new Response();
        request.getMimeHeaders().addValue("TE").setString("deflate;q=1.0, gzip;q=0.5");

        OutputFilterFactory result = config.useCompression(request, response, factories);
        Assert.assertNotNull(result);
        Assert.assertEquals("deflate", result.getEncodingName());
        Assert.assertEquals("deflate", response.getMimeHeaders().getHeader("Transfer-Encoding"));
        Assert.assertNull(response.getMimeHeaders().getHeader("Content-Encoding"));
    }


}
