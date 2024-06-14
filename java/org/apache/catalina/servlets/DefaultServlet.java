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
package org.apache.catalina.servlets;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.connector.ResponseFacade;
import org.apache.catalina.util.IOTools;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.URLEncoder;
import org.apache.catalina.webresources.CachedResource;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.http.ResponseUtil;
import org.apache.tomcat.util.http.parser.ContentRange;
import org.apache.tomcat.util.http.parser.EntityTag;
import org.apache.tomcat.util.http.parser.Ranges;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;


/**
 * <p>
 * The default resource-serving servlet for most web applications, used to serve static resources such as HTML pages and
 * images.
 * </p>
 * <p>
 * This servlet is intended to be mapped to <em>/</em> e.g.:
 * </p>
 *
 * <pre>
 *   &lt;servlet-mapping&gt;
 *       &lt;servlet-name&gt;default&lt;/servlet-name&gt;
 *       &lt;url-pattern&gt;/&lt;/url-pattern&gt;
 *   &lt;/servlet-mapping&gt;
 * </pre>
 * <p>
 * It can be mapped to sub-paths, however in all cases resources are served from the web application resource root using
 * the full path from the root of the web application context. <br>
 * e.g. given a web application structure:
 * </p>
 *
 * <pre>
 * /context
 *   /images
 *     tomcat2.jpg
 *   /static
 *     /images
 *       tomcat.jpg
 * </pre>
 * <p>
 * ... and a servlet mapping that maps only <code>/static/*</code> to the default servlet:
 * </p>
 *
 * <pre>
 *   &lt;servlet-mapping&gt;
 *       &lt;servlet-name&gt;default&lt;/servlet-name&gt;
 *       &lt;url-pattern&gt;/static/*&lt;/url-pattern&gt;
 *   &lt;/servlet-mapping&gt;
 * </pre>
 * <p>
 * Then a request to <code>/context/static/images/tomcat.jpg</code> will succeed while a request to
 * <code>/context/images/tomcat2.jpg</code> will fail.
 * </p>
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class DefaultServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(DefaultServlet.class);

    /**
     * Full range marker.
     */
    protected static final Ranges FULL = new Ranges(null, new ArrayList<>());

    private static final ContentRange IGNORE = new ContentRange(null, 0, 0, 0);

    /**
     * MIME multipart separation string
     */
    protected static final String mimeSeparation = "CATALINA_MIME_BOUNDARY";

    /**
     * Size of file transfer buffer in bytes.
     */
    protected static final int BUFFER_SIZE = 4096;


    // ----------------------------------------------------- Instance Variables

    /**
     * The debugging detail level for this servlet.
     */
    protected int debug = 0;

    /**
     * The input buffer size to use when serving resources.
     */
    protected int input = 2048;

    /**
     * Should we generate directory listings?
     */
    protected boolean listings = false;

    /**
     * Status code to use for directory redirects.
     */
    protected int directoryRedirectStatusCode = HttpServletResponse.SC_FOUND;

    /**
     * Read only flag. By default, it's set to true.
     */
    protected boolean readOnly = true;

    /**
     * List of compression formats to serve and their preference order.
     */
    protected CompressionFormat[] compressionFormats;

    /**
     * The output buffer size to use when serving resources.
     */
    protected int output = 2048;

    /**
     * Allow customized directory listing per directory.
     */
    protected String localXsltFile = null;

    /**
     * Allow customized directory listing per context.
     */
    protected String contextXsltFile = null;

    /**
     * Allow customized directory listing per instance.
     */
    protected String globalXsltFile = null;

    /**
     * Allow a readme file to be included.
     */
    protected String readmeFile = null;

    /**
     * The complete set of web application resources
     */
    protected transient WebResourceRoot resources = null;

    /**
     * File encoding to be used when reading static files. If none is specified the platform default is used.
     */
    protected String fileEncoding = null;
    private transient Charset fileEncodingCharset = null;

    /**
     * If a file has a BOM, should that be used in preference to fileEncoding? Will default to {@link BomConfig#TRUE} in
     * {@link #init()}.
     */
    private BomConfig useBomIfPresent = null;

    /**
     * Minimum size for sendfile usage in bytes.
     */
    protected int sendfileSize = 48 * 1024;

    /**
     * Should the Accept-Ranges: bytes header be send with static resources?
     */
    protected boolean useAcceptRanges = true;

    /**
     * Flag to determine if server information is presented.
     */
    protected boolean showServerInfo = true;

    /**
     * Flag to determine if resources should be sorted.
     */
    protected boolean sortListings = false;

    /**
     * The sorting manager for sorting files and directories.
     */
    protected transient SortManager sortManager;

    /**
     * Flag that indicates whether partial PUTs are permitted.
     */
    private boolean allowPartialPut = true;


    // --------------------------------------------------------- Public Methods

    @Override
    public void destroy() {
        // NOOP
    }


    @Override
    public void init() throws ServletException {

        if (getServletConfig().getInitParameter("debug") != null) {
            debug = Integer.parseInt(getServletConfig().getInitParameter("debug"));
        }

        if (getServletConfig().getInitParameter("input") != null) {
            input = Integer.parseInt(getServletConfig().getInitParameter("input"));
        }

        if (getServletConfig().getInitParameter("output") != null) {
            output = Integer.parseInt(getServletConfig().getInitParameter("output"));
        }

        listings = Boolean.parseBoolean(getServletConfig().getInitParameter("listings"));

        if (getServletConfig().getInitParameter("directoryRedirectStatusCode") != null) {
            String statusCodeString = getServletConfig().getInitParameter("directoryRedirectStatusCode");
            int statusCode = Integer.parseInt(statusCodeString);
            switch (statusCode) {
                case HttpServletResponse.SC_MOVED_PERMANENTLY:
                case HttpServletResponse.SC_FOUND:
                case HttpServletResponse.SC_TEMPORARY_REDIRECT:
                case HttpServletResponse.SC_PERMANENT_REDIRECT:
                    directoryRedirectStatusCode = statusCode;
                    break;
                default:
                    log("Invalid redirectStatusCode of " + statusCode);
            }
        }

        if (getServletConfig().getInitParameter("readonly") != null) {
            readOnly = Boolean.parseBoolean(getServletConfig().getInitParameter("readonly"));
        }

        compressionFormats = parseCompressionFormats(getServletConfig().getInitParameter("precompressed"),
                getServletConfig().getInitParameter("gzip"));

        if (getServletConfig().getInitParameter("sendfileSize") != null) {
            sendfileSize = Integer.parseInt(getServletConfig().getInitParameter("sendfileSize")) * 1024;
        }

        fileEncoding = getServletConfig().getInitParameter("fileEncoding");
        if (fileEncoding == null) {
            fileEncodingCharset = Charset.defaultCharset();
            fileEncoding = fileEncodingCharset.name();
        } else {
            try {
                fileEncodingCharset = B2CConverter.getCharset(fileEncoding);
            } catch (UnsupportedEncodingException e) {
                throw new ServletException(e);
            }
        }

        String useBomIfPresent = getServletConfig().getInitParameter("useBomIfPresent");
        if (useBomIfPresent == null) {
            // Use default
            this.useBomIfPresent = BomConfig.TRUE;
        } else {
            for (BomConfig bomConfig : BomConfig.values()) {
                if (bomConfig.configurationValue.equalsIgnoreCase(useBomIfPresent)) {
                    this.useBomIfPresent = bomConfig;
                    break;
                }
            }
            if (this.useBomIfPresent == null) {
                // Unrecognised configuration value
                IllegalArgumentException iae =
                        new IllegalArgumentException(sm.getString("defaultServlet.unknownBomConfig", useBomIfPresent));
                throw new ServletException(iae);
            }
        }

        globalXsltFile = getServletConfig().getInitParameter("globalXsltFile");
        contextXsltFile = getServletConfig().getInitParameter("contextXsltFile");
        localXsltFile = getServletConfig().getInitParameter("localXsltFile");
        readmeFile = getServletConfig().getInitParameter("readmeFile");

        if (getServletConfig().getInitParameter("useAcceptRanges") != null) {
            useAcceptRanges = Boolean.parseBoolean(getServletConfig().getInitParameter("useAcceptRanges"));
        }

        // Prevent the use of buffer sizes that are too small
        if (input < 256) {
            input = 256;
        }
        if (output < 256) {
            output = 256;
        }

        if (debug > 0) {
            log("DefaultServlet.init:  input buffer size=" + input + ", output buffer size=" + output);
        }

        // Load the web resources
        resources = (WebResourceRoot) getServletContext().getAttribute(Globals.RESOURCES_ATTR);

        if (resources == null) {
            throw new UnavailableException(sm.getString("defaultServlet.noResources"));
        }

        if (getServletConfig().getInitParameter("showServerInfo") != null) {
            showServerInfo = Boolean.parseBoolean(getServletConfig().getInitParameter("showServerInfo"));
        }

        if (getServletConfig().getInitParameter("sortListings") != null) {
            sortListings = Boolean.parseBoolean(getServletConfig().getInitParameter("sortListings"));

            if (sortListings) {
                boolean sortDirectoriesFirst;
                if (getServletConfig().getInitParameter("sortDirectoriesFirst") != null) {
                    sortDirectoriesFirst =
                            Boolean.parseBoolean(getServletConfig().getInitParameter("sortDirectoriesFirst"));
                } else {
                    sortDirectoriesFirst = false;
                }

                sortManager = new SortManager(sortDirectoriesFirst);
            }
        }

        if (getServletConfig().getInitParameter("allowPartialPut") != null) {
            allowPartialPut = Boolean.parseBoolean(getServletConfig().getInitParameter("allowPartialPut"));
        }
    }

    private CompressionFormat[] parseCompressionFormats(String precompressed, String gzip) {
        List<CompressionFormat> ret = new ArrayList<>();
        if (precompressed != null && precompressed.indexOf('=') > 0) {
            for (String pair : precompressed.split(",")) {
                String[] setting = pair.split("=");
                String encoding = setting[0];
                String extension = setting[1];
                ret.add(new CompressionFormat(extension, encoding));
            }
        } else if (precompressed != null) {
            if (Boolean.parseBoolean(precompressed)) {
                ret.add(new CompressionFormat(".br", "br"));
                ret.add(new CompressionFormat(".gz", "gzip"));
            }
        } else if (Boolean.parseBoolean(gzip)) {
            // gzip handling is for backwards compatibility with Tomcat 8.x
            ret.add(new CompressionFormat(".gz", "gzip"));
        }
        return ret.toArray(new CompressionFormat[0]);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Return the relative path associated with this servlet.
     *
     * @param request The servlet request we are processing
     *
     * @return the relative path
     */
    protected String getRelativePath(HttpServletRequest request) {
        return getRelativePath(request, false);
    }

    protected String getRelativePath(HttpServletRequest request, boolean allowEmptyPath) {
        // IMPORTANT: DefaultServlet can be mapped to '/' or '/path/*' but always
        // serves resources from the web app root with context rooted paths.
        // i.e. it cannot be used to mount the web app root under a sub-path
        // This method must construct a complete context rooted path, although
        // subclasses can change this behaviour.

        String servletPath;
        String pathInfo;

        if (request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null) {
            // For includes, get the info from the attributes
            pathInfo = (String) request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
            servletPath = (String) request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
        } else {
            pathInfo = request.getPathInfo();
            servletPath = request.getServletPath();
        }

        StringBuilder result = new StringBuilder();
        if (servletPath.length() > 0) {
            result.append(servletPath);
        }
        if (pathInfo != null) {
            result.append(pathInfo);
        }
        if (result.length() == 0 && !allowEmptyPath) {
            result.append('/');
        }

        return result.toString();
    }


    /**
     * Determines the appropriate path to prepend resources with when generating directory listings. Depending on the
     * behaviour of {@link #getRelativePath(HttpServletRequest)} this will change.
     *
     * @param request the request to determine the path for
     *
     * @return the prefix to apply to all resources in the listing.
     */
    protected String getPathPrefix(final HttpServletRequest request) {
        return request.getContextPath();
    }


    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        if (req.getDispatcherType() == DispatcherType.ERROR) {
            doGet(req, resp);
        } else {
            super.service(req, resp);
        }
    }


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        // Serve the requested resource, including the data content
        serveResource(request, response, true, fileEncoding);

    }


    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        // Serve the requested resource, without the data content unless we are
        // being included since in that case the content needs to be provided so
        // the correct content length is reported for the including resource
        boolean serveContent = DispatcherType.INCLUDE.equals(request.getDispatcherType());
        serveResource(request, response, serveContent, fileEncoding);
    }


    /**
     * Override default implementation to ensure that TRACE is correctly handled.
     *
     * @param req  the {@link HttpServletRequest} object that contains the request the client made of the servlet
     * @param resp the {@link HttpServletResponse} object that contains the response the servlet returns to the client
     *
     * @exception IOException      if an input or output error occurs while the servlet is handling the OPTIONS request
     * @exception ServletException if the request for the OPTIONS cannot be handled
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        resp.setHeader("Allow", determineMethodsAllowed(req));
    }


    /**
     * Determines the methods normally allowed for the resource.
     *
     * @param req The Servlet request
     *
     * @return The allowed HTTP methods
     */
    protected String determineMethodsAllowed(HttpServletRequest req) {
        StringBuilder allow = new StringBuilder();

        // Start with methods that are always allowed
        allow.append("OPTIONS, GET, HEAD, POST");

        // PUT and DELETE depend on readonly
        if (!readOnly) {
            allow.append(", PUT, DELETE");
        }

        // Trace - assume disabled unless we can prove otherwise
        if (req instanceof RequestFacade && ((RequestFacade) req).getAllowTrace()) {
            allow.append(", TRACE");
        }

        return allow.toString();
    }


    protected void sendNotAllowed(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.addHeader("Allow", determineMethodsAllowed(req));
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        doGet(request, response);
    }


    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        if (readOnly) {
            sendNotAllowed(req, resp);
            return;
        }

        String path = getRelativePath(req);

        WebResource resource = resources.getResource(path);

        ContentRange range = parseContentRange(req, resp);

        if (range == null) {
            // Processing error. parseContentRange() set the error code
            return;
        }

        InputStream resourceInputStream = null;

        try {
            // Append data specified in ranges to existing content for this
            // resource - create a temp. file on the local filesystem to
            // perform this operation
            // Assume just one range is specified for now
            if (range == IGNORE) {
                resourceInputStream = req.getInputStream();
            } else {
                File contentFile = executePartialPut(req, range, path);
                resourceInputStream = new FileInputStream(contentFile);
            }

            if (resources.write(path, resourceInputStream, true)) {
                if (resource.exists()) {
                    resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                } else {
                    resp.setStatus(HttpServletResponse.SC_CREATED);
                }
            } else {
                resp.sendError(HttpServletResponse.SC_CONFLICT);
            }
        } finally {
            if (resourceInputStream != null) {
                try {
                    resourceInputStream.close();
                } catch (IOException ioe) {
                    // Ignore
                }
            }
        }
    }


    /**
     * Handle a partial PUT. New content specified in request is appended to existing content in oldRevisionContent (if
     * present). This code does not support simultaneous partial updates to the same resource.
     *
     * @param req   The Servlet request
     * @param range The range that will be written
     * @param path  The path
     *
     * @return the associated file object
     *
     * @throws IOException an IO error occurred
     */
    protected File executePartialPut(HttpServletRequest req, ContentRange range, String path) throws IOException {

        // Append data specified in ranges to existing content for this
        // resource - create a temp. file on the local filesystem to
        // perform this operation
        File tempDir = (File) getServletContext().getAttribute(ServletContext.TEMPDIR);
        // Convert all '/' characters to '.' in resourcePath
        String convertedResourcePath = path.replace('/', '.');
        File contentFile = new File(tempDir, convertedResourcePath);
        if (contentFile.createNewFile()) {
            // Clean up contentFile when Tomcat is terminated
            contentFile.deleteOnExit();
        }

        try (RandomAccessFile randAccessContentFile = new RandomAccessFile(contentFile, "rw")) {

            WebResource oldResource = resources.getResource(path);

            // Copy data in oldRevisionContent to contentFile
            if (oldResource.isFile()) {
                try (BufferedInputStream bufOldRevStream =
                        new BufferedInputStream(oldResource.getInputStream(), BUFFER_SIZE)) {

                    int numBytesRead;
                    byte[] copyBuffer = new byte[BUFFER_SIZE];
                    while ((numBytesRead = bufOldRevStream.read(copyBuffer)) != -1) {
                        randAccessContentFile.write(copyBuffer, 0, numBytesRead);
                    }

                }
            }

            randAccessContentFile.setLength(range.getLength());

            // Append data in request input stream to contentFile
            randAccessContentFile.seek(range.getStart());
            int numBytesRead;
            byte[] transferBuffer = new byte[BUFFER_SIZE];
            try (BufferedInputStream requestBufInStream = new BufferedInputStream(req.getInputStream(), BUFFER_SIZE)) {
                while ((numBytesRead = requestBufInStream.read(transferBuffer)) != -1) {
                    randAccessContentFile.write(transferBuffer, 0, numBytesRead);
                }
            }
        }

        return contentFile;
    }


    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        if (readOnly) {
            sendNotAllowed(req, resp);
            return;
        }

        String path = getRelativePath(req);

        WebResource resource = resources.getResource(path);

        if (resource.exists()) {
            if (resource.delete()) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }

    }


    /**
     * Check if the conditions specified in the optional If headers are satisfied.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resource The resource
     *
     * @return <code>true</code> if the resource meets all the specified conditions, and <code>false</code> if any of
     *             the conditions is not satisfied, in which case request processing is stopped
     *
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfHeaders(HttpServletRequest request, HttpServletResponse response, WebResource resource)
            throws IOException {

        return checkIfMatch(request, response, resource) && checkIfModifiedSince(request, response, resource) &&
                checkIfNoneMatch(request, response, resource) && checkIfUnmodifiedSince(request, response, resource);

    }


    /**
     * URL rewriter.
     *
     * @param path Path which has to be rewritten
     *
     * @return the rewritten path
     */
    protected String rewriteUrl(String path) {
        return URLEncoder.DEFAULT.encode(path, StandardCharsets.UTF_8);
    }


    /**
     * Serve the specified resource, optionally including the data content.
     *
     * @param request       The servlet request we are processing
     * @param response      The servlet response we are creating
     * @param content       Should the content be included?
     * @param inputEncoding The encoding to use if it is necessary to access the source as characters rather than as
     *                          bytes
     *
     * @exception IOException      if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void serveResource(HttpServletRequest request, HttpServletResponse response, boolean content,
            String inputEncoding) throws IOException, ServletException {

        boolean serveContent = content;

        // Identify the requested resource path
        String path = getRelativePath(request, true);

        if (debug > 0) {
            if (serveContent) {
                log("DefaultServlet.serveResource:  Serving resource '" + path + "' headers and data");
            } else {
                log("DefaultServlet.serveResource:  Serving resource '" + path + "' headers only");
            }
        }

        if (path.length() == 0) {
            // Context root redirect
            doDirectoryRedirect(request, response);
            return;
        }

        WebResource resource = resources.getResource(path);
        boolean isError = DispatcherType.ERROR == request.getDispatcherType();

        if (!resource.exists()) {
            // Check if we're included so we can return the appropriate
            // missing resource name in the error
            String requestUri = (String) request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI);
            if (requestUri == null) {
                requestUri = request.getRequestURI();
            } else {
                // We're included
                // SRV.9.3 says we must throw a FNFE
                throw new FileNotFoundException(sm.getString("defaultServlet.missingResource", requestUri));
            }

            if (isError) {
                response.sendError(((Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).intValue());
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        sm.getString("defaultServlet.missingResource", requestUri));
            }
            return;
        }

        if (!resource.canRead()) {
            // Check if we're included so we can return the appropriate
            // missing resource name in the error
            String requestUri = (String) request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI);
            if (requestUri == null) {
                requestUri = request.getRequestURI();
            } else {
                // We're included
                // Spec doesn't say what to do in this case but a FNFE seems
                // reasonable
                throw new FileNotFoundException(sm.getString("defaultServlet.missingResource", requestUri));
            }

            if (isError) {
                response.sendError(((Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).intValue());
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, requestUri);
            }
            return;
        }

        boolean included = false;
        // Check if the conditions specified in the optional If headers are
        // satisfied.
        if (resource.isFile()) {
            // Checking If headers
            included = (request.getAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH) != null);
            if (!included && !isError && !checkIfHeaders(request, response, resource)) {
                return;
            }
        }

        // Find content type.
        String contentType = resource.getMimeType();
        if (contentType == null) {
            contentType = getServletContext().getMimeType(resource.getName());
            resource.setMimeType(contentType);
        }

        // These need to reflect the original resource, not the potentially
        // precompressed version of the resource so get them now if they are going to
        // be needed later
        String eTag = null;
        String lastModifiedHttp = null;
        if (resource.isFile() && !isError) {
            eTag = generateETag(resource);
            lastModifiedHttp = resource.getLastModifiedHttp();
        }


        // Serve a precompressed version of the file if present
        boolean usingPrecompressedVersion = false;
        if (compressionFormats.length > 0 && !included && resource.isFile() && !pathEndsWithCompressedExtension(path)) {
            List<PrecompressedResource> precompressedResources = getAvailablePrecompressedResources(path);
            if (!precompressedResources.isEmpty()) {
                ResponseUtil.addVaryFieldName(response, "accept-encoding");
                PrecompressedResource bestResource = getBestPrecompressedResource(request, precompressedResources);
                if (bestResource != null) {
                    response.addHeader("Content-Encoding", bestResource.format.encoding);
                    resource = bestResource.resource;
                    usingPrecompressedVersion = true;
                }
            }
        }

        Ranges ranges = FULL;
        long contentLength = -1L;

        if (resource.isDirectory()) {
            if (!path.endsWith("/")) {
                doDirectoryRedirect(request, response);
                return;
            }

            // Skip directory listings if we have been configured to
            // suppress them
            if (!listings) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        sm.getString("defaultServlet.missingResource", request.getRequestURI()));
                return;
            }
            contentType = "text/html;charset=UTF-8";
        } else {
            if (!isError) {
                if (useAcceptRanges) {
                    // Accept ranges header
                    response.setHeader("Accept-Ranges", "bytes");
                }

                // Parse range specifier
                ranges = parseRange(request, response, resource);
                if (ranges == null) {
                    return;
                }

                // ETag header
                response.setHeader("ETag", eTag);

                // Last-Modified header
                response.setHeader("Last-Modified", lastModifiedHttp);
            }

            // Get content length
            contentLength = resource.getContentLength();
            // Special case for zero length files, which would cause a
            // (silent) ISE when setting the output buffer size
            if (contentLength == 0L) {
                serveContent = false;
            }
        }

        ServletOutputStream ostream = null;
        PrintWriter writer = null;

        if (serveContent) {
            // Trying to retrieve the servlet output stream
            try {
                ostream = response.getOutputStream();
            } catch (IllegalStateException e) {
                // If it fails, we try to get a Writer instead if we're
                // trying to serve a text file
                if (!usingPrecompressedVersion && isText(contentType)) {
                    writer = response.getWriter();
                    // Cannot reliably serve partial content with a Writer
                    ranges = FULL;
                } else {
                    throw e;
                }
            }
        }

        // Check to see if a Filter, Valve or wrapper has written some content.
        // If it has, disable range requests and setting of a content length
        // since neither can be done reliably.
        ServletResponse r = response;
        long contentWritten = 0;
        while (r instanceof ServletResponseWrapper) {
            r = ((ServletResponseWrapper) r).getResponse();
        }
        if (r instanceof ResponseFacade) {
            contentWritten = ((ResponseFacade) r).getContentWritten();
        }
        if (contentWritten > 0) {
            ranges = FULL;
        }

        String outputEncoding = response.getCharacterEncoding();
        Charset charset = B2CConverter.getCharset(outputEncoding);
        boolean conversionRequired;
        /*
         * The test below deliberately uses != to compare two Strings. This is because the code is looking to see if the
         * default character encoding has been returned because no explicit character encoding has been defined. There
         * is no clean way of doing this via the Servlet API. It would be possible to add a Tomcat specific API but that
         * would require quite a bit of code to get to the Tomcat specific request object that may have been wrapped.
         * The != test is a (slightly hacky) quick way of doing this.
         */
        boolean outputEncodingSpecified = outputEncoding != org.apache.coyote.Constants.DEFAULT_BODY_CHARSET.name() &&
                outputEncoding != resources.getContext().getResponseCharacterEncoding();
        if (!usingPrecompressedVersion && isText(contentType) && outputEncodingSpecified &&
                !charset.equals(fileEncodingCharset)) {
            conversionRequired = true;
            // Conversion often results fewer/more/different bytes.
            // That does not play nicely with range requests.
            ranges = FULL;
        } else {
            conversionRequired = false;
        }

        if (resource.isDirectory() || isError || ranges == FULL) {
            // Set the appropriate output headers
            if (contentType != null) {
                if (debug > 0) {
                    log("DefaultServlet.serveFile:  contentType='" + contentType + "'");
                }
                // Don't override a previously set content type
                if (response.getContentType() == null) {
                    response.setContentType(contentType);
                }
            }
            if (resource.isFile() && contentLength >= 0 && (!serveContent || ostream != null)) {
                if (debug > 0) {
                    log("DefaultServlet.serveFile:  contentLength=" + contentLength);
                }
                // Don't set a content length if something else has already
                // written to the response or if conversion will be taking place
                if (contentWritten == 0 && !conversionRequired) {
                    response.setContentLengthLong(contentLength);
                }
            }

            if (serveContent) {
                try {
                    response.setBufferSize(output);
                } catch (IllegalStateException e) {
                    // Silent catch
                }
                InputStream renderResult = null;
                if (ostream == null) {
                    // Output via a writer so can't use sendfile or write
                    // content directly.
                    if (resource.isDirectory()) {
                        renderResult = render(request, getPathPrefix(request), resource, inputEncoding);
                    } else {
                        renderResult = resource.getInputStream();
                        if (included) {
                            // Need to make sure any BOM is removed
                            if (!renderResult.markSupported()) {
                                renderResult = new BufferedInputStream(renderResult);
                            }
                            Charset bomCharset = processBom(renderResult, useBomIfPresent.stripBom);
                            if (bomCharset != null && useBomIfPresent.useBomEncoding) {
                                inputEncoding = bomCharset.name();
                            }
                        }
                    }
                    copy(renderResult, writer, inputEncoding);
                } else {
                    // Output is via an OutputStream
                    if (resource.isDirectory()) {
                        renderResult = render(request, getPathPrefix(request), resource, inputEncoding);
                    } else {
                        // Output is content of resource
                        // Check to see if conversion is required
                        if (conversionRequired || included) {
                            // When including a file, we need to check for a BOM
                            // to determine if a conversion is required, so we
                            // might as well always convert
                            InputStream source = resource.getInputStream();
                            if (!source.markSupported()) {
                                source = new BufferedInputStream(source);
                            }
                            Charset bomCharset = processBom(source, useBomIfPresent.stripBom);
                            if (bomCharset != null && useBomIfPresent.useBomEncoding) {
                                inputEncoding = bomCharset.name();
                            }
                            // Following test also ensures included resources
                            // are converted if an explicit output encoding was
                            // specified
                            if (outputEncodingSpecified) {
                                OutputStreamWriter osw = new OutputStreamWriter(ostream, charset);
                                PrintWriter pw = new PrintWriter(osw);
                                copy(source, pw, inputEncoding);
                                pw.flush();
                            } else {
                                // Just included but no conversion
                                renderResult = source;
                            }
                        } else {
                            if (!checkSendfile(request, response, resource, contentLength, null)) {
                                // sendfile not possible so check if resource
                                // content is available directly via
                                // CachedResource. Do not want to call
                                // getContent() on other resource
                                // implementations as that could trigger loading
                                // the contents of a very large file into memory
                                byte[] resourceBody = null;
                                if (resource instanceof CachedResource) {
                                    resourceBody = resource.getContent();
                                }
                                if (resourceBody == null) {
                                    // Resource content not directly available,
                                    // use InputStream
                                    renderResult = resource.getInputStream();
                                } else {
                                    // Use the resource content directly
                                    ostream.write(resourceBody);
                                }
                            }
                        }
                    }
                    // If a stream was configured, it needs to be copied to
                    // the output (this method closes the stream)
                    if (renderResult != null) {
                        copy(renderResult, ostream);
                    }
                }
            }

        } else {

            if ((ranges == null) || (ranges.getEntries().isEmpty())) {
                return;
            }

            // Partial content response.

            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

            if (ranges.getEntries().size() == 1) {

                Ranges.Entry range = ranges.getEntries().get(0);
                long start = getStart(range, contentLength);
                long end = getEnd(range, contentLength);
                response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + contentLength);
                long length = end - start + 1;
                response.setContentLengthLong(length);

                if (contentType != null) {
                    if (debug > 0) {
                        log("DefaultServlet.serveFile:  contentType='" + contentType + "'");
                    }
                    response.setContentType(contentType);
                }

                if (serveContent) {
                    try {
                        response.setBufferSize(output);
                    } catch (IllegalStateException e) {
                        // Silent catch
                    }
                    if (ostream != null) {
                        if (!checkSendfile(request, response, resource, contentLength, range)) {
                            copy(resource, contentLength, ostream, range);
                        }
                    } else {
                        // we should not get here
                        throw new IllegalStateException();
                    }
                }
            } else {
                response.setContentType("multipart/byteranges; boundary=" + mimeSeparation);
                if (serveContent) {
                    try {
                        response.setBufferSize(output);
                    } catch (IllegalStateException e) {
                        // Silent catch
                    }
                    if (ostream != null) {
                        copy(resource, contentLength, ostream, ranges, contentType);
                    } else {
                        // we should not get here
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }


    /*
     * Code borrowed heavily from Jasper's EncodingDetector
     */
    private static Charset processBom(InputStream is, boolean stripBom) throws IOException {
        // Java supported character sets do not use BOMs longer than 4 bytes
        byte[] bom = new byte[4];
        is.mark(bom.length);

        int count = is.read(bom);

        // BOMs are at least 2 bytes
        if (count < 2) {
            skip(is, 0, stripBom);
            return null;
        }

        // Look for two byte BOMs
        int b0 = bom[0] & 0xFF;
        int b1 = bom[1] & 0xFF;
        if (b0 == 0xFE && b1 == 0xFF) {
            skip(is, 2, stripBom);
            return StandardCharsets.UTF_16BE;
        }
        // Delay the UTF_16LE check if there are more that 2 bytes since it
        // overlaps with UTF-32LE.
        if (count == 2 && b0 == 0xFF && b1 == 0xFE) {
            skip(is, 2, stripBom);
            return StandardCharsets.UTF_16LE;
        }

        // Remaining BOMs are at least 3 bytes
        if (count < 3) {
            skip(is, 0, stripBom);
            return null;
        }

        // UTF-8 is only 3-byte BOM
        int b2 = bom[2] & 0xFF;
        if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
            skip(is, 3, stripBom);
            return StandardCharsets.UTF_8;
        }

        if (count < 4) {
            skip(is, 0, stripBom);
            return null;
        }

        // Look for 4-byte BOMs
        int b3 = bom[3] & 0xFF;
        if (b0 == 0x00 && b1 == 0x00 && b2 == 0xFE && b3 == 0xFF) {
            return Charset.forName("UTF-32BE");
        }
        if (b0 == 0xFF && b1 == 0xFE && b2 == 0x00 && b3 == 0x00) {
            return Charset.forName("UTF-32LE");
        }

        // Now we can check for UTF16-LE. There is an assumption here that we
        // won't see a UTF16-LE file with a BOM where the first real data is
        // 0x00 0x00
        if (b0 == 0xFF && b1 == 0xFE) {
            skip(is, 2, stripBom);
            return StandardCharsets.UTF_16LE;
        }

        skip(is, 0, stripBom);
        return null;
    }


    private static void skip(InputStream is, int skip, boolean stripBom) throws IOException {
        is.reset();
        if (stripBom) {
            while (skip-- > 0) {
                if (is.read() < 0) {
                    // Ignore since included
                    break;
                }
            }
        }
    }


    private static boolean isText(String contentType) {
        return contentType == null || contentType.startsWith("text") || contentType.endsWith("xml") ||
                contentType.contains("/javascript");
    }

    private static boolean validate(ContentRange range) {
        // bytes is the only range unit supported
        return (range != null) && ("bytes".equals(range.getUnits())) && (range.getStart() >= 0) &&
                (range.getEnd() >= 0) && (range.getStart() <= range.getEnd()) && (range.getLength() > 0);
    }

    private static boolean validate(Ranges.Entry range, long length) {
        long start = getStart(range, length);
        long end = getEnd(range, length);
        return (start >= 0) && (end >= 0) && (start <= end);
    }

    private static long getStart(Ranges.Entry range, long length) {
        long start = range.getStart();
        if (start == -1) {
            long end = range.getEnd();
            // If there is no start, then the start is based on the end
            if (end >= length) {
                return 0;
            } else {
                return length - end;
            }
        } else {
            return start;
        }
    }

    private static long getEnd(Ranges.Entry range, long length) {
        long end = range.getEnd();
        if (range.getStart() == -1 || end == -1 || end >= length) {
            return length - 1;
        } else {
            return end;
        }
    }

    private boolean pathEndsWithCompressedExtension(String path) {
        for (CompressionFormat format : compressionFormats) {
            if (path.endsWith(format.extension)) {
                return true;
            }
        }
        return false;
    }

    private List<PrecompressedResource> getAvailablePrecompressedResources(String path) {
        List<PrecompressedResource> ret = new ArrayList<>(compressionFormats.length);
        for (CompressionFormat format : compressionFormats) {
            WebResource precompressedResource = resources.getResource(path + format.extension);
            if (precompressedResource.exists() && precompressedResource.isFile()) {
                ret.add(new PrecompressedResource(precompressedResource, format));
            }
        }
        return ret;
    }

    /**
     * Match the client preferred encoding formats to the available precompressed resources.
     *
     * @param request                The servlet request we are processing
     * @param precompressedResources List of available precompressed resources.
     *
     * @return The best matching precompressed resource or null if no match was found.
     */
    private PrecompressedResource getBestPrecompressedResource(HttpServletRequest request,
            List<PrecompressedResource> precompressedResources) {
        Enumeration<String> headers = request.getHeaders("Accept-Encoding");
        PrecompressedResource bestResource = null;
        double bestResourceQuality = 0;
        int bestResourcePreference = Integer.MAX_VALUE;
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            for (String preference : header.split(",")) {
                double quality = 1;
                int qualityIdx = preference.indexOf(';');
                if (qualityIdx > 0) {
                    int equalsIdx = preference.indexOf('=', qualityIdx + 1);
                    if (equalsIdx == -1) {
                        continue;
                    }
                    quality = Double.parseDouble(preference.substring(equalsIdx + 1).trim());
                }
                if (quality >= bestResourceQuality) {
                    String encoding = preference;
                    if (qualityIdx > 0) {
                        encoding = encoding.substring(0, qualityIdx);
                    }
                    encoding = encoding.trim();
                    if ("identity".equals(encoding)) {
                        bestResource = null;
                        bestResourceQuality = quality;
                        bestResourcePreference = Integer.MAX_VALUE;
                        continue;
                    }
                    if ("*".equals(encoding)) {
                        bestResource = precompressedResources.get(0);
                        bestResourceQuality = quality;
                        bestResourcePreference = 0;
                        continue;
                    }
                    for (int i = 0; i < precompressedResources.size(); ++i) {
                        PrecompressedResource resource = precompressedResources.get(i);
                        if (encoding.equals(resource.format.encoding)) {
                            if (quality > bestResourceQuality || i < bestResourcePreference) {
                                bestResource = resource;
                                bestResourceQuality = quality;
                                bestResourcePreference = i;
                            }
                            break;
                        }
                    }
                }
            }
        }
        return bestResource;
    }

    private void doDirectoryRedirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        StringBuilder location = new StringBuilder(request.getRequestURI());
        location.append('/');
        if (request.getQueryString() != null) {
            location.append('?');
            location.append(request.getQueryString());
        }
        // Avoid protocol relative redirects
        while (location.length() > 1 && location.charAt(1) == '/') {
            location.deleteCharAt(0);
        }
        response.sendRedirect(response.encodeRedirectURL(location.toString()), directoryRedirectStatusCode);
    }

    /**
     * Parse the content-range header.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @return the partial content-range, {@code null} if the content-range header was invalid or {@code #IGNORE} if
     *             there is no header to process
     *
     * @throws IOException an IO error occurred
     */
    protected ContentRange parseContentRange(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // Retrieving the content-range header (if any is specified
        String contentRangeHeader = request.getHeader("Content-Range");

        if (contentRangeHeader == null) {
            return IGNORE;
        }

        if (!allowPartialPut) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        ContentRange contentRange = ContentRange.parse(new StringReader(contentRangeHeader));

        if (contentRange == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (!validate(contentRange)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        return contentRange;
    }


    /**
     * Parse the range header.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resource The resource
     *
     * @return a list of ranges, {@code null} if the range header was invalid or {@code #FULL} if the Range header
     *             should be ignored.
     *
     * @throws IOException an IO error occurred
     */
    protected Ranges parseRange(HttpServletRequest request, HttpServletResponse response, WebResource resource)
            throws IOException {

        // Range headers are only valid on GET requests. That implies they are
        // also valid on HEAD requests. This method is only called by doGet()
        // and doHead() so no need to check the request method.

        // Checking If-Range
        String headerValue = request.getHeader("If-Range");

        if (headerValue != null) {

            long headerValueTime = (-1L);
            try {
                headerValueTime = request.getDateHeader("If-Range");
            } catch (IllegalArgumentException e) {
                // Ignore
            }

            String eTag = generateETag(resource);
            long lastModified = resource.getLastModified();

            if (headerValueTime == (-1L)) {
                // If the ETag the client gave does not match the entity
                // etag, then the entire entity is returned.
                if (!eTag.equals(headerValue.trim())) {
                    return FULL;
                }
            } else {
                // If the timestamp of the entity the client got differs from
                // the last modification date of the entity, the entire entity
                // is returned.
                if (Math.abs(lastModified - headerValueTime) > 1000) {
                    return FULL;
                }
            }
        }

        long fileLength = resource.getContentLength();

        if (fileLength == 0) {
            // Range header makes no sense for a zero length resource. Tomcat
            // therefore opts to ignore it.
            return FULL;
        }

        // Retrieving the range header (if any is specified
        String rangeHeader = request.getHeader("Range");

        if (rangeHeader == null) {
            // No Range header is the same as ignoring any Range header
            return FULL;
        }

        Ranges ranges = Ranges.parse(new StringReader(rangeHeader));

        if (ranges == null) {
            // The Range header is present but not formatted correctly.
            // Could argue for a 400 response but 416 is more specific.
            // There is also the option to ignore the (invalid) Range header.
            // RFC7233#4.4 notes that many servers do ignore the Range header in
            // these circumstances but Tomcat has always returned a 416.
            response.addHeader("Content-Range", "bytes */" + fileLength);
            response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            return null;
        }

        // bytes is the only range unit supported (and I don't see the point
        // of adding new ones).
        if (!ranges.getUnits().equals("bytes")) {
            // RFC7233#3.1 Servers must ignore range units they don't understand
            return FULL;
        }

        for (Ranges.Entry range : ranges.getEntries()) {
            if (!validate(range, fileLength)) {
                response.addHeader("Content-Range", "bytes */" + fileLength);
                response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return null;
            }
        }

        return ranges;
    }


    /**
     * Decide which way to render. HTML or XML.
     *
     * @param request     The HttpServletRequest being served
     * @param contextPath The path
     * @param resource    The resource
     * @param encoding    The encoding to use to process the readme (if any)
     *
     * @return the input stream with the rendered output
     *
     * @throws IOException      an IO error occurred
     * @throws ServletException rendering error
     */
    protected InputStream render(HttpServletRequest request, String contextPath, WebResource resource, String encoding)
            throws IOException, ServletException {

        Source xsltSource = findXsltSource(resource);

        if (xsltSource == null) {
            return renderHtml(request, contextPath, resource, encoding);
        }
        return renderXml(request, contextPath, resource, xsltSource, encoding);
    }


    /**
     * Return an InputStream to an XML representation of the contents this directory.
     *
     * @param request     The HttpServletRequest being served
     * @param contextPath Context path to which our internal paths are relative
     * @param resource    The associated resource
     * @param xsltSource  The XSL stylesheet
     * @param encoding    The encoding to use to process the readme (if any)
     *
     * @return the XML data
     *
     * @throws IOException      an IO error occurred
     * @throws ServletException rendering error
     */
    protected InputStream renderXml(HttpServletRequest request, String contextPath, WebResource resource,
            Source xsltSource, String encoding) throws IOException, ServletException {

        StringBuilder sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<listing ");
        sb.append(" contextPath='");
        sb.append(contextPath);
        sb.append('\'');
        sb.append(" directory='");
        sb.append(resource.getName());
        sb.append("' ");
        sb.append(" hasParent='").append(!resource.getName().equals("/"));
        sb.append("'>");

        sb.append("<entries>");

        String[] entries = resources.list(resource.getWebappPath());

        // rewriteUrl(contextPath) is expensive. cache result for later reuse
        String rewrittenContextPath = rewriteUrl(contextPath);
        String directoryWebappPath = resource.getWebappPath();

        for (String entry : entries) {

            if (entry.equalsIgnoreCase("WEB-INF") || entry.equalsIgnoreCase("META-INF") ||
                    entry.equalsIgnoreCase(localXsltFile)) {
                continue;
            }

            if ((directoryWebappPath + entry).equals(contextXsltFile)) {
                continue;
            }

            WebResource childResource = resources.getResource(directoryWebappPath + entry);
            if (!childResource.exists()) {
                continue;
            }

            sb.append("<entry");
            sb.append(" type='").append(childResource.isDirectory() ? "dir" : "file").append('\'');
            sb.append(" urlPath='").append(rewrittenContextPath)
                    .append(Escape.xml(rewriteUrl(directoryWebappPath + entry)))
                    .append(childResource.isDirectory() ? "/" : "").append('\'');
            if (childResource.isFile()) {
                sb.append(" size='").append(renderSize(childResource.getContentLength())).append('\'');
            }
            sb.append(" date='").append(childResource.getLastModifiedHttp()).append('\'');
            sb.append(" longDate='").append(childResource.getLastModified()).append('\'');

            sb.append('>');
            sb.append(Escape.htmlElementContent(entry));
            if (childResource.isDirectory()) {
                sb.append('/');
            }
            sb.append("</entry>");
        }
        sb.append("</entries>");

        String readme = getReadme(resource, encoding);

        if (readme != null) {
            sb.append("<readme><![CDATA[");
            sb.append(readme);
            sb.append("]]></readme>");
        }

        sb.append("</listing>");

        // Prevent possible memory leak. Ensure Transformer and
        // TransformerFactory are not loaded from the web application.
        Thread currentThread = Thread.currentThread();
        ClassLoader original = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(DefaultServlet.class.getClassLoader());

            TransformerFactory tFactory = TransformerFactory.newInstance();
            Source xmlSource = new StreamSource(new StringReader(sb.toString()));
            Transformer transformer = tFactory.newTransformer(xsltSource);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            OutputStreamWriter osWriter = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
            StreamResult out = new StreamResult(osWriter);
            transformer.transform(xmlSource, out);
            osWriter.flush();
            return new ByteArrayInputStream(stream.toByteArray());
        } catch (TransformerException e) {
            throw new ServletException(sm.getString("defaultServlet.xslError"), e);
        } finally {
            currentThread.setContextClassLoader(original);
        }
    }

    /**
     * Return an InputStream to an HTML representation of the contents of this directory.
     *
     * @param request     The HttpServletRequest being served
     * @param contextPath Context path to which our internal paths are relative
     * @param resource    The associated resource
     * @param encoding    The encoding to use to process the readme (if any)
     *
     * @return the HTML data
     *
     * @throws IOException an IO error occurred
     */
    protected InputStream renderHtml(HttpServletRequest request, String contextPath, WebResource resource,
            String encoding) throws IOException {

        // Prepare a writer to a buffered area
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        OutputStreamWriter osWriter = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        PrintWriter writer = new PrintWriter(osWriter);

        StringBuilder sb = new StringBuilder();

        String directoryWebappPath = resource.getWebappPath();
        WebResource[] entries = resources.listResources(directoryWebappPath);

        // rewriteUrl(contextPath) is expensive. cache result for later reuse
        String rewrittenContextPath = rewriteUrl(contextPath);

        // Render the page header
        sb.append("<!doctype html><html>\r\n");
        /*
         * TODO Activate this as soon as we use smClient with the request locales
         * sb.append("<!doctype html><html lang=\""); sb.append(smClient.getLocale().getLanguage()).append("\">\r\n");
         */
        sb.append("<head>\r\n");
        sb.append("<title>");
        sb.append(sm.getString("directory.title", directoryWebappPath));
        sb.append("</title>\r\n");
        sb.append("<style>");
        sb.append(org.apache.catalina.util.TomcatCSS.TOMCAT_CSS);
        sb.append("</style> ");
        sb.append("</head>\r\n");
        sb.append("<body>");
        sb.append("<h1>");
        sb.append(sm.getString("directory.title", directoryWebappPath));

        // Render the link to our parent (if required)
        String parentDirectory = directoryWebappPath;
        if (parentDirectory.endsWith("/")) {
            parentDirectory = parentDirectory.substring(0, parentDirectory.length() - 1);
        }
        int slash = parentDirectory.lastIndexOf('/');
        if (slash >= 0) {
            String parent = directoryWebappPath.substring(0, slash);
            sb.append(" - <a href=\"");
            sb.append(rewrittenContextPath);
            if (parent.equals("")) {
                parent = "/";
            }
            sb.append(rewriteUrl(parent));
            if (!parent.endsWith("/")) {
                sb.append('/');
            }
            sb.append("\">");
            sb.append("<b>");
            sb.append(sm.getString("directory.parent", parent));
            sb.append("</b>");
            sb.append("</a>");
        }

        sb.append("</h1>");
        sb.append("<hr class=\"line\">");

        sb.append("<table width=\"100%\" cellspacing=\"0\"" + " cellpadding=\"5\" align=\"center\">\r\n");

        SortManager.Order order;
        if (sortListings && null != request) {
            order = sortManager.getOrder(request.getQueryString());
        } else {
            order = null;
        }
        // Render the column headings
        sb.append("<tr>\r\n");
        sb.append("<td align=\"left\"><font size=\"+1\"><strong>");
        if (sortListings && null != request) {
            sb.append("<a href=\"?C=N;O=");
            sb.append(getOrderChar(order, 'N'));
            sb.append("\">");
            sb.append(sm.getString("directory.filename"));
            sb.append("</a>");
        } else {
            sb.append(sm.getString("directory.filename"));
        }
        sb.append("</strong></font></td>\r\n");
        sb.append("<td align=\"center\"><font size=\"+1\"><strong>");
        if (sortListings && null != request) {
            sb.append("<a href=\"?C=S;O=");
            sb.append(getOrderChar(order, 'S'));
            sb.append("\">");
            sb.append(sm.getString("directory.size"));
            sb.append("</a>");
        } else {
            sb.append(sm.getString("directory.size"));
        }
        sb.append("</strong></font></td>\r\n");
        sb.append("<td align=\"right\"><font size=\"+1\"><strong>");
        if (sortListings && null != request) {
            sb.append("<a href=\"?C=M;O=");
            sb.append(getOrderChar(order, 'M'));
            sb.append("\">");
            sb.append(sm.getString("directory.lastModified"));
            sb.append("</a>");
        } else {
            sb.append(sm.getString("directory.lastModified"));
        }
        sb.append("</strong></font></td>\r\n");
        sb.append("</tr>");

        if (null != sortManager && null != request) {
            sortManager.sort(entries, request.getQueryString());
        }

        boolean shade = false;
        for (WebResource childResource : entries) {
            String filename = childResource.getName();
            if (filename.equalsIgnoreCase("WEB-INF") || filename.equalsIgnoreCase("META-INF")) {
                continue;
            }

            if (!childResource.exists()) {
                continue;
            }

            sb.append("<tr");
            if (shade) {
                sb.append(" bgcolor=\"#eeeeee\"");
            }
            sb.append(">\r\n");
            shade = !shade;

            sb.append("<td align=\"left\">&nbsp;&nbsp;\r\n");
            sb.append("<a href=\"");
            sb.append(rewrittenContextPath);
            sb.append(rewriteUrl(childResource.getWebappPath()));
            if (childResource.isDirectory()) {
                sb.append('/');
            }
            sb.append("\"><tt>");
            sb.append(Escape.htmlElementContent(filename));
            if (childResource.isDirectory()) {
                sb.append('/');
            }
            sb.append("</tt></a></td>\r\n");

            sb.append("<td align=\"right\"><tt>");
            if (childResource.isDirectory()) {
                sb.append("&nbsp;");
            } else {
                sb.append(renderSize(childResource.getContentLength()));
            }
            sb.append("</tt></td>\r\n");

            sb.append("<td align=\"right\"><tt>");
            sb.append(childResource.getLastModifiedHttp());
            sb.append("</tt></td>\r\n");

            sb.append("</tr>\r\n");
        }

        // Render the page footer
        sb.append("</table>\r\n");

        sb.append("<hr class=\"line\">");

        String readme = getReadme(resource, encoding);
        if (readme != null) {
            sb.append(readme);
            sb.append("<hr class=\"line\">");
        }

        if (showServerInfo) {
            sb.append("<h3>").append(ServerInfo.getServerInfo()).append("</h3>");
        }
        sb.append("</body>\r\n");
        sb.append("</html>\r\n");

        // Return an input stream to the underlying bytes
        writer.write(sb.toString());
        writer.flush();
        return new ByteArrayInputStream(stream.toByteArray());

    }


    /**
     * Render the specified file size (in bytes).
     *
     * @param size File size (in bytes)
     *
     * @return the formatted size
     */
    protected String renderSize(long size) {

        long leftSide = size / 1024;
        long rightSide = (size % 1024) / 103; // Makes 1 digit
        if ((leftSide == 0) && (rightSide == 0) && (size > 0)) {
            rightSide = 1;
        }

        return ("" + leftSide + "." + rightSide + " KiB");

    }


    /**
     * Get the readme file as a string.
     *
     * @param directory The directory to search
     * @param encoding  The readme encoding
     *
     * @return the readme for the specified directory
     */
    protected String getReadme(WebResource directory, String encoding) {

        if (readmeFile != null) {
            WebResource resource = resources.getResource(directory.getWebappPath() + readmeFile);
            if (resource.isFile()) {
                StringWriter buffer = new StringWriter();
                InputStreamReader reader = null;
                try (InputStream is = resource.getInputStream()) {
                    if (encoding != null) {
                        reader = new InputStreamReader(is, encoding);
                    } else {
                        reader = new InputStreamReader(is);
                    }
                    copyRange(reader, new PrintWriter(buffer));
                } catch (IOException e) {
                    log(sm.getString("defaultServlet.readerCloseFailed"), e);
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                        }
                    }
                }
                return buffer.toString();
            } else {
                if (debug > 10) {
                    log("readme '" + readmeFile + "' not found");
                }

                return null;
            }
        }

        return null;
    }


    /**
     * Return a Source for the xsl template (if possible).
     *
     * @param directory The directory to search
     *
     * @return the source for the specified directory
     *
     * @throws IOException an IO error occurred
     */
    protected Source findXsltSource(WebResource directory) throws IOException {

        if (localXsltFile != null) {
            WebResource resource = resources.getResource(directory.getWebappPath() + localXsltFile);
            if (resource.isFile()) {
                InputStream is = resource.getInputStream();
                if (is != null) {
                    return new StreamSource(is);
                }
            }
            if (debug > 10) {
                log("localXsltFile '" + localXsltFile + "' not found");
            }
        }

        if (contextXsltFile != null) {
            InputStream is = getServletContext().getResourceAsStream(contextXsltFile);
            if (is != null) {
                return new StreamSource(is);
            }

            if (debug > 10) {
                log("contextXsltFile '" + contextXsltFile + "' not found");
            }
        }

        /*
         * Open and read in file in one fell swoop to reduce chance chance of leaving handle open.
         */
        if (globalXsltFile != null) {
            File f = validateGlobalXsltFile();
            if (f != null) {
                long globalXsltFileSize = f.length();
                if (globalXsltFileSize > Integer.MAX_VALUE) {
                    log("globalXsltFile [" + f.getAbsolutePath() + "] is too big to buffer");
                } else {
                    try (FileInputStream fis = new FileInputStream(f)) {
                        byte b[] = new byte[(int) f.length()];
                        IOTools.readFully(fis, b);
                        return new StreamSource(new ByteArrayInputStream(b));
                    }
                }
            }
        }

        return null;
    }


    private File validateGlobalXsltFile() {
        Context context = resources.getContext();

        File baseConf = new File(context.getCatalinaBase(), "conf");
        File result = validateGlobalXsltFile(baseConf);
        if (result == null) {
            File homeConf = new File(context.getCatalinaHome(), "conf");
            if (!baseConf.equals(homeConf)) {
                result = validateGlobalXsltFile(homeConf);
            }
        }

        return result;
    }


    private File validateGlobalXsltFile(File base) {
        File candidate = new File(globalXsltFile);
        if (!candidate.isAbsolute()) {
            candidate = new File(base, globalXsltFile);
        }

        if (!candidate.isFile()) {
            return null;
        }

        // First check that the resulting path is under the provided base
        try {
            if (!candidate.getCanonicalFile().toPath().startsWith(base.getCanonicalFile().toPath())) {
                return null;
            }
        } catch (IOException ioe) {
            return null;
        }

        // Next check that an .xsl or .xslt file has been specified
        String nameLower = candidate.getName().toLowerCase(Locale.ENGLISH);
        if (!nameLower.endsWith(".xslt") && !nameLower.endsWith(".xsl")) {
            return null;
        }

        return candidate;
    }


    // -------------------------------------------------------- protected Methods

    /**
     * Check if sendfile can be used.
     *
     * @param request  The Servlet request
     * @param response The Servlet response
     * @param resource The resource
     * @param length   The length which will be written (will be used only if range is null)
     * @param range    The range that will be written
     *
     * @return <code>true</code> if sendfile should be used (writing is then delegated to the endpoint)
     */
    protected boolean checkSendfile(HttpServletRequest request, HttpServletResponse response, WebResource resource,
            long length, Ranges.Entry range) {
        String canonicalPath;
        if (sendfileSize > 0 && length > sendfileSize &&
                (Boolean.TRUE.equals(request.getAttribute(Globals.SENDFILE_SUPPORTED_ATTR))) &&
                (request.getClass().getName().equals("org.apache.catalina.connector.RequestFacade")) &&
                (response.getClass().getName().equals("org.apache.catalina.connector.ResponseFacade")) &&
                resource.isFile() && ((canonicalPath = resource.getCanonicalPath()) != null)) {
            request.setAttribute(Globals.SENDFILE_FILENAME_ATTR, canonicalPath);
            if (range == null) {
                request.setAttribute(Globals.SENDFILE_FILE_START_ATTR, Long.valueOf(0L));
                request.setAttribute(Globals.SENDFILE_FILE_END_ATTR, Long.valueOf(length));
            } else {
                request.setAttribute(Globals.SENDFILE_FILE_START_ATTR, Long.valueOf(getStart(range, length)));
                request.setAttribute(Globals.SENDFILE_FILE_END_ATTR, Long.valueOf(getEnd(range, length) + 1));
            }
            return true;
        }
        return false;
    }


    /**
     * Check if the if-match condition is satisfied.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resource The resource
     *
     * @return <code>true</code> if the resource meets the specified condition, and <code>false</code> if the condition
     *             is not satisfied, in which case request processing is stopped
     *
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfMatch(HttpServletRequest request, HttpServletResponse response, WebResource resource)
            throws IOException {

        String headerValue = request.getHeader("If-Match");
        if (headerValue != null) {

            boolean conditionSatisfied;

            if (!headerValue.equals("*")) {
                String resourceETag = generateETag(resource);
                if (resourceETag == null) {
                    conditionSatisfied = false;
                } else {
                    // RFC 7232 requires strong comparison for If-Match headers
                    Boolean matched = EntityTag.compareEntityTag(new StringReader(headerValue), false, resourceETag);
                    if (matched == null) {
                        if (debug > 10) {
                            log("DefaultServlet.checkIfMatch:  Invalid header value [" + headerValue + "]");
                        }
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                        return false;
                    }
                    conditionSatisfied = matched.booleanValue();
                }
            } else {
                conditionSatisfied = true;
            }

            if (!conditionSatisfied) {
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                return false;
            }
        }
        return true;
    }


    /**
     * Check if the if-modified-since condition is satisfied.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resource The resource
     *
     * @return <code>true</code> if the resource meets the specified condition, and <code>false</code> if the condition
     *             is not satisfied, in which case request processing is stopped
     */
    protected boolean checkIfModifiedSince(HttpServletRequest request, HttpServletResponse response,
            WebResource resource) {
        try {
            long headerValue = request.getDateHeader("If-Modified-Since");
            long lastModified = resource.getLastModified();
            if (headerValue != -1) {

                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((request.getHeader("If-None-Match") == null) && (lastModified < headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", generateETag(resource));

                    return false;
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;
    }


    /**
     * Check if the if-none-match condition is satisfied.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resource The resource
     *
     * @return <code>true</code> if the resource meets the specified condition, and <code>false</code> if the condition
     *             is not satisfied, in which case request processing is stopped
     *
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfNoneMatch(HttpServletRequest request, HttpServletResponse response, WebResource resource)
            throws IOException {

        String headerValue = request.getHeader("If-None-Match");
        if (headerValue != null) {

            boolean conditionSatisfied;

            String resourceETag = generateETag(resource);
            if (!headerValue.equals("*")) {
                if (resourceETag == null) {
                    conditionSatisfied = false;
                } else {
                    // RFC 7232 requires weak comparison for If-None-Match headers
                    Boolean matched = EntityTag.compareEntityTag(new StringReader(headerValue), true, resourceETag);
                    if (matched == null) {
                        if (debug > 10) {
                            log("DefaultServlet.checkIfNoneMatch:  Invalid header value [" + headerValue + "]");
                        }
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                        return false;
                    }
                    conditionSatisfied = matched.booleanValue();
                }
            } else {
                conditionSatisfied = true;
            }

            if (conditionSatisfied) {
                // For GET and HEAD, we should respond with
                // 304 Not Modified.
                // For every other method, 412 Precondition Failed is sent
                // back.
                if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", resourceETag);
                } else {
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                }
                return false;
            }
        }
        return true;
    }


    /**
     * Check if the if-unmodified-since condition is satisfied.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resource The resource
     *
     * @return <code>true</code> if the resource meets the specified condition, and <code>false</code> if the condition
     *             is not satisfied, in which case request processing is stopped
     *
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfUnmodifiedSince(HttpServletRequest request, HttpServletResponse response,
            WebResource resource) throws IOException {
        try {
            long lastModified = resource.getLastModified();
            long headerValue = request.getDateHeader("If-Unmodified-Since");
            if (headerValue != -1) {
                if (lastModified >= (headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;
    }


    /**
     * Provides the entity tag (the ETag header) for the given resource. Intended to be over-ridden by custom
     * DefaultServlet implementations that wish to use an alternative format for the entity tag.
     *
     * @param resource The resource for which an entity tag is required.
     *
     * @return The result of calling {@link WebResource#getETag()} on the given resource
     */
    protected String generateETag(WebResource resource) {
        return resource.getETag();
    }


    /**
     * Copy the contents of the specified input stream to the specified output stream, and ensure that both streams are
     * closed before returning (even in the face of an exception).
     *
     * @param is      The input stream to read the source resource from
     * @param ostream The output stream to write to
     *
     * @exception IOException if an input/output error occurs
     */
    protected void copy(InputStream is, ServletOutputStream ostream) throws IOException {

        IOException exception = null;
        InputStream istream = new BufferedInputStream(is, input);

        // Copy the input stream to the output stream
        exception = copyRange(istream, ostream);

        // Clean up the input stream
        istream.close();

        // Rethrow any exception that has occurred
        if (exception != null) {
            throw exception;
        }
    }


    /**
     * Copy the contents of the specified input stream to the specified output stream, and ensure that both streams are
     * closed before returning (even in the face of an exception).
     *
     * @param is       The input stream to read the source resource from
     * @param writer   The writer to write to
     * @param encoding The encoding to use when reading the source input stream
     *
     * @exception IOException if an input/output error occurs
     */
    protected void copy(InputStream is, PrintWriter writer, String encoding) throws IOException {
        IOException exception = null;

        Reader reader;
        if (encoding == null) {
            reader = new InputStreamReader(is);
        } else {
            reader = new InputStreamReader(is, encoding);
        }

        // Copy the input stream to the output stream
        exception = copyRange(reader, writer);

        // Clean up the reader
        reader.close();

        // Rethrow any exception that has occurred
        if (exception != null) {
            throw exception;
        }
    }


    /**
     * Copy the contents of the specified input stream to the specified output stream, and ensure that both streams are
     * closed before returning (even in the face of an exception).
     *
     * @param resource The source resource
     * @param length   the resource length
     * @param ostream  The output stream to write to
     * @param range    Range the client wanted to retrieve
     *
     * @exception IOException if an input/output error occurs
     */
    protected void copy(WebResource resource, long length, ServletOutputStream ostream, Ranges.Entry range)
            throws IOException {

        IOException exception = null;

        InputStream resourceInputStream = resource.getInputStream();
        InputStream istream = new BufferedInputStream(resourceInputStream, input);
        exception = copyRange(istream, ostream, getStart(range, length), getEnd(range, length));

        // Clean up the input stream
        istream.close();

        // Rethrow any exception that has occurred
        if (exception != null) {
            throw exception;
        }

    }


    /**
     * Copy the contents of the specified input stream to the specified output stream, and ensure that both streams are
     * closed before returning (even in the face of an exception).
     *
     * @param resource    The source resource
     * @param length      the resource length
     * @param ostream     The output stream to write to
     * @param ranges      Enumeration of the ranges the client wanted to retrieve
     * @param contentType Content type of the resource
     *
     * @exception IOException if an input/output error occurs
     */
    protected void copy(WebResource resource, long length, ServletOutputStream ostream, Ranges ranges,
            String contentType) throws IOException {

        IOException exception = null;

        for (Ranges.Entry range : ranges.getEntries()) {
            if (exception != null) {
                break;
            }
            InputStream resourceInputStream = resource.getInputStream();
            try (InputStream istream = new BufferedInputStream(resourceInputStream, input)) {

                // Writing MIME header.
                ostream.println();
                ostream.println("--" + mimeSeparation);
                if (contentType != null) {
                    ostream.println("Content-Type: " + contentType);
                }
                long start = getStart(range, length);
                long end = getEnd(range, length);
                ostream.println("Content-Range: bytes " + start + "-" + end + "/" + (end - start));
                ostream.println();

                // Printing content
                exception = copyRange(istream, ostream, start, end);
            }
        }

        ostream.println();
        ostream.print("--" + mimeSeparation + "--");

        // Rethrow any exception that has occurred
        if (exception != null) {
            throw exception;
        }

    }


    /**
     * Copy the contents of the specified input stream to the specified output stream, and ensure that both streams are
     * closed before returning (even in the face of an exception).
     *
     * @param istream The input stream to read from
     * @param ostream The output stream to write to
     *
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(InputStream istream, ServletOutputStream ostream) {

        // Copy the input stream to the output stream
        IOException exception = null;
        byte buffer[] = new byte[input];
        int len = buffer.length;
        while (true) {
            try {
                len = istream.read(buffer);
                if (len == -1) {
                    break;
                }
                ostream.write(buffer, 0, len);
            } catch (IOException e) {
                exception = e;
                len = -1;
                break;
            }
        }
        return exception;

    }


    /**
     * Copy the contents of the specified input stream to the specified output stream, and ensure that both streams are
     * closed before returning (even in the face of an exception).
     *
     * @param reader The reader to read from
     * @param writer The writer to write to
     *
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(Reader reader, PrintWriter writer) {

        // Copy the input stream to the output stream
        IOException exception = null;
        char buffer[] = new char[input];
        int len = buffer.length;
        while (true) {
            try {
                len = reader.read(buffer);
                if (len == -1) {
                    break;
                }
                writer.write(buffer, 0, len);
            } catch (IOException e) {
                exception = e;
                len = -1;
                break;
            }
        }
        return exception;

    }


    /**
     * Copy the contents of the specified input stream to the specified output stream, and ensure that both streams are
     * closed before returning (even in the face of an exception).
     *
     * @param istream The input stream to read from
     * @param ostream The output stream to write to
     * @param start   Start of the range which will be copied
     * @param end     End of the range which will be copied
     *
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(InputStream istream, ServletOutputStream ostream, long start, long end) {

        if (debug > 10) {
            log("Serving bytes:" + start + "-" + end);
        }

        long skipped = 0;
        try {
            skipped = istream.skip(start);
        } catch (IOException e) {
            return e;
        }
        if (skipped < start) {
            return new IOException(sm.getString("defaultServlet.skipfail", Long.valueOf(skipped), Long.valueOf(start)));
        }

        IOException exception = null;
        long bytesToRead = end - start + 1;

        byte buffer[] = new byte[input];
        int len = buffer.length;
        while ((bytesToRead > 0) && (len >= buffer.length)) {
            try {
                len = istream.read(buffer);
                if (bytesToRead >= len) {
                    ostream.write(buffer, 0, len);
                    bytesToRead -= len;
                } else {
                    ostream.write(buffer, 0, (int) bytesToRead);
                    bytesToRead = 0;
                }
            } catch (IOException e) {
                exception = e;
                len = -1;
            }
            if (len < buffer.length) {
                break;
            }
        }

        return exception;

    }


    protected static class CompressionFormat implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String extension;
        public final String encoding;

        public CompressionFormat(String extension, String encoding) {
            this.extension = extension;
            this.encoding = encoding;
        }
    }


    private static class PrecompressedResource {
        public final WebResource resource;
        public final CompressionFormat format;

        private PrecompressedResource(WebResource resource, CompressionFormat format) {
            this.resource = resource;
            this.format = format;
        }
    }


    /**
     * Gets the ordering character to be used for a particular column.
     *
     * @param order  The order that is currently being applied
     * @param column The column that will be rendered.
     *
     * @return Either 'A' or 'D', to indicate "ascending" or "descending" sort order.
     */
    private char getOrderChar(SortManager.Order order, char column) {
        if (column == order.column) {
            if (order.ascending) {
                return 'D';
            } else {
                return 'A';
            }
        } else {
            return 'D';
        }
    }


    /**
     * A class encapsulating the sorting of resources.
     */
    private static class SortManager {
        /**
         * The default sort.
         */
        protected Comparator<WebResource> defaultResourceComparator;

        /**
         * Comparator to use when sorting resources by name.
         */
        protected Comparator<WebResource> resourceNameComparator;

        /**
         * Comparator to use when sorting files by name, ascending (reverse).
         */
        protected Comparator<WebResource> resourceNameComparatorAsc;

        /**
         * Comparator to use when sorting resources by size.
         */
        protected Comparator<WebResource> resourceSizeComparator;

        /**
         * Comparator to use when sorting files by size, ascending (reverse).
         */
        protected Comparator<WebResource> resourceSizeComparatorAsc;

        /**
         * Comparator to use when sorting resources by last-modified date.
         */
        protected Comparator<WebResource> resourceLastModifiedComparator;

        /**
         * Comparator to use when sorting files by last-modified date, ascending (reverse).
         */
        protected Comparator<WebResource> resourceLastModifiedComparatorAsc;

        SortManager(boolean directoriesFirst) {
            resourceNameComparator = Comparator.comparing(WebResource::getName);
            resourceNameComparatorAsc = resourceNameComparator.reversed();
            resourceSizeComparator =
                    Comparator.comparing(WebResource::getContentLength).thenComparing(resourceNameComparator);
            resourceSizeComparatorAsc = resourceSizeComparator.reversed();
            resourceLastModifiedComparator =
                    Comparator.comparing(WebResource::getLastModified).thenComparing(resourceNameComparator);
            resourceLastModifiedComparatorAsc = resourceLastModifiedComparator.reversed();

            if (directoriesFirst) {
                Comparator<WebResource> dirsFirst = comparingTrueFirst(WebResource::isDirectory);
                resourceNameComparator = dirsFirst.thenComparing(resourceNameComparator);
                resourceNameComparatorAsc = dirsFirst.thenComparing(resourceNameComparatorAsc);
                resourceSizeComparator = dirsFirst.thenComparing(resourceSizeComparator);
                resourceSizeComparatorAsc = dirsFirst.thenComparing(resourceSizeComparatorAsc);
                resourceLastModifiedComparator = dirsFirst.thenComparing(resourceLastModifiedComparator);
                resourceLastModifiedComparatorAsc = dirsFirst.thenComparing(resourceLastModifiedComparatorAsc);
            }

            defaultResourceComparator = resourceNameComparator;
        }

        /**
         * Sorts an array of resources according to an ordering string.
         *
         * @param resources The array to sort.
         * @param order     The ordering string.
         *
         * @see #getOrder(String)
         */
        public void sort(WebResource[] resources, String order) {
            Comparator<WebResource> comparator = getComparator(order);

            if (null != comparator) {
                Arrays.sort(resources, comparator);
            }
        }

        public Comparator<WebResource> getComparator(String order) {
            return getComparator(getOrder(order));
        }

        public Comparator<WebResource> getComparator(Order order) {
            if (null == order) {
                return defaultResourceComparator;
            }

            if ('N' == order.column) {
                if (order.ascending) {
                    return resourceNameComparatorAsc;
                } else {
                    return resourceNameComparator;
                }
            }

            if ('S' == order.column) {
                if (order.ascending) {
                    return resourceSizeComparatorAsc;
                } else {
                    return resourceSizeComparator;
                }
            }

            if ('M' == order.column) {
                if (order.ascending) {
                    return resourceLastModifiedComparatorAsc;
                } else {
                    return resourceLastModifiedComparator;
                }
            }

            return defaultResourceComparator;
        }

        /**
         * Gets the Order to apply given an ordering-string. This ordering-string matches a subset of the
         * ordering-strings supported by <a href="https://httpd.apache.org/docs/2.4/mod/mod_autoindex.html#query">Apache
         * httpd</a>.
         *
         * @param order The ordering-string provided by the client.
         *
         * @return An Order specifying the column and ascending/descending to be applied to resources.
         */
        public Order getOrder(String order) {
            if (null == order || 0 == order.trim().length()) {
                return Order.DEFAULT;
            }

            String[] options = order.split(";");

            if (0 == options.length) {
                return Order.DEFAULT;
            }

            char column = '\0';
            boolean ascending = false;

            for (String option : options) {
                option = option.trim();

                if (2 < option.length()) {
                    char opt = option.charAt(0);
                    if ('C' == opt) {
                        column = option.charAt(2);
                    } else if ('O' == opt) {
                        ascending = ('A' == option.charAt(2));
                    }
                }
            }

            if ('N' == column) {
                if (ascending) {
                    return Order.NAME_ASC;
                } else {
                    return Order.NAME;
                }
            }

            if ('S' == column) {
                if (ascending) {
                    return Order.SIZE_ASC;
                } else {
                    return Order.SIZE;
                }
            }

            if ('M' == column) {
                if (ascending) {
                    return Order.LAST_MODIFIED_ASC;
                } else {
                    return Order.LAST_MODIFIED;
                }
            }

            return Order.DEFAULT;
        }

        public static class Order {
            final char column;
            final boolean ascending;

            Order(char column, boolean ascending) {
                this.column = column;
                this.ascending = ascending;
            }

            public static final Order NAME = new Order('N', false);
            public static final Order NAME_ASC = new Order('N', true);
            public static final Order SIZE = new Order('S', false);
            public static final Order SIZE_ASC = new Order('S', true);
            public static final Order LAST_MODIFIED = new Order('M', false);
            public static final Order LAST_MODIFIED_ASC = new Order('M', true);

            public static final Order DEFAULT = NAME;
        }
    }


    private static Comparator<WebResource> comparingTrueFirst(Function<WebResource,Boolean> keyExtractor) {
        return (s1, s2) -> {
            Boolean r1 = keyExtractor.apply(s1);
            Boolean r2 = keyExtractor.apply(s2);
            if (r1.booleanValue()) {
                if (r2.booleanValue()) {
                    return 0;
                } else {
                    return -1; // r1 (property is true) first
                }
            } else if (r2.booleanValue()) {
                return 1; // r2 (property is true) first
            } else {
                return 0;
            }
        };
    }


    enum BomConfig {
        /**
         * BoM is stripped if present and any BoM found used to determine the encoding used to read the resource.
         */
        TRUE("true", true, true),
        /**
         * BoM is stripped if present but the configured file encoding is used to read the resource.
         */
        FALSE("false", true, false),
        /**
         * BoM is not stripped and the configured file encoding is used to read the resource.
         */
        PASS_THROUGH("pass-through", false, false);

        final String configurationValue;
        final boolean stripBom;
        final boolean useBomEncoding;

        BomConfig(String configurationValue, boolean stripBom, boolean useBomEncoding) {
            this.configurationValue = configurationValue;
            this.stripBom = stripBom;
            this.useBomEncoding = useBomEncoding;
        }
    }
}
