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
 * Represents a file or directory within a web application. It borrows heavily
 * from {@link java.io.File}.
 */
public interface WebResource {
    /**
     * See {@link java.io.File#lastModified()}.
     */
    long getLastModified();

    /**
     * Return the last modified time of this resource in the correct format for
     * the HTTP Last-Modified header as specified by RFC 2616.
     */
    String getLastModifiedHttp();

    /**
     * See {@link java.io.File#exists()}.
     */
    boolean exists();

    /**
     * Indicates if this resource is required for applications to correctly scan
     * the file structure but that does not exist in either the main or any
     * additional {@link WebResourceSet}. For example, if an external
     * directory is mapped to /WEB-INF/lib in an otherwise empty web
     * application, /WEB-INF will be represented as a virtual resource.
     */
    boolean isVirtual();

    /**
     * See {@link java.io.File#isDirectory()}.
     */
    boolean isDirectory();

    /**
     * See {@link java.io.File#isFile()}.
     */
    boolean isFile();

    /**
     * See {@link java.io.File#delete()}.
     */
    boolean delete();

    /**
     * See {@link java.io.File#getName()}.
     */
    String getName();

    /**
     * See {@link java.io.File#length()}.
     */
    long getContentLength();

    /**
     * See {@link java.io.File#getCanonicalPath()}.
     */
    String getCanonicalPath();

    /**
     * See {@link java.io.File#canRead()}.
     */
    boolean canRead();

    /**
     * The path of this resource relative to the web application root. If the
     * resource is a directory, the return value will end in '/'.
     */
    String getWebappPath();

    /**
     * Return the strong ETag if available (currently not supported) else return
     * the weak ETag calculated from the content length and last modified.
     *
     * @return  The ETag for this resource
     */
    String getETag();

    /**
     * Set the MIME type for this Resource.
     */
    void setMimeType(String mimeType);

    /**
     * Get the MIME type for this Resource.
     */
    String getMimeType();

    /**
     * Obtain an InputStream based on the contents of this resource.
     *
     * @return  An InputStream based on the contents of this resource or
     *          <code>null</code> if the resource does not exist or does not
     *          represent a file
     */
    InputStream getInputStream();

    /**
     * Obtain the cached binary content of this resource.
     */
    byte[] getContent();

    /**
     * The time the file was created. If not available, the result of
     * {@link #getLastModified()} will be returned.
     */
    long getCreation();

    /**
     * Obtain a URL to access the resource or <code>null</code> if no such URL
     * is available or if the resource does not exist.
     */
    URL getURL();

    /**
     * Obtain a reference to the WebResourceRoot of which this WebResource is a
     * part.
     */
    WebResourceRoot getWebResourceRoot();

    /**
     * Obtain the certificates that were used to sign this resource to verify
     * it or @null if none.
     *
     * @see java.util.jar.JarEntry#getCertificates()
     */
    Certificate[] getCertificates();

    /**
     * Obtain the manifest associated with this resource or @null if none.
     *
     * @see java.util.jar.JarFile#getManifest()
     */
    Manifest getManifest();
}
