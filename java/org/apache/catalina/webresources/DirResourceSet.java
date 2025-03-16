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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Manifest;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceLockSet;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceRoot.ResourceSetType;
import org.apache.catalina.util.ResourceSet;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.http.RequestUtil;

/**
 * Represents a {@link org.apache.catalina.WebResourceSet} based on a directory.
 */
public class DirResourceSet extends AbstractFileResourceSet implements WebResourceLockSet {

    private static final Log log = LogFactory.getLog(DirResourceSet.class);

    private final Map<String,ResourceLock> resourceLocksByPath = new HashMap<>();
    private final Object resourceLocksByPathLock = new Object();


    /**
     * A no argument constructor is required for this to work with the digester.
     */
    public DirResourceSet() {
        super("/");
    }

    /**
     * Creates a new {@link org.apache.catalina.WebResourceSet} based on a directory.
     *
     * @param root         The {@link WebResourceRoot} this new {@link org.apache.catalina.WebResourceSet} will be added
     *                         to.
     * @param webAppMount  The path within the web application at which this {@link org.apache.catalina.WebResourceSet}
     *                         will be mounted. For example, to add a directory of JARs to a web application, the
     *                         directory would be mounted at "/WEB-INF/lib/"
     * @param base         The absolute path to the directory on the file system from which the resources will be
     *                         served.
     * @param internalPath The path within this new {@link org.apache.catalina.WebResourceSet} where resources will be
     *                         served from.
     */
    public DirResourceSet(WebResourceRoot root, String webAppMount, String base, String internalPath) {
        super(internalPath);
        setRoot(root);
        setWebAppMount(webAppMount);
        setBase(base);

        if (root.getContext().getAddWebinfClassesResources()) {
            File f = new File(base, internalPath);
            f = new File(f, "/WEB-INF/classes/META-INF/resources");

            if (f.isDirectory()) {
                root.createWebResourceSet(ResourceSetType.RESOURCE_JAR, "/", f.getAbsolutePath(), null, "/");
            }
        }

        if (getRoot().getState().isAvailable()) {
            try {
                start();
            } catch (LifecycleException e) {
                throw new IllegalStateException(e);
            }
        }
    }


    @SuppressWarnings("null") // lock can never be null when lock.key is read
    @Override
    public WebResource getResource(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();
        WebResourceRoot root = getRoot();
        boolean readOnly = isReadOnly();
        if (path.startsWith(webAppMount)) {
            /*
             * Lock the path for reading until the WebResource has been constructed. The lock prevents concurrent reads
             * and writes (e.g. HTTP GET and PUT / DELETE) for the same path causing corruption of the FileResource
             * where some of the fields are set as if the file exists and some as set as if it does not.
             */
            ResourceLock lock = readOnly ? null : lockForRead(path);
            try {
                File f = file(path.substring(webAppMount.length()), false);
                if (f == null) {
                    return new EmptyResource(root, path);
                }
                if (!f.exists()) {
                    return new EmptyResource(root, path, f);
                }
                if (f.isDirectory() && path.charAt(path.length() - 1) != '/') {
                    path = path + '/';
                }
                return new FileResource(root, path, f, readOnly, getManifest(), this, readOnly ? null : lock.key);
            } finally {
                if (!readOnly) {
                    unlockForRead(lock);
                }
            }
        } else {
            return new EmptyResource(root, path);
        }
    }


    @Override
    public String[] list(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();
        if (path.startsWith(webAppMount)) {
            File f = file(path.substring(webAppMount.length()), true);
            if (f == null) {
                return EMPTY_STRING_ARRAY;
            }
            String[] result = f.list();
            return Objects.requireNonNullElse(result, EMPTY_STRING_ARRAY);
        } else {
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            if (webAppMount.startsWith(path)) {
                int i = webAppMount.indexOf('/', path.length());
                if (i == -1) {
                    return new String[] { webAppMount.substring(path.length()) };
                } else {
                    return new String[] { webAppMount.substring(path.length(), i) };
                }
            }
            return EMPTY_STRING_ARRAY;
        }
    }

    @Override
    public Set<String> listWebAppPaths(String path) {
        checkPath(path);
        String webAppMount = getWebAppMount();
        ResourceSet<String> result = new ResourceSet<>();
        if (path.startsWith(webAppMount)) {
            File f = file(path.substring(webAppMount.length()), true);
            if (f != null) {
                File[] list = f.listFiles();
                if (list != null) {
                    for (File entry : list) {
                        // f has already been validated so the following checks
                        // can be much simpler than those in file()
                        if (!getRoot().getAllowLinking()) {
                            // allow linking is disabled so need to check for
                            // symlinks
                            boolean symlink = true;
                            String absPath = null;
                            String canPath = null;
                            try {
                                // We know that 'f' must be valid since it will
                                // have been checked in the call to file()
                                // above. Therefore strip off the path of the
                                // path that was contributed by 'f' and check
                                // that what is left does not contain a symlink.
                                absPath = entry.getAbsolutePath().substring(f.getAbsolutePath().length());
                                String entryCanPath = entry.getCanonicalPath();
                                String fCanPath = f.getCanonicalPath();
                                if (entryCanPath.length() >= fCanPath.length()) {
                                    canPath = entryCanPath.substring(fCanPath.length());
                                    if (absPath.equals(canPath)) {
                                        symlink = false;
                                    }
                                }
                            } catch (IOException ioe) {
                                // Ignore the exception. Assume we have a symlink.
                                canPath = "Unknown";
                            }
                            if (symlink) {
                                logIgnoredSymlink(getRoot().getContext().getName(), absPath, canPath);
                                continue;
                            }
                        }
                        StringBuilder sb = new StringBuilder(path);
                        if (path.charAt(path.length() - 1) != '/') {
                            sb.append('/');
                        }
                        sb.append(entry.getName());
                        if (entry.isDirectory()) {
                            sb.append('/');
                        }
                        result.add(sb.toString());
                    }
                }
            }
        } else {
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            if (webAppMount.startsWith(path)) {
                int i = webAppMount.indexOf('/', path.length());
                if (i == -1) {
                    result.add(webAppMount + "/");
                } else {
                    result.add(webAppMount.substring(0, i + 1));
                }
            }
        }
        result.setLocked(true);
        return result;
    }

    @Override
    public boolean mkdir(String path) {
        checkPath(path);
        if (isReadOnly()) {
            return false;
        }
        String webAppMount = getWebAppMount();
        if (path.startsWith(webAppMount)) {
            File f = file(path.substring(webAppMount.length()), false);
            if (f == null) {
                return false;
            }
            return f.mkdir();
        } else {
            return false;
        }
    }

    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        checkPath(path);

        if (is == null) {
            throw new NullPointerException(sm.getString("dirResourceSet.writeNpe"));
        }

        if (isReadOnly()) {
            return false;
        }

        // write() is meant to create a file so ensure that the path doesn't
        // end in '/'
        if (path.endsWith("/")) {
            return false;
        }

        String webAppMount = getWebAppMount();
        if (!path.startsWith(webAppMount)) {
            return false;
        }

        File dest;
        /*
         * Lock the path for writing until the write is complete. The lock prevents concurrent reads and writes (e.g.
         * HTTP GET and PUT / DELETE) for the same path causing corruption of the FileResource where some of the fields
         * are set as if the file exists and some as set as if it does not.
         */
        ResourceLock lock = lockForWrite(path);
        try {
            dest = file(path.substring(webAppMount.length()), false);
            if (dest == null) {
                return false;
            }

            if (dest.exists() && !overwrite) {
                return false;
            }

            try {
                if (overwrite) {
                    Files.copy(is, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.copy(is, dest.toPath());
                }
            } catch (IOException ioe) {
                return false;
            }

            return true;
        } finally {
            unlockForWrite(lock);
        }
    }

    @Override
    protected void checkType(File file) {
        if (!file.isDirectory()) {
            throw new IllegalArgumentException(
                    sm.getString("dirResourceSet.notDirectory", getBase(), File.separator, getInternalPath()));
        }
    }

    // -------------------------------------------------------- Lifecycle methods
    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        // Is this an exploded web application?
        if (getWebAppMount().isEmpty()) {
            // Look for a manifest
            File mf = file("META-INF/MANIFEST.MF", true);
            if (mf != null && mf.isFile()) {
                try (FileInputStream fis = new FileInputStream(mf)) {
                    setManifest(new Manifest(fis));
                } catch (IOException e) {
                    log.warn(sm.getString("dirResourceSet.manifestFail", mf.getAbsolutePath()), e);
                }
            }
        }
        // Check for exposure to CVE-2024-56337
        if (isReadOnly()) {
            // CVE-2024-56337 (nor CVE-2024-50379) is not exploitable on a read-only ResourceSet
            return;
        }
        if (JreCompat.getInstance().isCanonCachesDisabled()) {
            // CVE-2024-56337 (nor CVE-2024-50379) is not exploitable if the canonical file name cache is disabled
            return;
        }
        // This ResourceSet may be exposed to CVE-2024-56337.
        if (JreCompat.getInstance().disableCanonCaches()) {
            /*
             * The canonical file name cache was enabled and is now disabled.
             */
            log.warn(sm.getString("dirResourceSet.canonCaches.disabled", getFileBase(),
                    getRoot().getContext().getName()));
        } else {
            /*
             * The canonical file name cache could not be disabled (or Tomcat cannot confirm it has been disabled). This
             * ResourceSet may be exposed to CVE-2024-56337.
             */
            throw new IllegalStateException(sm.getString("dirResourceSet.canonCaches.enabled", getFileBase(),
                    getRoot().getContext().getName()));
        }
    }


    private String getLockKey(String path) {
        /*
         * Normalize path to ensure that the same key is used for the same path. Always convert path to lower case as
         * the file system may be case insensitive. A minor performance improvement is possible by removing the
         * conversion to lower case for case sensitive file systems but confirming that all the directories within a
         * DirResourceSet are case sensitive is much harder than it might first appear due to various edge cases. In
         * particular, Windows can make individual directories case sensitive and File.getCanonicalPath() doesn't return
         * the canonical file name on Linux for some case insensitive file systems (such as mounted Windows shares).
         */
        return RequestUtil.normalize(path).toLowerCase(Locale.ENGLISH);
    }


    @Override
    public ResourceLock lockForRead(String path) {
        String key = getLockKey(path);
        ResourceLock resourceLock;
        synchronized (resourceLocksByPathLock) {
            /*
             * Obtain the ResourceLock and increment the usage count inside the sync to ensure that that map always has
             * a consistent view of the currently "in-use" ResourceLocks.
             */
            resourceLock = resourceLocksByPath.get(key);
            if (resourceLock == null) {
                resourceLock = new ResourceLock(key);
                resourceLocksByPath.put(key, resourceLock);
            }
            resourceLock.count.incrementAndGet();
        }
        // Obtain the lock outside the sync as it will block if there is a current write lock.
        resourceLock.reentrantLock.readLock().lock();
        return resourceLock;
    }


    @Override
    public void unlockForRead(ResourceLock resourceLock) {
        // Unlock outside the sync as there is no need to do it inside.
        resourceLock.reentrantLock.readLock().unlock();
        synchronized (resourceLocksByPathLock) {
            /*
             * Decrement the usage count and remove ResourceLocks no longer required inside the sync to ensure that that
             * map always has a consistent view of the currently "in-use" ResourceLocks.
             */
            if (resourceLock.count.decrementAndGet() == 0) {
                resourceLocksByPath.remove(resourceLock.key);
            }
        }
    }


    @Override
    public ResourceLock lockForWrite(String path) {
        String key = getLockKey(path);
        ResourceLock resourceLock;
        synchronized (resourceLocksByPathLock) {
            /*
             * Obtain the ResourceLock and increment the usage count inside the sync to ensure that that map always has
             * a consistent view of the currently "in-use" ResourceLocks.
             */
            resourceLock = resourceLocksByPath.get(key);
            if (resourceLock == null) {
                resourceLock = new ResourceLock(key);
                resourceLocksByPath.put(key, resourceLock);
            }
            resourceLock.count.incrementAndGet();
        }
        // Obtain the lock outside the sync as it will block if there are any other current locks.
        resourceLock.reentrantLock.writeLock().lock();
        return resourceLock;
    }


    @Override
    public void unlockForWrite(ResourceLock resourceLock) {
        // Unlock outside the sync as there is no need to do it inside.
        resourceLock.reentrantLock.writeLock().unlock();
        synchronized (resourceLocksByPathLock) {
            /*
             * Decrement the usage count and remove ResourceLocks no longer required inside the sync to ensure that that
             * map always has a consistent view of the currently "in-use" ResourceLocks.
             */
            if (resourceLock.count.decrementAndGet() == 0) {
                resourceLocksByPath.remove(resourceLock.key);
            }
        }
    }
}
