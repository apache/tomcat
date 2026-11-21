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
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.coyote.http11.filters.BrotliEncoder;

/*
 * Brotli is only used if the Brotli library is available so these tests are skipped if it is not.
 */
@RunWith(Parameterized.class)
public class TestCompressionConfigBrotli {

    @Parameterized.Parameters(name = "{index}: accept-encoding[{2}], expected[{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        // The encoding that is expected is null if the response is not compressed
        parameterSets.add(new Object[] { new String[] { "br" }, "br" });
        parameterSets.add(new Object[] { new String[] { "BR" }, "br" });
        parameterSets.add(new Object[] { new String[] { "gzip" }, "gzip" });
        parameterSets.add(new Object[] { new String[] { "br, gzip" }, "br" });
        parameterSets.add(new Object[] { new String[] { "gzip, br" }, "br" });
        parameterSets.add(new Object[] { new String[] { "gzip", "br" }, "br" });
        parameterSets.add(new Object[] { new String[] { "gzip, deflate, br" }, "br" });
        // The user-agent's preference is respected
        parameterSets.add(new Object[] { new String[] { "br;q=0.5, gzip" }, "gzip" });
        parameterSets.add(new Object[] { new String[] { "br, gzip;q=0.5" }, "br" });
        parameterSets.add(new Object[] { new String[] { "br;q=0.8, gzip;q=0.8" }, "br" });
        // A weight of zero means not acceptable
        parameterSets.add(new Object[] { new String[] { "br;q=0, gzip" }, "gzip" });
        parameterSets.add(new Object[] { new String[] { "br, gzip;q=0" }, "br" });
        parameterSets.add(new Object[] { new String[] { "br;q=0" }, null });
        parameterSets.add(new Object[] { new String[] { "br;q=0, gzip;q=0" }, null });
        parameterSets.add(new Object[] { new String[] { "br", "br;q=0" }, null });
        // "*" matches the encodings that are not listed. gzip is used if the user-agent has no preference.
        parameterSets.add(new Object[] { new String[] { "*" }, "gzip" });
        parameterSets.add(new Object[] { new String[] { "*, br;q=0" }, "gzip" });
        parameterSets.add(new Object[] { new String[] { "*, gzip;q=0" }, "br" });
        parameterSets.add(new Object[] { new String[] { "*;q=0" }, null });
        parameterSets.add(new Object[] { new String[] { "*;q=0, br" }, "br" });
        parameterSets.add(new Object[] { new String[] { "br;q=0.1, *" }, "gzip" });
        // Nothing that can be used
        parameterSets.add(new Object[] { new String[] {}, null });
        parameterSets.add(new Object[] { new String[] { "identity" }, null });
        parameterSets.add(new Object[] { new String[] { "deflate, zstd" }, null });
        parameterSets.add(new Object[] { new String[] { "xbr" }, null });
        parameterSets.add(new Object[] { new String[] { "<>br" }, null });
        parameterSets.add(new Object[] { new String[] { "br;q=abc" }, null });

        List<Object[]> result = new ArrayList<>(parameterSets.size());
        for (Object[] parameterSet : parameterSets) {
            StringJoiner description = new StringJoiner(", ");
            for (String header : (String[]) parameterSet[0]) {
                description.add('"' + header + '"');
            }
            result.add(new Object[] { parameterSet[0], parameterSet[1], description.toString() });
        }
        return result;
    }

    @Parameter(0)
    public String[] headers;
    @Parameter(1)
    public String expected;
    @Parameter(2)
    public String headersDescription;


    @Before
    public void checkBrotliIsAvailable() {
        Assume.assumeTrue(BrotliEncoder.isAvailable());
    }


    private Response compress(CompressionConfig compressionConfig, String... acceptEncodings) {
        Request request = new Request();
        for (String acceptEncoding : acceptEncodings) {
            request.getMimeHeaders().addValue("accept-encoding").setString(acceptEncoding);
        }
        Response response = new Response();
        response.setContentType("text/plain");
        compressionConfig.useCompression(request, response);
        return response;
    }


    @Test
    public void testNegotiation() {
        CompressionConfig compressionConfig = new CompressionConfig();
        compressionConfig.setCompression("on");
        compressionConfig.setCompressionBrotliQuality(4);

        Request request = new Request();
        for (String header : headers) {
            request.getMimeHeaders().addValue("accept-encoding").setString(header);
        }
        Response response = new Response();
        response.setContentType("text/plain");
        response.setContentLength(1_000_000);

        boolean useCompression = compressionConfig.useCompression(request, response);

        Assert.assertEquals(expected != null, useCompression);
        Assert.assertEquals(expected, response.getMimeHeaders().getHeader("Content-Encoding"));
        Assert.assertEquals("br".equals(expected), CompressionConfig.isBrotli(response));
        if (useCompression) {
            Assert.assertEquals(-1, response.getContentLengthLong());
        }
        Assert.assertEquals("accept-encoding", response.getMimeHeaders().getHeader("Vary"));
    }


    @Test
    public void testBrotliDisabledByDefault() {
        CompressionConfig compressionConfig = new CompressionConfig();
        compressionConfig.setCompression("on");
        Assert.assertEquals(-1, compressionConfig.getCompressionBrotliQuality());

        Response response = compress(compressionConfig, headers);

        // Only gzip, which is what was used before Brotli was supported
        Assert.assertFalse(CompressionConfig.isBrotli(response));
        String contentEncoding = response.getMimeHeaders().getHeader("Content-Encoding");
        Assert.assertTrue(contentEncoding, contentEncoding == null || "gzip".equals(contentEncoding));
    }


    @Test
    public void testTransferEncodingIsNotAffected() {
        CompressionConfig compressionConfig = new CompressionConfig();
        compressionConfig.setCompression("force");
        compressionConfig.setCompressionBrotliQuality(4);
        Request request = new Request();
        request.getMimeHeaders().addValue("TE").setString("gzip");
        request.getMimeHeaders().addValue("accept-encoding").setString("br");
        Response response = new Response();

        Assert.assertTrue(compressionConfig.useCompression(request, response));
        Assert.assertEquals("gzip", response.getMimeHeaders().getHeader("Transfer-Encoding"));
        Assert.assertNull(response.getMimeHeaders().getHeader("Content-Encoding"));
        Assert.assertFalse(CompressionConfig.isBrotli(response));
    }


    @Test
    public void testExistingChecksApplyToBrotli() {
        CompressionConfig compressionConfig = new CompressionConfig();
        compressionConfig.setCompression("on");
        compressionConfig.setCompressionBrotliQuality(4);

        // Strong ETag
        Response response = new Response();
        response.setContentType("text/plain");
        response.getMimeHeaders().addValue("ETag").setString("\"abc\"");
        Request request = new Request();
        request.getMimeHeaders().addValue("accept-encoding").setString("br");
        Assert.assertFalse(compressionConfig.useCompression(request, response));

        // Weak ETag
        response = new Response();
        response.setContentType("text/plain");
        response.getMimeHeaders().addValue("ETag").setString("W/\"abc\"");
        Assert.assertTrue(compressionConfig.useCompression(request, response));

        // Already encoded
        response = new Response();
        response.setContentType("text/plain");
        response.getMimeHeaders().addValue("Content-Encoding").setString("br");
        Assert.assertFalse(compressionConfig.useCompression(request, response));

        // Partial content
        response = new Response();
        response.setContentType("text/plain");
        response.setStatus(206);
        Assert.assertFalse(compressionConfig.useCompression(request, response));

        // Too small and the wrong type
        response = new Response();
        response.setContentType("text/plain");
        response.setContentLength(10);
        Assert.assertFalse(compressionConfig.useCompression(request, response));
        response = new Response();
        response.setContentType("image/png");
        Assert.assertFalse(compressionConfig.useCompression(request, response));
    }


    /*
     * The quality is read from the configuration once, when Brotli is chosen. It is held by the response and is that
     * which was used to choose Brotli, whatever happens to the configuration after that.
     */
    @Test
    public void testQualityIsHeldByTheResponse() {
        CompressionConfig compressionConfig = new CompressionConfig();
        compressionConfig.setCompression("force");
        compressionConfig.setCompressionBrotliQuality(7);

        Request request = new Request();
        request.getMimeHeaders().addValue("accept-encoding").setString("br");
        Response response = new Response();
        Assert.assertTrue(compressionConfig.useCompression(request, response));
        Assert.assertEquals(7, CompressionConfig.getBrotliQuality(response));

        for (int quality : new int[] { -1, 0, 11 }) {
            compressionConfig.setCompressionBrotliQuality(quality);
            Assert.assertEquals(7, CompressionConfig.getBrotliQuality(response));
        }

        // A response that is reused, as HTTP/1.1 does, must not see the quality of the previous one. It is not
        // compressed with Brotli.
        response.recycle();
        compressionConfig.setCompressionBrotliQuality(-1);
        Request gzipRequest = new Request();
        gzipRequest.getMimeHeaders().addValue("accept-encoding").setString("gzip");
        Assert.assertTrue(compressionConfig.useCompression(gzipRequest, response));
        Assert.assertEquals(-1, CompressionConfig.getBrotliQuality(response));
        response.recycle();
        Assert.assertEquals(-1, CompressionConfig.getBrotliQuality(response));
        Assert.assertEquals(-1, CompressionConfig.getBrotliQuality(new Response()));
    }


    @Test
    public void testQuality() {
        CompressionConfig compressionConfig = new CompressionConfig();
        Assert.assertEquals(-1, compressionConfig.getCompressionBrotliQuality());
        for (int quality : new int[] { -1, 0, 5, 11 }) {
            compressionConfig.setCompressionBrotliQuality(quality);
            Assert.assertEquals(quality, compressionConfig.getCompressionBrotliQuality());
        }
        for (int quality : new int[] { -2, 12, Integer.MIN_VALUE, Integer.MAX_VALUE }) {
            try {
                compressionConfig.setCompressionBrotliQuality(quality);
                Assert.fail("Expected IllegalArgumentException for " + quality);
            } catch (IllegalArgumentException expectedException) {
                // Expected
            }
            // The previous value is kept
            Assert.assertEquals(11, compressionConfig.getCompressionBrotliQuality());
        }
    }
}
