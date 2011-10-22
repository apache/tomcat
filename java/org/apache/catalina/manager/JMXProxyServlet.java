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
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Set;

import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.ExceptionUtils;
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
                    + "= " + escape(value.toString()));
        } catch (Exception ex) {
            writer.println("Error - " + ex.toString());
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
            writer.println("Error - " + ex.toString());
        }
    }

    public void listBeans( PrintWriter writer, String qry )
    {

        Set<ObjectName> names = null;
        try {
            names=mBeanServer.queryNames(new ObjectName(qry), null);
            writer.println("OK - Number of results: " + names.size());
            writer.println();
        } catch (Exception e) {
            writer.println("Error - " + e.toString());
            return;
        }

        Iterator<ObjectName> it=names.iterator();
        while( it.hasNext()) {
            ObjectName oname=it.next();
            writer.println( "Name: " + oname.toString());

            try {
                MBeanInfo minfo=mBeanServer.getMBeanInfo(oname);
                // can't be null - I think
                String code=minfo.getClassName();
                if ("org.apache.commons.modeler.BaseModelMBean".equals(code)) {
                    code=(String)mBeanServer.getAttribute(oname, "modelerType");
                }
                writer.println("modelerType: " + code);

                MBeanAttributeInfo attrs[]=minfo.getAttributes();
                Object value=null;

                for( int i=0; i< attrs.length; i++ ) {
                    if( ! attrs[i].isReadable() ) continue;
                    if( ! isSupported( attrs[i].getType() )) continue;
                    String attName=attrs[i].getName();
                    if( "modelerType".equals( attName)) continue;
                    if( attName.indexOf( "=") >=0 ||
                            attName.indexOf( ":") >=0 ||
                            attName.indexOf( " ") >=0 ) {
                        continue;
                    }

                    try {
                        value=mBeanServer.getAttribute(oname, attName);
                    } catch( Throwable t) {
                        log("Error getting attribute " + oname +
                            " " + attName + " " + t.toString());
                        continue;
                    }
                    if( value==null ) continue;
                    String valueString;
                    try {
                        Class<?> c = value.getClass();
                        if (c.isArray()) {
                            int len = Array.getLength(value);
                            StringBuilder sb = new StringBuilder("Array[" +
                                    c.getComponentType().getName() + "] of length " + len);
                            if (len > 0) {
                                sb.append("\r\n");
                            }
                            for (int j = 0; j < len; j++) {
                                sb.append("\t");
                                Object item = Array.get(value, j);
                                if (item == null) {
                                    sb.append("NULL VALUE");
                                } else {
                                    try {
                                        sb.append(escape(item.toString()));
                                    }
                                    catch (Throwable t) {
                                        ExceptionUtils.handleThrowable(t);
                                        sb.append("NON-STRINGABLE VALUE");
                                    }
                                }
                                if (j < len - 1) {
                                    sb.append("\r\n");
                                }
                            }
                            valueString = sb.toString();
                        }
                        else {
                            valueString = escape(value.toString());
                        }
                        writer.println( attName + ": " + valueString);
                    }
                    catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            }
            writer.println();
        }

    }

    public String escape(String value) {
        // The only invalid char is \n
        // We also need to keep the string short and split it with \nSPACE
        // XXX TODO
        int idx=value.indexOf( "\n" );
        if( idx < 0 ) return value;

        int prev=0;
        StringBuilder sb=new StringBuilder();
        while( idx >= 0 ) {
            appendHead(sb, value, prev, idx);

            sb.append( "\\n\n ");
            prev=idx+1;
            if( idx==value.length() -1 ) break;
            idx=value.indexOf('\n', idx+1);
        }
        if( prev < value.length() )
            appendHead( sb, value, prev, value.length());
        return sb.toString();
    }

    private void appendHead( StringBuilder sb, String value, int start, int end) {
        if (end < 1) return;

        int pos=start;
        while( end-pos > 78 ) {
            sb.append( value.substring(pos, pos+78));
            sb.append( "\n ");
            pos=pos+78;
        }
        sb.append( value.substring(pos,end));
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
}
