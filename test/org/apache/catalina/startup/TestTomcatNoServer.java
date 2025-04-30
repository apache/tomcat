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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

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

        boolean pass = true;

        /*
         *  Check that each entry for embedded is present in web.xml with the same media type.
         *  Also finds entries that are missing in conf/web.xml
         */
        Iterator<String> embeddedExtensionIterator = embeddedExtensions.iterator();
        while (embeddedExtensionIterator.hasNext()) {
            String embeddedExtension = embeddedExtensionIterator.next();
            String embeddedMediaType = ctx.findMimeMapping(embeddedExtension);

            if (!embeddedMediaType.equals(webXmlMimeMappings.get(embeddedExtension))) {
                pass = false;
                System.out.println("Extension [" + embeddedExtension + "] is mapped to [" + embeddedMediaType +
                        "] in embedded but [" + webXmlMimeMappings.get(embeddedExtension) + "] in conf/web.xml");
            }
            // Remove from both whether they matched or not
            embeddedExtensionIterator.remove();
            webXmlMimeMappings.remove(embeddedExtension);
        }

        // Check for entries missing in embedded
        if (webXmlMimeMappings.size() > 0) {
            pass = false;
            for (Map.Entry<String,String> mapping : webXmlMimeMappings.entrySet()) {
                System.out.println("Extension [" + mapping.getKey() + "] is mapped to [" + mapping.getValue() +
                        "] in conf/web.xml but [null] in embedded");
            }
        }

        Assert.assertTrue(pass);
    }

    @Test
    public void testJarsDecoration() throws Exception {
        File libDir = new File(LoggingBaseTest.getBuildDirectory(), "lib");
        try (JarFile catalinaJar = new JarFile(new File(libDir, "tomcat-util.jar"))) {
            Manifest manifest = catalinaJar.getManifest();
            Assert.assertFalse(manifest.getMainAttributes().getValue("Export-Package").isEmpty());
            Assert.assertNotNull(catalinaJar.getJarEntry("module-info.class"));
        }
    }
}
