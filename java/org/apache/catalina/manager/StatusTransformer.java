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

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.security.Escape;

/**
 * This is a refactoring of the servlet to externalize
 * the output into a simple class. Although we could
 * use XSLT, that is unnecessarily complex.
 *
 * @author Peter Lin
 */
public class StatusTransformer {

    // --------------------------------------------------------- Public Methods

    public static void setContentType(HttpServletResponse response, int mode) {
        if (mode == 0){
            response.setContentType("text/html;charset="+Constants.CHARSET);
        } else if (mode == 1){
            response.setContentType("text/xml;charset="+Constants.CHARSET);
        }
    }


    /**
     * Write an HTML or XML header.
     *
     * @param writer the PrintWriter to use
     * @param args Path prefix for URLs
     * @param mode - 0 = HTML header, 1 = XML declaration
     */
    public static void writeHeader(PrintWriter writer, Object[] args, int mode) {
        if (mode == 0) {
            // HTML Header Section
            writer.print(MessageFormat.format(Constants.HTML_HEADER_SECTION, args));
        } else if (mode == 1) {
            writer.write(Constants.XML_DECLARATION);
            writer.print(MessageFormat.format
                     (Constants.XML_STYLE, args));
            writer.write("<status>");
        }
    }


    /**
     * Write the header body. XML output doesn't bother
     * to output this stuff, since it's just title.
     *
     * @param writer The output writer
     * @param args What to write
     * @param mode 0 means write
     */
    public static void writeBody(PrintWriter writer, Object[] args, int mode) {
        if (mode == 0){
            writer.print(MessageFormat.format(Constants.BODY_HEADER_SECTION, args));
        }
    }


    /**
     * Write the manager webapp information.
     *
     * @param writer The output writer
     * @param args What to write
     * @param mode 0 means write
     */
    public static void writeManager(PrintWriter writer, Object[] args, int mode) {
        if (mode == 0) {
            writer.print(MessageFormat.format(Constants.MANAGER_SECTION, args));
        }
    }


    public static void writePageHeading(PrintWriter writer, Object[] args, int mode) {
        if (mode == 0) {
            writer.print(MessageFormat.format(Constants.SERVER_HEADER_SECTION, args));
        }
    }


    public static void writeServerInfo(PrintWriter writer, Object[] args, int mode){
        if (mode == 0) {
            writer.print(MessageFormat.format(Constants.SERVER_ROW_SECTION, args));
        }
    }


    public static void writeFooter(PrintWriter writer, int mode) {
        if (mode == 0){
            // HTML Tail Section
            writer.print(Constants.HTML_TAIL_SECTION);
        } else if (mode == 1){
            writer.write("</status>");
        }
    }


    /**
     * Write the VM state.
     * @param writer The output writer
     * @param mode Mode <code>0</code> will generate HTML.
     *             Mode <code>1</code> will generate XML.
     * @param args I18n labels for the VM state values
     * @throws Exception Propagated JMX error
     */
    public static void writeVMState(PrintWriter writer, int mode, Object[] args)
        throws Exception {

        SortedMap<String, MemoryPoolMXBean> memoryPoolMBeans = new TreeMap<>();
        for (MemoryPoolMXBean mbean: ManagementFactory.getMemoryPoolMXBeans()) {
            String sortKey = mbean.getType() + ":" + mbean.getName();
            memoryPoolMBeans.put(sortKey, mbean);
        }

        if (mode == 0){
            writer.print("<h1>JVM</h1>");

            writer.print("<p>");
            writer.print( args[0] );
            writer.print(' ');
            writer.print(formatSize(
                    Long.valueOf(Runtime.getRuntime().freeMemory()), true));
            writer.print(' ');
            writer.print(args[1]);
            writer.print(' ');
            writer.print(formatSize(
                    Long.valueOf(Runtime.getRuntime().totalMemory()), true));
            writer.print(' ');
            writer.print(args[2]);
            writer.print(' ');
            writer.print(formatSize(
                    Long.valueOf(Runtime.getRuntime().maxMemory()), true));
            writer.print("</p>");

            writer.write("<table border=\"0\"><thead><tr><th>" + args[3] + "</th><th>" + args[4] + "</th><th>" + args[5] + "</th><th>" + args[6] + "</th><th>" + args[7] + "</th><th>" + args[8] + "</th></tr></thead><tbody>");
            for (MemoryPoolMXBean memoryPoolMBean : memoryPoolMBeans.values()) {
                MemoryUsage usage = memoryPoolMBean.getUsage();
                writer.write("<tr><td>");
                writer.print(memoryPoolMBean.getName());
                writer.write("</td><td>");
                writer.print(memoryPoolMBean.getType());
                writer.write("</td><td>");
                writer.print(formatSize(Long.valueOf(usage.getInit()), true));
                writer.write("</td><td>");
                writer.print(formatSize(Long.valueOf(usage.getCommitted()), true));
                writer.write("</td><td>");
                writer.print(formatSize(Long.valueOf(usage.getMax()), true));
                writer.write("</td><td>");
                writer.print(formatSize(Long.valueOf(usage.getUsed()), true));
                if (usage.getMax() > 0) {
                    writer.write(" ("
                            + (usage.getUsed() * 100 / usage.getMax()) + "%)");
                }
                writer.write("</td></tr>");
            }
            writer.write("</tbody></table>");
        } else if (mode == 1){
            writer.write("<jvm>");

            writer.write("<memory");
            writer.write(" free='" + Runtime.getRuntime().freeMemory() + "'");
            writer.write(" total='" + Runtime.getRuntime().totalMemory() + "'");
            writer.write(" max='" + Runtime.getRuntime().maxMemory() + "'/>");

            for (MemoryPoolMXBean memoryPoolMBean : memoryPoolMBeans.values()) {
                MemoryUsage usage = memoryPoolMBean.getUsage();
                writer.write("<memorypool");
                writer.write(" name='" + Escape.xml("", memoryPoolMBean.getName()) + "'");
                writer.write(" type='" + memoryPoolMBean.getType() + "'");
                writer.write(" usageInit='" + usage.getInit() + "'");
                writer.write(" usageCommitted='" + usage.getCommitted() + "'");
                writer.write(" usageMax='" + usage.getMax() + "'");
                writer.write(" usageUsed='" + usage.getUsed() + "'/>");
            }

            writer.write("</jvm>");
        }

    }


    /**
     * Write connector state.
     * @param writer The output writer
     * @param tpName MBean name of the thread pool
     * @param name Connector name
     * @param mBeanServer MBean server
     * @param globalRequestProcessors MBean names for the global request processors
     * @param requestProcessors MBean names for the request processors
     * @param mode Mode <code>0</code> will generate HTML.
     *             Mode <code>1</code> will generate XML.
     * @param args I18n labels for the Connector state values
     * @throws Exception Propagated JMX error
     */
    public static void writeConnectorState(PrintWriter writer,
            ObjectName tpName, String name, MBeanServer mBeanServer,
            List<ObjectName> globalRequestProcessors,
            List<ObjectName> requestProcessors, int mode, Object[] args) throws Exception {

        if (mode == 0) {
            writer.print("<h1>");
            writer.print(name);
            writer.print("</h1>");

            writer.print("<p>");
            writer.print( args[0] );
            writer.print(' ');
            writer.print(mBeanServer.getAttribute(tpName, "maxThreads"));
            writer.print(' ');
            writer.print(args[1]);
            writer.print(' ');
            writer.print(mBeanServer.getAttribute(tpName, "currentThreadCount"));
            writer.print(' ');
            writer.print(args[2]);
            writer.print(' ');
            writer.print(mBeanServer.getAttribute(tpName, "currentThreadsBusy"));
            writer.print(' ');
            writer.print(args[3]);
            writer.print(' ');
            writer.print(mBeanServer.getAttribute(tpName, "keepAliveCount"));

            writer.print("<br>");

            ObjectName grpName = null;

            for (ObjectName objectName : globalRequestProcessors) {
                // Find the HTTP/1.1 RequestGroupInfo - BZ 65404
                if (name.equals(objectName.getKeyProperty("name")) && objectName.getKeyProperty("Upgrade") == null) {
                    grpName = objectName;
                }
            }

            if (grpName == null) {
                return;
            }

            writer.print( args[4] );
            writer.print(' ');
            writer.print(formatTime(mBeanServer.getAttribute(grpName, "maxTime"), false));
            writer.print(' ');
            writer.print(args[5]);
            writer.print(' ');
            writer.print(formatTime(mBeanServer.getAttribute(grpName, "processingTime"), true));
            writer.print(' ');
            writer.print(args[6]);
            writer.print(' ');
            writer.print(mBeanServer.getAttribute(grpName, "requestCount"));
            writer.print(' ');
            writer.print(args[7]);
            writer.print(' ');
            writer.print(mBeanServer.getAttribute(grpName, "errorCount"));
            writer.print(' ');
            writer.print(args[8]);
            writer.print(' ');
            writer.print(formatSize(mBeanServer.getAttribute(grpName, "bytesReceived"), true));
            writer.print(' ');
            writer.print(args[9]);
            writer.print(' ');
            writer.print(formatSize(mBeanServer.getAttribute(grpName, "bytesSent"), true));
            writer.print("</p>");

            writer.print("<table border=\"0\"><tr><th>"+ args[10] + "</th><th>" + args[11] + "</th><th>" + args[12] +"</th><th>" + args[13] +"</th><th>" + args[14] + "</th><th>" + args[15] + "</th><th>" + args[16] + "</th><th>" + args[17] + "</th></tr>");

            for (ObjectName objectName : requestProcessors) {
                if (name.equals(objectName.getKeyProperty("worker"))) {
                    writer.print("<tr>");
                    writeProcessorState(writer, objectName, mBeanServer, mode);
                    writer.print("</tr>");
                }
            }

            writer.print("</table>");

            writer.print("<p>");
            writer.print( args[18] );
            writer.print("</p>");
        } else if (mode == 1){
            writer.write("<connector name='" + name + "'>");

            writer.write("<threadInfo ");
            writer.write(" maxThreads=\"" + mBeanServer.getAttribute(tpName, "maxThreads") + "\"");
            writer.write(" currentThreadCount=\"" + mBeanServer.getAttribute(tpName, "currentThreadCount") + "\"");
            writer.write(" currentThreadsBusy=\"" + mBeanServer.getAttribute(tpName, "currentThreadsBusy") + "\"");
            writer.write(" />");

            ObjectName grpName = null;

            for (ObjectName objectName : globalRequestProcessors) {
                // Find the HTTP/1.1 RequestGroupInfo - BZ 65404
                if (name.equals(objectName.getKeyProperty("name")) && objectName.getKeyProperty("Upgrade") == null) {
                    grpName = objectName;
                }
            }

            if (grpName != null) {

                writer.write("<requestInfo ");
                writer.write(" maxTime=\"" + mBeanServer.getAttribute(grpName, "maxTime") + "\"");
                writer.write(" processingTime=\"" + mBeanServer.getAttribute(grpName, "processingTime") + "\"");
                writer.write(" requestCount=\"" + mBeanServer.getAttribute(grpName, "requestCount") + "\"");
                writer.write(" errorCount=\"" + mBeanServer.getAttribute(grpName, "errorCount") + "\"");
                writer.write(" bytesReceived=\"" + mBeanServer.getAttribute(grpName, "bytesReceived") + "\"");
                writer.write(" bytesSent=\"" + mBeanServer.getAttribute(grpName, "bytesSent") + "\"");
                writer.write(" />");

                writer.write("<workers>");
                for (ObjectName objectName : requestProcessors) {
                    if (name.equals(objectName.getKeyProperty("worker"))) {
                        writeProcessorState(writer, objectName, mBeanServer, mode);
                    }
                }
                writer.write("</workers>");
            }

            writer.write("</connector>");
        }
    }


    /**
     * Write processor state.
     * @param writer The output writer
     * @param pName MBean name of the processor
     * @param  mBeanServer MBean server
     * @param mode Mode <code>0</code> will generate HTML.
     *   Mode <code>1</code> will generate XML.
     * @throws Exception Propagated JMX error
     */
    protected static void writeProcessorState(PrintWriter writer,
                                              ObjectName pName,
                                              MBeanServer mBeanServer,
                                              int mode)
        throws Exception {

        Integer stageValue =
            (Integer) mBeanServer.getAttribute(pName, "stage");
        int stage = stageValue.intValue();
        boolean fullStatus = true;
        boolean showRequest = true;
        String stageStr = null;

        switch (stage) {

        case (1/*org.apache.coyote.Constants.STAGE_PARSE*/):
            stageStr = "P";
            fullStatus = false;
            break;
        case (2/*org.apache.coyote.Constants.STAGE_PREPARE*/):
            stageStr = "P";
            fullStatus = false;
            break;
        case (3/*org.apache.coyote.Constants.STAGE_SERVICE*/):
            stageStr = "S";
            break;
        case (4/*org.apache.coyote.Constants.STAGE_ENDINPUT*/):
            stageStr = "F";
            break;
        case (5/*org.apache.coyote.Constants.STAGE_ENDOUTPUT*/):
            stageStr = "F";
            break;
        case (7/*org.apache.coyote.Constants.STAGE_ENDED*/):
            stageStr = "R";
            fullStatus = false;
            break;
        case (6/*org.apache.coyote.Constants.STAGE_KEEPALIVE*/):
            stageStr = "K";
            fullStatus = true;
            showRequest = false;
            break;
        case (0/*org.apache.coyote.Constants.STAGE_NEW*/):
            stageStr = "R";
            fullStatus = false;
            break;
        default:
            // Unknown stage
            stageStr = "?";
            fullStatus = false;

        }

        if (mode == 0) {
            writer.write("<td><strong>");
            writer.write(stageStr);
            writer.write("</strong></td>");

            if (fullStatus) {
                writer.write("<td>");
                writer.print(formatTime(mBeanServer.getAttribute
                                        (pName, "requestProcessingTime"), false));
                writer.write("</td>");
                writer.write("<td>");
                if (showRequest) {
                    writer.print(formatSize(mBeanServer.getAttribute
                                            (pName, "requestBytesSent"), false));
                } else {
                    writer.write("?");
                }
                writer.write("</td>");
                writer.write("<td>");
                if (showRequest) {
                    writer.print(formatSize(mBeanServer.getAttribute
                                            (pName, "requestBytesReceived"),
                                            false));
                } else {
                    writer.write("?");
                }
                writer.write("</td>");
                writer.write("<td>");
                writer.print(Escape.htmlElementContent(mBeanServer.getAttribute
                                    (pName, "remoteAddrForwarded")));
                writer.write("</td>");
                writer.write("<td>");
                writer.print(Escape.htmlElementContent(mBeanServer.getAttribute
                                    (pName, "remoteAddr")));
                writer.write("</td>");
                writer.write("<td nowrap>");
                writer.write(Escape.htmlElementContent(mBeanServer.getAttribute
                                    (pName, "virtualHost")));
                writer.write("</td>");
                writer.write("<td nowrap class=\"row-left\">");
                if (showRequest) {
                    writer.write(Escape.htmlElementContent(mBeanServer.getAttribute
                                        (pName, "method")));
                    writer.write(' ');
                    writer.write(Escape.htmlElementContent(mBeanServer.getAttribute
                                        (pName, "currentUri")));
                    String queryString = (String) mBeanServer.getAttribute
                        (pName, "currentQueryString");
                    if ((queryString != null) && (!queryString.equals(""))) {
                        writer.write("?");
                        writer.print(Escape.htmlElementContent(queryString));
                    }
                    writer.write(' ');
                    writer.write(Escape.htmlElementContent(mBeanServer.getAttribute
                                        (pName, "protocol")));
                } else {
                    writer.write("?");
                }
                writer.write("</td>");
            } else {
                writer.write("<td>?</td><td>?</td><td>?</td><td>?</td><td>?</td><td>?</td>");
            }
        } else if (mode == 1){
            writer.write("<worker ");
            writer.write(" stage=\"" + stageStr + "\"");

            if (fullStatus) {
                writer.write(" requestProcessingTime=\""
                             + mBeanServer.getAttribute
                             (pName, "requestProcessingTime") + "\"");
                writer.write(" requestBytesSent=\"");
                if (showRequest) {
                    writer.write("" + mBeanServer.getAttribute
                                 (pName, "requestBytesSent"));
                } else {
                    writer.write("0");
                }
                writer.write("\"");
                writer.write(" requestBytesReceived=\"");
                if (showRequest) {
                    writer.write("" + mBeanServer.getAttribute
                                 (pName, "requestBytesReceived"));
                } else {
                    writer.write("0");
                }
                writer.write("\"");
                writer.write(" remoteAddr=\""
                             + Escape.htmlElementContent(mBeanServer.getAttribute
                                      (pName, "remoteAddr")) + "\"");
                writer.write(" virtualHost=\""
                             + Escape.htmlElementContent(mBeanServer.getAttribute
                                      (pName, "virtualHost")) + "\"");

                if (showRequest) {
                    writer.write(" method=\""
                                 + Escape.htmlElementContent(mBeanServer.getAttribute
                                          (pName, "method")) + "\"");
                    writer.write(" currentUri=\""
                                 + Escape.htmlElementContent(mBeanServer.getAttribute
                                          (pName, "currentUri")) + "\"");

                    String queryString = (String) mBeanServer.getAttribute
                        (pName, "currentQueryString");
                    if ((queryString != null) && (!queryString.equals(""))) {
                        writer.write(" currentQueryString=\""
                                     + Escape.htmlElementContent(queryString) + "\"");
                    } else {
                        writer.write(" currentQueryString=\"&#63;\"");
                    }
                    writer.write(" protocol=\""
                                 + Escape.htmlElementContent(mBeanServer.getAttribute
                                          (pName, "protocol")) + "\"");
                } else {
                    writer.write(" method=\"&#63;\"");
                    writer.write(" currentUri=\"&#63;\"");
                    writer.write(" currentQueryString=\"&#63;\"");
                    writer.write(" protocol=\"&#63;\"");
                }
            } else {
                writer.write(" requestProcessingTime=\"0\"");
                writer.write(" requestBytesSent=\"0\"");
                writer.write(" requestBytesReceived=\"0\"");
                writer.write(" remoteAddr=\"&#63;\"");
                writer.write(" virtualHost=\"&#63;\"");
                writer.write(" method=\"&#63;\"");
                writer.write(" currentUri=\"&#63;\"");
                writer.write(" currentQueryString=\"&#63;\"");
                writer.write(" protocol=\"&#63;\"");
            }
            writer.write(" />");
        }

    }


    /**
     * Write applications state.
     * @param writer The output writer
     * @param mBeanServer MBean server
     * @param mode Mode <code>0</code> will generate HTML.
     *   Mode <code>1</code> will generate XML.
     * @throws Exception Propagated JMX error
     */
    public static void writeDetailedState(PrintWriter writer,
                                          MBeanServer mBeanServer, int mode)
        throws Exception {

        if (mode == 0){
            ObjectName queryHosts = new ObjectName("*:j2eeType=WebModule,*");
            Set<ObjectName> hostsON = mBeanServer.queryNames(queryHosts, null);

            // Navigation menu
            writer.print("<h1>");
            writer.print("Application list");
            writer.print("</h1>");

            writer.print("<p>");
            int count = 0;
            Iterator<ObjectName> iterator = hostsON.iterator();
            while (iterator.hasNext()) {
                ObjectName contextON = iterator.next();
                String webModuleName = contextON.getKeyProperty("name");
                if (webModuleName.startsWith("//")) {
                    webModuleName = webModuleName.substring(2);
                }
                int slash = webModuleName.indexOf('/');
                if (slash == -1) {
                    count++;
                    continue;
                }

                writer.print("<a href=\"#" + (count++) + ".0\">");
                writer.print(Escape.htmlElementContent(webModuleName));
                writer.print("</a>");
                if (iterator.hasNext()) {
                    writer.print("<br>");
                }

            }
            writer.print("</p>");

            // Webapp list
            count = 0;
            iterator = hostsON.iterator();
            while (iterator.hasNext()) {
                ObjectName contextON = iterator.next();
                writer.print("<a class=\"A.name\" name=\""
                             + (count++) + ".0\">");
                writeContext(writer, contextON, mBeanServer, mode);
            }

        } else if (mode == 1){
            // for now we don't write out the Detailed state in XML
        }

    }


    /**
     * Write context state.
     * @param writer The output writer
     * @param objectName The context MBean name
     * @param mBeanServer MBean server
     * @param mode Mode <code>0</code> will generate HTML.
     *   Mode <code>1</code> will generate XML.
     * @throws Exception Propagated JMX error
     */
    protected static void writeContext(PrintWriter writer,
                                       ObjectName objectName,
                                       MBeanServer mBeanServer, int mode)
        throws Exception {

        if (mode == 0){
            String webModuleName = objectName.getKeyProperty("name");
            String name = webModuleName;
            if (name == null) {
                return;
            }

            String hostName = null;
            String contextName = null;
            if (name.startsWith("//")) {
                name = name.substring(2);
            }
            int slash = name.indexOf('/');
            if (slash != -1) {
                hostName = name.substring(0, slash);
                contextName = name.substring(slash);
            } else {
                return;
            }

            ObjectName queryManager = new ObjectName
                (objectName.getDomain() + ":type=Manager,context=" + contextName
                 + ",host=" + hostName + ",*");
            Set<ObjectName> managersON =
                mBeanServer.queryNames(queryManager, null);
            ObjectName managerON = null;
            for (ObjectName aManagersON : managersON) {
                managerON = aManagersON;
            }

            ObjectName queryJspMonitor = new ObjectName
                (objectName.getDomain() + ":type=JspMonitor,WebModule=" +
                 webModuleName + ",*");
            Set<ObjectName> jspMonitorONs =
                mBeanServer.queryNames(queryJspMonitor, null);

            // Special case for the root context
            if (contextName.equals("/")) {
                contextName = "";
            }

            writer.print("<h1>");
            writer.print(Escape.htmlElementContent(name));
            writer.print("</h1>");
            writer.print("</a>");

            writer.print("<p>");
            Object startTime = mBeanServer.getAttribute(objectName,
                                                        "startTime");
            writer.print(" Start time: " +
                         new Date(((Long) startTime).longValue()));
            writer.print(" Startup time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "startupTime"), false));
            writer.print(" TLD scan time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "tldScanTime"), false));
            if (managerON != null) {
                writeManager(writer, managerON, mBeanServer, mode);
            }
            if (jspMonitorONs != null) {
                writeJspMonitor(writer, jspMonitorONs, mBeanServer, mode);
            }
            writer.print("</p>");

            String onStr = objectName.getDomain()
                + ":j2eeType=Servlet,WebModule=" + webModuleName + ",*";
            ObjectName servletObjectName = new ObjectName(onStr);
            Set<ObjectInstance> set =
                mBeanServer.queryMBeans(servletObjectName, null);
            for (ObjectInstance oi : set) {
                writeWrapper(writer, oi.getObjectName(), mBeanServer, mode);
            }

        } else if (mode == 1){
            // for now we don't write out the context in XML
        }

    }


    /**
     * Write detailed information about a manager.
     * @param writer The output writer
     * @param objectName The manager MBean name
     * @param mBeanServer MBean server
     * @param mode Mode <code>0</code> will generate HTML.
     *   Mode <code>1</code> will generate XML.
     * @throws Exception Propagated JMX error
     */
    public static void writeManager(PrintWriter writer, ObjectName objectName,
                                    MBeanServer mBeanServer, int mode)
        throws Exception {

        if (mode == 0) {
            writer.print("<br>");
            writer.print(" Active sessions: ");
            writer.print(mBeanServer.getAttribute
                         (objectName, "activeSessions"));
            writer.print(" Session count: ");
            writer.print(mBeanServer.getAttribute
                         (objectName, "sessionCounter"));
            writer.print(" Max active sessions: ");
            writer.print(mBeanServer.getAttribute(objectName, "maxActive"));
            writer.print(" Rejected session creations: ");
            writer.print(mBeanServer.getAttribute
                         (objectName, "rejectedSessions"));
            writer.print(" Expired sessions: ");
            writer.print(mBeanServer.getAttribute
                         (objectName, "expiredSessions"));
            writer.print(" Longest session alive time: ");
            writer.print(formatSeconds(mBeanServer.getAttribute(
                                                    objectName,
                                                    "sessionMaxAliveTime")));
            writer.print(" Average session alive time: ");
            writer.print(formatSeconds(mBeanServer.getAttribute(
                                                    objectName,
                                                    "sessionAverageAliveTime")));
            writer.print(" Processing time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "processingTime"), false));
        } else if (mode == 1) {
            // for now we don't write out the wrapper details
        }

    }


    /**
     * Write JSP monitoring information.
     * @param writer The output writer
     * @param jspMonitorONs The JSP MBean names
     * @param mBeanServer MBean server
     * @param mode Mode <code>0</code> will generate HTML.
     *   Mode <code>1</code> will generate XML.
     * @throws Exception Propagated JMX error
     */
    public static void writeJspMonitor(PrintWriter writer,
                                       Set<ObjectName> jspMonitorONs,
                                       MBeanServer mBeanServer,
                                       int mode)
            throws Exception {

        int jspCount = 0;
        int jspReloadCount = 0;

        for (ObjectName jspMonitorON : jspMonitorONs) {
            Object obj = mBeanServer.getAttribute(jspMonitorON, "jspCount");
            jspCount += ((Integer) obj).intValue();
            obj = mBeanServer.getAttribute(jspMonitorON, "jspReloadCount");
            jspReloadCount += ((Integer) obj).intValue();
        }

        if (mode == 0) {
            writer.print("<br>");
            writer.print(" JSPs loaded: ");
            writer.print(jspCount);
            writer.print(" JSPs reloaded: ");
            writer.print(jspReloadCount);
        } else if (mode == 1) {
            // for now we don't write out anything
        }
    }


    /**
     * Write detailed information about a wrapper.
     * @param writer The output writer
     * @param objectName The wrapper MBean names
     * @param mBeanServer MBean server
     * @param mode Mode <code>0</code> will generate HTML.
     *   Mode <code>1</code> will generate XML.
     * @throws Exception Propagated JMX error
     */
    public static void writeWrapper(PrintWriter writer, ObjectName objectName,
                                    MBeanServer mBeanServer, int mode)
        throws Exception {

        if (mode == 0) {
            String servletName = objectName.getKeyProperty("name");

            String[] mappings = (String[])
                mBeanServer.invoke(objectName, "findMappings", null, null);

            writer.print("<h2>");
            writer.print(Escape.htmlElementContent(servletName));
            if ((mappings != null) && (mappings.length > 0)) {
                writer.print(" [ ");
                for (int i = 0; i < mappings.length; i++) {
                    writer.print(Escape.htmlElementContent(mappings[i]));
                    if (i < mappings.length - 1) {
                        writer.print(" , ");
                    }
                }
                writer.print(" ] ");
            }
            writer.print("</h2>");

            writer.print("<p>");
            writer.print(" Processing time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "processingTime"), true));
            writer.print(" Max time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "maxTime"), false));
            writer.print(" Request count: ");
            writer.print(mBeanServer.getAttribute(objectName, "requestCount"));
            writer.print(" Error count: ");
            writer.print(mBeanServer.getAttribute(objectName, "errorCount"));
            writer.print(" Load time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "loadTime"), false));
            writer.print(" Classloading time: ");
            writer.print(formatTime(mBeanServer.getAttribute
                                    (objectName, "classLoadTime"), false));
            writer.print("</p>");
        } else if (mode == 1){
            // for now we don't write out the wrapper details
        }

    }


    /**
     * Display the given size in bytes, either as KB or MB.
     *
     * @param obj The object to format
     * @param mb true to display megabytes, false for kilobytes
     * @return formatted size
     */
    public static String formatSize(Object obj, boolean mb) {

        long bytes = -1L;

        if (obj instanceof Long) {
            bytes = ((Long) obj).longValue();
        } else if (obj instanceof Integer) {
            bytes = ((Integer) obj).intValue();
        }

        if (mb) {
            StringBuilder buff = new StringBuilder();
            if (bytes < 0) {
                buff.append('-');
                bytes = -bytes;
            }
            long mbytes = bytes / (1024 * 1024);
            long rest =
                ((bytes - (mbytes * (1024 * 1024))) * 100) / (1024 * 1024);
            buff.append(mbytes).append('.');
            if (rest < 10) {
                buff.append('0');
            }
            buff.append(rest).append(" MB");
            return buff.toString();
        } else {
            return ((bytes / 1024) + " KB");
        }

    }


    /**
     * Display the given time in ms, either as ms or s.
     *
     * @param obj The object to format
     * @param seconds true to display seconds, false for milliseconds
     * @return formatted time
     */
    public static String formatTime(Object obj, boolean seconds) {

        long time = -1L;

        if (obj instanceof Long) {
            time = ((Long) obj).longValue();
        } else if (obj instanceof Integer) {
            time = ((Integer) obj).intValue();
        }

        if (seconds) {
            return ((((float) time ) / 1000) + " s");
        } else {
            return (time + " ms");
        }
    }


    /**
     * Formats the given time (given in seconds) as a string.
     *
     * @param obj Time object to be formatted as string
     * @return formatted time
     */
    public static String formatSeconds(Object obj) {

        long time = -1L;

        if (obj instanceof Long) {
            time = ((Long) obj).longValue();
        } else if (obj instanceof Integer) {
            time = ((Integer) obj).intValue();
        }

        return (time + " s");
    }

}
