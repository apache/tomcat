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
import java.util.Set;
import java.util.StringJoiner;

import javax.management.JMRuntimeException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * General helper to dump MBean contents to the log.
 */
public class MBeanDumper {

    private static final Log log = LogFactory.getLog(MBeanDumper.class);

    private static final String CRLF = "\r\n";


    /**
     * The following code to dump MBeans has been copied from JMXProxyServlet.
     *
     * @param mbeanServer the MBean server
     * @param names a set of object names for which to dump the info
     * @return a string representation of the MBeans
     */
    public static String dumpBeans(MBeanServer mbeanServer, Set<ObjectName> names) {
        StringBuilder buf = new StringBuilder();
        for (ObjectName oname : names) {
            buf.append("Name: ");
            buf.append(oname.toString());
            buf.append(CRLF);

            try {
                MBeanInfo minfo = mbeanServer.getMBeanInfo(oname);
                // can't be null - I think
                String code = minfo.getClassName();
                if ("org.apache.commons.modeler.BaseModelMBean".equals(code)) {
                    code = (String) mbeanServer.getAttribute(oname, "modelerType");
                }
                buf.append("modelerType: ");
                buf.append(code);
                buf.append(CRLF);

                MBeanAttributeInfo attrs[] = minfo.getAttributes();
                Object value = null;

                for (MBeanAttributeInfo attr : attrs) {
                    if (!attr.isReadable())
                        continue;
                    String attName = attr.getName();
                    if ("modelerType".equals(attName))
                        continue;
                    if (attName.indexOf('=') >= 0 || attName.indexOf(':') >= 0
                            || attName.indexOf(' ') >= 0) {
                        continue;
                    }

                    try {
                        value = mbeanServer.getAttribute(oname, attName);
                    } catch (JMRuntimeException rme) {
                        Throwable cause = rme.getCause();
                        if (cause instanceof UnsupportedOperationException) {
                            if (log.isDebugEnabled()) {
                                log.debug("Error getting attribute " + oname + " " + attName, rme);
                            }
                        } else if (cause instanceof NullPointerException) {
                            if (log.isDebugEnabled()) {
                                log.debug("Error getting attribute " + oname + " " + attName, rme);
                            }
                        } else {
                            log.error("Error getting attribute " + oname + " " + attName, rme);
                        }
                        continue;
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        log.error("Error getting attribute " + oname + " " + attName, t);
                        continue;
                    }
                    if (value == null) {
                        continue;
                    }
                    String valueString;
                    try {
                        Class<?> c = value.getClass();
                        if (c.isArray()) {
                            int len = Array.getLength(value);
                            StringBuilder sb = new StringBuilder("Array["
                                    + c.getComponentType().getName() + "] of length " + len);
                            if (len > 0) {
                                sb.append(CRLF);
                            }
                            for (int j = 0; j < len; j++) {
                                Object item = Array.get(value, j);
                                sb.append(tableItemToString(item));
                                if (j < len - 1) {
                                    sb.append(CRLF);
                                }
                            }
                            valueString = sb.toString();
                        } else if (TabularData.class.isInstance(value)) {
                            TabularData tab = TabularData.class.cast(value);
                            StringJoiner joiner = new StringJoiner(CRLF);
                            joiner.add(
                                    "TabularData[" + tab.getTabularType().getRowType().getTypeName()
                                            + "] of length " + tab.size());
                            for (Object item : tab.values()) {
                                joiner.add(tableItemToString(item));
                            }
                            valueString = joiner.toString();
                        } else {
                            valueString = valueToString(value);
                        }
                        buf.append(attName);
                        buf.append(": ");
                        buf.append(valueString);
                        buf.append(CRLF);
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            }
            buf.append(CRLF);
        }
        return buf.toString();
    }


    public static String escape(String value) {
        // The only invalid char is \n
        // We also need to keep the string short and split it with \nSPACE
        // XXX TODO
        int idx = value.indexOf("\n");
        if (idx < 0) {
            return value;
        }

        int prev = 0;
        StringBuilder sb = new StringBuilder();
        while (idx >= 0) {
            appendHead(sb, value, prev, idx);
            sb.append("\\n\n ");
            prev = idx + 1;
            if (idx == value.length() - 1)
                break;
            idx = value.indexOf('\n', idx + 1);
        }
        if (prev < value.length()) {
            appendHead(sb, value, prev, value.length());
        }
        return sb.toString();
    }


    private static void appendHead(StringBuilder sb, String value, int start, int end) {
        if (end < 1) {
            return;
        }

        int pos = start;
        while (end - pos > 78) {
            sb.append(value.substring(pos, pos + 78));
            sb.append("\n ");
            pos = pos + 78;
        }
        sb.append(value.substring(pos, end));
    }


    private static String tableItemToString(Object item) {
        if (item == null) {
            return "\t" + "NULL VALUE";
        } else {
            try {
                return "\t" + valueToString(item);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                return "\t" + "NON-STRINGABLE VALUE";
            }
        }
    }


    private static String valueToString(Object value) {
        String valueString;
        if (CompositeData.class.isInstance(value)) {
            StringBuilder sb = new StringBuilder("{");
            String sep = "";
            CompositeData composite = CompositeData.class.cast(value);
            Set<String> keys = composite.getCompositeType().keySet();
            for (String key : keys) {
                sb.append(sep).append(key).append("=").append(composite.get(key));
                sep = ", ";
            }
            sb.append("}");
            valueString = sb.toString();
        } else {
            valueString = value.toString();
        }
        return escape(valueString);
    }
}
