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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.DistributedManager;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.manager.util.BaseSessionComparator;
import org.apache.catalina.manager.util.ReverseComparator;
import org.apache.catalina.manager.util.SessionUtils;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.URLEncoder;
import org.apache.tomcat.util.http.fileupload.ParameterParser;
import org.apache.tomcat.util.res.StringManager;

/**
* Servlet that enables remote management of the web applications deployed
* within the same virtual host as this web application is.  Normally, this
* functionality will be protected by a security constraint in the web
* application deployment descriptor.  However, this requirement can be
* relaxed during testing.
* <p>
* The difference between the <code>ManagerServlet</code> and this
* Servlet is that this Servlet prints out a HTML interface which
* makes it easier to administrate.
* <p>
* However if you use a software that parses the output of
* <code>ManagerServlet</code> you won't be able to upgrade
* to this Servlet since the output are not in the
* same format ar from <code>ManagerServlet</code>
*
* @author Bip Thelin
* @author Malcolm Edgar
* @author Glenn L. Nielsen
* @version $Id$
* @see ManagerServlet
*/

public final class HTMLManagerServlet extends ManagerServlet {

    private static final long serialVersionUID = 1L;

    protected static final URLEncoder URL_ENCODER; 
    protected static final String APPLICATION_MESSAGE = "message";
    protected static final String APPLICATION_ERROR = "error";
    
    protected static final String sessionsListJspPath  = "/WEB-INF/jsp/sessionsList.jsp";
    protected static final String sessionDetailJspPath = "/WEB-INF/jsp/sessionDetail.jsp";

    static {
        URL_ENCODER = new URLEncoder();
        // '/' should not be encoded in context paths
        URL_ENCODER.addSafeCharacter('/');
    }
    
    private final Random randomSource = new Random();
    
    private boolean showProxySessions = false;

    // --------------------------------------------------------- Public Methods

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

        StringManager smClient = getStringManager(request);
        
        // Identify the request parameters that we need
        // By obtaining the command from the pathInfo, per-command security can
        // be configured in web.xml
        String command = request.getPathInfo();

        String path = request.getParameter("path");

        // Prepare our output writer to generate the response message
        response.setContentType("text/html; charset=" + Constants.CHARSET);

        String message = "";
        // Process the requested command
        if (command == null || command.equals("/")) {
            // No command == list
        } else if (command.equals("/list")) {
            // List always displayed - nothing to do here
        } else if (command.equals("/sessions")) {
            try {
                doSessions(path, request, response, smClient);
                return;
            } catch (Exception e) {
                log("HTMLManagerServlet.sessions[" + path + "]", e);
                message = smClient.getString("managerServlet.exception",
                        e.toString());
            }
        } else if (command.equals("/upload") || command.equals("/deploy") ||
                command.equals("/reload") || command.equals("/undeploy") ||
                command.equals("/expire") || command.equals("/start") ||
                command.equals("/stop")) {
            message =
                smClient.getString("managerServlet.postCommand", command);
        } else {
            message =
                smClient.getString("managerServlet.unknownCommand", command);
        }

        list(request, response, message, smClient);
    }

    /**
     * Process a POST request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    public void doPost(HttpServletRequest request,
                      HttpServletResponse response)
        throws IOException, ServletException {

        StringManager smClient = getStringManager(request);

        // Identify the request parameters that we need
        // By obtaining the command from the pathInfo, per-command security can
        // be configured in web.xml
        String command = request.getPathInfo();

        String path = request.getParameter("path");
        String deployPath = request.getParameter("deployPath");
        String deployConfig = request.getParameter("deployConfig");
        String deployWar = request.getParameter("deployWar");

        // Prepare our output writer to generate the response message
        response.setContentType("text/html; charset=" + Constants.CHARSET);

        String message = "";

        if (command == null || command.length() == 0) {
            // No command == list
            // List always displayed -> do nothing
        } else if (command.equals("/upload")) {
            message = upload(request, smClient);
        } else if (command.equals("/deploy")) {
            message = deployInternal(deployConfig, deployPath, deployWar,
                    smClient);
        } else if (command.equals("/reload")) {
            message = reload(path, smClient);
        } else if (command.equals("/undeploy")) {
            message = undeploy(path, smClient);
        } else if (command.equals("/expire")) {
            message = expireSessions(path, request, smClient);
        } else if (command.equals("/start")) {
            message = start(path, smClient);
        } else if (command.equals("/stop")) {
            message = stop(path, smClient);
        } else if (command.equals("/findleaks")) {
            message = findleaks(smClient);
        } else {
            // Try GET
            doGet(request,response);
            return;
        }

        list(request, response, message, smClient);
    }

    /**
     * Generate a once time token (nonce) for authenticating subsequent
     * requests. This will also add the token to the session. The nonce
     * generation is a simplified version of ManagerBase.generateSessionId().
     * 
     */
    protected String generateNonce() {
        byte random[] = new byte[16];

        // Render the result as a String of hexadecimal digits
        StringBuilder buffer = new StringBuilder();

        randomSource.nextBytes(random);
       
        for (int j = 0; j < random.length; j++) {
            byte b1 = (byte) ((random[j] & 0xf0) >> 4);
            byte b2 = (byte) (random[j] & 0x0f);
            if (b1 < 10)
                buffer.append((char) ('0' + b1));
            else
                buffer.append((char) ('A' + (b1 - 10)));
            if (b2 < 10)
                buffer.append((char) ('0' + b2));
            else
                buffer.append((char) ('A' + (b2 - 10)));
        }

        return buffer.toString();
    }

    protected String upload(HttpServletRequest request, StringManager smClient)
            throws IOException, ServletException {
        String message = "";

        Part warPart = null;
        String filename = null;

        Collection<Part> parts = request.getParts();
        Iterator<Part> iter = parts.iterator();
        
        try {
            while (iter.hasNext()) {
                Part part = iter.next();
                if (part.getName().equals("deployWar") && warPart == null) {
                    warPart = part;
                } else {
                    part.delete();
                }
            }

            while (true) {
                if (warPart == null) {
                    message = smClient.getString(
                            "htmlManagerServlet.deployUploadNoFile");
                    break;
                }
                filename =
                    extractFilename(warPart.getHeader("Content-Disposition"));
                if (!filename.toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                    message = smClient.getString(
                            "htmlManagerServlet.deployUploadNotWar", filename);
                    break;
                }
                // Get the filename if uploaded name includes a path
                if (filename.lastIndexOf('\\') >= 0) {
                    filename =
                        filename.substring(filename.lastIndexOf('\\') + 1);
                }
                if (filename.lastIndexOf('/') >= 0) {
                    filename =
                        filename.substring(filename.lastIndexOf('/') + 1);
                }

                // Identify the appBase of the owning Host of this Context
                // (if any)
                File file = new File(getAppBase(), filename);
                if (file.exists()) {
                    message = smClient.getString(
                            "htmlManagerServlet.deployUploadWarExists",
                            filename);
                    break;
                }
                
                ContextName cn = new ContextName(filename);
                String name = cn.getName();

                if ((host.findChild(name) != null) && !isDeployed(name)) {
                    message = smClient.getString(
                            "htmlManagerServlet.deployUploadInServerXml",
                            filename);
                    break;
                }

                if (!isServiced(name)) {
                    addServiced(name);
                    try {
                        warPart.write(file.getAbsolutePath());
                        // Perform new deployment
                        check(name);
                    } finally {
                        removeServiced(name);
                    }
                }
                break;
            }
        } catch(Exception e) {
            message = smClient.getString
                ("htmlManagerServlet.deployUploadFail", e.getMessage());
            log(message, e);
        } finally {
            if (warPart != null) {
                warPart.delete();
            }
            warPart = null;
        }
        return message;
    }

    /*
     * Adapted from FileUploadBase.getFileName()
     */
    private String extractFilename(String cd) {
        String fileName = null;
        if (cd != null) {
            String cdl = cd.toLowerCase(Locale.ENGLISH);
            if (cdl.startsWith("form-data") || cdl.startsWith("attachment")) {
                ParameterParser parser = new ParameterParser();
                parser.setLowerCaseNames(true);
                // Parameter parser can handle null input
                Map<String,String> params =
                    parser.parse(cd, ';');
                if (params.containsKey("filename")) {
                    fileName = params.get("filename");
                    if (fileName != null) {
                        fileName = fileName.trim();
                    } else {
                        // Even if there is no value, the parameter is present,
                        // so we return an empty file name rather than no file
                        // name.
                        fileName = "";
                    }
                }
            }
        }
        return fileName;
    }

    /**
     * Deploy an application for the specified path from the specified
     * web application archive.
     *
     * @param config URL of the context configuration file to be deployed
     * @param path Context path of the application to be deployed
     * @param war URL of the web application archive to be deployed
     * @return message String
     */
    protected String deployInternal(String config, String path, String war,
            StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.deploy(printWriter, config, path, war, false, smClient);

        return stringWriter.toString();
    }

    /**
     * Render a HTML list of the currently active Contexts in our virtual host,
     * and memory and server status information.
     *
     * @param request The request
     * @param response The response
     * @param message a message to display
     */
    public void list(HttpServletRequest request,
                     HttpServletResponse response,
                     String message,
                     StringManager smClient) throws IOException {

        if (debug >= 1)
            log("list: Listing contexts for virtual host '" +
                host.getName() + "'");

        PrintWriter writer = response.getWriter();

        // HTML Header Section
        writer.print(Constants.HTML_HEADER_SECTION);

        // Body Header Section
        Object[] args = new Object[2];
        args[0] = request.getContextPath();
        args[1] = smClient.getString("htmlManagerServlet.title");
        writer.print(MessageFormat.format
                     (Constants.BODY_HEADER_SECTION, args));

        // Message Section
        args = new Object[3];
        args[0] = smClient.getString("htmlManagerServlet.messageLabel");
        if (message == null || message.length() == 0) {
            args[1] = "OK";
        } else {
            args[1] = RequestUtil.filter(message);
        }
        writer.print(MessageFormat.format(Constants.MESSAGE_SECTION, args));

        // Manager Section
        args = new Object[9];
        args[0] = smClient.getString("htmlManagerServlet.manager");
        args[1] = response.encodeURL(request.getContextPath() + "/html/list");
        args[2] = smClient.getString("htmlManagerServlet.list");
        args[3] = response.encodeURL
            (request.getContextPath() + "/" +
             smClient.getString("htmlManagerServlet.helpHtmlManagerFile"));
        args[4] = smClient.getString("htmlManagerServlet.helpHtmlManager");
        args[5] = response.encodeURL
            (request.getContextPath() + "/" +
             smClient.getString("htmlManagerServlet.helpManagerFile"));
        args[6] = smClient.getString("htmlManagerServlet.helpManager");
        args[7] = response.encodeURL
            (request.getContextPath() + "/status");
        args[8] = smClient.getString("statusServlet.title");
        writer.print(MessageFormat.format(Constants.MANAGER_SECTION, args));

        // Apps Header Section
        args = new Object[7];
        args[0] = smClient.getString("htmlManagerServlet.appsTitle");
        args[1] = smClient.getString("htmlManagerServlet.appsPath");
        args[2] = smClient.getString("htmlManagerServlet.appsVersion");
        args[3] = smClient.getString("htmlManagerServlet.appsName");
        args[4] = smClient.getString("htmlManagerServlet.appsAvailable");
        args[5] = smClient.getString("htmlManagerServlet.appsSessions");
        args[6] = smClient.getString("htmlManagerServlet.appsTasks");
        writer.print(MessageFormat.format(APPS_HEADER_SECTION, args));

        // Apps Row Section
        // Create sorted map of deployed applications by context name.
        Container children[] = host.findChildren();
        String contextNames[] = new String[children.length];
        for (int i = 0; i < children.length; i++)
            contextNames[i] = children[i].getName();

        Arrays.sort(contextNames);
        
        String appsStart = smClient.getString("htmlManagerServlet.appsStart");
        String appsStop = smClient.getString("htmlManagerServlet.appsStop");
        String appsReload = smClient.getString("htmlManagerServlet.appsReload");
        String appsUndeploy =
            smClient.getString("htmlManagerServlet.appsUndeploy");
        String appsExpire = smClient.getString("htmlManagerServlet.appsExpire");
        String noVersion = "<i>" +
            smClient.getString("htmlManagerServlet.noVersion") + "</i>";

        boolean isHighlighted = true;
        boolean isDeployed = true;
        String highlightColor = null;

        for (String contextName : contextNames) {
            Context ctxt = (Context) host.findChild(contextName);
            
            if (ctxt != null) {
                // Bugzilla 34818, alternating row colors
                isHighlighted = !isHighlighted;
                if(isHighlighted) {
                    highlightColor = "#C3F3C3";
                } else {
                    highlightColor = "#FFFFFF";
                }

                String contextPath = ctxt.getPath();
                String displayPath = contextPath;
                if (displayPath.equals("")) {
                    displayPath = "/";
                }

                try {
                    isDeployed = isDeployed(contextName);
                } catch (Exception e) {
                    // Assume false on failure for safety
                    isDeployed = false;
                }
                
                args = new Object[7];
                args[0] = "<a href=\"" + displayPath + "\">" +
                        URL_ENCODER.encode(displayPath) + "</a>";
                args[1] = ctxt.getWebappVersion();
                if ("".equals(args[1])) {
                    args[1]= noVersion;
                }
                args[2] = ctxt.getDisplayName();
                if (args[2] == null) {
                    args[2] = "&nbsp;";
                }
                args[3] = new Boolean(ctxt.getAvailable());
                args[4] = response.encodeURL
                    (request.getContextPath() +
                     "/html/sessions?path=" + URL_ENCODER.encode(displayPath));
                Manager manager = ctxt.getManager(); 
                if (manager instanceof DistributedManager && showProxySessions) {
                    args[5] = new Integer(
                            ((DistributedManager)manager).getActiveSessionsFull());
                } else if (ctxt.getManager() != null){
                    args[5] = new Integer(manager.getActiveSessions());
                } else {
                    args[5] = new Integer(0);
                }

                args[6] = highlightColor;

                writer.print
                    (MessageFormat.format(APPS_ROW_DETAILS_SECTION, args));

                args = new Object[14];
                args[0] = response.encodeURL
                    (request.getContextPath() +
                     "/html/start?path=" + URL_ENCODER.encode(displayPath));
                args[1] = appsStart;
                args[2] = response.encodeURL
                    (request.getContextPath() +
                     "/html/stop?path=" + URL_ENCODER.encode(displayPath));
                args[3] = appsStop;
                args[4] = response.encodeURL
                    (request.getContextPath() +
                     "/html/reload?path=" + URL_ENCODER.encode(displayPath));
                args[5] = appsReload;
                args[6] = response.encodeURL
                    (request.getContextPath() +
                     "/html/undeploy?path=" + URL_ENCODER.encode(displayPath));
                args[7] = appsUndeploy;
                
                args[8] = response.encodeURL
                    (request.getContextPath() +
                     "/html/expire?path=" + URL_ENCODER.encode(displayPath));
                args[9] = appsExpire;
                args[10] = smClient.getString(
                        "htmlManagerServlet.expire.explain");
                if (manager == null) {
                    args[11] = smClient.getString(
                            "htmlManagerServlet.noManager");
                } else {
                    args[11] = new Integer(
                            ctxt.getManager().getMaxInactiveInterval()/60);
                }
                args[12] = smClient.getString("htmlManagerServlet.expire.unit");
                args[13] = highlightColor;
                
                if (ctxt.getName().equals(this.context.getName())) {
                    writer.print(MessageFormat.format(
                        MANAGER_APP_ROW_BUTTON_SECTION, args));
                } else if (ctxt.getAvailable() && isDeployed) {
                    writer.print(MessageFormat.format(
                        STARTED_DEPLOYED_APPS_ROW_BUTTON_SECTION, args));
                } else if (ctxt.getAvailable() && !isDeployed) {
                    writer.print(MessageFormat.format(
                        STARTED_NONDEPLOYED_APPS_ROW_BUTTON_SECTION, args));
                } else if (!ctxt.getAvailable() && isDeployed) {
                    writer.print(MessageFormat.format(
                        STOPPED_DEPLOYED_APPS_ROW_BUTTON_SECTION, args));
                } else {
                    writer.print(MessageFormat.format(
                        STOPPED_NONDEPLOYED_APPS_ROW_BUTTON_SECTION, args));
                }

            }
        }

        // Deploy Section
        args = new Object[7];
        args[0] = smClient.getString("htmlManagerServlet.deployTitle");
        args[1] = smClient.getString("htmlManagerServlet.deployServer");
        args[2] = response.encodeURL(request.getContextPath() + "/html/deploy");
        args[3] = smClient.getString("htmlManagerServlet.deployPath");
        args[4] = smClient.getString("htmlManagerServlet.deployConfig");
        args[5] = smClient.getString("htmlManagerServlet.deployWar");
        args[6] = smClient.getString("htmlManagerServlet.deployButton");
        writer.print(MessageFormat.format(DEPLOY_SECTION, args));

        args = new Object[4];
        args[0] = smClient.getString("htmlManagerServlet.deployUpload");
        args[1] = response.encodeURL(request.getContextPath() + "/html/upload");
        args[2] = smClient.getString("htmlManagerServlet.deployUploadFile");
        args[3] = smClient.getString("htmlManagerServlet.deployButton");
        writer.print(MessageFormat.format(UPLOAD_SECTION, args));

        // Diagnostics section
        args = new Object[5];
        args[0] = smClient.getString("htmlManagerServlet.diagnosticsTitle");
        args[1] = smClient.getString("htmlManagerServlet.diagnosticsLeak");
        args[2] = response.encodeURL(
                request.getContextPath() + "/html/findleaks");
        args[3] = smClient.getString("htmlManagerServlet.diagnosticsLeakWarning");
        args[4] = smClient.getString("htmlManagerServlet.diagnosticsLeakButton");
        writer.print(MessageFormat.format(DIAGNOSTICS_SECTION, args));

        // Server Header Section
        args = new Object[7];
        args[0] = smClient.getString("htmlManagerServlet.serverTitle");
        args[1] = smClient.getString("htmlManagerServlet.serverVersion");
        args[2] = smClient.getString("htmlManagerServlet.serverJVMVersion");
        args[3] = smClient.getString("htmlManagerServlet.serverJVMVendor");
        args[4] = smClient.getString("htmlManagerServlet.serverOSName");
        args[5] = smClient.getString("htmlManagerServlet.serverOSVersion");
        args[6] = smClient.getString("htmlManagerServlet.serverOSArch");
        writer.print(MessageFormat.format
                     (Constants.SERVER_HEADER_SECTION, args));

        // Server Row Section
        args = new Object[6];
        args[0] = ServerInfo.getServerInfo();
        args[1] = System.getProperty("java.runtime.version");
        args[2] = System.getProperty("java.vm.vendor");
        args[3] = System.getProperty("os.name");
        args[4] = System.getProperty("os.version");
        args[5] = System.getProperty("os.arch");
        writer.print(MessageFormat.format(Constants.SERVER_ROW_SECTION, args));

        // HTML Tail Section
        writer.print(Constants.HTML_TAIL_SECTION);

        // Finish up the response
        writer.flush();
        writer.close();
    }

    /**
     * Reload the web application at the specified context path.
     *
     * @see ManagerServlet#reload(PrintWriter, String, StringManager)
     *
     * @param path Context path of the application to be restarted
     * @return message String
     */
    protected String reload(String path, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.reload(printWriter, path, smClient);

        return stringWriter.toString();
    }

    /**
     * Undeploy the web application at the specified context path.
     *
     * @see ManagerServlet#undeploy(PrintWriter, String, StringManager)
     *
     * @param path Context path of the application to be undeployed
     * @return message String
     */
    protected String undeploy(String path, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.undeploy(printWriter, path, smClient);

        return stringWriter.toString();
    }

    /**
     * Display session information and invoke list.
     *
     * @see ManagerServlet#sessions(PrintWriter, String, int, StringManager)
     *
     * @param path Context path of the application to list session information
     * @param idle Expire all sessions with idle time &ge; idle for this context
     * @return message String
     */
    public String sessions(String path, int idle, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.sessions(printWriter, path, idle, smClient);

        return stringWriter.toString();
    }

    /**
     * Display session information and invoke list.
     *
     * @see ManagerServlet#sessions(PrintWriter, String, StringManager)
     *
     * @param path Context path of the application to list session information
     * @return message String
     */
    public String sessions(String path, StringManager smClient) {

        return sessions(path, -1, smClient);
    }

    /**
     * Start the web application at the specified context path.
     *
     * @see ManagerServlet#start(PrintWriter, String, StringManager)
     *
     * @param path Context path of the application to be started
     * @return message String
     */
    public String start(String path, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.start(printWriter, path, smClient);

        return stringWriter.toString();
    }

    /**
     * Stop the web application at the specified context path.
     *
     * @see ManagerServlet#stop(PrintWriter, String, StringManager)
     *
     * @param path Context path of the application to be stopped
     * @return message String
     */
    protected String stop(String path, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.stop(printWriter, path, smClient);

        return stringWriter.toString();
    }

    /**
     * Find potential memory leaks caused by web application reload.
     *
     * @see ManagerServlet#findleaks(PrintWriter, StringManager) 
     *
     * @return message String
     */
    protected String findleaks(StringManager smClient) {

        StringBuilder msg = new StringBuilder();

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.findleaks(printWriter, smClient);

        if (stringWriter.getBuffer().length() > 0) {
            msg.append(smClient.getString("htmlManagerServlet.findleaksList"));
            msg.append(stringWriter.toString());
        } else {
            msg.append(smClient.getString("htmlManagerServlet.findleaksNone"));
        }
        
        return msg.toString();
    }
    
    
    /**
     * @see javax.servlet.Servlet#getServletInfo()
     */
    @Override
    public String getServletInfo() {
        return "HTMLManagerServlet, Copyright (c) 1999-2010, The Apache Software Foundation";
    }   
    
    /**
     * @see javax.servlet.GenericServlet#init()
     */
    @Override
    public void init() throws ServletException {
        super.init();
        
        // Set our properties from the initialization parameters
        String value = null;
        value = getServletConfig().getInitParameter("showProxySessions");
        showProxySessions = Boolean.parseBoolean(value);
    }   

    // ------------------------------------------------ Sessions administration

    /**
     *
     * Extract the expiration request parameter
     * 
     * @param path
     * @param req
     */
    protected String expireSessions(String path, HttpServletRequest req,
            StringManager smClient) {
        int idle = -1;
        String idleParam = req.getParameter("idle");
        if (idleParam != null) {
            try {
                idle = Integer.parseInt(idleParam);
            } catch (NumberFormatException e) {
                log("Could not parse idle parameter to an int: " + idleParam);
            }
        }
        return sessions(path, idle, smClient);
    }

    /**
     * 
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException 
     */
    protected void doSessions(String path, HttpServletRequest req,
            HttpServletResponse resp, StringManager smClient)
            throws ServletException, IOException {
        req.setAttribute("path", path);
        String action = req.getParameter("action");
        if (debug >= 1) {
            log("sessions: Session action '" + action + "' for web application at '" + path + "'");
        }
        if ("sessionDetail".equals(action)) {
            String sessionId = req.getParameter("sessionId");
            displaySessionDetailPage(req, resp, path, sessionId, smClient);
            return;
        } else if ("invalidateSessions".equals(action)) {
            String[] sessionIds = req.getParameterValues("sessionIds");
            int i = invalidateSessions(path, sessionIds, smClient);
            req.setAttribute(APPLICATION_MESSAGE, "" + i + " sessions invalidated.");
        } else if ("removeSessionAttribute".equals(action)) {
            String sessionId = req.getParameter("sessionId");
            String name = req.getParameter("attributeName");
            boolean removed =
                removeSessionAttribute(path, sessionId, name, smClient);
            String outMessage = removed ? "Session attribute '" + name + "' removed." : "Session did not contain any attribute named '" + name + "'";
            req.setAttribute(APPLICATION_MESSAGE, outMessage);
            resp.sendRedirect(resp.encodeRedirectURL(req.getRequestURL().append("?path=").append(path).append("&action=sessionDetail&sessionId=").append(sessionId).toString()));
            return;
        } // else
        displaySessionsListPage(path, req, resp, smClient);
    }

    protected List<Session> getSessionsForPath(String path,
            StringManager smClient) {
        if ((path == null) || (!path.startsWith("/") && path.equals(""))) {
            throw new IllegalArgumentException(smClient.getString(
                    "managerServlet.invalidPath", RequestUtil.filter(path)));
        }
        String searchPath = path;
        if( path.equals("/") )
            searchPath = "";
        Context ctxt = (Context) host.findChild(searchPath);
        if (null == ctxt) {
            throw new IllegalArgumentException(smClient.getString(
                    "managerServlet.noContext", RequestUtil.filter(path)));
        }
        Manager manager = ctxt.getManager();
        List<Session> sessions = new ArrayList<Session>();
        sessions.addAll(Arrays.asList(manager.findSessions()));
        if (manager instanceof DistributedManager && showProxySessions) {
            // Add dummy proxy sessions
            Set<String> sessionIds =
                ((DistributedManager) manager).getSessionIdsFull();
            // Remove active (primary and backup) session IDs from full list
            for (Session session : sessions) {
                sessionIds.remove(session.getId());
            }
            // Left with just proxy sessions - add them
            for (String sessionId : sessionIds) {
                sessions.add(new DummyProxySession(sessionId));
            }
        }
        return sessions;
    }
    protected Session getSessionForPathAndId(String path, String id,
            StringManager smClient) throws IOException {
        if ((path == null) || (!path.startsWith("/") && path.equals(""))) {
            throw new IllegalArgumentException(smClient.getString(
                    "managerServlet.invalidPath", RequestUtil.filter(path)));
        }
        String searchPath = path;
        if( path.equals("/") )
            searchPath = "";
        Context ctxt = (Context) host.findChild(searchPath);
        if (null == ctxt) {
            throw new IllegalArgumentException(smClient.getString(
                    "managerServlet.noContext", RequestUtil.filter(path)));
        }
        Session session = ctxt.getManager().findSession(id);
        return session;
    }

    /**
     * 
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException
     */
    protected void displaySessionsListPage(String path, HttpServletRequest req,
            HttpServletResponse resp, StringManager smClient)
            throws ServletException, IOException {
        List<Session> sessions = getSessionsForPath(path, smClient);
        String sortBy = req.getParameter("sort");
        String orderBy = null;
        if (null != sortBy && !"".equals(sortBy.trim())) {
            Comparator<Session> comparator = getComparator(sortBy);
            if (comparator != null) {
                orderBy = req.getParameter("order");
                if ("DESC".equalsIgnoreCase(orderBy)) {
                    comparator = new ReverseComparator(comparator);
                    // orderBy = "ASC";
                } else {
                    //orderBy = "DESC";
                }
                try {
                    Collections.sort(sessions, comparator);
                } catch (IllegalStateException ise) {
                    // at least 1 of the sessions is invalidated
                    req.setAttribute(APPLICATION_ERROR, "Can't sort session list: one session is invalidated");
                }
            } else {
                log("WARNING: unknown sort order: " + sortBy);
            }
        }
        // keep sort order
        req.setAttribute("sort", sortBy);
        req.setAttribute("order", orderBy);
        req.setAttribute("activeSessions", sessions);
        //strong>NOTE</strong> - This header will be overridden
        // automatically if a <code>RequestDispatcher.forward()</code> call is
        // ultimately invoked.
        resp.setHeader("Pragma", "No-cache"); // HTTP 1.0
        resp.setHeader("Cache-Control", "no-cache,no-store,max-age=0"); // HTTP 1.1
        resp.setDateHeader("Expires", 0); // 0 means now
        getServletContext().getRequestDispatcher(sessionsListJspPath).include(req, resp);
    }

    /**
     * 
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException
     */
    protected void displaySessionDetailPage(HttpServletRequest req,
            HttpServletResponse resp, String path, String sessionId,
            StringManager smClient) throws ServletException, IOException {
        Session session = getSessionForPathAndId(path, sessionId, smClient);
        //strong>NOTE</strong> - This header will be overridden
        // automatically if a <code>RequestDispatcher.forward()</code> call is
        // ultimately invoked.
        resp.setHeader("Pragma", "No-cache"); // HTTP 1.0
        resp.setHeader("Cache-Control", "no-cache,no-store,max-age=0"); // HTTP 1.1
        resp.setDateHeader("Expires", 0); // 0 means now
        req.setAttribute("currentSession", session);
        getServletContext().getRequestDispatcher(resp.encodeURL(sessionDetailJspPath)).include(req, resp);
    }

    /**
     * Invalidate HttpSessions
     * @param sessionIds
     * @return number of invalidated sessions
     * @throws IOException 
     */
    public int invalidateSessions(String path, String[] sessionIds,
            StringManager smClient) throws IOException {
        if (null == sessionIds) {
            return 0;
        }
        int nbAffectedSessions = 0;
        for (int i = 0; i < sessionIds.length; ++i) {
            String sessionId = sessionIds[i];
            HttpSession session = getSessionForPathAndId(path, sessionId, smClient).getSession();
            if (null == session) {
                // Shouldn't happen, but let's play nice...
                if (debug >= 1) {
                    log("WARNING: can't invalidate null session " + sessionId);
                }
                continue;
            }
            try {
                session.invalidate();
                ++nbAffectedSessions;
                if (debug >= 1) {
                    log("Invalidating session id " + sessionId);
                }
            } catch (IllegalStateException ise) {
                if (debug >= 1) {
                    log("Can't invalidate already invalidated session id " + sessionId);
                }
            }
        }
        return nbAffectedSessions;
    }

    /**
     * Removes an attribute from an HttpSession
     * @param sessionId
     * @param attributeName
     * @return true if there was an attribute removed, false otherwise
     * @throws IOException 
     */
    public boolean removeSessionAttribute(String path, String sessionId,
            String attributeName, StringManager smClient) throws IOException {
        HttpSession session = getSessionForPathAndId(path, sessionId, smClient).getSession();
        if (null == session) {
            // Shouldn't happen, but let's play nice...
            if (debug >= 1) {
                log("WARNING: can't remove attribute '" + attributeName + "' for null session " + sessionId);
            }
            return false;
        }
        boolean wasPresent = (null != session.getAttribute(attributeName));
        try {
            session.removeAttribute(attributeName);
        } catch (IllegalStateException ise) {
            if (debug >= 1) {
                log("Can't remote attribute '" + attributeName + "' for invalidated session id " + sessionId);
            }
        }
        return wasPresent;
    }

    /**
     * Sets the maximum inactive interval (session timeout) an HttpSession
     * @param sessionId
     * @param maxInactiveInterval in seconds
     * @return old value for maxInactiveInterval
     * @throws IOException 
     */
    public int setSessionMaxInactiveInterval(String path, String sessionId,
            int maxInactiveInterval, StringManager smClient) throws IOException {
        HttpSession session = getSessionForPathAndId(path, sessionId, smClient).getSession();
        if (null == session) {
            // Shouldn't happen, but let's play nice...
            if (debug >= 1) {
                log("WARNING: can't set timout for null session " + sessionId);
            }
            return 0;
        }
        try {
            int oldMaxInactiveInterval = session.getMaxInactiveInterval();
            session.setMaxInactiveInterval(maxInactiveInterval);
            return oldMaxInactiveInterval;
        } catch (IllegalStateException ise) {
            if (debug >= 1) {
                log("Can't set MaxInactiveInterval '" + maxInactiveInterval + "' for invalidated session id " + sessionId);
            }
            return 0;
        }
    }

    protected Comparator<Session> getComparator(String sortBy) {
        Comparator<Session> comparator = null;
        if ("CreationTime".equalsIgnoreCase(sortBy)) {
            comparator = new BaseSessionComparator<Date>() {
                @Override
                public Comparable<Date> getComparableObject(Session session) {
                    return new Date(session.getCreationTime());
                }
            };
        } else if ("id".equalsIgnoreCase(sortBy)) {
            comparator = new BaseSessionComparator<String>() {
                @Override
                public Comparable<String> getComparableObject(Session session) {
                    return session.getId();
                }
            };
        } else if ("LastAccessedTime".equalsIgnoreCase(sortBy)) {
            comparator = new BaseSessionComparator<Date>() {
                @Override
                public Comparable<Date> getComparableObject(Session session) {
                    return new Date(session.getLastAccessedTime());
                }
            };
        } else if ("MaxInactiveInterval".equalsIgnoreCase(sortBy)) {
            comparator = new BaseSessionComparator<Date>() {
                @Override
                public Comparable<Date> getComparableObject(Session session) {
                    return new Date(session.getMaxInactiveInterval());
                }
            };
        } else if ("new".equalsIgnoreCase(sortBy)) {
            comparator = new BaseSessionComparator<Boolean>() {
                @Override
                public Comparable<Boolean> getComparableObject(Session session) {
                    return Boolean.valueOf(session.getSession().isNew());
                }
            };
        } else if ("locale".equalsIgnoreCase(sortBy)) {
            comparator = new BaseSessionComparator<String>() {
                @Override
                public Comparable<String> getComparableObject(Session session) {
                    return JspHelper.guessDisplayLocaleFromSession(session);
                }
            };
        } else if ("user".equalsIgnoreCase(sortBy)) {
            comparator = new BaseSessionComparator<String>() {
                @Override
                public Comparable<String> getComparableObject(Session session) {
                    return JspHelper.guessDisplayUserFromSession(session);
                }
            };
        } else if ("UsedTime".equalsIgnoreCase(sortBy)) {
            comparator = new BaseSessionComparator<Date>() {
                @Override
                public Comparable<Date> getComparableObject(Session session) {
                    return new Date(SessionUtils.getUsedTimeForSession(session));
                }
            };
        } else if ("InactiveTime".equalsIgnoreCase(sortBy)) {
            comparator = new BaseSessionComparator<Date>() {
                @Override
                public Comparable<Date> getComparableObject(Session session) {
                    return new Date(SessionUtils.getInactiveTimeForSession(session));
                }
            };
        } else if ("TTL".equalsIgnoreCase(sortBy)) {
            comparator = new BaseSessionComparator<Date>() {
                @Override
                public Comparable<Date> getComparableObject(Session session) {
                    return new Date(SessionUtils.getTTLForSession(session));
                }
            };
        }
        //TODO: complete this to TTL, etc.
        return comparator;
    }

    // ------------------------------------------------------ Private Constants

    // These HTML sections are broken in relatively small sections, because of
    // limited number of substitutions MessageFormat can process
    // (maximum of 10).

    private static final String APPS_HEADER_SECTION =
        "<table border=\"1\" cellspacing=\"0\" cellpadding=\"3\">\n" +
        "<tr>\n" +
        " <td colspan=\"6\" class=\"title\">{0}</td>\n" +
        "</tr>\n" +
        "<tr>\n" +
        " <td class=\"header-left\"><small>{1}</small></td>\n" +
        " <td class=\"header-left\"><small>{2}</small></td>\n" +
        " <td class=\"header-center\"><small>{3}</small></td>\n" +
        " <td class=\"header-center\"><small>{4}</small></td>\n" +
        " <td class=\"header-left\"><small>{5}</small></td>\n" +
        " <td class=\"header-left\"><small>{6}</small></td>\n" +
        "</tr>\n";

    private static final String APPS_ROW_DETAILS_SECTION =
        "<tr>\n" +
        " <td class=\"row-left\" bgcolor=\"{6}\" rowspan=\"2\"><small>{0}</small></td>\n" +
        " <td class=\"row-left\" bgcolor=\"{6}\" rowspan=\"2\"><small>{1}</small></td>\n" +
        " <td class=\"row-left\" bgcolor=\"{6}\" rowspan=\"2\"><small>{2}</small></td>\n" +
        " <td class=\"row-center\" bgcolor=\"{6}\" rowspan=\"2\"><small>{3}</small></td>\n" +
        " <td class=\"row-center\" bgcolor=\"{6}\" rowspan=\"2\">" +
        "<small><a href=\"{4}\">{5}</a></small></td>\n";

    private static final String MANAGER_APP_ROW_BUTTON_SECTION =
        " <td class=\"row-left\" bgcolor=\"{13}\">\n" +
        "  <small>\n" +
        "  &nbsp;{1}&nbsp;\n" +
        "  &nbsp;{3}&nbsp;\n" +
        "  &nbsp;{5}&nbsp;\n" +
        "  &nbsp;{7}&nbsp;\n" +
        "  </small>\n" +
        " </td>\n" +
        "</tr><tr>\n" +
        " <td class=\"row-left\" bgcolor=\"{13}\">\n" +
        "  <form method=\"POST\" action=\"{8}\">\n" +
        "  <small>\n" +
        "  &nbsp;<input type=\"submit\" value=\"{9}\">&nbsp;{10}&nbsp;<input type=\"text\" name=\"idle\" size=\"5\" value=\"{11}\">&nbsp;{12}&nbsp;\n" +
        "  </small>\n" +
        "  </form>\n" +
        " </td>\n" +
        "</tr>\n";

    private static final String STARTED_DEPLOYED_APPS_ROW_BUTTON_SECTION =
        " <td class=\"row-left\" bgcolor=\"{13}\">\n" +
        "  &nbsp;<small>{1}</small>&nbsp;\n" +
        "  <form class=\"inline\" method=\"POST\" action=\"{2}\">" +
        "  <small><input type=\"submit\" value=\"{3}\"></small>" +
        "  </form>\n" +
        "  <form class=\"inline\" method=\"POST\" action=\"{4}\">" +
        "  <small><input type=\"submit\" value=\"{5}\"></small>" +
        "  </form>\n" +
        "  <form class=\"inline\" method=\"POST\" action=\"{6}\">" +
        "  <small><input type=\"submit\" value=\"{7}\"></small>" +
        "  </form>\n" +
        " </td>\n" +
        " </tr><tr>\n" +
        " <td class=\"row-left\" bgcolor=\"{13}\">\n" +
        "  <form method=\"POST\" action=\"{8}\">\n" +
        "  <small>\n" +
        "  &nbsp;<input type=\"submit\" value=\"{9}\">&nbsp;{10}&nbsp;<input type=\"text\" name=\"idle\" size=\"5\" value=\"{11}\">&nbsp;{12}&nbsp;\n" +
        "  </small>\n" +
        "  </form>\n" +
        " </td>\n" +
        "</tr>\n";

    private static final String STOPPED_DEPLOYED_APPS_ROW_BUTTON_SECTION =
        " <td class=\"row-left\" bgcolor=\"{13}\" rowspan=\"2\">\n" +
        "  <form class=\"inline\" method=\"POST\" action=\"{0}\">" +
        "  <small><input type=\"submit\" value=\"{1}\"></small>" +
        "  </form>\n" +
        "  &nbsp;<small>{3}</small>&nbsp;\n" +
        "  &nbsp;<small>{5}</small>&nbsp;\n" +
        "  <form class=\"inline\" method=\"POST\" action=\"{6}\">" +
        "  <small><input type=\"submit\" value=\"{7}\"></small>" +
        "  </form>\n" +
        " </td>\n" +
        "</tr>\n<tr></tr>\n";

    private static final String STARTED_NONDEPLOYED_APPS_ROW_BUTTON_SECTION =
        " <td class=\"row-left\" bgcolor=\"{13}\" rowspan=\"2\">\n" +
        "  &nbsp;<small>{1}</small>&nbsp;\n" +
        "  <form class=\"inline\" method=\"POST\" action=\"{2}\">" +
        "  <small><input type=\"submit\" value=\"{3}\"></small>" +
        "  </form>\n" +
        "  <form class=\"inline\" method=\"POST\" action=\"{4}\">" +
        "  <small><input type=\"submit\" value=\"{5}\"></small>" +
        "  </form>\n" +
        "  &nbsp;<small>{7}</small>&nbsp;\n" +
        " </td>\n" +
        "</tr>\n<tr></tr>\n";

    private static final String STOPPED_NONDEPLOYED_APPS_ROW_BUTTON_SECTION =
        " <td class=\"row-left\" bgcolor=\"{13}\" rowspan=\"2\">\n" +
        "  <form class=\"inline\" method=\"POST\" action=\"{0}\">" +
        "  <small><input type=\"submit\" value=\"{1}\"></small>" +
        "  </form>\n" +
        "  &nbsp;<small>{3}</small>&nbsp;\n" +
        "  &nbsp;<small>{5}</small>&nbsp;\n" +
        "  &nbsp;<small>{7}</small>&nbsp;\n" +
        " </td>\n" +
        "</tr>\n<tr></tr>\n";

    private static final String DEPLOY_SECTION =
        "</table>\n" +
        "<br>\n" +
        "<table border=\"1\" cellspacing=\"0\" cellpadding=\"3\">\n" +
        "<tr>\n" +
        " <td colspan=\"2\" class=\"title\">{0}</td>\n" +
        "</tr>\n" +
        "<tr>\n" +
        " <td colspan=\"2\" class=\"header-left\"><small>{1}</small></td>\n" +
        "</tr>\n" +
        "<tr>\n" +
        " <td colspan=\"2\">\n" +
        "<form method=\"post\" action=\"{2}\">\n" +
        "<table cellspacing=\"0\" cellpadding=\"3\">\n" +
        "<tr>\n" +
        " <td class=\"row-right\">\n" +
        "  <small>{3}</small>\n" +
        " </td>\n" +
        " <td class=\"row-left\">\n" +
        "  <input type=\"text\" name=\"deployPath\" size=\"20\">\n" +
        " </td>\n" +
        "</tr>\n" +
        "<tr>\n" +
        " <td class=\"row-right\">\n" +
        "  <small>{4}</small>\n" +
        " </td>\n" +
        " <td class=\"row-left\">\n" +
        "  <input type=\"text\" name=\"deployConfig\" size=\"20\">\n" +
        " </td>\n" +
        "</tr>\n" +
        "<tr>\n" +
        " <td class=\"row-right\">\n" +
        "  <small>{5}</small>\n" +
        " </td>\n" +
        " <td class=\"row-left\">\n" +
        "  <input type=\"text\" name=\"deployWar\" size=\"40\">\n" +
        " </td>\n" +
        "</tr>\n" +
        "<tr>\n" +
        " <td class=\"row-right\">\n" +
        "  &nbsp;\n" +
        " </td>\n" +
        " <td class=\"row-left\">\n" +
        "  <input type=\"submit\" value=\"{6}\">\n" +
        " </td>\n" +
        "</tr>\n" +
        "</table>\n" +
        "</form>\n" +
        "</td>\n" +
        "</tr>\n";

    private static final String UPLOAD_SECTION =
        "<tr>\n" +
        " <td colspan=\"2\" class=\"header-left\"><small>{0}</small></td>\n" +
        "</tr>\n" +
        "<tr>\n" +
        " <td colspan=\"2\">\n" +
        "<form method=\"post\" action=\"{1}\" " +
        "enctype=\"multipart/form-data\">\n" +
        "<table cellspacing=\"0\" cellpadding=\"3\">\n" +
        "<tr>\n" +
        " <td class=\"row-right\">\n" +
        "  <small>{2}</small>\n" +
        " </td>\n" +
        " <td class=\"row-left\">\n" +
        "  <input type=\"file\" name=\"deployWar\" size=\"40\">\n" +
        " </td>\n" +
        "</tr>\n" +
        "<tr>\n" +
        " <td class=\"row-right\">\n" +
        "  &nbsp;\n" +
        " </td>\n" +
        " <td class=\"row-left\">\n" +
        "  <input type=\"submit\" value=\"{3}\">\n" +
        " </td>\n" +
        "</tr>\n" +
        "</table>\n" +
        "</form>\n" +
        "</table>\n" +
        "<br>\n" +
        "\n";

    private static final String DIAGNOSTICS_SECTION =
        "<table border=\"1\" cellspacing=\"0\" cellpadding=\"3\">\n" +
        "<tr>\n" +
        " <td colspan=\"2\" class=\"title\">{0}</td>\n" +
        "</tr>\n" +
        "<tr>\n" +
        " <td colspan=\"2\" class=\"header-left\"><small>{1}</small></td>\n" +
        "</tr>\n" +
        "<tr>\n" +
        " <td colspan=\"2\">\n" +
        "<form method=\"post\" action=\"{2}\">\n" +
        "<table cellspacing=\"0\" cellpadding=\"3\">\n" +
        "<tr>\n" +
        " <td class=\"row-left\">\n" +
        "  <input type=\"submit\" value=\"{4}\">\n" +
        " </td>\n" +
        " <td class=\"row-left\">\n" +
        "  <small>{3}</small>\n" +
        " </td>\n" +
        "</tr>\n" +
        "</table>\n" +
        "</form>\n" +
        "</td>\n" +
        "</tr>\n" +
        "</table>\n" +
        "<br>";
}
