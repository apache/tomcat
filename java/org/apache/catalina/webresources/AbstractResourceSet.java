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

import java.util.jar.Manifest;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.util.LifecycleBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * Abstract {@link WebResourceSet} implementation that provides common functionality for all web resource set
 * implementations.
 */
public abstract class AbstractResourceSet extends LifecycleBase implements WebResourceSet {

    /** Constructs a new AbstractResourceSet. */
    protected AbstractResourceSet() {}

    /** The root. */
    private WebResourceRoot root;
    /** The base. */
    private String base;
    /** The internal path. */
    private String internalPath = "";
    /** The web app mount. */
    private String webAppMount;
    /** Whether this resource set is class loader only. */
    private boolean classLoaderOnly;
    /** Whether this resource set is static only. */
    private boolean staticOnly;
    /** The manifest. */
    private Manifest manifest;


    /** The string manager for this class. */
    protected static final StringManager sm = StringManager.getManager(AbstractResourceSet.class);


    /**
     * Checks that the given path is valid.
     *
     * @param path The path to check
     *
     * @throws IllegalArgumentException if the path is invalid
     */
    protected final void checkPath(String path) {
        if (path == null || path.isEmpty() || path.charAt(0) != '/') {
            throw new IllegalArgumentException(sm.getString("abstractResourceSet.checkPath", path));
        }
    }

    /**
     * Sets the root.
     *
     * @param root The root
     */
    @Override
    public final void setRoot(WebResourceRoot root) {
        this.root = root;
    }

    /**
     * Gets the root.
     *
     * @return The root
     */
    protected final WebResourceRoot getRoot() {
        return root;
    }


    /**
     * Gets the internal path.
     *
     * @return The internal path
     */
    protected final String getInternalPath() {
        return internalPath;
    }

    /**
     * Sets the internal path.
     *
     * @param internalPath The internal path
     */
    public final void setInternalPath(String internalPath) {
        checkPath(internalPath);
        // Optimise internal processing
        if (internalPath.equals("/")) {
            this.internalPath = "";
        } else {
            this.internalPath = internalPath;
        }
    }

    /**
     * Sets the web app mount.
     *
     * @param webAppMount The web app mount
     */
    public final void setWebAppMount(String webAppMount) {
        checkPath(webAppMount);
        /*
         * Originally, only "/" was changed to "" to allow some optimisations. The fix for CVE-2025-49125 means that
         * mounted WebResourceSets will break if webAppMount ends in '/'. So now the trailing "/" is removed in all
         * cases.
         */
        if (webAppMount.endsWith("/")) {
            this.webAppMount = webAppMount.substring(0, webAppMount.length() - 1);
        } else {
            this.webAppMount = webAppMount;
        }
    }

    /**
     * Gets the web app mount.
     *
     * @return The web app mount
     */
    protected final String getWebAppMount() {
        return webAppMount;
    }

    /**
     * Checks if the given path is mounted at the given web app mount.
     *
     * @param path         The path to check
     * @param webAppMount  The web app mount
     *
     * @return {@code true} if the path is mounted
     */
    protected boolean isPathMounted(String path, String webAppMount) {
        // Doesn't call getWebAppMount() as value might have changed
        if (path.startsWith(webAppMount)) {
            if (path.length() != webAppMount.length() && path.charAt(webAppMount.length()) != '/') {
                return false;
            }
            return true;
        }
        return false;
    }


    /**
     * Sets the base.
     *
     * @param base The base
     */
    public final void setBase(String base) {
        this.base = base;
    }

    /**
     * Gets the base.
     *
     * @return The base
     */
    protected final String getBase() {
        return base;
    }

    /**
     * Checks if this resource set is class loader only.
     *
     * @return {@code true} if this resource set is class loader only
     */
    @Override
    public boolean getClassLoaderOnly() {
        return classLoaderOnly;
    }

    /**
     * Sets whether this resource set is class loader only.
     *
     * @param classLoaderOnly {@code true} if this resource set is class loader only
     */
    @Override
    public void setClassLoaderOnly(boolean classLoaderOnly) {
        this.classLoaderOnly = classLoaderOnly;
    }

    /**
     * Checks if this resource set is static only.
     *
     * @return {@code true} if this resource set is static only
     */
    @Override
    public boolean getStaticOnly() {
        return staticOnly;
    }

    /**
     * Sets whether this resource set is static only.
     *
     * @param staticOnly {@code true} if this resource set is static only
     */
    @Override
    public void setStaticOnly(boolean staticOnly) {
        this.staticOnly = staticOnly;
    }

    /**
     * Sets the manifest.
     *
     * @param manifest The manifest
     */
    protected final void setManifest(Manifest manifest) {
        this.manifest = manifest;
    }

    /**
     * Gets the manifest.
     *
     * @return The manifest
     */
    protected final Manifest getManifest() {
        return manifest;
    }


    // -------------------------------------------------------- Lifecycle methods
    /**
     * Starts this resource set.
     *
     * @throws LifecycleException if a startup error occurs
     */
    @Override
    protected final void startInternal() throws LifecycleException {
        setState(LifecycleState.STARTING);
    }

    /**
     * Stops this resource set.
     *
     * @throws LifecycleException if a shutdown error occurs
     */
    @Override
    protected final void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
    }

    /**
     * Destroys this resource set.
     *
     * @throws LifecycleException if a destroy error occurs
     */
    @Override
    protected final void destroyInternal() throws LifecycleException {
        gc();
    }
}
