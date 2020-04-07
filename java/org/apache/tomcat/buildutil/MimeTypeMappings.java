/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.tomcat.buildutil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.tomcat.util.descriptor.DigesterFactory;
import org.apache.tomcat.util.descriptor.XmlErrorHandler;
import org.apache.tomcat.util.descriptor.web.WebRuleSet;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.digester.Digester;
import org.xml.sax.InputSource;

public class MimeTypeMappings {

    public static void main(String... args) throws Exception {
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

        Map<String,String> webXmlMimeMappings = webXmlDefaultFragment.getMimeMappings();
        SortedMap<String, String> sortedWebXmlMimeMappings = new TreeMap<>(webXmlMimeMappings);

        File f = new File("java/org/apache/catalina/startup/MimeTypeMappings.properties");
        FileOutputStream fos = new FileOutputStream(f);
        Writer w = new OutputStreamWriter(fos, StandardCharsets.US_ASCII);

        Utils.insertLicense(w);

        w.write(System.lineSeparator());

        for (Map.Entry<String, String> mapping : sortedWebXmlMimeMappings.entrySet()) {
            w.write(mapping.getKey());
            w.write("=");
            w.write(mapping.getValue());
            w.write(System.lineSeparator());
        }

        w.close();
        fos.close();
    }
}
