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
package org.apache.tomcat.util.descriptor.tld;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

import org.apache.tomcat.Jar;
import org.apache.tomcat.util.scan.JarFactory;

/**
 * A TLD Resource Path as defined in JSP 7.3.2.
 * <p>
 * This encapsulates references to Tag Library Descriptors that can be located
 * in different places:
 * <ul>
 * <li>As resources within an application</li>
 * <li>As entries in JAR files included in the application</li>
 * <li>As resources provided by the container</li>
 * </ul>
 * When configuring a mapping from a well-known URI to a TLD, a user is allowed
 * to specify just the name of a JAR file that implicitly contains a TLD in
 * <code>META-INF/taglib.tld</code>. Such a mapping must be explicitly converted
 * to a URL and entryName when using this implementation.
 */
public class TldResourcePath {
    private final URL url;
    private final String webappPath;
    private final String entryName;

    /**
     * Constructor identifying a TLD resource directly.
     *
     * @param url        the location of the TLD
     * @param webappPath the web application path, if any, of the TLD
     */
    public TldResourcePath(URL url, String webappPath) {
        this(url, webappPath, null);
    }

    /**
     * Constructor identifying a TLD packaged within a JAR file.
     *
     * @param url        the location of the JAR
     * @param webappPath the web application path, if any, of the JAR
     * @param entryName  the name of the entry in the JAR
     */
    public TldResourcePath(URL url, String webappPath, String entryName) {
        this.url = url;
        this.webappPath = webappPath;
        this.entryName = entryName;
    }

    /**
     * Returns the URL of the TLD or of the JAR containing the TLD.
     *
     * @return the URL of the TLD
     */
    public URL getUrl() {
        return url;
    }

    /**
     * Returns the path within the web application, if any, that the resource
     * returned by {@link #getUrl()} was obtained from.
     *
     * @return the web application path or @null if the the resource is not
     *         located within a web application
     */
    public String getWebappPath() {
        return webappPath;
    }

    /**
     * Returns the name of the JAR entry that contains the TLD.
     * May be null to indicate the URL refers directly to the TLD itself.
     *
     * @return the name of the JAR entry that contains the TLD
     */
    public String getEntryName() {
        return entryName;
    }

    /**
     * Return the external form of the URL representing this TLD.
     * This can be used as a canonical location for the TLD itself, for example,
     * as the systemId to use when parsing its XML.
     *
     * @return the external form of the URL representing this TLD
     */
    public String toExternalForm() {
        if (entryName == null) {
            return url.toExternalForm();
        } else {
            return "jar:" + url.toExternalForm() + "!/" + entryName;
        }
    }

    /**
     * Opens a stream to access the TLD.
     *
     * @return a stream containing the TLD content
     * @throws IOException if there was a problem opening the stream
     */
    public InputStream openStream() throws IOException {
        if (entryName == null) {
            return url.openStream();
        } else {
            URL entryUrl = JarFactory.getJarEntryURL(url, entryName);
            return entryUrl.openStream();
        }
    }

    public Jar openJar() throws IOException {
        if (entryName == null) {
            return null;
        } else {
            return JarFactory.newInstance(url);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TldResourcePath other = (TldResourcePath) o;

        return url.equals(other.url) &&
                Objects.equals(webappPath, other.webappPath) &&
                Objects.equals(entryName, other.entryName);
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = result * 31 + Objects.hashCode(webappPath);
        result = result * 31 + Objects.hashCode(entryName);
        return result;
    }
}
