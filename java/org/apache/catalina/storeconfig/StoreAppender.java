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

package org.apache.catalina.storeconfig;

import java.beans.IndexedPropertyDescriptor;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.Iterator;

import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.descriptor.web.ResourceBase;
import org.apache.tomcat.util.security.Escape;

/**
 * StoreAppends generate really the xml tag elements
 */
public class StoreAppender {

    /**
     * The set of classes that represent persistable properties.
     */
    private static Class<?> persistables[] = { String.class, Integer.class,
            Integer.TYPE, Boolean.class, Boolean.TYPE, Byte.class, Byte.TYPE,
            Character.class, Character.TYPE, Double.class, Double.TYPE,
            Float.class, Float.TYPE, Long.class, Long.TYPE, Short.class,
            Short.TYPE, InetAddress.class };

    private int pos = 0;

    /**
     * Print the closing tag.
     *
     * @param aWriter The output writer
     * @param aDesc Store description of the current element
     * @throws Exception A store error occurred
     */
    public void printCloseTag(PrintWriter aWriter, StoreDescription aDesc)
            throws Exception {
        aWriter.print("</");
        aWriter.print(aDesc.getTag());
        aWriter.println(">");
    }

    /**
     * Print only the open tag with all attributes.
     *
     * @param aWriter The output writer
     * @param indent Indentation level
     * @param bean The current bean that is stored
     * @param aDesc Store description of the current element
     * @throws Exception A store error occurred
     */
    public void printOpenTag(PrintWriter aWriter, int indent, Object bean,
            StoreDescription aDesc) throws Exception {
        aWriter.print("<");
        aWriter.print(aDesc.getTag());
        if (aDesc.isAttributes() && bean != null)
            printAttributes(aWriter, indent, bean, aDesc);
        aWriter.println(">");
    }

    /**
     * Print tag with all attributes
     *
     * @param aWriter The output writer
     * @param indent Indentation level
     * @param bean The current bean that is stored
     * @param aDesc Store description of the current element
     * @throws Exception A store error occurred
     */
    public void printTag(PrintWriter aWriter, int indent, Object bean,
            StoreDescription aDesc) throws Exception {
        aWriter.print("<");
        aWriter.print(aDesc.getTag());
        if (aDesc.isAttributes() && bean != null)
            printAttributes(aWriter, indent, bean, aDesc);
        aWriter.println("/>");
    }

    /**
     * Print the value from tag as content.
     *
     * @param aWriter The output writer
     * @param tag The element name
     * @param content Element content
     * @throws Exception A store error occurred
     */
    public void printTagContent(PrintWriter aWriter, String tag, String content)
            throws Exception {
        aWriter.print("<");
        aWriter.print(tag);
        aWriter.print(">");
        aWriter.print(Escape.xml(content));
        aWriter.print("</");
        aWriter.print(tag);
        aWriter.println(">");
    }

    /**
     * Print an array of values.
     *
     * @param aWriter The output writer
     * @param tag The element name
     * @param indent Indentation level
     * @param elements Array of element values
     */
    public void printTagValueArray(PrintWriter aWriter, String tag, int indent,
            String[] elements) {
        if (elements != null && elements.length > 0) {
            printIndent(aWriter, indent + 2);
            aWriter.print("<");
            aWriter.print(tag);
            aWriter.print(">");
            for (int i = 0; i < elements.length; i++) {
                printIndent(aWriter, indent + 4);
                aWriter.print(elements[i]);
                if (i + 1 < elements.length)
                    aWriter.println(",");
            }
            printIndent(aWriter, indent + 2);
            aWriter.print("</");
            aWriter.print(tag);
            aWriter.println(">");
        }
    }

    /**
     * Print an array of elements.
     *
     * @param aWriter The output writer
     * @param tag The element name
     * @param indent Indentation level
     * @param elements Array of elements
     * @throws Exception Store error occurred
     */
    public void printTagArray(PrintWriter aWriter, String tag, int indent,
            String[] elements) throws Exception {
        if (elements != null) {
            for (int i = 0; i < elements.length; i++) {
                printIndent(aWriter, indent);
                printTagContent(aWriter, tag, elements[i]);
            }
        }
    }

    /**
     * Print some spaces.
     *
     * @param aWriter The output writer
     * @param indent The number of spaces
     */
    public void printIndent(PrintWriter aWriter, int indent) {
        for (int i = 0; i < indent; i++) {
            aWriter.print(' ');
        }
        pos = indent;
    }

    /**
     * Store the relevant attributes of the specified JavaBean, plus a
     * <code>className</code> attribute defining the fully qualified Java
     * class name of the bean.
     *
     * @param writer PrintWriter to which we are storing
     * @param indent Indentation level
     * @param bean
     *            Bean whose properties are to be rendered as attributes,
     * @param desc Store description of the current element
     *
     * @exception Exception
     *                if an exception occurs while storing
     */
    public void printAttributes(PrintWriter writer, int indent, Object bean,
            StoreDescription desc) throws Exception {

        printAttributes(writer, indent, true, bean, desc);

    }

    /**
     * Store the relevant attributes of the specified JavaBean.
     *
     * @param writer PrintWriter to which we are storing
     * @param indent Indentation level
     * @param include
     *            Should we include a <code>className</code> attribute?
     * @param bean
     *            Bean whose properties are to be rendered as attributes,
     * @param desc
     *            RegistryDescriptor from this bean
     *
     * @exception Exception
     *                if an exception occurs while storing
     */
    public void printAttributes(PrintWriter writer, int indent,
            boolean include, Object bean, StoreDescription desc)
            throws Exception {

        // Render a className attribute if requested
        if (include && desc != null && !desc.isStandard()) {
            writer.print(" className=\"");
            writer.print(bean.getClass().getName());
            writer.print("\"");
        }

        // Acquire the list of properties for this bean
        PropertyDescriptor descriptors[] = Introspector.getBeanInfo(
                bean.getClass()).getPropertyDescriptors();
        if (descriptors == null) {
            descriptors = new PropertyDescriptor[0];
        }

        // Create blank instance
        Object bean2 = defaultInstance(bean);
        for (int i = 0; i < descriptors.length; i++) {
            Object value = checkAttribute(desc, descriptors[i], descriptors[i].getName(), bean, bean2);
            if (value != null) {
                printAttribute(writer, indent, bean, desc, descriptors[i].getName(), bean2, value);
            }
        }

        if (bean instanceof ResourceBase) {
            ResourceBase resource = (ResourceBase) bean;
            for (Iterator<String> iter = resource.listProperties(); iter.hasNext();) {
                String name = iter.next();
                Object value = resource.getProperty(name);
                if (!isPersistable(value.getClass())) {
                    continue;
                }
                if (desc.isTransientAttribute(name)) {
                    continue; // Skip the specified exceptions
                }
                printValue(writer, indent, name, value);

            }
        }
    }

    /**
     * Check if the attribute should be printed.
     * @param desc RegistryDescriptor from this bean
     * @param descriptor PropertyDescriptor from this bean property
     * @param attributeName The attribute name to store
     * @param bean The current bean
     * @param bean2 A default instance of the bean for comparison
     * @return null if the value should be skipped, the value to print otherwise
     */
    protected Object checkAttribute(StoreDescription desc, PropertyDescriptor descriptor, String attributeName, Object bean, Object bean2) {
        if (descriptor instanceof IndexedPropertyDescriptor) {
            return null; // Indexed properties are not persisted
        }
        if (!isPersistable(descriptor.getPropertyType())
                || (descriptor.getReadMethod() == null)
                || (descriptor.getWriteMethod() == null)) {
            return null; // Must be a read-write primitive or String
        }
        if (desc.isTransientAttribute(descriptor.getName())) {
            return null; // Skip the specified exceptions
        }
        Object value = IntrospectionUtils.getProperty(bean, descriptor.getName());
        if (value == null) {
            return null; // Null values are not persisted
        }
        Object value2 = IntrospectionUtils.getProperty(bean2, descriptor.getName());
        if (value.equals(value2)) {
            // The property has its default value
            return null;
        }
        return value;
    }

    /**
     * Store the specified of the specified JavaBean.
     *
     * @param writer PrintWriter to which we are storing
     * @param indent Indentation level
     * @param bean The current bean
     * @param desc RegistryDescriptor from this bean
     * @param attributeName The attribute name to store
     * @param bean2 A default instance of the bean for comparison
     * @param value The attribute value
     */
    protected void printAttribute(PrintWriter writer, int indent, Object bean, StoreDescription desc, String attributeName, Object bean2, Object value) {
        if (isPrintValue(bean, bean2, attributeName, desc))
            printValue(writer, indent, attributeName, value);
    }

    /**
     * Determine if the attribute value needs to be stored.
     *
     * @param bean
     *            original bean
     * @param bean2
     *            default bean
     * @param attrName
     *            attribute name
     * @param desc
     *            StoreDescription from bean
     * @return <code>true</code> if the value should be stored
     */
    public boolean isPrintValue(Object bean, Object bean2, String attrName,
            StoreDescription desc) {
        return true;
    }

    /**
     * Generate default Instance for the specified bean.
     *
     * @param bean The bean
     * @return an object from same class as bean parameter
     * @throws ReflectiveOperationException Error creating a new instance
     */
    public Object defaultInstance(Object bean) throws ReflectiveOperationException {
        return bean.getClass().getConstructor().newInstance();
    }

    /**
     * Print an attribute value.
     *
     * @param writer PrintWriter to which we are storing
     * @param indent Indentation level
     * @param name Attribute name
     * @param value Attribute value
     */
    public void printValue(PrintWriter writer, int indent, String name,
            Object value) {
        // Convert IP addresses to strings so they will be persisted
        if (value instanceof InetAddress) {
            value = ((InetAddress) value).getHostAddress();
        }
        if (!(value instanceof String)) {
            value = value.toString();
        }
        String strValue = Escape.xml((String) value);
        pos = pos + name.length() + strValue.length();
        if (pos > 60) {
            writer.println();
            printIndent(writer, indent + 4);
        } else {
            writer.print(' ');
        }
        writer.print(name);
        writer.print("=\"");
        writer.print(strValue);
        writer.print("\"");
    }

    /**
     * Given a string, this method replaces all occurrences of '&lt;', '&gt;',
     * '&amp;', and '"'.
     * @param input The string to escape
     * @return the escaped string
     * @deprecated This method will be removed in Tomcat 9
     */
    @Deprecated
    public String convertStr(String input) {

        StringBuffer filtered = new StringBuffer(input.length());
        char c;
        for (int i = 0; i < input.length(); i++) {
            c = input.charAt(i);
            if (c == '<') {
                filtered.append("&lt;");
            } else if (c == '>') {
                filtered.append("&gt;");
            } else if (c == '\'') {
                filtered.append("&apos;");
            } else if (c == '"') {
                filtered.append("&quot;");
            } else if (c == '&') {
                filtered.append("&amp;");
            } else {
                filtered.append(c);
            }
        }
        return filtered.toString();
    }

    /**
     * Is the specified property type one for which we should generate a
     * persistence attribute?
     *
     * @param clazz
     *            Java class to be tested
     * @return <code>true</code> if the specified class should be stored
     */
    protected boolean isPersistable(Class<?> clazz) {

        for (int i = 0; i < persistables.length; i++) {
            if (persistables[i] == clazz || persistables[i].isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;

    }
}
