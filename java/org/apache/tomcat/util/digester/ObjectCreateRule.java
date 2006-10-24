/* $Id$
 *
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


import org.xml.sax.Attributes;


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
     * @param digester The associated Digester
     * @param className Java class name of the object to be created
     *
     * @deprecated The digester instance is now set in the {@link Digester#addRule} method. 
     * Use {@link #ObjectCreateRule(String className)} instead.
     */
    public ObjectCreateRule(Digester digester, String className) {

        this(className);

    }


    /**
     * Construct an object create rule with the specified class.
     *
     * @param digester The associated Digester
     * @param clazz Java class name of the object to be created
     *
     * @deprecated The digester instance is now set in the {@link Digester#addRule} method. 
     * Use {@link #ObjectCreateRule(Class clazz)} instead.
     */
    public ObjectCreateRule(Digester digester, Class clazz) {

        this(clazz);

    }


    /**
     * Construct an object create rule with the specified class name and an
     * optional attribute name containing an override.
     *
     * @param digester The associated Digester
     * @param className Java class name of the object to be created
     * @param attributeName Attribute name which, if present, contains an
     *  override of the class name to create
     *
     * @deprecated The digester instance is now set in the {@link Digester#addRule} method. 
     * Use {@link #ObjectCreateRule(String className, String attributeName)} instead.
     */
    public ObjectCreateRule(Digester digester, String className,
                            String attributeName) {

        this (className, attributeName);

    }


    /**
     * Construct an object create rule with the specified class and an
     * optional attribute name containing an override.
     *
     * @param digester The associated Digester
     * @param attributeName Attribute name which, if present, contains an
     * @param clazz Java class name of the object to be created
     *  override of the class name to create
     *
     * @deprecated The digester instance is now set in the {@link Digester#addRule} method. 
     * Use {@link #ObjectCreateRule(String attributeName, Class clazz)} instead.
     */
    public ObjectCreateRule(Digester digester,
                            String attributeName,
                            Class clazz) {

        this(attributeName, clazz);

    }

    /**
     * Construct an object create rule with the specified class name.
     *
     * @param className Java class name of the object to be created
     */
    public ObjectCreateRule(String className) {

        this(className, (String) null);

    }


    /**
     * Construct an object create rule with the specified class.
     *
     * @param clazz Java class name of the object to be created
     */
    public ObjectCreateRule(Class clazz) {

        this(clazz.getName(), (String) null);

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


    /**
     * Construct an object create rule with the specified class and an
     * optional attribute name containing an override.
     *
     * @param attributeName Attribute name which, if present, contains an
     * @param clazz Java class name of the object to be created
     *  override of the class name to create
     */
    public ObjectCreateRule(String attributeName,
                            Class clazz) {

        this(clazz.getName(), attributeName);

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
     * @param attributes The attribute list of this element
     */
    public void begin(Attributes attributes) throws Exception {

        // Identify the name of the class to instantiate
        String realClassName = className;
        if (attributeName != null) {
            String value = attributes.getValue(attributeName);
            if (value != null) {
                realClassName = value;
            }
        }
        if (digester.log.isDebugEnabled()) {
            digester.log.debug("[ObjectCreateRule]{" + digester.match +
                    "}New " + realClassName);
        }

        // Instantiate the new object and push it on the context stack
        Class clazz = digester.getClassLoader().loadClass(realClassName);
        Object instance = clazz.newInstance();
        digester.push(instance);

    }


    /**
     * Process the end of this element.
     */
    public void end() throws Exception {

        Object top = digester.pop();
        if (digester.log.isDebugEnabled()) {
            digester.log.debug("[ObjectCreateRule]{" + digester.match +
                    "} Pop " + top.getClass().getName());
        }

    }


    /**
     * Render a printable version of this Rule.
     */
    public String toString() {

        StringBuffer sb = new StringBuffer("ObjectCreateRule[");
        sb.append("className=");
        sb.append(className);
        sb.append(", attributeName=");
        sb.append(attributeName);
        sb.append("]");
        return (sb.toString());

    }


}
