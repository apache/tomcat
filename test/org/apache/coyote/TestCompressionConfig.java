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

    @Parameterized.Parameters(name = "{index}: headers[{0}], compress[{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { new String[] {  }, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "gzip" }, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "xgzip" }, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "<>gzip" }, Boolean.FALSE });
        parameterSets.add(new Object[] { new String[] { "foo", "gzip" }, Boolean.TRUE });
        parameterSets.add(new Object[] { new String[] { "<>", "gzip" }, Boolean.TRUE });

        return parameterSets;
    }

    @Parameter(0)
    public String[] headers;
    @Parameter(1)
    public Boolean compress;

    @Test
    public void testUseCompression() throws Exception {

        CompressionConfig compressionConfig = new CompressionConfig();
        // Skip length and MIME type checks
        compressionConfig.setCompression("force");

        Request request = new Request();
        Response response = new Response();

        for (String header : headers) {
            request.getMimeHeaders().addValue("accept-encoding").setString(header);
        }

        Assert.assertEquals(compress, Boolean.valueOf(compressionConfig.useCompression(request, response)));
    }
}
