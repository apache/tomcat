/*
 * Copyright 1999,2004-2006 The Apache Software Foundation.
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


package org.apache.catalina.manager.host;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.StringTokenizer;

import javax.management.MBeanServer;
import javax.servlet.ServletException;
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
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.util.StringManager;
import org.apache.tomcat.util.modeler.Registry;


/**
 * Servlet that enables remote management of the virtual hosts installed
 * on the server.  Normally, this functionality will be protected by 
 * a security constraint in the web application deployment descriptor.  
 * However, this requirement can be relaxed during testing.
 * <p>
 * This servlet examines the value returned by <code>getPathInfo()</code>
 * and related query parameters to determine what action is being requested.
 * The following actions and parameters (starting after the servlet path)
 * are supported:
 * <ul>
 * <li><b>/add?name={host-name}&aliases={host-aliases}&manager={manager}</b> -
 *     Create and add a new virtual host. The <code>host-name</code> attribute
 *     indicates the name of the new host. The <code>host-aliases</code> 
 *     attribute is a comma separated list of the host alias names. 
 *     The <code>manager</code> attribute is a boolean value indicating if the
 *     webapp manager will be installed in the newly created host (optional, 
 *     false by default).</li>
 * <li><b>/remove?name={host-name}</b> - Remove a virtual host. 
 *     The <code>host-name</code> attribute indicates the name of the host.
 *     </li>
 * <li><b>/list</b> - List the virtual hosts installed on the server.
 *     Each host will be listed with the following format 
 *     <code>host-name#host-aliases</code>.</li>
 * <li><b>/start?name={host-name}</b> - Start the virtual host.</li>
 * <li><b>/stop?name={host-name}</b> - Stop the virtual host.</li>
 * </ul>
 * <p>
 * <b>NOTE</b> - Attempting to stop or remove the host containing
 * this servlet itself will not succeed.  Therefore, this servlet should
 * generally be deployed in a separate virtual host.
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
 * @version $Revision: 384293 $ $Date: 2006-03-08 19:09:36 +0100 (mer., 08 mars 2006) $
 */

public class HostManagerServlet
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
     * The associated host.
     */
    protected Host host = null;

    
    /**
     * The associated engine.
     */
    protected Engine engine = null;

    
    /**
     * MBean server.
     */
    protected MBeanServer mBeanServer = null;


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
            engine = null;
        } else {
            context = (Context) wrapper.getParent();
            host = (Host) context.getParent();
            engine = (Engine) host.getParent();
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
                (sm.getString("hostManagerServlet.cannotInvoke"));

        // Identify the request parameters that we need
        String command = request.getPathInfo();
        if (command == null)
            command = request.getServletPath();
        String name = request.getParameter("name");
  
        // Prepare our output writer to generate the response message
        response.setContentType("text/plain; charset=" + Constants.CHARSET);
        PrintWriter writer = response.getWriter();

        // Process the requested command
        if (command == null) {
            writer.println(sm.getString("hostManagerServlet.noCommand"));
        } else if (command.equals("/add")) {
            add(request, writer, name, false);
        } else if (command.equals("/remove")) {
            remove(writer, name);
        } else if (command.equals("/list")) {
            list(writer);
        } else if (command.equals("/start")) {
            start(writer, name);
        } else if (command.equals("/stop")) {
            stop(writer, name);
        } else {
            writer.println(sm.getString("hostManagerServlet.unknownCommand",
                                        command));
        }

        // Finish up the response
        writer.flush();
        writer.close();

    }


    /**
     * Add host with the given parameters.
     *
     * @param request The request
     * @param writer The output writer
     * @param name The host name
     * @param htmlMode Flag value
     */
    protected void add(HttpServletRequest request, PrintWriter writer, String name, boolean htmlMode ) {
        String aliases = request.getParameter("aliases");
        String appBase = request.getParameter("appBase");
        boolean manager = booleanParameter(request, "manager", false, htmlMode);
        boolean autoDeploy = booleanParameter(request, "autoDeploy", true, htmlMode);
        boolean deployOnStartup = booleanParameter(request, "deployOnStartup", true, htmlMode);
        boolean deployXML = booleanParameter(request, "deployXML", true, htmlMode);
        boolean unpackWARs = booleanParameter(request, "unpackWARs", true, htmlMode);
        boolean xmlNamespaceAware = booleanParameter(request, "xmlNamespaceAware", false, htmlMode);
        boolean xmlValidation = booleanParameter(request, "xmlValidation", false, htmlMode);
        add(writer, name, aliases, appBase, manager,
            autoDeploy,
            deployOnStartup,
            deployXML,                                       
            unpackWARs,
            xmlNamespaceAware,
            xmlValidation);
    }


    /**
     * extract boolean value from checkbox with default
     * @param request
     * @param parameter
     * @param theDefault
     * @param htmlMode
     * @return
     */
    protected boolean booleanParameter(HttpServletRequest request,
            String parameter, boolean theDefault, boolean htmlMode) {
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
        } else if (htmlMode)
            booleanValue = false;
        return booleanValue;
    }


    /**
     * Initialize this servlet.
     */
    public void init() throws ServletException {

        // Ensure that our ContainerServlet properties have been set
        if ((wrapper == null) || (context == null))
            throw new UnavailableException
                (sm.getString("hostManagerServlet.noWrapper"));

        // Verify that we were not accessed using the invoker servlet
        String servletName = getServletConfig().getServletName();
        if (servletName == null)
            servletName = "";
        if (servletName.startsWith("org.apache.catalina.INVOKER."))
            throw new UnavailableException
                (sm.getString("hostManagerServlet.cannotInvoke"));

        // Set our properties from the initialization parameters
        String value = null;
        try {
            value = getServletConfig().getInitParameter("debug");
            debug = Integer.parseInt(value);
        } catch (Throwable t) {
            ;
        }

    }



    // -------------------------------------------------------- Private Methods


    /**
     * Add a host using the specified parameters.
     *
     * @param writer Writer to render results to
     * @param name host name
     * @param aliases comma separated alias list
     * @param appBase application base for the host
     * @param manager should the manager webapp be deployed to the new host ?
     */
    protected synchronized void add
        (PrintWriter writer, String name, String aliases, String appBase, 
         boolean manager,
         boolean autoDeploy,
         boolean deployOnStartup,
         boolean deployXML,                                       
         boolean unpackWARs,
         boolean xmlNamespaceAware,
         boolean xmlValidation) {
        if (debug >= 1) {
            log("add: Adding host '" + name + "'");
        }

        // Validate the requested host name
        if ((name == null) || name.length() == 0) {
            writer.println(sm.getString("hostManagerServlet.invalidHostName", name));
            return;
        }

        // Check if host already exists
        if (engine.findChild(name) != null) {
            writer.println
                (sm.getString("hostManagerServlet.alreadyHost", name));
            return;
        }

        // Validate and create appBase
        File appBaseFile = null;
        if (appBase == null || appBase.length() == 0) {
            appBase = name;
        }
        File file = new File(appBase);
        if (!file.isAbsolute())
            file = new File(System.getProperty("catalina.base"), appBase);
        try {
            appBaseFile = file.getCanonicalFile();
        } catch (IOException e) {
            appBaseFile = file;
        }
        if (!appBaseFile.exists()) {
            appBaseFile.mkdirs();
        }
        
        // Create base for config files
        File configBaseFile = getConfigBase(name);
        
        // Copy manager.xml if requested
        if (manager) {
            InputStream is = null;
            OutputStream os = null;
            try {
                is = getServletContext().getResourceAsStream("/manager.xml");
                os = new FileOutputStream(new File(configBaseFile, "manager.xml"));
                byte buffer[] = new byte[512];
                int len = buffer.length;
                while (true) {
                    len = is.read(buffer);
                    if (len == -1)
                        break;
                    os.write(buffer, 0, len);
                }
            } catch (IOException e) {
                writer.println
                    (sm.getString("hostManagerServlet.managerXml"));
                return;
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
        
        StandardHost host = new StandardHost();
        host.setAppBase(appBase);
        host.setName(name);

        host.addLifecycleListener(new HostConfig());

        // Add host aliases
        if ((aliases != null) && !("".equals(aliases))) {
            StringTokenizer tok = new StringTokenizer(aliases, ",");
            while (tok.hasMoreTokens()) {
                host.addAlias(tok.nextToken());
            }
        }
        host.setAutoDeploy(autoDeploy);
        host.setDeployOnStartup(deployOnStartup);
        host.setDeployXML(deployXML);
        host.setUnpackWARs(unpackWARs);
        host.setXmlNamespaceAware(xmlNamespaceAware);
        host.setXmlValidation(xmlValidation);
        
        // Add new host
        try {
            engine.addChild(host);
        } catch (Exception e) {
            writer.println(sm.getString("hostManagerServlet.exception",
                                        e.toString()));
            return;
        }
        
        host = (StandardHost) engine.findChild(name);
        if (host != null) {
            writer.println(sm.getString("hostManagerServlet.add", name));
        } else {
            // Something failed
            writer.println(sm.getString("hostManagerServlet.addFailed", name));
        }
        
    }


    /**
     * Remove the specified host.
     *
     * @param writer Writer to render results to
     * @param name host name
     */
    protected synchronized void remove(PrintWriter writer, String name) {

        if (debug >= 1) {
            log("remove: Removing host '" + name + "'");
        }

        // Validate the requested host name
        if ((name == null) || name.length() == 0) {
            writer.println(sm.getString("hostManagerServlet.invalidHostName", name));
            return;
        }

        // Check if host exists
        if (engine.findChild(name) == null) {
            writer.println
                (sm.getString("hostManagerServlet.noHost", name));
            return;
        }

        // Prevent removing our own host
        if (engine.findChild(name) == host) {
            writer.println
                (sm.getString("hostManagerServlet.cannotRemoveOwnHost", name));
            return;
        }

        // Remove host
        // Note that the host will not get physically removed
        try {
            engine.removeChild(engine.findChild(name));
        } catch (Exception e) {
            writer.println(sm.getString("hostManagerServlet.exception",
                                        e.toString()));
            return;
        }
        
        Host host = (StandardHost) engine.findChild(name);
        if (host == null) {
            writer.println(sm.getString("hostManagerServlet.remove", name));
        } else {
            // Something failed
            writer.println(sm.getString("hostManagerServlet.removeFailed", name));
        }
        
    }


    /**
     * Render a list of the currently active Contexts in our virtual host.
     *
     * @param writer Writer to render to
     */
    protected void list(PrintWriter writer) {

        if (debug >= 1)
            log("list: Listing hosts for engine '" 
                + engine.getName() + "'");

        writer.println(sm.getString("hostManagerServlet.listed",
                                    engine.getName()));
        Container[] hosts = engine.findChildren();
        for (int i = 0; i < hosts.length; i++) {
            Host host = (Host) hosts[i];
            String name = host.getName();
            String[] aliases = host.findAliases();
            StringBuffer buf = new StringBuffer();
            if (aliases.length > 0) {
                buf.append(aliases[0]);
                for (int j = 1; j < aliases.length; j++) {
                    buf.append(',').append(aliases[j]);
                }
            }
            writer.println(sm.getString("hostManagerServlet.listitem",
                                        name, buf.toString()));
        }
    }


    /**
     * Start the host with the specified name.
     *
     * @param writer Writer to render to
     * @param name Host name
     */
    protected void start(PrintWriter writer, String name) {

        if (debug >= 1)
            log("start: Starting host with name '" + name + "'");

        // Validate the requested host name
        if ((name == null) || name.length() == 0) {
            writer.println(sm.getString("hostManagerServlet.invalidHostName", name));
            return;
        }

        // Check if host exists
        if (engine.findChild(name) == null) {
            writer.println
                (sm.getString("hostManagerServlet.noHost", name));
            return;
        }

        // Prevent starting our own host
        if (engine.findChild(name) == host) {
            writer.println
                (sm.getString("hostManagerServlet.cannotStartOwnHost", name));
            return;
        }

        // Start host
        try {
            ((Lifecycle) engine.findChild(name)).start();
            writer.println
                (sm.getString("hostManagerServlet.started", name));
        } catch (Throwable t) {
            getServletContext().log
                (sm.getString("hostManagerServlet.startFailed", name), t);
            writer.println
                (sm.getString("hostManagerServlet.startFailed", name));
            writer.println(sm.getString("hostManagerServlet.exception",
                                        t.toString()));
            return;
        }
        
    }


    /**
     * Start the host with the specified name.
     *
     * @param writer Writer to render to
     * @param name Host name
     */
    protected void stop(PrintWriter writer, String name) {

        if (debug >= 1)
            log("stop: Stopping host with name '" + name + "'");

        // Validate the requested host name
        if ((name == null) || name.length() == 0) {
            writer.println(sm.getString("hostManagerServlet.invalidHostName", name));
            return;
        }

        // Check if host exists
        if (engine.findChild(name) == null) {
            writer.println
                (sm.getString("hostManagerServlet.noHost", name));
            return;
        }

        // Prevent starting our own host
        if (engine.findChild(name) == host) {
            writer.println
                (sm.getString("hostManagerServlet.cannotStopOwnHost", name));
            return;
        }

        // Start host
        try {
            ((Lifecycle) engine.findChild(name)).stop();
            writer.println
                (sm.getString("hostManagerServlet.stopped", name));
        } catch (Throwable t) {
            getServletContext().log
                (sm.getString("hostManagerServlet.stopFailed", name), t);
            writer.println
                (sm.getString("hostManagerServlet.stopFailed", name));
            writer.println(sm.getString("hostManagerServlet.exception",
                                        t.toString()));
            return;
        }
        
    }


    // -------------------------------------------------------- Support Methods


    /**
     * Get config base.
     */
    protected File getConfigBase(String hostName) {
        File configBase = 
            new File(System.getProperty("catalina.base"), "conf");
        if (!configBase.exists()) {
            return null;
        }
        if (engine != null) {
            configBase = new File(configBase, engine.getName());
        }
        if (host != null) {
            configBase = new File(configBase, hostName);
        }
        configBase.mkdirs();
        return configBase;
    }


}
