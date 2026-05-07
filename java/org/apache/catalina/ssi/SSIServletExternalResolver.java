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
package org.apache.catalina.ssi;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.http.Method;
import org.apache.tomcat.util.http.RequestUtil;
import org.apache.tomcat.util.res.StringManager;

/**
 * An implementation of SSIExternalResolver that is used with servlets.
 */
public class SSIServletExternalResolver implements SSIExternalResolver {
    private static final StringManager sm = StringManager.getManager(SSIServletExternalResolver.class);
    /**
     * Standard CGI variable names exposed to SSI.
     */
    protected final String[] VARIABLE_NAMES = { "AUTH_TYPE", "CONTENT_LENGTH", "CONTENT_TYPE", "DOCUMENT_NAME",
            "DOCUMENT_URI", "GATEWAY_INTERFACE", "HTTP_ACCEPT", "HTTP_ACCEPT_ENCODING", "HTTP_ACCEPT_LANGUAGE",
            "HTTP_CONNECTION", "HTTP_HOST", "HTTP_REFERER", "HTTP_USER_AGENT", "PATH_INFO", "PATH_TRANSLATED",
            "QUERY_STRING", "QUERY_STRING_UNESCAPED", "REMOTE_ADDR", "REMOTE_HOST", "REMOTE_PORT", "REMOTE_USER",
            "REQUEST_METHOD", "REQUEST_URI", "SCRIPT_FILENAME", "SCRIPT_NAME", "SERVER_ADDR", "SERVER_NAME",
            "SERVER_PORT", "SERVER_PROTOCOL", "SERVER_SOFTWARE", "UNIQUE_ID" };
    /**
     * The servlet context for resource access.
     */
    protected final ServletContext context;
    /**
     * The current HTTP servlet request.
     */
    protected final HttpServletRequest req;
    /**
     * The current HTTP servlet response.
     */
    protected final HttpServletResponse res;
    /**
     * Whether virtual paths are relative to the webapp.
     */
    protected final boolean isVirtualWebappRelative;
    /**
     * Debug level for SSI processing.
     */
    protected final int debug;
    /**
     * Character encoding for included file content.
     */
    protected final String inputEncoding;

    /**
     * Constructor.
     *
     * @param context the servlet context
     * @param req the HTTP servlet request
     * @param res the HTTP servlet response
     * @param isVirtualWebappRelative whether virtual paths are relative to the webapp
     * @param debug debug level
     * @param inputEncoding character encoding for included file content
     */
    public SSIServletExternalResolver(ServletContext context, HttpServletRequest req, HttpServletResponse res,
            boolean isVirtualWebappRelative, int debug, String inputEncoding) {
        this.context = context;
        this.req = req;
        this.res = res;
        this.isVirtualWebappRelative = isVirtualWebappRelative;
        this.debug = debug;
        this.inputEncoding = inputEncoding;
    }


    /**
     * Log a message with an optional throwable.
     *
     * @param message the message to log
     * @param throwable the optional throwable
     */
    @Override
    public void log(String message, Throwable throwable) {
        /*
         * We can't assume that Servlet.log(message, null) is the same as Servlet.log( message ), since API doesn't seem
         * to say so.
         */
        if (throwable != null) {
            context.log(message, throwable);
        } else {
            context.log(message);
        }
    }


    /**
     * Add standard CGI variable names to the given collection.
     *
     * @param variableNames the collection to add variable names to
     */
    @Override
    public void addVariableNames(Collection<String> variableNames) {
        for (String variableName : VARIABLE_NAMES) {
            String variableValue = getVariableValue(variableName);
            if (variableValue != null) {
                variableNames.add(variableName);
            }
        }
        Enumeration<String> e = req.getAttributeNames();
        while (e.hasMoreElements()) {
            String name = e.nextElement();
            if (!isNameReserved(name)) {
                variableNames.add(name);
            }
        }
    }


    /**
     * Get a request attribute by name, case-insensitive.
     *
     * @param targetName the attribute name
     *
     * @return the attribute value or null
     */
    protected Object getReqAttributeIgnoreCase(String targetName) {
        Object object = null;
        if (!isNameReserved(targetName)) {
            object = req.getAttribute(targetName);
            if (object == null) {
                Enumeration<String> e = req.getAttributeNames();
                while (e.hasMoreElements()) {
                    String name = e.nextElement();
                    if (targetName.equalsIgnoreCase(name) && !isNameReserved(name)) {
                        object = req.getAttribute(name);
                        if (object != null) {
                            break;
                        }
                    }
                }
            }
        }
        return object;
    }


    /**
     * Check if a name is reserved (java., javax., sun. prefixes).
     *
     * @param name the name to check
     *
     * @return true if the name is reserved
     */
    protected boolean isNameReserved(String name) {
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.");
    }


    /**
     * Set an SSI variable value as a request attribute.
     *
     * @param name the variable name
     * @param value the variable value
     */
    @Override
    public void setVariableValue(String name, String value) {
        if (!isNameReserved(name)) {
            req.setAttribute(name, value);
        }
    }


    /**
     * Get the value of an SSI variable.
     *
     * @param name the variable name
     *
     * @return the variable value or null
     */
    @Override
    public String getVariableValue(String name) {
        String retVal;
        Object object = getReqAttributeIgnoreCase(name);
        if (object != null) {
            retVal = object.toString();
        } else {
            retVal = getCGIVariable(name);
        }
        return retVal;
    }


    /**
     * Get the value of a CGI variable by name.
     *
     * @param name the CGI variable name
     *
     * @return the variable value or null
     */
    protected String getCGIVariable(String name) {
        String retVal = null;
        String[] nameParts = name.toUpperCase(Locale.ENGLISH).split("_");
        int requiredParts = 2;
        if (nameParts.length == 1) {
            if (nameParts[0].equals("PATH")) {
                requiredParts = 1;
            }
        } else if (nameParts[0].equals("AUTH")) {
            if (nameParts[1].equals("TYPE")) {
                retVal = req.getAuthType();
            }
        } else if (nameParts[0].equals("CONTENT")) {
            if (nameParts[1].equals("LENGTH")) {
                long contentLength = req.getContentLengthLong();
                if (contentLength >= 0) {
                    retVal = Long.toString(contentLength);
                }
            } else if (nameParts[1].equals("TYPE")) {
                retVal = req.getContentType();
            }
        } else if (nameParts[0].equals("DOCUMENT")) {
            if (nameParts[1].equals("NAME")) {
                String requestURI = req.getRequestURI();
                retVal = requestURI.substring(requestURI.lastIndexOf('/') + 1);
            } else if (nameParts[1].equals("URI")) {
                retVal = req.getRequestURI();
            }
        } else if (name.equalsIgnoreCase("GATEWAY_INTERFACE")) {
            retVal = "CGI/1.1";
        } else if (nameParts[0].equals("HTTP")) {
            if (nameParts[1].equals("ACCEPT")) {
                String accept = null;
                if (nameParts.length == 2) {
                    accept = "Accept";
                } else if (nameParts[2].equals("ENCODING")) {
                    requiredParts = 3;
                    accept = "Accept-Encoding";
                } else if (nameParts[2].equals("LANGUAGE")) {
                    requiredParts = 3;
                    accept = "Accept-Language";
                }
                if (accept != null) {
                    Enumeration<String> acceptHeaders = req.getHeaders(accept);
                    if (acceptHeaders != null) {
                        if (acceptHeaders.hasMoreElements()) {
                            StringBuilder rv = new StringBuilder(acceptHeaders.nextElement());
                            while (acceptHeaders.hasMoreElements()) {
                                rv.append(", ");
                                rv.append(acceptHeaders.nextElement());
                            }
                            retVal = rv.toString();
                        }
                    }
                }
            } else if (nameParts[1].equals("CONNECTION")) {
                retVal = req.getHeader("Connection");
            } else if (nameParts[1].equals("HOST")) {
                retVal = req.getHeader("Host");
            } else if (nameParts[1].equals("REFERER")) {
                retVal = req.getHeader("Referer");
            } else if (nameParts[1].equals("USER")) {
                if (nameParts.length == 3) {
                    if (nameParts[2].equals("AGENT")) {
                        requiredParts = 3;
                        retVal = req.getHeader("User-Agent");
                    }
                }
            }

        } else if (nameParts[0].equals("PATH")) {
            if (nameParts[1].equals("INFO")) {
                retVal = req.getPathInfo();
            } else if (nameParts[1].equals("TRANSLATED")) {
                retVal = req.getPathTranslated();
            }
        } else if (nameParts[0].equals("QUERY")) {
            if (nameParts[1].equals("STRING")) {
                String queryString = req.getQueryString();
                if (nameParts.length == 2) {
                    // apache displays this as an empty string rather than (none)
                    retVal = nullToEmptyString(queryString);
                } else if (nameParts[2].equals("UNESCAPED")) {
                    requiredParts = 3;
                    if (queryString != null) {
                        Charset uriCharset = null;
                        Charset requestCharset = null;
                        boolean useBodyEncodingForURI = false;

                        // Get encoding settings from request / connector if possible
                        if (req instanceof Request) {
                            try {
                                requestCharset = ((Request) req).getCoyoteRequest().getCharset();
                            } catch (UnsupportedEncodingException e) {
                                // Ignore
                            }
                            Connector connector = ((Request) req).getConnector();
                            uriCharset = connector.getURICharset();
                            useBodyEncodingForURI = connector.getUseBodyEncodingForURI();
                        }

                        Charset queryStringCharset;

                        // If valid, apply settings from request / connector
                        if (useBodyEncodingForURI && requestCharset != null) {
                            queryStringCharset = requestCharset;
                        } else if (uriCharset != null) {
                            queryStringCharset = uriCharset;
                        } else {
                            // Use default as a last resort
                            queryStringCharset = StandardCharsets.UTF_8;
                        }

                        retVal = UDecoder.URLDecode(queryString, queryStringCharset);
                    }
                }
            }
        } else if (nameParts[0].equals("REMOTE")) {
            if (nameParts[1].equals("ADDR")) {
                retVal = req.getRemoteAddr();
            } else if (nameParts[1].equals("HOST")) {
                retVal = req.getRemoteHost();
            } else if (nameParts[1].equals("IDENT")) {
                // Not implemented
            } else if (nameParts[1].equals("PORT")) {
                retVal = Integer.toString(req.getRemotePort());
            } else if (nameParts[1].equals("USER")) {
                retVal = req.getRemoteUser();
            }
        } else if (nameParts[0].equals("REQUEST")) {
            if (nameParts[1].equals("METHOD")) {
                retVal = req.getMethod();
            } else if (nameParts[1].equals("URI")) {
                // If this is an error page, get the original URI
                retVal = (String) req.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
                if (retVal == null) {
                    retVal = req.getRequestURI();
                }
            }
        } else if (nameParts[0].equals("SCRIPT")) {
            String scriptName = req.getServletPath();
            if (nameParts[1].equals("FILENAME")) {
                retVal = context.getRealPath(scriptName);
            } else if (nameParts[1].equals("NAME")) {
                retVal = scriptName;
            }
        } else if (nameParts[0].equals("SERVER")) {
            if (nameParts[1].equals("ADDR")) {
                retVal = req.getLocalAddr();
            }
            if (nameParts[1].equals("NAME")) {
                retVal = req.getServerName();
            } else if (nameParts[1].equals("PORT")) {
                retVal = Integer.toString(req.getServerPort());
            } else if (nameParts[1].equals("PROTOCOL")) {
                retVal = req.getProtocol();
            } else if (nameParts[1].equals("SOFTWARE")) {
                retVal = context.getServerInfo() + ' ' + System.getProperty("java.vm.name") + '/' +
                        System.getProperty("java.vm.version") + ' ' + System.getProperty("os.name");
            }
        } else if (name.equalsIgnoreCase("UNIQUE_ID")) {
            retVal = req.getRequestedSessionId();
        }
        if (requiredParts != nameParts.length) {
            return null;
        }
        return retVal;
    }

    /**
     * Get the current date.
     *
     * @return the current date
     */
    @Override
    public Date getCurrentDate() {
        return new Date();
    }


    /**
     * Convert null to an empty string.
     *
     * @param string the input string
     *
     * @return the string or empty string if null
     */
    protected String nullToEmptyString(String string) {
        String retVal = string;
        if (retVal == null) {
            retVal = "";
        }
        return retVal;
    }


    /**
     * Get the path portion without the file name.
     *
     * @param servletPath the servlet path
     *
     * @return the path without file name or null
     */
    protected String getPathWithoutFileName(String servletPath) {
        String retVal = null;
        int lastSlash = servletPath.lastIndexOf('/');
        if (lastSlash >= 0) {
            // cut off file name
            retVal = servletPath.substring(0, lastSlash + 1);
        }
        return retVal;
    }


    /**
     * Remove the context path prefix from a servlet path.
     *
     * @param contextPath the context path
     * @param servletPath the servlet path
     *
     * @return the path without context prefix
     */
    protected String getPathWithoutContext(final String contextPath, final String servletPath) {
        if (servletPath.startsWith(contextPath)) {
            return servletPath.substring(contextPath.length());
        }
        return servletPath;
    }


    /**
     * Resolve a relative path to an absolute normalized path.
     *
     * @param path the relative path
     *
     * @return the absolute normalized path
     *
     * @throws IOException if path normalization fails
     */
    protected String getAbsolutePath(String path) throws IOException {
        String pathWithoutContext = SSIServletRequestUtil.getRelativePath(req);
        String prefix = getPathWithoutFileName(pathWithoutContext);
        if (prefix == null) {
            throw new IOException(sm.getString("ssiServletExternalResolver.removeFilenameError", pathWithoutContext));
        }
        String fullPath = prefix + path;
        String retVal = RequestUtil.normalize(fullPath);
        if (retVal == null) {
            throw new IOException(sm.getString("ssiServletExternalResolver.normalizationError", fullPath));
        }
        return retVal;
    }


    /**
     * Resolve a non-virtual (relative) path to a servlet context and path.
     *
     * @param nonVirtualPath the non-virtual path
     *
     * @return the servlet context and resolved path
     *
     * @throws IOException if the path is invalid
     */
    protected ServletContextAndPath getServletContextAndPathFromNonVirtualPath(String nonVirtualPath)
            throws IOException {
        if (nonVirtualPath.startsWith("/") || nonVirtualPath.startsWith("\\")) {
            throw new IOException(sm.getString("ssiServletExternalResolver.absoluteNonVirtualPath", nonVirtualPath));
        }
        if (nonVirtualPath.contains("../")) {
            throw new IOException(
                    sm.getString("ssiServletExternalResolver.pathTraversalNonVirtualPath", nonVirtualPath));
        }
        return new ServletContextAndPath(context, getAbsolutePath(nonVirtualPath));
    }


    /**
     * Resolve a virtual path to a servlet context and path.
     *
     * @param virtualPath the virtual path
     *
     * @return the servlet context and resolved path
     *
     * @throws IOException if the path cannot be resolved
     */
    protected ServletContextAndPath getServletContextAndPathFromVirtualPath(String virtualPath) throws IOException {

        if (!virtualPath.startsWith("/") && !virtualPath.startsWith("\\")) {
            return new ServletContextAndPath(context, getAbsolutePath(virtualPath));
        }

        String normalized = RequestUtil.normalize(virtualPath);
        if (isVirtualWebappRelative) {
            return new ServletContextAndPath(context, normalized);
        }

        ServletContext normContext = context.getContext(normalized);
        if (normContext == null) {
            throw new IOException(sm.getString("ssiServletExternalResolver.noContext", normalized));
        }
        // If it's the root context, then there is no context element to remove.
        // ie: '/file1.shtml' vs '/appName1/file1.shtml'
        if (!isRootContext(normContext)) {
            String noContext = getPathWithoutContext(normContext.getContextPath(), normalized);
            return new ServletContextAndPath(normContext, noContext);
        }

        return new ServletContextAndPath(normContext, normalized);
    }


    // Assumes servletContext is not-null
    // Assumes that identity comparison will be true for the same context
    // Assuming the above, getContext("/") will be non-null as long as the root context is accessible.
    // If it isn't, then servletContext can't be the root context anyway, hence they will not match.
    /**
     * Check if the given servlet context is the root context.
     *
     * @param servletContext the servlet context
     *
     * @return true if the context is the root
     */
    protected boolean isRootContext(ServletContext servletContext) {
        return servletContext == servletContext.getContext("/");
    }


    /**
     * Resolve a path to a servlet context and path.
     *
     * @param originalPath the original path
     * @param virtual whether the path is virtual
     *
     * @return the servlet context and resolved path
     *
     * @throws IOException if the path cannot be resolved
     */
    protected ServletContextAndPath getServletContextAndPath(String originalPath, boolean virtual) throws IOException {
        if (debug > 0) {
            log("SSIServletExternalResolver.getServletContextAndPath( " + originalPath + ", " + virtual + ")", null);
        }
        if (virtual) {
            return getServletContextAndPathFromVirtualPath(originalPath);
        } else {
            return getServletContextAndPathFromNonVirtualPath(originalPath);
        }
    }


    /**
     * Get a URL connection for the given path.
     *
     * @param originalPath the original path
     * @param virtual whether the path is virtual
     *
     * @return the URL connection
     *
     * @throws IOException if the resource is not found
     */
    protected URLConnection getURLConnection(String originalPath, boolean virtual) throws IOException {
        ServletContextAndPath csAndP = getServletContextAndPath(originalPath, virtual);
        ServletContext context = csAndP.getServletContext();
        String path = csAndP.getPath();
        URL url = context.getResource(path);
        if (url == null) {
            throw new IOException(sm.getString("ssiServletExternalResolver.noResource", path));
        }
        return url.openConnection();
    }


    /**
     * Get the last modified time of a file.
     *
     * @param path the file path
     * @param virtual whether the path is virtual
     *
     * @return the last modified time in milliseconds
     *
     * @throws IOException if the file cannot be accessed
     */
    @Override
    public long getFileLastModified(String path, boolean virtual) throws IOException {
        long lastModified = 0;
        try {
            URLConnection urlConnection = getURLConnection(path, virtual);
            lastModified = urlConnection.getLastModified();
        } catch (IOException ignore) {
            // Ignore this. It will always fail for non-file based includes
        }
        return lastModified;
    }


    /**
     * Get the size of a file.
     *
     * @param path the file path
     * @param virtual whether the path is virtual
     *
     * @return the file size in bytes or -1 if unknown
     *
     * @throws IOException if the file cannot be accessed
     */
    @Override
    public long getFileSize(String path, boolean virtual) throws IOException {
        long fileSize = -1;
        try {
            URLConnection urlConnection = getURLConnection(path, virtual);
            fileSize = urlConnection.getContentLengthLong();
        } catch (IOException ignore) {
            // Ignore this. It will always fail for non-file based includes
        }
        return fileSize;
    }


    /*
     * We are making lots of unnecessary copies of the included data here. If someone ever complains that this is slow,
     * we should connect the included stream to the print writer that SSICommand uses.
     */
    /**
     * Get the text content of a file.
     *
     * @param originalPath the original file path
     * @param virtual whether the path is virtual
     *
     * @return the file content as text
     *
     * @throws IOException if the file cannot be read
     */
    @Override
    public String getFileText(String originalPath, boolean virtual) throws IOException {
        try {
            ServletContextAndPath csAndP = getServletContextAndPath(originalPath, virtual);
            ServletContext context = csAndP.getServletContext();
            String path = csAndP.getPath();
            RequestDispatcher rd = context.getRequestDispatcher(path);
            if (rd == null) {
                throw new IOException(sm.getString("ssiServletExternalResolver.requestDispatcherError", path));
            }
            ByteArrayServletOutputStream basos = new ByteArrayServletOutputStream();
            ResponseIncludeWrapper responseIncludeWrapper = new ResponseIncludeWrapper(res, basos);
            rd.include(req, responseIncludeWrapper);
            // We can't assume the included servlet flushed its output
            responseIncludeWrapper.flushOutputStreamOrWriter();
            byte[] bytes = basos.toByteArray();

            // Assume platform default encoding unless otherwise specified
            String retVal;
            if (inputEncoding == null) {
                retVal = new String(bytes);
            } else {
                retVal = new String(bytes, B2CConverter.getCharset(inputEncoding));
            }

            /*
             * Make an assumption that an empty response is a failure. This is a problem if a truly empty file were
             * included, but not sure how else to tell.
             */
            if (retVal.isEmpty() && !Method.HEAD.equals(req.getMethod())) {
                throw new IOException(sm.getString("ssiServletExternalResolver.noFile", path));
            }
            return retVal;
        } catch (ServletException e) {
            throw new IOException(sm.getString("ssiServletExternalResolver.noIncludeFile", originalPath), e);
        }
    }

    protected static class ServletContextAndPath {
        protected final ServletContext servletContext;
        protected final String path;


        public ServletContextAndPath(ServletContext servletContext, String path) {
            this.servletContext = servletContext;
            this.path = path;
        }


        public ServletContext getServletContext() {
            return servletContext;
        }

        /**
         * Get the path.
         *
         * @return the path
         */
        public String getPath() {
            return path;
        }
    }
}
