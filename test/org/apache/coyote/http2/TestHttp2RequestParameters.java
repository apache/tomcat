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

package org.apache.coyote.http2;

import org.junit.Assert;
import org.junit.Test;

public class TestHttp2RequestParameters extends Http2TestBase {
    /**
     * Test case for https://bz.apache.org/bugzilla/show_bug.cgi?id=69918 POST parameters are not returned from a call
     * to any of the {@link org.apache.catalina.connector.Request} getParameterXXX() methods if the request is HTTP/2
     * and the content-length header is not set.
     *
     * @throws Exception If the test encounters an unexpected error
     */
    @Test
    public void testBug69918() throws Exception {
        http2Connect();

        sendParameterPostRequest(3, null, "a=1&b=2", -1, false);
        output.setTraceBody(true);

        boolean foundBody = false;
        while (parser.readFrame()) {
            String trace = output.getTrace();
            if (trace.contains("3-Body-2")) {
                foundBody = true;
            } else if (trace.contains("3-Body-0")) {
                Assert.fail("Parameter count was 0. Trace: " + trace);
            }
            if (trace.contains("3-EndOfStream")) {
                break;
            }
        }
        Assert.assertTrue("Parameter count should be 2, trace: " + output.getTrace(), foundBody);
    }
}
