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
package org.apache.tomcat.util.descriptor.web;

import java.io.StringReader;
import java.net.URL;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import org.xml.sax.InputSource;

public class TestWebXmlParser {

    private static final String WEB_XML = "TestWebXmlParser-web.xml";

    @Test
    public void testUrlPatterns() throws Exception {
        URL webXmlUrl = TestWebXmlParser.class.getResource(WEB_XML);
        Assert.assertNotNull("Could not locate " + WEB_XML, webXmlUrl);

        WebXml webXml = new WebXml();
        WebXmlParser parser = new WebXmlParser(false, false, true);
        Assert.assertTrue(parser.parseWebXml(webXmlUrl, webXml, false));

        Assert.assertEquals("servlet", webXml.getServletMappings().get("/servlet%25"));

        FilterMap filterMap = webXml.getFilterMappings().iterator().next();
        Assert.assertArrayEquals(new String[] { "/filter%25" }, filterMap.getURLPatterns());

        JspPropertyGroup jspPropertyGroup = webXml.getJspPropertyGroups().iterator().next();
        Assert.assertEquals(Set.of("/jsp%25"), jspPropertyGroup.getUrlPatterns());

        LoginConfig loginConfig = webXml.getLoginConfig();
        Assert.assertEquals("/login%25", loginConfig.getLoginPage());
        Assert.assertEquals("/login-error%25", loginConfig.getErrorPage());

        ErrorPage errorPage = webXml.getErrorPages().values().iterator().next();
        Assert.assertEquals("/error%25", errorPage.getLocation());

        SecurityConstraint securityConstraint = webXml.getSecurityConstraints().iterator().next();
        SecurityCollection securityCollection = securityConstraint.findCollection("resource");
        Assert.assertArrayEquals(new String[] { "/secure%25" }, securityCollection.findPatterns());
    }

    @Test
    public void testCookieMaxAgeInvalidFailsParse() {
        // An invalid max-age value must fail the parse so the deployment fails,
        // rather than breaking session cookie creation at run time
        String webXml = "<web-app><session-config><cookie-config>" +
                "<max-age>abc</max-age>" +
                "</cookie-config></session-config></web-app>";

        WebXml result = new WebXml();
        WebXmlParser parser = new WebXmlParser(false, false, true);

        Assert.assertFalse(parser.parseWebXml(new InputSource(new StringReader(webXml)), result, false));
    }

    @Test
    public void testCookieMaxAgeEmptyFailsParse() {
        // The digester passes an empty body text to the setter, so an empty
        // element must also fail the parse
        String webXml = "<web-app><session-config><cookie-config>" +
                "<max-age></max-age>" +
                "</cookie-config></session-config></web-app>";

        WebXml result = new WebXml();
        WebXmlParser parser = new WebXmlParser(false, false, true);

        Assert.assertFalse(parser.parseWebXml(new InputSource(new StringReader(webXml)), result, false));
    }

    @Test
    public void testCookieMaxAgeValidParses() {
        String webXml = "<web-app><session-config><cookie-config>" +
                "<max-age>60</max-age>" +
                "</cookie-config></session-config></web-app>";

        WebXml result = new WebXml();
        WebXmlParser parser = new WebXmlParser(false, false, true);

        Assert.assertTrue(parser.parseWebXml(new InputSource(new StringReader(webXml)), result, false));
        Assert.assertEquals(Integer.valueOf(60), result.getSessionConfig().getCookieMaxAge());
    }
}
