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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.util.ServerInfo;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

/**
 * This servlet will display a complete status of the HTTP/1.1 connector.
 *
 * @author Remy Maucherat
 */
public class StatusManagerServlet extends HttpServlet implements NotificationListener {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------- Instance Variables
    /**
     * MBean server.
     */
    protected MBeanServer mBeanServer = null;


    /**
     * Vector of thread pools object names.
     */
    protected final List<ObjectName> threadPools = Collections.synchronizedList(new ArrayList<>());


    /**
     * Vector of request processors object names.
     */
    protected final List<ObjectName> requestProcessors = Collections.synchronizedList(new ArrayList<>());


    /**
     * Vector of global request processors object names.
     */
    protected final List<ObjectName> globalRequestProcessors = Collections.synchronizedList(new ArrayList<>());


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    // --------------------------------------------------------- Public Methods

    @Override
    public void init() throws ServletException {

        // Retrieve the MBean server
        mBeanServer = Registry.getRegistry(null, null).getMBeanServer();

        try {

            // Query Thread Pools
            String onStr = "*:type=ThreadPool,*";
            ObjectName objectName = new ObjectName(onStr);
            Set<ObjectInstance> set = mBeanServer.queryMBeans(objectName, null);
            Iterator<ObjectInstance> iterator = set.iterator();
            while (iterator.hasNext()) {
                ObjectInstance oi = iterator.next();
                threadPools.add(oi.getObjectName());
            }

            // Query Global Request Processors
            onStr = "*:type=GlobalRequestProcessor,*";
            objectName = new ObjectName(onStr);
            set = mBeanServer.queryMBeans(objectName, null);
            iterator = set.iterator();
            while (iterator.hasNext()) {
                ObjectInstance oi = iterator.next();
                globalRequestProcessors.add(oi.getObjectName());
            }

            // Query Request Processors
            onStr = "*:type=RequestProcessor,*";
            objectName = new ObjectName(onStr);
            set = mBeanServer.queryMBeans(objectName, null);
            iterator = set.iterator();
            while (iterator.hasNext()) {
                ObjectInstance oi = iterator.next();
                requestProcessors.add(oi.getObjectName());
            }

            // Register with MBean server
            onStr = "JMImplementation:type=MBeanServerDelegate";
            objectName = new ObjectName(onStr);
            mBeanServer.addNotificationListener(objectName, this, null, null);

        } catch (Exception e) {
            log(sm.getString("managerServlet.error.jmx"), e);
        }

    }


    @Override
    public void destroy() {

        // Unregister with MBean server
        String onStr = "JMImplementation:type=MBeanServerDelegate";
        ObjectName objectName;
        try {
            objectName = new ObjectName(onStr);
            mBeanServer.removeNotificationListener(objectName, this, null, null);
        } catch (Exception e) {
            log(sm.getString("managerServlet.error.jmx"), e);
        }

    }


    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        StringManager smClient = StringManager.getManager(Constants.Package, request.getLocales());

        // mode is flag for HTML, JSON or XML output
        int mode = 0;
        // if ?XML=true, set the mode to XML
        if (request.getParameter("XML") != null && request.getParameter("XML").equals("true")) {
            mode = 1;
        }
        // if ?JSON=true, set the mode to JSON
        if (request.getParameter("JSON") != null && request.getParameter("JSON").equals("true")) {
            mode = 2;
        }
        StatusTransformer.setContentType(response, mode);

        PrintWriter writer = response.getWriter();

        boolean completeStatus = false;
        if (request.getPathInfo() != null && request.getPathInfo().equals("/all")) {
            completeStatus = true;
        }
        // use StatusTransformer to output status
        Object[] args = new Object[1];
        args[0] = getServletContext().getContextPath();
        StatusTransformer.writeHeader(writer, args, mode);

        // Body Header Section
        args = new Object[2];
        args[0] = getServletContext().getContextPath();
        if (completeStatus) {
            args[1] = smClient.getString("statusServlet.complete");
        } else {
            args[1] = smClient.getString("statusServlet.title");
        }
        // use StatusTransformer to output status
        StatusTransformer.writeBody(writer, args, mode);

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
        if (completeStatus) {
            args[7] = response.encodeURL(getServletContext().getContextPath() + "/status");
            args[8] = smClient.getString("statusServlet.title");
        } else {
            args[7] = response.encodeURL(getServletContext().getContextPath() + "/status/all");
            args[8] = smClient.getString("statusServlet.complete");
        }
        // use StatusTransformer to output status
        StatusTransformer.writeManager(writer, args, mode);

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
        StatusTransformer.writePageHeading(writer, args, mode);

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
            StatusTransformer.writeVMState(writer, mode, args);

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
            StatusTransformer.writeConnectorsState(writer, mBeanServer, threadPools, globalRequestProcessors,
                    requestProcessors, mode, args);

            if (request.getPathInfo() != null && request.getPathInfo().equals("/all")) {
                // Note: Retrieving the full status is much slower
                // use StatusTransformer to output status
                StatusTransformer.writeDetailedState(writer, mBeanServer, mode);
            }

        } catch (Exception e) {
            throw new ServletException(e);
        }

        // use StatusTransformer to output status
        StatusTransformer.writeFooter(writer, mode);
    }


    // ------------------------------------------- NotificationListener Methods

    @Override
    public void handleNotification(Notification notification, Object handback) {

        if (notification instanceof MBeanServerNotification) {
            ObjectName objectName = ((MBeanServerNotification) notification).getMBeanName();
            if (notification.getType().equals(MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
                String type = objectName.getKeyProperty("type");
                if (type != null) {
                    if (type.equals("ThreadPool")) {
                        threadPools.add(objectName);
                    } else if (type.equals("GlobalRequestProcessor")) {
                        globalRequestProcessors.add(objectName);
                    } else if (type.equals("RequestProcessor")) {
                        requestProcessors.add(objectName);
                    }
                }
            } else if (notification.getType().equals(MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
                String type = objectName.getKeyProperty("type");
                if (type != null) {
                    if (type.equals("ThreadPool")) {
                        threadPools.remove(objectName);
                    } else if (type.equals("GlobalRequestProcessor")) {
                        globalRequestProcessors.remove(objectName);
                    } else if (type.equals("RequestProcessor")) {
                        requestProcessors.remove(objectName);
                    }
                }
            }
        }
    }
}
