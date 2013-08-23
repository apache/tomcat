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
package org.apache.tomcat.util.descriptor;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;

/**
 * A resolver for locally cached XML resources.
 */
public class LocalResolver implements EntityResolver2 {

    private final Class<?> base;
    private final Map<String,String> publicIds;
    private final Map<String,String> systemIds;


    /**
     * Constructor providing mappings of public and system identifiers to local
     * resources. Each map contains a mapping from a well-known identifier to a
     * resource path that will be further resolved using the base Class using
     * Class#getResource(String).
     *
     * @param base the class to use to locate local copies
     * @param publicIds mapping of public identifiers to local resources
     * @param systemIds mapping of system identifiers to local resources
     */
    public LocalResolver(Class<?> base, Map<String,String> publicIds,
            Map<String,String> systemIds) {
        this.base = base;
        this.publicIds = publicIds;
        this.systemIds = systemIds;
    }


    @Override
    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException, IOException {
        return resolveEntity(null, publicId, null, systemId);
    }


    @Override
    public InputSource resolveEntity(String name, String publicId,
            String baseURI, String systemId) throws SAXException, IOException {

        String resolved = resolve(publicId, systemId, baseURI);
        if (resolved == null) {
            return null;
        }

        URL url = base.getResource(resolved);
        if (url != null) {
            resolved = url.toExternalForm();
        }
        InputSource is = new InputSource(resolved);
        is.setPublicId(publicId);
        return is;
    }


    @Override
    public InputSource getExternalSubset(String name, String baseURI)
            throws SAXException, IOException {
        return null;
    }


    private String resolve(String publicId, String systemId, String baseURI) {
        // try resolving using the publicId
        String resolved = publicIds.get(publicId);
        if (resolved != null) {
            return resolved;
        }

        // try resolving using the systemId
        if (systemId == null) {
            return null;
        }

        systemId = resolve(baseURI, systemId);
        resolved = systemIds.get(systemId);
        if (resolved != null) {
            return resolved;
        }

        // fall back to the supplied systemId
        return systemId;
    }


    private static String resolve(String baseURI, String systemId) {
        try {
            if (baseURI == null) {
                return systemId;
            }
            URI systemUri = new URI(systemId);
            if (systemUri.isAbsolute()) {
                return systemId;
            }
            return new URL(new URL(baseURI), systemId).toString();
        } catch (URISyntaxException e) {
            return systemId;
        } catch (MalformedURLException e) {
            return systemId;
        }
    }
}