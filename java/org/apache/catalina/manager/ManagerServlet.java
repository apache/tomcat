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


package org.apache.catalina.manager;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.naming.Binding;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.Role;
import org.apache.catalina.Server;
import org.apache.catalina.ServerFactory;
import org.apache.catalina.Session;
import org.apache.catalina.UserDatabase;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.StringManager;
import org.apache.tomcat.util.modeler.Registry;


/**
 * Servlet that enables remote management of the web applications installed
 * within the same virtual host as this web application is.  Normally, this
 * functionality will be protected by a security constraint in the web
 * application deployment descriptor.  However, this requirement can be
 * relaxed during testing.
 * <p>
 * This servlet examines the value returned by <code>getPathInfo()</code>
 * and related query parameters to determine what action is being requested.
 * The following actions and parameters (starting after the servlet path)
 * are supported:
 * <ul>
 * <li><b>/deploy?config={config-url}</b> - Install and start a new
 *     web application, based on the contents of the context configuration
 *     file found at the specified URL.  The <code>docBase</code> attribute
 *     of the context configuration file is used to locate the actual
 *     WAR or directory containing the application.</li>
 * <li><b>/deploy?config={config-url}&war={war-url}/</b> - Install and start
 *     a new web application, based on the contents of the context
 *     configuration file found at <code>{config-url}</code>, overriding the
 *     <code>docBase</code> attribute with the contents of the web
 *     application archive found at <code>{war-url}</code>.</li>
 * <li><b>/deploy?path=/xxx&war={war-url}</b> - Install and start a new
 *     web application attached to context path <code>/xxx</code>, based
 *     on the contents of the web application archive found at the
 *     specified URL.</li>
 * <li><b>/list</b> - List the context paths of all currently installed web
 *     applications for this virtual host.  Each context will be listed with
 *     the following format <code>path:status:sessions</code>.
 *     Where path is the context path.  Status is either running or stopped.
 *     Sessions is the number of active Sessions.</li>
 * <li><b>/reload?path=/xxx</b> - Reload the Java classes and resources for
 *     the application at the specified path.</li>
 * <li><b>/resources?type=xxxx</b> - Enumerate the available global JNDI
 *     resources, optionally limited to those of the specified type
 *     (fully qualified Java class name), if available.</li>
 * <li><b>/roles</b> - Enumerate the available security role names and
 *     descriptions from the user database connected to the <code>users</code>
 *     resource reference.
 * <li><b>/serverinfo</b> - Display system OS and JVM properties.
 * <li><b>/sessions?path=/xxx</b> - List session information about the web
 *     application attached to context path <code>/xxx</code> for this
 *     virtual host.</li>
 * <li><b>/start?path=/xxx</b> - Start the web application attached to
 *     context path <code>/xxx</code> for this virtual host.</li>
 * <li><b>/stop?path=/xxx</b> - Stop the web application attached to
 *     context path <code>/xxx</code> for this virtual host.</li>
 * <li><b>/undeploy?path=/xxx</b> - Shutdown and remove the web application
 *     attached to context path <code>/xxx</code> for this virtual host,
 *     and remove the underlying WAR file or document base directory.
 *     (<em>NOTE</em> - This is only allowed if the WAR file or document
 *     base is stored in the <code>appBase</code> directory of this host,
 *     typically as a result of being placed there via the <code>/deploy</code>
 *     command.</li>
 * </ul>
 * <p>Use <code>path=/</code> for the ROOT context.</p>
 * <p>The syntax of the URL for a web application archive must conform to one
 * of the following patterns to be successfully deployed:</p>
 * <ul>
 * <li><b>file:/absolute/path/to/a/directory</b> - You can specify the absolute
 *     path of a directory that contains the unpacked version of a web
 *     application.  This directory will be attached to the context path you
 *     specify without any changes.</li>
 * <li><b>jar:file:/absolute/path/to/a/warfile.war!/</b> - You can specify a
 *     URL to a local web application archive file.  The syntax must conform to
 *     the rules specified by the <code>JarURLConnection</code> class for a
 *     reference to an entire JAR file.</li>
 * <li><b>jar:http://hostname:port/path/to/a/warfile.war!/</b> - You can specify
 *     a URL to a remote (HTTP-accessible) web application archive file.  The
 *     syntax must conform to the rules specified by the
 *     <code>JarURLConnection</code> class for a reference to an entire
 *     JAR file.</li>
 * </ul>
 * <p>
 * <b>NOTE</b> - Attempting to reload or remove the application containing
 * this servlet itself will not succeed.  Therefore, this servlet should
 * generally be deployed as a separate web application within the virtual host
 * to be managed.
 * <p>
 * <b>NOTE</b> - For security reasons, this application will not operate
 * when accessed via the invoker servlet.  You must explicitly map this servlet
 * with a servlet mapping, and you will always want to protect it with
 * appropriate security constraints as well.
 * <p>
 * The following servlet initialization parameters are recognized:
 * <ul>
 * <li><b>debug</b> - The debugging detail level that controls the amount
 *     of information that is logged by this servlet.  Default is zero.
 * </ul>
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Revision: 393613 $ $Date: 2006-04-12 23:08:01 +0200 (mer., 12 avr. 2006) $
 */

public class ManagerServlet
    extends HttpServlet implements ContainerServlet {


    // ----------------------------------------------------- Instance Variables


    /**
     * Path where context descriptors should be deployed.
     */
    protected File configBase = null;


    /**
     * The Context container associated with our web application.
     */
    protected Context context = null;


    /**
     * The debugging detail level for this servlet.
     */
    protected int debug = 1;


    /**
     * File object representing the directory into which the deploy() command
     * will store the WAR and context configuration files that have been
     * uploaded.
     */
    protected File deployed = null;


    /**
     * Path used to store revisions of webapps.
     */
    protected File versioned = null;


    /**
     * Path used to store context descriptors.
     */
    protected File contextDescriptors = null;


    /**
     * The associated host.
     */
    protected Host host = null;

    
    /**
     * The host appBase.
     */
    protected File appBase = null;
    
    
    /**
     * MBean server.
     */
    protected MBeanServer mBeanServer = null;


    /**
     * The associated deployer ObjectName.
     */
    protected ObjectName oname = null;
    

    /**
     * The global JNDI <code>NamingContext</code> for this server,
     * if available.
     */
    protected javax.naming.Context global = null;


    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * The Wrapper container associated with this servlet.
     */
    protected Wrapper wrapper = null;


    // ----------------------------------------------- ContainerServlet Methods


    /**
     * Return the Wrapper with which we are associated.
     */
    public Wrapper getWrapper() {

        return (this.wrapper);

    }


    /**
     * Set the Wrapper with which we are associated.
     *
     * @param wrapper The new wrapper
     */
    public void setWrapper(Wrapper wrapper) {

        this.wrapper = wrapper;
        if (wrapper == null) {
            context = null;
            host = null;
            oname = null;
        } else {
            context = (Context) wrapper.getParent();
            host = (Host) context.getParent();
            Engine engine = (Engine) host.getParent();
            try {
                oname = new ObjectName(engine.getName() 
                        + ":type=Deployer,host=" + host.getName());
            } catch (Exception e) {
                // ?
            }
        }

        // Retrieve the MBean server
        mBeanServer = Registry.getRegistry(null, null).getMBeanServer();
        
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Finalize this servlet.
     */
    public void destroy() {

        ;       // No actions necessary

    }


    /**
     * Process a GET request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws IOException, ServletException {

        // Verify that we were not accessed using the invoker servlet
        if (request.getAttribute(Globals.INVOKED_ATTR) != null)
            throw new UnavailableException
                (sm.getString("managerServlet.cannotInvoke"));

        // Identify the request parameters that we need
        String command = request.getPathInfo();
        if (command == null)
            command = request.getServletPath();
        String config = request.getParameter("config");
        String path = request.getParameter("path");
        String type = request.getParameter("type");
        String war = request.getParameter("war");
        String tag = request.getParameter("tag");
        boolean update = false;
        if ((request.getParameter("update") != null) 
            && (request.getParameter("update").equals("true"))) {
            update = true;
        }

        // Prepare our output writer to generate the response message
        response.setContentType("text/plain; charset=" + Constants.CHARSET);
        PrintWriter writer = response.getWriter();

        // Process the requested command (note - "/deploy" is not listed here)
        if (command == null) {
            writer.println(sm.getString("managerServlet.noCommand"));
        } else if (command.equals("/deploy")) {
            if (war != null || config != null) {
                deploy(writer, config, path, war, update);
            } else {
                deploy(writer, path, tag);
            }
        } else if (command.equals("/install")) {
            // Deprecated
            deploy(writer, config, path, war, false);
        } else if (command.equals("/list")) {
            list(writer);
        } else if (command.equals("/reload")) {
            reload(writer, path);
        } else if (command.equals("/remove")) {
            // Deprecated
            undeploy(writer, path);
        } else if (command.equals("/resources")) {
            resources(writer, type);
        } else if (command.equals("/roles")) {
            roles(writer);
        } else if (command.equals("/save")) {
            save(writer, path);
        } else if (command.equals("/serverinfo")) {
            serverinfo(writer);
        } else if (command.equals("/sessions")) {
            sessions(writer, path);
        } else if (command.equals("/start")) {
            start(writer, path);
        } else if (command.equals("/stop")) {
            stop(writer, path);
        } else if (command.equals("/undeploy")) {
            undeploy(writer, path);
        } else {
            writer.println(sm.getString("managerServlet.unknownCommand",
                                        command));
        }

        // Finish up the response
        writer.flush();
        writer.close();

    }


    /**
     * Process a PUT request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    public void doPut(HttpServletRequest request,
                      HttpServletResponse response)
        throws IOException, ServletException {

        // Verify that we were not accessed using the invoker servlet
        if (request.getAttribute(Globals.INVOKED_ATTR) != null)
            throw new UnavailableException
                (sm.getString("managerServlet.cannotInvoke"));

        // Identify the request parameters that we need
        String command = request.getPathInfo();
        if (command == null)
            command = request.getServletPath();
        String path = request.getParameter("path");
        String tag = request.getParameter("tag");
        boolean update = false;
        if ((request.getParameter("update") != null) 
            && (request.getParameter("update").equals("true"))) {
            update = true;
        }

        // Prepare our output writer to generate the response message
        response.setContentType("text/plain;charset="+Constants.CHARSET);
        PrintWriter writer = response.getWriter();

        // Process the requested command
        if (command == null) {
            writer.println(sm.getString("managerServlet.noCommand"));
        } else if (command.equals("/deploy")) {
            deploy(writer, path, tag, update, request);
        } else {
            writer.println(sm.getString("managerServlet.unknownCommand",
                                        command));
        }

        // Finish up the response
        writer.flush();
        writer.close();

    }


    /**
     * Initialize this servlet.
     */
    public void init() throws ServletException {

        // Ensure that our ContainerServlet properties have been set
        if ((wrapper == null) || (context == null))
            throw new UnavailableException
                (sm.getString("managerServlet.noWrapper"));

        // Verify that we were not accessed using the invoker servlet
        String servletName = getServletConfig().getServletName();
        if (servletName == null)
            servletName = "";
        if (servletName.startsWith("org.apache.catalina.INVOKER."))
            throw new UnavailableException
                (sm.getString("managerServlet.cannotInvoke"));

        // Set our properties from the initialization parameters
        String value = null;
        try {
            value = getServletConfig().getInitParameter("debug");
            debug = Integer.parseInt(value);
        } catch (Throwable t) {
            ;
        }

        // Acquire global JNDI resources if available
        Server server = ServerFactory.getServer();
        if ((server != null) && (server instanceof StandardServer)) {
            global = ((StandardServer) server).getGlobalNamingContext();
        }

        // Calculate the directory into which we will be deploying applications
        versioned = (File) getServletContext().getAttribute
            ("javax.servlet.context.tempdir");

        // Identify the appBase of the owning Host of this Context
        // (if any)
        String appBase = ((Host) context.getParent()).getAppBase();
        deployed = new File(appBase);
        if (!deployed.isAbsolute()) {
            deployed = new File(System.getProperty("catalina.base"),
                                appBase);
        }
        configBase = new File(System.getProperty("catalina.base"), "conf");
        Container container = context;
        Container host = null;
        Container engine = null;
        while (container != null) {
            if (container instanceof Host)
                host = container;
            if (container instanceof Engine)
                engine = container;
            container = container.getParent();
        }
        if (engine != null) {
            configBase = new File(configBase, engine.getName());
        }
        if (host != null) {
            configBase = new File(configBase, host.getName());
        }
        // Note: The directory must exist for this to work.

        // Log debugging messages as necessary
        if (debug >= 1) {
            log("init: Associated with Deployer '" +
                oname + "'");
            if (global != null) {
                log("init: Global resources are available");
            }
        }

    }



    // -------------------------------------------------------- Private Methods


    /**
     * Store server configuration.
     * 
     * @param path Optional context path to save
     */
    protected synchronized void save(PrintWriter writer, String path) {

        Server server = ServerFactory.getServer();

        if (!(server instanceof StandardServer)) {
            writer.println(sm.getString("managerServlet.saveFail", server));
            return;
        }

        if ((path == null) || path.length() == 0 || !path.startsWith("/")) {
            try {
                ((StandardServer) server).storeConfig();
                writer.println(sm.getString("managerServlet.saved"));
            } catch (Exception e) {
                log("managerServlet.storeConfig", e);
                writer.println(sm.getString("managerServlet.exception",
                                            e.toString()));
                return;
            }
        } else {
            String contextPath = path;
            if (path.equals("/")) {
                contextPath = "";
            }
            Context context = (Context) host.findChild(contextPath);
            if (context == null) {
                writer.println(sm.getString("managerServlet.noContext", path));
                return;
            }
            try {
                ((StandardServer) server).storeContext(context);
                writer.println(sm.getString("managerServlet.savedContext", 
                               path));
            } catch (Exception e) {
                log("managerServlet.save[" + path + "]", e);
                writer.println(sm.getString("managerServlet.exception",
                                            e.toString()));
                return;
            }
        }

    }


    /**
     * Deploy a web application archive (included in the current request)
     * at the specified context path.
     *
     * @param writer Writer to render results to
     * @param path Context path of the application to be installed
     * @param tag Tag to be associated with the webapp
     * @param request Servlet request we are processing
     */
    protected synchronized void deploy
        (PrintWriter writer, String path,
         String tag, boolean update, HttpServletRequest request) {

        if (debug >= 1) {
            log("deploy: Deploying web application at '" + path + "'");
        }

        // Validate the requested context path
        if ((path == null) || path.length() == 0 || !path.startsWith("/")) {
            writer.println(sm.getString("managerServlet.invalidPath", path));
            return;
        }
        String displayPath = path;
        if( path.equals("/") )
            path = "";
        String basename = getDocBase(path);

        // Check if app already exists, or undeploy it if updating
        Context context = (Context) host.findChild(path);
        if (update) {
            if (context != null) {
                undeploy(writer, displayPath);
            }
            context = (Context) host.findChild(path);
        }
        if (context != null) {
            writer.println
                (sm.getString("managerServlet.alreadyContext",
                              displayPath));
            return;
        }

        // Calculate the base path
        File deployedPath = deployed;
        if (tag != null) {
            deployedPath = new File(versioned, tag);
            deployedPath.mkdirs();
        }

        // Upload the web application archive to a local WAR file
        File localWar = new File(deployedPath, basename + ".war");
        if (debug >= 2) {
            log("Uploading WAR file to " + localWar);
        }

        // Copy WAR to appBase
        try {
            if (!isServiced(path)) {
                addServiced(path);
                try {
                    // Upload WAR
                    uploadWar(request, localWar);
                    // Copy WAR and XML to the host app base if needed
                    if (tag != null) {
                        deployedPath = deployed;
                        File localWarCopy = new File(deployedPath, basename + ".war");
                        copy(localWar, localWarCopy);
                        localWar = localWarCopy;
                        copy(localWar, new File(getAppBase(), basename + ".war"));
                    }
                    // Perform new deployment
                    check(path);
                } finally {
                    removeServiced(path);
                }
            }
        } catch (Exception e) {
            log("managerServlet.check[" + displayPath + "]", e);
            writer.println(sm.getString("managerServlet.exception",
                                        e.toString()));
            return;
        }
        
        context = (Context) host.findChild(path);
        if (context != null && context.getConfigured()) {
            writer.println(sm.getString("managerServlet.deployed", displayPath));
        } else {
            // Something failed
            writer.println(sm.getString("managerServlet.deployFailed", displayPath));
        }
        
    }


    /**
     * Install an application for the specified path from the specified
     * web application archive.
     *
     * @param writer Writer to render results to
     * @param tag Revision tag to deploy from
     * @param path Context path of the application to be installed
     */
    protected void deploy(PrintWriter writer, String path, String tag) {

        // Validate the requested context path
        if ((path == null) || path.length() == 0 || !path.startsWith("/")) {
            writer.println(sm.getString("managerServlet.invalidPath", path));
            return;
        }
        String displayPath = path;
        if( path.equals("/") )
            path = "";

        // Calculate the base path
        File deployedPath = versioned;
        if (tag != null) {
            deployedPath = new File(deployedPath, tag);
        }

        // Find the local WAR file
        File localWar = new File(deployedPath, getDocBase(path) + ".war");
        // Find the local context deployment file (if any)
        File localXml = new File(configBase, getConfigFile(path) + ".xml");

        // Check if app already exists, or undeploy it if updating
        Context context = (Context) host.findChild(path);
        if (context != null) {
            undeploy(writer, displayPath);
        }

        // Copy WAR to appBase
        try {
            if (!isServiced(path)) {
                addServiced(path);
                try {
                    copy(localWar, new File(getAppBase(), getDocBase(path) + ".war"));
                    // Perform new deployment
                    check(path);
                } finally {
                    removeServiced(path);
                }
            }
        } catch (Exception e) {
            log("managerServlet.check[" + displayPath + "]", e);
            writer.println(sm.getString("managerServlet.exception",
                                        e.toString()));
            return;
        }
        
        context = (Context) host.findChild(path);
        if (context != null && context.getConfigured()) {
            writer.println(sm.getString("managerServlet.deployed", displayPath));
        } else {
            // Something failed
            writer.println(sm.getString("managerServlet.deployFailed", displayPath));
        }
        
    }


    /**
     * Install an application for the specified path from the specified
     * web application archive.
     *
     * @param writer Writer to render results to
     * @param config URL of the context configuration file to be installed
     * @param path Context path of the application to be installed
     * @param war URL of the web application archive to be installed
     * @param update true to override any existing webapp on the path
     */
    protected void deploy(PrintWriter writer, String config,
            String path, String war, boolean update) {
        
        if (config != null && config.length() == 0) {
            config = null;
        }
        if (war != null && war.length() == 0) {
            war = null;
        }
        
        if (debug >= 1) {
            if (config != null && config.length() > 0) {
                if (war != null) {
                    log("install: Installing context configuration at '" +
                            config + "' from '" + war + "'");
                } else {
                    log("install: Installing context configuration at '" +
                            config + "'");
                }
            } else {
                if (path != null && path.length() > 0) {
                    log("install: Installing web application at '" + path +
                            "' from '" + war + "'");
                } else {
                    log("install: Installing web application from '" + war + "'");
                }
            }
        }
        
        if (path == null || path.length() == 0 || !path.startsWith("/")) {
            writer.println(sm.getString("managerServlet.invalidPath",
                                        RequestUtil.filter(path)));
            return;
        }
        String displayPath = path;
        if("/".equals(path)) {
            path = "";
        }
        
        // Check if app already exists, or undeploy it if updating
        Context context = (Context) host.findChild(path);
        if (update) {
            if (context != null) {
                undeploy(writer, displayPath);
            }
            context = (Context) host.findChild(path);
        }
        if (context != null) {
            writer.println
            (sm.getString("managerServlet.alreadyContext",
                    displayPath));
            return;
        }
        
        if (config != null && (config.startsWith("file:"))) {
            config = config.substring("file:".length());
        }
        if (war != null && (war.startsWith("file:"))) {
            war = war.substring("file:".length());
        }
        
        try {
            if (!isServiced(path)) {
                addServiced(path);
                try {
                    if (config != null) {
                        copy(new File(config), 
                                new File(configBase, getConfigFile(path) + ".xml"));
                    }
                    if (war != null) {
                        if (war.endsWith(".war")) {
                            copy(new File(war), 
                                    new File(getAppBase(), getDocBase(path) + ".war"));
                        } else {
                            copy(new File(war), 
                                    new File(getAppBase(), getDocBase(path)));
                        }
                    }
                    // Perform new deployment
                    check(path);
                } finally {
                    removeServiced(path);
                }
            }
            context = (Context) host.findChild(path);
            if (context != null && context.getConfigured()) {
                writer.println(sm.getString("managerServlet.deployed", displayPath));
            } else {
                // Something failed
                writer.println(sm.getString("managerServlet.deployFailed", displayPath));
            }
        } catch (Throwable t) {
            log("ManagerServlet.install[" + displayPath + "]", t);
            writer.println(sm.getString("managerServlet.exception",
                    t.toString()));
        }
        
    }


    /**
     * Render a list of the currently active Contexts in our virtual host.
     *
     * @param writer Writer to render to
     */
    protected void list(PrintWriter writer) {

        if (debug >= 1)
            log("list: Listing contexts for virtual host '" +
                host.getName() + "'");

        writer.println(sm.getString("managerServlet.listed",
                                    host.getName()));
        Container[] contexts = host.findChildren();
        for (int i = 0; i < contexts.length; i++) {
            Context context = (Context) contexts[i];
            String displayPath = context.getPath();
            if( displayPath.equals("") )
                displayPath = "/";
            if (context != null ) {
                if (context.getAvailable()) {
                    writer.println(sm.getString("managerServlet.listitem",
                                                displayPath,
                                                "running",
                                      "" + context.getManager().findSessions().length,
                                                context.getDocBase()));
                } else {
                    writer.println(sm.getString("managerServlet.listitem",
                                                displayPath,
                                                "stopped",
                                                "0",
                                                context.getDocBase()));
                }
            }
        }
    }


    /**
     * Reload the web application at the specified context path.
     *
     * @param writer Writer to render to
     * @param path Context path of the application to be restarted
     */
    protected void reload(PrintWriter writer, String path) {

        if (debug >= 1)
            log("restart: Reloading web application at '" + path + "'");

        if ((path == null) || (!path.startsWith("/") && path.equals(""))) {
            writer.println(sm.getString("managerServlet.invalidPath",
                                        RequestUtil.filter(path)));
            return;
        }
        String displayPath = path;
        if( path.equals("/") )
            path = "";

        try {
            Context context = (Context) host.findChild(path);
            if (context == null) {
                writer.println(sm.getString
                               ("managerServlet.noContext",
                                   RequestUtil.filter(displayPath)));
                return;
            }
            // It isn't possible for the manager to reload itself
            if (context.getPath().equals(this.context.getPath())) {
                writer.println(sm.getString("managerServlet.noSelf"));
                return;
            }
            context.reload();
            writer.println
                (sm.getString("managerServlet.reloaded", displayPath));
        } catch (Throwable t) {
            log("ManagerServlet.reload[" + displayPath + "]", t);
            writer.println(sm.getString("managerServlet.exception",
                                        t.toString()));
        }

    }


    /**
     * Render a list of available global JNDI resources.
     *
     * @param type Fully qualified class name of the resource type of interest,
     *  or <code>null</code> to list resources of all types
     */
    protected void resources(PrintWriter writer, String type) {

        if (debug >= 1) {
            if (type != null) {
                log("resources:  Listing resources of type " + type);
            } else {
                log("resources:  Listing resources of all types");
            }
        }

        // Is the global JNDI resources context available?
        if (global == null) {
            writer.println(sm.getString("managerServlet.noGlobal"));
            return;
        }

        // Enumerate the global JNDI resources of the requested type
        if (type != null) {
            writer.println(sm.getString("managerServlet.resourcesType",
                                        type));
        } else {
            writer.println(sm.getString("managerServlet.resourcesAll"));
        }

        Class clazz = null;
        try {
            if (type != null) {
                clazz = Class.forName(type);
            }
        } catch (Throwable t) {
            log("ManagerServlet.resources[" + type + "]", t);
            writer.println(sm.getString("managerServlet.exception",
                                        t.toString()));
            return;
        }

        printResources(writer, "", global, type, clazz);

    }


    /**
     * List the resources of the given context.
     */
    protected void printResources(PrintWriter writer, String prefix,
                                  javax.naming.Context namingContext,
                                  String type, Class clazz) {

        try {
            NamingEnumeration items = namingContext.listBindings("");
            while (items.hasMore()) {
                Binding item = (Binding) items.next();
                if (item.getObject() instanceof javax.naming.Context) {
                    printResources
                        (writer, prefix + item.getName() + "/",
                         (javax.naming.Context) item.getObject(), type, clazz);
                } else {
                    if ((clazz != null) &&
                        (!(clazz.isInstance(item.getObject())))) {
                        continue;
                    }
                    writer.print(prefix + item.getName());
                    writer.print(':');
                    writer.print(item.getClassName());
                    // Do we want a description if available?
                    writer.println();
                }
            }
        } catch (Throwable t) {
            log("ManagerServlet.resources[" + type + "]", t);
            writer.println(sm.getString("managerServlet.exception",
                                        t.toString()));
        }

    }


    /**
     * Render a list of security role names (and corresponding descriptions)
     * from the <code>org.apache.catalina.UserDatabase</code> resource that is
     * connected to the <code>users</code> resource reference.  Typically, this
     * will be the global user database, but can be adjusted if you have
     * different user databases for different virtual hosts.
     *
     * @param writer Writer to render to
     */
    protected void roles(PrintWriter writer) {

        if (debug >= 1) {
            log("roles:  List security roles from user database");
        }

        // Look up the UserDatabase instance we should use
        UserDatabase database = null;
        try {
            InitialContext ic = new InitialContext();
            database = (UserDatabase) ic.lookup("java:comp/env/users");
        } catch (NamingException e) {
            writer.println(sm.getString("managerServlet.userDatabaseError"));
            log("java:comp/env/users", e);
            return;
        }
        if (database == null) {
            writer.println(sm.getString("managerServlet.userDatabaseMissing"));
            return;
        }

        // Enumerate the available roles
        writer.println(sm.getString("managerServlet.rolesList"));
        Iterator roles = database.getRoles();
        if (roles != null) {
            while (roles.hasNext()) {
                Role role = (Role) roles.next();
                writer.print(role.getRolename());
                writer.print(':');
                if (role.getDescription() != null) {
                    writer.print(role.getDescription());
                }
                writer.println();
            }
        }


    }


    /**
     * Writes System OS and JVM properties.
     * @param writer Writer to render to
     */
    protected void serverinfo(PrintWriter writer) {
        if (debug >= 1)
            log("serverinfo");
        try {
            StringBuffer props = new StringBuffer();
            props.append("OK - Server info");
            props.append("\nTomcat Version: ");
            props.append(ServerInfo.getServerInfo());
            props.append("\nOS Name: ");
            props.append(System.getProperty("os.name"));
            props.append("\nOS Version: ");
            props.append(System.getProperty("os.version"));
            props.append("\nOS Architecture: ");
            props.append(System.getProperty("os.arch"));
            props.append("\nJVM Version: ");
            props.append(System.getProperty("java.runtime.version"));
            props.append("\nJVM Vendor: ");
            props.append(System.getProperty("java.vm.vendor"));
            writer.println(props.toString());
        } catch (Throwable t) {
            getServletContext().log("ManagerServlet.serverinfo",t);
            writer.println(sm.getString("managerServlet.exception",
                                        t.toString()));
        }
    }

    /**
     * Session information for the web application at the specified context path.
     * Displays a profile of session MaxInactiveInterval timeouts listing number
     * of sessions for each 10 minute timeout interval up to 10 hours.
     *
     * @param writer Writer to render to
     * @param path Context path of the application to list session information for
     */
    protected void sessions(PrintWriter writer, String path) {

        if (debug >= 1)
            log("sessions: Session information for web application at '" + path + "'");

        if ((path == null) || (!path.startsWith("/") && path.equals(""))) {
            writer.println(sm.getString("managerServlet.invalidPath",
                                        RequestUtil.filter(path)));
            return;
        }
        String displayPath = path;
        if( path.equals("/") )
            path = "";
        try {
            Context context = (Context) host.findChild(path);
            if (context == null) {
                writer.println(sm.getString("managerServlet.noContext",
                                            RequestUtil.filter(displayPath)));
                return;
            }
            writer.println(sm.getString("managerServlet.sessions", displayPath));
            writer.println(sm.getString("managerServlet.sessiondefaultmax",
                                "" + context.getManager().getMaxInactiveInterval()/60));
            Session [] sessions = context.getManager().findSessions();
            int [] timeout = new int[60];
            int notimeout = 0;
            for (int i = 0; i < sessions.length; i++) {
                int time = sessions[i].getMaxInactiveInterval()/(10*60);
                if (time < 0)
                    notimeout++;
                else if (time >= timeout.length)
                    timeout[timeout.length-1]++;
                else
                    timeout[time]++;
            }
            if (timeout[0] > 0)
                writer.println(sm.getString("managerServlet.sessiontimeout",
                                            "<10", "" + timeout[0]));
            for (int i = 1; i < timeout.length-1; i++) {
                if (timeout[i] > 0)
                    writer.println(sm.getString("managerServlet.sessiontimeout",
                                     "" + (i)*10 + " - <" + (i+1)*10,
                                                "" + timeout[i]));
            }
            if (timeout[timeout.length-1] > 0)
                writer.println(sm.getString("managerServlet.sessiontimeout",
                                            ">=" + timeout.length*10,
                                            "" + timeout[timeout.length-1]));
            if (notimeout > 0)
                writer.println(sm.getString("managerServlet.sessiontimeout",
                                            "unlimited","" + notimeout));
        } catch (Throwable t) {
            log("ManagerServlet.sessions[" + displayPath + "]", t);
            writer.println(sm.getString("managerServlet.exception",
                                        t.toString()));
        }

    }


    /**
     * Start the web application at the specified context path.
     *
     * @param writer Writer to render to
     * @param path Context path of the application to be started
     */
    protected void start(PrintWriter writer, String path) {

        if (debug >= 1)
            log("start: Starting web application at '" + path + "'");

        if ((path == null) || (!path.startsWith("/") && path.equals(""))) {
            writer.println(sm.getString("managerServlet.invalidPath",
                                        RequestUtil.filter(path)));
            return;
        }
        String displayPath = path;
        if( path.equals("/") )
            path = "";

        try {
            Context context = (Context) host.findChild(path);
            if (context == null) {
                writer.println(sm.getString("managerServlet.noContext", 
                                            RequestUtil.filter(displayPath)));
                return;
            }
            ((Lifecycle) context).start();
            if (context.getAvailable())
                writer.println
                    (sm.getString("managerServlet.started", displayPath));
            else
                writer.println
                    (sm.getString("managerServlet.startFailed", displayPath));
        } catch (Throwable t) {
            getServletContext().log
                (sm.getString("managerServlet.startFailed", displayPath), t);
            writer.println
                (sm.getString("managerServlet.startFailed", displayPath));
            writer.println(sm.getString("managerServlet.exception",
                                        t.toString()));
        }

    }


    /**
     * Stop the web application at the specified context path.
     *
     * @param writer Writer to render to
     * @param path Context path of the application to be stopped
     */
    protected void stop(PrintWriter writer, String path) {

        if (debug >= 1)
            log("stop: Stopping web application at '" + path + "'");

        if ((path == null) || (!path.startsWith("/") && path.equals(""))) {
            writer.println(sm.getString("managerServlet.invalidPath",
                                        RequestUtil.filter(path)));
            return;
        }
        String displayPath = path;
        if( path.equals("/") )
            path = "";

        try {
            Context context = (Context) host.findChild(path);
            if (context == null) {
                writer.println(sm.getString("managerServlet.noContext", 
                                            RequestUtil.filter(displayPath)));
                return;
            }
            // It isn't possible for the manager to stop itself
            if (context.getPath().equals(this.context.getPath())) {
                writer.println(sm.getString("managerServlet.noSelf"));
                return;
            }
            ((Lifecycle) context).stop();
            writer.println(sm.getString("managerServlet.stopped", displayPath));
        } catch (Throwable t) {
            log("ManagerServlet.stop[" + displayPath + "]", t);
            writer.println(sm.getString("managerServlet.exception",
                                        t.toString()));
        }

    }


    /**
     * Undeploy the web application at the specified context path.
     *
     * @param writer Writer to render to
     * @param path Context path of the application to be removed
     */
    protected void undeploy(PrintWriter writer, String path) {

        if (debug >= 1)
            log("undeploy: Undeploying web application at '" + path + "'");

        if ((path == null) || (!path.startsWith("/") && path.equals(""))) {
            writer.println(sm.getString("managerServlet.invalidPath",
                                        RequestUtil.filter(path)));
            return;
        }
        String displayPath = path;
        if( path.equals("/") )
            path = "";

        try {

            // Validate the Context of the specified application
            Context context = (Context) host.findChild(path);
            if (context == null) {
                writer.println(sm.getString("managerServlet.noContext",
                                            RequestUtil.filter(displayPath)));
                return;
            }

            // Identify the appBase of the owning Host of this Context (if any)
            String appBase = null;
            File appBaseDir = null;
            if (context.getParent() instanceof Host) {
                appBase = ((Host) context.getParent()).getAppBase();
                appBaseDir = new File(appBase);
                if (!appBaseDir.isAbsolute()) {
                    appBaseDir = new File(System.getProperty("catalina.base"),
                                          appBase);
                }
            }

            if (!isServiced(path)) {
                addServiced(path);
                try {
                    // Try to stop the context first to be nicer
                    ((Lifecycle) context).stop();
                } catch (Throwable t) {
                    // Ignore
                }
                try {
                    File war = new File(getAppBase(), getDocBase(path) + ".war");
                    File dir = new File(getAppBase(), getDocBase(path));
                    File xml = new File(configBase, getConfigFile(path) + ".xml");
                    if (war.exists()) {
                        war.delete();
                    } else if (dir.exists()) {
                        undeployDir(dir);
                    } else {
                        xml.delete();
                    }
                    // Perform new deployment
                    check(path);
                } finally {
                    removeServiced(path);
                }
            }
            writer.println(sm.getString("managerServlet.undeployed",
                                        displayPath));
        } catch (Throwable t) {
            log("ManagerServlet.undeploy[" + displayPath + "]", t);
            writer.println(sm.getString("managerServlet.exception",
                                        t.toString()));
        }

    }


    // -------------------------------------------------------- Support Methods


    /**
     * Given a context path, get the config file name.
     */
    protected String getConfigFile(String path) {
        String basename = null;
        if (path.equals("")) {
            basename = "ROOT";
        } else {
            basename = path.substring(1).replace('/', '#');
        }
        return (basename);
    }


    /**
     * Given a context path, get the config file name.
     */
    protected String getDocBase(String path) {
        String basename = null;
        if (path.equals("")) {
            basename = "ROOT";
        } else {
            basename = path.substring(1);
        }
        return (basename);
    }

    
    /**
     * Return a File object representing the "application root" directory
     * for our associated Host.
     */
    protected File getAppBase() {

        if (appBase != null) {
            return appBase;
        }

        File file = new File(host.getAppBase());
        if (!file.isAbsolute())
            file = new File(System.getProperty("catalina.base"),
                            host.getAppBase());
        try {
            appBase = file.getCanonicalFile();
        } catch (IOException e) {
            appBase = file;
        }
        return (appBase);

    }


    /**
     * Invoke the check method on the deployer.
     */
    protected void check(String name) 
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "check", params, signature);
    }
    

    /**
     * Invoke the check method on the deployer.
     */
    protected boolean isServiced(String name) 
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        Boolean result = 
            (Boolean) mBeanServer.invoke(oname, "isServiced", params, signature);
        return result.booleanValue();
    }
    

    /**
     * Invoke the check method on the deployer.
     */
    protected void addServiced(String name) 
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "addServiced", params, signature);
    }
    

    /**
     * Invoke the check method on the deployer.
     */
    protected void removeServiced(String name) 
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "removeServiced", params, signature);
    }
    

    /**
     * Delete the specified directory, including all of its contents and
     * subdirectories recursively.
     *
     * @param dir File object representing the directory to be deleted
     */
    protected void undeployDir(File dir) {

        String files[] = dir.list();
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; i < files.length; i++) {
            File file = new File(dir, files[i]);
            if (file.isDirectory()) {
                undeployDir(file);
            } else {
                file.delete();
            }
        }
        dir.delete();

    }


    /**
     * Upload the WAR file included in this request, and store it at the
     * specified file location.
     *
     * @param request The servlet request we are processing
     * @param war The file into which we should store the uploaded WAR
     *
     * @exception IOException if an I/O error occurs during processing
     */
    protected void uploadWar(HttpServletRequest request, File war)
        throws IOException {

        war.delete();
        ServletInputStream istream = null;
        BufferedOutputStream ostream = null;
        try {
            istream = request.getInputStream();
            ostream =
                new BufferedOutputStream(new FileOutputStream(war), 1024);
            byte buffer[] = new byte[1024];
            while (true) {
                int n = istream.read(buffer);
                if (n < 0) {
                    break;
                }
                ostream.write(buffer, 0, n);
            }
            ostream.flush();
            ostream.close();
            ostream = null;
            istream.close();
            istream = null;
        } catch (IOException e) {
            war.delete();
            throw e;
        } finally {
            if (ostream != null) {
                try {
                    ostream.close();
                } catch (Throwable t) {
                    ;
                }
                ostream = null;
            }
            if (istream != null) {
                try {
                    istream.close();
                } catch (Throwable t) {
                    ;
                }
                istream = null;
            }
        }

    }


    /**
     * Copy the specified file or directory to the destination.
     *
     * @param src File object representing the source
     * @param dest File object representing the destination
     */
    public static boolean copy(File src, File dest) {
        boolean result = false;
        try {
            if( src != null &&
                    !src.getCanonicalPath().equals(dest.getCanonicalPath()) ) {
                result = copyInternal(src, dest, new byte[4096]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    
    /**
     * Copy the specified file or directory to the destination.
     *
     * @param src File object representing the source
     * @param dest File object representing the destination
     */
    public static boolean copyInternal(File src, File dest, byte[] buf) {
        
        boolean result = true;
        
        String files[] = null;
        if (src.isDirectory()) {
            files = src.list();
            result = dest.mkdir();
        } else {
            files = new String[1];
            files[0] = "";
        }
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; (i < files.length) && result; i++) {
            File fileSrc = new File(src, files[i]);
            File fileDest = new File(dest, files[i]);
            if (fileSrc.isDirectory()) {
                result = copyInternal(fileSrc, fileDest, buf);
            } else {
                FileInputStream is = null;
                FileOutputStream os = null;
                try {
                    is = new FileInputStream(fileSrc);
                    os = new FileOutputStream(fileDest);
                    int len = 0;
                    while (true) {
                        len = is.read(buf);
                        if (len == -1)
                            break;
                        os.write(buf, 0, len);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    result = false;
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                        }
                    }
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        }
        return result;
        
    }
    
    
}
