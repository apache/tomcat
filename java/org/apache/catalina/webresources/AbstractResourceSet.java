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

public abstract class AbstractResourceSet extends LifecycleBase implements WebResourceSet {

    private WebResourceRoot root;
    private String base;
    private String internalPath = "";
    private String webAppMount;
    private boolean classLoaderOnly;
    private boolean staticOnly;
    private Manifest manifest;


    protected static final StringManager sm = StringManager.getManager(AbstractResourceSet.class);


    protected final void checkPath(String path) {
        if (path == null || path.isEmpty() || path.charAt(0) != '/') {
            throw new IllegalArgumentException(sm.getString("abstractResourceSet.checkPath", path));
        }
    }

    @Override
    public final void setRoot(WebResourceRoot root) {
        this.root = root;
    }

    protected final WebResourceRoot getRoot() {
        return root;
    }


    protected final String getInternalPath() {
        return internalPath;
    }

    public final void setInternalPath(String internalPath) {
        checkPath(internalPath);
        // Optimise internal processing
        if (internalPath.equals("/")) {
            this.internalPath = "";
        } else {
            this.internalPath = internalPath;
        }
    }

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

    protected final String getWebAppMount() {
        return webAppMount;
    }

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


    public final void setBase(String base) {
        this.base = base;
    }

    protected final String getBase() {
        return base;
    }

    @Override
    public boolean getClassLoaderOnly() {
        return classLoaderOnly;
    }

    @Override
    public void setClassLoaderOnly(boolean classLoaderOnly) {
        this.classLoaderOnly = classLoaderOnly;
    }

    @Override
    public boolean getStaticOnly() {
        return staticOnly;
    }

    @Override
    public void setStaticOnly(boolean staticOnly) {
        this.staticOnly = staticOnly;
    }

    protected final void setManifest(Manifest manifest) {
        this.manifest = manifest;
    }

    protected final Manifest getManifest() {
        return manifest;
    }


    // -------------------------------------------------------- Lifecycle methods
    @Override
    protected final void startInternal() throws LifecycleException {
        setState(LifecycleState.STARTING);
    }

    @Override
    protected final void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
    }

    @Override
    protected final void destroyInternal() throws LifecycleException {
        gc();
    }
}
