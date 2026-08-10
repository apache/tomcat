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

import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * End to end tests for the <code>urlPatternsProvidedInDecodedForm</code> Context attribute. The web application maps a
 * Servlet at <code>/servlet%25</code> and protects <code>/secure%25</code> with a security constraint that denies all
 * access. Whether those patterns are stored as <code>%</code> or as <code>%25</code> determines which request URI
 * reaches them once the URI has been decoded.
 */
public class TestUrlPatternsProvidedInDecodedForm extends TomcatBaseTest {

    /*
     * The URI that decodes to "%".
     */
    private static final String ENCODED_FORM_URI_SUFFIX = "%25";

    /*
     * The URI that decodes to "%25".
     */
    private static final String DECODED_FORM_URI_SUFFIX = "%2525";


    @Test
    public void testEncodedForm() throws Exception {
        // Patterns are decoded when read so the Servlet is mapped at "/servlet%"
        doTestUrlPatterns(false, ENCODED_FORM_URI_SUFFIX, DECODED_FORM_URI_SUFFIX);
    }


    @Test
    public void testDecodedForm() throws Exception {
        // Patterns are used as provided so the Servlet is mapped at "/servlet%25"
        doTestUrlPatterns(true, DECODED_FORM_URI_SUFFIX, ENCODED_FORM_URI_SUFFIX);
    }


    @SuppressWarnings("deprecation")
    private void doTestUrlPatterns(boolean urlPatternsProvidedInDecodedForm, String matchingSuffix,
            String nonMatchingSuffix) throws Exception {

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-url-patterns");
        Context ctx = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        ctx.setUrlPatternsProvidedInDecodedForm(urlPatternsProvidedInDecodedForm);

        tomcat.start();

        // The Servlet mapping is only reachable via the URI that decodes to the configured pattern
        ByteChunk body = new ByteChunk();
        Assert.assertEquals(HttpServletResponse.SC_OK, getUrl(uri("/test/servlet", matchingSuffix), body, null));
        Assert.assertEquals("OK", body.toString());

        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND,
                getUrl(uri("/test/servlet", nonMatchingSuffix), new ByteChunk(), null));

        // The security constraint follows the same pattern so it only protects the matching URI
        Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN,
                getUrl(uri("/test/secure", matchingSuffix), new ByteChunk(), null));

        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND,
                getUrl(uri("/test/secure", nonMatchingSuffix), new ByteChunk(), null));
    }


    private String uri(String path, String suffix) {
        return "http://localhost:" + getPort() + path + suffix;
    }
}
