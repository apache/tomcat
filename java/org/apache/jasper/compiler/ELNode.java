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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jakarta.servlet.jsp.tagext.FunctionInfo;

import org.apache.jasper.JasperException;

/**
 * This class defines internal representation for an EL Expression. It currently only defines functions. It can be
 * expanded to define all the components of an EL expression, if need to.
 */
public abstract class ELNode {

    /**
     * Creates a new ELNode instance.
     */
    protected ELNode() {
    }

    /**
     * Accepts a visitor for traversing this node.
     *
     * @param v the visitor
     *
     * @throws JasperException if an error occurs during visitation
     */
    public abstract void accept(Visitor v) throws JasperException;


    /**
     * Represents an EL expression: anything in ${ and }.
     */
    public static class Root extends ELNode {

        private final ELNode.Nodes expr;
        private final char type;

        /**
         * Creates a new Root node.
         *
         * @param expr the expression nodes
         * @param type the expression type character
         */
        Root(ELNode.Nodes expr, char type) {
            this.expr = expr;
            this.type = type;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Returns the expression contained in this root node.
         *
         * @return the expression nodes
         */
        public ELNode.Nodes getExpression() {
            return expr;
        }

        /**
         * Returns the type character of this expression.
         *
         * @return the type character
         */
        public char getType() {
            return type;
        }
    }

    /**
     * Represents text outside of EL expression.
     */
    public static class Text extends ELNode {

        private final String text;

        /**
         * Creates a new Text node.
         *
         * @param text the text content
         */
        Text(String text) {
            this.text = text;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Returns the text content.
         *
         * @return the text
         */
        public String getText() {
            return text;
        }
    }

    /**
     * Represents anything in EL expression, other than functions, including function arguments etc.
     */
    public static class ELText extends ELNode {

        private final String text;

        /**
         * Creates a new ELText node.
         *
         * @param text the text content within the EL expression
         */
        ELText(String text) {
            this.text = text;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Returns the text content.
         *
         * @return the text
         */
        public String getText() {
            return text;
        }
    }

    /**
     * Represents a function. Currently only include the prefix and function name, but not its arguments.
     */
    public static class Function extends ELNode {

        private final String prefix;
        private final String name;
        private final String originalText;
        private String uri;
        private FunctionInfo functionInfo;
        private String methodName;
        private String[] parameters;

        /**
         * Creates a new Function node.
         *
         * @param prefix the namespace prefix
         * @param name the function name
         * @param originalText the original function text
         */
        Function(String prefix, String name, String originalText) {
            this.prefix = prefix;
            this.name = name;
            this.originalText = originalText;
        }

        @Override
        public void accept(Visitor v) throws JasperException {
            v.visit(this);
        }

        /**
         * Returns the namespace prefix.
         *
         * @return the prefix
         */
        public String getPrefix() {
            return prefix;
        }

        /**
         * Returns the function name.
         *
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the original function text as it appeared in the source.
         *
         * @return the original text
         */
        public String getOriginalText() {
            return originalText;
        }

        /**
         * Sets the URI associated with this function's namespace.
         *
         * @param uri the namespace URI
         */
        public void setUri(String uri) {
            this.uri = uri;
        }

        /**
         * Returns the URI associated with this function's namespace.
         *
         * @return the URI
         */
        public String getUri() {
            return uri;
        }

        /**
         * Sets the FunctionInfo for this function.
         *
         * @param f the function info
         */
        public void setFunctionInfo(FunctionInfo f) {
            this.functionInfo = f;
        }

        /**
         * Returns the FunctionInfo for this function.
         *
         * @return the function info
         */
        public FunctionInfo getFunctionInfo() {
            return functionInfo;
        }

        /**
         * Sets the Java method name mapped to this EL function.
         *
         * @param methodName the method name
         */
        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        /**
         * Returns the Java method name mapped to this EL function.
         *
         * @return the method name
         */
        public String getMethodName() {
            return methodName;
        }

        /**
         * Sets the parameter type signatures for this function.
         *
         * @param parameters the parameter type signatures
         */
        public void setParameters(String[] parameters) {
            this.parameters = parameters;
        }

        /**
         * Returns the parameter type signatures for this function.
         *
         * @return the parameter type signatures
         */
        public String[] getParameters() {
            return parameters;
        }
    }

    /**
     * An ordered list of ELNode.
     */
    public static class Nodes {

        /**
         * Creates a new empty Nodes list.
         */
        public Nodes() {
            list = new ArrayList<>();
        }

        /*
         * Name used for creating a map for the functions in this EL expression, for communication to Generator.
         */
        private String mapName = null; // The function map associated this EL
        private final List<ELNode> list;

        /**
         * Adds an ELNode to this list.
         *
         * @param en the node to add
         */
        public void add(ELNode en) {
            list.add(en);
        }

        /**
         * Visit the nodes in the list with the supplied visitor.
         *
         * @param v The visitor used
         *
         * @throws JasperException if an error occurs while visiting a node
         */
        public void visit(Visitor v) throws JasperException {
            for (ELNode n : list) {
                n.accept(v);
            }
        }

        /**
         * Returns an iterator over the nodes in this list.
         *
         * @return an iterator over the nodes
         */
        public Iterator<ELNode> iterator() {
            return list.iterator();
        }

        /**
         * Returns whether this list contains no nodes.
         *
         * @return true if empty
         */
        public boolean isEmpty() {
            return list.isEmpty();
        }

        /**
         * Checks whether the expression contains an EL expression in the form ${...}.
         *
         * @return true if the expression contains a ${...}
         */
        public boolean containsEL() {
            for (ELNode n : list) {
                if (n instanceof Root) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Sets the function map name for this EL expression.
         *
         * @param name the function map name
         */
        public void setMapName(String name) {
            this.mapName = name;
        }

        /**
         * Returns the function map name for this EL expression.
         *
         * @return the function map name
         */
        public String getMapName() {
            return mapName;
        }

    }

    /**
     * A visitor class for traversing ELNodes.
     */
    public static class Visitor {

        /**
         * Creates a new Visitor instance.
         */
        public Visitor() {
        }

        /**
         * Visits a Root node by traversing its contained expression.
         *
         * @param n the root node
         *
         * @throws JasperException if an error occurs during visitation
         */
        public void visit(Root n) throws JasperException {
            n.getExpression().visit(this);
        }

        /**
         * Visits a Function node. No-op by default; override to handle functions.
         *
         * @param n the function node
         *
         * @throws JasperException if an error occurs during visitation
         */
        @SuppressWarnings("unused")
        public void visit(Function n) throws JasperException {
            // NOOP by default
        }

        /**
         * Visits a Text node. No-op by default; override to handle text.
         *
         * @param n the text node
         *
         * @throws JasperException if an error occurs during visitation
         */
        @SuppressWarnings("unused")
        public void visit(Text n) throws JasperException {
            // NOOP by default
        }

        /**
         * Visits an ELText node. No-op by default; override to handle EL text.
         *
         * @param n the EL text node
         *
         * @throws JasperException if an error occurs during visitation
         */
        @SuppressWarnings("unused")
        public void visit(ELText n) throws JasperException {
            // NOOP by default
        }
    }
}

