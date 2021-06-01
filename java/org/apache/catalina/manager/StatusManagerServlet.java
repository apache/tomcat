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


import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import javax.management.MBeanServer;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.util.ServerInfo;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

/**
 * This servlet will display a complete status of the HTTP/1.1 connector.
 *
 * @author Remy Maucherat
 */
public class StatusManagerServlet
    extends HttpServlet implements NotificationListener {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------- Instance Variables
    /**
     * MBean server.
     */
    protected MBeanServer mBeanServer = null;


    /**
     * Vector of protocol handlers object names.
     */
    protected final Vector<ObjectName> protocolHandlers = new Vector<>();


    /**
     * Vector of thread pools object names.
     */
    protected final Vector<ObjectName> threadPools = new Vector<>();


    /**
     * Vector of request processors object names.
     */
    protected final Vector<ObjectName> requestProcessors = new Vector<>();


    /**
     * Vector of global request processors object names.
     */
    protected final Vector<ObjectName> globalRequestProcessors = new Vector<>();


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // --------------------------------------------------------- Public Methods


    /**
     * Initialize this servlet.
     */
    @Override
    public void init() throws ServletException {

        // Retrieve the MBean server
        mBeanServer = Registry.getRegistry(null, null).getMBeanServer();

        try {

            // Query protocol handlers
            String onStr = "*:type=ProtocolHandler,*";
            ObjectName objectName = new ObjectName(onStr);
            Set<ObjectInstance> set = mBeanServer.queryMBeans(objectName, null);
            Iterator<ObjectInstance> iterator = set.iterator();
            while (iterator.hasNext()) {
                ObjectInstance oi = iterator.next();
                protocolHandlers.addElement(oi.getObjectName());
            }

            // Query Thread Pools
            onStr = "*:type=ThreadPool,*";
            objectName = new ObjectName(onStr);
            set = mBeanServer.queryMBeans(objectName, null);
            iterator = set.iterator();
            while (iterator.hasNext()) {
                ObjectInstance oi = iterator.next();
                threadPools.addElement(oi.getObjectName());
            }

            // Query Global Request Processors
            onStr = "*:type=GlobalRequestProcessor,*";
            objectName = new ObjectName(onStr);
            set = mBeanServer.queryMBeans(objectName, null);
            iterator = set.iterator();
            while (iterator.hasNext()) {
                ObjectInstance oi = iterator.next();
                globalRequestProcessors.addElement(oi.getObjectName());
            }

            // Query Request Processors
            onStr = "*:type=RequestProcessor,*";
            objectName = new ObjectName(onStr);
            set = mBeanServer.queryMBeans(objectName, null);
            iterator = set.iterator();
            while (iterator.hasNext()) {
                ObjectInstance oi = iterator.next();
                requestProcessors.addElement(oi.getObjectName());
            }

            // Register with MBean server
            onStr = "JMImplementation:type=MBeanServerDelegate";
            objectName = new ObjectName(onStr);
            mBeanServer.addNotificationListener(objectName, this, null, null);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    /**
     * Finalize this servlet.
     */
    @Override
    public void destroy() {

        // Unregister with MBean server
        String onStr = "JMImplementation:type=MBeanServerDelegate";
        ObjectName objectName;
        try {
            objectName = new ObjectName(onStr);
            mBeanServer.removeNotificationListener(objectName, this, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

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

        // mode is flag for HTML or XML output
        int mode = 0;
        // if ?XML=true, set the mode to XML
        if (request.getParameter("XML") != null
            && request.getParameter("XML").equals("true")) {
            mode = 1;
        }
        StatusTransformer.setContentType(response, mode);

        PrintWriter writer = response.getWriter();

        boolean completeStatus = false;
        if ((request.getPathInfo() != null)
            && (request.getPathInfo().equals("/all"))) {
            completeStatus = true;
        }
        // use StatusTransformer to output status
        Object[] args = new Object[1];
        args[0] = request.getContextPath();
        StatusTransformer.writeHeader(writer,args,mode);

        // Body Header Section
        args = new Object[2];
        args[0] = request.getContextPath();
        if (completeStatus) {
            args[1] = smClient.getString("statusServlet.complete");
        } else {
            args[1] = smClient.getString("statusServlet.title");
        }
        // use StatusTransformer to output status
        StatusTransformer.writeBody(writer,args,mode);

        // Manager Section
        args = new Object[9];
        args[0] = smClient.getString("htmlManagerServlet.manager");
        args[1] = response.encodeURL(request.getContextPath() + "/html/list");
        args[2] = smClient.getString("htmlManagerServlet.list");
        args[3] = // External link
            (request.getContextPath() + "/" +
             smClient.getString("htmlManagerServlet.helpHtmlManagerFile"));
        args[4] = smClient.getString("htmlManagerServlet.helpHtmlManager");
        args[5] = // External link
            (request.getContextPath() + "/" +
             smClient.getString("htmlManagerServlet.helpManagerFile"));
        args[6] = smClient.getString("htmlManagerServlet.helpManager");
        if (completeStatus) {
            args[7] = response.encodeURL
                (request.getContextPath() + "/status");
            args[8] = smClient.getString("statusServlet.title");
        } else {
            args[7] = response.encodeURL
                (request.getContextPath() + "/status/all");
            args[8] = smClient.getString("statusServlet.complete");
        }
        // use StatusTransformer to output status
        StatusTransformer.writeManager(writer,args,mode);

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
        // use StatusTransformer to output status
        StatusTransformer.writePageHeading(writer,args,mode);

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
        // use StatusTransformer to output status
        StatusTransformer.writeServerInfo(writer, args, mode);

        try {

            // Display operating system statistics using APR if available
            args = new Object[7];
            args[0] = smClient.getString("htmlManagerServlet.osPhysicalMemory");
            args[1] = smClient.getString("htmlManagerServlet.osAvailableMemory");
            args[2] = smClient.getString("htmlManagerServlet.osTotalPageFile");
            args[3] = smClient.getString("htmlManagerServlet.osFreePageFile");
            args[4] = smClient.getString("htmlManagerServlet.osMemoryLoad");
            args[5] = smClient.getString("htmlManagerServlet.osKernelTime");
            args[6] = smClient.getString("htmlManagerServlet.osUserTime");
            StatusTransformer.writeOSState(writer, mode, args);

            // Display virtual machine statistics
            args = new Object[9];
            args[0] = smClient.getString("htmlManagerServlet.jvmFreeMemory");
            args[1] = smClient.getString("htmlManagerServlet.jvmTotalMemory");
            args[2] = smClient.getString("htmlManagerServlet.jvmMaxMemory");
            args[3] = smClient.getString("htmlManagerServlet.jvmTableTitleMemoryPool");
            args[4] = smClient.getString("htmlManagerServlet.jvmTableTitleType");
            args[5] = smClient.getString("htmlManagerServlet.jvmTableTitleInitial");
            args[6] = smClient.getString("htmlManagerServlet.jvmTableTitleTotal");
            args[7] = smClient.getString("htmlManagerServlet.jvmTableTitleMaximum");
            args[8] = smClient.getString("htmlManagerServlet.jvmTableTitleUsed");
            // use StatusTransformer to output status
            StatusTransformer.writeVMState(writer,mode, args);

            Enumeration<ObjectName> enumeration = threadPools.elements();
            while (enumeration.hasMoreElements()) {
                ObjectName objectName = enumeration.nextElement();
                String name = objectName.getKeyProperty("name");
                args = new Object[19];
                args[0] = smClient.getString("htmlManagerServlet.connectorStateMaxThreads");
                args[1] = smClient.getString("htmlManagerServlet.connectorStateThreadCount");
                args[2] = smClient.getString("htmlManagerServlet.connectorStateThreadBusy");
                args[3] = smClient.getString("htmlManagerServlet.connectorStateAliveSocketCount");
                args[4] = smClient.getString("htmlManagerServlet.connectorStateMaxProcessingTime");
                args[5] = smClient.getString("htmlManagerServlet.connectorStateProcessingTime");
                args[6] = smClient.getString("htmlManagerServlet.connectorStateRequestCount");
                args[7] = smClient.getString("htmlManagerServlet.connectorStateErrorCount");
                args[8] = smClient.getString("htmlManagerServlet.connectorStateBytesReceived");
                args[9] = smClient.getString("htmlManagerServlet.connectorStateBytesSent");
                args[10] = smClient.getString("htmlManagerServlet.connectorStateTableTitleStage");
                args[11] = smClient.getString("htmlManagerServlet.connectorStateTableTitleTime");
                args[12] = smClient.getString("htmlManagerServlet.connectorStateTableTitleBSent");
                args[13] = smClient.getString("htmlManagerServlet.connectorStateTableTitleBRecv");
                args[14] = smClient.getString("htmlManagerServlet.connectorStateTableTitleClientForw");
                args[15] = smClient.getString("htmlManagerServlet.connectorStateTableTitleClientAct");
                args[16] = smClient.getString("htmlManagerServlet.connectorStateTableTitleVHost");
                args[17] = smClient.getString("htmlManagerServlet.connectorStateTableTitleRequest");
                args[18] = smClient.getString("htmlManagerServlet.connectorStateHint");
                // use StatusTransformer to output status
                StatusTransformer.writeConnectorState
                    (writer, objectName,
                     name, mBeanServer, globalRequestProcessors,
                     requestProcessors, mode, args);
            }

            if ((request.getPathInfo() != null)
                && (request.getPathInfo().equals("/all"))) {
                // Note: Retrieving the full status is much slower
                // use StatusTransformer to output status
                StatusTransformer.writeDetailedState
                    (writer, mBeanServer, mode);
            }

        } catch (Exception e) {
            throw new ServletException(e);
        }

        // use StatusTransformer to output status
        StatusTransformer.writeFooter(writer, mode);

    }

    // ------------------------------------------- NotificationListener Methods


    @Override
    public void handleNotification(Notification notification,
                                   java.lang.Object handback) {

        if (notification instanceof MBeanServerNotification) {
            ObjectName objectName =
                ((MBeanServerNotification) notification).getMBeanName();
            if (notification.getType().equals
                (MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
                String type = objectName.getKeyProperty("type");
                if (type != null) {
                    if (type.equals("ProtocolHandler")) {
                        protocolHandlers.addElement(objectName);
                    } else if (type.equals("ThreadPool")) {
                        threadPools.addElement(objectName);
                    } else if (type.equals("GlobalRequestProcessor")) {
                        globalRequestProcessors.addElement(objectName);
                    } else if (type.equals("RequestProcessor")) {
                        requestProcessors.addElement(objectName);
                    }
                }
            } else if (notification.getType().equals
                       (MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
                String type = objectName.getKeyProperty("type");
                if (type != null) {
                    if (type.equals("ProtocolHandler")) {
                        protocolHandlers.removeElement(objectName);
                    } else if (type.equals("ThreadPool")) {
                        threadPools.removeElement(objectName);
                    } else if (type.equals("GlobalRequestProcessor")) {
                        globalRequestProcessors.removeElement(objectName);
                    } else if (type.equals("RequestProcessor")) {
                        requestProcessors.removeElement(objectName);
                    }
                }
            }
        }
    }
}
