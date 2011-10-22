/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.lite.http;


import java.io.IOException;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.FileConnector;
import org.apache.tomcat.lite.io.BBucket;

/**
 * Mapper, which implements the servlet API mapping rules (which are derived
 * from the HTTP rules).
 *
 * This class doesn't use JNDI.
 */
public class BaseMapper {

    private static Logger logger =
        Logger.getLogger(BaseMapper.class.getName());

    // TODO:
    /**
     * Mapping should be done on bytes - as received from net, before
     * translation to chars. This would allow setting the default charset
     * for the context - or even executing the servlet and letting it specify
     * the charset to use for further decoding.
     *
     */
    public static interface Mapper {
        public void map(BBucket host, BBucket url, MappingData md);
    }


    /**
     * Like BaseMapper, for a Context.
     */
    public static class ServiceMapper extends BaseMapper {
        /**
         * Context associated with this wrapper, used for wrapper mapping.
         */
        public BaseMapper.Context contextMapElement = new BaseMapper.Context(this);

        /**
         * Set context, used for wrapper mapping (request dispatcher).
         *
         * @param welcomeResources Welcome files defined for this context
         */
        public void setContext(String path, String[] welcomeResources) {
            contextMapElement.name = path;
            contextMapElement.welcomeResources = welcomeResources;
        }


        /**
         * Add a wrapper to the context associated with this wrapper.
         *
         * @param path Wrapper mapping
         * @param wrapper The Wrapper object
         */
        public void addWrapper(String path, Object wrapper) {
            addWrapper(contextMapElement, path, wrapper);
        }


        public void addWrapper(String path, Object wrapper, boolean jspWildCard) {
            addWrapper(contextMapElement, path, wrapper, jspWildCard);
        }


        /**
         * Remove a wrapper from the context associated with this wrapper.
         *
         * @param path Wrapper mapping
         */
        public void removeWrapper(String path) {
            removeWrapper(contextMapElement, path);
        }


//        /**
//         * Map the specified URI relative to the context,
//         * mutating the given mapping data.
//         *
//         * @param uri URI
//         * @param mappingData This structure will contain the result of the mapping
//         *                    operation
//         */
//        public void map(CBuffer uri, MappingData mappingData)
//            throws Exception {
//
//           CBuffer uricc = uri.getCharBuffer();
//           internalMapWrapper(contextMapElement, uricc, mappingData);
//
//        }
    }

    /**
     * Array containing the virtual hosts definitions.
     */
    Host[] hosts = new Host[0];

    /**
     * If no other host is found.
     * For single-host servers ( most common ) this is the only one
     * used.
     */
    Host defaultHost = new Host();

    public BaseMapper() {
        defaultHost.contextList = new ContextList();
    }

    // --------------------------------------------------------- Public Methods

    public synchronized Host addHost(String name) {
        if (name == null) {
            name = "localhost";
        }
        Host[] newHosts = new Host[hosts.length + 1];
        Host newHost = new Host();
        newHost.name = name;
        newHost.contextList = new ContextList();

        if (insertMap(hosts, newHosts, newHost)) {
            hosts = newHosts;
        }
        return newHost;
    }


    /**
     * Remove a host from the mapper.
     *
     * @param name Virtual host name
     */
    public synchronized void removeHost(String name) {
        // Find and remove the old host
        int pos = find(hosts, name);
        if (pos < 0) {
            return;
        }
        Object host = hosts[pos].object;
        Host[] newHosts = new Host[hosts.length - 1];
        if (removeMap(hosts, newHosts, name)) {
            hosts = newHosts;
        }
        // Remove all aliases (they will map to the same host object)
        for (int i = 0; i < newHosts.length; i++) {
            if (newHosts[i].object == host) {
                Host[] newHosts2 = new Host[hosts.length - 1];
                if (removeMap(hosts, newHosts2, newHosts[i].name)) {
                    hosts = newHosts2;
                }
            }
        }
    }

    /**
     * Add an alias to an existing host.
     * @param name  The name of the host
     * @param alias The alias to add
     */
    public synchronized void addHostAlias(String name, String alias) {
        int pos = find(hosts, name);
        if (pos < 0) {
            // Should not be adding an alias for a host that doesn't exist but
            // just in case...
            return;
        }
        Host realHost = hosts[pos];

        Host[] newHosts = new Host[hosts.length + 1];
        Host newHost = new Host();
        newHost.name = alias;
        newHost.contextList = realHost.contextList;
        newHost.object = realHost;
        if (insertMap(hosts, newHosts, newHost)) {
            hosts = newHosts;
        }
    }

    private Host getHost(String host) {
        return getHost(CBuffer.newInstance().append(host));
    }

    private Host getHost(CBuffer host) {
        if (hosts == null || hosts.length <= 1 || host == null
                || host.length() == 0 || host.equals("")) {
            return defaultHost;
        } else {
            Host[] hosts = this.hosts;
            // TODO: if hosts.length == 1 or defaultHost ?
            int pos = findIgnoreCase(hosts, host);
            if ((pos != -1) && (host.equalsIgnoreCase(hosts[pos].name))) {
                return hosts[pos];
            } else {
                return defaultHost;
            }
        }
    }

    private Host getOrCreateHost(String hostName) {
        Host host = getHost(CBuffer.newInstance().append(hostName));
        if (host == null) {
            host = addHost(hostName);
        }
        return host;
    }

    // Contexts

    /**
     * Add a new Context to an existing Host.
     *
     * @param hostName Virtual host name this context belongs to
     * @param path Context path
     * @param context Context object
     * @param welcomeResources Welcome files defined for this context
     * @param resources Static resources of the context
     * @param ctxService
     */
    public BaseMapper.Context addContext(String hostName, String path, Object context,
            String[] welcomeResources, FileConnector resources,
            HttpChannel.HttpService ctxService) {

        if (path == null) {
            path = "/";
        }

        Host host = getOrCreateHost(hostName);

        int slashCount = slashCount(path);
        synchronized (host) {
            BaseMapper.Context[] contexts = host.contextList.contexts;
            // Update nesting
            if (slashCount > host.contextList.nesting) {
                host.contextList.nesting = slashCount;
            }
            for (int i = 0; i < contexts.length; i++) {
                if (path.equals(contexts[i].name)) {
                    return contexts[i];
                }
            }
            BaseMapper.Context[] newContexts = new BaseMapper.Context[contexts.length + 1];
            BaseMapper.Context newContext = new BaseMapper.Context(this);
            newContext.name = path;
            newContext.object = context;
            if (welcomeResources != null) {
                newContext.welcomeResources = welcomeResources;
            }
            newContext.resources = resources;
            if (ctxService != null) {
                newContext.defaultWrapper = new BaseMapper.ServiceMapping();
                newContext.defaultWrapper.object = ctxService;
            }

            if (insertMap(contexts, newContexts, newContext)) {
                host.contextList.contexts = newContexts;
            }
            return newContext;
        }

    }


    /**
     * Remove a context from an existing host.
     *
     * @param hostName Virtual host name this context belongs to
     * @param path Context path
     */
    public void removeContext(String hostName, String path) {
        Host host = getHost(hostName);
        synchronized (host) {
            BaseMapper.Context[] contexts = host.contextList.contexts;
            if( contexts.length == 0 ){
                return;
            }
            BaseMapper.Context[] newContexts = new BaseMapper.Context[contexts.length - 1];
            if (removeMap(contexts, newContexts, path)) {
                host.contextList.contexts = newContexts;
                // Recalculate nesting
                host.contextList.nesting = 0;
                for (int i = 0; i < newContexts.length; i++) {
                    int slashCount = slashCount(newContexts[i].name);
                    if (slashCount > host.contextList.nesting) {
                        host.contextList.nesting = slashCount;
                    }
                }
            }
        }
    }


    /**
     * Add a new Wrapper to an existing Context.
     *
     * @param hostName Virtual host name this wrapper belongs to
     * @param contextPath Context path this wrapper belongs to
     * @param path Wrapper mapping
     * @param wrapper Wrapper object
     */
    public void addWrapper(String hostName, String contextPath, String path,
                           Object wrapper) {
        addWrapper(hostName, contextPath, path, wrapper, false);
    }


    public void addWrapper(String hostName, String contextPath, String path,
                           Object wrapper, boolean jspWildCard) {
        Host host = getHost(hostName);
        BaseMapper.Context[] contexts = host.contextList.contexts;
        int pos2 = find(contexts, contextPath);
        if( pos2<0 ) {
            logger.severe("No context found: " + contextPath );
            return;
        }
        BaseMapper.Context context = contexts[pos2];
        if (context.name.equals(contextPath)) {
            addWrapper(context, path, wrapper, jspWildCard);
        }
    }


    public void addWrapper(BaseMapper.Context context, String path, Object wrapper) {
        addWrapper(context, path, wrapper, false);
    }


    /**
     * Adds a wrapper to the given context.
     *
     * @param context The context to which to add the wrapper
     * @param path Wrapper mapping
     * @param wrapper The Wrapper object
     * @param jspWildCard true if the wrapper corresponds to the JspServlet
     * and the mapping path contains a wildcard; false otherwise
     */
    protected void addWrapper(BaseMapper.Context context, String path, Object wrapper,
                              boolean jspWildCard) {

        synchronized (context) {
            BaseMapper.ServiceMapping newWrapper = new BaseMapper.ServiceMapping();
            newWrapper.object = wrapper;
            newWrapper.jspWildCard = jspWildCard;
            if (path.endsWith("/*")) {
                // Wildcard wrapper
                newWrapper.name = path.substring(0, path.length() - 2);
                BaseMapper.ServiceMapping[] oldWrappers = context.wildcardWrappers;
                BaseMapper.ServiceMapping[] newWrappers =
                    new BaseMapper.ServiceMapping[oldWrappers.length + 1];
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                    context.wildcardWrappers = newWrappers;
                    int slashCount = slashCount(newWrapper.name);
                    if (slashCount > context.nesting) {
                        context.nesting = slashCount;
                    }
                }
            } else if (path.startsWith("*.")) {
                // Extension wrapper
                newWrapper.name = path.substring(2);
                BaseMapper.ServiceMapping[] oldWrappers = context.extensionWrappers;
                BaseMapper.ServiceMapping[] newWrappers =
                    new BaseMapper.ServiceMapping[oldWrappers.length + 1];
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                    context.extensionWrappers = newWrappers;
                }
            } else if (path.equals("/")) {
                // Default wrapper
                newWrapper.name = "";
                context.defaultWrapper = newWrapper;
            } else {
                // Exact wrapper
                newWrapper.name = path;
                BaseMapper.ServiceMapping[] oldWrappers = context.exactWrappers;
                BaseMapper.ServiceMapping[] newWrappers =
                    new BaseMapper.ServiceMapping[oldWrappers.length + 1];
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                    context.exactWrappers = newWrappers;
                }
            }
        }
    }

    /**
     * Remove a wrapper from an existing context.
     *
     * @param hostName Virtual host name this wrapper belongs to
     * @param contextPath Context path this wrapper belongs to
     * @param path Wrapper mapping
     */
    public void removeWrapper(String hostName, String contextPath,
                              String path) {
        Host host = getHost(hostName);
        BaseMapper.Context[] contexts = host.contextList.contexts;
        int pos2 = find(contexts, contextPath);
        if (pos2 < 0) {
            return;
        }
        BaseMapper.Context context = contexts[pos2];
        if (context.name.equals(contextPath)) {
            removeWrapper(context, path);
        }
    }

    protected void removeWrapper(BaseMapper.Context context, String path) {
        synchronized (context) {
            if (path.endsWith("/*")) {
                // Wildcard wrapper
                String name = path.substring(0, path.length() - 2);
                BaseMapper.ServiceMapping[] oldWrappers = context.wildcardWrappers;
                BaseMapper.ServiceMapping[] newWrappers =
                    new BaseMapper.ServiceMapping[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    // Recalculate nesting
                    context.nesting = 0;
                    for (int i = 0; i < newWrappers.length; i++) {
                        int slashCount = slashCount(newWrappers[i].name);
                        if (slashCount > context.nesting) {
                            context.nesting = slashCount;
                        }
                    }
                    context.wildcardWrappers = newWrappers;
                }
            } else if (path.startsWith("*.")) {
                // Extension wrapper
                String name = path.substring(2);
                BaseMapper.ServiceMapping[] oldWrappers = context.extensionWrappers;
                BaseMapper.ServiceMapping[] newWrappers =
                    new BaseMapper.ServiceMapping[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    context.extensionWrappers = newWrappers;
                }
            } else if (path.equals("/")) {
                // Default wrapper
                context.defaultWrapper = null;
            } else {
                // Exact wrapper
                String name = path;
                BaseMapper.ServiceMapping[] oldWrappers = context.exactWrappers;
                BaseMapper.ServiceMapping[] newWrappers =
                    new BaseMapper.ServiceMapping[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    context.exactWrappers = newWrappers;
                }
            }
        }
    }

    /**
     * Map the specified host name and URI, mutating the given mapping data.
     *
     * @param host Virtual host name
     * @param uri URI
     * @param mappingData This structure will contain the result of the mapping
     *                    operation
     */
    public void map(CBuffer host, CBuffer uri,
                    MappingData mappingData)
        throws Exception {

        internalMap(host.length() == 0 ? null :
            host, uri, mappingData);
    }


    // -------------------------------------------------------- Private Methods

    // public Context mapContext(CBuffer host, CBuffer url);

    /**
     * Map the specified URI.
     */
    private final void internalMap(CBuffer host, CBuffer uri,
                                   MappingData mappingData)
        throws Exception {
        BaseMapper.Context[] contexts = null;
        BaseMapper.Context context = null;
        int nesting = 0;

        // Virtual host mapping
        Host mappedHost = getHost(host);
        contexts = mappedHost.contextList.contexts;
        nesting = mappedHost.contextList.nesting;

        // Context mapping
        if (contexts.length == 0) {
            return;
        }

        if (mappingData.context == null) {
            if (nesting < 1 || contexts.length == 1 && "".equals(contexts[0].name)) {
                // if 1 context (default) -> fast return
                context = contexts[0];
            } else if (nesting == 1) {
                // if all contexts are 1-component-only
                int nextSlash = uri.indexOf('/', 1);
                if (nextSlash == -1) {
                  nextSlash = uri.length();
                }
                mappingData.contextPath.set(uri, 0, nextSlash);
                int pos = find(contexts, uri);
                if (pos == -1) {
                        pos = find(contexts, "/");
                }
                if (pos >= 0) {
                    context = contexts[pos];
                }
            } else {
                int pos = find(contexts, uri);
                if (pos >= 0) {
                    int lastSlash = -1;
                    int length = -1;
                    boolean found = false;
                    CBuffer tmp = mappingData.tmpPrefix;
                    tmp.wrap(uri, 0, uri.length());

                    while (pos >= 0) {
                        if (tmp.startsWith(contexts[pos].name)) {
                            length = contexts[pos].name.length();
                            if (tmp.length() == length) {
                                found = true;
                                break;
                            } else if (tmp.startsWithIgnoreCase("/", length)) {
                                found = true;
                                break;
                            }
                        }
                        if (lastSlash == -1) {
                            lastSlash = tmp.nthSlash(nesting + 1);
                        } else {
                            lastSlash = tmp.lastIndexOf('/');
                        }
                        tmp.delete(lastSlash);
                        pos = find(contexts, tmp);
                    }

                    if (!found) {
                        if (contexts[0].name.equals("")) {
                            context = contexts[0];
                        }
                    } else {
                        context = contexts[pos];
                    }
                }
            }

            if (context != null) {
                mappingData.context = context.object;
                mappingData.contextPath.set(context.name);
            }
        }

        // Wrapper mapping
        if ((context != null) && (mappingData.getServiceObject() == null)) {
            internalMapWrapper(context, uri, mappingData);
        }

    }


    /**
     * Wrapper mapping, using servlet rules.
     */
    protected final void internalMapWrapper(
            BaseMapper.Context context,
            CBuffer url,
            MappingData mappingData)
                throws Exception {

        boolean noServletPath = false;
        if (url.length() < context.name.length()) {
            throw new IOException("Invalid mapping " + context.name + " " +
                    url);
        }

        try {
            // Set the servlet path.
            mappingData.tmpServletPath.set(url,
                    context.name.length(),
                    url.length() - context.name.length());

            if (mappingData.tmpServletPath.length() == 0) {
                mappingData.tmpServletPath.append('/');
                // This is just the context /example or /
                if (!context.name.equals("/")) {
                    noServletPath = true;
                }
            }

            mapAfterContext(context, url, mappingData.tmpServletPath, mappingData,
                    noServletPath);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.err.println(1);
        }
    }

    void mapAfterContext(BaseMapper.Context context,
            CBuffer url, CBuffer urlNoContext,
            MappingData mappingData, boolean noServletPath)
        throws Exception {


        // Rule 1 -- Exact Match
        BaseMapper.ServiceMapping[] exactWrappers = context.exactWrappers;
        internalMapExactWrapper(exactWrappers, urlNoContext, mappingData);

        // Rule 2 -- Prefix Match
        boolean checkJspWelcomeFiles = false;
        BaseMapper.ServiceMapping[] wildcardWrappers = context.wildcardWrappers;
        if (mappingData.getServiceObject() == null) {

            internalMapWildcardWrapper(wildcardWrappers, context.nesting,
                                       urlNoContext, mappingData);

            if (mappingData.getServiceObject() != null
                    && mappingData.service.jspWildCard) {
                if (urlNoContext.lastChar() == '/') {
                    /*
                     * Path ending in '/' was mapped to JSP servlet based on
                     * wildcard match (e.g., as specified in url-pattern of a
                     * jsp-property-group.
                     * Force the context's welcome files, which are interpreted
                     * as JSP files (since they match the url-pattern), to be
                     * considered. See Bugzilla 27664.
                     */
                    mappingData.service = null;
                    checkJspWelcomeFiles = true;
                } else {
                    // See Bugzilla 27704
                    mappingData.wrapperPath.set(urlNoContext);
                    mappingData.pathInfo.recycle();
                }
            }
        }

        if(mappingData.getServiceObject() == null && noServletPath) {
            // The path is empty, redirect to "/"
            mappingData.redirectPath.set(context.name);
            mappingData.redirectPath.append("/");
            return;
        }

        // Rule 3 -- Extension Match
        BaseMapper.ServiceMapping[] extensionWrappers = context.extensionWrappers;
        if (mappingData.getServiceObject() == null && !checkJspWelcomeFiles) {
            internalMapExtensionWrapper(extensionWrappers, urlNoContext, mappingData);
        }

        // Rule 4 -- Welcome resources processing for servlets
        if (mappingData.getServiceObject() == null) {
            boolean checkWelcomeFiles = checkJspWelcomeFiles;
            if (!checkWelcomeFiles) {
                checkWelcomeFiles = (urlNoContext.lastChar() == '/');
            }
            if (checkWelcomeFiles) {
                for (int i = 0; (i < context.welcomeResources.length)
                         && (mappingData.getServiceObject() == null); i++) {

                    CBuffer wpath = mappingData.tmpWelcome;
                    wpath.set(urlNoContext);
                    wpath.append(context.welcomeResources[i]);

                    // Rule 4a -- Welcome resources processing for exact macth
                    internalMapExactWrapper(exactWrappers, urlNoContext, mappingData);

                    // Rule 4b -- Welcome resources processing for prefix match
                    if (mappingData.getServiceObject() == null) {
                        internalMapWildcardWrapper
                            (wildcardWrappers, context.nesting,
                             urlNoContext, mappingData);
                    }

                    // Rule 4c -- Welcome resources processing
                    //            for physical folder
                    if (mappingData.getServiceObject() == null
                        && context.resources != null) {
                        String pathStr = urlNoContext.toString();

                        mapWelcomResource(context, urlNoContext, mappingData,
                                extensionWrappers, pathStr);

                    }
                }
            }

        }


        // Rule 7 -- Default servlet
        if (mappingData.getServiceObject() == null && !checkJspWelcomeFiles) {
            if (context.defaultWrapper != null) {
                mappingData.service = context.defaultWrapper;
                mappingData.requestPath.set(urlNoContext);
                mappingData.wrapperPath.set(urlNoContext);
            }
            // Redirection to a folder
            if (context.resources != null && urlNoContext.lastChar() != '/') {
                String pathStr = urlNoContext.toString();
                mapDefaultServlet(context, urlNoContext, mappingData,
                        url,
                        pathStr);
            }
        }
    }

    /**
     * Filesystem-dependent method:
     *  if pathStr corresponds to a directory, we'll need to redirect with /
     *  at end.
     */
    protected void mapDefaultServlet(BaseMapper.Context context,
            CBuffer path,
            MappingData mappingData,
            CBuffer url,
            String pathStr) throws IOException {

        if (context.resources != null
                && context.resources.isDirectory(pathStr)) {
            mappingData.redirectPath.set(url);
            mappingData.redirectPath.append("/");
        } else {
            mappingData.requestPath.set(pathStr);
            mappingData.wrapperPath.set(pathStr);
        }
    }


    /**
     * Filesystem dependent method:
     *  check if a resource exists in filesystem.
     */
    protected void mapWelcomResource(BaseMapper.Context context, CBuffer path,
                               MappingData mappingData,
                               BaseMapper.ServiceMapping[] extensionWrappers, String pathStr) {

        if (context.resources != null &&
                context.resources.isFile(pathStr)) {
            internalMapExtensionWrapper(extensionWrappers,
                                        path, mappingData);
            if (mappingData.getServiceObject() == null
                && context.defaultWrapper != null) {
                mappingData.service = context.defaultWrapper;
                mappingData.requestPath.set(path);
                mappingData.wrapperPath.set(path);
                mappingData.requestPath.set(pathStr);
                mappingData.wrapperPath.set(pathStr);
            }
        }
    }

    /**
     * Exact mapping.
     */
    private final void internalMapExactWrapper
        (BaseMapper.ServiceMapping[] wrappers, CBuffer path, MappingData mappingData) {
        int pos = find(wrappers, path);
        if ((pos != -1) && (path.equals(wrappers[pos].name))) {
            mappingData.requestPath.set(wrappers[pos].name);
            mappingData.wrapperPath.set(wrappers[pos].name);
            mappingData.service = wrappers[pos];
        }
    }


    /**
     * Prefix mapping. ( /foo/* )
     */
    private final void internalMapWildcardWrapper
        (BaseMapper.ServiceMapping[] wrappers, int nesting, CBuffer path,
         MappingData mappingData) {

        int lastSlash = -1;
        int length = -1;

        CBuffer tmp = mappingData.tmpPrefix;
        tmp.wrap(path, 0, path.length());

        int pos = find(wrappers, tmp);
        if (pos != -1) {
            boolean found = false;
            while (pos >= 0) {
                if (tmp.startsWith(wrappers[pos].name)) {
                    length = wrappers[pos].name.length();
                    if (tmp.length() == length) {
                        found = true;
                        break;
                    } else if (tmp.startsWithIgnoreCase("/", length)) {
                        found = true;
                        break;
                    }
                }
                if (lastSlash == -1) {
                    lastSlash = tmp.nthSlash(nesting + 1);
                } else {
                    lastSlash = tmp.lastIndexOf('/');
                }
                tmp.delete(lastSlash);
                pos = find(wrappers, tmp);
            }
            if (found) {
                mappingData.wrapperPath.set(wrappers[pos].name);

                if (path.length() > length) {
                    mappingData.pathInfo.set
                        (path, length, path.length() - length);
                }
                mappingData.requestPath.set(path);

                mappingData.service = wrappers[pos];
            }
        }
    }


    /**
     * Extension mappings.
     */
    protected final void internalMapExtensionWrapper
        (BaseMapper.ServiceMapping[] wrappers, CBuffer path, MappingData mappingData) {

        int dot = path.getExtension(mappingData.ext, '/', '.');
        if (dot >= 0) {
            int pos = find(wrappers, mappingData.ext);

            if ((pos != -1)
                    && (mappingData.ext.equals(wrappers[pos].name))) {

                mappingData.wrapperPath.set(path);
                mappingData.requestPath.set(path);

                mappingData.service = wrappers[pos];
            }
        }
    }


    /**
     * Find a map elemnt given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    private static final int find(BaseMapper.Mapping[] map, CBuffer name) {

        int a = 0;
        int b = map.length - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }

        if (name.compare(map[0].name) < 0 ) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2;
            int result = name.compare(map[i].name);
            if (result == 1) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = name.compare(map[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }

    }

    /**
     * Find a map elemnt given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    private static final int findIgnoreCase(BaseMapper.Mapping[] map,
            CBuffer name) {
        int a = 0;
        int b = map.length - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }
        if (name.compareIgnoreCase(map[0].name) < 0 ) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2;
            int result = name.compareIgnoreCase(map[i].name);
            if (result == 1) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = name.compareIgnoreCase(map[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }

    }


    /**
     * Find a map element given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    private static final int find(BaseMapper.Mapping[] map, String name) {

        int a = 0;
        int b = map.length - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }

        if (name.compareTo(map[0].name) < 0) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2;
            int result = name.compareTo(map[i].name);
            if (result > 0) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = name.compareTo(map[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }

    }


    /**
     * Return the slash count in a given string.
     */
    private static final int slashCount(String name) {
        int pos = -1;
        int count = 0;
        while ((pos = name.indexOf('/', pos + 1)) != -1) {
            count++;
        }
        return count;
    }


    /**
     * Insert into the right place in a sorted MapElement array, and prevent
     * duplicates.
     */
    private static final boolean insertMap
        (BaseMapper.Mapping[] oldMap, BaseMapper.Mapping[] newMap, BaseMapper.Mapping newElement) {
        int pos = find(oldMap, newElement.name);
        if ((pos != -1) && (newElement.name.equals(oldMap[pos].name))) {
            return false;
        }
        System.arraycopy(oldMap, 0, newMap, 0, pos + 1);
        newMap[pos + 1] = newElement;
        System.arraycopy
            (oldMap, pos + 1, newMap, pos + 2, oldMap.length - pos - 1);
        return true;
    }


    /**
     * Insert into the right place in a sorted MapElement array.
     */
    private static final boolean removeMap
        (BaseMapper.Mapping[] oldMap, BaseMapper.Mapping[] newMap, String name) {
        int pos = find(oldMap, name);
        if ((pos != -1) && (name.equals(oldMap[pos].name))) {
            System.arraycopy(oldMap, 0, newMap, 0, pos);
            System.arraycopy(oldMap, pos + 1, newMap, pos,
                             oldMap.length - pos - 1);
            return true;
        }
        return false;
    }


    // ------------------------------------------------- MapElement Inner Class


    protected static final class Host
        extends BaseMapper.Mapping {
        //Map<String, Context> contexts = new HashMap();
        //Context rootContext;

        public ContextList contextList = null;

    }


    // ------------------------------------------------ ContextList Inner Class

    // Shared among host aliases.
    protected static final class ContextList {

        public BaseMapper.Context[] contexts = new BaseMapper.Context[0];
        public int nesting = 0;

    }


    public static final class Context extends BaseMapper.Mapping {

        Context(BaseMapper mapper) {
            this.mapper = mapper;
        }
        public BaseMapper mapper;
        public String[] welcomeResources = new String[0];
        public FileConnector resources = null;

        public BaseMapper.ServiceMapping defaultWrapper = null;

        public BaseMapper.ServiceMapping[] exactWrappers = new BaseMapper.ServiceMapping[0];
        public BaseMapper.ServiceMapping[] wildcardWrappers = new BaseMapper.ServiceMapping[0];
        public BaseMapper.ServiceMapping[] extensionWrappers = new BaseMapper.ServiceMapping[0];
        public int nesting = 0;

        public void addWrapper(String path, HttpService service) {
            mapper.addWrapper(this, path, service);
        }

    }


    public static class ServiceMapping extends BaseMapper.Mapping {
        public boolean jspWildCard = false;
        // If set, the service will run in the selector thread ( should
        // be non-blocking )
        public boolean selectorThread = false;

    }


    protected static abstract class Mapping {
        public String name = null;
        public Object object = null;

        public String toString() {
            if (name == null || "".equals(name)) {
                return "DEFAULT";
            }
            return name;
        }
    }


    // ---------------------------------------------------- Context Inner Class


}
