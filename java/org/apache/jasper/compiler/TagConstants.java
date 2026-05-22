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
package org.apache.jasper.compiler;

/**
 * Constants for JSP tag action names and URIs used during compilation.
 */
public interface TagConstants {

    /**
     * The standard JSP namespace URI.
     */
    String JSP_URI = "http://java.sun.com/JSP/Page";

    /**
     * Prefix for directive action names.
     */
    String DIRECTIVE_ACTION = "directive.";

    /**
     * Internal name for the root action.
     */
    String ROOT_ACTION = "root";

    /**
     * JSP namespace name for the root action.
     */
    String JSP_ROOT_ACTION = "jsp:root";

    /**
     * Internal name for the page directive action.
     */
    String PAGE_DIRECTIVE_ACTION = "directive.page";

    /**
     * JSP namespace name for the page directive action.
     */
    String JSP_PAGE_DIRECTIVE_ACTION = "jsp:directive.page";

    /**
     * Internal name for the include directive action.
     */
    String INCLUDE_DIRECTIVE_ACTION = "directive.include";

    /**
     * JSP namespace name for the include directive action.
     */
    String JSP_INCLUDE_DIRECTIVE_ACTION = "jsp:directive.include";

    /**
     * Internal name for the declaration action.
     */
    String DECLARATION_ACTION = "declaration";

    /**
     * JSP namespace name for the declaration action.
     */
    String JSP_DECLARATION_ACTION = "jsp:declaration";

    /**
     * Internal name for the scriptlet action.
     */
    String SCRIPTLET_ACTION = "scriptlet";

    /**
     * JSP namespace name for the scriptlet action.
     */
    String JSP_SCRIPTLET_ACTION = "jsp:scriptlet";

    /**
     * Internal name for the expression action.
     */
    String EXPRESSION_ACTION = "expression";

    /**
     * JSP namespace name for the expression action.
     */
    String JSP_EXPRESSION_ACTION = "jsp:expression";

    /**
     * Internal name for the useBean action.
     */
    String USE_BEAN_ACTION = "useBean";

    /**
     * JSP namespace name for the useBean action.
     */
    String JSP_USE_BEAN_ACTION = "jsp:useBean";

    /**
     * Internal name for the setProperty action.
     */
    String SET_PROPERTY_ACTION = "setProperty";

    /**
     * JSP namespace name for the setProperty action.
     */
    String JSP_SET_PROPERTY_ACTION = "jsp:setProperty";

    /**
     * Internal name for the getProperty action.
     */
    String GET_PROPERTY_ACTION = "getProperty";

    /**
     * JSP namespace name for the getProperty action.
     */
    String JSP_GET_PROPERTY_ACTION = "jsp:getProperty";

    /**
     * Internal name for the include action.
     */
    String INCLUDE_ACTION = "include";

    /**
     * JSP namespace name for the include action.
     */
    String JSP_INCLUDE_ACTION = "jsp:include";

    /**
     * Internal name for the forward action.
     */
    String FORWARD_ACTION = "forward";

    /**
     * JSP namespace name for the forward action.
     */
    String JSP_FORWARD_ACTION = "jsp:forward";

    /**
     * Internal name for the param action.
     */
    String PARAM_ACTION = "param";

    /**
     * JSP namespace name for the param action.
     */
    String JSP_PARAM_ACTION = "jsp:param";

    /**
     * Internal name for the text action.
     */
    String TEXT_ACTION = "text";

    /**
     * JSP namespace name for the text action.
     */
    String JSP_TEXT_ACTION = "jsp:text";

    /**
     * Closing tag for the text action.
     */
    String JSP_TEXT_ACTION_END = "</jsp:text>";

    /**
     * Internal name for the attribute action.
     */
    String ATTRIBUTE_ACTION = "attribute";

    /**
     * JSP namespace name for the attribute action.
     */
    String JSP_ATTRIBUTE_ACTION = "jsp:attribute";

    /**
     * Internal name for the body action.
     */
    String BODY_ACTION = "body";

    /**
     * JSP namespace name for the body action.
     */
    String JSP_BODY_ACTION = "jsp:body";

    /**
     * Internal name for the element action.
     */
    String ELEMENT_ACTION = "element";

    /**
     * JSP namespace name for the element action.
     */
    String JSP_ELEMENT_ACTION = "jsp:element";

    /**
     * Internal name for the output action.
     */
    String OUTPUT_ACTION = "output";

    /**
     * JSP namespace name for the output action.
     */
    String JSP_OUTPUT_ACTION = "jsp:output";

    /**
     * Internal name for the taglib directive action.
     */
    String TAGLIB_DIRECTIVE_ACTION = "taglib";

    /**
     * JSP namespace name for the taglib directive action.
     */
    String JSP_TAGLIB_DIRECTIVE_ACTION = "jsp:taglib";

    // Tag Files
    /**
     * Internal name for the invoke action.
     */
    String INVOKE_ACTION = "invoke";

    /**
     * JSP namespace name for the invoke action.
     */
    String JSP_INVOKE_ACTION = "jsp:invoke";

    /**
     * Internal name for the doBody action.
     */
    String DOBODY_ACTION = "doBody";

    /**
     * JSP namespace name for the doBody action.
     */
    String JSP_DOBODY_ACTION = "jsp:doBody";

    // Tag File Directives
    /**
     * Internal name for the tag directive action.
     */
    String TAG_DIRECTIVE_ACTION = "directive.tag";

    /**
     * JSP namespace name for the tag directive action.
     */
    String JSP_TAG_DIRECTIVE_ACTION = "jsp:directive.tag";

    /**
     * Internal name for the attribute directive action.
     */
    String ATTRIBUTE_DIRECTIVE_ACTION = "directive.attribute";

    /**
     * JSP namespace name for the attribute directive action.
     */
    String JSP_ATTRIBUTE_DIRECTIVE_ACTION = "jsp:directive.attribute";

    /**
     * Internal name for the variable directive action.
     */
    String VARIABLE_DIRECTIVE_ACTION = "directive.variable";

    /**
     * JSP namespace name for the variable directive action.
     */
    String JSP_VARIABLE_DIRECTIVE_ACTION = "jsp:directive.variable";

    // Directive attributes
    /**
     * URI prefix for tag directory namespaces.
     */
    String URN_JSPTAGDIR = "urn:jsptagdir:";

    /**
     * URI prefix for TLD namespaces.
     */
    String URN_JSPTLD = "urn:jsptld:";
}
