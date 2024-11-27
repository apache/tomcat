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
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.Charset;
import java.security.Permission;
import java.security.cert.Certificate;
import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;

/**
 * This class is designed to wrap a 'raw' WebResource and providing caching for expensive operations. Inexpensive
 * operations may be passed through to the underlying resource.
 */
public class CachedResource implements WebResource {

    private static final Log log = LogFactory.getLog(CachedResource.class);
    private static final StringManager sm = StringManager.getManager(CachedResource.class);

    // Estimate (on high side to be safe) of average size excluding content
    // based on profiler data.
    private static final long CACHE_ENTRY_SIZE = 500;

    private final Cache cache;
    private final StandardRoot root;
    private final String webAppPath;
    private final long ttl;
    private final int objectMaxSizeBytes;
    private final boolean usesClassLoaderResources;

    private volatile WebResource webResource;
    private volatile WebResource[] webResources;
    private volatile long nextCheck;

    private volatile Long cachedLastModified = null;
    private volatile String cachedLastModifiedHttp = null;
    private volatile byte[] cachedContent = null;
    private volatile Boolean cachedIsFile = null;
    private volatile Boolean cachedIsDirectory = null;
    private volatile Boolean cachedExists = null;
    private volatile Boolean cachedIsVirtual = null;
    private volatile Long cachedContentLength = null;
    private final Object cachedContentLengthLock = new Object();
    private volatile String cachedStrongETag = null;


    public CachedResource(Cache cache, StandardRoot root, String path, long ttl, int objectMaxSizeBytes,
            boolean usesClassLoaderResources) {
        this.cache = cache;
        this.root = root;
        this.webAppPath = path;
        this.ttl = ttl;
        this.objectMaxSizeBytes = objectMaxSizeBytes;
        this.usesClassLoaderResources = usesClassLoaderResources;
    }

    protected boolean validateResource(boolean useClassLoaderResources) {
        // It is possible that some resources will only be visible for a given
        // value of useClassLoaderResources. Therefore, if the lookup is made
        // with a different value of useClassLoaderResources than was used when
        // creating the cache entry, invalidate the entry. This should have
        // minimal performance impact as it would be unusual for a resource to
        // be looked up both as a static resource and as a class loader
        // resource.
        if (usesClassLoaderResources != useClassLoaderResources) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (webResource == null) {
            synchronized (this) {
                if (webResource == null) {
                    webResource = root.getResourceInternal(webAppPath, useClassLoaderResources);
                    getLastModified();
                    getContentLength();
                    nextCheck = ttl + now;
                    // exists() is a relatively expensive check for a file so
                    // use the fact that we know if it exists at this point
                    if (webResource instanceof EmptyResource) {
                        cachedExists = Boolean.FALSE;
                    } else {
                        cachedExists = Boolean.TRUE;
                    }
                    return true;
                }
            }
        }

        if (now < nextCheck) {
            return true;
        }

        // Assume resources inside WARs will not change
        if (!root.isPackedWarFile()) {
            WebResource webResourceInternal = root.getResourceInternal(webAppPath, useClassLoaderResources);
            if (!webResource.exists() && webResourceInternal.exists()) {
                return false;
            }

            // If modified date or length change - resource has changed / been
            // removed etc.
            if (webResource.getLastModified() != getLastModified() ||
                    webResource.getContentLength() != getContentLength()) {
                return false;
            }

            // Has a resource been inserted / removed in a different resource set
            if (webResource.getLastModified() != webResourceInternal.getLastModified() ||
                    webResource.getContentLength() != webResourceInternal.getContentLength()) {
                return false;
            }
        }

        nextCheck = ttl + now;
        return true;
    }

    protected boolean validateResources(boolean useClassLoaderResources) {
        long now = System.currentTimeMillis();

        if (webResources == null) {
            synchronized (this) {
                if (webResources == null) {
                    webResources = root.getResourcesInternal(webAppPath, useClassLoaderResources);
                    nextCheck = ttl + now;
                    return true;
                }
            }
        }

        if (now < nextCheck) {
            return true;
        }

        // Assume resources inside WARs will not change
        if (root.isPackedWarFile()) {
            nextCheck = ttl + now;
            return true;
        } else {
            // At this point, always expire the entry and re-populating it is
            // likely to be as expensive as validating it.
            return false;
        }
    }

    protected long getNextCheck() {
        return nextCheck;
    }

    @Override
    public long getLastModified() {
        if (cachedLastModified == null) {
            cachedLastModified = Long.valueOf(webResource.getLastModified());
        }
        return cachedLastModified.longValue();
    }

    @Override
    public String getLastModifiedHttp() {
        if (cachedLastModifiedHttp == null) {
            cachedLastModifiedHttp = webResource.getLastModifiedHttp();
        }
        return cachedLastModifiedHttp;
    }

    @Override
    public boolean exists() {
        if (cachedExists == null) {
            cachedExists = Boolean.valueOf(webResource.exists());
        }
        return cachedExists.booleanValue();
    }

    @Override
    public boolean isVirtual() {
        if (cachedIsVirtual == null) {
            cachedIsVirtual = Boolean.valueOf(webResource.isVirtual());
        }
        return cachedIsVirtual.booleanValue();
    }

    @Override
    public boolean isDirectory() {
        if (cachedIsDirectory == null) {
            cachedIsDirectory = Boolean.valueOf(webResource.isDirectory());
        }
        return cachedIsDirectory.booleanValue();
    }

    @Override
    public boolean isFile() {
        if (cachedIsFile == null) {
            cachedIsFile = Boolean.valueOf(webResource.isFile());
        }
        return cachedIsFile.booleanValue();
    }

    @Override
    public boolean delete() {
        boolean deleteResult = webResource.delete();
        if (deleteResult) {
            cache.removeCacheEntry(webAppPath);
        }
        return deleteResult;
    }

    @Override
    public String getName() {
        return webResource.getName();
    }

    @Override
    public long getContentLength() {
        /*
         * Cache the content length for two reasons.
         *
         * 1. It is relatively expensive to calculate, it shouldn't change and this method is called multiple times
         * during cache validation. Caching, therefore, offers a performance benefit.
         *
         * 2. There is a race condition if concurrent threads are trying to PUT and DELETE the same resource. If the
         * DELETE thread removes the cache entry after the PUT thread has created it but before validation is complete
         * the DELETE thread sees a content length for a non-existant resource but the PUT thread sees the actual
         * content length. While that isn't an issue for the individual requests it, does corrupt the cache size
         * tracking as the size used for the resource is different between when it is added to the cache and when it is
         * removed. Caching combined with locking ensures that a consistent content length is reported for the resource.
         */
        if (cachedContentLength == null) {
            synchronized (cachedContentLengthLock) {
                if (cachedContentLength == null) {
                    if (webResource == null) {
                        cachedContentLength = Long.valueOf(0);
                    } else {
                        cachedContentLength = Long.valueOf(webResource.getContentLength());
                    }
                }
            }
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
    public String getStrongETag() {
        if (cachedStrongETag == null) {
            byte[] buf = getContent();
            if (buf != null) {
                buf = ConcurrentMessageDigest.digest("SHA-1", buf);
                cachedStrongETag = "\"" + HexUtils.toHexString(buf) + "\"";
            } else {
                cachedStrongETag = webResource.getStrongETag();
            }
        }
        return cachedStrongETag;
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
        byte[] content = getContent();
        if (content == null) {
            // Can't cache InputStreams
            return webResource.getInputStream();
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public byte[] getContent() {
        if (cachedContent == null) {
            if (getContentLength() > objectMaxSizeBytes) {
                return null;
            }
            cachedContent = webResource.getContent();
        }
        return cachedContent;
    }

    @Override
    public long getCreation() {
        return webResource.getCreation();
    }

    @Override
    public URL getURL() {
        /*
         * We don't want applications using this URL to access the resource directly as that could lead to inconsistent
         * results when the resource is updated on the file system but the cache entry has not yet expired. We saw this,
         * for example, in JSP compilation.
         *
         * - last modified time was obtained via ServletContext.getResource("path").openConnection().getLastModified()
         *
         * - JSP content was obtained via ServletContext.getResourceAsStream("path")
         *
         * The result was that the JSP modification was detected but the JSP content was read from the cache so the
         * non-updated JSP page was used to generate the .java and .class file
         *
         * One option to resolve this issue is to use a custom URL scheme for resource URLs. This would allow us, via
         * registration of a URLStreamHandlerFactory, to control how the resources are accessed and ensure that all
         * access go via the cache. We took this approach for war: URLs so we can use jar:war:file: URLs to reference
         * resources in unpacked WAR files. However, because URL.setURLStreamHandlerFactory() may only be called once,
         * this can cause problems when using other libraries that also want to use a custom URL scheme.
         *
         * The approach below allows us to insert a custom URLStreamHandler without registering a custom protocol. The
         * only limitation (compared to registering a custom protocol) is that if the application constructs the same
         * URL from a String, they will access the resource directly and not via the cache.
         */
        URL resourceURL = webResource.getURL();
        if (resourceURL == null) {
            return null;
        }
        try {
            CachedResourceURLStreamHandler handler =
                    new CachedResourceURLStreamHandler(resourceURL, root, webAppPath, usesClassLoaderResources);
            @SuppressWarnings("deprecation")
            URL result = new URL(null, resourceURL.toExternalForm(), handler);
            handler.setCacheURL(result);
            return result;
        } catch (MalformedURLException e) {
            log.error(sm.getString("cachedResource.invalidURL", resourceURL.toExternalForm()), e);
            return null;
        }
    }

    @Override
    public URL getCodeBase() {
        return webResource.getCodeBase();
    }

    @Override
    public Certificate[] getCertificates() {
        return webResource.getCertificates();
    }

    @Override
    public Manifest getManifest() {
        return webResource.getManifest();
    }

    @Override
    public WebResourceRoot getWebResourceRoot() {
        return webResource.getWebResourceRoot();
    }

    WebResource getWebResource() {
        return webResource;
    }

    WebResource[] getWebResources() {
        return webResources;
    }

    boolean usesClassLoaderResources() {
        return usesClassLoaderResources;
    }


    // Assume that the cache entry will always include the content unless the
    // resource content is larger than objectMaxSizeBytes. This isn't always the
    // case but it makes tracking the current cache size easier.
    long getSize() {
        long result = CACHE_ENTRY_SIZE;
        // Longer paths use a noticeable amount of memory so account for this in
        // the cache size. The fixed component of a String instance's memory
        // usage is accounted for in the 500 bytes above.
        result += getWebappPath().length() * 2;
        if (getContentLength() <= objectMaxSizeBytes) {
            result += getContentLength();
        }
        return result;
    }


    /*
     * Mimics the behaviour of FileURLConnection.getInputStream for a directory. Deliberately uses default locale.
     */
    private static InputStream buildInputStream(String[] files) {
        Arrays.sort(files, Collator.getInstance(Locale.getDefault()));
        StringBuilder result = new StringBuilder();
        for (String file : files) {
            result.append(file);
            // Every entry is followed by \n including the last
            result.append('\n');
        }
        return new ByteArrayInputStream(result.toString().getBytes(Charset.defaultCharset()));
    }


    /**
     * URLStreamHandler to handle a URL for a cached resource, delegating reads to the Cache.
     * <ul>
     * <li>delegates reads to the Cache, to ensure consistent invalidation behavior</li>
     * <li>delegates hashCode()/ equals() behavior to the underlying Resource URL. (Equinox/ OSGi compatibility)</li>
     * <li>detects the case where a new relative URL is created from the wrapped URL, inheriting its handler; in this
     * case reverts to default behavior</li>
     * </ul>
     */
    private static class CachedResourceURLStreamHandler extends URLStreamHandler {

        private final URL resourceURL;
        private final StandardRoot root;
        private final String webAppPath;
        private final boolean usesClassLoaderResources;

        private URL cacheURL = null;

        CachedResourceURLStreamHandler(URL resourceURL, StandardRoot root, String webAppPath,
                boolean usesClassLoaderResources) {
            this.resourceURL = resourceURL;
            this.root = root;
            this.webAppPath = webAppPath;
            this.usesClassLoaderResources = usesClassLoaderResources;
        }

        protected void setCacheURL(URL cacheURL) {
            this.cacheURL = cacheURL;
        }

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            // This deliberately uses ==. If u isn't the URL object this
            // URLStreamHandler was constructed for we do not want to use this
            // URLStreamHandler to create a connection.
            if (cacheURL != null && u == cacheURL) {
                if ("jar".equals(cacheURL.getProtocol())) {
                    return new CachedResourceJarURLConnection(resourceURL, root, webAppPath, usesClassLoaderResources);
                } else {
                    return new CachedResourceURLConnection(resourceURL, root, webAppPath, usesClassLoaderResources);
                }
            } else {
                // This stream handler has been inherited by a URL that was constructed from a cache URL.
                // We need to break that link.
                URI constructedURI;
                try {
                    constructedURI = new URI(u.toExternalForm());
                } catch (URISyntaxException e) {
                    // Not ideal but consistent with API
                    throw new IOException(e);
                }
                URL constructedURL = constructedURI.toURL();
                return constructedURL.openConnection();
            }
        }

        /**
         * {@inheritDoc}
         * <p>
         * We don't know what the requirements are for equals for the wrapped resourceURL so if u1 is the cacheURL,
         * delegate to the resourceURL and it's handler. Otherwise, use the default implementation from
         * URLStreamHandler.
         */
        @Override
        protected boolean equals(URL u1, URL u2) {
            // Deliberate use of ==
            if (cacheURL == u1) {
                return resourceURL.equals(u2);
            }
            // Not the cacheURL. This stream handler has been inherited by a URL that was constructed from a cache URL.
            // Use the default implementation from URLStreamHandler.
            return super.equals(u1, u2);
        }

        /**
         * {@inheritDoc}
         * <p>
         * We don't know what the requirements are for hashcode for the wrapped resourceURL so if u1 is the cacheURL,
         * delegate to the resourceURL and it's handler. Otherwise, use the default implementation from
         * URLStreamHandler.
         */
        @Override
        protected int hashCode(URL u) {
            // Deliberate use of ==
            if (cacheURL == u) {
                return resourceURL.hashCode();
            }
            // Not the cacheURL. This stream handler has been inherited by a URL that was constructed from a cache URL.
            // Use the default implementation from URLStreamHandler.
            return super.hashCode(u);
        }
    }


    /*
     * Keep this in sync with CachedResourceJarURLConnection.
     */
    private static class CachedResourceURLConnection extends URLConnection {

        private final StandardRoot root;
        private final String webAppPath;
        private final boolean usesClassLoaderResources;
        private final URL resourceURL;

        protected CachedResourceURLConnection(URL resourceURL, StandardRoot root, String webAppPath,
                boolean usesClassLoaderResources) {
            super(resourceURL);
            this.root = root;
            this.webAppPath = webAppPath;
            this.usesClassLoaderResources = usesClassLoaderResources;
            this.resourceURL = resourceURL;
        }

        @Override
        public void connect() throws IOException {
            // NO-OP
        }

        @Override
        public InputStream getInputStream() throws IOException {
            WebResource resource = getResource();
            if (resource.isDirectory()) {
                return buildInputStream(resource.getWebResourceRoot().list(webAppPath));
            } else {
                return getResource().getInputStream();
            }
        }

        @Override
        public Permission getPermission() throws IOException {
            // Doesn't trigger a call to connect for file:// URLs
            return resourceURL.openConnection().getPermission();
        }

        @Override
        public long getLastModified() {
            return getResource().getLastModified();
        }

        @Override
        public long getContentLengthLong() {
            return getResource().getContentLength();
        }

        private WebResource getResource() {
            return root.getResource(webAppPath, false, usesClassLoaderResources);
        }
    }


    /*
     * Keep this in sync with CachedResourceURLConnection.
     */
    private static class CachedResourceJarURLConnection extends JarURLConnection {

        private final StandardRoot root;
        private final String webAppPath;
        private final boolean usesClassLoaderResources;
        private final URL resourceURL;

        protected CachedResourceJarURLConnection(URL resourceURL, StandardRoot root, String webAppPath,
                boolean usesClassLoaderResources) throws IOException {
            super(resourceURL);
            this.root = root;
            this.webAppPath = webAppPath;
            this.usesClassLoaderResources = usesClassLoaderResources;
            this.resourceURL = resourceURL;
        }

        @Override
        public void connect() throws IOException {
            // NO-OP
        }

        @Override
        public InputStream getInputStream() throws IOException {
            WebResource resource = getResource();
            if (resource.isDirectory()) {
                return buildInputStream(resource.getWebResourceRoot().list(webAppPath));
            } else {
                return getResource().getInputStream();
            }
        }

        @Override
        public Permission getPermission() throws IOException {
            // Doesn't trigger a call to connect for jar:// URLs
            return resourceURL.openConnection().getPermission();
        }

        @Override
        public long getLastModified() {
            return getResource().getLastModified();
        }

        @Override
        public long getContentLengthLong() {
            return getResource().getContentLength();
        }

        private WebResource getResource() {
            return root.getResource(webAppPath, false, usesClassLoaderResources);
        }

        @Override
        public JarFile getJarFile() throws IOException {
            return ((JarURLConnection) resourceURL.openConnection()).getJarFile();
        }

    }
}
