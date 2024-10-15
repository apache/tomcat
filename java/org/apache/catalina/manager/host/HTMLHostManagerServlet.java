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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Host;
import org.apache.catalina.util.ServerInfo;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;

/**
 * Servlet that enables remote management of the virtual hosts deployed on the server. Normally, this functionality will
 * be protected by a security constraint in the web application deployment descriptor. However, this requirement can be
 * relaxed during testing.
 * <p>
 * The difference between the <code>HostManagerServlet</code> and this Servlet is that this Servlet prints out an HTML
 * interface which makes it easier to administrate.
 * <p>
 * However if you use a software that parses the output of <code>HostManagerServlet</code> you won't be able to upgrade
 * to this Servlet since the output are not in the same format as from <code>HostManagerServlet</code>
 *
 * @author Bip Thelin
 * @author Malcolm Edgar
 * @author Glenn L. Nielsen
 * @author Peter Rossbach
 *
 * @see org.apache.catalina.manager.ManagerServlet
 */
public final class HTMLHostManagerServlet extends HostManagerServlet {

    private static final long serialVersionUID = 1L;


    // --------------------------------------------------------- Public Methods


    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        StringManager smClient = StringManager.getManager(Constants.Package, request.getLocales());

        // Identify the request parameters that we need
        String command = request.getPathInfo();

        // Prepare our output writer to generate the response message
        response.setContentType("text/html; charset=" + Constants.CHARSET);

        String message = "";
        // Process the requested command
        if (command == null) {
            // No command == list
        } else if (command.equals("/list")) {
            // Nothing to do - always generate list
        } else if (command.equals("/add") || command.equals("/remove") || command.equals("/start") ||
                command.equals("/stop") || command.equals("/persist")) {
            message = smClient.getString("hostManagerServlet.postCommand", command);
        } else {
            message = smClient.getString("hostManagerServlet.unknownCommand", command);
        }

        list(request, response, message, smClient);
    }


    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        StringManager smClient = StringManager.getManager(Constants.Package, request.getLocales());

        // Identify the request parameters that we need
        String command = request.getPathInfo();

        String name = request.getParameter("name");

        // Prepare our output writer to generate the response message
        response.setContentType("text/html; charset=" + Constants.CHARSET);

        String message = "";

        // Process the requested command
        if (command == null) {
            // No command == list
        } else if (command.equals("/add")) {
            message = add(request, name, smClient);
        } else if (command.equals("/remove")) {
            message = remove(name, smClient);
        } else if (command.equals("/start")) {
            message = start(name, smClient);
        } else if (command.equals("/stop")) {
            message = stop(name, smClient);
        } else if (command.equals("/persist")) {
            message = persist(smClient);
        } else {
            // Try GET
            doGet(request, response);
        }

        list(request, response, message, smClient);
    }


    /**
     * Add a host using the specified parameters.
     *
     * @param request  The Servlet request
     * @param name     Host name
     * @param smClient StringManager for the client's locale
     *
     * @return output
     */
    protected String add(HttpServletRequest request, String name, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.add(request, printWriter, name, true, smClient);

        return stringWriter.toString();
    }


    /**
     * Remove the specified host.
     *
     * @param name     Host name
     * @param smClient StringManager for the client's locale
     *
     * @return output
     */
    protected String remove(String name, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.remove(printWriter, name, smClient);

        return stringWriter.toString();
    }


    /**
     * Start the host with the specified name.
     *
     * @param name     Host name
     * @param smClient StringManager for the client's locale
     *
     * @return output
     */
    protected String start(String name, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.start(printWriter, name, smClient);

        return stringWriter.toString();
    }


    /**
     * Stop the host with the specified name.
     *
     * @param name     Host name
     * @param smClient StringManager for the client's locale
     *
     * @return output
     */
    protected String stop(String name, StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.stop(printWriter, name, smClient);

        return stringWriter.toString();
    }


    /**
     * Persist the current configuration to server.xml.
     *
     * @param smClient i18n resources localized for the client
     *
     * @return output
     */
    protected String persist(StringManager smClient) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        super.persist(printWriter, smClient);

        return stringWriter.toString();
    }


    /**
     * Render an HTML list of the currently active Contexts in our virtual host, and memory and server status
     * information.
     *
     * @param request  The request
     * @param response The response
     * @param message  a message to display
     * @param smClient StringManager for the client's locale
     *
     * @throws IOException An IO error occurred
     */
    public void list(HttpServletRequest request, HttpServletResponse response, String message, StringManager smClient)
            throws IOException {

        if (debug >= 1) {
            log(sm.getString("hostManagerServlet.list", engine.getName()));
        }

        PrintWriter writer = response.getWriter();

        Object[] args = new Object[2];
        args[0] = getServletContext().getContextPath();
        args[1] = smClient.getString("htmlHostManagerServlet.title");

        // HTML Header Section
        writer.print(MessageFormat.format(org.apache.catalina.manager.Constants.HTML_HEADER_SECTION, args));

        // Body Header Section
        writer.print(MessageFormat.format(org.apache.catalina.manager.Constants.BODY_HEADER_SECTION, args));

        // Message Section
        args = new Object[3];
        args[0] = smClient.getString("htmlHostManagerServlet.messageLabel");
        if (message == null || message.length() == 0) {
            args[1] = "OK";
        } else {
            args[1] = Escape.htmlElementContent(message);
        }
        writer.print(MessageFormat.format(Constants.MESSAGE_SECTION, args));

        // Manager Section
        args = new Object[9];
        args[0] = smClient.getString("htmlHostManagerServlet.manager");
        args[1] = response.encodeURL(getServletContext().getContextPath() + "/html/list");
        args[2] = smClient.getString("htmlHostManagerServlet.list");
        args[3] = // External link
                getServletContext().getContextPath() + "/" +
                        smClient.getString("htmlHostManagerServlet.helpHtmlManagerFile");
        args[4] = smClient.getString("htmlHostManagerServlet.helpHtmlManager");
        args[5] = // External link
                getServletContext().getContextPath() + "/" +
                        smClient.getString("htmlHostManagerServlet.helpManagerFile");
        args[6] = smClient.getString("htmlHostManagerServlet.helpManager");
        args[7] = response.encodeURL("/manager/status");
        args[8] = smClient.getString("statusServlet.title");
        writer.print(MessageFormat.format(Constants.MANAGER_SECTION, args));

        // Hosts Header Section
        args = new Object[3];
        args[0] = smClient.getString("htmlHostManagerServlet.hostName");
        args[1] = smClient.getString("htmlHostManagerServlet.hostAliases");
        args[2] = smClient.getString("htmlHostManagerServlet.hostTasks");
        writer.print(MessageFormat.format(HOSTS_HEADER_SECTION, args));

        // Hosts Row Section
        // Create sorted set of host names.
        Container[] children = engine.findChildren();
        String hostNames[] = new String[children.length];
        for (int i = 0; i < children.length; i++) {
            hostNames[i] = children[i].getName();
        }

        SortedSet<String> sortedHostNames = new TreeSet<>();
        sortedHostNames.addAll(Arrays.asList(hostNames));

        String hostsStart = smClient.getString("htmlHostManagerServlet.hostsStart");
        String hostsStop = smClient.getString("htmlHostManagerServlet.hostsStop");
        String hostsRemove = smClient.getString("htmlHostManagerServlet.hostsRemove");
        String hostThis = smClient.getString("htmlHostManagerServlet.hostThis");

        for (String hostName : sortedHostNames) {
            Host host = (Host) engine.findChild(hostName);

            if (host != null) {
                args = new Object[2];
                args[0] = // External link
                        Escape.htmlElementContent(hostName);
                String[] aliases = host.findAliases();
                StringBuilder buf = new StringBuilder();
                if (aliases.length > 0) {
                    buf.append(aliases[0]);
                    for (int j = 1; j < aliases.length; j++) {
                        buf.append(", ").append(aliases[j]);
                    }
                }

                if (buf.length() == 0) {
                    buf.append("&nbsp;");
                    args[1] = buf.toString();
                } else {
                    args[1] = Escape.htmlElementContent(buf.toString());
                }

                writer.print(MessageFormat.format(HOSTS_ROW_DETAILS_SECTION, args));

                args = new Object[5];
                if (host.getState().isAvailable()) {
                    args[0] = response.encodeURL(getServletContext().getContextPath() + "/html/stop?name=" +
                            URLEncoder.encode(hostName, "UTF-8"));
                    args[1] = hostsStop;
                } else {
                    args[0] = response.encodeURL(getServletContext().getContextPath() + "/html/start?name=" +
                            URLEncoder.encode(hostName, "UTF-8"));
                    args[1] = hostsStart;
                }
                args[2] = response.encodeURL(getServletContext().getContextPath() + "/html/remove?name=" +
                        URLEncoder.encode(hostName, "UTF-8"));
                args[3] = hostsRemove;
                args[4] = hostThis;
                if (host == this.installedHost) {
                    writer.print(MessageFormat.format(MANAGER_HOST_ROW_BUTTON_SECTION, args));
                } else {
                    writer.print(MessageFormat.format(HOSTS_ROW_BUTTON_SECTION, args));
                }
            }
        }

        // Add Section
        args = new Object[6];
        args[0] = smClient.getString("htmlHostManagerServlet.addTitle");
        args[1] = smClient.getString("htmlHostManagerServlet.addHost");
        args[2] = response.encodeURL(getServletContext().getContextPath() + "/html/add");
        args[3] = smClient.getString("htmlHostManagerServlet.addName");
        args[4] = smClient.getString("htmlHostManagerServlet.addAliases");
        args[5] = smClient.getString("htmlHostManagerServlet.addAppBase");
        writer.print(MessageFormat.format(ADD_SECTION_START, args));

        args = new Object[3];
        args[0] = smClient.getString("htmlHostManagerServlet.addAutoDeploy");
        args[1] = "autoDeploy";
        args[2] = "checked";
        writer.print(MessageFormat.format(ADD_SECTION_BOOLEAN, args));
        args[0] = smClient.getString("htmlHostManagerServlet.addDeployOnStartup");
        args[1] = "deployOnStartup";
        args[2] = "checked";
        writer.print(MessageFormat.format(ADD_SECTION_BOOLEAN, args));
        args[0] = smClient.getString("htmlHostManagerServlet.addDeployXML");
        args[1] = "deployXML";
        args[2] = "checked";
        writer.print(MessageFormat.format(ADD_SECTION_BOOLEAN, args));
        args[0] = smClient.getString("htmlHostManagerServlet.addUnpackWARs");
        args[1] = "unpackWARs";
        args[2] = "checked";
        writer.print(MessageFormat.format(ADD_SECTION_BOOLEAN, args));

        args[0] = smClient.getString("htmlHostManagerServlet.addManager");
        args[1] = "manager";
        args[2] = "checked";
        writer.print(MessageFormat.format(ADD_SECTION_BOOLEAN, args));

        args[0] = smClient.getString("htmlHostManagerServlet.addCopyXML");
        args[1] = "copyXML";
        args[2] = "";
        writer.print(MessageFormat.format(ADD_SECTION_BOOLEAN, args));

        args = new Object[1];
        args[0] = smClient.getString("htmlHostManagerServlet.addButton");
        writer.print(MessageFormat.format(ADD_SECTION_END, args));

        // Persist Configuration Section
        args = new Object[4];
        args[0] = smClient.getString("htmlHostManagerServlet.persistTitle");
        args[1] = response.encodeURL(getServletContext().getContextPath() + "/html/persist");
        args[2] = smClient.getString("htmlHostManagerServlet.persistAllButton");
        args[3] = smClient.getString("htmlHostManagerServlet.persistAll");
        writer.print(MessageFormat.format(PERSIST_SECTION, args));

        // Server Header Section
        args = new Object[7];
        args[0] = smClient.getString("htmlHostManagerServlet.serverTitle");
        args[1] = smClient.getString("htmlHostManagerServlet.serverVersion");
        args[2] = smClient.getString("htmlHostManagerServlet.serverJVMVersion");
        args[3] = smClient.getString("htmlHostManagerServlet.serverJVMVendor");
        args[4] = smClient.getString("htmlHostManagerServlet.serverOSName");
        args[5] = smClient.getString("htmlHostManagerServlet.serverOSVersion");
        args[6] = smClient.getString("htmlHostManagerServlet.serverOSArch");
        writer.print(MessageFormat.format(Constants.SERVER_HEADER_SECTION, args));

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


    // ------------------------------------------------------ Private Constants

    // These HTML sections are broken in relatively small sections, because of
    // limited number of substitutions MessageFormat can process
    // (maximum of 10).

    //@formatter:off
    private static final String HOSTS_HEADER_SECTION =
            "<table border=\"1\" cellspacing=\"0\" cellpadding=\"3\">\n" +
            "<tr>\n" +
            " <td colspan=\"5\" class=\"title\">{0}</td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td class=\"header-left\"><small>{0}</small></td>\n" +
            " <td class=\"header-center\"><small>{1}</small></td>\n" +
            " <td class=\"header-center\"><small>{2}</small></td>\n" +
            "</tr>\n";

    private static final String HOSTS_ROW_DETAILS_SECTION =
            "<tr>\n" +
            " <td class=\"row-left\"><small><a href=\"http://{0}\" " + Constants.REL_EXTERNAL + ">{0}</a>" +
                    "</small></td>\n" +
            " <td class=\"row-center\"><small>{1}</small></td>\n";

    private static final String MANAGER_HOST_ROW_BUTTON_SECTION =
            " <td class=\"row-left\">\n" +
            "  <small>{4}</small>\n" +
            " </td>\n" +
            "</tr>\n";

    private static final String HOSTS_ROW_BUTTON_SECTION =
            " <td class=\"row-left\" NOWRAP>\n" +
            "  <form class=\"inline\" method=\"POST\" action=\"{0}\">" +
            "   <small><input type=\"submit\" value=\"{1}\"></small>" +
            "  </form>\n" +
            "  <form class=\"inline\" method=\"POST\" action=\"{2}\">" +
            "   <small><input type=\"submit\" value=\"{3}\"></small>" +
            "  </form>\n" +
            " </td>\n" +
            "</tr>\n";

    private static final String ADD_SECTION_START =
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
            "  <input type=\"text\" name=\"name\" size=\"20\">\n" +
            " </td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td class=\"row-right\">\n" +
            "  <small>{4}</small>\n" +
            " </td>\n" +
            " <td class=\"row-left\">\n" +
            "  <input type=\"text\" name=\"aliases\" size=\"64\">\n" +
            " </td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td class=\"row-right\">\n" +
            "  <small>{5}</small>\n" +
            " </td>\n" +
            " <td class=\"row-left\">\n" +
            "  <input type=\"text\" name=\"appBase\" size=\"64\">\n" +
            " </td>\n" +
            "</tr>\n" ;

    private static final String ADD_SECTION_BOOLEAN =
            "<tr>\n" +
            " <td class=\"row-right\">\n" +
            "  <small>{0}</small>\n" +
            " </td>\n" +
            " <td class=\"row-left\">\n" +
            "  <input type=\"checkbox\" name=\"{1}\" {2}>\n" +
            " </td>\n" +
            "</tr>\n" ;

    private static final String ADD_SECTION_END =
            "<tr>\n" +
            " <td class=\"row-right\">\n" +
            "  &nbsp;\n" +
            " </td>\n" +
            " <td class=\"row-left\">\n" +
            "  <input type=\"submit\" value=\"{0}\">\n" +
            " </td>\n" +
            "</tr>\n" +
            "</table>\n" +
            "</form>\n" +
            "</td>\n" +
            "</tr>\n" +
            "</table>\n" +
            "<br>\n" +
            "\n";

    private static final String PERSIST_SECTION =
            "<table border=\"1\" cellspacing=\"0\" cellpadding=\"3\">\n" +
            "<tr>\n" +
            " <td class=\"title\">{0}</td>\n" +
            "</tr>\n" +
            "<tr>\n" +
            " <td class=\"row-left\">\n" +
            "  <form class=\"inline\" method=\"POST\" action=\"{1}\">" +
            "   <small><input type=\"submit\" value=\"{2}\"></small>" +
            "  </form> {3}\n" +
            " </td>\n" +
            "</tr>\n" +
            "</table>\n" +
            "<br>\n" +
            "\n";
    //@formatter:on
}
