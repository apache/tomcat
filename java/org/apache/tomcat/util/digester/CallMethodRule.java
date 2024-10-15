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

import java.util.Arrays;

import org.apache.tomcat.util.IntrospectionUtils;
import org.xml.sax.Attributes;

/**
 * <p>Rule implementation that calls a method on an object on the stack
 * (normally the top/parent object), passing arguments collected from
 * subsequent <code>CallParamRule</code> rules or from the body of this
 * element. </p>
 *
 * <p>By using {@link #CallMethodRule(String methodName)}
 * a method call can be made to a method which accepts no
 * arguments.</p>
 *
 * <p>Incompatible method parameter types are converted
 * using <code>org.apache.commons.beanutils.ConvertUtils</code>.
 * </p>
 *
 * <p>This rule now uses
 * <a href="https://commons.apache.org/beanutils/apidocs/org/apache/commons/beanutils/MethodUtils.html">
 * org.apache.commons.beanutils.MethodUtils#invokeMethod
 * </a> by default.
 * This increases the kinds of methods successfully and allows primitives
 * to be matched by passing in wrapper classes.
 * There are rare cases when org.apache.commons.beanutils.MethodUtils#invokeExactMethod
 * (the old default) is required.
 * This method is much stricter in its reflection.
 * Setting the <code>UseExactMatch</code> to true reverts to the use of this
 * method.</p>
 *
 * <p>Note that the target method is invoked when the  <i>end</i> of
 * the tag the CallMethodRule fired on is encountered, <i>not</i> when the
 * last parameter becomes available. This implies that rules which fire on
 * tags nested within the one associated with the CallMethodRule will
 * fire before the CallMethodRule invokes the target method. This behaviour is
 * not configurable. </p>
 *
 * <p>Note also that if a CallMethodRule is expecting exactly one parameter
 * and that parameter is not available (eg CallParamRule is used with an
 * attribute name but the attribute does not exist) then the method will
 * not be invoked. If a CallMethodRule is expecting more than one parameter,
 * then it is always invoked, regardless of whether the parameters were
 * available or not (missing parameters are passed as null values).</p>
 */
public class CallMethodRule extends Rule {

    // ----------------------------------------------------------- Constructors

    /**
     * Construct a "call method" rule with the specified method name.  The
     * parameter types (if any) default to java.lang.String.
     *
     * @param methodName Method name of the parent method to call
     * @param paramCount The number of parameters to collect, or
     *  zero for a single argument from the body of this element.
     */
    public CallMethodRule(String methodName, int paramCount) {
        this(0, methodName, paramCount);
    }


    /**
     * Construct a "call method" rule with the specified method name.  The
     * parameter types (if any) default to java.lang.String.
     *
     * @param targetOffset location of the target object. Positive numbers are
     * relative to the top of the digester object stack. Negative numbers
     * are relative to the bottom of the stack. Zero implies the top
     * object on the stack.
     * @param methodName Method name of the parent method to call
     * @param paramCount The number of parameters to collect, or
     *  zero for a single argument from the body of this element.
     */
    public CallMethodRule(int targetOffset, String methodName, int paramCount) {
        this.targetOffset = targetOffset;
        this.methodName = methodName;
        this.paramCount = paramCount;
        if (paramCount == 0) {
            this.paramTypes = new Class[] { String.class };
        } else {
            this.paramTypes = new Class[paramCount];
            Arrays.fill(this.paramTypes, String.class);
        }
    }


    /**
     * Construct a "call method" rule with the specified method name.
     * The method should accept no parameters.
     *
     * @param methodName Method name of the parent method to call
     */
    public CallMethodRule(String methodName) {
        this(0, methodName, 0, null);
    }


    /**
     * Construct a "call method" rule with the specified method name and
     * parameter types. If <code>paramCount</code> is set to zero the rule
     * will use the body of this element as the single argument of the
     * method, unless <code>paramTypes</code> is null or empty, in this
     * case the rule will call the specified method with no arguments.
     *
     * @param targetOffset location of the target object. Positive numbers are
     * relative to the top of the digester object stack. Negative numbers
     * are relative to the bottom of the stack. Zero implies the top
     * object on the stack.
     * @param methodName Method name of the parent method to call
     * @param paramCount The number of parameters to collect, or
     *  zero for a single argument from the body of this element
     * @param paramTypes The Java classes that represent the
     *  parameter types of the method arguments
     *  (if you wish to use a primitive type, specify the corresponding
     *  Java wrapper class instead, such as <code>java.lang.Boolean.TYPE</code>
     *  for a <code>boolean</code> parameter)
     */
    public CallMethodRule(int targetOffset, String methodName, int paramCount,
                          Class<?>[] paramTypes) {

        this.targetOffset = targetOffset;
        this.methodName = methodName;
        this.paramCount = paramCount;
        if (paramTypes == null) {
            this.paramTypes = new Class[paramCount];
            Arrays.fill(this.paramTypes, String.class);
        } else {
            this.paramTypes = new Class[paramTypes.length];
            System.arraycopy(paramTypes, 0, this.paramTypes, 0, this.paramTypes.length);
        }
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * The body text collected from this element.
     */
    protected String bodyText = null;


    /**
     * location of the target object for the call, relative to the
     * top of the digester object stack. The default value of zero
     * means the target object is the one on top of the stack.
     */
    protected final int targetOffset;


    /**
     * The method name to call on the parent object.
     */
    protected final String methodName;


    /**
     * The number of parameters to collect from <code>MethodParam</code> rules.
     * If this value is zero, a single parameter will be collected from the
     * body of this element.
     */
    protected final int paramCount;


    /**
     * The parameter types of the parameters to be collected.
     */
    protected Class<?> paramTypes[] = null;


    /**
     * Should <code>MethodUtils.invokeExactMethod</code> be used for reflection.
     */
    protected boolean useExactMatch = false;


    // --------------------------------------------------------- Public Methods

    /**
     * Should <code>MethodUtils.invokeExactMethod</code>
     * be used for the reflection.
     * @return <code>true</code> if invokeExactMethod is used
     */
    public boolean getUseExactMatch() {
        return useExactMatch;
    }


    /**
     * Set whether <code>MethodUtils.invokeExactMethod</code>
     * should be used for the reflection.
     * @param useExactMatch The flag value
     */
    public void setUseExactMatch(boolean useExactMatch) {
        this.useExactMatch = useExactMatch;
    }


    /**
     * Process the start of this element.
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

        // Push an array to capture the parameter values if necessary
        if (paramCount > 0) {
            Object[] parameters = new Object[paramCount];
            digester.pushParams(parameters);
        }

    }


    /**
     * Process the body text of this element.
     *
     * @param namespace the namespace URI of the matching element, or an
     *   empty string if the parser is not namespace aware or the element has
     *   no namespace
     * @param name the local name if the parser is namespace aware, or just
     *   the element name otherwise
     * @param bodyText The body text of this element
     */
    @Override
    public void body(String namespace, String name, String bodyText)
            throws Exception {

        if (paramCount == 0) {
            this.bodyText = bodyText.trim().intern();
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
    @SuppressWarnings("null") // parameters can't trigger NPE
    @Override
    public void end(String namespace, String name) throws Exception {

        // Retrieve or construct the parameter values array
        Object parameters[] = null;
        if (paramCount > 0) {

            parameters = (Object[]) digester.popParams();

            if (digester.log.isTraceEnabled()) {
                for (int i=0,size=parameters.length;i<size;i++) {
                    digester.log.trace("[CallMethodRule](" + i + ")" + parameters[i]) ;
                }
            }

            // In the case where the parameter for the method
            // is taken from an attribute, and that attribute
            // isn't actually defined in the source XML file,
            // skip the method call
            if (paramCount == 1 && parameters[0] == null) {
                return;
            }

        } else if (paramTypes.length != 0) {

            // In the case where the parameter for the method
            // is taken from the body text, but there is no
            // body text included in the source XML file,
            // skip the method call
            if (bodyText == null) {
                return;
            }

            parameters = new Object[1];
            parameters[0] = bodyText;
        }

        // Construct the parameter values array we will need
        // We only do the conversion if the param value is a String and
        // the specified paramType is not String.
        Object paramValues[] = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            // convert nulls and convert stringy parameters
            // for non-stringy param types
            Object param = parameters[i];
            // Tolerate null non-primitive values
            if(null == param && !paramTypes[i].isPrimitive()) {
                paramValues[i] = null;
            } else if(param instanceof String &&
                    !String.class.isAssignableFrom(paramTypes[i])) {

                paramValues[i] =
                        IntrospectionUtils.convert((String) parameters[i], paramTypes[i]);
            } else {
                paramValues[i] = parameters[i];
            }
        }

        // Determine the target object for the method call
        Object target;
        if (targetOffset >= 0) {
            target = digester.peek(targetOffset);
        } else {
            target = digester.peek( digester.getCount() + targetOffset );
        }

        if (target == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("[CallMethodRule]{");
            sb.append(digester.match);
            sb.append("} Call target is null (");
            sb.append("targetOffset=");
            sb.append(targetOffset);
            sb.append(",stackdepth=");
            sb.append(digester.getCount());
            sb.append(')');
            throw new org.xml.sax.SAXException(sb.toString());
        }

        // Invoke the required method on the top object
        if (digester.log.isTraceEnabled()) {
            StringBuilder sb = new StringBuilder("[CallMethodRule]{");
            sb.append(digester.match);
            sb.append("} Call ");
            sb.append(target.getClass().getName());
            sb.append('.');
            sb.append(methodName);
            sb.append('(');
            for (int i = 0; i < paramValues.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                if (paramValues[i] == null) {
                    sb.append("null");
                } else {
                    sb.append(paramValues[i].toString());
                }
                sb.append('/');
                if (paramTypes[i] == null) {
                    sb.append("null");
                } else {
                    sb.append(paramTypes[i].getName());
                }
            }
            sb.append(')');
            digester.log.trace(sb.toString());
        }
        Object result = IntrospectionUtils.callMethodN(target, methodName,
                paramValues, paramTypes);
        processMethodCallResult(result);

        StringBuilder code = digester.getGeneratedCode();
        if (code != null) {
            code.append(digester.toVariableName(target)).append('.').append(methodName);
            code.append('(');
            for (int i = 0; i < paramValues.length; i++) {
                if (i > 0) {
                    code.append(", ");
                }
                if (bodyText != null) {
                    code.append("\"").append(IntrospectionUtils.escape(bodyText)).append("\"");
                } else if (paramValues[i] instanceof String) {
                    code.append("\"").append(IntrospectionUtils.escape(paramValues[i].toString())).append("\"");
                } else {
                    code.append(digester.toVariableName(paramValues[i]));
                }
            }
            code.append(");");
            code.append(System.lineSeparator());
        }
    }


    /**
     * Clean up after parsing is complete.
     */
    @Override
    public void finish() throws Exception {
        bodyText = null;
    }


    /**
     * Subclasses may override this method to perform additional processing of the
     * invoked method's result.
     *
     * @param result the Object returned by the method invoked, possibly null
     */
    protected void processMethodCallResult(Object result) {
        // do nothing
    }


    /**
     * Render a printable version of this Rule.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CallMethodRule[");
        sb.append("methodName=");
        sb.append(methodName);
        sb.append(", paramCount=");
        sb.append(paramCount);
        sb.append(", paramTypes={");
        if (paramTypes != null) {
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(paramTypes[i].getName());
            }
        }
        sb.append('}');
        sb.append(']');
        return sb.toString();
    }
}
