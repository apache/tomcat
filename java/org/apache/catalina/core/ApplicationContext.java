/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.core;


import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import javax.naming.Binding;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.deploy.ApplicationParameter;
import org.apache.catalina.util.Enumerator;
import org.apache.catalina.util.ResourceSet;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.StringManager;
import org.apache.naming.resources.DirContextURLStreamHandler;
import org.apache.naming.resources.Resource;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.mapper.MappingData;


/**
 * Standard implementation of <code>ServletContext</code> that represents
 * a web application's execution environment.  An instance of this class is
 * associated with each instance of <code>StandardContext</code>.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Revision: 377994 $ $Date: 2006-02-15 13:37:28 +0100 (mer., 15 févr. 2006) $
 */

public class ApplicationContext
    implements ServletContext {

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new instance of this class, associated with the specified
     * Context instance.
     *
     * @param context The associated Context instance
     */
    public ApplicationContext(String basePath, StandardContext context) {
        super();
        this.context = context;
        this.basePath = basePath;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The context attributes for this context.
     */
    private HashMap attributes = new HashMap();


    /**
     * List of read only attributes for this context.
     */
    private HashMap readOnlyAttributes = new HashMap();


    /**
     * The Context instance with which we are associated.
     */
    private StandardContext context = null;


    /**
     * Empty collection to serve as the basis for empty enumerations.
     * <strong>DO NOT ADD ANY ELEMENTS TO THIS COLLECTION!</strong>
     */
    private static final ArrayList empty = new ArrayList();


    /**
     * The facade around this object.
     */
    private ServletContext facade = new ApplicationContextFacade(this);


    /**
     * The merged context initialization parameters for this Context.
     */
    private HashMap parameters = null;


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
      StringManager.getManager(Constants.Package);


    /**
     * Base path.
     */
    private String basePath = null;


    /**
     * Thread local mapping data.
     */
    private ThreadLocal localMappingData = new ThreadLocal();


    /**
     * Thread local URI message bytes.
     */
    private ThreadLocal localUriMB = new ThreadLocal();


    // --------------------------------------------------------- Public Methods


    /**
     * Return the resources object that is mapped to a specified path.
     * The path must begin with a "/" and is interpreted as relative to the
     * current context root.
     */
    public DirContext getResources() {

        return context.getResources();

    }


    // ------------------------------------------------- ServletContext Methods


    /**
     * Return the value of the specified context attribute, if any;
     * otherwise return <code>null</code>.
     *
     * @param name Name of the context attribute to return
     */
    public Object getAttribute(String name) {

        synchronized (attributes) {
            return (attributes.get(name));
        }

    }


    /**
     * Return an enumeration of the names of the context attributes
     * associated with this context.
     */
    public Enumeration getAttributeNames() {

        synchronized (attributes) {
            return new Enumerator(attributes.keySet(), true);
        }

    }


    /**
     * Return a <code>ServletContext</code> object that corresponds to a
     * specified URI on the server.  This method allows servlets to gain
     * access to the context for various parts of the server, and as needed
     * obtain <code>RequestDispatcher</code> objects or resources from the
     * context.  The given path must be absolute (beginning with a "/"),
     * and is interpreted based on our virtual host's document root.
     *
     * @param uri Absolute URI of a resource on the server
     */
    public ServletContext getContext(String uri) {

        // Validate the format of the specified argument
        if ((uri == null) || (!uri.startsWith("/")))
            return (null);

        Context child = null;
        try {
            Host host = (Host) context.getParent();
            String mapuri = uri;
            while (true) {
                child = (Context) host.findChild(mapuri);
                if (child != null)
                    break;
                int slash = mapuri.lastIndexOf('/');
                if (slash < 0)
                    break;
                mapuri = mapuri.substring(0, slash);
            }
        } catch (Throwable t) {
            return (null);
        }

        if (child == null)
            return (null);

        if (context.getCrossContext()) {
            // If crossContext is enabled, can always return the context
            return child.getServletContext();
        } else if (child == context) {
            // Can still return the current context
            return context.getServletContext();
        } else {
            // Nothing to return
            return (null);
        }
    }

    
    /**
     * Return the main path associated with this context.
     */
    public String getContextPath() {
        return context.getPath();
    }
    

    /**
     * Return the value of the specified initialization parameter, or
     * <code>null</code> if this parameter does not exist.
     *
     * @param name Name of the initialization parameter to retrieve
     */
    public String getInitParameter(final String name) {

        mergeParameters();
        synchronized (parameters) {
            return ((String) parameters.get(name));
        }
    }


    /**
     * Return the names of the context's initialization parameters, or an
     * empty enumeration if the context has no initialization parameters.
     */
    public Enumeration getInitParameterNames() {

        mergeParameters();
        synchronized (parameters) {
           return (new Enumerator(parameters.keySet()));
        }

    }


    /**
     * Return the major version of the Java Servlet API that we implement.
     */
    public int getMajorVersion() {

        return (Constants.MAJOR_VERSION);

    }


    /**
     * Return the minor version of the Java Servlet API that we implement.
     */
    public int getMinorVersion() {

        return (Constants.MINOR_VERSION);

    }


    /**
     * Return the MIME type of the specified file, or <code>null</code> if
     * the MIME type cannot be determined.
     *
     * @param file Filename for which to identify a MIME type
     */
    public String getMimeType(String file) {

        if (file == null)
            return (null);
        int period = file.lastIndexOf(".");
        if (period < 0)
            return (null);
        String extension = file.substring(period + 1);
        if (extension.length() < 1)
            return (null);
        return (context.findMimeMapping(extension));

    }


    /**
     * Return a <code>RequestDispatcher</code> object that acts as a
     * wrapper for the named servlet.
     *
     * @param name Name of the servlet for which a dispatcher is requested
     */
    public RequestDispatcher getNamedDispatcher(String name) {

        // Validate the name argument
        if (name == null)
            return (null);

        // Create and return a corresponding request dispatcher
        Wrapper wrapper = (Wrapper) context.findChild(name);
        if (wrapper == null)
            return (null);
        
        return new ApplicationDispatcher(wrapper, null, null, null, null, name);

    }


    /**
     * Return the real path for a given virtual path, if possible; otherwise
     * return <code>null</code>.
     *
     * @param path The path to the desired resource
     */
    public String getRealPath(String path) {

        if (!context.isFilesystemBased())
            return null;

        if (path == null) {
            return null;
        }

        File file = new File(basePath, path);
        return (file.getAbsolutePath());

    }


    /**
     * Return a <code>RequestDispatcher</code> instance that acts as a
     * wrapper for the resource at the given path.  The path must begin
     * with a "/" and is interpreted as relative to the current context root.
     *
     * @param path The path to the desired resource.
     */
    public RequestDispatcher getRequestDispatcher(String path) {

        // Validate the path argument
        if (path == null)
            return (null);
        if (!path.startsWith("/"))
            throw new IllegalArgumentException
                (sm.getString
                 ("applicationContext.requestDispatcher.iae", path));
        path = normalize(path);
        if (path == null)
            return (null);

        // Retrieve the thread local URI
        MessageBytes uriMB = (MessageBytes) localUriMB.get();
        if (uriMB == null) {
            uriMB = MessageBytes.newInstance();
            CharChunk uriCC = uriMB.getCharChunk();
            uriCC.setLimit(-1);
            localUriMB.set(uriMB);
        } else {
            uriMB.recycle();
        }

        // Get query string
        String queryString = null;
        int pos = path.indexOf('?');
        if (pos >= 0) {
            queryString = path.substring(pos + 1);
        } else {
            pos = path.length();
        }
 
        // Retrieve the thread local mapping data
        MappingData mappingData = (MappingData) localMappingData.get();
        if (mappingData == null) {
            mappingData = new MappingData();
            localMappingData.set(mappingData);
        }

        // Map the URI
        CharChunk uriCC = uriMB.getCharChunk();
        try {
            uriCC.append(context.getPath(), 0, context.getPath().length());
            /*
             * Ignore any trailing path params (separated by ';') for mapping
             * purposes
             */
            int semicolon = path.indexOf(';');
            if (pos >= 0 && semicolon > pos) {
                semicolon = -1;
            }
            uriCC.append(path, 0, semicolon > 0 ? semicolon : pos);
            context.getMapper().map(uriMB, mappingData);
            if (mappingData.wrapper == null) {
                return (null);
            }
            /*
             * Append any trailing path params (separated by ';') that were
             * ignored for mapping purposes, so that they're reflected in the
             * RequestDispatcher's requestURI
             */
            if (semicolon > 0) {
                uriCC.append(path, semicolon, pos - semicolon);
            }
        } catch (Exception e) {
            // Should never happen
            log(sm.getString("applicationContext.mapping.error"), e);
            return (null);
        }

        Wrapper wrapper = (Wrapper) mappingData.wrapper;
        String wrapperPath = mappingData.wrapperPath.toString();
        String pathInfo = mappingData.pathInfo.toString();

        mappingData.recycle();
        
        // Construct a RequestDispatcher to process this request
        return new ApplicationDispatcher
            (wrapper, uriCC.toString(), wrapperPath, pathInfo, 
             queryString, null);

    }



    /**
     * Return the URL to the resource that is mapped to a specified path.
     * The path must begin with a "/" and is interpreted as relative to the
     * current context root.
     *
     * @param path The path to the desired resource
     *
     * @exception MalformedURLException if the path is not given
     *  in the correct form
     */
    public URL getResource(String path)
        throws MalformedURLException {

        if (path == null || !path.startsWith("/")) {
            throw new MalformedURLException(sm.getString("applicationContext.requestDispatcher.iae", path));
        }
        
        path = normalize(path);
        if (path == null)
            return (null);

        String libPath = "/WEB-INF/lib/";
        if ((path.startsWith(libPath)) && (path.endsWith(".jar"))) {
            File jarFile = null;
            if (context.isFilesystemBased()) {
                jarFile = new File(basePath, path);
            } else {
                jarFile = new File(context.getWorkPath(), path);
            }
            if (jarFile.exists()) {
                return jarFile.toURL();
            } else {
                return null;
            }
        } else {

            DirContext resources = context.getResources();
            if (resources != null) {
                String fullPath = context.getName() + path;
                String hostName = context.getParent().getName();
                try {
                    resources.lookup(path);
                    return new URL
                        ("jndi", "", 0, getJNDIUri(hostName, fullPath),
                         new DirContextURLStreamHandler(resources));
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        return (null);

    }


    /**
     * Return the requested resource as an <code>InputStream</code>.  The
     * path must be specified according to the rules described under
     * <code>getResource</code>.  If no such resource can be identified,
     * return <code>null</code>.
     *
     * @param path The path to the desired resource.
     */
    public InputStream getResourceAsStream(String path) {

        path = normalize(path);
        if (path == null)
            return (null);

        DirContext resources = context.getResources();
        if (resources != null) {
            try {
                Object resource = resources.lookup(path);
                if (resource instanceof Resource)
                    return (((Resource) resource).streamContent());
            } catch (Exception e) {
            }
        }
        return (null);

    }


    /**
     * Return a Set containing the resource paths of resources member of the
     * specified collection. Each path will be a String starting with
     * a "/" character. The returned set is immutable.
     *
     * @param path Collection path
     */
    public Set getResourcePaths(String path) {

        // Validate the path argument
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException
                (sm.getString("applicationContext.resourcePaths.iae", path));
        }

        path = normalize(path);
        if (path == null)
            return (null);

        DirContext resources = context.getResources();
        if (resources != null) {
            return (getResourcePathsInternal(resources, path));
        }
        return (null);

    }


    /**
     * Internal implementation of getResourcesPath() logic.
     *
     * @param resources Directory context to search
     * @param path Collection path
     */
    private Set getResourcePathsInternal(DirContext resources, String path) {

        ResourceSet set = new ResourceSet();
        try {
            listCollectionPaths(set, resources, path);
        } catch (NamingException e) {
            return (null);
        }
        set.setLocked(true);
        return (set);

    }


    /**
     * Return the name and version of the servlet container.
     */
    public String getServerInfo() {

        return (ServerInfo.getServerInfo());

    }


    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Servlet getServlet(String name) {

        return (null);

    }


    /**
     * Return the display name of this web application.
     */
    public String getServletContextName() {

        return (context.getDisplayName());

    }


    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Enumeration getServletNames() {
        return (new Enumerator(empty));
    }


    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Enumeration getServlets() {
        return (new Enumerator(empty));
    }


    /**
     * Writes the specified message to a servlet log file.
     *
     * @param message Message to be written
     */
    public void log(String message) {

        context.getLogger().info(message);

    }


    /**
     * Writes the specified exception and message to a servlet log file.
     *
     * @param exception Exception to be reported
     * @param message Message to be written
     *
     * @deprecated As of Java Servlet API 2.1, use
     *  <code>log(String, Throwable)</code> instead
     */
    public void log(Exception exception, String message) {
        
        context.getLogger().error(message, exception);

    }


    /**
     * Writes the specified message and exception to a servlet log file.
     *
     * @param message Message to be written
     * @param throwable Exception to be reported
     */
    public void log(String message, Throwable throwable) {
        
        context.getLogger().error(message, throwable);

    }


    /**
     * Remove the context attribute with the specified name, if any.
     *
     * @param name Name of the context attribute to be removed
     */
    public void removeAttribute(String name) {

        Object value = null;
        boolean found = false;

        // Remove the specified attribute
        synchronized (attributes) {
            // Check for read only attribute
           if (readOnlyAttributes.containsKey(name))
                return;
            found = attributes.containsKey(name);
            if (found) {
                value = attributes.get(name);
                attributes.remove(name);
            } else {
                return;
            }
        }

        // Notify interested application event listeners
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0))
            return;
        ServletContextAttributeEvent event =
          new ServletContextAttributeEvent(context.getServletContext(),
                                            name, value);
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletContextAttributeListener))
                continue;
            ServletContextAttributeListener listener =
                (ServletContextAttributeListener) listeners[i];
            try {
                context.fireContainerEvent("beforeContextAttributeRemoved",
                                           listener);
                listener.attributeRemoved(event);
                context.fireContainerEvent("afterContextAttributeRemoved",
                                           listener);
            } catch (Throwable t) {
                context.fireContainerEvent("afterContextAttributeRemoved",
                                           listener);
                // FIXME - should we do anything besides log these?
                log(sm.getString("applicationContext.attributeEvent"), t);
            }
        }

    }


    /**
     * Bind the specified value with the specified context attribute name,
     * replacing any existing value for that name.
     *
     * @param name Attribute name to be bound
     * @param value New attribute value to be bound
     */
    public void setAttribute(String name, Object value) {

        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("applicationContext.setAttribute.namenull"));

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        Object oldValue = null;
        boolean replaced = false;

        // Add or replace the specified attribute
        synchronized (attributes) {
            // Check for read only attribute
            if (readOnlyAttributes.containsKey(name))
                return;
            oldValue = attributes.get(name);
            if (oldValue != null)
                replaced = true;
            attributes.put(name, value);
        }

        // Notify interested application event listeners
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0))
            return;
        ServletContextAttributeEvent event = null;
        if (replaced)
            event =
                new ServletContextAttributeEvent(context.getServletContext(),
                                                 name, oldValue);
        else
            event =
                new ServletContextAttributeEvent(context.getServletContext(),
                                                 name, value);

        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletContextAttributeListener))
                continue;
            ServletContextAttributeListener listener =
                (ServletContextAttributeListener) listeners[i];
            try {
                if (replaced) {
                    context.fireContainerEvent
                        ("beforeContextAttributeReplaced", listener);
                    listener.attributeReplaced(event);
                    context.fireContainerEvent("afterContextAttributeReplaced",
                                               listener);
                } else {
                    context.fireContainerEvent("beforeContextAttributeAdded",
                                               listener);
                    listener.attributeAdded(event);
                    context.fireContainerEvent("afterContextAttributeAdded",
                                               listener);
                }
            } catch (Throwable t) {
                if (replaced)
                    context.fireContainerEvent("afterContextAttributeReplaced",
                                               listener);
                else
                    context.fireContainerEvent("afterContextAttributeAdded",
                                               listener);
                // FIXME - should we do anything besides log these?
                log(sm.getString("applicationContext.attributeEvent"), t);
            }
        }

    }


    // -------------------------------------------------------- Package Methods


    /**
     * Clear all application-created attributes.
     */
    void clearAttributes() {

        // Create list of attributes to be removed
        ArrayList list = new ArrayList();
        synchronized (attributes) {
            Iterator iter = attributes.keySet().iterator();
            while (iter.hasNext()) {
                list.add(iter.next());
            }
        }

        // Remove application originated attributes
        // (read only attributes will be left in place)
        Iterator keys = list.iterator();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            removeAttribute(key);
        }
        
    }
    
    
    /**
     * Return the facade associated with this ApplicationContext.
     */
    protected ServletContext getFacade() {

        return (this.facade);

    }


    /**
     * Set an attribute as read only.
     */
    void setAttributeReadOnly(String name) {

        synchronized (attributes) {
            if (attributes.containsKey(name))
                readOnlyAttributes.put(name, name);
        }

    }


    // -------------------------------------------------------- Private Methods


    /**
     * Return a context-relative path, beginning with a "/", that represents
     * the canonical version of the specified path after ".." and "." elements
     * are resolved out.  If the specified path attempts to go outside the
     * boundaries of the current context (i.e. too many ".." path elements
     * are present), return <code>null</code> instead.
     *
     * @param path Path to be normalized
     */
    private String normalize(String path) {

        if (path == null) {
            return null;
        }

        String normalized = path;

        // Normalize the slashes and add leading slash if necessary
        if (normalized.indexOf('\\') >= 0)
            normalized = normalized.replace('\\', '/');

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0)
                break;
            if (index == 0)
                return (null);  // Trying to go outside our context
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2) +
                normalized.substring(index + 3);
        }

        // Return the normalized path that we have completed
        return (normalized);

    }


    /**
     * Merge the context initialization parameters specified in the application
     * deployment descriptor with the application parameters described in the
     * server configuration, respecting the <code>override</code> property of
     * the application parameters appropriately.
     */
    private void mergeParameters() {

        if (parameters != null)
            return;
        HashMap results = new HashMap();
        String names[] = context.findParameters();
        for (int i = 0; i < names.length; i++)
            results.put(names[i], context.findParameter(names[i]));
        ApplicationParameter params[] =
            context.findApplicationParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].getOverride()) {
                if (results.get(params[i].getName()) == null)
                    results.put(params[i].getName(), params[i].getValue());
            } else {
                results.put(params[i].getName(), params[i].getValue());
            }
        }
        parameters = results;

    }


    /**
     * List resource paths (recursively), and store all of them in the given
     * Set.
     */
    private static void listCollectionPaths
        (Set set, DirContext resources, String path)
        throws NamingException {

        Enumeration childPaths = resources.listBindings(path);
        while (childPaths.hasMoreElements()) {
            Binding binding = (Binding) childPaths.nextElement();
            String name = binding.getName();
            StringBuffer childPath = new StringBuffer(path);
            if (!"/".equals(path) && !path.endsWith("/"))
                childPath.append("/");
            childPath.append(name);
            Object object = binding.getObject();
            if (object instanceof DirContext) {
                childPath.append("/");
            }
            set.add(childPath.toString());
        }

    }


    /**
     * Get full path, based on the host name and the context path.
     */
    private static String getJNDIUri(String hostName, String path) {
        if (!path.startsWith("/"))
            return "/" + hostName + "/" + path;
        else
            return "/" + hostName + path;
    }


}
