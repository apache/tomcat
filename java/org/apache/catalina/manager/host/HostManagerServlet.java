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
package org.apache.catalina.manager.host;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringTokenizer;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.HostConfig;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * Servlet that enables remote management of the virtual hosts installed on the server. Normally, this functionality
 * will be protected by a security constraint in the web application deployment descriptor. However, this requirement
 * can be relaxed during testing.
 * <p>
 * This servlet examines the value returned by <code>getPathInfo()</code> and related query parameters to determine what
 * action is being requested. The following actions and parameters (starting after the servlet path) are supported:
 * <ul>
 * <li><b>/add?name={host-name}&amp;aliases={host-aliases}&amp;manager={manager}</b> - Create and add a new virtual
 * host. The <code>host-name</code> attribute indicates the name of the new host. The <code>host-aliases</code>
 * attribute is a comma separated list of the host alias names. The <code>manager</code> attribute is a boolean value
 * indicating if the webapp manager will be installed in the newly created host (optional, false by default).</li>
 * <li><b>/remove?name={host-name}</b> - Remove a virtual host. The <code>host-name</code> attribute indicates the name
 * of the host.</li>
 * <li><b>/list</b> - List the virtual hosts installed on the server. Each host will be listed with the following format
 * <code>host-name#host-aliases</code>.</li>
 * <li><b>/start?name={host-name}</b> - Start the virtual host.</li>
 * <li><b>/stop?name={host-name}</b> - Stop the virtual host.</li>
 * </ul>
 * <p>
 * <b>NOTE</b> - Attempting to stop or remove the host containing this servlet itself will not succeed. Therefore, this
 * servlet should generally be deployed in a separate virtual host.
 * <p>
 * The following servlet initialization parameters are recognized:
 * <ul>
 * <li><b>debug</b> - The debugging detail level that controls the amount of information that is logged by this servlet.
 * Default is zero.
 * </ul>
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class HostManagerServlet extends HttpServlet implements ContainerServlet {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------- Instance Variables


    /**
     * The Context container associated with our web application.
     */
    protected transient Context context = null;


    /**
     * The debugging detail level for this servlet.
     */
    protected int debug = 1;


    /**
     * The associated host.
     */
    protected transient Host installedHost = null;


    /**
     * The associated engine.
     */
    protected transient Engine engine = null;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * The Wrapper container associated with this servlet.
     */
    protected transient Wrapper wrapper = null;


    // ----------------------------------------------- ContainerServlet Methods


    @Override
    public Wrapper getWrapper() {
        return this.wrapper;
    }


    @Override
    public void setWrapper(Wrapper wrapper) {

        this.wrapper = wrapper;
        if (wrapper == null) {
            context = null;
            installedHost = null;
            engine = null;
        } else {
            context = (Context) wrapper.getParent();
            installedHost = (Host) context.getParent();
            engine = (Engine) installedHost.getParent();
        }
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public void destroy() {

        // No actions necessary

    }


    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        StringManager smClient = StringManager.getManager(Constants.Package, request.getLocales());

        // Identify the request parameters that we need
        String command = request.getPathInfo();
        if (command == null) {
            command = request.getServletPath();
        }
        String name = request.getParameter("name");

        // Prepare our output writer to generate the response message
        response.setContentType("text/plain; charset=" + Constants.CHARSET);
        // Stop older versions of IE thinking they know best. We set text/plain
        // in the line above for a reason. IE's behaviour is unwanted at best
        // and dangerous at worst.
        response.setHeader("X-Content-Type-Options", "nosniff");
        PrintWriter writer = response.getWriter();

        // Process the requested command
        if (command == null) {
            writer.println(smClient.getString("hostManagerServlet.noCommand"));
        } else if (command.equals("/add")) {
            add(request, writer, name, false, smClient);
        } else if (command.equals("/remove")) {
            remove(writer, name, smClient);
        } else if (command.equals("/list")) {
            list(writer, smClient);
        } else if (command.equals("/start")) {
            start(writer, name, smClient);
        } else if (command.equals("/stop")) {
            stop(writer, name, smClient);
        } else if (command.equals("/persist")) {
            persist(writer, smClient);
        } else {
            writer.println(smClient.getString("hostManagerServlet.unknownCommand", command));
        }

        // Finish up the response
        writer.flush();
        writer.close();

    }

    /**
     * Add host with the given parameters.
     *
     * @param request  The request
     * @param writer   The output writer
     * @param name     The host name
     * @param htmlMode Flag value
     * @param smClient StringManager for the client's locale
     */
    protected void add(HttpServletRequest request, PrintWriter writer, String name, boolean htmlMode,
            StringManager smClient) {
        String aliases = request.getParameter("aliases");
        String appBase = request.getParameter("appBase");
        boolean manager = booleanParameter(request, "manager", false, htmlMode);
        boolean autoDeploy = booleanParameter(request, "autoDeploy", true, htmlMode);
        boolean deployOnStartup = booleanParameter(request, "deployOnStartup", true, htmlMode);
        boolean deployXML = booleanParameter(request, "deployXML", true, htmlMode);
        boolean unpackWARs = booleanParameter(request, "unpackWARs", true, htmlMode);
        boolean copyXML = booleanParameter(request, "copyXML", false, htmlMode);
        add(writer, name, aliases, appBase, manager, autoDeploy, deployOnStartup, deployXML, unpackWARs, copyXML,
                smClient);
    }


    /**
     * Extract boolean value from checkbox with default.
     *
     * @param request    The Servlet request
     * @param parameter  The parameter name
     * @param theDefault Default value
     * @param htmlMode   Flag value
     *
     * @return the boolean value for the parameter
     */
    protected boolean booleanParameter(HttpServletRequest request, String parameter, boolean theDefault,
            boolean htmlMode) {
        String value = request.getParameter(parameter);
        boolean booleanValue = theDefault;
        if (value != null) {
            if (htmlMode) {
                if (value.equals("on")) {
                    booleanValue = true;
                }
            } else if (theDefault) {
                if (value.equals("false")) {
                    booleanValue = false;
                }
            } else if (value.equals("true")) {
                booleanValue = true;
            }
        } else if (htmlMode) {
            booleanValue = false;
        }
        return booleanValue;
    }


    @Override
    public void init() throws ServletException {

        // Ensure that our ContainerServlet properties have been set
        if (wrapper == null || context == null) {
            throw new UnavailableException(sm.getString("hostManagerServlet.noWrapper"));
        }

        // Set our properties from the initialization parameters
        String value = null;
        try {
            value = getServletConfig().getInitParameter("debug");
            debug = Integer.parseInt(value);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
        }

    }


    // -------------------------------------------------------- Private Methods


    /**
     * Add a host using the specified parameters.
     *
     * @param writer          Writer to render results to
     * @param name            host name
     * @param aliases         comma separated alias list
     * @param appBase         application base for the host
     * @param manager         should the manager webapp be deployed to the new host ?
     * @param autoDeploy      Flag value
     * @param deployOnStartup Flag value
     * @param deployXML       Flag value
     * @param unpackWARs      Flag value
     * @param copyXML         Flag value
     * @param smClient        StringManager for the client's locale
     */
    protected synchronized void add(PrintWriter writer, String name, String aliases, String appBase, boolean manager,
            boolean autoDeploy, boolean deployOnStartup, boolean deployXML, boolean unpackWARs, boolean copyXML,
            StringManager smClient) {
        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.add", name));
        }

        // Validate the requested host name
        if (name == null || name.length() == 0) {
            writer.println(smClient.getString("hostManagerServlet.invalidHostName", name));
            return;
        }

        // Check if host already exists
        if (engine.findChild(name) != null) {
            writer.println(smClient.getString("hostManagerServlet.alreadyHost", name));
            return;
        }

        // Validate and create appBase
        File appBaseFile = null;
        File file = null;
        String applicationBase = appBase;
        if (applicationBase == null || applicationBase.length() == 0) {
            applicationBase = name;
        }
        file = new File(applicationBase);
        if (!file.isAbsolute()) {
            file = new File(engine.getCatalinaBase(), file.getPath());
        }
        try {
            appBaseFile = file.getCanonicalFile();
        } catch (IOException e) {
            appBaseFile = file;
        }
        if (!appBaseFile.mkdirs() && !appBaseFile.isDirectory()) {
            writer.println(smClient.getString("hostManagerServlet.appBaseCreateFail", appBaseFile.toString(), name));
            return;
        }

        // Create base for config files
        File configBaseFile = getConfigBase(name);

        // Copy manager.xml if requested
        if (manager) {
            if (configBaseFile == null) {
                writer.println(smClient.getString("hostManagerServlet.configBaseCreateFail", name));
                return;
            }
            try (InputStream is = getServletContext().getResourceAsStream("/WEB-INF/manager.xml")) {
                if (is == null) {
                    writer.println(smClient.getString("hostManagerServlet.managerXml"));
                    return;
                }
                Path dest = new File(configBaseFile, "manager.xml").toPath();
                Files.copy(is, dest);
            } catch (IOException e) {
                writer.println(smClient.getString("hostManagerServlet.managerXml"));
                return;
            }
        }

        StandardHost host = new StandardHost();
        host.setAppBase(applicationBase);
        host.setName(name);

        host.addLifecycleListener(new HostConfig());

        // Add host aliases
        if (aliases != null && !aliases.isEmpty()) {
            StringTokenizer tok = new StringTokenizer(aliases, ", ");
            while (tok.hasMoreTokens()) {
                host.addAlias(tok.nextToken());
            }
        }
        host.setAutoDeploy(autoDeploy);
        host.setDeployOnStartup(deployOnStartup);
        host.setDeployXML(deployXML);
        host.setUnpackWARs(unpackWARs);
        host.setCopyXML(copyXML);

        // Add new host
        try {
            engine.addChild(host);
        } catch (Exception e) {
            writer.println(smClient.getString("hostManagerServlet.exception", e.toString()));
            return;
        }

        host = (StandardHost) engine.findChild(name);
        if (host != null) {
            writer.println(smClient.getString("hostManagerServlet.addSuccess", name));
        } else {
            // Something failed
            writer.println(smClient.getString("hostManagerServlet.addFailed", name));
        }

    }


    /**
     * Remove the specified host.
     *
     * @param writer   Writer to render results to
     * @param name     host name
     * @param smClient StringManager for the client's locale
     */
    protected synchronized void remove(PrintWriter writer, String name, StringManager smClient) {

        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.remove", name));
        }

        // Validate the requested host name
        if (name == null || name.length() == 0) {
            writer.println(smClient.getString("hostManagerServlet.invalidHostName", name));
            return;
        }

        // Check if host exists
        if (engine.findChild(name) == null) {
            writer.println(smClient.getString("hostManagerServlet.noHost", name));
            return;
        }

        // Prevent removing our own host
        if (engine.findChild(name) == installedHost) {
            writer.println(smClient.getString("hostManagerServlet.cannotRemoveOwnHost", name));
            return;
        }

        // Remove host
        // Note that the host will not get physically removed
        try {
            Container child = engine.findChild(name);
            engine.removeChild(child);
            if (child instanceof ContainerBase) {
                child.destroy();
            }
        } catch (Exception e) {
            writer.println(smClient.getString("hostManagerServlet.exception", e.toString()));
            return;
        }

        Host host = (StandardHost) engine.findChild(name);
        if (host == null) {
            writer.println(smClient.getString("hostManagerServlet.removeSuccess", name));
        } else {
            // Something failed
            writer.println(smClient.getString("hostManagerServlet.removeFailed", name));
        }

    }


    /**
     * Render a list of the currently active Contexts in our virtual host.
     *
     * @param writer   Writer to render to
     * @param smClient StringManager for the client's locale
     */
    protected void list(PrintWriter writer, StringManager smClient) {

        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.list", engine.getName()));
        }

        writer.println(smClient.getString("hostManagerServlet.listed", engine.getName()));
        Container[] hosts = engine.findChildren();
        for (Container container : hosts) {
            Host host = (Host) container;
            String name = host.getName();
            String[] aliases = host.findAliases();
            writer.println(String.format("[%s]:[%s]", name, StringUtils.join(aliases)));
        }
    }


    /**
     * Start the host with the specified name.
     *
     * @param writer   Writer to render to
     * @param name     Host name
     * @param smClient StringManager for the client's locale
     */
    protected void start(PrintWriter writer, String name, StringManager smClient) {

        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.start", name));
        }

        // Validate the requested host name
        if (name == null || name.length() == 0) {
            writer.println(smClient.getString("hostManagerServlet.invalidHostName", name));
            return;
        }

        Container host = engine.findChild(name);

        // Check if host exists
        if (host == null) {
            writer.println(smClient.getString("hostManagerServlet.noHost", name));
            return;
        }

        // Prevent starting our own host
        if (host == installedHost) {
            writer.println(smClient.getString("hostManagerServlet.cannotStartOwnHost", name));
            return;
        }

        // Don't start host if already started
        if (host.getState().isAvailable()) {
            writer.println(smClient.getString("hostManagerServlet.alreadyStarted", name));
            return;
        }

        // Start host
        try {
            host.start();
            writer.println(smClient.getString("hostManagerServlet.started", name));
        } catch (Exception e) {
            getServletContext().log(sm.getString("hostManagerServlet.startFailed", name), e);
            writer.println(smClient.getString("hostManagerServlet.startFailed", name));
            writer.println(smClient.getString("hostManagerServlet.exception", e.toString()));
        }
    }


    /**
     * Stop the host with the specified name.
     *
     * @param writer   Writer to render to
     * @param name     Host name
     * @param smClient StringManager for the client's locale
     */
    protected void stop(PrintWriter writer, String name, StringManager smClient) {

        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.stop", name));
        }

        // Validate the requested host name
        if (name == null || name.length() == 0) {
            writer.println(smClient.getString("hostManagerServlet.invalidHostName", name));
            return;
        }

        Container host = engine.findChild(name);

        // Check if host exists
        if (host == null) {
            writer.println(smClient.getString("hostManagerServlet.noHost", name));
            return;
        }

        // Prevent stopping our own host
        if (host == installedHost) {
            writer.println(smClient.getString("hostManagerServlet.cannotStopOwnHost", name));
            return;
        }

        // Don't stop host if already stopped
        if (!host.getState().isAvailable()) {
            writer.println(smClient.getString("hostManagerServlet.alreadyStopped", name));
            return;
        }

        // Stop host
        try {
            host.stop();
            writer.println(smClient.getString("hostManagerServlet.stopped", name));
        } catch (Exception e) {
            getServletContext().log(sm.getString("hostManagerServlet.stopFailed", name), e);
            writer.println(smClient.getString("hostManagerServlet.stopFailed", name));
            writer.println(smClient.getString("hostManagerServlet.exception", e.toString()));
        }
    }


    /**
     * Persist the current configuration to server.xml.
     *
     * @param writer   Writer to render to
     * @param smClient i18n resources localized for the client
     */
    protected void persist(PrintWriter writer, StringManager smClient) {

        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.persist"));
        }

        try {
            MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName oname = new ObjectName(engine.getDomain() + ":type=StoreConfig");
            platformMBeanServer.invoke(oname, "storeConfig", null, null);
            writer.println(smClient.getString("hostManagerServlet.persisted"));
        } catch (Exception e) {
            getServletContext().log(sm.getString("hostManagerServlet.persistFailed"), e);
            writer.println(smClient.getString("hostManagerServlet.persistFailed"));
            // catch InstanceNotFoundException when StoreConfig is not enabled instead of printing
            // the failure message
            if (e instanceof InstanceNotFoundException) {
                writer.println(smClient.getString("hostManagerServlet.noStoreConfig"));
            } else {
                writer.println(smClient.getString("hostManagerServlet.exception", e.toString()));
            }
        }
    }


    // -------------------------------------------------------- Support Methods

    /**
     * Get config base.
     *
     * @param hostName The host name
     *
     * @return the config base for the host
     */
    protected File getConfigBase(String hostName) {
        File configBase = new File(context.getCatalinaBase(), "conf");
        if (!configBase.exists()) {
            return null;
        }
        if (engine != null) {
            configBase = new File(configBase, engine.getName());
        }
        if (installedHost != null) {
            configBase = new File(configBase, hostName);
        }
        if (!configBase.mkdirs() && !configBase.isDirectory()) {
            return null;
        }
        return configBase;
    }
}
