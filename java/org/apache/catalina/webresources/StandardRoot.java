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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>
 * Provides the resources implementation for a web application. The
 * {@link org.apache.catalina.Lifecycle} of this class should be aligned with
 * that of the associated {@link Context}.
 * </p><p>
 * This implementation assumes that the base attribute supplied to {@link
 * StandardRoot#createWebResourceSet(
 * org.apache.catalina.WebResourceRoot.ResourceSetType, String, String, String)}
 * represents the absolute path to a file.
 * </p>
 */
public class StandardRoot extends LifecycleMBeanBase
        implements WebResourceRoot {

    protected static final StringManager sm =
            StringManager.getManager(Constants.Package);

    private Context context;
    private boolean allowLinking = false;
    private ArrayList<WebResourceSet> preResources = new ArrayList<>();
    private WebResourceSet main;
    private ArrayList<WebResourceSet> jarResources = new ArrayList<>();
    private ArrayList<WebResourceSet> postResources = new ArrayList<>();

    private Cache cache = new Cache(this);
    private boolean cachingAllowed = true;

    // Constructs to make iteration over all WebResourceSets simpler
    private ArrayList<WebResourceSet> mainResources = new ArrayList<>();
    private ArrayList<ArrayList<WebResourceSet>> allResources =
            new ArrayList<>();
    {
        allResources.add(preResources);
        allResources.add(mainResources);
        allResources.add(jarResources);
        allResources.add(postResources);
    }


    /**
     * Creates a new standard implementation of {@link WebResourceRoot}. A no
     * argument constructor is required for this to work with the digester.
     * {@link #setContext(Context)} must be called before this component is
     * initialized.
     */
    public StandardRoot() {
        // NO-OP
    }

    public StandardRoot(Context context) {
        this.context = context;
    }

    @Override
    public String[] list(String path) {
        checkState();

        // Set because we don't want duplicates
        HashSet<String> result = new HashSet<>();
        for (ArrayList<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                String[] entries = webResourceSet.list(path);
                for (String entry : entries) {
                    result.add(entry);
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }


    @Override
    public Set<String> listWebAppPaths(String path) {
        checkState();

        // Set because we don't want duplicates
        HashSet<String> result = new HashSet<>();
        for (ArrayList<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                result.addAll(webResourceSet.listWebAppPaths(path));
            }
        }
        if (result.size() == 0) {
            return null;
        }
        return result;
    }

    @Override
    public boolean mkdir(String path) {
        checkState();

        if (preResourceExists(path)) {
            return false;
        }

        return main.mkdir(path);
    }

    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        checkState();

        if (!overwrite && preResourceExists(path)) {
            return false;
        }

        return main.write(path, is, overwrite);
    }

    private boolean preResourceExists(String path) {
        for (WebResourceSet webResourceSet : preResources) {
            WebResource webResource = webResourceSet.getResource(path);
            if (webResource.exists()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public WebResource getResource(String path) {
        if (isCachingAllowed()) {
            return cache.getResource(path);
        } else {
            return getResourceInternal(path);
        }
    }

    protected WebResource getResourceInternal(String path) {
        checkState();

        WebResource result = null;
        for (ArrayList<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                result = webResourceSet.getResource(path);
                if (result.exists()) {
                    return result;
                }
            }
        }

        // Default is empty resource in main resources
        return new EmptyResource(this, path);
    }

    @Override
    public WebResource[] getResources(String path) {
        checkState();

        ArrayList<WebResource> result = new ArrayList<>();
        for (ArrayList<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                WebResource webResource = webResourceSet.getResource(path);
                if (webResource.exists()) {
                    result.add(webResource);
                }
            }
        }

        if (result.size() == 0) {
            result.add(main.getResource(path));
        }

        return result.toArray(new WebResource[result.size()]);
    }

    @Override
    public WebResource[] listResources(String path) {
        checkState();

        String[] resources = list(path);
        WebResource[] result = new WebResource[resources.length];
        for (int i = 0; i < resources.length; i++) {
            if (path.charAt(path.length() - 1) == '/') {
                result[i] = getResource(path + resources[i]);
            } else {
                result[i] = getResource(path + '/' + resources[i]);
            }
        }
        return result;
    }


    @Override
    public void createWebResourceSet(ResourceSetType type, URL url,
            String webAppPath, String internalPath) {
        createWebResourceSet(type, toBase(url), webAppPath, internalPath);
    }

    @Override
    public void createWebResourceSet(ResourceSetType type, String base,
            String webAppPath, String internalPath) {

        ArrayList<WebResourceSet> resourceList;
        WebResourceSet resourceSet;

        switch (type) {
            case PRE:
                resourceList = preResources;
                break;
            case RESOURCE_JAR:
                resourceList = jarResources;
                break;
            case POST:
                resourceList = postResources;
                break;
            default:
                throw new IllegalArgumentException(
                        sm.getString("standardRoot.createUnknownType", type));
        }

        // This implementation assumes that the base for all resources will be a
        // file.
        File file = new File(base);

        if (file.isFile()) {
            if (file.getName().toLowerCase(Locale.ENGLISH).endsWith(".jar")) {
                resourceSet = new JarResourceSet(this, base, webAppPath,
                        internalPath);
            } else {
                resourceSet = new FileResourceSet(this, base, webAppPath,
                        internalPath);
            }
        } else if (file.isDirectory()) {
            resourceSet =
                    new DirResourceSet(this, base, webAppPath, internalPath);
        } else {
            throw new IllegalArgumentException(
                    sm.getString("standardRoot.createInvalidFile", file));
        }

        resourceList.add(resourceSet);
    }

    @Override
    public void addPreResources(WebResourceSet webResourceSet) {
        webResourceSet.setRoot(this);
        preResources.add(webResourceSet);
    }

    @Override
    public void addJarResources(WebResourceSet webResourceSet) {
        webResourceSet.setRoot(this);
        jarResources.add(webResourceSet);
    }

    @Override
    public void addPostResources(WebResourceSet webResourceSet) {
        webResourceSet.setRoot(this);
        postResources.add(webResourceSet);
    }

    @Override
    public void setAllowLinking(boolean allowLinking) {
        this.allowLinking = allowLinking;
    }

    @Override
    public boolean getAllowLinking() {
        return allowLinking;
    }

    @Override
    public void setCachingAllowed(boolean cachingAllowed) {
        this.cachingAllowed = cachingAllowed;
    }

    @Override
    public boolean isCachingAllowed() {
        return cachingAllowed;
    }

    @Override
    public long getCacheTtl() {
        return cache.getTtl();
    }

    @Override
    public void setCacheTtl(long cacheTtl) {
        cache.setTtl(cacheTtl);
    }

    @Override
    public long getCacheMaxSize() {
        return cache.getMaxSize();
    }

    @Override
    public void setCacheMaxSize(long cacheMaxSize) {
        cache.setMaxSize(cacheMaxSize);
    }

    @Override
    public void setCacheMaxObjectSize(long cacheMaxObjectSize) {
        cache.setMaxObjectSize(cacheMaxObjectSize);
    }

    @Override
    public long getCacheMaxObjectSize() {
        return cache.getMaxObjectSize();
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void setContext(Context context) {
        this.context = context;
    }

    private void checkState() {
        if (!getState().isAvailable()) {
            throw new IllegalStateException(
                    sm.getString("standardRoot.checkStateNotStarted"));
        }
    }

    protected String toBase(URL url) {
        File f = null;

        if ("jar".equals(url.getProtocol())) {
            String jarUrl = url.toString();
            String fileUrl = jarUrl.substring(4, jarUrl.length() - 2);
            try {
                f = new File(new URL(fileUrl).toURI());
            } catch (MalformedURLException | URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            try {
                f = new File(url.toURI());
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return f.getAbsolutePath();
    }

    @Override
    public void backgroundProcess() {
        cache.backgroundProcess();
    }

    // ----------------------------------------------------------- JMX Lifecycle
    @Override
    protected String getDomainInternal() {
        return context.getDomain();
    }

    @Override
    protected String getObjectNameKeyProperties() {
        StringBuilder keyProperties = new StringBuilder("type=WebResourceRoot");
        keyProperties.append(context.getMBeanKeyProperties());

        return keyProperties.toString();
    }

    // --------------------------------------------------------------- Lifecycle

    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();

        if (context == null) {
            throw new IllegalStateException(
                    sm.getString("standardRoot.noContext"));
        }

        for (ArrayList<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                webResourceSet.init();
            }
        }
    }

    @Override
    protected void startInternal() throws LifecycleException {
        String docBase = context.getDocBase();

        File f = new File(docBase);
        if (!f.isAbsolute()) {
            f = new File(((Host)context.getParent()).getAppBaseFile(), f.getName());
        }
        if (f.isDirectory()) {
            main = new DirResourceSet(this, f.getAbsolutePath(), "", "");
        } else if(f.isFile() && docBase.endsWith(".war")) {
            main = new JarResourceSet(this, f.getAbsolutePath(), "", "");
        } else {
            throw new IllegalArgumentException(
                    sm.getString("standardRoot.startInvalidMain"));
        }

        mainResources.clear();
        mainResources.add(main);

        for (ArrayList<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                webResourceSet.start();
            }
        }

        setState(LifecycleState.STARTING);
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        for (ArrayList<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                webResourceSet.stop();
            }
        }

        mainResources.clear();
        jarResources.clear();

        setState(LifecycleState.STOPPING);
    }

    @Override
    protected void destroyInternal() throws LifecycleException {
        for (ArrayList<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                webResourceSet.destroy();
            }
        }

        super.destroyInternal();
    }
}
