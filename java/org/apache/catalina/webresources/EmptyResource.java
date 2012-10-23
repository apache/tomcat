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
import java.net.URL;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;

public class EmptyResource implements WebResource {

    private final WebResourceRoot root;
    private final String webAppPath;

    public EmptyResource(WebResourceRoot root, String webAppPath) {
        this.root = root;
        this.webAppPath = webAppPath;
    }

    @Override
    public long getLastModified() {
        return 0;
    }

    @Override
    public String getLastModifiedHttp() {
        return null;
    }

    @Override
    public boolean exists() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public boolean isFile() {
        return false;
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public String getName() {
        int index = webAppPath.lastIndexOf('/');
        if (index == -1) {
            return webAppPath;
        } else {
            return webAppPath.substring(index + 1);
        }
    }

    @Override
    public long getContentLength() {
        return 0;
    }

    @Override
    public String getCanonicalPath() {
        return null;
    }

    @Override
    public boolean canRead() {
        return false;
    }

    @Override
    public String getWebappPath() {
        return webAppPath;
    }

    @Override
    public String getETag() {
        return null;
    }

    @Override
    public void setMimeType(String mimeType) {
        // NOOP
    }

    @Override
    public String getMimeType() {
        return null;
    }

    @Override
    public InputStream getInputStream() {
        return null;
    }

    @Override
    public byte[] getContent() {
        return null;
    }

    @Override
    public long getCreation() {
        return 0;
    }

    @Override
    public URL getURL() {
        return null;
    }

    @Override
    public WebResourceRoot getWebResourceRoot() {
        return root;
    }
}
