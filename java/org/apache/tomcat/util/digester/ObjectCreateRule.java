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
package org.apache.tomcat.util.digester;


import org.apache.juli.logging.Log;
import org.xml.sax.Attributes;

import java.net.URL;
import java.net.URLClassLoader;


/**
 * Rule implementation that creates a new object and pushes it
 * onto the object stack.  When the element is complete, the
 * object will be popped
 */

public class ObjectCreateRule extends Rule {


    // ----------------------------------------------------------- Constructors


    /**
     * Construct an object create rule with the specified class name.
     *
     * @param className Java class name of the object to be created
     */
    public ObjectCreateRule(String className) {

        this(className, null);

    }


    /**
     * Construct an object create rule with the specified class name and an
     * optional attribute name containing an override.
     *
     * @param className Java class name of the object to be created
     * @param attributeName Attribute name which, if present, contains an
     *  override of the class name to create
     */
    public ObjectCreateRule(String className,
                            String attributeName) {

        this.className = className;
        this.attributeName = attributeName;

    }


    // ----------------------------------------------------- Instance Variables

    /**
     * The attribute containing an override class name if it is present.
     */
    protected String attributeName = null;


    /**
     * The Java class name of the object to be created.
     */
    protected String className = null;


    // --------------------------------------------------------- Public Methods


    /**
     * Process the beginning of this element.
     *
     * @param namespace the namespace URI of the matching element, or an
     *   empty string if the parser is not namespace aware or the element has
     *   no namespace
     * @param name the local name if the parser is namespace aware, or just
     *   the element name otherwise
     * @param attributes The attribute list for this element
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {

        String realClassName = getRealClassName(attributes);

        if (realClassName == null) {
            throw new NullPointerException(sm.getString("rule.noClassName", namespace, name));
        }

        // Instantiate the new object and push it on the context stack
        ClassLoader classLoader = digester.getClassLoader();
        Object instance;
        try{
            Class<?> clazz = classLoader.loadClass(realClassName);
            instance = clazz.getConstructor().newInstance();
            digester.push(instance);
        }catch (ClassNotFoundException t) {
            Log log = digester.log;
            log.warn(t.getMessage(), t);
            showClassPath(classLoader, 0, log);
            throw t;
        }


        StringBuilder code = digester.getGeneratedCode();
        if (code != null) {
            code.append(System.lineSeparator());
            code.append(System.lineSeparator());
            code.append(realClassName).append(' ').append(digester.toVariableName(instance)).append(" = new ");
            code.append(realClassName).append("();").append(System.lineSeparator());
        }
    }


    /**
     * Return the actual class name of the class to be instantiated.
     * @param attributes The attribute list for this element
     * @return the class name
     */
    protected String getRealClassName(Attributes attributes) {
        // Identify the name of the class to instantiate
        String realClassName = className;
        if (attributeName != null) {
            String value = attributes.getValue(attributeName);
            if (value != null) {
                realClassName = value;
            }
        }
        return realClassName;
    }

    /**
     * Logs the classpath URLs of the given {@link ClassLoader} and its parent class loaders
     * recursively. This is useful for debugging the class loading mechanism.
     *
     * @param classLoader    the {@link ClassLoader} to inspect. If it is a {@link URLClassLoader},
     *                       its URLs will be logged.
     * @param antiRecursion  recursion depth counter to prevent infinite loops through parent class loaders.
     *                       Stops recursion if it exceeds 7.
     * @param log            the {@link Log} to record classpath information and recursion warnings.
     *
     * <p>If the class loader is a {@link URLClassLoader}, the method logs its URLs and proceeds to
     * inspect its parent class loader, stopping if too many recursions occur.</p>
     *
     * @see URLClassLoader#getURLs()
     */
    public static void showClassPath(ClassLoader classLoader, int antiRecursion, Log log) {
        StringBuffer extraInfo = new StringBuffer("ClassLoader " + classLoader + "\n");
        if (classLoader instanceof URLClassLoader) {
            URLClassLoader ucl = (URLClassLoader) classLoader;
            URL[] urls = ucl.getURLs();
            if (null == urls || 0 == urls.length) {
                extraInfo.append("null == urls");
            } else {
                int i = 0;
                for (i = 0; i < urls.length; i++) {
                    URL url = urls[i];
                    extraInfo.append(url.toString()).append(",\n");
                }
                log.warn(extraInfo);
            }
            ClassLoader parent = ucl.getParent();
            if (null != parent) {
                if (!parent.equals(ucl)) {
                    if (7 < antiRecursion) {
                        log.error("too many recursions");
                    } else {
                        antiRecursion++;
                        log.warn("\nParent " + antiRecursion + ":");
                        showClassPath(parent, antiRecursion, log);
                    }
                }
            }
        }
    }


    /**
     * Process the end of this element.
     *
     * @param namespace the namespace URI of the matching element, or an
     *   empty string if the parser is not namespace aware or the element has
     *   no namespace
     * @param name the local name if the parser is namespace aware, or just
     *   the element name otherwise
     */
    @Override
    public void end(String namespace, String name) throws Exception {

        Object top = digester.pop();
        if (digester.log.isTraceEnabled()) {
            digester.log.trace("[ObjectCreateRule]{" + digester.match +
                    "} Pop " + top.getClass().getName());
        }

    }


    /**
     * Render a printable version of this Rule.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ObjectCreateRule[");
        sb.append("className=");
        sb.append(className);
        sb.append(", attributeName=");
        sb.append(attributeName);
        sb.append(']');
        return sb.toString();
    }





}
