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
import java.security.cert.Certificate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.catalina.WebResourceRoot;

public abstract class AbstractArchiveResource extends AbstractResource {

    private final String base;
    private final String baseUrl;
    private final JarEntry resource;
    private final Manifest manifest;
    private final String name;
    private boolean readCerts = false;
    private Certificate[] certificates;

    protected AbstractArchiveResource(WebResourceRoot root, String webAppPath,
            String base, String baseUrl, JarEntry jarEntry,
            String internalPath, Manifest manifest) {
        super(root, webAppPath);
        this.base = base;
        this.baseUrl = baseUrl;
        this.resource = jarEntry;
        this.manifest = manifest;

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

    protected final String getBase() {
        return base;
    }

    protected final String getBaseUrl() {
        return baseUrl;
    }

    protected final JarEntry getResource() {
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

    @Override
    public final byte[] getContent() {
        long len = getContentLength();

        if (len > Integer.MAX_VALUE) {
            // Can't create an array that big
            throw new ArrayIndexOutOfBoundsException(sm.getString(
                    "abstractResource.getContentTooLarge", getWebappPath(),
                    Long.valueOf(len)));
        }

        int size = (int) len;
        byte[] result = new byte[size];

        int pos = 0;
        try (JarInputStreamWrapper jisw = getJarInputStreamWrapper()) {
            while (pos < size) {
                int n = jisw.read(result, pos, size - pos);
                if (n < 0) {
                    break;
                }
                pos += n;
            }
            // Once the stream has been read, read the certs
            certificates = jisw.getCertificates();
            readCerts = true;
        } catch (IOException ioe) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("abstractResource.getContentFail",
                        getWebappPath()), ioe);
            }
        }

        return result;
    }


    @Override
    public Certificate[] getCertificates() {
        if (!readCerts) {
            // TODO - get content first
            throw new IllegalStateException();
        }
        return certificates;
    }

    @Override
    public Manifest getManifest() {
        return manifest;
    }

    @Override
    protected final InputStream doGetInputStream() {
        return getJarInputStreamWrapper();
    }

    protected abstract JarInputStreamWrapper getJarInputStreamWrapper();

    protected class JarInputStreamWrapper extends InputStream {

        private final JarFile jarFile;
        private final JarEntry jarEntry;
        private final InputStream is;


        public JarInputStreamWrapper(JarFile jarFile, JarEntry jarEntry, InputStream is) {
            this.jarFile = jarFile;
            this.jarEntry = jarEntry;
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

        public Certificate[] getCertificates() {
            return jarEntry.getCertificates();
        }
    }
}
