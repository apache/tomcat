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
import java.util.StringJoiner;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class TestCompressionConfig {

    @Parameterized.Parameters(name = "{index}: accept-encoding[{4}], ETag [{1}], compress[{2}], useTE[{3}]")
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

        // A weight of zero means not acceptable. A malformed weight is not acceptable either.
        for (Boolean useTE : new Boolean[] { Boolean.FALSE, Boolean.TRUE }) {
            parameterSets.add(new Object[] { new String[] { "gzip;q=0" }, null, Boolean.FALSE, useTE });
            parameterSets.add(new Object[] { new String[] { "gzip;q=0.0" }, null, Boolean.FALSE, useTE });
            parameterSets.add(new Object[] { new String[] { "gzip;q=abc" }, null, Boolean.FALSE, useTE });
            parameterSets.add(new Object[] { new String[] { "foo", "gzip;q=0" }, null, Boolean.FALSE, useTE });
            parameterSets.add(new Object[] { new String[] { "gzip;q=abc", "gzip" }, null, Boolean.TRUE, useTE });
            parameterSets.add(new Object[] { new String[] { "gzip;q=0.5" }, null, Boolean.TRUE, useTE });
        }

        // "*" matches any content coding that is not listed explicitly. It is not meaningful for the TE header.
        parameterSets.add(new Object[] { new String[] { "*" }, null, Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "*;q=0.5" }, null, Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "foo, *" }, null, Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "*;q=0" }, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "*;q=abc" }, null, Boolean.FALSE, Boolean.FALSE });
        // An explicit entry takes precedence over "*", wherever it appears
        parameterSets.add(new Object[] { new String[] { "gzip;q=0, *" }, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "*, gzip;q=0" }, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip;q=0", "*" }, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "*", "gzip;q=0" }, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "*;q=0, gzip" }, null, Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "*;q=0", "gzip" }, null, Boolean.TRUE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip", "*;q=0" }, null, Boolean.TRUE, Boolean.FALSE });
        // An explicit weight of zero is authoritative even if the same coding is also listed with a positive weight
        parameterSets.add(new Object[] { new String[] { "gzip, gzip;q=0" }, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip;q=0, gzip" }, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip;q=0.5, gzip;q=0" }, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip", "gzip;q=0" }, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip;q=0", "gzip" }, null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "GZIP, gzip;q=0" }, null, Boolean.FALSE, Boolean.FALSE });
        // "*" is not used for the TE header
        parameterSets.add(new Object[] { new String[] { "*" }, null, Boolean.FALSE, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "*;q=0.5" }, null, Boolean.FALSE, Boolean.TRUE });

        // Add a description of the headers so the test name shows them rather than an array reference. Each header is
        // quoted so a single header with multiple values can be distinguished from multiple headers.
        List<Object[]> result = new ArrayList<>(parameterSets.size());
        for (Object[] parameterSet : parameterSets) {
            StringJoiner description = new StringJoiner(", ");
            for (String header : (String[]) parameterSet[0]) {
                description.add('"' + header + '"');
            }
            result.add(new Object[] { parameterSet[0], parameterSet[1], parameterSet[2], parameterSet[3],
                    description.toString() });
        }

        return result;
    }

    @Parameter(0)
    public String[] headers;
    @Parameter(1)
    public String eTag;
    @Parameter(2)
    public Boolean compress;
    @Parameter(3)
    public Boolean useTE;
    @Parameter(4)
    public String headersDescription;

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

        boolean useCompression = compressionConfig.useCompression(request, response);
        Assert.assertEquals(compress, Boolean.valueOf(useCompression));

        if (useTE.booleanValue()) {
            Assert.assertNull(response.getMimeHeaders().getHeader("Content-Encoding"));
            if (useCompression) {
                Assert.assertEquals("gzip", response.getMimeHeaders().getHeader("Transfer-Encoding"));
            } else {
                Assert.assertNull(response.getMimeHeaders().getHeader("Transfer-Encoding"));
            }
        } else {
            Assert.assertNull(response.getMimeHeaders().getHeader("Transfer-Encoding"));
            if (useCompression) {
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
}
