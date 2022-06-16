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
package org.apache.naming.factory;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.naming.ResourceRef;
import org.apache.naming.StringManager;

/**
 * Object factory for any Resource conforming to the JavaBean spec.
 *
 * <p>This factory can be configured in a <code>&lt;Context&gt;</code> element
 * in your <code>conf/server.xml</code>
 * configuration file.  An example of factory configuration is:</p>
 * <pre>
 * &lt;Resource name="jdbc/myDataSource" auth="SERVLET"
 *   type="oracle.jdbc.pool.OracleConnectionCacheImpl"/&gt;
 * &lt;ResourceParams name="jdbc/myDataSource"&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;factory&lt;/name&gt;
 *     &lt;value&gt;org.apache.naming.factory.BeanFactory&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;driverType&lt;/name&gt;
 *     &lt;value&gt;thin&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;serverName&lt;/name&gt;
 *     &lt;value&gt;hue&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;networkProtocol&lt;/name&gt;
 *     &lt;value&gt;tcp&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;databaseName&lt;/name&gt;
 *     &lt;value&gt;XXXX&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;portNumber&lt;/name&gt;
 *     &lt;value&gt;NNNN&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;user&lt;/name&gt;
 *     &lt;value&gt;XXXX&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;password&lt;/name&gt;
 *     &lt;value&gt;XXXX&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;maxLimit&lt;/name&gt;
 *     &lt;value&gt;5&lt;/value&gt;
 *   &lt;/parameter&gt;
 * &lt;/ResourceParams&gt;
 * </pre>
 *
 * @author Aner Perez [aner at ncstech.com]
 */
public class BeanFactory implements ObjectFactory {

    private static final StringManager sm = StringManager.getManager(BeanFactory.class);

    private final Log log = LogFactory.getLog(BeanFactory.class); // Not static

    /**
     * Create a new Bean instance.
     *
     * @param obj The reference object describing the Bean
     */
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?,?> environment)
            throws NamingException {

        if (obj instanceof ResourceRef) {

            try {
                Reference ref = (Reference) obj;
                String beanClassName = ref.getClassName();
                Class<?> beanClass = null;
                ClassLoader tcl = Thread.currentThread().getContextClassLoader();
                try {
                    if (tcl != null) {
                        beanClass = tcl.loadClass(beanClassName);
                    } else {
                        beanClass = Class.forName(beanClassName);
                    }
                } catch(ClassNotFoundException cnfe) {
                    NamingException ne = new NamingException(sm.getString("beanFactory.classNotFound", beanClassName));
                    ne.initCause(cnfe);
                    throw ne;
                }

                BeanInfo bi = Introspector.getBeanInfo(beanClass);
                PropertyDescriptor[] pda = bi.getPropertyDescriptors();

                Object bean = beanClass.getConstructor().newInstance();

                // Look for the removed forceString option
                RefAddr ra = ref.get("forceString");
                if (ra != null) {
                    log.warn(sm.getString("beanFactory.noForceString"));
                }

                Enumeration<RefAddr> e = ref.getAll();
                String value;

                while (e.hasMoreElements()) {

                    ra = e.nextElement();
                    String propName = ra.getType();

                    if (propName.equals(Constants.FACTORY) ||
                        propName.equals("scope") || propName.equals("auth") ||
                        propName.equals("forceString") ||
                        propName.equals("singleton")) {
                        continue;
                    }

                    value = (String)ra.getContent();

                    Object[] valueArray = new Object[1];

                    int i = 0;
                    for (i = 0; i < pda.length; i++) {

                        if (pda[i].getName().equals(propName)) {

                            Class<?> propType = pda[i].getPropertyType();
                            Method setProp = pda[i].getWriteMethod();

                            if (propType.equals(String.class)) {
                                valueArray[0] = value;
                            } else if (propType.equals(Character.class) || propType.equals(char.class)) {
                                valueArray[0] = Character.valueOf(value.charAt(0));
                            } else if (propType.equals(Byte.class) || propType.equals(byte.class)) {
                                valueArray[0] = Byte.valueOf(value);
                            } else if (propType.equals(Short.class) || propType.equals(short.class)) {
                                valueArray[0] = Short.valueOf(value);
                            } else if (propType.equals(Integer.class) || propType.equals(int.class)) {
                                valueArray[0] = Integer.valueOf(value);
                            } else if (propType.equals(Long.class) || propType.equals(long.class)) {
                                valueArray[0] = Long.valueOf(value);
                            } else if (propType.equals(Float.class) || propType.equals(float.class)) {
                                valueArray[0] = Float.valueOf(value);
                            } else if (propType.equals(Double.class) || propType.equals(double.class)) {
                                valueArray[0] = Double.valueOf(value);
                            } else if (propType.equals(Boolean.class) || propType.equals(boolean.class)) {
                                valueArray[0] = Boolean.valueOf(value);
                            } else if (setProp != null) {
                                // This is a Tomcat specific extension and is not part of the
                                // Java Bean specification.
                                String setterName = setProp.getName();
                                try {
                                    setProp = bean.getClass().getMethod(setterName, String.class);
                                    valueArray[0] = value;
                                } catch (NoSuchMethodException nsme) {
                                    throw new NamingException(sm.getString(
                                            "beanFactory.noStringConversion", propName, propType.getName()));
                                }
                            } else {
                                throw new NamingException(sm.getString(
                                        "beanFactory.noStringConversion", propName, propType.getName()));
                            }

                            if (setProp != null) {
                                setProp.invoke(bean, valueArray);
                            } else {
                                throw new NamingException(sm.getString("beanFactory.readOnlyProperty", propName));
                            }

                            break;
                        }
                    }

                    if (i == pda.length) {
                        throw new NamingException(sm.getString("beanFactory.noSetMethod", propName));
                    }
                }

                return bean;

            } catch (java.beans.IntrospectionException ie) {
                NamingException ne = new NamingException(ie.getMessage());
                ne.setRootCause(ie);
                throw ne;
            } catch (java.lang.ReflectiveOperationException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ThreadDeath) {
                    throw (ThreadDeath) cause;
                }
                if (cause instanceof VirtualMachineError) {
                    throw (VirtualMachineError) cause;
                }
                NamingException ne = new NamingException(e.getMessage());
                ne.setRootCause(e);
                throw ne;
            }

        } else {
            return null;
        }
    }
}
