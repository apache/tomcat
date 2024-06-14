/**
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
package org.apache.catalina.storeconfig;

import java.beans.IndexedPropertyDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.catalina.Globals;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.net.SocketProperties;

/**
 * Store the Connector attributes. Connector has really special design. A Connector is only a startup Wrapper for a
 * ProtocolHandler. This meant that ProtocolHandler get all there attributes from the Connector attribute map. Strange
 * is that some attributes change their name.
 */
public class ConnectorStoreAppender extends StoreAppender {

    protected static final HashMap<String,String> replacements = new HashMap<>();
    protected static final Set<String> internalExecutorAttributes = new HashSet<>();
    static {
        replacements.put("timeout", "connectionUploadTimeout");
        replacements.put("randomfile", "randomFile");

        internalExecutorAttributes.add("maxThreads");
        internalExecutorAttributes.add("minSpareThreads");
        internalExecutorAttributes.add("threadPriority");
    }

    @Override
    public void printAttributes(PrintWriter writer, int indent, boolean include, Object bean, StoreDescription desc)
            throws Exception {

        // Render a className attribute if requested
        if (include && !desc.isStandard()) {
            writer.print(" className=\"");
            writer.print(bean.getClass().getName());
            writer.print("\"");
        }

        Connector connector = (Connector) bean;
        String protocol = connector.getProtocol();
        List<String> propertyKeys = getPropertyKeys(connector);
        // Create blank instance
        Object bean2 = new Connector(protocol);// defaultInstance(bean);
        for (String key : propertyKeys) {
            Object value = IntrospectionUtils.getProperty(bean, key);
            if (desc.isTransientAttribute(key)) {
                continue; // Skip the specified exceptions
            }
            if (value == null) {
                continue; // Null values are not persisted
            }
            if (!isPersistable(value.getClass())) {
                continue;
            }
            Object value2 = IntrospectionUtils.getProperty(bean2, key);
            if (value.equals(value2)) {
                // The property has its default value
                continue;
            }
            if (isPrintValue(bean, bean2, key, desc)) {
                printValue(writer, indent, key, value);
            }
        }
        if (protocol != null && !"HTTP/1.1".equals(protocol)) {
            super.printValue(writer, indent, "protocol", protocol);
        }
        String executorName = connector.getExecutorName();
        if (!Connector.INTERNAL_EXECUTOR_NAME.equals(executorName)) {
            super.printValue(writer, indent, "executor", executorName);
        }

    }

    /**
     * Get all properties from Connector and current ProtocolHandler.
     *
     * @param bean The connector
     *
     * @return List of Connector property names
     *
     * @throws IntrospectionException Error introspecting connector
     */
    protected List<String> getPropertyKeys(Connector bean) throws IntrospectionException {
        List<String> propertyKeys = new ArrayList<>();
        // Acquire the list of properties for this bean
        ProtocolHandler protocolHandler = bean.getProtocolHandler();
        // Acquire the list of properties for this bean
        PropertyDescriptor descriptors[] = Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors();
        if (descriptors == null) {
            descriptors = new PropertyDescriptor[0];
        }
        for (PropertyDescriptor descriptor : descriptors) {
            if (descriptor instanceof IndexedPropertyDescriptor) {
                continue; // Indexed properties are not persisted
            }
            if (!isPersistable(descriptor.getPropertyType()) || (descriptor.getReadMethod() == null) ||
                    (descriptor.getWriteMethod() == null)) {
                continue; // Must be a read-write primitive or String
            }
            if ("protocol".equals(descriptor.getName()) || "protocolHandlerClassName".equals(descriptor.getName())) {
                continue;
            }
            propertyKeys.add(descriptor.getName());
        }
        // Add the properties of the protocol handler
        descriptors = Introspector.getBeanInfo(protocolHandler.getClass()).getPropertyDescriptors();
        if (descriptors == null) {
            descriptors = new PropertyDescriptor[0];
        }
        for (PropertyDescriptor descriptor : descriptors) {
            if (descriptor instanceof IndexedPropertyDescriptor) {
                continue; // Indexed properties are not persisted
            }
            if (!isPersistable(descriptor.getPropertyType()) || (descriptor.getReadMethod() == null) ||
                    (descriptor.getWriteMethod() == null)) {
                continue; // Must be a read-write primitive or String
            }
            String key = descriptor.getName();
            if (!Connector.INTERNAL_EXECUTOR_NAME.equals(bean.getExecutorName()) &&
                    internalExecutorAttributes.contains(key)) {
                continue;
            }
            if (replacements.get(key) != null) {
                key = replacements.get(key);
            }
            if (!propertyKeys.contains(key)) {
                propertyKeys.add(key);
            }
        }
        // Add the properties for the socket
        final String socketName = "socket.";
        descriptors = Introspector.getBeanInfo(SocketProperties.class).getPropertyDescriptors();
        if (descriptors == null) {
            descriptors = new PropertyDescriptor[0];
        }
        for (PropertyDescriptor descriptor : descriptors) {
            if (descriptor instanceof IndexedPropertyDescriptor) {
                continue; // Indexed properties are not persisted
            }
            if (!isPersistable(descriptor.getPropertyType()) || (descriptor.getReadMethod() == null) ||
                    (descriptor.getWriteMethod() == null)) {
                continue; // Must be a read-write primitive or String
            }
            String key = descriptor.getName();
            if (replacements.get(key) != null) {
                key = replacements.get(key);
            }
            if (!propertyKeys.contains(key)) {
                // Add socket.[original name] if this is not a property
                // that could be set elsewhere
                propertyKeys.add(socketName + descriptor.getName());
            }
        }
        return propertyKeys;
    }

    /**
     * Print Attributes for the connector
     *
     * @param aWriter Current writer
     * @param indent  Indentation level
     * @param bean    The connector bean
     * @param aDesc   The connector description
     *
     * @throws Exception Store error occurred
     */
    protected void storeConnectorAttributes(PrintWriter aWriter, int indent, Object bean, StoreDescription aDesc)
            throws Exception {
        if (aDesc.isAttributes()) {
            printAttributes(aWriter, indent, false, bean, aDesc);
        }
    }

    @Override
    public void printOpenTag(PrintWriter aWriter, int indent, Object bean, StoreDescription aDesc) throws Exception {
        aWriter.print("<");
        aWriter.print(aDesc.getTag());
        storeConnectorAttributes(aWriter, indent, bean, aDesc);
        aWriter.println(">");
    }

    @Override
    public void printTag(PrintWriter aWriter, int indent, Object bean, StoreDescription aDesc) throws Exception {
        aWriter.print("<");
        aWriter.print(aDesc.getTag());
        storeConnectorAttributes(aWriter, indent, bean, aDesc);
        aWriter.println("/>");
    }

    @Override
    public void printValue(PrintWriter writer, int indent, String name, Object value) {
        String repl = name;
        if (replacements.get(name) != null) {
            repl = replacements.get(name);
        }
        super.printValue(writer, indent, repl, value);
    }

    /**
     * Print Connector Values.
     * <ul>
     * <li>Special handling to default jkHome.</li>
     * <li>Don't save catalina.base path at server.xml</li>
     * <li>
     * </ul>
     *
     * @see org.apache.catalina.storeconfig.StoreAppender#isPrintValue(Object, Object, String, StoreDescription)
     */
    @Override
    public boolean isPrintValue(Object bean, Object bean2, String attrName, StoreDescription desc) {
        boolean isPrint = super.isPrintValue(bean, bean2, attrName, desc);
        if (isPrint) {
            if ("jkHome".equals(attrName)) {
                Connector connector = (Connector) bean;
                File catalinaBase = getCatalinaBase();
                File jkHomeBase = getJkHomeBase((String) connector.getProperty("jkHome"), catalinaBase);
                isPrint = !catalinaBase.equals(jkHomeBase);

            }
        }
        return isPrint;
    }

    protected File getCatalinaBase() {

        File file = new File(System.getProperty(Globals.CATALINA_BASE_PROP));
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {
        }
        return file;
    }

    protected File getJkHomeBase(String jkHome, File appBase) {

        File jkHomeBase;
        File file = new File(jkHome);
        if (!file.isAbsolute()) {
            file = new File(appBase, jkHome);
        }
        try {
            jkHomeBase = file.getCanonicalFile();
        } catch (IOException e) {
            jkHomeBase = file;
        }
        return jkHomeBase;
    }

}