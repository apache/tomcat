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
import java.util.Collections;
import java.util.Set;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.util.LifecycleBase;

/**
 * A {@link WebResourceSet} implementation that is not backed by a file system
 * and behaves as if it has no resources available. This is  primarily used in
 * embedded mode when the web application is configured entirely
 * programmatically and does not use any static resources from the file system.
 */
public class EmptyResourceSet extends LifecycleBase implements WebResourceSet {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private WebResourceRoot root;
    private boolean classLoaderOnly;
    private boolean staticOnly;

    public EmptyResourceSet(WebResourceRoot root) {
        this.root = root;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns an {@link EmptyResource}.
     */
    @Override
    public WebResource getResource(String path) {
        return new EmptyResource(root, path);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns an empty array.
     */
    @Override
    public String[] list(String path) {
        return EMPTY_STRING_ARRAY;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns an empty set.
     */
    @Override
    public Set<String> listWebAppPaths(String path) {
        return Collections.emptySet();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns false.
     */
    @Override
    public boolean mkdir(String path) {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns false.
     */
    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        return false;
    }

    @Override
    public void setRoot(WebResourceRoot root) {
        this.root = root;
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

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns null.
     */
    @Override
    public URL getBaseUrl() {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Calls to this method will be ignored as this implementation always read
     * only.
     */
    @Override
    public void setReadOnly(boolean readOnly) {

    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns true.
     */
    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public void gc() {
        // NO-OP
    }

    @Override
    protected void initInternal() throws LifecycleException {
        // NO-OP
    }

    @Override
    protected void startInternal() throws LifecycleException {
        setState(LifecycleState.STARTING);
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        setState(LifecycleState.STOPPING);
    }

    @Override
    protected void destroyInternal() throws LifecycleException {
        // NO-OP
    }
}
