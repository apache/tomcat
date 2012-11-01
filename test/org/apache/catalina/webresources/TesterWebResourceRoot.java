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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.core.TesterContext;

/**
 * Minimal implementation for use in unit tests that supports main and pre
 * resources.
 */
public class TesterWebResourceRoot implements WebResourceRoot {

    private WebResourceSet main;

    private List<WebResourceSet> resources = new ArrayList<>();

    public void setWebResourceSet(WebResourceSet main) {
        this.main = main;
    }

    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        // NO-OP
    }

    @Override
    public LifecycleListener[] findLifecycleListeners() {
        return null;
    }

    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        // NO-OP
    }

    @Override
    public void init() throws LifecycleException {
        // NO-OP
    }

    @Override
    public void start() throws LifecycleException {
        resources.add(main);
    }

    @Override
    public void stop() throws LifecycleException {
        // NO-OP
    }

    @Override
    public void destroy() throws LifecycleException {
        // NO-OP
    }

    @Override
    public LifecycleState getState() {
        return LifecycleState.STARTED;
    }

    @Override
    public String getStateName() {
        return null;
    }

    @Override
    public WebResource getResource(String path) {
        WebResource result = null;
        for (WebResourceSet webResourceSet : resources) {
            result = webResourceSet.getResource(path);
            if (result.exists()) {
                return result;
            }
        }

        // Default is empty resource in main resources
        return new EmptyResource(this, path);
    }

    @Override
    public WebResource[] getResources(String path) {
        return null;
    }

    @Override
    public String[] list(String path) {
        // Set because we don't want duplicates
        HashSet<String> result = new HashSet<>();
        for (WebResourceSet webResourceSet : resources) {
            String[] entries = webResourceSet.list(path);
            for (String entry : entries) {
                result.add(entry);
            }
        }
        return result.toArray(new String[result.size()]);
    }

    @Override
    public Set<String> listWebAppPaths(String path) {
        // Set because we don't want duplicates
        HashSet<String> result = new HashSet<>();
        for (WebResourceSet webResourceSet : resources) {
            result.addAll(webResourceSet.listWebAppPaths(path));
        }
        if (result.size() == 0 && !getResource(path).isDirectory()) {
            return null;
        }
        return result;
    }

    @Override
    public WebResource[] listResources(String path) {
        return null;
    }

    @Override
    public boolean mkdir(String path) {
        if (getResource(path).exists()) {
            return false;
        }

        return main.mkdir(path);
    }

    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        if (getResource(path).exists()) {
            return false;
        }

        return main.write(path, is, false);
    }

    @Override
    public Context getContext() {
        return new TesterContext();
    }

    @Override
    public void setContext(Context context) {
        // NO-OP
    }

    @Override
    public void createWebResourceSet(ResourceSetType type, URL url,
            String webAppPath, String internalPath) {
        // NO-OP
    }

    @Override
    public void createWebResourceSet(ResourceSetType type, String base,
            String webAppMount, String internalPath) {
        // NO-OP
    }

    @Override
    public void setAllowLinking(boolean allowLinking) {
        // NO-OP
    }

    @Override
    public boolean getAllowLinking() {
        return false;
    }

    @Override
    public void setCachingAllowed(boolean cachingAllowed) {
        // NO-OP
    }

    @Override
    public boolean isCachingAllowed() {
        return false;
    }

    @Override
    public void setCacheTtl(long ttl) {
        // NO-OP
    }

    @Override
    public long getCacheTtl() {
        return 0;
    }

    @Override
    public void setCacheMaxSize(long cacheMaxSize) {
        // NO-OP
    }

    @Override
    public long getCacheMaxSize() {
        return 0;
    }

    @Override
    public void setCacheMaxObjectSize(long cacheMaxObjectSize) {
        // NO-OP
    }

    @Override
    public long getCacheMaxObjectSize() {
        return 0;
    }

    @Override
    public void addPreResources(WebResourceSet webResourceSet) {
        resources.add(webResourceSet);
    }

    @Override
    public void addJarResources(WebResourceSet webResourceSet) {
        // NO-OP
    }

    @Override
    public void addPostResources(WebResourceSet webResourceSet) {
        // NO-OP
    }

    @Override
    public void backgroundProcess() {
        // NO-OP
    }
}
