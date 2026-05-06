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
package org.apache.catalina.webresources;

import java.io.InputStream;
import java.security.MessageDigest;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;

/**
 * Abstract {@link WebResource} implementation that provides common functionality for all web resource implementations.
 */
public abstract class AbstractResource implements WebResource {

    /** The string manager for this class. */
    protected static final StringManager sm = StringManager.getManager(AbstractResource.class);

    /** The root. */
    private final WebResourceRoot root;
    /** The web app path. */
    private final String webAppPath;

    /** The MIME type. */
    private String mimeType = null;
    /** The weak ETag. */
    private volatile String weakETag;
    /** The strong ETag. */
    private volatile String strongETag;


    /**
     * Constructs a new AbstractResource.
     *
     * @param root        The root
     * @param webAppPath  The web app path
     */
    protected AbstractResource(WebResourceRoot root, String webAppPath) {
        this.root = root;
        this.webAppPath = webAppPath;
    }


    /**
     * Gets the web resource root.
     *
     * @return The web resource root
     */
    @Override
    public final WebResourceRoot getWebResourceRoot() {
        return root;
    }


    /**
     * Gets the web app path.
     *
     * @return The web app path
     */
    @Override
    public final String getWebappPath() {
        return webAppPath;
    }


    /**
     * Gets the last modified date as an HTTP date string.
     *
     * @return The last modified date as an HTTP date string
     */
    @Override
    public final String getLastModifiedHttp() {
        return FastHttpDateFormat.formatDate(getLastModified());
    }


    /**
     * Gets the weak ETag for this resource.
     *
     * @return The weak ETag
     */
    @Override
    public final String getETag() {
        if (weakETag == null) {
            synchronized (this) {
                if (weakETag == null) {
                    long contentLength = getContentLength();
                    long lastModified = getLastModified();
                    if ((contentLength >= 0) || (lastModified >= 0)) {
                        weakETag = "W/\"" + contentLength + "-" + lastModified + "\"";
                    }
                }
            }
        }
        return weakETag;
    }

    /**
     * Gets the strong ETag for this resource.
     *
     * @return The strong ETag
     */
    @Override
    public final String getStrongETag() {
        if (strongETag == null) {
            synchronized (this) {
                if (strongETag == null) {
                    long contentLength = getContentLength();
                    long lastModified = getLastModified();
                    if (contentLength > 0 && lastModified > 0) {
                        if (contentLength <= 16 * 1024) {
                            byte[] buf = getContent();
                            if (buf != null) {
                                buf = ConcurrentMessageDigest.digestSHA256(buf);
                                strongETag = "\"" + HexUtils.toHexString(buf) + "\"";
                            } else {
                                strongETag = getETag();
                            }
                        } else {
                            byte[] buf = new byte[4096];
                            try (InputStream is = getInputStream()) {
                                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                                while (true) {
                                    int n = is.read(buf);
                                    if (n <= 0) {
                                        break;
                                    }
                                    digest.update(buf, 0, n);
                                }
                                strongETag = "\"" + HexUtils.toHexString(digest.digest()) + "\"";
                            } catch (Exception e) {
                                strongETag = getETag();
                            }
                        }
                    } else {
                        strongETag = getETag();
                    }
                }
            }
        }
        return strongETag;
    }

    /**
     * Sets the MIME type for this resource.
     *
     * @param mimeType The MIME type
     */
    @Override
    public final void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }


    /**
     * Gets the MIME type for this resource.
     *
     * @return The MIME type
     */
    @Override
    public final String getMimeType() {
        if (mimeType == null) {
            String name = getName();
            int extensionStart = name.lastIndexOf('.');
            if (extensionStart > -1) {
                String extension = name.substring(extensionStart + 1);
                mimeType = root.getContext().findMimeMapping(extension);
            }
        }
        return mimeType;
    }


    /**
     * Gets the input stream for this resource.
     *
     * @return the input stream for this resource
     */
    @Override
    public final InputStream getInputStream() {
        InputStream is = doGetInputStream();

        if (is == null || !root.getTrackLockedFiles()) {
            return is;
        }

        return new TrackedInputStream(root, getName(), is);
    }

    /**
     * Returns the input stream for this resource.
     *
     * @return the input stream for this resource
     */
    protected abstract InputStream doGetInputStream();


    /**
     * Gets the logger for this resource.
     *
     * @return the logger
     */
    protected abstract Log getLog();
}
