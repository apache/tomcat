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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.catalina.WebResourceRoot;

public abstract class AbstractArchiveResource extends AbstractResource {

    private final String base;
    private final String baseUrl;
    private final JarEntry resource;
    private final String name;

    protected AbstractArchiveResource(WebResourceRoot root, String webAppPath,
            String base, String baseUrl, JarEntry jarEntry,
            String internalPath) {
        super(root, webAppPath);
        this.base = base;
        this.baseUrl = baseUrl;
        this.resource = jarEntry;

        String resourceName = resource.getName();
        if (resourceName.charAt(resourceName.length() - 1) == '/') {
            resourceName = resourceName.substring(0, resourceName.length() - 1);
        }
        if (internalPath.length() > 0 && resourceName.equals(
                internalPath.subSequence(1, internalPath.length()))) {
            name = "";
        } else {
            int index = resourceName.lastIndexOf('/');
            if (index == -1) {
                name = resourceName;
            } else {
                name = resourceName.substring(index + 1);
            }
        }
    }

    public String getBase() {
        return base;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public JarEntry getResource() {
        return resource;
    }

    @Override
    public long getLastModified() {
        return resource.getTime();
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return resource.isDirectory();
    }

    @Override
    public boolean isFile() {
        return !resource.isDirectory();
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getContentLength() {
        return resource.getSize();
    }

    @Override
    public String getCanonicalPath() {
        return null;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public long getCreation() {
        return resource.getTime();
    }

    @Override
    public URL getURL() {
        try {
            return new URL(baseUrl + "!/" + resource.getName());
        } catch (MalformedURLException e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("fileResource.getUrlFail",
                        resource.getName(), baseUrl), e);
            }
            return null;
        }
    }


    protected static class JarInputStreamWrapper extends InputStream {

        private final JarFile jarFile;
        private final InputStream is;


        public JarInputStreamWrapper(JarFile jarFile, InputStream is) {
            this.jarFile = jarFile;
            this.is = is;
        }


        @Override
        public int read() throws IOException {
            return is.read();
        }


        @Override
        public int read(byte[] b) throws IOException {
            return is.read(b);
        }


        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return is.read(b, off, len);
        }


        @Override
        public long skip(long n) throws IOException {
            return is.skip(n);
        }


        @Override
        public int available() throws IOException {
            return is.available();
        }


        @Override
        public void close() throws IOException {
            // Closing the JarFile releases the file lock on the JAR and also
            // closes all input streams created from the JarFile.
            jarFile.close();
        }


        @Override
        public synchronized void mark(int readlimit) {
            is.mark(readlimit);
        }


        @Override
        public synchronized void reset() throws IOException {
            is.reset();
        }


        @Override
        public boolean markSupported() {
            return is.markSupported();
        }
    }
}
