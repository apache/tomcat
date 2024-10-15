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
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Set;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.ExpressionFactory;
import jakarta.el.ImportHandler;
import jakarta.el.ValueExpression;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspFactory;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.el.NotFoundELResolver;
import jakarta.servlet.jsp.tagext.BodyContent;

import org.apache.jasper.Constants;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.el.ELContextImpl;
import org.apache.jasper.runtime.JspContextWrapper.ELContextWrapper;

/**
 * Implementation of the PageContext class from the JSP spec. Also doubles as a
 * VariableResolver for the EL.
 *
 * @author Anil K. Vijendran
 * @author Larry Cable
 * @author Hans Bergsten
 * @author Pierre Delisle
 * @author Mark Roth
 * @author Jan Luehe
 * @author Jacob Hookom
 */
public class PageContextImpl extends PageContext {

    private static final JspFactory jspf = JspFactory.getDefaultFactory();

    private BodyContentImpl[] outs;

    private int depth;

    // per-servlet state
    private Servlet servlet;

    private ServletConfig config;

    private ServletContext context;

    private JspApplicationContextImpl applicationContext;

    private String errorPageURL;

    private boolean limitBodyContentBuffer;

    private int bodyContentTagBufferSize = Constants.DEFAULT_TAG_BUFFER_SIZE;

    // page-scope attributes
    private final transient HashMap<String, Object> attributes;

    // per-request state
    private transient ServletRequest request;

    private transient ServletResponse response;

    private transient HttpSession session;

    private transient ELContextImpl elContext;


    // initial output stream
    private transient JspWriter out;

    private transient JspWriterImpl baseOut;

    /*
     * Constructor.
     */
    PageContextImpl() {
        this.outs = new BodyContentImpl[0];
        this.attributes = new HashMap<>(16);
        this.depth = -1;
    }

    @Override
    public void initialize(Servlet servlet, ServletRequest request,
            ServletResponse response, String errorPageURL,
            boolean needsSession, int bufferSize, boolean autoFlush)
            throws IOException {

        // initialize state
        this.servlet = servlet;
        this.config = servlet.getServletConfig();
        this.context = config.getServletContext();
        this.errorPageURL = errorPageURL;
        this.request = request;
        this.response = response;

        limitBodyContentBuffer = Boolean.parseBoolean(config.getInitParameter("limitBodyContentBuffer"));
        String bodyContentTagBufferSize = config.getInitParameter("bodyContentTagBufferSize");
        if (bodyContentTagBufferSize != null) {
            this.bodyContentTagBufferSize = Integer.parseInt(bodyContentTagBufferSize);
        }

        // initialize application context
        this.applicationContext = JspApplicationContextImpl.getInstance(context);

        // Setup session (if required)
        if (request instanceof HttpServletRequest && needsSession) {
            this.session = ((HttpServletRequest) request).getSession();
        }
        if (needsSession && session == null) {
            throw new IllegalStateException(Localizer.getMessage("jsp.error.page.sessionRequired"));
        }

        // initialize the initial out ...
        depth = -1;
        if (bufferSize == JspWriter.DEFAULT_BUFFER) {
            bufferSize = Constants.DEFAULT_BUFFER_SIZE;
        }
        if (this.baseOut == null) {
            this.baseOut = new JspWriterImpl(response, bufferSize, autoFlush);
        } else {
            this.baseOut.init(response, bufferSize, autoFlush);
        }
        this.out = baseOut;

        // register names/values as per spec
        setAttribute(OUT, this.out);
        setAttribute(REQUEST, request);
        setAttribute(RESPONSE, response);

        if (session != null) {
            setAttribute(SESSION, session);
        }

        setAttribute(PAGE, servlet);
        setAttribute(CONFIG, config);
        setAttribute(PAGECONTEXT, this);
        setAttribute(APPLICATION, context);
    }

    @Override
    public void release() {
        out = baseOut;
        try {
            ((JspWriterImpl) out).flushBuffer();
        } catch (IOException ex) {
            IllegalStateException ise = new IllegalStateException(Localizer.getMessage("jsp.error.flush"), ex);
            throw ise;
        } finally {
            servlet = null;
            config = null;
            context = null;
            applicationContext = null;
            elContext = null;
            errorPageURL = null;
            request = null;
            response = null;
            depth = -1;
            baseOut.recycle();
            session = null;
            attributes.clear();
            for (BodyContentImpl body: outs) {
                body.recycle();
            }
        }
    }

    @Override
    public Object getAttribute(final String name) {
        return getAttribute(name, PAGE_SCOPE);
    }

    @Override
    public Object getAttribute(final String name, final int scope) {

        if (name == null) {
            throw new NullPointerException(Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        switch (scope) {
        case PAGE_SCOPE:
            return attributes.get(name);

        case REQUEST_SCOPE:
            return request.getAttribute(name);

        case SESSION_SCOPE:
            if (session == null) {
                throw new IllegalStateException(Localizer.getMessage("jsp.error.page.noSession"));
            }
            return session.getAttribute(name);

        case APPLICATION_SCOPE:
            return context.getAttribute(name);

        default:
            throw new IllegalArgumentException(Localizer.getMessage("jsp.error.page.invalid.scope"));
        }
    }

    @Override
    public void setAttribute(final String name, final Object attribute) {
        setAttribute(name, attribute, PAGE_SCOPE);
    }

    @Override
    public void setAttribute(final String name, final Object o, final int scope) {

        if (name == null) {
            throw new NullPointerException(Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        if (o == null) {
            removeAttribute(name, scope);
        } else {
            switch (scope) {
            case PAGE_SCOPE:
                attributes.put(name, o);
                break;

            case REQUEST_SCOPE:
                request.setAttribute(name, o);
                break;

            case SESSION_SCOPE:
                if (session == null) {
                    throw new IllegalStateException(Localizer
                            .getMessage("jsp.error.page.noSession"));
                }
                session.setAttribute(name, o);
                break;

            case APPLICATION_SCOPE:
                context.setAttribute(name, o);
                break;

            default:
                throw new IllegalArgumentException(Localizer.getMessage("jsp.error.page.invalid.scope"));
            }
        }
    }

    @Override
    public void removeAttribute(final String name, final int scope) {

        if (name == null) {
            throw new NullPointerException(Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        switch (scope) {
        case PAGE_SCOPE:
            attributes.remove(name);
            break;

        case REQUEST_SCOPE:
            request.removeAttribute(name);
            break;

        case SESSION_SCOPE:
            if (session == null) {
                throw new IllegalStateException(Localizer.getMessage("jsp.error.page.noSession"));
            }
            session.removeAttribute(name);
            break;

        case APPLICATION_SCOPE:
            context.removeAttribute(name);
            break;

        default:
            throw new IllegalArgumentException(Localizer.getMessage("jsp.error.page.invalid.scope"));
        }
    }

    @Override
    public int getAttributesScope(final String name) {

        if (name == null) {
            throw new NullPointerException(Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        if (attributes.get(name) != null) {
            return PAGE_SCOPE;
        }

        if (request.getAttribute(name) != null) {
            return REQUEST_SCOPE;
        }

        if (session != null) {
            try {
                if (session.getAttribute(name) != null) {
                    return SESSION_SCOPE;
                }
            } catch(IllegalStateException ise) {
                // Session has been invalidated.
                // Ignore and fall through to application scope.
            }
        }

        if (context.getAttribute(name) != null) {
            return APPLICATION_SCOPE;
        }

        return 0;
    }

    @Override
    public Object findAttribute(final String name) {
        if (name == null) {
            throw new NullPointerException(Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        Object o = attributes.get(name);
        if (o != null) {
            return o;
        }

        o = request.getAttribute(name);
        if (o != null) {
            return o;
        }

        if (session != null) {
            try {
                o = session.getAttribute(name);
            } catch(IllegalStateException ise) {
                // Session has been invalidated.
                // Ignore and fall through to application scope.
            }
            if (o != null) {
                return o;
            }
        }

        return context.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNamesInScope(final int scope) {
        switch (scope) {
        case PAGE_SCOPE:
            return Collections.enumeration(attributes.keySet());

        case REQUEST_SCOPE:
            return request.getAttributeNames();

        case SESSION_SCOPE:
            if (session == null) {
                throw new IllegalStateException(Localizer.getMessage("jsp.error.page.noSession"));
            }
            return session.getAttributeNames();

        case APPLICATION_SCOPE:
            return context.getAttributeNames();

        default:
            throw new IllegalArgumentException(Localizer.getMessage("jsp.error.page.invalid.scope"));
        }
    }

    @Override
    public void removeAttribute(final String name) {

        if (name == null) {
            throw new NullPointerException(Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        removeAttribute(name, PAGE_SCOPE);
        removeAttribute(name, REQUEST_SCOPE);
        if( session != null ) {
            try {
                removeAttribute(name, SESSION_SCOPE);
            } catch(IllegalStateException ise) {
                // Session has been invalidated.
                // Ignore and fall throw to application scope.
            }
        }
        removeAttribute(name, APPLICATION_SCOPE);
    }

    @Override
    public JspWriter getOut() {
        return out;
    }

    @Override
    public HttpSession getSession() {
        return session;
    }

    @Override
    public ServletConfig getServletConfig() {
        return config;
    }

    @Override
    public ServletContext getServletContext() {
        return config.getServletContext();
    }

    @Override
    public ServletRequest getRequest() {
        return request;
    }

    @Override
    public ServletResponse getResponse() {
        return response;
    }

    /**
     * Returns the exception associated with this page context, if any.
     * <p>
     * Added wrapping for Throwables to avoid ClassCastException: see Bugzilla
     * 31171 for details.
     *
     * @return The Exception associated with this page context, if any.
     */
    @Override
    public Exception getException() {
        Throwable t = JspRuntimeLibrary.getThrowable(request);

        // Only wrap if needed
        if ((t != null) && (!(t instanceof Exception))) {
            t = new JspException(t);
        }

        return (Exception) t;
    }

    @Override
    public Object getPage() {
        return servlet;
    }

    private String getAbsolutePathRelativeToContext(String relativeUrlPath) {
        String path = relativeUrlPath;

        if (!path.startsWith("/")) {
            String uri = (String) request.getAttribute(
                    RequestDispatcher.INCLUDE_SERVLET_PATH);
            if (uri == null) {
                uri = ((HttpServletRequest) request).getServletPath();
            }
            String baseURI = uri.substring(0, uri.lastIndexOf('/'));
            path = baseURI + '/' + path;
        }

        return path;
    }

    @Override
    public void include(String relativeUrlPath) throws ServletException,
            IOException {
        JspRuntimeLibrary
                .include(request, response, relativeUrlPath, out, true);
    }

    @Override
    public void include(final String relativeUrlPath, final boolean flush)
            throws ServletException, IOException {
        JspRuntimeLibrary.include(request, response, relativeUrlPath, out, flush);
    }

    @Override
    public void forward(final String relativeUrlPath) throws ServletException, IOException {
        // JSP.4.5 If the buffer was flushed, throw IllegalStateException
        try {
            out.clear();
            baseOut.clear();
        } catch (IOException ex) {
            throw new IllegalStateException(Localizer.getMessage(
                    "jsp.error.attempt_to_clear_flushed_buffer"), ex);
        }

        // Make sure that the response object is not the wrapper for include
        while (response instanceof ServletResponseWrapperInclude) {
            response = ((ServletResponseWrapperInclude) response).getResponse();
        }

        final String path = getAbsolutePathRelativeToContext(relativeUrlPath);
        String includeUri = (String) request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);

        if (includeUri != null) {
            request.removeAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
        }
        try {
            context.getRequestDispatcher(path).forward(request, response);
        } finally {
            if (includeUri != null) {
                request.setAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH, includeUri);
            }
        }
    }

    @Override
    public BodyContent pushBody() {
        return (BodyContent) pushBody(null);
    }

    @Override
    public JspWriter pushBody(Writer writer) {
        depth++;
        if (depth >= outs.length) {
            BodyContentImpl[] newOuts = Arrays.copyOf(outs, depth + 1);
            newOuts[depth] = new BodyContentImpl(out, limitBodyContentBuffer, bodyContentTagBufferSize);
            outs = newOuts;
        }

        outs[depth].setWriter(writer);
        out = outs[depth];

        // Update the value of the "out" attribute in the page scope
        // attribute namespace of this PageContext
        setAttribute(OUT, out);

        return outs[depth];
    }

    @Override
    public JspWriter popBody() {
        depth--;
        if (depth >= 0) {
            out = outs[depth];
        } else {
            out = baseOut;
        }

        // Update the value of the "out" attribute in the page scope
        // attribute namespace of this PageContext
        setAttribute(OUT, out);

        return out;
    }

    @Override
    public void handlePageException(Exception ex) throws IOException,
            ServletException {
        // Should never be called since handleException() called with a
        // Throwable in the generated servlet.
        handlePageException((Throwable) ex);
    }

    @Override
    public void handlePageException(final Throwable t) throws IOException, ServletException {
        if (t == null) {
            throw new NullPointerException(Localizer.getMessage("jsp.error.page.nullThrowable"));
        }

        if (errorPageURL != null && !errorPageURL.equals("")) {

            /*
             * Set request attributes. Do not set the
             * jakarta.servlet.error.exception attribute here (instead, set in the
             * generated servlet code for the error page) in order to prevent
             * the ErrorReportValve, which is invoked as part of forwarding the
             * request to the error page, from throwing it if the response has
             * not been committed (the response will have been committed if the
             * error page is a JSP page).
             */
            request.setAttribute(EXCEPTION, t);
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE,
                    Integer.valueOf(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
            request.setAttribute(RequestDispatcher.ERROR_METHOD, ((HttpServletRequest) request).getMethod());
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, ((HttpServletRequest) request).getRequestURI());
            request.setAttribute(RequestDispatcher.ERROR_QUERY_STRING, ((HttpServletRequest) request).getQueryString());
            request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME, config.getServletName());
            try {
                forward(errorPageURL);
            } catch (IllegalStateException ise) {
                include(errorPageURL);
            }

            // The error page could be inside an include.

            Object newException = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

            // t==null means the attribute was not set.
            if ((newException != null) && (newException == t)) {
                request.removeAttribute(RequestDispatcher.ERROR_EXCEPTION);
            }

            // now clear the error code - to prevent double handling.
            request.removeAttribute(RequestDispatcher.ERROR_STATUS_CODE);
            request.removeAttribute(RequestDispatcher.ERROR_REQUEST_URI);
            request.removeAttribute(RequestDispatcher.ERROR_SERVLET_NAME);
            request.removeAttribute(EXCEPTION);

        } else {
            // Otherwise throw the exception wrapped inside a ServletException.
            // Set the exception as the root cause in the ServletException
            // to get a stack trace for the real problem
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            if (t instanceof ServletException) {
                throw (ServletException) t;
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }

            Throwable rootCause = null;
            if (t instanceof JspException || t instanceof ELException) {
                rootCause = t.getCause();
            }

            if (rootCause != null) {
                throw new ServletException(
                        t.getClass().getName() + ": " + t.getMessage(), rootCause);
            }

            throw new ServletException(t);
        }
    }

    /**
     * Proprietary method to evaluate EL expressions. XXX - This method should
     * go away once the EL interpreter moves out of JSTL and into its own
     * project. For now, this is necessary because the standard machinery is too
     * slow.
     *
     * @param expression
     *            The expression to be evaluated
     * @param expectedType
     *            The expected resulting type
     * @param pageContext
     *            The page context
     * @param functionMap
     *            Maps prefix and name to Method
     * @return The result of the evaluation
     * @throws ELException If an error occurs during the evaluation
     */
    public static Object proprietaryEvaluate(final String expression,
            final Class<?> expectedType, final PageContext pageContext,
            final ProtectedFunctionMapper functionMap)
            throws ELException {
        final ExpressionFactory exprFactory = jspf.getJspApplicationContext(pageContext.getServletContext()).getExpressionFactory();
        ELContext ctx = pageContext.getELContext();
        ELContextImpl ctxImpl;
        if (ctx instanceof ELContextWrapper) {
            ctxImpl = (ELContextImpl) ((ELContextWrapper) ctx).getWrappedELContext();
        } else {
            ctxImpl = (ELContextImpl) ctx;
        }
        ctxImpl.setFunctionMapper(functionMap);
        ValueExpression ve = exprFactory.createValueExpression(ctx, expression, expectedType);
        return ve.getValue(ctx);
    }

    @Override
    public ELContext getELContext() {
        if (elContext == null) {
            elContext = applicationContext.createELContext(this);
            if (servlet instanceof JspSourceImports) {
                ImportHandler ih = elContext.getImportHandler();
                Set<String> packageImports = ((JspSourceImports) servlet).getPackageImports();
                if (packageImports != null) {
                    for (String packageImport : packageImports) {
                        ih.importPackage(packageImport);
                    }
                }
                Set<String> classImports = ((JspSourceImports) servlet).getClassImports();
                if (classImports != null) {
                    for (String classImport : classImports) {
                        if (classImport.startsWith("static ")) {
                            classImport = classImport.substring(7);
                            try {
                                ih.importStatic(classImport);
                            } catch (ELException e) {
                                // Ignore - not all static imports are valid for EL
                            }
                        } else {
                            ih.importClass(classImport);
                        }
                    }
                }
            }
            if (servlet instanceof JspSourceDirectives) {
                if (((JspSourceDirectives) servlet).getErrorOnELNotFound()) {
                    elContext.putContext(NotFoundELResolver.class, Boolean.TRUE);
                }
            }
        }
        return this.elContext;
    }
}
