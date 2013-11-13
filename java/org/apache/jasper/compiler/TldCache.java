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

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.tomcat.util.descriptor.tld.TaglibXml;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;

/**
 * This class caches parsed instances of TLD files to remove the need for the
 * same TLD to be parsed for each JSP that references it. It does not protect
 * against multiple threads processing the same, new TLD but it does ensure that
 * each all threads will use the same TLD object after parsing.
 */
public class TldCache {

    public static final String SERVLET_CONTEXT_ATTRIBUTE_NAME =
            TldCache.class.getName();

    private final Map<String, TldResourcePath> uriTldResourcePathMap = new HashMap<>();
    private final Map<TldResourcePath, TaglibXml> tldResourcePathTaglibXmlMap = new HashMap<>();


    public static TldCache getInstance(ServletContext servletContext) {
        if (servletContext == null) {
            throw new IllegalArgumentException(Localizer.getMessage(
                    "org.apache.jasper.compiler.TldCache.servletContextNull"));
        }
        return (TldCache) servletContext.getAttribute(SERVLET_CONTEXT_ATTRIBUTE_NAME);
    }


    public TldCache(Map<String, TldResourcePath> uriTldResourcePathMap,
            Map<TldResourcePath, TaglibXml> tldResourcePathTaglibXmlMap) {
        this.uriTldResourcePathMap.putAll(uriTldResourcePathMap);
        this.tldResourcePathTaglibXmlMap.putAll(tldResourcePathTaglibXmlMap);
    }


    public TldResourcePath getTldResourcePath(String uri) {
        return uriTldResourcePathMap.get(uri);
    }
}
