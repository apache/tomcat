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
package org.apache.catalina.startup;

import java.io.File;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.ByteChunk;

/**
 * End to end tests to ensure URLs and URL patterns are treated as URL-decoded values. The web application maps a
 * Servlet at <code>/servlet%25</code> and protects <code>/secure%25</code> with a security constraint that denies all
 * access.
 */
public class TestUrlPatterns extends TomcatBaseTest {

    @Test
    public void testPatternsAreTreatedAsUrlDecoded() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-url-patterns");
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        tomcat.start();

        /*
         * The Servlet mapping is only reachable via the URI that decodes to the configured pattern.
         *
         * Note: The request URI needs to be provided in URI-Encoded form
         */
        ByteChunk body = new ByteChunk();
        Assert.assertEquals(HttpServletResponse.SC_OK, getUrl(uri("/test/servlet%2525"), body, null));
        Assert.assertEquals("OK", body.toString());

        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, getUrl(uri("/test/servlet%25"), new ByteChunk(), null));

        // The security constraint follows the same pattern so it only protects the matching URI
        Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN, getUrl(uri("/test/secure%2525"), new ByteChunk(), null));

        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, getUrl(uri("/test/secure%25"), new ByteChunk(), null));
    }


    private String uri(String path) {
        return "http://localhost:" + getPort() + path;
    }
}
