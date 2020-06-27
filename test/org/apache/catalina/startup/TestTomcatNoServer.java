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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.core.StandardContext;
import org.apache.tomcat.util.descriptor.DigesterFactory;
import org.apache.tomcat.util.descriptor.XmlErrorHandler;
import org.apache.tomcat.util.descriptor.web.WebRuleSet;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.digester.Digester;
import org.xml.sax.InputSource;


/**
 * Tests that do not require a Tomcat instance to be started.
 */
public class TestTomcatNoServer {

    @Test
    public void testDefaultMimeTypeMappings() throws Exception {
        StandardContext ctx = new StandardContext();

        Tomcat.initWebappDefaults(ctx);

        InputSource globalWebXml = new InputSource(new File("conf/web.xml").getAbsoluteFile().toURI().toString());

        WebXml webXmlDefaultFragment = new WebXml();
        webXmlDefaultFragment.setOverridable(true);
        webXmlDefaultFragment.setDistributable(true);
        webXmlDefaultFragment.setAlwaysAddWelcomeFiles(false);

        Digester digester = DigesterFactory.newDigester(true, true, new WebRuleSet(), true);
        XmlErrorHandler handler = new XmlErrorHandler();
        digester.setErrorHandler(handler);
        digester.push(webXmlDefaultFragment);
        digester.parse(globalWebXml);
        Assert.assertEquals(0, handler.getErrors().size());
        Assert.assertEquals(0, handler.getWarnings().size());

        Map<String,String> webXmlMimeMappings = webXmlDefaultFragment.getMimeMappings();

        Set<String> embeddedExtensions = new HashSet<>(Arrays.asList(ctx.findMimeMappings()));

        // Find entries present in conf/web.xml that are missing in embedded
        Set<String> missingInEmbedded = new HashSet<>(webXmlMimeMappings.keySet());
        missingInEmbedded.removeAll(embeddedExtensions);
        if (missingInEmbedded.size() > 0) {
            for (String missingExtension : missingInEmbedded) {
                System.out.println("Missing in embedded: [" + missingExtension +
                        "]-[" + webXmlMimeMappings.get(missingExtension) + "]");
            }
            Assert.fail("Embedded is missing [" + missingInEmbedded.size() + "] entires compared to conf/web.xml");
        }

        // Find entries present in embedded that are missing in conf/web.xml
        Set<String> missingInWebXml = new HashSet<>(embeddedExtensions);
        missingInWebXml.removeAll(webXmlMimeMappings.keySet());
        if (missingInWebXml.size() > 0) {
            for (String missingExtension : missingInWebXml) {
                System.out.println("Missing in embedded: [" + missingExtension +
                        "]-[" + ctx.findMimeMapping(missingExtension) + "]");
            }
            Assert.fail("Embedded is missing [" + missingInWebXml.size() + "] entires compared to conf/web.xml");
        }
    }
}
