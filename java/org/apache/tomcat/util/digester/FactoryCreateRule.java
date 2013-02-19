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

import org.xml.sax.Attributes;


/**
 * <p>Rule implementation that uses an {@link ObjectCreationFactory} to create
 * a new object which it pushes onto the object stack.  When the element is
 * complete, the object will be popped.</p>
 *
 * <p>This rule is intended in situations where the element's attributes are
 * needed before the object can be created.  A common scenario is for the
 * ObjectCreationFactory implementation to use the attributes  as parameters
 * in a call to either a factory method or to a non-empty constructor.
 */

public class FactoryCreateRule extends Rule {

    // ----------------------------------------------------------- Fields

    /** Should exceptions thrown by the factory be ignored? */
    private boolean ignoreCreateExceptions;
    /** Stock to manage */
    private ArrayStack<Boolean> exceptionIgnoredStack;


    // ----------------------------------------------------------- Constructors

    /**
     * Construct a factory create rule using the given, already instantiated,
     * {@link ObjectCreationFactory}.
     *
     * @param creationFactory called on to create the object.
     * @param ignoreCreateExceptions if true, exceptions thrown by the object
     *  creation factory will be ignored.
     */
    public FactoryCreateRule(
                            ObjectCreationFactory creationFactory,
                            boolean ignoreCreateExceptions) {

        this.creationFactory = creationFactory;
        this.ignoreCreateExceptions = ignoreCreateExceptions;
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * The object creation factory we will use to instantiate objects
     * as required based on the attributes specified in the matched XML
     * element.
     */
    protected ObjectCreationFactory creationFactory = null;


    // --------------------------------------------------------- Public Methods


    /**
     * Process the beginning of this element.
     *
     * @param attributes The attribute list of this element
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes) throws Exception {

        if (ignoreCreateExceptions) {

            if (exceptionIgnoredStack == null) {
                exceptionIgnoredStack = new ArrayStack<>();
            }

            try {
                Object instance = creationFactory.createObject(attributes);

                if (digester.log.isDebugEnabled()) {
                    digester.log.debug("[FactoryCreateRule]{" + digester.match +
                            "} New " + instance.getClass().getName());
                }
                digester.push(instance);
                exceptionIgnoredStack.push(Boolean.FALSE);

            } catch (Exception e) {
                // log message and error
                if (digester.log.isInfoEnabled()) {
                    digester.log.info("[FactoryCreateRule] Create exception ignored: " +
                        ((e.getMessage() == null) ? e.getClass().getName() : e.getMessage()));
                    if (digester.log.isDebugEnabled()) {
                        digester.log.debug("[FactoryCreateRule] Ignored exception:", e);
                    }
                }
                exceptionIgnoredStack.push(Boolean.TRUE);
            }

        } else {
            Object instance = creationFactory.createObject(attributes);

            if (digester.log.isDebugEnabled()) {
                digester.log.debug("[FactoryCreateRule]{" + digester.match +
                        "} New " + instance.getClass().getName());
            }
            digester.push(instance);
        }
    }


    /**
     * Process the end of this element.
     */
    @Override
    public void end(String namespace, String name) throws Exception {

        // check if object was created
        // this only happens if an exception was thrown and we're ignoring them
        if (
                ignoreCreateExceptions &&
                exceptionIgnoredStack != null &&
                !(exceptionIgnoredStack.empty())) {

            if ((exceptionIgnoredStack.pop()).booleanValue()) {
                // creation exception was ignored
                // nothing was put onto the stack
                if (digester.log.isTraceEnabled()) {
                    digester.log.trace("[FactoryCreateRule] No creation so no push so no pop");
                }
                return;
            }
        }

        Object top = digester.pop();
        if (digester.log.isDebugEnabled()) {
            digester.log.debug("[FactoryCreateRule]{" + digester.match +
                    "} Pop " + top.getClass().getName());
        }

    }


    /**
     * Clean up after parsing is complete.
     */
    @Override
    public void finish() throws Exception {
        // NO-OP
    }


    /**
     * Render a printable version of this Rule.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("FactoryCreateRule[");
        if (creationFactory != null) {
            sb.append("creationFactory=");
            sb.append(creationFactory);
        }
        sb.append("]");
        return (sb.toString());

    }
}
