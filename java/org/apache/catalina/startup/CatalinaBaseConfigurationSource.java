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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.file.ConfigurationSource;
import org.apache.tomcat.util.res.StringManager;

public class CatalinaBaseConfigurationSource implements ConfigurationSource {

    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    public static final String LEGACY_SERVER_EMBED_XML = "server-embed.xml";

    private final String serverXmlPath;
    private final File catalinaBaseFile;
    private final URI catalinaBaseUri;

    public CatalinaBaseConfigurationSource(File catalinaBaseFile, String serverXmlPath) {
        this.catalinaBaseFile = catalinaBaseFile;
        catalinaBaseUri = catalinaBaseFile.toURI();
        this.serverXmlPath = serverXmlPath;
    }

    @Override
    public Resource getServerXml() throws IOException {
        IOException ioe = null;
        Resource result = null;
        try {
            if (serverXmlPath == null || serverXmlPath.equals(Catalina.SERVER_XML)) {
                result = ConfigurationSource.super.getServerXml();
            } else {
                result = getResource(serverXmlPath);
            }
        } catch (IOException e) {
            ioe = e;
        }
        if (result == null) {
            // Compatibility with legacy server-embed.xml location
            InputStream stream = getClass().getClassLoader().getResourceAsStream(LEGACY_SERVER_EMBED_XML);
            if (stream != null) {
                try {
                    result = new Resource(stream,
                            getClass().getClassLoader().getResource(LEGACY_SERVER_EMBED_XML).toURI());
                } catch (URISyntaxException e) {
                    stream.close();
                }
            }
        }

        if (result == null && ioe != null) {
            throw ioe;
        } else {
            return result;
        }
    }

    @Override
    public Resource getResource(String name) throws IOException {
        // Originally only File was supported. Class loader and URI were added
        // later. However (see bug 65106) treating some URIs as files can cause
        // problems. Therefore, if path starts with a valid URI scheme then skip
        // straight to processing this as a URI.
        if (!UriUtil.isAbsoluteURI(name)) {
            File f = new File(name);
            if (!f.isAbsolute()) {
                f = new File(catalinaBaseFile, name);
            }
            if (f.isFile()) {
                FileInputStream fis = new FileInputStream(f);
                return new Resource(fis, f.toURI());
            }

            // Try classloader
            InputStream stream = null;
            try {
                stream = getClass().getClassLoader().getResourceAsStream(name);
                if (stream != null) {
                    return new Resource(stream, getClass().getClassLoader().getResource(name).toURI());
                }
            } catch (URISyntaxException e) {
                stream.close();
                throw new IOException(sm.getString("catalinaConfigurationSource.cannotObtainURL", name), e);
            }
        }

        // Then try URI.
        URI uri = null;
        try {
            uri = getURIInternal(name);
        } catch (IllegalArgumentException e) {
            throw new IOException(sm.getString("catalinaConfigurationSource.cannotObtainURL", name));
        }

        // Obtain the input stream we need
        try {
            URL url = uri.toURL();
            return new Resource(url.openConnection().getInputStream(), uri);
        } catch (MalformedURLException e) {
            throw new IOException(sm.getString("catalinaConfigurationSource.cannotObtainURL", name), e);
        }
    }

    @Override
    public URI getURI(String name) {
        // Originally only File was supported. Class loader and URI were added
        // later. However (see bug 65106) treating some URIs as files can cause
        // problems. Therefore, if path starts with a valid URI scheme then skip
        // straight to processing this as a URI.
        if (!UriUtil.isAbsoluteURI(name)) {
            File f = new File(name);
            if (!f.isAbsolute()) {
                f = new File(catalinaBaseFile, name);
            }
            if (f.isFile()) {
                return f.toURI();
            }

            // Try classloader
            try {
                URL resource = getClass().getClassLoader().getResource(name);
                if (resource != null) {
                    return resource.toURI();
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        return getURIInternal(name);
    }

    private URI getURIInternal(String name) {
        // Then try URI.
        // Using resolve() enables the code to handle relative paths that did
        // not point to a file
        URI uri;
        if (catalinaBaseUri != null) {
            uri = catalinaBaseUri.resolve(name);
        } else {
            uri = URI.create(name);
        }
        return uri;
    }
}
