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
package org.apache.jasper.compiler;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestParserController extends TomcatBaseTest {

    @Test
    public void testIncludeInvalidUri() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();
        int sc = getUrl("http://localhost:" + getPort() + "/test/jsp/parsing/include-invalid-uri.jsp", res, null);

        Assert.assertEquals(500, sc);

        // The report should include the translation error for the invalid
        // include. The error handling code previously threw a
        // NullPointerException itself which was reported instead.
        String result = res.toString();
        Assert.assertTrue(result, result.contains("The include [a b.jsp] is invalid"));
        Assert.assertFalse(result, result.contains("Cannot invoke"));
    }
}
