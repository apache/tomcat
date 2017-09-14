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
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.ObjectName;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.TrackedWebResource;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.http.RequestUtil;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>
 * Provides the resources implementation for a web application. The
 * {@link org.apache.catalina.Lifecycle} of this class should be aligned with
 * that of the associated {@link Context}.
 * </p><p>
 * This implementation assumes that the base attribute supplied to {@link
 * StandardRoot#createWebResourceSet(
 * org.apache.catalina.WebResourceRoot.ResourceSetType, String, String, String,
 * String)} represents the absolute path to a file.
 * </p>
 */
public class StandardRoot extends LifecycleMBeanBase implements WebResourceRoot {

    private static final Log log = LogFactory.getLog(StandardRoot.class);
    protected static final StringManager sm = StringManager.getManager(StandardRoot.class);

    private Context context;
    private boolean allowLinking = false;
    private final List<WebResourceSet> preResources = new ArrayList<>();
    private WebResourceSet main;
    private final List<WebResourceSet> classResources = new ArrayList<>();
    private final List<WebResourceSet> jarResources = new ArrayList<>();
    private final List<WebResourceSet> postResources = new ArrayList<>();

    private final Cache cache = new Cache(this);
    private boolean cachingAllowed = true;
    private ObjectName cacheJmxName = null;

    private boolean trackLockedFiles = false;
    private final Set<TrackedWebResource> trackedResources =
            Collections.newSetFromMap(new ConcurrentHashMap<TrackedWebResource,Boolean>());

    // Constructs to make iteration over all WebResourceSets simpler
    private final List<WebResourceSet> mainResources = new ArrayList<>();
    private final List<List<WebResourceSet>> allResources =
            new ArrayList<>();
    {
        allResources.add(preResources);
        allResources.add(mainResources);
        allResources.add(classResources);
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
        return list(path, true);
    }

    private String[] list(String path, boolean validate) {
        if (validate) {
            path = validate(path);
        }

        // Set because we don't want duplicates
        // LinkedHashSet to retain the order. It is the order of the
        // WebResourceSet that matters but it is simpler to retain the order
        // over all of the JARs.
        HashSet<String> result = new LinkedHashSet<>();
        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                if (!webResourceSet.getClassLoaderOnly()) {
                    String[] entries = webResourceSet.list(path);
                    for (String entry : entries) {
                        result.add(entry);
                    }
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }


    @Override
    public Set<String> listWebAppPaths(String path) {
        path = validate(path);

        // Set because we don't want duplicates
        HashSet<String> result = new HashSet<>();
        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                if (!webResourceSet.getClassLoaderOnly()) {
                    result.addAll(webResourceSet.listWebAppPaths(path));
                }
            }
        }
        if (result.size() == 0) {
            return null;
        }
        return result;
    }

    @Override
    public boolean mkdir(String path) {
        path = validate(path);

        if (preResourceExists(path)) {
            return false;
        }

        boolean mkdirResult = main.mkdir(path);

        if (mkdirResult && isCachingAllowed()) {
            // Remove the entry from the cache so the new directory is visible
            cache.removeCacheEntry(path);
        }
        return mkdirResult;
    }

    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        path = validate(path);

        if (!overwrite && preResourceExists(path)) {
            return false;
        }

        boolean writeResult = main.write(path, is, overwrite);

        if (writeResult && isCachingAllowed()) {
            // Remove the entry from the cache so the new resource is visible
            cache.removeCacheEntry(path);
        }

        return writeResult;
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
        return getResource(path, true, false);
    }

    private WebResource getResource(String path, boolean validate,
            boolean useClassLoaderResources) {
        if (validate) {
            path = validate(path);
        }

        if (isCachingAllowed()) {
            return cache.getResource(path, useClassLoaderResources);
        } else {
            return getResourceInternal(path, useClassLoaderResources);
        }
    }


    @Override
    public WebResource getClassLoaderResource(String path) {
        return getResource("/WEB-INF/classes" + path, true, true);
    }


    @Override
    public WebResource[] getClassLoaderResources(String path) {
        return getResources("/WEB-INF/classes" + path, true);
    }


    /**
     * Ensures that this object is in a valid state to serve resources, checks
     * that the path is a String that starts with '/' and checks that the path
     * can be normalized without stepping outside of the root.
     *
     * @param path
     * @return  the normalized path
     */
    private String validate(String path) {
        if (!getState().isAvailable()) {
            throw new IllegalStateException(
                    sm.getString("standardRoot.checkStateNotStarted"));
        }

        if (path == null || path.length() == 0 || !path.startsWith("/")) {
            throw new IllegalArgumentException(
                    sm.getString("standardRoot.invalidPath", path));
        }

        String result;
        if (File.separatorChar == '\\') {
            // On Windows '\\' is a separator so in case a Windows style
            // separator has managed to make it into the path, replace it.
            result = RequestUtil.normalize(path, true);
        } else {
            // On UNIX and similar systems, '\\' is a valid file name so do not
            // convert it to '/'
            result = RequestUtil.normalize(path, false);
        }
        if (result == null || result.length() == 0 || !result.startsWith("/")) {
            throw new IllegalArgumentException(
                    sm.getString("standardRoot.invalidPathNormal", path, result));
        }

        return result;
    }

    protected final WebResource getResourceInternal(String path,
            boolean useClassLoaderResources) {
        WebResource result = null;
        WebResource virtual = null;
        WebResource mainEmpty = null;
        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                if (!useClassLoaderResources &&  !webResourceSet.getClassLoaderOnly() ||
                        useClassLoaderResources && !webResourceSet.getStaticOnly()) {
                    result = webResourceSet.getResource(path);
                    if (result.exists()) {
                        return result;
                    }
                    if (virtual == null) {
                        if (result.isVirtual()) {
                            virtual = result;
                        } else if (main.equals(webResourceSet)) {
                            mainEmpty = result;
                        }
                    }
                }
            }
        }

        // Use the first virtual result if no real result was found
        if (virtual != null) {
            return virtual;
        }

        // Default is empty resource in main resources
        return mainEmpty;
    }

    @Override
    public WebResource[] getResources(String path) {
        return getResources(path, false);
    }

    private WebResource[] getResources(String path,
            boolean useClassLoaderResources) {
        path = validate(path);

        if (isCachingAllowed()) {
            return cache.getResources(path, useClassLoaderResources);
        } else {
            return getResourcesInternal(path, useClassLoaderResources);
        }
    }

    protected WebResource[] getResourcesInternal(String path,
            boolean useClassLoaderResources) {
        List<WebResource> result = new ArrayList<>();
        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                if (useClassLoaderResources || !webResourceSet.getClassLoaderOnly()) {
                    WebResource webResource = webResourceSet.getResource(path);
                    if (webResource.exists()) {
                        result.add(webResource);
                    }
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
        return listResources(path, true);
    }

    protected WebResource[] listResources(String path, boolean validate) {
        if (validate) {
            path = validate(path);
        }

        String[] resources = list(path, false);
        WebResource[] result = new WebResource[resources.length];
        for (int i = 0; i < resources.length; i++) {
            if (path.charAt(path.length() - 1) == '/') {
                result[i] = getResource(path + resources[i], false, false);
            } else {
                result[i] = getResource(path + '/' + resources[i], false, false);
            }
        }
        return result;
    }

    // TODO: Should the createWebResourceSet() methods be removed to some
    //       utility class for file system based resource sets?

    @Override
    public void createWebResourceSet(ResourceSetType type, String webAppMount,
            URL url, String internalPath) {
        BaseLocation baseLocation = new BaseLocation(url);
        createWebResourceSet(type, webAppMount, baseLocation.getBasePath(),
                baseLocation.getArchivePath(), internalPath);
    }

    @Override
    public void createWebResourceSet(ResourceSetType type, String webAppMount,
            String base, String archivePath, String internalPath) {
        List<WebResourceSet> resourceList;
        WebResourceSet resourceSet;

        switch (type) {
            case PRE:
                resourceList = preResources;
                break;
            case CLASSES_JAR:
                resourceList = classResources;
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
            if (archivePath != null) {
                // Must be a JAR nested inside a WAR if archivePath is non-null
                resourceSet = new JarWarResourceSet(this, webAppMount, base,
                        archivePath, internalPath);
            } else if (file.getName().toLowerCase(Locale.ENGLISH).endsWith(".jar")) {
                resourceSet = new JarResourceSet(this, webAppMount, base,
                        internalPath);
            } else {
                resourceSet = new FileResourceSet(this, webAppMount, base,
                        internalPath);
            }
        } else if (file.isDirectory()) {
            resourceSet =
                    new DirResourceSet(this, webAppMount, base, internalPath);
        } else {
            throw new IllegalArgumentException(
                    sm.getString("standardRoot.createInvalidFile", file));
        }

        if (type.equals(ResourceSetType.CLASSES_JAR)) {
            resourceSet.setClassLoaderOnly(true);
        } else if (type.equals(ResourceSetType.RESOURCE_JAR)) {
            resourceSet.setStaticOnly(true);
        }

        resourceList.add(resourceSet);
    }

    @Override
    public void addPreResources(WebResourceSet webResourceSet) {
        webResourceSet.setRoot(this);
        preResources.add(webResourceSet);
    }

    @Override
    public WebResourceSet[] getPreResources() {
        return preResources.toArray(new WebResourceSet[preResources.size()]);
    }

    @Override
    public void addJarResources(WebResourceSet webResourceSet) {
        webResourceSet.setRoot(this);
        jarResources.add(webResourceSet);
    }

    @Override
    public WebResourceSet[] getJarResources() {
        return jarResources.toArray(new WebResourceSet[jarResources.size()]);
    }

    @Override
    public void addPostResources(WebResourceSet webResourceSet) {
        webResourceSet.setRoot(this);
        postResources.add(webResourceSet);
    }

    @Override
    public WebResourceSet[] getPostResources() {
        return postResources.toArray(new WebResourceSet[postResources.size()]);
    }

    protected WebResourceSet[] getClassResources() {
        return classResources.toArray(new WebResourceSet[classResources.size()]);
    }

    protected void addClassResources(WebResourceSet webResourceSet) {
        webResourceSet.setRoot(this);
        classResources.add(webResourceSet);
    }

    @Override
    public void setAllowLinking(boolean allowLinking) {
        if (this.allowLinking != allowLinking && cachingAllowed) {
            // If allow linking changes, invalidate the cache.
            cache.clear();
        }
        this.allowLinking = allowLinking;
    }

    @Override
    public boolean getAllowLinking() {
        return allowLinking;
    }

    @Override
    public void setCachingAllowed(boolean cachingAllowed) {
        this.cachingAllowed = cachingAllowed;
        if (!cachingAllowed) {
            cache.clear();
        }
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
    public void setCacheObjectMaxSize(int cacheObjectMaxSize) {
        cache.setObjectMaxSize(cacheObjectMaxSize);
        // Don't enforce the limit when not running as attributes may get set in
        // any order.
        if (getState().isAvailable()) {
            cache.enforceObjectMaxSizeLimit();
        }
    }

    @Override
    public int getCacheObjectMaxSize() {
        return cache.getObjectMaxSize();
    }

    @Override
    public void setTrackLockedFiles(boolean trackLockedFiles) {
        this.trackLockedFiles = trackLockedFiles;
        if (!trackLockedFiles) {
            trackedResources.clear();
        }
    }

    @Override
    public boolean getTrackLockedFiles() {
        return trackLockedFiles;
    }

    public List<String> getTrackedResources() {
        List<String> result = new ArrayList<>(trackedResources.size());
        for (TrackedWebResource resource : trackedResources) {
            result.add(resource.toString());
        }
        return result;
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * Class loader resources are handled by treating JARs in WEB-INF/lib as
     * resource JARs (without the internal META-INF/resources/ prefix) mounted
     * at WEB-INF/classes (rather than the web app root). This enables reuse
     * of the resource handling plumbing.
     *
     * These resources are marked as class loader only so they are only used in
     * the methods that are explicitly defined to return class loader resources.
     * This prevents calls to getResource("/WEB-INF/classes") returning from one
     * or more of the JAR files.
     *
     * @throws LifecycleException If an error occurs that should stop the web
     *                            application from starting
     */
    protected void processWebInfLib() throws LifecycleException {
        WebResource[] possibleJars = listResources("/WEB-INF/lib", false);

        for (WebResource possibleJar : possibleJars) {
            if (possibleJar.isFile() && possibleJar.getName().endsWith(".jar")) {
                createWebResourceSet(ResourceSetType.CLASSES_JAR,
                        "/WEB-INF/classes", possibleJar.getURL(), "/");
            }
        }
    }

    /**
     * For unit testing.
     * @param main The main resources
     */
    protected final void setMainResources(WebResourceSet main) {
        this.main = main;
        mainResources.clear();
        mainResources.add(main);
    }


    @Override
    public void backgroundProcess() {
        cache.backgroundProcess();
        gc();
    }



    @Override
    public void gc() {
        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                webResourceSet.gc();
            }
        }
    }

    @Override
    public void registerTrackedResource(TrackedWebResource trackedResource) {
        trackedResources.add(trackedResource);
    }


    @Override
    public void deregisterTrackedResource(TrackedWebResource trackedResource) {
        trackedResources.remove(trackedResource);
    }


    @Override
    public List<URL> getBaseUrls() {
        List<URL> result = new ArrayList<>();
        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                if (!webResourceSet.getClassLoaderOnly()) {
                    URL url = webResourceSet.getBaseUrl();
                    if (url != null) {
                        result.add(url);
                    }
                }
            }
        }
        return result;
    }



    /*
     * Returns true if and only if all the resources for this web application
     * are provided via a packed WAR file. It is used to optimise cache
     * validation in this case on the basis that the WAR file will not change.
     */
    protected boolean isPackedWarFile() {
        return main instanceof WarResourceSet && preResources.isEmpty() && postResources.isEmpty();
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

        cacheJmxName = register(cache, getObjectNameKeyProperties() + ",name=Cache");

        registerURLStreamHandlerFactory();

        if (context == null) {
            throw new IllegalStateException(
                    sm.getString("standardRoot.noContext"));
        }

        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                webResourceSet.init();
            }
        }
    }

    protected void registerURLStreamHandlerFactory() {
        // Ensure support for jar:war:file:/ URLs will be available (required
        // for resource JARs in packed WAR files).
        TomcatURLStreamHandlerFactory.register();
    }

    @Override
    protected void startInternal() throws LifecycleException {
        mainResources.clear();

        main = createMainResourceSet();

        mainResources.add(main);

        for (List<WebResourceSet> list : allResources) {
            // Skip class resources since they are started below
            if (list != classResources) {
                for (WebResourceSet webResourceSet : list) {
                    webResourceSet.start();
                }
            }
        }

        // This has to be called after the other resources have been started
        // else it won't find all the matching resources
        processWebInfLib();
        // Need to start the newly found resources
        for (WebResourceSet classResource : classResources) {
            classResource.start();
        }

        cache.enforceObjectMaxSizeLimit();

        setState(LifecycleState.STARTING);
    }

    protected WebResourceSet createMainResourceSet() {
        String docBase = context.getDocBase();

        WebResourceSet mainResourceSet;
        if (docBase == null) {
            mainResourceSet = new EmptyResourceSet(this);
        } else {
            File f = new File(docBase);
            if (!f.isAbsolute()) {
                f = new File(((Host)context.getParent()).getAppBaseFile(), f.getPath());
            }
            if (f.isDirectory()) {
                mainResourceSet = new DirResourceSet(this, "/", f.getAbsolutePath(), "/");
            } else if(f.isFile() && docBase.endsWith(".war")) {
                mainResourceSet = new WarResourceSet(this, "/", f.getAbsolutePath());
            } else {
                throw new IllegalArgumentException(
                        sm.getString("standardRoot.startInvalidMain",
                                f.getAbsolutePath()));
            }
        }

        return mainResourceSet;
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                webResourceSet.stop();
            }
        }

        if (main != null) {
            main.destroy();
        }
        mainResources.clear();

        for (WebResourceSet webResourceSet : jarResources) {
            webResourceSet.destroy();
        }
        jarResources.clear();

        for (WebResourceSet webResourceSet : classResources) {
            webResourceSet.destroy();
        }
        classResources.clear();

        for (TrackedWebResource trackedResource : trackedResources) {
            log.error(sm.getString("standardRoot.lockedFile",
                    context.getName(),
                    trackedResource.getName()),
                    trackedResource.getCreatedBy());
            try {
                trackedResource.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        cache.clear();

        setState(LifecycleState.STOPPING);
    }

    @Override
    protected void destroyInternal() throws LifecycleException {
        for (List<WebResourceSet> list : allResources) {
            for (WebResourceSet webResourceSet : list) {
                webResourceSet.destroy();
            }
        }

        unregister(cacheJmxName);

        super.destroyInternal();
    }


    // Unit tests need to access this class
    static class BaseLocation {

        private final String basePath;
        private final String archivePath;

        BaseLocation(URL url) {
            File f = null;

            if ("jar".equals(url.getProtocol()) || "war".equals(url.getProtocol())) {
                String jarUrl = url.toString();
                int endOfFileUrl = -1;
                if ("jar".equals(url.getProtocol())) {
                    endOfFileUrl = jarUrl.indexOf("!/");
                } else {
                    endOfFileUrl = jarUrl.indexOf(UriUtil.getWarSeparator());
                }
                String fileUrl = jarUrl.substring(4, endOfFileUrl);
                try {
                    f = new File(new URL(fileUrl).toURI());
                } catch (MalformedURLException | URISyntaxException e) {
                    throw new IllegalArgumentException(e);
                }
                int startOfArchivePath = endOfFileUrl + 2;
                if (jarUrl.length() >  startOfArchivePath) {
                    archivePath = jarUrl.substring(startOfArchivePath);
                } else {
                    archivePath = null;
                }
            } else if ("file".equals(url.getProtocol())){
                try {
                    f = new File(url.toURI());
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException(e);
                }
                archivePath = null;
            } else {
                throw new IllegalArgumentException(sm.getString(
                        "standardRoot.unsupportedProtocol", url.getProtocol()));
            }

            basePath = f.getAbsolutePath();
        }


        String getBasePath() {
            return basePath;
        }


        String getArchivePath() {
            return archivePath;
        }
    }
}
