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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.DistributedManager;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.manager.util.SessionUtils;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.URLEncoder;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;

/**
 * Servlet that enables remote management of the web applications deployed within the same virtual host as this web
 * application is. Normally, this functionality will be protected by a security constraint in the web application
 * deployment descriptor. However, this requirement can be relaxed during testing.
 * <p>
 * The difference between the <code>ManagerServlet</code> and this Servlet is that this Servlet prints out an HTML
 * interface which makes it easier to administrate.
 * <p>
 * However if you use a software that parses the output of <code>ManagerServlet</code> you won't be able to upgrade to
 * this Servlet since the output are not in the same format ar from <code>ManagerServlet</code>
 *
 * @author Bip Thelin
 * @author Malcolm Edgar
 * @author Glenn L. Nielsen
 *
 * @see ManagerServlet
 */
public final class HTMLManagerServlet extends ManagerServlet {

    private static final long serialVersionUID = 1L;

    static final String APPLICATION_MESSAGE = "message";
    static final String APPLICATION_ERROR = "error";

    static final String sessionsListJspPath = "/WEB-INF/jsp/sessionsList.jsp";
    static final String sessionDetailJspPath = "/WEB-INF/jsp/sessionDetail.jsp";
    static final String connectorCiphersJspPath = "/WEB-INF/jsp/connectorCiphers.jsp";
    static final String connectorCertsJspPath = "/WEB-INF/jsp/connectorCerts.jsp";
    static final String connectorTrustedCertsJspPath = "/WEB-INF/jsp/connectorTrustedCerts.jsp";

    private boolean showProxySessions = false;
    private String htmlSubTitle = null;

    // --------------------------------------------------------- Public Methods

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        StringManager smClient = StringManager.getManager(Constants.Package, request.getLocales());

        // Identify the request parameters that we need
        // By obtaining the command from the pathInfo, per-command security can
        // be configured in web.xml
        String command = request.getPathInfo();

        String path = request.getParameter("path");
        ContextName cn = null;
        if (path != null) {
            cn = new ContextName(path, request.getParameter("version"));
        }

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
                doSessions(cn, request, response, smClient);
                return;
            } catch (Exception e) {
                log(sm.getString("htmlManagerServlet.error.sessions", cn), e);
                message = smClient.getString("managerServlet.exception", e.toString());
            }
        } else if (command.equals("/sslConnectorCiphers")) {
            sslConnectorCiphers(request, response, smClient);
        } else if (command.equals("/sslConnectorCerts")) {
            sslConnectorCerts(request, response, smClient);
        } else if (command.equals("/sslConnectorTrustedCerts")) {
            sslConnectorTrustedCerts(request, response, smClient);
        } else if (command.equals("/upload") || command.equals("/deploy") || command.equals("/reload") ||
                command.equals("/undeploy") || command.equals("/expire") || command.equals("/start") ||
                command.equals("/stop")) {
            message = smClient.getString("managerServlet.postCommand", command);
        } else {
            message = smClient.getString("managerServlet.unknownCommand", command);
        }

        list(request, response, message, smClient);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        StringManager smClient = StringManager.getManager(Constants.Package, request.getLocales());

        // Identify the request parameters that we need
        // By obtaining the command from the pathInfo, per-command security can
        // be configured in web.xml
        String command = request.getPathInfo();

        String path = request.getParameter("path");
        ContextName cn = null;
        if (path != null) {
            cn = new ContextName(path, request.getParameter("version"));
        }

        String deployPath = request.getParameter("deployPath");
        String deployWar = request.getParameter("deployWar");
        String deployConfig = request.getParameter("deployConfig");
        ContextName deployCn = null;
        if (deployPath != null && deployPath.length() > 0) {
            deployCn = new ContextName(deployPath, request.getParameter("deployVersion"));
        } else if (deployConfig != null && deployConfig.length() > 0) {
            deployCn = ContextName.extractFromPath(deployConfig);
        } else if (deployWar != null && deployWar.length() > 0) {
            deployCn = ContextName.extractFromPath(deployWar);
        }

        String tlsHostName = request.getParameter("tlsHostName");

        // Prepare our output writer to generate the response message
        response.setContentType("text/html; charset=" + Constants.CHARSET);

        String message = "";

        if (command == null || command.length() == 0) {
            // No command == list
            // List always displayed -> do nothing
        } else if (command.equals("/upload")) {
            message = upload(request, smClient);
        } else if (command.equals("/deploy")) {
            message = deployInternal(deployConfig, deployCn, deployWar, smClient);
        } else if (command.equals("/reload")) {
            message = reload(cn, smClient);
        } else if (command.equals("/undeploy")) {
            message = undeploy(cn, smClient);
        } else if (command.equals("/expire")) {
            message = expireSessions(cn, request, smClient);
        } else if (command.equals("/start")) {
            message = start(cn, smClient);
        } else if (command.equals("/stop")) {
            message = stop(cn, smClient);
        } else if (command.equals("/findleaks")) {
            message = findleaks(smClient);
        } else if (command.equals("/sslReload")) {
            message = sslReload(tlsHostName, smClient);
        } else {
            // Try GET
            doGet(request, response);
            return;
        }

        list(request, response, message, smClient);
    }


    protected String upload(HttpServletRequest request, StringManager smClient) {
        String message = "";

        try {
            while (true) {
                Part warPart = request.getPart("deployWar");
                if (warPart == null) {
                    message = smClient.getString("htmlManagerServlet.deployUploadNoFile");
                    break;
                }
                String filename = warPart.getSubmittedFileName();
                if (filename == null || !filename.toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                    message = smClient.getString("htmlManagerServlet.deployUploadNotWar", filename);
                    break;
                }
                // Get the filename if uploaded name includes a path
                if (filename.lastIndexOf('\\') >= 0) {
                    filename = filename.substring(filename.lastIndexOf('\\') + 1);
                }
                if (filename.lastIndexOf('/') >= 0) {
                    filename = filename.substring(filename.lastIndexOf('/') + 1);
                }

                // Identify the appBase of the owning Host of this Context
                // (if any)
                File file = new File(host.getAppBaseFile(), filename);
                if (file.exists()) {
                    message = smClient.getString("htmlManagerServlet.deployUploadWarExists", filename);
                    break;
                }

                ContextName cn = new ContextName(filename, true);
                String name = cn.getName();

                if (host.findChild(name) != null && !isDeployed(name)) {
                    message = smClient.getString("htmlManagerServlet.deployUploadInServerXml", filename);
                    break;
                }

                if (tryAddServiced(name)) {
                    try {
                        warPart.write(file.getAbsolutePath());
                    } finally {
                        removeServiced(name);
                    }
                    // Perform new deployment
                    check(name);
                } else {
                    message = smClient.getString("managerServlet.inService", name);
                }
                break;
            }
        } catch (Exception e) {
            message = smClient.getString("htmlManagerServlet.deployUploadFail", e.getMessage());
            log(message, e);
        }
        return message;
    }

    /**
     * Deploy an application for the specified path from the specified web application archive.
     *
     * @param config   URL of the context configuration file to be deployed
     * @param cn       Name of the application to be deployed
     * @param war      URL of the web application archive to be deployed
     * @param smClient internationalized strings
     *
     * @return message String
     */
    protected String deployInternal(String config, ContextName cn, String war, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.deploy(printWriter, config, cn, war, false, smClient);

        return stringWriter.toString();
    }

    /**
     * Render an HTML list of the currently active Contexts in our virtual host, and memory and server status
     * information.
     *
     * @param request  The request
     * @param response The response
     * @param message  a message to display
     * @param smClient internationalized strings
     *
     * @throws IOException an IO error occurred
     */
    protected void list(HttpServletRequest request, HttpServletResponse response, String message,
            StringManager smClient) throws IOException {

        if (debug >= 1) {
            log("list: Listing contexts for virtual host '" + host.getName() + "'");
        }

        PrintWriter writer = response.getWriter();

        Object[] args = new Object[2];
        args[0] = getServletContext().getContextPath();
        args[1] = smClient.getString("htmlManagerServlet.title");
        if (htmlSubTitle != null) {
            args[1] += "</font><br/><font size=\"+1\">" + htmlSubTitle;
        }

        // HTML Header Section
        writer.print(MessageFormat.format(Constants.HTML_HEADER_SECTION, args));

        // Body Header Section
        writer.print(MessageFormat.format(Constants.BODY_HEADER_SECTION, args));

        // Message Section
        args = new Object[3];
        args[0] = smClient.getString("htmlManagerServlet.messageLabel");
        if (message == null || message.length() == 0) {
            args[1] = "OK";
        } else {
            args[1] = Escape.htmlElementContent(message);
        }
        writer.print(MessageFormat.format(Constants.MESSAGE_SECTION, args));

        // Manager Section
        args = new Object[9];
        args[0] = smClient.getString("htmlManagerServlet.manager");
        args[1] = response.encodeURL(getServletContext().getContextPath() + "/html/list");
        args[2] = smClient.getString("htmlManagerServlet.list");
        args[3] = // External link
                getServletContext().getContextPath() + "/" +
                        smClient.getString("htmlManagerServlet.helpHtmlManagerFile");
        args[4] = smClient.getString("htmlManagerServlet.helpHtmlManager");
        args[5] = // External link
                getServletContext().getContextPath() + "/" + smClient.getString("htmlManagerServlet.helpManagerFile");
        args[6] = smClient.getString("htmlManagerServlet.helpManager");
        args[7] = response.encodeURL(getServletContext().getContextPath() + "/status");
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
        for (int i = 0; i < children.length; i++) {
            contextNames[i] = children[i].getName();
        }

        Arrays.sort(contextNames);

        String appsStart = smClient.getString("htmlManagerServlet.appsStart");
        String appsStop = smClient.getString("htmlManagerServlet.appsStop");
        String appsReload = smClient.getString("htmlManagerServlet.appsReload");
        String appsUndeploy = smClient.getString("htmlManagerServlet.appsUndeploy");
        String appsExpire = smClient.getString("htmlManagerServlet.appsExpire");
        String noVersion = "<i>" + smClient.getString("htmlManagerServlet.noVersion") + "</i>";

        boolean isHighlighted = true;
        boolean isDeployed = true;
        String highlightColor = null;

        for (String contextName : contextNames) {
            Context ctxt = (Context) host.findChild(contextName);

            if (ctxt != null) {
                // Bugzilla 34818, alternating row colors
                isHighlighted = !isHighlighted;
                if (isHighlighted) {
                    highlightColor = "#C3F3C3";
                } else {
                    highlightColor = "#FFFFFF";
                }

                String contextPath = ctxt.getPath();
                String displayPath = contextPath;
                if (displayPath.equals("")) {
                    displayPath = "/";
                }

                StringBuilder tmp = new StringBuilder();
                tmp.append("path=");
                tmp.append(URLEncoder.DEFAULT.encode(displayPath, StandardCharsets.UTF_8));
                final String webappVersion = ctxt.getWebappVersion();
                if (webappVersion != null && webappVersion.length() > 0) {
                    tmp.append("&version=");
                    tmp.append(URLEncoder.DEFAULT.encode(webappVersion, StandardCharsets.UTF_8));
                }
                String pathVersion = tmp.toString();

                try {
                    isDeployed = isDeployed(contextName);
                } catch (Exception e) {
                    // Assume false on failure for safety
                    isDeployed = false;
                }

                args = new Object[7];
                args[0] = // External link
                        "<a href=\"" + URLEncoder.DEFAULT.encode(contextPath + "/", StandardCharsets.UTF_8) + "\" " +
                                Constants.REL_EXTERNAL + ">" + Escape.htmlElementContent(displayPath) + "</a>";
                if (webappVersion == null || webappVersion.isEmpty()) {
                    args[1] = noVersion;
                } else {
                    args[1] = Escape.htmlElementContent(webappVersion);
                }
                if (ctxt.getDisplayName() == null) {
                    args[2] = "&nbsp;";
                } else {
                    args[2] = Escape.htmlElementContent(ctxt.getDisplayName());
                }
                args[3] = Boolean.valueOf(ctxt.getState().isAvailable());
                args[4] = Escape.htmlElementContent(
                        response.encodeURL(getServletContext().getContextPath() + "/html/sessions?" + pathVersion));
                Manager manager = ctxt.getManager();
                if (manager instanceof DistributedManager && showProxySessions) {
                    args[5] = Integer.valueOf(((DistributedManager) manager).getActiveSessionsFull());
                } else if (manager != null) {
                    args[5] = Integer.valueOf(manager.getActiveSessions());
                } else {
                    args[5] = Integer.valueOf(0);
                }

                args[6] = highlightColor;

                writer.print(MessageFormat.format(APPS_ROW_DETAILS_SECTION, args));

                args = new Object[14];
                args[0] = Escape.htmlElementContent(
                        response.encodeURL(request.getContextPath() + "/html/start?" + pathVersion));
                args[1] = appsStart;
                args[2] = Escape
                        .htmlElementContent(response.encodeURL(request.getContextPath() + "/html/stop?" + pathVersion));
                args[3] = appsStop;
                args[4] = Escape.htmlElementContent(
                        response.encodeURL(request.getContextPath() + "/html/reload?" + pathVersion));
                args[5] = appsReload;
                args[6] = Escape.htmlElementContent(
                        response.encodeURL(request.getContextPath() + "/html/undeploy?" + pathVersion));
                args[7] = appsUndeploy;
                args[8] = Escape.htmlElementContent(
                        response.encodeURL(request.getContextPath() + "/html/expire?" + pathVersion));
                args[9] = appsExpire;
                args[10] = smClient.getString("htmlManagerServlet.expire.explain");
                if (manager == null) {
                    args[11] = smClient.getString("htmlManagerServlet.noManager");
                } else {
                    args[11] = Integer.valueOf(ctxt.getSessionTimeout());
                }
                args[12] = smClient.getString("htmlManagerServlet.expire.unit");
                args[13] = highlightColor;

                if (ctxt.getName().equals(this.context.getName())) {
                    writer.print(MessageFormat.format(MANAGER_APP_ROW_BUTTON_SECTION, args));
                } else if (ctxt.getState().isAvailable() && isDeployed) {
                    writer.print(MessageFormat.format(STARTED_DEPLOYED_APPS_ROW_BUTTON_SECTION, args));
                } else if (ctxt.getState().isAvailable() && !isDeployed) {
                    writer.print(MessageFormat.format(STARTED_NONDEPLOYED_APPS_ROW_BUTTON_SECTION, args));
                } else if (!ctxt.getState().isAvailable() && isDeployed) {
                    writer.print(MessageFormat.format(STOPPED_DEPLOYED_APPS_ROW_BUTTON_SECTION, args));
                } else {
                    writer.print(MessageFormat.format(STOPPED_NONDEPLOYED_APPS_ROW_BUTTON_SECTION, args));
                }

            }
        }

        // Deploy Section
        args = new Object[8];
        args[0] = smClient.getString("htmlManagerServlet.deployTitle");
        args[1] = smClient.getString("htmlManagerServlet.deployServer");
        args[2] = response.encodeURL(getServletContext().getContextPath() + "/html/deploy");
        args[3] = smClient.getString("htmlManagerServlet.deployPath");
        args[4] = smClient.getString("htmlManagerServlet.deployVersion");
        args[5] = smClient.getString("htmlManagerServlet.deployConfig");
        args[6] = smClient.getString("htmlManagerServlet.deployWar");
        args[7] = smClient.getString("htmlManagerServlet.deployButton");
        writer.print(MessageFormat.format(DEPLOY_SECTION, args));

        args = new Object[4];
        args[0] = smClient.getString("htmlManagerServlet.deployUpload");
        args[1] = response.encodeURL(getServletContext().getContextPath() + "/html/upload");
        args[2] = smClient.getString("htmlManagerServlet.deployUploadFile");
        args[3] = smClient.getString("htmlManagerServlet.deployButton");
        writer.print(MessageFormat.format(UPLOAD_SECTION, args));

        // Config section
        args = new Object[5];
        args[0] = smClient.getString("htmlManagerServlet.configTitle");
        args[1] = smClient.getString("htmlManagerServlet.configSslReloadTitle");
        args[2] = response.encodeURL(getServletContext().getContextPath() + "/html/sslReload");
        args[3] = smClient.getString("htmlManagerServlet.configSslHostName");
        args[4] = smClient.getString("htmlManagerServlet.configReloadButton");
        writer.print(MessageFormat.format(CONFIG_SECTION, args));

        // Diagnostics section
        args = new Object[15];
        args[0] = smClient.getString("htmlManagerServlet.diagnosticsTitle");
        args[1] = smClient.getString("htmlManagerServlet.diagnosticsLeak");
        args[2] = response.encodeURL(getServletContext().getContextPath() + "/html/findleaks");
        args[3] = smClient.getString("htmlManagerServlet.diagnosticsLeakWarning");
        args[4] = smClient.getString("htmlManagerServlet.diagnosticsLeakButton");
        args[5] = smClient.getString("htmlManagerServlet.diagnosticsSsl");
        args[6] = response.encodeURL(getServletContext().getContextPath() + "/html/sslConnectorCiphers");
        args[7] = smClient.getString("htmlManagerServlet.diagnosticsSslConnectorCipherButton");
        args[8] = smClient.getString("htmlManagerServlet.diagnosticsSslConnectorCipherText");
        args[9] = response.encodeURL(getServletContext().getContextPath() + "/html/sslConnectorCerts");
        args[10] = smClient.getString("htmlManagerServlet.diagnosticsSslConnectorCertsButton");
        args[11] = smClient.getString("htmlManagerServlet.diagnosticsSslConnectorCertsText");
        args[12] = response.encodeURL(getServletContext().getContextPath() + "/html/sslConnectorTrustedCerts");
        args[13] = smClient.getString("htmlManagerServlet.diagnosticsSslConnectorTrustedCertsButton");
        args[14] = smClient.getString("htmlManagerServlet.diagnosticsSslConnectorTrustedCertsText");
        writer.print(MessageFormat.format(DIAGNOSTICS_SECTION, args));

        // Server Header Section
        args = new Object[9];
        args[0] = smClient.getString("htmlManagerServlet.serverTitle");
        args[1] = smClient.getString("htmlManagerServlet.serverVersion");
        args[2] = smClient.getString("htmlManagerServlet.serverJVMVersion");
        args[3] = smClient.getString("htmlManagerServlet.serverJVMVendor");
        args[4] = smClient.getString("htmlManagerServlet.serverOSName");
        args[5] = smClient.getString("htmlManagerServlet.serverOSVersion");
        args[6] = smClient.getString("htmlManagerServlet.serverOSArch");
        args[7] = smClient.getString("htmlManagerServlet.serverHostname");
        args[8] = smClient.getString("htmlManagerServlet.serverIPAddress");
        writer.print(MessageFormat.format(Constants.SERVER_HEADER_SECTION, args));

        // Server Row Section
        args = new Object[8];
        args[0] = ServerInfo.getServerInfo();
        args[1] = System.getProperty("java.runtime.version");
        args[2] = System.getProperty("java.vm.vendor");
        args[3] = System.getProperty("os.name");
        args[4] = System.getProperty("os.version");
        args[5] = System.getProperty("os.arch");
        try {
            InetAddress address = InetAddress.getLocalHost();
            args[6] = address.getHostName();
            args[7] = address.getHostAddress();
        } catch (UnknownHostException e) {
            args[6] = "-";
            args[7] = "-";
        }
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
     * @see ManagerServlet#reload(PrintWriter, ContextName, StringManager)
     *
     * @param cn       Name of the application to be restarted
     * @param smClient StringManager for the client's locale
     *
     * @return message String
     */
    protected String reload(ContextName cn, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.reload(printWriter, cn, smClient);

        return stringWriter.toString();
    }

    /**
     * Undeploy the web application at the specified context path.
     *
     * @see ManagerServlet#undeploy(PrintWriter, ContextName, StringManager)
     *
     * @param cn       Name of the application to be undeployed
     * @param smClient StringManager for the client's locale
     *
     * @return message String
     */
    protected String undeploy(ContextName cn, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.undeploy(printWriter, cn, smClient);

        return stringWriter.toString();
    }

    /**
     * Display session information and invoke list.
     *
     * @see ManagerServlet#sessions(PrintWriter, ContextName, int, StringManager)
     *
     * @param cn       Name of the application to list session information
     * @param idle     Expire all sessions with idle time &ge; idle for this context
     * @param smClient StringManager for the client's locale
     *
     * @return message String
     */
    protected String sessions(ContextName cn, int idle, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.sessions(printWriter, cn, idle, smClient);

        return stringWriter.toString();
    }

    /**
     * Start the web application at the specified context path.
     *
     * @see ManagerServlet#start(PrintWriter, ContextName, StringManager)
     *
     * @param cn       Name of the application to be started
     * @param smClient StringManager for the client's locale
     *
     * @return message String
     */
    protected String start(ContextName cn, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.start(printWriter, cn, smClient);

        return stringWriter.toString();
    }

    /**
     * Stop the web application at the specified context path.
     *
     * @see ManagerServlet#stop(PrintWriter, ContextName, StringManager)
     *
     * @param cn       Name of the application to be stopped
     * @param smClient StringManager for the client's locale
     *
     * @return message String
     */
    protected String stop(ContextName cn, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.stop(printWriter, cn, smClient);

        return stringWriter.toString();
    }

    /**
     * Find potential memory leaks caused by web application reload.
     *
     * @see ManagerServlet#findleaks(boolean, PrintWriter, StringManager)
     *
     * @param smClient StringManager for the client's locale
     *
     * @return message String
     */
    protected String findleaks(StringManager smClient) {

        StringBuilder msg = new StringBuilder();

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.findleaks(false, printWriter, smClient);

        String writerText = stringWriter.toString();

        if (writerText.length() > 0) {
            if (!writerText.startsWith("FAIL -")) {
                msg.append(smClient.getString("htmlManagerServlet.findleaksList"));
            }
            msg.append(writerText);
        } else {
            msg.append(smClient.getString("htmlManagerServlet.findleaksNone"));
        }

        return msg.toString();
    }


    protected String sslReload(String tlsHostName, StringManager smClient) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.sslReload(printWriter, tlsHostName, smClient);

        return stringWriter.toString();
    }


    protected void sslConnectorCiphers(HttpServletRequest request, HttpServletResponse response, StringManager smClient)
            throws ServletException, IOException {
        request.setAttribute("cipherList", getConnectorCiphers(smClient));
        getServletContext().getRequestDispatcher(connectorCiphersJspPath).forward(request, response);
    }


    protected void sslConnectorCerts(HttpServletRequest request, HttpServletResponse response, StringManager smClient)
            throws ServletException, IOException {
        request.setAttribute("certList", getConnectorCerts(smClient));
        getServletContext().getRequestDispatcher(connectorCertsJspPath).forward(request, response);
    }


    protected void sslConnectorTrustedCerts(HttpServletRequest request, HttpServletResponse response,
            StringManager smClient) throws ServletException, IOException {
        request.setAttribute("trustedCertList", getConnectorTrustedCerts(smClient));
        getServletContext().getRequestDispatcher(connectorTrustedCertsJspPath).forward(request, response);
    }


    @Override
    public String getServletInfo() {
        return "HTMLManagerServlet, Copyright (c) 1999-2024, The Apache Software Foundation";
    }

    @Override
    public void init() throws ServletException {
        super.init();

        // Set our properties from the initialization parameters
        String value = null;
        value = getServletConfig().getInitParameter("showProxySessions");
        showProxySessions = Boolean.parseBoolean(value);

        htmlSubTitle = getServletConfig().getInitParameter("htmlSubTitle");
    }

    // ------------------------------------------------ Sessions administration

    /**
     * Extract the expiration request parameter
     *
     * @param cn       Name of the application from which to expire sessions
     * @param req      The Servlet request
     * @param smClient StringManager for the client's locale
     *
     * @return message string
     */
    protected String expireSessions(ContextName cn, HttpServletRequest req, StringManager smClient) {
        int idle = -1;
        String idleParam = req.getParameter("idle");
        if (idleParam != null) {
            try {
                idle = Integer.parseInt(idleParam);
            } catch (NumberFormatException e) {
                log(sm.getString("managerServlet.error.idleParam", idleParam));
            }
        }
        return sessions(cn, idle, smClient);
    }

    /**
     * Handle session operations.
     *
     * @param cn       Name of the application for the sessions operation
     * @param req      The Servlet request
     * @param resp     The Servlet response
     * @param smClient StringManager for the client's locale
     *
     * @throws ServletException Propagated Servlet error
     * @throws IOException      An IO error occurred
     */
    protected void doSessions(ContextName cn, HttpServletRequest req, HttpServletResponse resp, StringManager smClient)
            throws ServletException, IOException {
        req.setAttribute("path", cn.getPath());
        req.setAttribute("version", cn.getVersion());
        String action = req.getParameter("action");
        if (debug >= 1) {
            log("sessions: Session action '" + action + "' for web application '" + cn.getDisplayName() + "'");
        }
        if ("sessionDetail".equals(action)) {
            String sessionId = req.getParameter("sessionId");
            displaySessionDetailPage(req, resp, cn, sessionId, smClient);
            return;
        } else if ("invalidateSessions".equals(action)) {
            String[] sessionIds = req.getParameterValues("sessionIds");
            int i = invalidateSessions(cn, sessionIds, smClient);
            req.setAttribute(APPLICATION_MESSAGE, "" + i + " sessions invalidated.");
        } else if ("removeSessionAttribute".equals(action)) {
            String sessionId = req.getParameter("sessionId");
            String name = req.getParameter("attributeName");
            boolean removed = removeSessionAttribute(cn, sessionId, name, smClient);
            String outMessage = removed ? "Session attribute '" + name + "' removed." :
                    "Session did not contain any attribute named '" + name + "'";
            req.setAttribute(APPLICATION_MESSAGE, outMessage);
            displaySessionDetailPage(req, resp, cn, sessionId, smClient);
            return;
        } // else
        displaySessionsListPage(cn, req, resp, smClient);
    }

    protected List<Session> getSessionsForName(ContextName cn, StringManager smClient) {
        if (cn == null || !(cn.getPath().startsWith("/") || cn.getPath().equals(""))) {
            String path = null;
            if (cn != null) {
                path = cn.getPath();
            }
            throw new IllegalArgumentException(
                    smClient.getString("managerServlet.invalidPath", Escape.htmlElementContent(path)));
        }

        Context ctxt = (Context) host.findChild(cn.getName());
        if (null == ctxt) {
            throw new IllegalArgumentException(
                    smClient.getString("managerServlet.noContext", Escape.htmlElementContent(cn.getDisplayName())));
        }
        Manager manager = ctxt.getManager();
        List<Session> sessions = new ArrayList<>(Arrays.asList(manager.findSessions()));
        if (manager instanceof DistributedManager && showProxySessions) {
            // Add dummy proxy sessions
            Set<String> sessionIds = ((DistributedManager) manager).getSessionIdsFull();
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

    protected Session getSessionForNameAndId(ContextName cn, String id, StringManager smClient) {

        List<Session> sessions = getSessionsForName(cn, smClient);
        if (sessions.isEmpty()) {
            return null;
        }
        for (Session session : sessions) {
            if (session.getId().equals(id)) {
                return session;
            }
        }
        return null;
    }

    /**
     * List session.
     *
     * @param cn       Name of the application for which the sessions will be listed
     * @param req      The Servlet request
     * @param resp     The Servlet response
     * @param smClient StringManager for the client's locale
     *
     * @throws ServletException Propagated Servlet error
     * @throws IOException      An IO error occurred
     */
    protected void displaySessionsListPage(ContextName cn, HttpServletRequest req, HttpServletResponse resp,
            StringManager smClient) throws ServletException, IOException {
        List<Session> sessions = getSessionsForName(cn, smClient);
        String sortBy = req.getParameter("sort");
        String orderBy = null;
        if (null != sortBy && !"".equals(sortBy.trim())) {
            Comparator<Session> comparator = getComparator(sortBy);
            if (comparator != null) {
                orderBy = req.getParameter("order");
                if ("DESC".equalsIgnoreCase(orderBy)) {
                    comparator = Collections.reverseOrder(comparator);
                    orderBy = "ASC";
                } else {
                    orderBy = "DESC";
                }
                try {
                    sessions.sort(comparator);
                } catch (IllegalStateException ise) {
                    // at least 1 of the sessions is invalidated
                    req.setAttribute(APPLICATION_ERROR, "Can't sort session list: one session is invalidated");
                }
            } else {
                log(sm.getString("htmlManagerServlet.error.sortOrder", sortBy));
            }
        }
        // keep sort order
        req.setAttribute("sort", sortBy);
        req.setAttribute("order", orderBy);
        req.setAttribute("activeSessions", sessions);
        // strong>NOTE</strong> - This header will be overridden
        // automatically if a <code>RequestDispatcher.forward()</code> call is
        // ultimately invoked.
        resp.setHeader("Pragma", "No-cache"); // HTTP 1.0
        resp.setHeader("Cache-Control", "no-cache,no-store,max-age=0"); // HTTP 1.1
        resp.setDateHeader("Expires", 0); // 0 means now
        getServletContext().getRequestDispatcher(sessionsListJspPath).include(req, resp);
    }

    /**
     * Display session details.
     *
     * @param req       The Servlet request
     * @param resp      The Servlet response
     * @param cn        Name of the application for which the sessions will be listed
     * @param sessionId the session id
     * @param smClient  StringManager for the client's locale
     *
     * @throws ServletException Propagated Servlet error
     * @throws IOException      An IO error occurred
     */
    protected void displaySessionDetailPage(HttpServletRequest req, HttpServletResponse resp, ContextName cn,
            String sessionId, StringManager smClient) throws ServletException, IOException {
        Session session = getSessionForNameAndId(cn, sessionId, smClient);
        // strong>NOTE</strong> - This header will be overridden
        // automatically if a <code>RequestDispatcher.forward()</code> call is
        // ultimately invoked.
        resp.setHeader("Pragma", "No-cache"); // HTTP 1.0
        resp.setHeader("Cache-Control", "no-cache,no-store,max-age=0"); // HTTP 1.1
        resp.setDateHeader("Expires", 0); // 0 means now
        req.setAttribute("currentSession", session);
        getServletContext().getRequestDispatcher(resp.encodeURL(sessionDetailJspPath)).include(req, resp);
    }

    /**
     * Invalidate specified sessions.
     *
     * @param cn         Name of the application for which sessions are to be invalidated
     * @param sessionIds the session ids of the sessions
     * @param smClient   StringManager for the client's locale
     *
     * @return number of invalidated sessions
     */
    protected int invalidateSessions(ContextName cn, String[] sessionIds, StringManager smClient) {
        if (null == sessionIds) {
            return 0;
        }
        int nbAffectedSessions = 0;
        for (String sessionId : sessionIds) {
            Session session = getSessionForNameAndId(cn, sessionId, smClient);
            if (null == session) {
                // Shouldn't happen, but let's play nice...
                if (debug >= 1) {
                    log("Cannot invalidate null session " + sessionId);
                }
                continue;
            }
            try {
                session.getSession().invalidate();
                ++nbAffectedSessions;
                if (debug >= 1) {
                    log("Invalidating session id " + sessionId);
                }
            } catch (IllegalStateException ise) {
                if (debug >= 1) {
                    log("Cannot invalidate already invalidated session id " + sessionId);
                }
            }
        }
        return nbAffectedSessions;
    }

    /**
     * Removes an attribute from an HttpSession
     *
     * @param cn            Name of the application hosting the session from which the attribute is to be removed
     * @param sessionId     the session id
     * @param attributeName the attribute name
     * @param smClient      StringManager for the client's locale
     *
     * @return true if there was an attribute removed, false otherwise
     */
    protected boolean removeSessionAttribute(ContextName cn, String sessionId, String attributeName,
            StringManager smClient) {
        Session session = getSessionForNameAndId(cn, sessionId, smClient);
        if (null == session) {
            // Shouldn't happen, but let's play nice...
            if (debug >= 1) {
                log("Cannot remove attribute '" + attributeName + "' for null session " + sessionId);
            }
            return false;
        }
        HttpSession httpSession = session.getSession();
        boolean wasPresent = null != httpSession.getAttribute(attributeName);
        try {
            httpSession.removeAttribute(attributeName);
        } catch (IllegalStateException ise) {
            if (debug >= 1) {
                log("Cannot remote attribute '" + attributeName + "' for invalidated session id " + sessionId);
            }
        }
        return wasPresent;
    }

    protected Comparator<Session> getComparator(String sortBy) {
        Comparator<Session> comparator = null;
        if ("CreationTime".equalsIgnoreCase(sortBy)) {
            return Comparator.comparingLong(Session::getCreationTime);

        } else if ("id".equalsIgnoreCase(sortBy)) {
            return comparingNullable(Session::getId);

        } else if ("LastAccessedTime".equalsIgnoreCase(sortBy)) {
            return Comparator.comparingLong(Session::getLastAccessedTime);

        } else if ("MaxInactiveInterval".equalsIgnoreCase(sortBy)) {
            return Comparator.comparingInt(Session::getMaxInactiveInterval);

        } else if ("new".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(s -> Boolean.valueOf(s.getSession().isNew()));

        } else if ("locale".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(JspHelper::guessDisplayLocaleFromSession);

        } else if ("user".equalsIgnoreCase(sortBy)) {
            return comparingNullable(JspHelper::guessDisplayUserFromSession);

        } else if ("UsedTime".equalsIgnoreCase(sortBy)) {
            return Comparator.comparingLong(SessionUtils::getUsedTimeForSession);

        } else if ("InactiveTime".equalsIgnoreCase(sortBy)) {
            return Comparator.comparingLong(SessionUtils::getInactiveTimeForSession);

        } else if ("TTL".equalsIgnoreCase(sortBy)) {
            return Comparator.comparingLong(SessionUtils::getTTLForSession);

        }
        return comparator;
    }


    /*
     * Like Comparator.comparing() but allows objects being compared to be null. null values are ordered before all
     * other values.
     */
    private static <U extends Comparable<? super U>> Comparator<Session> comparingNullable(
            Function<Session,? extends U> keyExtractor) {
        return (s1, s2) -> {
            U c1 = keyExtractor.apply(s1);
            U c2 = keyExtractor.apply(s2);
            return c1 == null ? c2 == null ? 0 : -1 : c2 == null ? 1 : c1.compareTo(c2);
        };
    }


    // ------------------------------------------------------ Private Constants

    // These HTML sections are broken in relatively small sections, because of
    // limited number of substitutions MessageFormat can process
    // (maximum of 10).

    //@formatter:off
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
            "  &nbsp;&nbsp;<small><input type=\"submit\" value=\"{7}\"></small>" +
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
            " <td class=\"row-left\" bgcolor=\"{13}\">\n" +
            "  &nbsp;<small>{1}</small>&nbsp;\n" +
            "  <form class=\"inline\" method=\"POST\" action=\"{2}\">" +
            "  <small><input type=\"submit\" value=\"{3}\"></small>" +
            "  </form>\n" +
            "  <form class=\"inline\" method=\"POST\" action=\"{4}\">" +
            "  <small><input type=\"submit\" value=\"{5}\"></small>" +
            "  </form>\n" +
            "  &nbsp;<small>{7}</small>&nbsp;\n" +
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
            "  <input type=\"text\" name=\"deployVersion\" size=\"20\">\n" +
            " </td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td class=\"row-right\">\n" +
            "  <small>{5}</small>\n" +
            " </td>\n" +
            " <td class=\"row-left\">\n" +
            "  <input type=\"text\" name=\"deployConfig\" size=\"20\">\n" +
            " </td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td class=\"row-right\">\n" +
            "  <small>{6}</small>\n" +
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
            "  <input type=\"submit\" value=\"{7}\">\n" +
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
            "</td>\n" +
            "</tr>\n" +
            "</table>\n" +
            "<br>\n" +
            "\n";

    private static final String CONFIG_SECTION =
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
            "  <input type=\"text\" name=\"tlsHostName\" size=\"20\">\n" +
            " </td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td class=\"row-right\">\n" +
            "  &nbsp;\n" +
            " </td>\n" +
            " <td class=\"row-left\">\n" +
            "  <input type=\"submit\" value=\"{4}\">\n" +
            " </td>\n" +
            "</tr>\n" +
            "</table>\n" +
            "</form>\n" +
            "</td>\n" +
            "</tr>\n" +
            "</table>\n" +
            "<br>";

    private static final String DIAGNOSTICS_SECTION =
            "<table border=\"1\" cellspacing=\"0\" cellpadding=\"3\">\n" +
            "<tr>\n" +
            " <td colspan=\"2\" class=\"title\">{0}</td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td colspan=\"2\" class=\"header-left\"><small>{1}</small></td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td class=\"row-left\">\n" +
            "  <form method=\"post\" action=\"{2}\">\n" +
            "   <input type=\"submit\" value=\"{4}\">\n" +
            "  </form>\n" +
            " </td>\n" +
            " <td class=\"row-left\">\n" +
            "  <small>{3}</small>\n" +
            " </td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td colspan=\"2\" class=\"header-left\"><small>{5}</small></td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td class=\"row-left\">\n" +
            "  <form method=\"post\" action=\"{6}\">\n" +
            "   <input type=\"submit\" value=\"{7}\">\n" +
            "  </form>\n" +
            " </td>\n" +
            " <td class=\"row-left\">\n" +
            "  <small>{8}</small>\n" +
            " </td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td class=\"row-left\">\n" +
            "  <form method=\"post\" action=\"{9}\">\n" +
            "   <input type=\"submit\" value=\"{10}\">\n" +
            "  </form>\n" +
            " </td>\n" +
            " <td class=\"row-left\">\n" +
            "  <small>{11}</small>\n" +
            " </td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td class=\"row-left\">\n" +
            "  <form method=\"post\" action=\"{12}\">\n" +
            "   <input type=\"submit\" value=\"{13}\">\n" +
            "  </form>\n" +
            " </td>\n" +
            " <td class=\"row-left\">\n" +
            "  <small>{14}</small>\n" +
            " </td>\n" +
            "</tr>\n" +
            "</table>\n" +
            "<br>";
    //@formatter:on
}
