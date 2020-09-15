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
package org.apache.catalina.manager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.naming.Binding;
import javax.naming.NamingEnumeration;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Manager;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Session;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.ExpandWar;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.IOTools;
import org.apache.catalina.util.ServerInfo;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.tomcat.util.Diagnostics;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.SSLContext;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;


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
 * <li><b>/deploy?config={config-url}&amp;war={war-url}/</b> - Install and start
 *     a new web application, based on the contents of the context
 *     configuration file found at <code>{config-url}</code>, overriding the
 *     <code>docBase</code> attribute with the contents of the web
 *     application archive found at <code>{war-url}</code>.</li>
 * <li><b>/deploy?path=/xxx&amp;war={war-url}</b> - Install and start a new
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
 * <li><b>/serverinfo</b> - Display system OS and JVM properties.
 * <li><b>/sessions</b> - Deprecated. Use expire.
 * <li><b>/expire?path=/xxx</b> - List session idle time information about the
 *     web application attached to context path <code>/xxx</code> for this
 *     virtual host.</li>
 * <li><b>/expire?path=/xxx&amp;idle=mm</b> - Expire sessions
 *     for the context path <code>/xxx</code> which were idle for at
 *     least mm minutes.</li>
 * <li><b>/sslConnectorCiphers</b> - Display diagnostic info on SSL/TLS ciphers
 *     that are currently configured for each connector.
 * <li><b>/start?path=/xxx</b> - Start the web application attached to
 *     context path <code>/xxx</code> for this virtual host.</li>
 * <li><b>/stop?path=/xxx</b> - Stop the web application attached to
 *     context path <code>/xxx</code> for this virtual host.</li>
 * <li><b>/threaddump</b> - Write a JVM thread dump.</li>
 * <li><b>/undeploy?path=/xxx</b> - Shutdown and remove the web application
 *     attached to context path <code>/xxx</code> for this virtual host,
 *     and remove the underlying WAR file or document base directory.
 *     (<em>NOTE</em> - This is only allowed if the WAR file or document
 *     base is stored in the <code>appBase</code> directory of this host,
 *     typically as a result of being placed there via the <code>/deploy</code>
 *     command.</li>
 * <li><b>/vminfo</b> - Write some VM info.</li>
 * <li><b>/save</b> - Save the current server configuration to server.xml</li>
 * <li><b>/save?path=/xxx</b> - Save the context configuration for the web
 *     application deployed with path <code>/xxx</code> to an appropriately
 *     named context.xml file in the <code>xmlBase</code> for the associated
 *     Host.</li>
 * </ul>
 * <p>Use <code>path=/</code> for the ROOT context.</p>
 * <p>The syntax of the URL for a web application archive must conform to one
 * of the following patterns to be successfully deployed:</p>
 * <ul>
 * <li><b>file:/absolute/path/to/a/directory</b> - You can specify the absolute
 *     path of a directory that contains the unpacked version of a web
 *     application.  This directory will be attached to the context path you
 *     specify without any changes.</li>
 * </ul>
 * <p>
 * <b>NOTE</b> - Attempting to reload or remove the application containing
 * this servlet itself will not succeed.  Therefore, this servlet should
 * generally be deployed as a separate web application within the virtual host
 * to be managed.
 * <p>
 * The following servlet initialization parameters are recognized:
 * <ul>
 * <li><b>debug</b> - The debugging detail level that controls the amount
 *     of information that is logged by this servlet.  Default is zero.
 * </ul>
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class ManagerServlet extends HttpServlet implements ContainerServlet {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------- Instance Variables


    /**
     * Path where context descriptors should be deployed.
     */
    protected File configBase = null;


    /**
     * The Context container associated with our web application.
     */
    protected transient Context context = null;


    /**
     * The debugging detail level for this servlet.
     */
    protected int debug = 1;


    /**
     * Path used to store revisions of webapps.
     */
    protected File versioned = null;


    /**
     * The associated host.
     */
    protected transient Host host = null;


    /**
     * MBean server.
     */
    protected transient MBeanServer mBeanServer = null;


    /**
     * The associated deployer ObjectName.
     */
    protected ObjectName oname = null;


    /**
     * The global JNDI <code>NamingContext</code> for this server,
     * if available.
     */
    protected transient javax.naming.Context global = null;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * The Wrapper container associated with this servlet.
     */
    protected transient Wrapper wrapper = null;


    // ----------------------------------------------- ContainerServlet Methods


    /**
     * Return the Wrapper with which we are associated.
     */
    @Override
    public Wrapper getWrapper() {
        return this.wrapper;
    }


    /**
     * Set the Wrapper with which we are associated.
     *
     * @param wrapper The new wrapper
     */
    @Override
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
            String name = engine.getName() + ":type=Deployer,host=" +
                    host.getName();
            try {
                oname = new ObjectName(name);
            } catch (Exception e) {
                log(sm.getString("managerServlet.objectNameFail", name), e);
            }
        }

        // Retrieve the MBean server
        mBeanServer = Registry.getRegistry(null, null).getMBeanServer();

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Finalize this servlet.
     */
    @Override
    public void destroy() {

        // No actions necessary

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
    @Override
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws IOException, ServletException {

        StringManager smClient = StringManager.getManager(
                Constants.Package, request.getLocales());

        // Identify the request parameters that we need
        String command = request.getPathInfo();
        if (command == null)
            command = request.getServletPath();

        String path = request.getParameter("path");
        String war = request.getParameter("war");
        String config = request.getParameter("config");
        ContextName cn = null;
        if (path != null) {
            cn = new ContextName(path, request.getParameter("version"));
        } else if (config != null) {
            cn = ContextName.extractFromPath(config);
        } else if (war != null) {
            cn = ContextName.extractFromPath(war);
        }

        String type = request.getParameter("type");
        String tag = request.getParameter("tag");
        boolean update = false;
        if ((request.getParameter("update") != null)
            && (request.getParameter("update").equals("true"))) {
            update = true;
        }
        String tlsHostName = request.getParameter("tlsHostName");

        boolean statusLine = false;
        if ("true".equals(request.getParameter("statusLine"))) {
            statusLine = true;
        }

        // Prepare our output writer to generate the response message
        response.setContentType("text/plain; charset=" + Constants.CHARSET);
        // Stop older versions of IE thinking they know best. We set text/plain
        // in the line above for a reason. IE's behaviour is unwanted at best
        // and dangerous at worst.
        response.setHeader("X-Content-Type-Options", "nosniff");
        PrintWriter writer = response.getWriter();

        // Process the requested command
        if (command == null) {
            writer.println(smClient.getString("managerServlet.noCommand"));
        } else if (command.equals("/deploy")) {
            if (war != null || config != null) {
                deploy(writer, config, cn, war, update, smClient);
            } else if (tag != null) {
                deploy(writer, cn, tag, smClient);
            } else {
                writer.println(smClient.getString(
                        "managerServlet.invalidCommand", command));
            }
        } else if (command.equals("/list")) {
            list(writer, smClient);
        } else if (command.equals("/reload")) {
            reload(writer, cn, smClient);
        } else if (command.equals("/resources")) {
            resources(writer, type, smClient);
        } else if (command.equals("/save")) {
            save(writer, path, smClient);
        } else if (command.equals("/serverinfo")) {
            serverinfo(writer, smClient);
        } else if (command.equals("/sessions")) {
            expireSessions(writer, cn, request, smClient);
        } else if (command.equals("/expire")) {
            expireSessions(writer, cn, request, smClient);
        } else if (command.equals("/start")) {
            start(writer, cn, smClient);
        } else if (command.equals("/stop")) {
            stop(writer, cn, smClient);
        } else if (command.equals("/undeploy")) {
            undeploy(writer, cn, smClient);
        } else if (command.equals("/findleaks")) {
            findleaks(statusLine, writer, smClient);
        } else if (command.equals("/vminfo")) {
            vmInfo(writer, smClient, request.getLocales());
        } else if (command.equals("/threaddump")) {
            threadDump(writer, smClient, request.getLocales());
        } else if (command.equals("/sslConnectorCiphers")) {
            sslConnectorCiphers(writer, smClient);
        } else if (command.equals("/sslConnectorCerts")) {
            sslConnectorCerts(writer, smClient);
        } else if (command.equals("/sslConnectorTrustedCerts")) {
            sslConnectorTrustedCerts(writer, smClient);
        } else if (command.equals("/sslReload")) {
            sslReload(writer, tlsHostName, smClient);
        } else {
            writer.println(smClient.getString("managerServlet.unknownCommand",
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
    @Override
    public void doPut(HttpServletRequest request,
                      HttpServletResponse response)
        throws IOException, ServletException {

        StringManager smClient = StringManager.getManager(
                Constants.Package, request.getLocales());

        // Identify the request parameters that we need
        String command = request.getPathInfo();
        if (command == null)
            command = request.getServletPath();
        String path = request.getParameter("path");
        ContextName cn = null;
        if (path != null) {
            cn = new ContextName(path, request.getParameter("version"));
        }
        String config = request.getParameter("config");
        String tag = request.getParameter("tag");
        boolean update = false;
        if ((request.getParameter("update") != null)
            && (request.getParameter("update").equals("true"))) {
            update = true;
        }

        // Prepare our output writer to generate the response message
        response.setContentType("text/plain;charset="+Constants.CHARSET);
        // Stop older versions of IE thinking they know best. We set text/plain
        // in the line above for a reason. IE's behaviour is unwanted at best
        // and dangerous at worst.
        response.setHeader("X-Content-Type-Options", "nosniff");
        PrintWriter writer = response.getWriter();

        // Process the requested command
        if (command == null) {
            writer.println(smClient.getString("managerServlet.noCommand"));
        } else if (command.equals("/deploy")) {
            deploy(writer, config, cn, tag, update, request, smClient);
        } else {
            writer.println(smClient.getString("managerServlet.unknownCommand",
                    command));
        }

        // Finish up the response
        writer.flush();
        writer.close();

    }


    /**
     * Initialize this servlet.
     */
    @Override
    public void init() throws ServletException {

        // Ensure that our ContainerServlet properties have been set
        if ((wrapper == null) || (context == null))
            throw new UnavailableException(
                    sm.getString("managerServlet.noWrapper"));

        // Set our properties from the initialization parameters
        String value = null;
        try {
            value = getServletConfig().getInitParameter("debug");
            debug = Integer.parseInt(value);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }

        // Acquire global JNDI resources if available
        Server server = ((Engine)host.getParent()).getService().getServer();
        if (server != null) {
            global = server.getGlobalNamingContext();
        }

        // Calculate the directory into which we will be deploying applications
        versioned = (File) getServletContext().getAttribute
            (ServletContext.TEMPDIR);

        configBase = new File(context.getCatalinaBase(), "conf");
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
     * Find potential memory leaks caused by web application reload.
     *
     * @param statusLine Print a status line
     * @param writer The output writer
     * @param smClient StringManager for the client's locale
     */
    protected void findleaks(boolean statusLine, PrintWriter writer,
            StringManager smClient) {

        if (!(host instanceof StandardHost)) {
            writer.println(smClient.getString("managerServlet.findleaksFail"));
            return;
        }

        String[] results =
            ((StandardHost) host).findReloadedContextMemoryLeaks();

        if (results.length > 0) {
            if (statusLine) {
                writer.println(
                        smClient.getString("managerServlet.findleaksList"));
            }
            for (String result : results) {
                if (result.isEmpty()) {
                    result = "/";
                }
                writer.println(result);
            }
        } else if (statusLine) {
            writer.println(smClient.getString("managerServlet.findleaksNone"));
        }
    }


    protected void sslReload(PrintWriter writer, String tlsHostName, StringManager smClient) {
        Connector connectors[] = getConnectors();
        boolean found = false;
        for (Connector connector : connectors) {
            if (Boolean.TRUE.equals(connector.getProperty("SSLEnabled"))) {
                ProtocolHandler protocol = connector.getProtocolHandler();
                if (protocol instanceof AbstractHttp11Protocol<?>) {
                    AbstractHttp11Protocol<?> http11Protoocol = (AbstractHttp11Protocol<?>) protocol;
                    if (tlsHostName == null || tlsHostName.length() == 0) {
                        found = true;
                        http11Protoocol.reloadSslHostConfigs();
                    } else {
                        SSLHostConfig[] sslHostConfigs = http11Protoocol.findSslHostConfigs();
                        for (SSLHostConfig sslHostConfig : sslHostConfigs) {
                            if (sslHostConfig.getHostName().equalsIgnoreCase(tlsHostName)) {
                                found = true;
                                http11Protoocol.reloadSslHostConfig(tlsHostName);
                            }
                        }
                    }
                }
            }
        }
        if (found) {
            if (tlsHostName == null || tlsHostName.length() == 0) {
                writer.println(smClient.getString("managerServlet.sslReloadAll"));
            } else {
                writer.println(smClient.getString("managerServlet.sslReload", tlsHostName));
            }
        } else {
            writer.println(smClient.getString("managerServlet.sslReloadFail"));
        }
    }


    /**
     * Write some VM info.
     *
     * @param writer The output writer
     * @param smClient StringManager for the client's locale
     * @param requestedLocales the client's locales
     */
    protected void vmInfo(PrintWriter writer, StringManager smClient,
            Enumeration<Locale> requestedLocales) {
        writer.println(smClient.getString("managerServlet.vminfo"));
        writer.print(Diagnostics.getVMInfo(requestedLocales));
    }

    /**
     * Write a JVM thread dump.
     *
     * @param writer The output writer
     * @param smClient StringManager for the client's locale
     * @param requestedLocales the client's locales
     */
    protected void threadDump(PrintWriter writer, StringManager smClient,
            Enumeration<Locale> requestedLocales) {
        writer.println(smClient.getString("managerServlet.threaddump"));
        writer.print(Diagnostics.getThreadDump(requestedLocales));
    }


    protected void sslConnectorCiphers(PrintWriter writer, StringManager smClient) {
        writer.println(smClient.getString("managerServlet.sslConnectorCiphers"));
        Map<String,List<String>> connectorCiphers = getConnectorCiphers(smClient);
        for (Map.Entry<String,List<String>> entry : connectorCiphers.entrySet()) {
            writer.println(entry.getKey());
            for (String cipher : entry.getValue()) {
                writer.print("  ");
                writer.println(cipher);
            }
        }
    }


    private void sslConnectorCerts(PrintWriter writer, StringManager smClient) {
        writer.println(smClient.getString("managerServlet.sslConnectorCerts"));
        Map<String,List<String>> connectorCerts = getConnectorCerts(smClient);
        for (Map.Entry<String,List<String>> entry : connectorCerts.entrySet()) {
            writer.println(entry.getKey());
            for (String cert : entry.getValue()) {
                writer.println(cert);
            }
        }
    }


    private void sslConnectorTrustedCerts(PrintWriter writer, StringManager smClient) {
        writer.println(smClient.getString("managerServlet.sslConnectorTrustedCerts"));
        Map<String,List<String>> connectorTrustedCerts = getConnectorTrustedCerts(smClient);
        for (Map.Entry<String,List<String>> entry : connectorTrustedCerts.entrySet()) {
            writer.println(entry.getKey());
            for (String cert : entry.getValue()) {
                writer.println(cert);
            }
        }
    }


    /**
     * Store server configuration.
     *
     * @param writer   Destination for any user message(s) during this operation
     * @param path     Optional context path to save
     * @param smClient i18n support for current client's locale
     */
    protected synchronized void save(PrintWriter writer, String path, StringManager smClient) {

        ObjectName storeConfigOname;
        try {
            // Note: Hard-coded domain used since this object is per Server/JVM
            storeConfigOname = new ObjectName("Catalina:type=StoreConfig");
        } catch (MalformedObjectNameException e) {
            // Should never happen. The name above is valid.
            log(sm.getString("managerServlet.exception"), e);
            writer.println(smClient.getString("managerServlet.exception", e.toString()));
            return;
        }

        if (!mBeanServer.isRegistered(storeConfigOname)) {
            writer.println(smClient.getString(
                    "managerServlet.storeConfig.noMBean", storeConfigOname));
            return;
        }

        if ((path == null) || path.length() == 0 || !path.startsWith("/")) {
            try {
                mBeanServer.invoke(storeConfigOname, "storeConfig", null, null);
                writer.println(smClient.getString("managerServlet.saved"));
            } catch (Exception e) {
                log(sm.getString("managerServlet.error.storeConfig"), e);
                writer.println(smClient.getString("managerServlet.exception",
                        e.toString()));
            }
        } else {
            String contextPath = path;
            if (path.equals("/")) {
                contextPath = "";
            }
            Context context = (Context) host.findChild(contextPath);
            if (context == null) {
                writer.println(smClient.getString("managerServlet.noContext",
                        path));
                return;
            }
            try {
                Boolean result = (Boolean) mBeanServer.invoke(storeConfigOname, "store",
                        new Object[] {context},
                        new String [] { "org.apache.catalina.Context"});
                if (result.booleanValue()) {
                    writer.println(smClient.getString("managerServlet.savedContext", path));
                } else {
                    writer.println(smClient.getString("managerServlet.savedContextFail", path));
                }
            } catch (Exception e) {
                log(sm.getString("managerServlet.error.storeContextConfig", path), e);
                writer.println(smClient.getString("managerServlet.exception",
                        e.toString()));
            }
        }
    }


    /**
     * Deploy a web application archive (included in the current request)
     * at the specified context path.
     *
     * @param writer   Writer to render results to
     * @param config   URL of the context configuration file to be installed
     * @param cn       Name of the application to be installed
     * @param tag      Tag to be associated with the webapp
     * @param update   Flag that indicates that any existing app should be
     *                   replaced
     * @param request  Servlet request we are processing
     * @param smClient i18n messages using the locale of the client
     */
    protected synchronized void deploy
        (PrintWriter writer, String config, ContextName cn,
         String tag, boolean update, HttpServletRequest request,
         StringManager smClient) {

        if (config != null && config.length() == 0) {
            config = null;
        }

        if (debug >= 1) {
            if (config == null) {
                log("deploy: Deploying web application '" + cn + "'");
            } else {
                log("deploy: Deploying web application '" + cn + "' " +
                        "with context configuration at '" + config + "'");
            }
        }

        // Validate the requested context path
        if (!validateContextName(cn, writer, smClient)) {
            return;
        }
        String name = cn.getName();
        String baseName = cn.getBaseName();
        String displayPath = cn.getDisplayName();

        // If app exists deployment can only proceed if update is true
        // Note existing WAR will be deleted and then replaced
        Context context = (Context) host.findChild(name);
        if (context != null && !update) {
            writer.println(smClient.getString("managerServlet.alreadyContext",
                    displayPath));
            return;
        }

        if (config != null && (config.startsWith("file:"))) {
            config = config.substring("file:".length());
        }

        File deployedWar = new File(host.getAppBaseFile(), baseName + ".war");

        // Determine full path for uploaded WAR
        File uploadedWar;
        if (tag == null) {
            if (update) {
                // Append ".tmp" to the file name so it won't get deployed if auto
                // deployment is enabled. It also means the old war won't get
                // deleted if the upload fails
                uploadedWar = new File(deployedWar.getAbsolutePath() + ".tmp");
                if (uploadedWar.exists() && !uploadedWar.delete()) {
                    writer.println(smClient.getString("managerServlet.deleteFail",
                            uploadedWar));
                }
            } else {
                uploadedWar = deployedWar;
            }
        } else {
            File uploadPath = new File(versioned, tag);
            if (!uploadPath.mkdirs() && !uploadPath.isDirectory()) {
                writer.println(smClient.getString("managerServlet.mkdirFail",
                        uploadPath));
                return;
            }
            uploadedWar = new File(uploadPath, baseName + ".war");
        }
        if (debug >= 2) {
            log("Uploading WAR file to " + uploadedWar);
        }

        try {
            if (isServiced(name)) {
                writer.println(smClient.getString("managerServlet.inService", displayPath));
            } else {
                addServiced(name);
                try {
                    if (config != null) {
                        if (!configBase.mkdirs() && !configBase.isDirectory()) {
                            writer.println(smClient.getString(
                                    "managerServlet.mkdirFail",configBase));
                            return;
                        }
                        if (ExpandWar.copy(new File(config),
                                new File(configBase, baseName + ".xml")) == false) {
                            throw new Exception(sm.getString("managerServlet.copyError", config));
                        }
                    }
                    // Upload WAR
                    uploadWar(writer, request, uploadedWar, smClient);
                    if (update && tag == null) {
                        if (deployedWar.exists() && !deployedWar.delete()) {
                            writer.println(smClient.getString("managerServlet.deleteFail",
                                    deployedWar));
                            return;
                        }
                        // Rename uploaded WAR file
                        if (!uploadedWar.renameTo(deployedWar)) {
                            writer.println(smClient.getString("managerServlet.renameFail",
                                    uploadedWar, deployedWar));
                            return;
                        }
                    }
                    if (tag != null) {
                        // Copy WAR to the host's appBase
                        ExpandWar.copy(uploadedWar, deployedWar);
                    }
                    // Perform new deployment
                    check(name);
                } finally {
                    removeServiced(name);
                }
            }
        } catch (Exception e) {
            log(sm.getString("managerServlet.error.deploy", displayPath), e);
            writer.println(smClient.getString("managerServlet.exception",
                    e.toString()));
            return;
        }

        writeDeployResult(writer, smClient, name, displayPath);
    }


    /**
     * Install an application for the specified path from the specified
     * web application archive.
     *
     * @param writer    Writer to render results to
     * @param tag       Revision tag to deploy from
     * @param cn        Name of the application to be installed
     * @param smClient  i18n messages using the locale of the client
     */
    protected void deploy(PrintWriter writer, ContextName cn, String tag,
            StringManager smClient) {

        // NOTE: It is assumed that update is always true in this method.

        // Validate the requested context path
        if (!validateContextName(cn, writer, smClient)) {
            return;
        }

        String baseName = cn.getBaseName();
        String name = cn.getName();
        String displayPath = cn.getDisplayName();

        // Find the local WAR file
        File localWar = new File(new File(versioned, tag), baseName + ".war");

        File deployedWar = new File(host.getAppBaseFile(), baseName + ".war");

        // Copy WAR to appBase
        try {
            if (isServiced(name)) {
                writer.println(smClient.getString("managerServlet.inService", displayPath));
            } else {
                addServiced(name);
                try {
                    if (!deployedWar.delete()) {
                        writer.println(smClient.getString("managerServlet.deleteFail",
                                deployedWar));
                        return;
                    }
                    ExpandWar.copy(localWar, deployedWar);
                    // Perform new deployment
                    check(name);
                } finally {
                    removeServiced(name);
                }
            }
        } catch (Exception e) {
            log(sm.getString("managerServlet.error.deploy", displayPath), e);
            writer.println(smClient.getString("managerServlet.exception",
                    e.toString()));
            return;
        }

        writeDeployResult(writer, smClient, name, displayPath);
    }


    /**
     * Install an application for the specified path from the specified
     * web application archive.
     *
     * @param writer    Writer to render results to
     * @param config    URL of the context configuration file to be installed
     * @param cn        Name of the application to be installed
     * @param war       URL of the web application archive to be installed
     * @param update    true to override any existing webapp on the path
     * @param smClient  i18n messages using the locale of the client
     */
    protected void deploy(PrintWriter writer, String config, ContextName cn,
            String war, boolean update, StringManager smClient) {

        if (config != null && config.length() == 0) {
            config = null;
        }
        if (war != null && war.length() == 0) {
            war = null;
        }

        if (debug >= 1) {
            if (config != null) {
                if (war != null) {
                    log("install: Installing context configuration at '" +
                            config + "' from '" + war + "'");
                } else {
                    log("install: Installing context configuration at '" +
                            config + "'");
                }
            } else {
                if (cn != null) {
                    log("install: Installing web application '" + cn +
                            "' from '" + war + "'");
                } else {
                    log("install: Installing web application from '" + war + "'");
                }
            }
        }

        if (!validateContextName(cn, writer, smClient)) {
            return;
        }
        @SuppressWarnings("null") // checked in call above
        String name = cn.getName();
        String baseName = cn.getBaseName();
        String displayPath = cn.getDisplayName();

        // If app exists deployment can only proceed if update is true
        // Note existing files will be deleted and then replaced
        Context context = (Context) host.findChild(name);
        if (context != null && !update) {
            writer.println(smClient.getString("managerServlet.alreadyContext",
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
            if (isServiced(name)) {
                writer.println(smClient.getString("managerServlet.inService", displayPath));
            } else {
                addServiced(name);
                try {
                    if (config != null) {
                        if (!configBase.mkdirs() && !configBase.isDirectory()) {
                            writer.println(smClient.getString(
                                    "managerServlet.mkdirFail",configBase));
                            return;
                        }
                        File localConfig = new File(configBase, baseName + ".xml");
                        if (localConfig.isFile() && !localConfig.delete()) {
                            writer.println(smClient.getString(
                                    "managerServlet.deleteFail", localConfig));
                            return;
                        }
                        ExpandWar.copy(new File(config), localConfig);
                    }
                    if (war != null) {
                        File localWar;
                        if (war.endsWith(".war")) {
                            localWar = new File(host.getAppBaseFile(), baseName + ".war");
                        } else {
                            localWar = new File(host.getAppBaseFile(), baseName);
                        }
                        if (localWar.exists() && !ExpandWar.delete(localWar)) {
                            writer.println(smClient.getString(
                                    "managerServlet.deleteFail", localWar));
                            return;
                        }
                        ExpandWar.copy(new File(war), localWar);
                    }
                    // Perform new deployment
                    check(name);
                } finally {
                    removeServiced(name);
                }
            }
            writeDeployResult(writer, smClient, name, displayPath);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log(sm.getString("managerServlet.error.deploy", displayPath), t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }

    }


    private void writeDeployResult(PrintWriter writer, StringManager smClient,
            String name, String displayPath) {
        Context deployed = (Context) host.findChild(name);
        if (deployed != null && deployed.getConfigured() &&
                deployed.getState().isAvailable()) {
            writer.println(smClient.getString(
                    "managerServlet.deployed", displayPath));
        } else if (deployed!=null && !deployed.getState().isAvailable()) {
            writer.println(smClient.getString(
                    "managerServlet.deployedButNotStarted", displayPath));
        } else {
            // Something failed
            writer.println(smClient.getString(
                    "managerServlet.deployFailed", displayPath));
        }
    }


    /**
     * Render a list of the currently active Contexts in our virtual host.
     *
     * @param writer Writer to render to
     * @param smClient i18n support for current client's locale
     */
    protected void list(PrintWriter writer, StringManager smClient) {

        if (debug >= 1)
            log("list: Listing contexts for virtual host '" +
                host.getName() + "'");

        writer.println(smClient.getString("managerServlet.listed",
                                    host.getName()));
        Container[] contexts = host.findChildren();
        for (Container container : contexts) {
            Context context = (Context) container;
            if (context != null) {
                String displayPath = context.getPath();
                if (displayPath.equals(""))
                    displayPath = "/";
                List<String> parts = null;
                if (context.getState().isAvailable()) {
                    parts = Arrays.asList(
                            displayPath,
                            "running",
                            "" + context.getManager().findSessions().length,
                            context.getDocBase());
                } else {
                    parts = Arrays.asList(
                            displayPath,
                            "stopped",
                            "0",
                            context.getDocBase());
                }
                writer.println(StringUtils.join(parts, ':'));
            }
        }
    }


    /**
     * Reload the web application at the specified context path.
     *
     * @param writer Writer to render to
     * @param cn Name of the application to be restarted
     * @param smClient i18n support for current client's locale
     */
    protected void reload(PrintWriter writer, ContextName cn,
            StringManager smClient) {

        if (debug >= 1)
            log("restart: Reloading web application '" + cn + "'");

        if (!validateContextName(cn, writer, smClient)) {
            return;
        }

        try {
            Context context = (Context) host.findChild(cn.getName());
            if (context == null) {
                writer.println(smClient.getString("managerServlet.noContext",
                        Escape.htmlElementContent(cn.getDisplayName())));
                return;
            }
            // It isn't possible for the manager to reload itself
            if (context.getName().equals(this.context.getName())) {
                writer.println(smClient.getString("managerServlet.noSelf"));
                return;
            }
            context.reload();
            writer.println(smClient.getString("managerServlet.reloaded",
                    cn.getDisplayName()));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log(sm.getString("managerServlet.error.reload", cn.getDisplayName()), t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }

    }


    /**
     * Render a list of available global JNDI resources.
     *
     * @param writer Writer to render to
     * @param type Fully qualified class name of the resource type of interest,
     *  or <code>null</code> to list resources of all types
     * @param smClient i18n support for current client's locale
     */
    protected void resources(PrintWriter writer, String type,
            StringManager smClient) {

        if (debug >= 1) {
            if (type != null) {
                log("resources:  Listing resources of type " + type);
            } else {
                log("resources:  Listing resources of all types");
            }
        }

        // Is the global JNDI resources context available?
        if (global == null) {
            writer.println(smClient.getString("managerServlet.noGlobal"));
            return;
        }

        // Enumerate the global JNDI resources of the requested type
        if (type != null) {
            writer.println(smClient.getString("managerServlet.resourcesType",
                    type));
        } else {
            writer.println(smClient.getString("managerServlet.resourcesAll"));
        }

        printResources(writer, "", global, type, smClient);

    }


    /**
     * List the resources of the given context.
     * @param writer Writer to render to
     * @param prefix Path for recursion
     * @param namingContext The naming context for lookups
     * @param type Fully qualified class name of the resource type of interest,
     *  or <code>null</code> to list resources of all types
     * @param smClient i18n support for current client's locale
     */
    protected void printResources(PrintWriter writer, String prefix,
                                  javax.naming.Context namingContext,
                                  String type,
                                  StringManager smClient) {
        try {
            NamingEnumeration<Binding> items = namingContext.listBindings("");
            while (items.hasMore()) {
                Binding item = items.next();
                Object obj = item.getObject();
                if (obj instanceof javax.naming.Context) {
                    printResources(writer, prefix + item.getName() + "/",
                            (javax.naming.Context) obj, type, smClient);
                } else {
                    if (type != null && (obj == null ||
                            !IntrospectionUtils.isInstance(obj.getClass(), type))) {
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
            ExceptionUtils.handleThrowable(t);
            log(sm.getString("managerServlet.error.resources", type), t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }
    }


    /**
     * Writes System OS and JVM properties.
     * @param writer Writer to render to
     * @param smClient i18n support for current client's locale
    */
    protected void serverinfo(PrintWriter writer,  StringManager smClient) {
        if (debug >= 1)
            log("serverinfo");
        try {
            writer.println(smClient.getString("managerServlet.serverInfo", ServerInfo.getServerInfo(),
                    System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                    System.getProperty("java.runtime.version"), System.getProperty("java.vm.vendor")));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log(sm.getString("managerServlet.error.serverInfo"), t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }
    }

    /**
     * Session information for the web application at the specified context path.
     * Displays a profile of session thisAccessedTime listing number
     * of sessions for each 10 minute interval up to 10 hours.
     *
     * @param writer Writer to render to
     * @param cn Name of the application to list session information for
     * @param idle Expire all sessions with idle time &gt; idle for this context
     * @param smClient i18n support for current client's locale
     */
    protected void sessions(PrintWriter writer, ContextName cn, int idle,
            StringManager smClient) {

        if (debug >= 1) {
            log("sessions: Session information for web application '" + cn + "'");
            if (idle >= 0)
                log("sessions: Session expiration for " + idle + " minutes '" + cn + "'");
        }

        if (!validateContextName(cn, writer, smClient)) {
            return;
        }

        String displayPath = cn.getDisplayName();

        try {
            Context context = (Context) host.findChild(cn.getName());
            if (context == null) {
                writer.println(smClient.getString("managerServlet.noContext",
                        Escape.htmlElementContent(displayPath)));
                return;
            }
            Manager manager = context.getManager() ;
            if(manager == null) {
                writer.println(smClient.getString("managerServlet.noManager",
                        Escape.htmlElementContent(displayPath)));
                return;
            }
            int maxCount = 60;
            int histoInterval = 1;
            int maxInactiveInterval = context.getSessionTimeout();
            if (maxInactiveInterval > 0) {
                histoInterval = maxInactiveInterval / maxCount;
                if (histoInterval * maxCount < maxInactiveInterval)
                    histoInterval++;
                if (0 == histoInterval)
                    histoInterval = 1;
                maxCount = maxInactiveInterval / histoInterval;
                if (histoInterval * maxCount < maxInactiveInterval)
                    maxCount++;
            }

            writer.println(smClient.getString("managerServlet.sessions",
                    displayPath));
            writer.println(smClient.getString(
                    "managerServlet.sessiondefaultmax",
                    "" + maxInactiveInterval));
            Session [] sessions = manager.findSessions();
            int[] timeout = new int[maxCount + 1];
            int notimeout = 0;
            int expired = 0;
            for (Session session : sessions) {
                int time = (int) (session.getIdleTimeInternal() / 1000L);
                if (idle >= 0 && time >= idle * 60) {
                    session.expire();
                    expired++;
                }
                time = time / 60 / histoInterval;
                if (time < 0)
                    notimeout++;
                else if (time >= maxCount)
                    timeout[maxCount]++;
                else
                    timeout[time]++;
            }
            if (timeout[0] > 0)
                writer.println(smClient.getString(
                        "managerServlet.sessiontimeout",
                        "<" + histoInterval, "" + timeout[0]));
            for (int i = 1; i < maxCount; i++) {
                if (timeout[i] > 0)
                    writer.println(smClient.getString(
                            "managerServlet.sessiontimeout",
                            "" + (i)*histoInterval + " - <" + (i+1)*histoInterval,
                            "" + timeout[i]));
            }
            if (timeout[maxCount] > 0) {
                writer.println(smClient.getString(
                        "managerServlet.sessiontimeout",
                        ">=" + maxCount*histoInterval,
                        "" + timeout[maxCount]));
            }
            if (notimeout > 0)
                writer.println(smClient.getString(
                        "managerServlet.sessiontimeout.unlimited",
                        "" + notimeout));
            if (idle >= 0)
                writer.println(smClient.getString(
                        "managerServlet.sessiontimeout.expired",
                        ">" + idle,"" + expired));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log(sm.getString("managerServlet.error.sessions", displayPath), t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }

    }


    /**
     * Extract the expiration request parameter
     *
     * @param writer Writer to render to
     * @param cn Name of the application to list session information for
     * @param req The Servlet request
     * @param smClient i18n support for current client's locale
     */
    protected void expireSessions(PrintWriter writer, ContextName cn,
            HttpServletRequest req, StringManager smClient) {
        int idle = -1;
        String idleParam = req.getParameter("idle");
        if (idleParam != null) {
            try {
                idle = Integer.parseInt(idleParam);
            } catch (NumberFormatException e) {
                log(sm.getString("managerServlet.error.idleParam", idleParam));
            }
        }
        sessions(writer, cn, idle, smClient);
    }

    /**
     * Start the web application at the specified context path.
     *
     * @param writer Writer to render to
     * @param cn Name of the application to be started
     * @param smClient i18n support for current client's locale
     */
    protected void start(PrintWriter writer, ContextName cn,
            StringManager smClient) {

        if (debug >= 1)
            log("start: Starting web application '" + cn + "'");

        if (!validateContextName(cn, writer, smClient)) {
            return;
        }

        String displayPath = cn.getDisplayName();

        try {
            Context context = (Context) host.findChild(cn.getName());
            if (context == null) {
                writer.println(smClient.getString("managerServlet.noContext",
                        Escape.htmlElementContent(displayPath)));
                return;
            }
            context.start();
            if (context.getState().isAvailable())
                writer.println(smClient.getString("managerServlet.started",
                        displayPath));
            else
                writer.println(smClient.getString("managerServlet.startFailed",
                        displayPath));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log(sm.getString("managerServlet.error.start", displayPath), t);
            writer.println(smClient.getString("managerServlet.startFailed",
                    displayPath));
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }

    }


    /**
     * Stop the web application at the specified context path.
     *
     * @param writer Writer to render to
     * @param cn Name of the application to be stopped
     * @param smClient i18n support for current client's locale
     */
    protected void stop(PrintWriter writer, ContextName cn,
            StringManager smClient) {

        if (debug >= 1)
            log("stop: Stopping web application '" + cn + "'");

        if (!validateContextName(cn, writer, smClient)) {
            return;
        }

        String displayPath = cn.getDisplayName();

        try {
            Context context = (Context) host.findChild(cn.getName());
            if (context == null) {
                writer.println(smClient.getString("managerServlet.noContext",
                        Escape.htmlElementContent(displayPath)));
                return;
            }
            // It isn't possible for the manager to stop itself
            if (context.getName().equals(this.context.getName())) {
                writer.println(smClient.getString("managerServlet.noSelf"));
                return;
            }
            context.stop();
            writer.println(smClient.getString(
                    "managerServlet.stopped", displayPath));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log(sm.getString("managerServlet.error.stop", displayPath), t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }

    }


    /**
     * Undeploy the web application at the specified context path.
     *
     * @param writer Writer to render to
     * @param cn Name of the application to be removed
     * @param smClient i18n support for current client's locale
     */
    protected void undeploy(PrintWriter writer, ContextName cn,
            StringManager smClient) {

        if (debug >= 1)
            log("undeploy: Undeploying web application at '" + cn + "'");

        if (!validateContextName(cn, writer, smClient)) {
            return;
        }

        String name = cn.getName();
        String baseName = cn.getBaseName();
        String displayPath = cn.getDisplayName();

        try {

            // Validate the Context of the specified application
            Context context = (Context) host.findChild(name);
            if (context == null) {
                writer.println(smClient.getString("managerServlet.noContext",
                        Escape.htmlElementContent(displayPath)));
                return;
            }

            if (!isDeployed(name)) {
                writer.println(smClient.getString("managerServlet.notDeployed",
                        Escape.htmlElementContent(displayPath)));
                return;
            }

            if (isServiced(name)) {
                writer.println(smClient.getString("managerServlet.inService", displayPath));
            } else {
                addServiced(name);
                try {
                    // Try to stop the context first to be nicer
                    context.stop();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                }
                try {
                    File war = new File(host.getAppBaseFile(), baseName + ".war");
                    File dir = new File(host.getAppBaseFile(), baseName);
                    File xml = new File(configBase, baseName + ".xml");
                    if (war.exists() && !war.delete()) {
                        writer.println(smClient.getString(
                                "managerServlet.deleteFail", war));
                        return;
                    } else if (dir.exists() && !ExpandWar.delete(dir, false)) {
                        writer.println(smClient.getString(
                                "managerServlet.deleteFail", dir));
                        return;
                    } else if (xml.exists() && !xml.delete()) {
                        writer.println(smClient.getString(
                                "managerServlet.deleteFail", xml));
                        return;
                    }
                    // Perform new deployment
                    check(name);
                } finally {
                    removeServiced(name);
                }
            }
            writer.println(smClient.getString("managerServlet.undeployed",
                    displayPath));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log(sm.getString("managerServlet.error.undeploy", displayPath), t);
            writer.println(smClient.getString("managerServlet.exception",
                    t.toString()));
        }

    }


    // -------------------------------------------------------- Support Methods


    /**
     * Invoke the isDeployed method on the deployer.
     *
     * @param name The webapp name
     * @return <code>true</code> if a webapp with that name is deployed
     * @throws Exception Propagate JMX invocation error
     */
    protected boolean isDeployed(String name)
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        Boolean result =
            (Boolean) mBeanServer.invoke(oname, "isDeployed", params, signature);
        return result.booleanValue();
    }


    /**
     * Invoke the check method on the deployer.
     *
     * @param name The webapp name
     * @throws Exception Propagate JMX invocation error
     */
    protected void check(String name)
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "check", params, signature);
    }


    /**
     * Invoke the isServiced method on the deployer.
     *
     * @param name The webapp name
     * @return <code>true</code> if a webapp with that name is being serviced
     * @throws Exception Propagate JMX invocation error
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
     * Invoke the addServiced method on the deployer.
     *
     * @param name The webapp name
     * @throws Exception Propagate JMX invocation error
     */
    protected void addServiced(String name)
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "addServiced", params, signature);
    }


    /**
     * Invoke the removeServiced method on the deployer.
     *
     * @param name The webapp name
     * @throws Exception Propagate JMX invocation error
     */
    protected void removeServiced(String name)
        throws Exception {
        String[] params = { name };
        String[] signature = { "java.lang.String" };
        mBeanServer.invoke(oname, "removeServiced", params, signature);
    }


    /**
     * Upload the WAR file included in this request, and store it at the
     * specified file location.
     *
     * @param writer    Writer to render to
     * @param request   The servlet request we are processing
     * @param war       The file into which we should store the uploaded WAR
     * @param smClient  The StringManager used to construct i18n messages based
     *                  on the Locale of the client
     *
     * @exception IOException if an I/O error occurs during processing
     */
    protected void uploadWar(PrintWriter writer, HttpServletRequest request,
            File war, StringManager smClient) throws IOException {

        if (war.exists() && !war.delete()) {
            String msg = smClient.getString("managerServlet.deleteFail", war);
            throw new IOException(msg);
        }

        try (ServletInputStream istream = request.getInputStream();
                OutputStream ostream = new FileOutputStream(war)) {
            IOTools.flow(istream, ostream);
        } catch (IOException e) {
            if (war.exists() && !war.delete()) {
                writer.println(
                        smClient.getString("managerServlet.deleteFail", war));
            }
            throw e;
        }
    }


    protected static boolean validateContextName(ContextName cn,
            PrintWriter writer, StringManager smClient) {

        // ContextName should be non-null with a path that is empty or starts
        // with /
        if (cn != null &&
                (cn.getPath().startsWith("/") || cn.getPath().equals(""))) {
            return true;
        }

        String path = null;
        if (cn != null) {
            path = Escape.htmlElementContent(cn.getPath());
        }
        writer.println(smClient.getString("managerServlet.invalidPath", path));
        return false;
    }

    protected Map<String,List<String>> getConnectorCiphers(StringManager smClient) {
        Map<String,List<String>> result = new HashMap<>();

        Connector connectors[] = getConnectors();
        for (Connector connector : connectors) {
            if (Boolean.TRUE.equals(connector.getProperty("SSLEnabled"))) {
                SSLHostConfig[] sslHostConfigs = connector.getProtocolHandler().findSslHostConfigs();
                for (SSLHostConfig sslHostConfig : sslHostConfigs) {
                    String name = connector.toString() + "-" + sslHostConfig.getHostName();
                    /* Add cipher list, keep order but remove duplicates */
                    result.put(name, new ArrayList<>(new LinkedHashSet<>(
                        Arrays.asList(sslHostConfig.getEnabledCiphers()))));
                }
            } else {
                ArrayList<String> cipherList = new ArrayList<>(1);
                cipherList.add(smClient.getString("managerServlet.notSslConnector"));
                result.put(connector.toString(), cipherList);
            }
        }
        return result;
    }


    protected Map<String,List<String>> getConnectorCerts(StringManager smClient) {
        Map<String,List<String>> result = new HashMap<>();

        Connector connectors[] = getConnectors();
        for (Connector connector : connectors) {
            if (Boolean.TRUE.equals(connector.getProperty("SSLEnabled"))) {
                SSLHostConfig[] sslHostConfigs = connector.getProtocolHandler().findSslHostConfigs();
                for (SSLHostConfig sslHostConfig : sslHostConfigs) {
                    if (sslHostConfig.getOpenSslContext().longValue() == 0) {
                        // Not set. Must be JSSE based.
                        Set<SSLHostConfigCertificate> sslHostConfigCerts =
                                sslHostConfig.getCertificates();
                        for (SSLHostConfigCertificate sslHostConfigCert : sslHostConfigCerts) {
                            String name = connector.toString() + "-" + sslHostConfig.getHostName() +
                                    "-" + sslHostConfigCert.getType();
                            List<String> certList = new ArrayList<>();
                            SSLContext sslContext = sslHostConfigCert.getSslContext();
                            String alias = sslHostConfigCert.getCertificateKeyAlias();
                            if (alias == null) {
                                alias = "tomcat";
                            }
                            X509Certificate[] certs = sslContext.getCertificateChain(alias);
                            if (certs == null) {
                                certList.add(smClient.getString("managerServlet.certsNotAvailable"));
                            } else {
                                for (Certificate cert : certs) {
                                    certList.add(cert.toString());
                                }
                            }
                            result.put(name, certList);
                        }
                    } else {
                        List<String> certList = new ArrayList<>();
                        certList.add(smClient.getString("managerServlet.certsNotAvailable"));
                        String name = connector.toString() + "-" + sslHostConfig.getHostName();
                        result.put(name, certList);
                    }
                }
            } else {
                List<String> certList = new ArrayList<>(1);
                certList.add(smClient.getString("managerServlet.notSslConnector"));
                result.put(connector.toString(), certList);
            }
        }

        return result;
    }


    protected Map<String,List<String>> getConnectorTrustedCerts(StringManager smClient) {
        Map<String,List<String>> result = new HashMap<>();

        Connector connectors[] = getConnectors();
        for (Connector connector : connectors) {
            if (Boolean.TRUE.equals(connector.getProperty("SSLEnabled"))) {
                SSLHostConfig[] sslHostConfigs = connector.getProtocolHandler().findSslHostConfigs();
                for (SSLHostConfig sslHostConfig : sslHostConfigs) {
                    String name = connector.toString() + "-" + sslHostConfig.getHostName();
                    List<String> certList = new ArrayList<>();
                    if (sslHostConfig.getOpenSslContext().longValue() == 0) {
                        // Not set. Must be JSSE based.
                        SSLContext sslContext =
                                sslHostConfig.getCertificates().iterator().next().getSslContext();
                        X509Certificate[] certs = sslContext.getAcceptedIssuers();
                        if (certs == null) {
                            certList.add(smClient.getString("managerServlet.certsNotAvailable"));
                        } else if (certs.length == 0) {
                            certList.add(smClient.getString("managerServlet.trustedCertsNotConfigured"));
                        } else {
                            for (Certificate cert : certs) {
                                certList.add(cert.toString());
                            }
                        }
                    } else {
                        certList.add(smClient.getString("managerServlet.certsNotAvailable"));
                    }
                    result.put(name, certList);
                }
            } else {
                List<String> certList = new ArrayList<>(1);
                certList.add(smClient.getString("managerServlet.notSslConnector"));
                result.put(connector.toString(), certList);
            }
        }

        return result;
    }


    private Connector[] getConnectors() {
        Engine e = (Engine) host.getParent();
        Service s = e.getService();
        return s.findConnectors();
    }
}
