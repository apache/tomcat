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
package org.apache.tomcat.util.http;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TestMethod {

    /*
     * Not testing performance. Just checking that there are no errors in the parsing code.
     */
    @Test
    public void testHttpMethodParsing() {
        List<String> methods = Arrays.asList(Method.GET, Method.POST, Method.PUT, Method.PATCH, Method.HEAD,
                Method.OPTIONS, Method.DELETE, Method.TRACE, Method.PROPPATCH, Method.PROPFIND, Method.MKCOL,
                Method.COPY, Method.MOVE, Method.LOCK, Method.UNLOCK, Method.CONNECT);

        for (String method : methods) {
            byte[] bytes = method.getBytes(StandardCharsets.ISO_8859_1);
            String result = Method.bytesToString(bytes, 0, bytes.length);
            Assert.assertEquals(method, result);
        }
    }
}
