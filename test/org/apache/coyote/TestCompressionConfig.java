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

        parameterSets.add(new Object[] { new String[] {  },              null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip" },        null, Boolean.TRUE,  Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "xgzip" },       null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "<>gzip" },      null, Boolean.FALSE, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "foo", "gzip" }, null, Boolean.TRUE,  Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "<>", "gzip" },  null, Boolean.TRUE,  Boolean.FALSE });

        parameterSets.add(new Object[] { new String[] { "gzip" },        null, Boolean.TRUE,  Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip" },        "W/", Boolean.TRUE,  Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip" },        "XX", Boolean.FALSE, Boolean.FALSE });

        parameterSets.add(new Object[] { new String[] {  },              null, Boolean.FALSE, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "gzip" },        null, Boolean.TRUE,  Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "xgzip" },       null, Boolean.FALSE, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "<>gzip" },      null, Boolean.FALSE, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "foo", "gzip" }, null, Boolean.TRUE,  Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "<>", "gzip" },  null, Boolean.TRUE,  Boolean.TRUE });

        parameterSets.add(new Object[] { new String[] { "gzip" },        null, Boolean.TRUE,  Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "gzip" },        "W/", Boolean.TRUE,  Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "gzip" },        "XX", Boolean.TRUE, Boolean.TRUE });

        parameterSets.add(new Object[] { new String[] { "foobar;foo=bar, gzip;bla=\"quoted\"" }, "XX", Boolean.TRUE, Boolean.TRUE });

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
}
