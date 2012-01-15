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
import java.util.Set;

import javax.management.Attribute;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.mbeans.MBeanDumper;
import org.apache.tomcat.util.modeler.Registry;

/**
 * This servlet will dump JMX attributes in a simple format
 * and implement proxy services for modeler.
 *
 * @author Costin Manolache
 */
public class JMXProxyServlet extends HttpServlet  {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------- Instance Variables
    /**
     * MBean server.
     */
    protected transient MBeanServer mBeanServer = null;
    protected transient Registry registry;

    // --------------------------------------------------------- Public Methods
    /**
     * Initialize this servlet.
     */
    @Override
    public void init() throws ServletException {
        // Retrieve the MBean server
        registry = Registry.getRegistry(null, null);
        mBeanServer = Registry.getRegistry(null, null).getMBeanServer();
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
        throws IOException, ServletException
    {

        response.setContentType("text/plain");

        PrintWriter writer = response.getWriter();

        if( mBeanServer==null ) {
            writer.println("Error - No mbean server");
            return;
        }

        String qry=request.getParameter("set");
        if( qry!= null ) {
            String name=request.getParameter("att");
            String val=request.getParameter("val");

            setAttribute( writer, qry, name, val );
            return;
        }
        qry=request.getParameter("get");
        if( qry!= null ) {
            String name=request.getParameter("att");
            getAttribute( writer, qry, name );
            return;
        }
        qry = request.getParameter("invoke");
        if(qry != null) {
            String opName=request.getParameter("op");
            String ps = request.getParameter("ps");
            String[] valuesStr;
            if (ps == null) {
                valuesStr = new String[0];
            } else {
                valuesStr = request.getParameter("ps").split(",");
            }
            invokeOperation( writer, qry, opName,valuesStr );
            return;
        }
        qry=request.getParameter("qry");
        if( qry == null ) {
            qry = "*:*";
        }

        listBeans( writer, qry );

    }

    public void getAttribute(PrintWriter writer, String onameStr, String att) {
        try {
            ObjectName oname = new ObjectName(onameStr);
            Object value = mBeanServer.getAttribute(oname, att);
            writer.println("OK - Attribute get '" + onameStr + "' - " + att
                    + "= " + MBeanDumper.escape(value.toString()));
        } catch (Exception ex) {
            writer.println("Error");
            ex.printStackTrace(writer);
        }
    }

    public void setAttribute( PrintWriter writer,
                              String onameStr, String att, String val )
    {
        try {
            ObjectName oname=new ObjectName( onameStr );
            String type=registry.getType(oname, att);
            Object valueObj=registry.convertValue(type, val );
            mBeanServer.setAttribute( oname, new Attribute(att, valueObj));
            writer.println("OK - Attribute set");
        } catch( Exception ex ) {
            writer.println("Error");
            ex.printStackTrace(writer);
        }
    }

    public void listBeans( PrintWriter writer, String qry )
    {

        Set<ObjectName> names = null;
        try {
            names=mBeanServer.queryNames(new ObjectName(qry), null);
            writer.println("OK - Number of results: " + names.size());
            writer.println();
        } catch (Exception ex) {
            writer.println("Error");
            ex.printStackTrace(writer);
            return;
        }

        String dump = MBeanDumper.dumpBeans(mBeanServer, names);
        writer.print(dump);
    }

    /**
     * Determines if a type is supported by the {@link JMXProxyServlet}.
     *
     * @param type  The type to check
     * @return      Always returns <code>true</code>
     */
    public boolean isSupported(String type) {
        return true;
    }


    private void invokeOperation(PrintWriter writer, String onameStr, String op,
            String[] valuesStr) {
        try {
            ObjectName oname=new ObjectName( onameStr );
            MBeanOperationInfo methodInfo = registry.getMethodInfo(oname,op);
            MBeanParameterInfo[] signature = methodInfo.getSignature();
            String[] signatureTypes = new String[signature.length];
            Object[] values = new Object[signature.length];
            for (int i = 0; i < signature.length; i++) {
               MBeanParameterInfo pi = signature[i];
               signatureTypes[i] = pi.getType();
               values[i] = registry.convertValue(pi.getType(), valuesStr[i] );
           }

            Object retVal = mBeanServer.invoke(oname,op,values,signatureTypes);
            writer.println("OK - Operation " + op + " returned:");
            output("", writer, retVal);
        } catch( Exception ex ) {
            writer.println("Error");
            ex.printStackTrace(writer);
        }
    }

    private void output(String indent, PrintWriter writer, Object result) {
        if (result instanceof Object[]) {
            for (Object obj : (Object[]) result) {
                output("  " + indent, writer, obj);
            }
        } else {
            writer.println(indent + result.toString());
        }
    }
}
