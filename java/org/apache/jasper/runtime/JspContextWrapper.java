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

package org.apache.jasper.runtime;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.el.ELContext;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.ExpressionEvaluator;
import javax.servlet.jsp.el.VariableResolver;
import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.VariableInfo;

import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.util.Enumerator;

/**
 * Implementation of a JSP Context Wrapper.
 * 
 * The JSP Context Wrapper is a JspContext created and maintained by a tag
 * handler implementation. It wraps the Invoking JSP Context, that is, the
 * JspContext instance passed to the tag handler by the invoking page via
 * setJspContext().
 * 
 * @author Kin-man Chung
 * @author Jan Luehe
 * @author Jacob Hookom
 */
public class JspContextWrapper extends PageContext implements VariableResolver {

	// Invoking JSP context
	private PageContext invokingJspCtxt;

	private transient HashMap<String, Object> pageAttributes;

	// ArrayList of NESTED scripting variables
	private ArrayList nestedVars;

	// ArrayList of AT_BEGIN scripting variables
	private ArrayList atBeginVars;

	// ArrayList of AT_END scripting variables
	private ArrayList atEndVars;

	private Map aliases;

	private HashMap<String, Object> originalNestedVars;

	public JspContextWrapper(JspContext jspContext, ArrayList nestedVars,
			ArrayList atBeginVars, ArrayList atEndVars, Map aliases) {
		this.invokingJspCtxt = (PageContext) jspContext;
		this.nestedVars = nestedVars;
		this.atBeginVars = atBeginVars;
		this.atEndVars = atEndVars;
		this.pageAttributes = new HashMap<String, Object>(16);
		this.aliases = aliases;

		if (nestedVars != null) {
			this.originalNestedVars = new HashMap<String, Object>(nestedVars.size());
		}
		syncBeginTagFile();
	}

	public void initialize(Servlet servlet, ServletRequest request,
			ServletResponse response, String errorPageURL,
			boolean needsSession, int bufferSize, boolean autoFlush)
			throws IOException, IllegalStateException, IllegalArgumentException {
	}

	public Object getAttribute(String name) {

		if (name == null) {
			throw new NullPointerException(Localizer
					.getMessage("jsp.error.attribute.null_name"));
		}

		return pageAttributes.get(name);
	}

	public Object getAttribute(String name, int scope) {

		if (name == null) {
			throw new NullPointerException(Localizer
					.getMessage("jsp.error.attribute.null_name"));
		}

		if (scope == PAGE_SCOPE) {
			return pageAttributes.get(name);
		}

		return invokingJspCtxt.getAttribute(name, scope);
	}

	public void setAttribute(String name, Object value) {

		if (name == null) {
			throw new NullPointerException(Localizer
					.getMessage("jsp.error.attribute.null_name"));
		}

		if (value != null) {
			pageAttributes.put(name, value);
		} else {
			removeAttribute(name, PAGE_SCOPE);
		}
	}

	public void setAttribute(String name, Object value, int scope) {

		if (name == null) {
			throw new NullPointerException(Localizer
					.getMessage("jsp.error.attribute.null_name"));
		}

		if (scope == PAGE_SCOPE) {
			if (value != null) {
				pageAttributes.put(name, value);
			} else {
				removeAttribute(name, PAGE_SCOPE);
			}
		} else {
			invokingJspCtxt.setAttribute(name, value, scope);
		}
	}

	public Object findAttribute(String name) {

		if (name == null) {
			throw new NullPointerException(Localizer
					.getMessage("jsp.error.attribute.null_name"));
		}

		Object o = pageAttributes.get(name);
		if (o == null) {
			o = invokingJspCtxt.getAttribute(name, REQUEST_SCOPE);
			if (o == null) {
				if (getSession() != null) {
					o = invokingJspCtxt.getAttribute(name, SESSION_SCOPE);
				}
				if (o == null) {
					o = invokingJspCtxt.getAttribute(name, APPLICATION_SCOPE);
				}
			}
		}

		return o;
	}

	public void removeAttribute(String name) {

		if (name == null) {
			throw new NullPointerException(Localizer
					.getMessage("jsp.error.attribute.null_name"));
		}

		pageAttributes.remove(name);
		invokingJspCtxt.removeAttribute(name, REQUEST_SCOPE);
		if (getSession() != null) {
			invokingJspCtxt.removeAttribute(name, SESSION_SCOPE);
		}
		invokingJspCtxt.removeAttribute(name, APPLICATION_SCOPE);
	}

	public void removeAttribute(String name, int scope) {

		if (name == null) {
			throw new NullPointerException(Localizer
					.getMessage("jsp.error.attribute.null_name"));
		}

		if (scope == PAGE_SCOPE) {
			pageAttributes.remove(name);
		} else {
			invokingJspCtxt.removeAttribute(name, scope);
		}
	}

	public int getAttributesScope(String name) {

		if (name == null) {
			throw new NullPointerException(Localizer
					.getMessage("jsp.error.attribute.null_name"));
		}

		if (pageAttributes.get(name) != null) {
			return PAGE_SCOPE;
		} else {
			return invokingJspCtxt.getAttributesScope(name);
		}
	}

	public Enumeration<String> getAttributeNamesInScope(int scope) {
		if (scope == PAGE_SCOPE) {
			return new Enumerator(pageAttributes.keySet().iterator());
		}

		return invokingJspCtxt.getAttributeNamesInScope(scope);
	}

	public void release() {
		invokingJspCtxt.release();
	}

	public JspWriter getOut() {
		return invokingJspCtxt.getOut();
	}

	public HttpSession getSession() {
		return invokingJspCtxt.getSession();
	}

	public Object getPage() {
		return invokingJspCtxt.getPage();
	}

	public ServletRequest getRequest() {
		return invokingJspCtxt.getRequest();
	}

	public ServletResponse getResponse() {
		return invokingJspCtxt.getResponse();
	}

	public Exception getException() {
		return invokingJspCtxt.getException();
	}

	public ServletConfig getServletConfig() {
		return invokingJspCtxt.getServletConfig();
	}

	public ServletContext getServletContext() {
		return invokingJspCtxt.getServletContext();
	}

	public void forward(String relativeUrlPath) throws ServletException,
			IOException {
		invokingJspCtxt.forward(relativeUrlPath);
	}

	public void include(String relativeUrlPath) throws ServletException,
			IOException {
		invokingJspCtxt.include(relativeUrlPath);
	}

	public void include(String relativeUrlPath, boolean flush)
			throws ServletException, IOException {
		invokingJspCtxt.include(relativeUrlPath, false);
	}

	public VariableResolver getVariableResolver() {
		return this;
	}

	public BodyContent pushBody() {
		return invokingJspCtxt.pushBody();
	}

	public JspWriter pushBody(Writer writer) {
		return invokingJspCtxt.pushBody(writer);
	}

	public JspWriter popBody() {
		return invokingJspCtxt.popBody();
	}

	public ExpressionEvaluator getExpressionEvaluator() {
		return invokingJspCtxt.getExpressionEvaluator();
	}

	public void handlePageException(Exception ex) throws IOException,
			ServletException {
		// Should never be called since handleException() called with a
		// Throwable in the generated servlet.
		handlePageException((Throwable) ex);
	}

	public void handlePageException(Throwable t) throws IOException,
			ServletException {
		invokingJspCtxt.handlePageException(t);
	}

	/**
	 * VariableResolver interface
	 */
	public Object resolveVariable(String pName) throws ELException {
		ELContext ctx = this.getELContext();
		return ctx.getELResolver().getValue(ctx, null, pName);
	}

	/**
	 * Synchronize variables at begin of tag file
	 */
	public void syncBeginTagFile() {
		saveNestedVariables();
	}

	/**
	 * Synchronize variables before fragment invokation
	 */
	public void syncBeforeInvoke() {
		copyTagToPageScope(VariableInfo.NESTED);
		copyTagToPageScope(VariableInfo.AT_BEGIN);
	}

	/**
	 * Synchronize variables at end of tag file
	 */
	public void syncEndTagFile() {
		copyTagToPageScope(VariableInfo.AT_BEGIN);
		copyTagToPageScope(VariableInfo.AT_END);
		restoreNestedVariables();
	}

	/**
	 * Copies the variables of the given scope from the virtual page scope of
	 * this JSP context wrapper to the page scope of the invoking JSP context.
	 * 
	 * @param scope
	 *            variable scope (one of NESTED, AT_BEGIN, or AT_END)
	 */
	private void copyTagToPageScope(int scope) {
		Iterator iter = null;

		switch (scope) {
		case VariableInfo.NESTED:
			if (nestedVars != null) {
				iter = nestedVars.iterator();
			}
			break;
		case VariableInfo.AT_BEGIN:
			if (atBeginVars != null) {
				iter = atBeginVars.iterator();
			}
			break;
		case VariableInfo.AT_END:
			if (atEndVars != null) {
				iter = atEndVars.iterator();
			}
			break;
		}

		while ((iter != null) && iter.hasNext()) {
			String varName = (String) iter.next();
			Object obj = getAttribute(varName);
			varName = findAlias(varName);
			if (obj != null) {
				invokingJspCtxt.setAttribute(varName, obj);
			} else {
				invokingJspCtxt.removeAttribute(varName, PAGE_SCOPE);
			}
		}
	}

	/**
	 * Saves the values of any NESTED variables that are present in the invoking
	 * JSP context, so they can later be restored.
	 */
	private void saveNestedVariables() {
		if (nestedVars != null) {
			Iterator iter = nestedVars.iterator();
			while (iter.hasNext()) {
				String varName = (String) iter.next();
				varName = findAlias(varName);
				Object obj = invokingJspCtxt.getAttribute(varName);
				if (obj != null) {
					originalNestedVars.put(varName, obj);
				}
			}
		}
	}

	/**
	 * Restores the values of any NESTED variables in the invoking JSP context.
	 */
	private void restoreNestedVariables() {
		if (nestedVars != null) {
			Iterator iter = nestedVars.iterator();
			while (iter.hasNext()) {
				String varName = (String) iter.next();
				varName = findAlias(varName);
				Object obj = originalNestedVars.get(varName);
				if (obj != null) {
					invokingJspCtxt.setAttribute(varName, obj);
				} else {
					invokingJspCtxt.removeAttribute(varName, PAGE_SCOPE);
				}
			}
		}
	}

	/**
	 * Checks to see if the given variable name is used as an alias, and if so,
	 * returns the variable name for which it is used as an alias.
	 * 
	 * @param varName
	 *            The variable name to check
	 * @return The variable name for which varName is used as an alias, or
	 *         varName if it is not being used as an alias
	 */
	private String findAlias(String varName) {

		if (aliases == null)
			return varName;

		String alias = (String) aliases.get(varName);
		if (alias == null) {
			return varName;
		}
		return alias;
	}

	//private ELContextImpl elContext;

	public ELContext getELContext() {
        // instead decorate!!!
        
        return this.invokingJspCtxt.getELContext();
        
        /*
		if (this.elContext != null) {
			JspFactory jspFact = JspFactory.getDefaultFactory();
			ServletContext servletContext = this.getServletContext();
			JspApplicationContextImpl jspCtx = (JspApplicationContextImpl) jspFact
					.getJspApplicationContext(servletContext);
			this.elContext = jspCtx.createELContext(this);
		}
		return this.elContext;
        */
	}
}
