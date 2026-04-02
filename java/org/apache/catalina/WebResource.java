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
package org.apache.catalina;

import java.io.InputStream;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.jar.Manifest;

/**
 * Represents a file or directory within a web application. It borrows heavily from {@link java.io.File}.
 */
public interface WebResource {
    /**
     * Returns the last modified time.
     *
     * @return {@link java.io.File#lastModified()}.
     */
    long getLastModified();

    /**
     * Returns the last modified time in HTTP format.
     *
     * @return the last modified time of this resource in the correct format for the HTTP Last-Modified header as
     *             specified by RFC 2616.
     */
    String getLastModifiedHttp();

    /**
     * Checks if this resource exists.
     *
     * @return {@link java.io.File#exists()}.
     */
    boolean exists();

    /**
     * Indicates if this resource is required for applications to correctly scan the file structure but that does not
     * exist in either the main or any additional {@link WebResourceSet}. For example, if an external directory is
     * mapped to /WEB-INF/lib in an otherwise empty web application, /WEB-INF will be represented as a virtual resource.
     *
     * @return <code>true</code> for a virtual resource
     */
    boolean isVirtual();

    /**
     * Checks if this resource is a directory.
     *
     * @return {@link java.io.File#isDirectory()}.
     */
    boolean isDirectory();

    /**
     * Checks if this resource is a file.
     *
     * @return {@link java.io.File#isFile()}.
     */
    boolean isFile();

    /**
     * Deletes this resource.
     *
     * @return {@link java.io.File#delete()}.
     */
    boolean delete();

    /**
     * Returns the name of this resource.
     *
     * @return {@link java.io.File#getName()}.
     */
    String getName();

    /**
     * Returns the content length of this resource.
     *
     * @return {@link java.io.File#length()}.
     */
    long getContentLength();

    /**
     * Returns the canonical path of this resource.
     *
     * @return {@link java.io.File#getCanonicalPath()}.
     */
    String getCanonicalPath();

    /**
     * Checks if this resource can be read.
     *
     * @return {@link java.io.File#canRead()}.
     */
    boolean canRead();

    /**
     * Returns the webapp path of this resource.
     *
     * @return The path of this resource relative to the web application root. If the resource is a directory, the
     *             return value will end in '/'.
     */
    String getWebappPath();

    /**
     * Returns the weak ETag calculated from the content length and last modified.
     *
     * @return The ETag for this resource
     */
    String getETag();

    /**
     * Return the strong ETag if available else return the weak ETag calculated from the content length and last
     * modified.
     *
     * @return The ETag for this resource
     */
    default String getStrongETag() {
        return getETag();
    }

    /**
     * Set the MIME type for this Resource.
     *
     * @param mimeType The mime type that will be associated with the resource
     */
    void setMimeType(String mimeType);

    /**
     * Returns the MIME type for this Resource.
     *
     * @return the MIME type for this Resource.
     */
    String getMimeType();

    /**
     * Obtains an InputStream based on the contents of this resource.
     *
     * @return An InputStream based on the contents of this resource or <code>null</code> if the resource does not exist
     *             or does not represent a file
     */
    InputStream getInputStream();

    /**
     * Returns the binary content of this resource.
     *
     * @return the binary content of this resource or {@code null} if it is not available in a byte[] because, for
     *             example, it is too big.
     */
    byte[] getContent();

    /**
     * Returns the creation time of this resource.
     *
     * @return The time the file was created. If not available, the result of {@link #getLastModified()} will be
     *             returned.
     */
    long getCreation();

    /**
     * Returns a URL to access this resource.
     *
     * @return a URL to access the resource or <code>null</code> if no such URL is available or if the resource does not
     *             exist.
     */
    URL getURL();

    /**
     * @return the code base for this resource that will be used when looking up the assigned permissions for the code
     *             base in the security policy file when running under a security manager.
     */
    URL getCodeBase();

    /**
     * Returns a reference to the WebResourceRoot of which this WebResource is a part.
     *
     * @return a reference to the WebResourceRoot of which this WebResource is a part.
     */
    WebResourceRoot getWebResourceRoot();

    /**
     * Returns the certificates that were used to sign this resource to verify it.
     *
     * @return the certificates that were used to sign this resource to verify it or @null if none.
     *
     * @see java.util.jar.JarEntry#getCertificates()
     */
    Certificate[] getCertificates();

    /**
     * Returns the manifest associated with this resource.
     *
     * @return the manifest associated with this resource or @null if none.
     *
     * @see java.util.jar.JarFile#getManifest()
     */
    Manifest getManifest();
}
