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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;

/**
 * This class is designed to wrap a 'raw' WebResource and providing caching for
 * expensive operations. Inexpensive operations may be passed through to the
 * underlying resource.
 */
public class CachedResource implements WebResource {

    private final StandardRoot root;
    private final String webAppPath;
    private final long ttl;

    private volatile WebResource webResource;
    private volatile long nextCheck;

    private volatile Long cachedLastModified = null;
    private volatile String cachedLastModifiedHttp = null;
    private volatile byte[] cachedContent = null;
    private volatile Boolean cachedIsFile = null;
    private volatile Boolean cachedIsDirectory = null;
    private volatile Boolean cachedExists = null;
    private volatile Long cachedContentLength = null;


    public CachedResource(StandardRoot root, String path, long ttl) {
        this.root = root;
        this.webAppPath = path;
        this.ttl = ttl;
    }

    protected boolean validate() {
        long now = System.currentTimeMillis();

        if (webResource == null) {
            synchronized (this) {
                if (webResource == null) {
                    webResource = root.getResourceInternal(webAppPath);
                    getLastModified();
                    getContentLength();
                    nextCheck = ttl + now;
                    return true;
                }
            }
        }

        if (now < nextCheck) {
            return true;
        }

        // If modified date or length change - resource has changed / been
        // removed etc.
        if (webResource.getLastModified() != getLastModified() ||
                webResource.getContentLength() != getContentLength()) {
            return false;
        }

        nextCheck = ttl + now;
        return true;
    }

    protected long getNextCheck() {
        return nextCheck;
    }

    @Override
    public long getLastModified() {
        Long cachedLastModified = this.cachedLastModified;
        if (cachedLastModified == null) {
            cachedLastModified =
                    Long.valueOf(webResource.getLastModified());
            this.cachedLastModified = cachedLastModified;
        }
        return cachedLastModified.longValue();
    }

    @Override
    public String getLastModifiedHttp() {
        String cachedLastModifiedHttp = this.cachedLastModifiedHttp;
        if (cachedLastModifiedHttp == null) {
            cachedLastModifiedHttp = webResource.getLastModifiedHttp();
            this.cachedLastModifiedHttp = cachedLastModifiedHttp;
        }
        return cachedLastModifiedHttp;
    }

    @Override
    public boolean exists() {
        Boolean cachedExists = this.cachedExists;
        if (cachedExists == null) {
            cachedExists = Boolean.valueOf(webResource.exists());
            this.cachedExists = cachedExists;
        }
        return cachedExists.booleanValue();
    }

    @Override
    public boolean isDirectory() {
        Boolean cachedIsDirectory = this.cachedIsDirectory;
        if (cachedIsDirectory == null) {
            cachedIsDirectory = Boolean.valueOf(webResource.isDirectory());
            this.cachedIsDirectory = cachedIsDirectory;
        }
        return cachedIsDirectory.booleanValue();
    }

    @Override
    public boolean isFile() {
        Boolean cachedIsFile = this.cachedIsFile;
        if (cachedIsFile == null) {
            cachedIsFile = Boolean.valueOf(webResource.isFile());
            this.cachedIsFile = cachedIsFile;
        }
        return cachedIsFile.booleanValue();
    }

    @Override
    public boolean delete() {
        return webResource.delete();
    }

    @Override
    public String getName() {
        return webResource.getName();
    }

    @Override
    public long getContentLength() {
        Long cachedContentLength = this.cachedContentLength;
        if (cachedContentLength == null) {
            cachedContentLength = Long.valueOf(webResource.getContentLength());
            this.cachedContentLength = cachedContentLength;
        }
        return cachedContentLength.longValue();
    }

    @Override
    public String getCanonicalPath() {
        return webResource.getCanonicalPath();
    }

    @Override
    public boolean canRead() {
        return webResource.canRead();
    }

    @Override
    public String getWebappPath() {
        return webAppPath;
    }

    @Override
    public String getETag() {
        return webResource.getETag();
    }

    @Override
    public void setMimeType(String mimeType) {
        webResource.setMimeType(mimeType);
    }

    @Override
    public String getMimeType() {
        return webResource.getMimeType();
    }

    @Override
    public InputStream getInputStream() {
        byte[] content = cachedContent;
        if (content == null) {
            // Can't cache InputStreams
            return webResource.getInputStream();
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public byte[] getContent() {
        byte[] cachedContent = this.cachedContent;
        if (cachedContent == null) {
            cachedContent = webResource.getContent();
            this.cachedContent = cachedContent;
        }
        return cachedContent;
    }

    @Override
    public long getCreation() {
        return webResource.getCreation();
    }

    @Override
    public URL getURL() {
        return webResource.getURL();
    }

    @Override
    public WebResourceRoot getWebResourceRoot() {
        return webResource.getWebResourceRoot();
    }
}
