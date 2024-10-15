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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.EvaluationListener;
import jakarta.el.FunctionMapper;
import jakarta.el.ImportHandler;
import jakarta.el.VariableMapper;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.JspApplicationContext;
import jakarta.servlet.jsp.JspContext;
import jakarta.servlet.jsp.JspFactory;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.el.NotFoundELResolver;
import jakarta.servlet.jsp.tagext.BodyContent;
import jakarta.servlet.jsp.tagext.JspTag;
import jakarta.servlet.jsp.tagext.VariableInfo;

import org.apache.jasper.compiler.Localizer;

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
public class JspContextWrapper extends PageContext{

    private final JspTag jspTag;

    // Invoking JSP context
    private final PageContext invokingJspCtxt;

    private final transient HashMap<String, Object> pageAttributes;

    // ArrayList of NESTED scripting variables
    private final ArrayList<String> nestedVars;

    // ArrayList of AT_BEGIN scripting variables
    private final ArrayList<String> atBeginVars;

    // ArrayList of AT_END scripting variables
    private final ArrayList<String> atEndVars;

    private final Map<String,String> aliases;

    private final HashMap<String, Object> originalNestedVars;

    private ServletContext servletContext = null;

    private ELContext elContext = null;

    private final PageContext rootJspCtxt;

    public JspContextWrapper(JspTag jspTag, JspContext jspContext,
            ArrayList<String> nestedVars, ArrayList<String> atBeginVars,
            ArrayList<String> atEndVars, Map<String,String> aliases) {
        this.jspTag = jspTag;
        this.invokingJspCtxt = (PageContext) jspContext;
        if (jspContext instanceof JspContextWrapper) {
            rootJspCtxt = ((JspContextWrapper)jspContext).rootJspCtxt;
        } else {
            rootJspCtxt = invokingJspCtxt;
        }
        this.nestedVars = nestedVars;
        this.atBeginVars = atBeginVars;
        this.atEndVars = atEndVars;
        this.pageAttributes = new HashMap<>(16);
        this.aliases = aliases;

        if (nestedVars != null) {
            this.originalNestedVars = new HashMap<>(nestedVars.size());
        } else {
            this.originalNestedVars = null;
        }
        syncBeginTagFile();
    }

    @Override
    public void initialize(Servlet servlet, ServletRequest request,
            ServletResponse response, String errorPageURL,
            boolean needsSession, int bufferSize, boolean autoFlush)
            throws IOException, IllegalStateException, IllegalArgumentException {
    }

    @Override
    public Object getAttribute(String name) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        return pageAttributes.get(name);
    }

    @Override
    public Object getAttribute(String name, int scope) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        if (scope == PAGE_SCOPE) {
            return pageAttributes.get(name);
        }

        return rootJspCtxt.getAttribute(name, scope);
    }

    @Override
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

    @Override
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
            rootJspCtxt.setAttribute(name, value, scope);
        }
    }

    @Override
    public Object findAttribute(String name) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        Object o = pageAttributes.get(name);
        if (o == null) {
            o = rootJspCtxt.getAttribute(name, REQUEST_SCOPE);
            if (o == null) {
                if (getSession() != null) {
                    try {
                        o = rootJspCtxt.getAttribute(name, SESSION_SCOPE);
                    } catch (IllegalStateException ise) {
                        // Session has been invalidated.
                        // Ignore and fall through to application scope.
                    }
                }
                if (o == null) {
                    o = rootJspCtxt.getAttribute(name, APPLICATION_SCOPE);
                }
            }
        }

        return o;
    }

    @Override
    public void removeAttribute(String name) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        pageAttributes.remove(name);
        rootJspCtxt.removeAttribute(name, REQUEST_SCOPE);
        if (getSession() != null) {
            rootJspCtxt.removeAttribute(name, SESSION_SCOPE);
        }
        rootJspCtxt.removeAttribute(name, APPLICATION_SCOPE);
    }

    @Override
    public void removeAttribute(String name, int scope) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        if (scope == PAGE_SCOPE) {
            pageAttributes.remove(name);
        } else {
            rootJspCtxt.removeAttribute(name, scope);
        }
    }

    @Override
    public int getAttributesScope(String name) {

        if (name == null) {
            throw new NullPointerException(Localizer
                    .getMessage("jsp.error.attribute.null_name"));
        }

        if (pageAttributes.get(name) != null) {
            return PAGE_SCOPE;
        } else {
            return rootJspCtxt.getAttributesScope(name);
        }
    }

    @Override
    public Enumeration<String> getAttributeNamesInScope(int scope) {
        if (scope == PAGE_SCOPE) {
            return Collections.enumeration(pageAttributes.keySet());
        }

        return rootJspCtxt.getAttributeNamesInScope(scope);
    }

    @Override
    public void release() {
        invokingJspCtxt.release();
    }

    @Override
    public JspWriter getOut() {
        return rootJspCtxt.getOut();
    }

    @Override
    public HttpSession getSession() {
        return rootJspCtxt.getSession();
    }

    @Override
    public Object getPage() {
        return invokingJspCtxt.getPage();
    }

    @Override
    public ServletRequest getRequest() {
        return invokingJspCtxt.getRequest();
    }

    @Override
    public ServletResponse getResponse() {
        return rootJspCtxt.getResponse();
    }

    @Override
    public Exception getException() {
        return invokingJspCtxt.getException();
    }

    @Override
    public ServletConfig getServletConfig() {
        return invokingJspCtxt.getServletConfig();
    }

    @Override
    public ServletContext getServletContext() {
        if (servletContext == null) {
            servletContext = rootJspCtxt.getServletContext();
        }
        return servletContext;
    }

    @Override
    public void forward(String relativeUrlPath) throws ServletException,
            IOException {
        invokingJspCtxt.forward(relativeUrlPath);
    }

    @Override
    public void include(String relativeUrlPath) throws ServletException,
            IOException {
        invokingJspCtxt.include(relativeUrlPath);
    }

    @Override
    public void include(String relativeUrlPath, boolean flush)
            throws ServletException, IOException {
        invokingJspCtxt.include(relativeUrlPath, false);
    }

    @Override
    public BodyContent pushBody() {
        return invokingJspCtxt.pushBody();
    }

    @Override
    public JspWriter pushBody(Writer writer) {
        return invokingJspCtxt.pushBody(writer);
    }

    @Override
    public JspWriter popBody() {
        return invokingJspCtxt.popBody();
    }

    @Override
    public void handlePageException(Exception ex) throws IOException,
            ServletException {
        // Should never be called since handleException() called with a
        // Throwable in the generated servlet.
        handlePageException((Throwable) ex);
    }

    @Override
    public void handlePageException(Throwable t) throws IOException,
            ServletException {
        invokingJspCtxt.handlePageException(t);
    }

    /**
     * Synchronize variables at begin of tag file
     */
    public void syncBeginTagFile() {
        saveNestedVariables();
    }

    /**
     * Synchronize variables before fragment invocation
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
        Iterator<String> iter = null;

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
            String varName = iter.next();
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
            for (String varName : nestedVars) {
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
            for (String varName : nestedVars) {
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

        if (aliases == null) {
            return varName;
        }

        String alias = aliases.get(varName);
        if (alias == null) {
            return varName;
        }
        return alias;
    }

    @Override
    public ELContext getELContext() {
        if (elContext == null) {
            elContext = new ELContextWrapper(rootJspCtxt.getELContext(), jspTag, this);
            JspFactory factory = JspFactory.getDefaultFactory();
            JspApplicationContext jspAppCtxt = factory.getJspApplicationContext(servletContext);
            if (jspAppCtxt instanceof JspApplicationContextImpl) {
                ((JspApplicationContextImpl) jspAppCtxt).fireListeners(elContext);
            }
        }
        return elContext;
    }


    static class ELContextWrapper extends ELContext {

        private final ELContext wrapped;
        private final JspTag jspTag;
        private final PageContext pageContext;
        private ImportHandler importHandler;

        private ELContextWrapper(ELContext wrapped, JspTag jspTag, PageContext pageContext) {
            this.wrapped = wrapped;
            this.jspTag = jspTag;
            this.pageContext = pageContext;
        }

        ELContext getWrappedELContext() {
            return wrapped;
        }

        @Override
        public void setPropertyResolved(boolean resolved) {
            wrapped.setPropertyResolved(resolved);
        }

        @Override
        public void setPropertyResolved(Object base, Object property) {
            wrapped.setPropertyResolved(base, property);
        }

        @Override
        public boolean isPropertyResolved() {
            return wrapped.isPropertyResolved();
        }

        @Override
        public void putContext(Class<?> key, Object contextObject) {
            if (key != JspContext.class) {
                wrapped.putContext(key, contextObject);
            }
        }

        @Override
        public Object getContext(Class<?> key) {
            if (key == JspContext.class) {
                return pageContext;
            }
            if (key == NotFoundELResolver.class) {
                if (jspTag instanceof JspSourceDirectives) {
                    return Boolean.valueOf(((JspSourceDirectives) jspTag).getErrorOnELNotFound());
                } else {
                    // returning Boolean.FALSE would have the same effect
                    return null;
                }
            }
            return wrapped.getContext(key);
        }

        @Override
        public ImportHandler getImportHandler() {
            if (importHandler == null) {
                importHandler = new ImportHandler();
                if (jspTag instanceof JspSourceImports) {
                    Set<String> packageImports = ((JspSourceImports) jspTag).getPackageImports();
                    if (packageImports != null) {
                        for (String packageImport : packageImports) {
                            importHandler.importPackage(packageImport);
                        }
                    }
                    Set<String> classImports = ((JspSourceImports) jspTag).getClassImports();
                    if (classImports != null) {
                        for (String classImport : classImports) {
                            importHandler.importClass(classImport);
                        }
                    }
                }

            }
            return importHandler;
        }

        @Override
        public Locale getLocale() {
            return wrapped.getLocale();
        }

        @Override
        public void setLocale(Locale locale) {
            wrapped.setLocale(locale);
        }

        @Override
        public void addEvaluationListener(EvaluationListener listener) {
            wrapped.addEvaluationListener(listener);
        }

        @Override
        public List<EvaluationListener> getEvaluationListeners() {
            return wrapped.getEvaluationListeners();
        }

        @Override
        public void notifyBeforeEvaluation(String expression) {
            wrapped.notifyBeforeEvaluation(expression);
        }

        @Override
        public void notifyAfterEvaluation(String expression) {
            wrapped.notifyAfterEvaluation(expression);
        }

        @Override
        public void notifyPropertyResolved(Object base, Object property) {
            wrapped.notifyPropertyResolved(base, property);
        }

        @Override
        public boolean isLambdaArgument(String name) {
            return wrapped.isLambdaArgument(name);
        }

        @Override
        public Object getLambdaArgument(String name) {
            return wrapped.getLambdaArgument(name);
        }

        @Override
        public void enterLambdaScope(Map<String, Object> arguments) {
            wrapped.enterLambdaScope(arguments);
        }

        @Override
        public void exitLambdaScope() {
            wrapped.exitLambdaScope();
        }

        @Override
        public <T> T convertToType(Object obj, Class<T> type) {
            return wrapped.convertToType(obj, type);
        }

        @Override
        public ELResolver getELResolver() {
            return wrapped.getELResolver();
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return wrapped.getFunctionMapper();
        }

        @Override
        public VariableMapper getVariableMapper() {
            return wrapped.getVariableMapper();
        }
    }
}
