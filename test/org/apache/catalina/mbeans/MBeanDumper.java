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
package org.apache.catalina.mbeans;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * General helper to dump MBean contents to the log.
 *
 */
public class MBeanDumper {

    private static Log log = LogFactory.getLog(MBeanDumper.class);

    /**
     * The following code to dump MBeans has been copied from JMXProxyServlet.
     *
     */
    public static void listBeans(MBeanServer mbeanServer, Set<ObjectName> names)
    {

        Iterator<ObjectName> it=names.iterator();
        while( it.hasNext()) {
            ObjectName oname=it.next();
            log.info( "Name: " + oname.toString());

            try {
                MBeanInfo minfo=mbeanServer.getMBeanInfo(oname);
                // can't be null - I think
                String code=minfo.getClassName();
                if ("org.apache.commons.modeler.BaseModelMBean".equals(code)) {
                    code=(String)mbeanServer.getAttribute(oname, "modelerType");
                }
                log.info("modelerType: " + code);

                MBeanAttributeInfo attrs[]=minfo.getAttributes();
                Object value=null;

                for( int i=0; i< attrs.length; i++ ) {
                    if( ! attrs[i].isReadable() ) continue;
                    String attName=attrs[i].getName();
                    if( "modelerType".equals( attName)) continue;
                    if( attName.indexOf( "=") >=0 ||
                            attName.indexOf( ":") >=0 ||
                            attName.indexOf( " ") >=0 ) {
                        continue;
                    }

                    try {
                        value=mbeanServer.getAttribute(oname, attName);
                    } catch( Throwable t) {
                        log.error("Error getting attribute " + oname +
                            " " + attName + " " + t.toString(), t);
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
                        log.info( attName + ": " + valueString);
                    }
                    catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            }
            log.info("");
        }

    }

    public static String escape(String value) {
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

    private static void appendHead( StringBuilder sb, String value, int start, int end) {
        if (end < 1) return;

        int pos=start;
        while( end-pos > 78 ) {
            sb.append( value.substring(pos, pos+78));
            sb.append( "\n ");
            pos=pos+78;
        }
        sb.append( value.substring(pos,end));
    }

}
