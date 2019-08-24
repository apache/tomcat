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
package org.apache.tomcat.util.descriptor.tagplugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.descriptor.DigesterFactory;
import org.apache.tomcat.util.descriptor.XmlErrorHandler;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Parser for Tag Plugin descriptors.
 */
@SuppressWarnings("deprecation")
public class TagPluginParser {
    private final Log log = LogFactory.getLog(TagPluginParser.class); // must not be static
    private static final String PREFIX = "tag-plugins/tag-plugin";
    private final Digester digester;
    private final Map<String, String> plugins = new HashMap<>();

    public TagPluginParser(ServletContext context, boolean blockExternal) {
        digester = DigesterFactory.newDigester(
                false, false, new TagPluginRuleSet(), blockExternal);
        digester.setClassLoader(context.getClassLoader());
    }

    public void parse(URL url) throws IOException, SAXException {
        try (InputStream is = url.openStream()) {
            XmlErrorHandler handler = new XmlErrorHandler();
            digester.setErrorHandler(handler);

            digester.push(this);

            InputSource source = new InputSource(url.toExternalForm());
            source.setByteStream(is);
            digester.parse(source);
            if (!handler.getWarnings().isEmpty() || !handler.getErrors().isEmpty()) {
                handler.logFindings(log, source.getSystemId());
                if (!handler.getErrors().isEmpty()) {
                    // throw the first to indicate there was an error during processing
                    throw handler.getErrors().iterator().next();
                }
            }
        } finally {
            digester.reset();
        }
    }

    public void addPlugin(String tagClass, String pluginClass) {
        plugins.put(tagClass, pluginClass);
    }

    public Map<String, String> getPlugins() {
        return plugins;
    }

    private static class TagPluginRuleSet extends RuleSetBase {
        @Override
        public void addRuleInstances(Digester digester) {
            digester.addCallMethod(PREFIX, "addPlugin", 2);
            digester.addCallParam(PREFIX + "/tag-class", 0);
            digester.addCallParam(PREFIX + "/plugin-class", 1);
        }
    }
}
