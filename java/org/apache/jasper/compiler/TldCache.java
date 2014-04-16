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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletContext;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.tomcat.util.descriptor.tld.TaglibXml;
import org.apache.tomcat.util.descriptor.tld.TldParser;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;
import org.apache.tomcat.util.scan.Jar;
import org.xml.sax.SAXException;

/**
 * This class caches parsed instances of TLD files to remove the need for the
 * same TLD to be parsed for each JSP that references it. It does not protect
 * against multiple threads processing the same, new TLD but it does ensure that
 * each all threads will use the same TLD object after parsing.
 */
public class TldCache {

    public static final String SERVLET_CONTEXT_ATTRIBUTE_NAME =
            TldCache.class.getName();

    private final ServletContext servletContext;
    private final Map<String,TldResourcePath> uriTldResourcePathMap = new HashMap<>();
    private final Map<TldResourcePath,TaglibXmlCacheEntry> tldResourcePathTaglibXmlMap =
            new HashMap<>();
    private final TldParser tldParser;


    public static TldCache getInstance(ServletContext servletContext) {
        if (servletContext == null) {
            throw new IllegalArgumentException(Localizer.getMessage(
                    "org.apache.jasper.compiler.TldCache.servletContextNull"));
        }
        return (TldCache) servletContext.getAttribute(SERVLET_CONTEXT_ATTRIBUTE_NAME);
    }


    public TldCache(ServletContext servletContext,
            Map<String, TldResourcePath> uriTldResourcePathMap,
            Map<TldResourcePath, TaglibXml> tldResourcePathTaglibXmlMap) {
        this.servletContext = servletContext;
        this.uriTldResourcePathMap.putAll(uriTldResourcePathMap);
        for (Entry<TldResourcePath, TaglibXml> entry : tldResourcePathTaglibXmlMap.entrySet()) {
            TldResourcePath tldResourcePath = entry.getKey();
            long lastModified[] = getLastModified(tldResourcePath);
            TaglibXmlCacheEntry cacheEntry = new TaglibXmlCacheEntry(
                    entry.getValue(), lastModified[0], lastModified[1]);
            this.tldResourcePathTaglibXmlMap.put(tldResourcePath, cacheEntry);
        }
        boolean validate = Boolean.parseBoolean(
                servletContext.getInitParameter(Constants.XML_VALIDATION_TLD_INIT_PARAM));
        String blockExternalString = servletContext.getInitParameter(
                Constants.XML_BLOCK_EXTERNAL_INIT_PARAM);
        boolean blockExternal;
        if (blockExternalString == null) {
            blockExternal = true;
        } else {
            blockExternal = Boolean.parseBoolean(blockExternalString);
        }
        tldParser = new TldParser(true, validate, blockExternal);
    }


    public TldResourcePath getTldResourcePath(String uri) {
        return uriTldResourcePathMap.get(uri);
    }


    public TaglibXml getTaglibXml(TldResourcePath tldResourcePath) throws JasperException {
        TaglibXmlCacheEntry cacheEntry = tldResourcePathTaglibXmlMap.get(tldResourcePath);
        long lastModified[] = getLastModified(tldResourcePath);
        if (lastModified[0] != cacheEntry.getWebAppPathLastModified() ||
                lastModified[1] != cacheEntry.getEntryLastModified()) {
            synchronized (cacheEntry) {
                if (lastModified[0] != cacheEntry.getWebAppPathLastModified() ||
                        lastModified[1] != cacheEntry.getEntryLastModified()) {
                    // Re-parse TLD
                    TaglibXml updatedTaglibXml;
                    try {
                        updatedTaglibXml = tldParser.parse(tldResourcePath);
                    } catch (IOException | SAXException e) {
                        throw new JasperException(e);
                    }
                    cacheEntry.setTaglibXml(updatedTaglibXml);
                    cacheEntry.setWebAppPathLastModified(lastModified[0]);
                    cacheEntry.setEntryLastModified(lastModified[1]);
                }
            }
        }
        return cacheEntry.getTaglibXml();
    }


    private long[] getLastModified(TldResourcePath tldResourcePath) {
        long[] result = new long[2];
        result[0] = -1;
        result[1] = -1;
        try {
            String webappPath = tldResourcePath.getWebappPath();
            if (webappPath != null) {
                // webappPath will be null for JARs containing TLDs that are on
                // the class path but not part of the web application
                URL url = servletContext.getResource(tldResourcePath.getWebappPath());
                URLConnection conn = url.openConnection();
                result[0] = conn.getLastModified();
                if ("file".equals(url.getProtocol())) {
                    // Reading the last modified time opens an input stream so we
                    // need to make sure it is closed again otherwise the TLD file
                    // will be locked until GC runs.
                    conn.getInputStream().close();
                }
            }
            try (Jar jar = tldResourcePath.getJar()) {
                if (jar != null) {
                    result[1] = jar.getLastModified(tldResourcePath.getEntryName());
                }
            }
        } catch (IOException e) {
            // Ignore (shouldn't happen)
        }
        return result;
    }

    private static class TaglibXmlCacheEntry {
        private volatile TaglibXml taglibXml;
        private volatile long webAppPathLastModified;
        private volatile long entryLastModified;

        public TaglibXmlCacheEntry(TaglibXml taglibXml, long webAppPathLastModified,
                long entryLastModified) {
            this.taglibXml = taglibXml;
            this.webAppPathLastModified = webAppPathLastModified;
            this.entryLastModified = entryLastModified;
        }

        public TaglibXml getTaglibXml() {
            return taglibXml;
        }

        public void setTaglibXml(TaglibXml taglibXml) {
            this.taglibXml = taglibXml;
        }

        public long getWebAppPathLastModified() {
            return webAppPathLastModified;
        }

        public void setWebAppPathLastModified(long webAppPathLastModified) {
            this.webAppPathLastModified = webAppPathLastModified;
        }

        public long getEntryLastModified() {
            return entryLastModified;
        }

        public void setEntryLastModified(long entryLastModified) {
            this.entryLastModified = entryLastModified;
        }
    }
}
