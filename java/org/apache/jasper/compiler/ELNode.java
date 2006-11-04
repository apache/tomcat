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

import java.util.*;
import javax.servlet.jsp.tagext.FunctionInfo;
import org.apache.jasper.JasperException;

/**
 * This class defines internal representation for an EL Expression
 *
 * It currently only defines functions.  It can be expanded to define
 * all the components of an EL expression, if need to.
 *
 * @author Kin-man Chung
 */

abstract class ELNode {

    abstract public void accept(Visitor v) throws JasperException;

    /**
     * Child classes
     */


    /**
     * Represents an EL expression: anything in ${ and }.
     */
    public static class Root extends ELNode {

	private ELNode.Nodes expr;
    private char type;

	Root(ELNode.Nodes expr, char type) {
	    this.expr = expr;
        this.type = type;
	}

	public void accept(Visitor v) throws JasperException {
	    v.visit(this);
	}

	public ELNode.Nodes getExpression() {
	    return expr;
	}

    public char getType() {
        return type;
    }
    }

    /**
     * Represents text outside of EL expression.
     */
    public static class Text extends ELNode {

	private String text;

	Text(String text) {
	    this.text = text;
	}

	public void accept(Visitor v) throws JasperException {
	    v.visit(this);
	}

	public String getText() {
	    return text;
	}
    }

    /**
     * Represents anything in EL expression, other than functions, including
     * function arguments etc
     */
    public static class ELText extends ELNode {

	private String text;

	ELText(String text) {
	    this.text = text;
	}

	public void accept(Visitor v) throws JasperException {
	    v.visit(this);
	}

	public String getText() {
	    return text;
	}
    }

    /**
     * Represents a function
     * Currently only include the prefix and function name, but not its
     * arguments.
     */
    public static class Function extends ELNode {

	private String prefix;
	private String name;
	private String uri;
	private FunctionInfo functionInfo;
	private String methodName;
	private String[] parameters;

	Function(String prefix, String name) {
	    this.prefix = prefix;
	    this.name = name;
	}

	public void accept(Visitor v) throws JasperException {
	    v.visit(this);
	}

	public String getPrefix() {
	    return prefix;
	}

	public String getName() {
	    return name;
	}

	public void setUri(String uri) {
	    this.uri = uri;
	}

	public String getUri() {
	    return uri;
	}

	public void setFunctionInfo(FunctionInfo f) {
	    this.functionInfo = f;
	}

	public FunctionInfo getFunctionInfo() {
	    return functionInfo;
	}

	public void setMethodName(String methodName) {
	    this.methodName = methodName;
	}

	public String getMethodName() {
	    return methodName;
	}

	public void setParameters(String[] parameters) {
	    this.parameters = parameters;
	}

	public String[] getParameters() {
	    return parameters;
	}
    }

    /**
     * An ordered list of ELNode.
     */
    public static class Nodes {

	/* Name used for creating a map for the functions in this
	   EL expression, for communication to Generator.
	 */
	String mapName = null;	// The function map associated this EL
	private List<ELNode> list;

	public Nodes() {
	    list = new ArrayList<ELNode>();
	}

	public void add(ELNode en) {
	    list.add(en);
	}

	/**
	 * Visit the nodes in the list with the supplied visitor
	 * @param v The visitor used
	 */
	public void visit(Visitor v) throws JasperException {
	    Iterator<ELNode> iter = list.iterator();
	    while (iter.hasNext()) {
	        ELNode n = iter.next();
	        n.accept(v);
	    }
	}

	public Iterator<ELNode> iterator() {
	    return list.iterator();
	}

	public boolean isEmpty() {
	    return list.size() == 0;
	}

	/**
	 * @return true if the expression contains a ${...}
	 */
	public boolean containsEL() {
	    Iterator<ELNode> iter = list.iterator();
	    while (iter.hasNext()) {
	        ELNode n = iter.next();
	        if (n instanceof Root) {
	            return true;
	        }
	    }
	    return false;
	}

	public void setMapName(String name) {
	    this.mapName = name;
	}

	public String getMapName() {
	    return mapName;
	}
    
    }

    /*
     * A visitor class for traversing ELNodes
     */
    public static class Visitor {

	public void visit(Root n) throws JasperException {
	    n.getExpression().visit(this);
	}

	public void visit(Function n) throws JasperException {
	}

	public void visit(Text n) throws JasperException {
	}

	public void visit(ELText n) throws JasperException {
	}
    }
}

