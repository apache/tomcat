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
package org.apache.catalina.mapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.Wrapper;
import org.apache.tomcat.util.buf.Ascii;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.res.StringManager;

/**
 * Mapper, which implements the servlet API mapping rules (which are derived
 * from the HTTP rules).
 *
 * @author Remy Maucherat
 */
public final class Mapper {


    private static final org.apache.juli.logging.Log log =
        org.apache.juli.logging.LogFactory.getLog(Mapper.class);

    protected static final StringManager sm =
        StringManager.getManager(Mapper.class.getPackage().getName());

    // ----------------------------------------------------- Instance Variables


    /**
     * Array containing the virtual hosts definitions.
     */
    protected MappedHost[] hosts = new MappedHost[0];


    /**
     * Default host name.
     */
    protected String defaultHostName = null;


    /**
     * Mapping from Context object to Context version to support
     * RequestDispatcher mappings.
     */
    protected Map<Context, ContextVersion> contextObjectToContextVersionMap =
            new ConcurrentHashMap<>();


    // --------------------------------------------------------- Public Methods

    /**
     * Set default host.
     *
     * @param defaultHostName Default host name
     */
    public void setDefaultHostName(String defaultHostName) {
        this.defaultHostName = defaultHostName;
    }

    /**
     * Add a new host to the mapper.
     *
     * @param name Virtual host name
     * @param host Host object
     */
    public synchronized void addHost(String name, String[] aliases,
                                     Host host) {
        MappedHost[] newHosts = new MappedHost[hosts.length + 1];
        MappedHost newHost = new MappedHost();
        ContextList contextList = new ContextList();
        newHost.name = name;
        newHost.contextList = contextList;
        newHost.object = host;
        if (insertMap(hosts, newHosts, newHost)) {
            hosts = newHosts;
        } else {
            MappedHost duplicate = hosts[find(hosts, name)];
            String duplicateHostName = duplicate.object.getName();
            log.error(sm.getString("mapper.duplicateHost", name,
                    duplicateHostName));
            // Do not add aliases, as removeHost(hostName) won't be able to remove them
            return;
        }
        for (String alias : aliases) {
            addHostAliasImpl(newHost, alias);
        }
    }


    /**
     * Remove a host from the mapper.
     *
     * @param name Virtual host name
     */
    public synchronized void removeHost(String name) {
        // Find and remove the old host
        MappedHost mappedHost = exactFind(hosts, name);
        if (mappedHost == null) {
            return;
        }
        Host host = mappedHost.object;
        MappedHost[] newHosts = new MappedHost[hosts.length - 1];
        if (removeMap(hosts, newHosts, name)) {
            hosts = newHosts;
        }

        // Remove all aliases (they will map to the same host object)
        for (int i = 0; i < newHosts.length; i++) {
            if (newHosts[i].object == host) {
                MappedHost[] newHosts2 = new MappedHost[hosts.length - 1];
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
        MappedHost realHost = exactFind(hosts, name);
        if (realHost == null) {
            // Should not be adding an alias for a host that doesn't exist but
            // just in case...
            return;
        }
        addHostAliasImpl(realHost, alias);
    }

    private void addHostAliasImpl(MappedHost realHost, String alias) {
        if (alias.equals(realHost.name)) {
            // An Alias with the same name as its own Host.
            // A harmless redundancy. E.g.
            // <Host name="localhost"><Alias>localhost</Alias></Host>
            return;
        }
        MappedHost[] newHosts = new MappedHost[hosts.length + 1];
        MappedHost newHost = new MappedHost();
        newHost.name = alias;
        newHost.contextList = realHost.contextList;
        newHost.object = realHost.object;
        if (insertMap(hosts, newHosts, newHost)) {
            hosts = newHosts;
        } else {
            MappedHost duplicate = hosts[find(hosts, alias)];
            String duplicateHostName = duplicate.object.getName();
            log.error(sm.getString("mapper.duplicateHostAlias", alias,
                    realHost.name, duplicateHostName));
        }
    }

    /**
     * Remove a host alias
     * @param alias The alias to remove
     */
    public synchronized void removeHostAlias(String alias) {
        // Find and remove the alias
        if (exactFind(hosts, alias) == null) {
            return;
        }
        MappedHost[] newHosts = new MappedHost[hosts.length - 1];
        if (removeMap(hosts, newHosts, alias)) {
            hosts = newHosts;
        }

    }


    /**
     * Add a new Context to an existing Host.
     *
     * @param hostName Virtual host name this context belongs to
     * @param host Host object
     * @param path Context path
     * @param version Context version
     * @param context Context object
     * @param welcomeResources Welcome files defined for this context
     * @param resources Static resources of the context
     */
    public void addContextVersion(String hostName, Host host, String path,
            String version, Context context, String[] welcomeResources,
            WebResourceRoot resources) {

        MappedHost[] hosts = this.hosts;
        MappedHost mappedHost = exactFind(hosts, hostName);
        if (mappedHost == null) {
            addHost(hostName, new String[0], host);
            hosts = this.hosts;
            mappedHost = exactFind(hosts, hostName);
            if (mappedHost == null) {
                log.error("No host found: " + hostName);
                return;
            }
        }
        int slashCount = slashCount(path);
        synchronized (mappedHost) {
            // Update nesting
            if (slashCount > mappedHost.contextList.nesting) {
                mappedHost.contextList.nesting = slashCount;
            }
            MappedContext mappedContext;
            {
                MappedContext[] contexts = mappedHost.contextList.contexts;
                mappedContext = exactFind(contexts, path);
                if (mappedContext == null) {
                    mappedContext = new MappedContext();
                    mappedContext.name = path;
                    MappedContext[] newContexts = new MappedContext[contexts.length + 1];
                    if (insertMap(contexts, newContexts, mappedContext)) {
                        mappedHost.contextList.contexts = newContexts;
                        // contexts = newContexts;
                    }
                }
            }

            ContextVersion[] contextVersions = mappedContext.versions;
            ContextVersion[] newContextVersions =
                new ContextVersion[contextVersions.length + 1];
            ContextVersion newContextVersion = new ContextVersion();
            newContextVersion.path = path;
            newContextVersion.slashCount = slashCount;
            newContextVersion.name = version;
            newContextVersion.object = context;
            newContextVersion.welcomeResources = welcomeResources;
            newContextVersion.resources = resources;
            if (insertMap(contextVersions, newContextVersions, newContextVersion)) {
                mappedContext.versions = newContextVersions;
                contextObjectToContextVersionMap.put(
                        context, newContextVersion);
            }
        }

    }


    /**
     * Remove a context from an existing host.
     *
     * @param ctxt      The actual context
     * @param hostName  Virtual host name this context belongs to
     * @param path      Context path
     * @param version   Context version
     */
    public void removeContextVersion(Context ctxt, String hostName,
            String path, String version) {

        contextObjectToContextVersionMap.remove(ctxt);

        MappedHost host = exactFind(this.hosts, hostName);
        if (host == null) {
            return;
        }

        synchronized (host) {
            MappedContext[] contexts = host.contextList.contexts;
            if (contexts.length == 0 ){
                return;
            }

            MappedContext context = exactFind(contexts, path);
            if (context == null) {
                return;
            }

            ContextVersion[] contextVersions = context.versions;
            ContextVersion[] newContextVersions =
                new ContextVersion[contextVersions.length - 1];
            if (removeMap(contextVersions, newContextVersions, version)) {
                context.versions = newContextVersions;

                if (context.versions.length == 0) {
                    // Remove the context
                    MappedContext[] newContexts = new MappedContext[contexts.length -1];
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
        }
    }


    public void addWrapper(String hostName, String contextPath, String version,
                           String path, Wrapper wrapper, boolean jspWildCard,
                           boolean resourceOnly) {
        MappedHost[] hosts = this.hosts;
        MappedHost host = exactFind(hosts, hostName);
        if (host == null) {
            return;
        }
        MappedContext[] contexts = host.contextList.contexts;
        MappedContext context = exactFind(contexts, contextPath);
        if (context == null) {
            log.error("No context found: " + contextPath );
            return;
        }
        ContextVersion[] contextVersions = context.versions;
        ContextVersion contextVersion = exactFind(contextVersions, version);
        if (contextVersion == null) {
            log.error("No context version found: " + contextPath + " " +
                    version);
            return;
        }
        addWrapper(contextVersion, path, wrapper, jspWildCard, resourceOnly);
    }


    /**
     * Adds a wrapper to the given context.
     *
     * @param context The context to which to add the wrapper
     * @param path Wrapper mapping
     * @param wrapper The Wrapper object
     * @param jspWildCard true if the wrapper corresponds to the JspServlet
     * @param resourceOnly true if this wrapper always expects a physical
     *                     resource to be present (such as a JSP)
     * and the mapping path contains a wildcard; false otherwise
     */
    protected void addWrapper(ContextVersion context, String path,
            Wrapper wrapper, boolean jspWildCard, boolean resourceOnly) {

        synchronized (context) {
            MappedWrapper newWrapper = new MappedWrapper();
            newWrapper.object = wrapper;
            newWrapper.jspWildCard = jspWildCard;
            newWrapper.resourceOnly = resourceOnly;
            if (path.endsWith("/*")) {
                // Wildcard wrapper
                newWrapper.name = path.substring(0, path.length() - 2);
                MappedWrapper[] oldWrappers = context.wildcardWrappers;
                MappedWrapper[] newWrappers =
                    new MappedWrapper[oldWrappers.length + 1];
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
                MappedWrapper[] oldWrappers = context.extensionWrappers;
                MappedWrapper[] newWrappers =
                    new MappedWrapper[oldWrappers.length + 1];
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                    context.extensionWrappers = newWrappers;
                }
            } else if (path.equals("/")) {
                // Default wrapper
                newWrapper.name = "";
                context.defaultWrapper = newWrapper;
            } else {
                // Exact wrapper
                if (path.length() == 0) {
                    // Special case for the Context Root mapping which is
                    // treated as an exact match
                    newWrapper.name = "/";
                } else {
                    newWrapper.name = path;
                }
                MappedWrapper[] oldWrappers = context.exactWrappers;
                MappedWrapper[] newWrappers =
                    new MappedWrapper[oldWrappers.length + 1];
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
            String version, String path) {
        MappedHost[] hosts = this.hosts;
        MappedHost host = exactFind(hosts, hostName);
        if (host == null) {
            return;
        }
        MappedContext[] contexts = host.contextList.contexts;
        MappedContext context = exactFind(contexts, contextPath);
        if (context == null) {
            return;
        }
        ContextVersion[] contextVersions = context.versions;
        ContextVersion contextVersion = exactFind(contextVersions, version);
        if (contextVersion == null) {
            return;
        }
        removeWrapper(contextVersion, path);
    }

    protected void removeWrapper(ContextVersion context, String path) {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("mapper.removeWrapper", context.name, path));
        }

        synchronized (context) {
            if (path.endsWith("/*")) {
                // Wildcard wrapper
                String name = path.substring(0, path.length() - 2);
                MappedWrapper[] oldWrappers = context.wildcardWrappers;
                if (oldWrappers.length == 0) {
                    return;
                }
                MappedWrapper[] newWrappers =
                    new MappedWrapper[oldWrappers.length - 1];
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
                MappedWrapper[] oldWrappers = context.extensionWrappers;
                if (oldWrappers.length == 0) {
                    return;
                }
                MappedWrapper[] newWrappers =
                    new MappedWrapper[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    context.extensionWrappers = newWrappers;
                }
            } else if (path.equals("/")) {
                // Default wrapper
                context.defaultWrapper = null;
            } else {
                // Exact wrapper
                String name;
                if (path.length() == 0) {
                    // Special case for the Context Root mapping which is
                    // treated as an exact match
                    name = "/";
                } else {
                    name = path;
                }
                MappedWrapper[] oldWrappers = context.exactWrappers;
                if (oldWrappers.length == 0) {
                    return;
                }
                MappedWrapper[] newWrappers =
                    new MappedWrapper[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    context.exactWrappers = newWrappers;
                }
            }
        }
    }


    /**
     * Add a welcome file to the given context.
     *
     * @param hostName
     * @param contextPath
     * @param welcomeFile
     */
    public void addWelcomeFile(String hostName, String contextPath,
            String version, String welcomeFile) {
        MappedHost[] hosts = this.hosts;
        MappedHost host = exactFind(hosts, hostName);
        if (host == null) {
            return;
        }
        MappedContext[] contexts = host.contextList.contexts;
        MappedContext context = exactFind(contexts, contextPath);
        if (context == null) {
            log.error("No context found: " + contextPath);
            return;
        }
        ContextVersion[] contextVersions = context.versions;
        ContextVersion contextVersion = exactFind(contextVersions, version);
        if (contextVersion == null) {
            log.error("No context version found: " + contextPath + " "
                    + version);
            return;
        }
        int len = contextVersion.welcomeResources.length + 1;
        String[] newWelcomeResources = new String[len];
        System.arraycopy(contextVersion.welcomeResources, 0,
                newWelcomeResources, 0, len - 1);
        newWelcomeResources[len - 1] = welcomeFile;
        contextVersion.welcomeResources = newWelcomeResources;
    }

    /**
     * Remove a welcome file from the given context.
     *
     * @param hostName
     * @param contextPath
     * @param welcomeFile
     */
    public void removeWelcomeFile(String hostName, String contextPath,
            String version, String welcomeFile) {
        MappedHost[] hosts = this.hosts;
        MappedHost host = exactFind(hosts, hostName);
        if (host == null) {
            return;
        }
        MappedContext[] contexts = host.contextList.contexts;
        MappedContext context = exactFind(contexts, contextPath);
        if (context == null) {
            log.error("No context found: " + contextPath);
            return;
        }
        ContextVersion[] contextVersions = context.versions;
        ContextVersion contextVersion = exactFind(contextVersions, version);
        if (contextVersion == null) {
            log.error("No context version found: " + contextPath + " "
                    + version);
            return;
        }
        int match = -1;
        for (int i = 0; i < contextVersion.welcomeResources.length; i++) {
            if (welcomeFile.equals(contextVersion.welcomeResources[i])) {
                match = i;
                break;
            }
        }
        if (match > -1) {
            int len = contextVersion.welcomeResources.length - 1;
            String[] newWelcomeResources = new String[len];
            System.arraycopy(contextVersion.welcomeResources, 0,
                    newWelcomeResources, 0, match);
            if (match < len) {
                System.arraycopy(contextVersion.welcomeResources, match + 1,
                        newWelcomeResources, match, len - match);
            }
            contextVersion.welcomeResources = newWelcomeResources;
        }
    }

    /**
     * Clear the welcome files for the given context.
     *
     * @param hostName
     * @param contextPath
     */
    public void clearWelcomeFiles(String hostName, String contextPath,
            String version) {
        MappedHost[] hosts = this.hosts;
        MappedHost host = exactFind(hosts, hostName);
        if (host == null) {
            return;
        }
        MappedContext[] contexts = host.contextList.contexts;
        MappedContext context = exactFind(contexts, contextPath);
        if (context == null) {
            log.error("No context found: " + contextPath);
            return;
        }
        ContextVersion[] contextVersions = context.versions;
        ContextVersion contextVersion = exactFind(contextVersions, version);
        if (contextVersion == null) {
            log.error("No context version found: " + contextPath + " "
                    + version);
            return;
        }
        contextVersion.welcomeResources = new String[0];
    }

    /**
     * Map the specified host name and URI, mutating the given mapping data.
     *
     * @param host Virtual host name
     * @param uri URI
     * @param mappingData This structure will contain the result of the mapping
     *                    operation
     */
    public void map(MessageBytes host, MessageBytes uri, String version,
                    MappingData mappingData)
        throws Exception {

        if (host.isNull()) {
            host.getCharChunk().append(defaultHostName);
        }
        host.toChars();
        uri.toChars();
        internalMap(host.getCharChunk(), uri.getCharChunk(), version,
                mappingData);

    }


    /**
     * Map the specified URI relative to the context,
     * mutating the given mapping data.
     *
     * @param context The actual context
     * @param uri URI
     * @param mappingData This structure will contain the result of the mapping
     *                    operation
     */
    public void map(Context context, MessageBytes uri,
            MappingData mappingData) throws Exception {

        ContextVersion contextVersion =
                contextObjectToContextVersionMap.get(context);
        uri.toChars();
        CharChunk uricc = uri.getCharChunk();
        uricc.setLimit(-1);
        internalMapWrapper(contextVersion, uricc, mappingData);

    }


    // -------------------------------------------------------- Private Methods


    /**
     * Map the specified URI.
     */
    private final void internalMap(CharChunk host, CharChunk uri,
            String version, MappingData mappingData) throws Exception {

        uri.setLimit(-1);

        MappedContext[] contexts = null;
        MappedContext context = null;
        ContextVersion contextVersion = null;

        int nesting = 0;

        // Virtual host mapping
        if (mappingData.host == null) {
            MappedHost[] hosts = this.hosts;
            MappedHost mappedHost = exactFindIgnoreCase(hosts, host);
            if (mappedHost == null) {
                if (defaultHostName == null) {
                    return;
                }
                mappedHost = exactFind(hosts, defaultHostName);
                if (mappedHost == null) {
                    return;
                }
            }
            mappingData.host = mappedHost.object;
            contexts = mappedHost.contextList.contexts;
            nesting = mappedHost.contextList.nesting;
        }

        // Context mapping
        if (mappingData.context == null && contexts != null) {
            int pos = find(contexts, uri);
            if (pos == -1) {
                return;
            }

            int lastSlash = -1;
            int uriEnd = uri.getEnd();
            int length = -1;
            boolean found = false;
            while (pos >= 0) {
                if (uri.startsWith(contexts[pos].name)) {
                    length = contexts[pos].name.length();
                    if (uri.getLength() == length) {
                        found = true;
                        break;
                    } else if (uri.startsWithIgnoreCase("/", length)) {
                        found = true;
                        break;
                    }
                }
                if (lastSlash == -1) {
                    lastSlash = nthSlash(uri, nesting + 1);
                } else {
                    lastSlash = lastSlash(uri);
                }
                uri.setEnd(lastSlash);
                pos = find(contexts, uri);
            }
            uri.setEnd(uriEnd);

            if (found) {
                context = contexts[pos];
            } else if (contexts[0].name.equals("")) {
                context = contexts[0];
            }

            if (context != null) {
                mappingData.contextPath.setString(context.name);
            }
        }

        if (context != null) {
            ContextVersion[] contextVersions = context.versions;
            int versionCount = contextVersions.length;
            if (versionCount > 1) {
                Context[] contextObjects = new Context[contextVersions.length];
                for (int i = 0; i < contextObjects.length; i++) {
                    contextObjects[i] = contextVersions[i].object;
                }
                mappingData.contexts = contextObjects;
            }

            if (version != null) {
                contextVersion = exactFind(contextVersions, version);
            }
            if (contextVersion == null) {
                // Return the latest version
                contextVersion = contextVersions[versionCount - 1];
            }
            mappingData.context = contextVersion.object;
            mappingData.contextSlashCount = contextVersion.slashCount;
        }

        // Wrapper mapping
        if ((contextVersion != null) && (mappingData.wrapper == null)) {
            internalMapWrapper(contextVersion, uri, mappingData);
        }

    }


    /**
     * Wrapper mapping.
     */
    private final void internalMapWrapper(ContextVersion contextVersion,
                                          CharChunk path,
                                          MappingData mappingData)
        throws Exception {

        int pathOffset = path.getOffset();
        int pathEnd = path.getEnd();
        int servletPath = pathOffset;
        boolean noServletPath = false;

        int length = contextVersion.path.length();
        if (length != (pathEnd - pathOffset)) {
            servletPath = pathOffset + length;
        } else {
            noServletPath = true;
            path.append('/');
            pathOffset = path.getOffset();
            pathEnd = path.getEnd();
            servletPath = pathOffset+length;
        }

        path.setOffset(servletPath);

        // Rule 1 -- Exact Match
        MappedWrapper[] exactWrappers = contextVersion.exactWrappers;
        internalMapExactWrapper(exactWrappers, path, mappingData);

        // Rule 2 -- Prefix Match
        boolean checkJspWelcomeFiles = false;
        MappedWrapper[] wildcardWrappers = contextVersion.wildcardWrappers;
        if (mappingData.wrapper == null) {
            internalMapWildcardWrapper(wildcardWrappers, contextVersion.nesting,
                                       path, mappingData);
            if (mappingData.wrapper != null && mappingData.jspWildCard) {
                char[] buf = path.getBuffer();
                if (buf[pathEnd - 1] == '/') {
                    /*
                     * Path ending in '/' was mapped to JSP servlet based on
                     * wildcard match (e.g., as specified in url-pattern of a
                     * jsp-property-group.
                     * Force the context's welcome files, which are interpreted
                     * as JSP files (since they match the url-pattern), to be
                     * considered. See Bugzilla 27664.
                     */
                    mappingData.wrapper = null;
                    checkJspWelcomeFiles = true;
                } else {
                    // See Bugzilla 27704
                    mappingData.wrapperPath.setChars(buf, path.getStart(),
                                                     path.getLength());
                    mappingData.pathInfo.recycle();
                }
            }
        }

        if(mappingData.wrapper == null && noServletPath) {
            // The path is empty, redirect to "/"
            mappingData.redirectPath.setChars
                (path.getBuffer(), pathOffset, pathEnd-pathOffset);
            path.setEnd(pathEnd - 1);
            return;
        }

        // Rule 3 -- Extension Match
        MappedWrapper[] extensionWrappers = contextVersion.extensionWrappers;
        if (mappingData.wrapper == null && !checkJspWelcomeFiles) {
            internalMapExtensionWrapper(extensionWrappers, path, mappingData,
                    true);
        }

        // Rule 4 -- Welcome resources processing for servlets
        if (mappingData.wrapper == null) {
            boolean checkWelcomeFiles = checkJspWelcomeFiles;
            if (!checkWelcomeFiles) {
                char[] buf = path.getBuffer();
                checkWelcomeFiles = (buf[pathEnd - 1] == '/');
            }
            if (checkWelcomeFiles) {
                for (int i = 0; (i < contextVersion.welcomeResources.length)
                         && (mappingData.wrapper == null); i++) {
                    path.setOffset(pathOffset);
                    path.setEnd(pathEnd);
                    path.append(contextVersion.welcomeResources[i], 0,
                            contextVersion.welcomeResources[i].length());
                    path.setOffset(servletPath);

                    // Rule 4a -- Welcome resources processing for exact macth
                    internalMapExactWrapper(exactWrappers, path, mappingData);

                    // Rule 4b -- Welcome resources processing for prefix match
                    if (mappingData.wrapper == null) {
                        internalMapWildcardWrapper
                            (wildcardWrappers, contextVersion.nesting,
                             path, mappingData);
                    }

                    // Rule 4c -- Welcome resources processing
                    //            for physical folder
                    if (mappingData.wrapper == null
                        && contextVersion.resources != null) {
                        String pathStr = path.toString();
                        WebResource file =
                                contextVersion.resources.getResource(pathStr);
                        if (file != null && file.isFile()) {
                            internalMapExtensionWrapper(extensionWrappers, path,
                                                        mappingData, true);
                            if (mappingData.wrapper == null
                                && contextVersion.defaultWrapper != null) {
                                mappingData.wrapper =
                                    contextVersion.defaultWrapper.object;
                                mappingData.requestPath.setChars
                                    (path.getBuffer(), path.getStart(),
                                     path.getLength());
                                mappingData.wrapperPath.setChars
                                    (path.getBuffer(), path.getStart(),
                                     path.getLength());
                                mappingData.requestPath.setString(pathStr);
                                mappingData.wrapperPath.setString(pathStr);
                            }
                        }
                    }
                }

                path.setOffset(servletPath);
                path.setEnd(pathEnd);
            }

        }

        /* welcome file processing - take 2
         * Now that we have looked for welcome files with a physical
         * backing, now look for an extension mapping listed
         * but may not have a physical backing to it. This is for
         * the case of index.jsf, index.do, etc.
         * A watered down version of rule 4
         */
        if (mappingData.wrapper == null) {
            boolean checkWelcomeFiles = checkJspWelcomeFiles;
            if (!checkWelcomeFiles) {
                char[] buf = path.getBuffer();
                checkWelcomeFiles = (buf[pathEnd - 1] == '/');
            }
            if (checkWelcomeFiles) {
                for (int i = 0; (i < contextVersion.welcomeResources.length)
                         && (mappingData.wrapper == null); i++) {
                    path.setOffset(pathOffset);
                    path.setEnd(pathEnd);
                    path.append(contextVersion.welcomeResources[i], 0,
                                contextVersion.welcomeResources[i].length());
                    path.setOffset(servletPath);
                    internalMapExtensionWrapper(extensionWrappers, path,
                                                mappingData, false);
                }

                path.setOffset(servletPath);
                path.setEnd(pathEnd);
            }
        }


        // Rule 7 -- Default servlet
        if (mappingData.wrapper == null && !checkJspWelcomeFiles) {
            if (contextVersion.defaultWrapper != null) {
                mappingData.wrapper = contextVersion.defaultWrapper.object;
                mappingData.requestPath.setChars
                    (path.getBuffer(), path.getStart(), path.getLength());
                mappingData.wrapperPath.setChars
                    (path.getBuffer(), path.getStart(), path.getLength());
            }
            // Redirection to a folder
            char[] buf = path.getBuffer();
            if (contextVersion.resources != null && buf[pathEnd -1 ] != '/') {
                String pathStr = path.toString();
                WebResource file =
                        contextVersion.resources.getResource(pathStr);
                if (file != null && file.isDirectory()) {
                    // Note: this mutates the path: do not do any processing
                    // after this (since we set the redirectPath, there
                    // shouldn't be any)
                    path.setOffset(pathOffset);
                    path.append('/');
                    mappingData.redirectPath.setChars
                        (path.getBuffer(), path.getStart(), path.getLength());
                } else {
                    mappingData.requestPath.setString(pathStr);
                    mappingData.wrapperPath.setString(pathStr);
                }
            }
        }

        path.setOffset(pathOffset);
        path.setEnd(pathEnd);

    }


    /**
     * Exact mapping.
     */
    private final void internalMapExactWrapper
        (MappedWrapper[] wrappers, CharChunk path, MappingData mappingData) {
        MappedWrapper wrapper = exactFind(wrappers, path);
        if (wrapper != null) {
            mappingData.requestPath.setString(wrapper.name);
            mappingData.wrapper = wrapper.object;
            if (path.equals("/")) {
                // Special handling for Context Root mapped servlet
                mappingData.pathInfo.setString("/");
                mappingData.wrapperPath.setString("");
                // This seems wrong but it is what the spec says...
                mappingData.contextPath.setString("");
            } else {
                mappingData.wrapperPath.setString(wrapper.name);
            }
        }
    }


    /**
     * Wildcard mapping.
     */
    private final void internalMapWildcardWrapper
        (MappedWrapper[] wrappers, int nesting, CharChunk path,
         MappingData mappingData) {

        int pathEnd = path.getEnd();

        int lastSlash = -1;
        int length = -1;
        int pos = find(wrappers, path);
        if (pos != -1) {
            boolean found = false;
            while (pos >= 0) {
                if (path.startsWith(wrappers[pos].name)) {
                    length = wrappers[pos].name.length();
                    if (path.getLength() == length) {
                        found = true;
                        break;
                    } else if (path.startsWithIgnoreCase("/", length)) {
                        found = true;
                        break;
                    }
                }
                if (lastSlash == -1) {
                    lastSlash = nthSlash(path, nesting + 1);
                } else {
                    lastSlash = lastSlash(path);
                }
                path.setEnd(lastSlash);
                pos = find(wrappers, path);
            }
            path.setEnd(pathEnd);
            if (found) {
                mappingData.wrapperPath.setString(wrappers[pos].name);
                if (path.getLength() > length) {
                    mappingData.pathInfo.setChars
                        (path.getBuffer(),
                         path.getOffset() + length,
                         path.getLength() - length);
                }
                mappingData.requestPath.setChars
                    (path.getBuffer(), path.getOffset(), path.getLength());
                mappingData.wrapper = wrappers[pos].object;
                mappingData.jspWildCard = wrappers[pos].jspWildCard;
            }
        }
    }


    /**
     * Extension mappings.
     *
     * @param wrappers          Set of wrappers to check for matches
     * @param path              Path to map
     * @param mappingData       Mapping data for result
     * @param resourceExpected  Is this mapping expecting to find a resource
     */
    private final void internalMapExtensionWrapper(MappedWrapper[] wrappers,
            CharChunk path, MappingData mappingData, boolean resourceExpected) {
        char[] buf = path.getBuffer();
        int pathEnd = path.getEnd();
        int servletPath = path.getOffset();
        int slash = -1;
        for (int i = pathEnd - 1; i >= servletPath; i--) {
            if (buf[i] == '/') {
                slash = i;
                break;
            }
        }
        if (slash >= 0) {
            int period = -1;
            for (int i = pathEnd - 1; i > slash; i--) {
                if (buf[i] == '.') {
                    period = i;
                    break;
                }
            }
            if (period >= 0) {
                path.setOffset(period + 1);
                path.setEnd(pathEnd);
                MappedWrapper wrapper = exactFind(wrappers, path);
                if (wrapper != null
                        && (resourceExpected || !wrapper.resourceOnly)) {
                    mappingData.wrapperPath.setChars(buf, servletPath, pathEnd
                            - servletPath);
                    mappingData.requestPath.setChars(buf, servletPath, pathEnd
                            - servletPath);
                    mappingData.wrapper = wrapper.object;
                }
                path.setOffset(servletPath);
                path.setEnd(pathEnd);
            }
        }
    }


    /**
     * Find a map element given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    private static final <T> int find(MapElement<T>[] map, CharChunk name) {
        return find(map, name, name.getStart(), name.getEnd());
    }


    /**
     * Find a map element given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    private static final <T> int find(MapElement<T>[] map, CharChunk name,
                                  int start, int end) {

        int a = 0;
        int b = map.length - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }

        if (compare(name, start, end, map[0].name) < 0 ) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2;
            int result = compare(name, start, end, map[i].name);
            if (result == 1) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = compare(name, start, end, map[b].name);
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
    private static final <T> int findIgnoreCase(MapElement<T>[] map, CharChunk name) {
        return findIgnoreCase(map, name, name.getStart(), name.getEnd());
    }


    /**
     * Find a map element given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    private static final <T> int findIgnoreCase(MapElement<T>[] map, CharChunk name,
                                  int start, int end) {

        int a = 0;
        int b = map.length - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }
        if (compareIgnoreCase(name, start, end, map[0].name) < 0 ) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2;
            int result = compareIgnoreCase(name, start, end, map[i].name);
            if (result == 1) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = compareIgnoreCase(name, start, end, map[b].name);
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
     * @see #exactFind(MapElement[], String)
     */
    private static final <T> int find(MapElement<T>[] map, String name) {

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
     * Find a map element given its name in a sorted array of map elements. This
     * will return the element that you were searching for. Otherwise it will
     * return <code>null</code>.
     * @see #find(MapElement[], String)
     */
    private static final <T, E extends MapElement<T>> E exactFind(E[] map,
            String name) {
        int pos = find(map, name);
        if (pos >= 0) {
            E result = map[pos];
            if (name.equals(result.name)) {
                return result;
            }
        }
        return null;
    }

    /**
     * Find a map element given its name in a sorted array of map elements. This
     * will return the element that you were searching for. Otherwise it will
     * return <code>null</code>.
     */
    private static final <T, E extends MapElement<T>> E exactFind(E[] map,
            CharChunk name) {
        int pos = find(map, name);
        if (pos >= 0) {
            E result = map[pos];
            if (name.equals(result.name)) {
                return result;
            }
        }
        return null;
    }

    /**
     * Find a map element given its name in a sorted array of map elements. This
     * will return the element that you were searching for. Otherwise it will
     * return <code>null</code>.
     * @see #findIgnoreCase(MapElement[], CharChunk)
     */
    private static final <T, E extends MapElement<T>> E exactFindIgnoreCase(
            E[] map, CharChunk name) {
        int pos = findIgnoreCase(map, name);
        if (pos >= 0) {
            E result = map[pos];
            if (name.equalsIgnoreCase(result.name)) {
                return result;
            }
        }
        return null;
    }


    /**
     * Compare given char chunk with String.
     * Return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    private static final int compare(CharChunk name, int start, int end,
                                     String compareTo) {
        int result = 0;
        char[] c = name.getBuffer();
        int len = compareTo.length();
        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (c[i + start] > compareTo.charAt(i)) {
                result = 1;
            } else if (c[i + start] < compareTo.charAt(i)) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length() > (end - start)) {
                result = -1;
            } else if (compareTo.length() < (end - start)) {
                result = 1;
            }
        }
        return result;
    }


    /**
     * Compare given char chunk with String ignoring case.
     * Return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    private static final int compareIgnoreCase(CharChunk name, int start, int end,
                                     String compareTo) {
        int result = 0;
        char[] c = name.getBuffer();
        int len = compareTo.length();
        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (Ascii.toLower(c[i + start]) > Ascii.toLower(compareTo.charAt(i))) {
                result = 1;
            } else if (Ascii.toLower(c[i + start]) < Ascii.toLower(compareTo.charAt(i))) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length() > (end - start)) {
                result = -1;
            } else if (compareTo.length() < (end - start)) {
                result = 1;
            }
        }
        return result;
    }


    /**
     * Find the position of the last slash in the given char chunk.
     */
    private static final int lastSlash(CharChunk name) {

        char[] c = name.getBuffer();
        int end = name.getEnd();
        int start = name.getStart();
        int pos = end;

        while (pos > start) {
            if (c[--pos] == '/') {
                break;
            }
        }

        return (pos);

    }


    /**
     * Find the position of the nth slash, in the given char chunk.
     */
    private static final int nthSlash(CharChunk name, int n) {

        char[] c = name.getBuffer();
        int end = name.getEnd();
        int start = name.getStart();
        int pos = start;
        int count = 0;

        while (pos < end) {
            if ((c[pos++] == '/') && ((++count) == n)) {
                pos--;
                break;
            }
        }

        return (pos);

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
    private static final <T> boolean insertMap
        (MapElement<T>[] oldMap, MapElement<T>[] newMap, MapElement<T> newElement) {
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
    private static final <T> boolean removeMap
        (MapElement<T>[] oldMap, MapElement<T>[] newMap, String name) {
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


    protected abstract static class MapElement<T> {

        public String name = null;
        public T object = null;

    }


    // ------------------------------------------------------- Host Inner Class


    protected static final class MappedHost
        extends MapElement<Host> {

        public ContextList contextList = null;

    }


    // ------------------------------------------------ ContextList Inner Class


    protected static final class ContextList {

        public MappedContext[] contexts = new MappedContext[0];
        public int nesting = 0;

    }


    // ---------------------------------------------------- Context Inner Class


    protected static final class MappedContext extends MapElement<Context> {
        public ContextVersion[] versions = new ContextVersion[0];
    }


    protected static final class ContextVersion extends MapElement<Context> {
        public String path = null;
        public int slashCount;
        public String[] welcomeResources = new String[0];
        public WebResourceRoot resources = null;
        public MappedWrapper defaultWrapper = null;
        public MappedWrapper[] exactWrappers = new MappedWrapper[0];
        public MappedWrapper[] wildcardWrappers = new MappedWrapper[0];
        public MappedWrapper[] extensionWrappers = new MappedWrapper[0];
        public int nesting = 0;

    }


    // ---------------------------------------------------- Wrapper Inner Class


    protected static class MappedWrapper
        extends MapElement<Wrapper> {

        public boolean jspWildCard = false;
        public boolean resourceOnly = false;
    }
}
