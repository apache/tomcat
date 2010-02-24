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


package org.apache.tomcat.servlets.file;


import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.lite.util.CopyUtils;
import org.apache.tomcat.lite.util.Dir2Html;
import org.apache.tomcat.lite.util.Range;
import org.apache.tomcat.lite.util.URLEncoder;

/**
 * The default resource-serving servlet for most web applications,
 * used to serve static resources such as HTML pages and images.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class DefaultServlet  extends HttpServlet {


    // ----------------------------------------------------- Instance Variables

    /**
     * Should we generate directory listings?
     */
    protected boolean listings = true;

    /**
     * Array containing the safe characters set.
     */
    protected static URLEncoder urlEncoder;

    /**
     * Allow a readme file to be included.
     */
    protected String readmeFile = null;

    // TODO: find a better default
    /**
     * The input buffer size to use when serving resources.
     */
    protected static int input = 2048;

    /**
     * The output buffer size to use when serving resources.
     */
    protected int output = 2048;

    /**
     * File encoding to be used when reading static files. If none is specified
     * the platform default is used.
     */
    protected String fileEncoding = null;

    static ThreadLocal formatTL = new ThreadLocal();
        
    Dir2Html dir2Html = new Dir2Html();
    /**
     * Full range marker.
     */
    protected static ArrayList FULL = new ArrayList();

    // Context base dir
    protected File basePath;
    protected String basePathName;
    
    // ----------------------------------------------------- Static Initializer


    /**
     * GMT timezone - all HTTP dates are on GMT
     */
    static {
        urlEncoder = new URLEncoder();
        urlEncoder.addSafeCharacter('-');
        urlEncoder.addSafeCharacter('_');
        urlEncoder.addSafeCharacter('.');
        urlEncoder.addSafeCharacter('*');
        urlEncoder.addSafeCharacter('/');
    }


    /**
     * MIME multipart separation string
     */
    protected static final String mimeSeparation = "TOMCAT_MIME_BOUNDARY";


    // --------------------------------------------------------- Public Methods
    /**
     * Finalize this servlet.
     */
    public void destroy() {
    }
    
    /**
     * Initialize this servlet.
     */
    public void init() throws ServletException {

        String realPath = getServletContext().getRealPath("/");
        basePath = new File(realPath);
        try {
            basePathName = basePath.getCanonicalPath();
        } catch (IOException e) {
            basePathName = basePath.getAbsolutePath();
        }
        log("Init default serviet, base: " + basePathName);
        
        // Set our properties from the initialization parameters
        String value = null;
        try {
            value = getServletConfig().getInitParameter("input");
            input = Integer.parseInt(value);
        } catch (Throwable t) {
            ;
        }
        try {
            value = getServletConfig().getInitParameter("listings");
            if (value != null )
                listings = (new Boolean(value)).booleanValue();
        } catch (Throwable t) {
            ;
        }
        try {
            value = getServletConfig().getInitParameter("output");
            output = Integer.parseInt(value);
        } catch (Throwable t) {
            ;
        }
        fileEncoding = getServletConfig().getInitParameter("fileEncoding");
        
        readmeFile = getServletConfig().getInitParameter("readmeFile");

        
        // Sanity check on the specified buffer sizes
        if (input < 256)
            input = 256;
        if (output < 256)
            output = 256;
    }

    public void setBasePath(String s) {
        this.basePathName = s;
        this.basePath = new File(s);
    }
    
    public String getBasePath() {
        return basePathName;
    }
    
    public void setInput(int i) {
        this.input = i;
    }
    
    public void setListings(boolean b) {
        this.listings = b;
    }

    public void setReadme(String s) {
        readmeFile = s;
    }

    public void setFileEncoding(String s) {
        fileEncoding = s;
    }
    
    
    public void loadDefaultMime() throws IOException {
        File mimeF = new File("/etc/mime.types");
        boolean loaded =false;
        if (!mimeF.exists()) {
            loaded =true;
            loadMimeFile( new FileInputStream(mimeF));
        }
        mimeF = new File("/etc/httpd/mime.types");
        if (mimeF.exists()) {
            loaded =true;
            loadMimeFile( new FileInputStream(mimeF));
        }
        if (!loaded) {
            throw new IOException("mime.types not found");
        }
    }

    public void loadMimeFile(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line = null;
        while((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) continue;
            if (line.startsWith("#")) continue;
            String[] parts = line.split("\\w+");
            String type = parts[0];
            for (int i=1; i < parts.length; i++) {
                String ext = parts[i];
                if (!ext.equals("")) {
                    addMimeType(type, ext);
                    System.err.println(type + " = " + ext);
                } else {
                    System.err.println("XXX " + ext);
                }
            }
        }
        
    }
    
    private void addMimeType(String type, String ext) {
        
    }
    // ------------------------------------------------------ Protected Methods

    public static final String INCLUDE_REQUEST_URI_ATTR =
        "javax.servlet.include.request_uri";
    public static final String INCLUDE_SERVLET_PATH_ATTR =
        "javax.servlet.include.servlet_path";
    public static final String INCLUDE_PATH_INFO_ATTR =
        "javax.servlet.include.path_info";
    public static final String INCLUDE_CONTEXT_PATH_ATTR =
        "javax.servlet.include.context_path";

    
    /**
     * Return the relative path associated with this servlet. 
     * Multiple sources are used - include attribute, servlet path, etc
     *
     * @param request The servlet request we are processing
     */
    public static String getRelativePath(HttpServletRequest request) {

        // Are we being processed by a RequestDispatcher.include()?
        if (request.getAttribute(INCLUDE_REQUEST_URI_ATTR) != null) {
            String result = (String) request.getAttribute(
                                            INCLUDE_PATH_INFO_ATTR);
            if (result == null)
                result = (String) request.getAttribute(
                                            INCLUDE_SERVLET_PATH_ATTR);
            if ((result == null) || (result.equals("")))
                result = "/";
            return (result);
        }

        // No, extract the desired path directly from the request
        // For 'default' servlet, the path info contains the path
        // if this is mapped to serve a subset of the files - we
        // need both
        
        String result = request.getPathInfo();
        if (result == null) {
            result = request.getServletPath();
        } else {
            result = request.getServletPath() + result;
        }
        if ((result == null) || (result.equals(""))) {
            result = "/";
        }
        return (result);
    }

    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
        throws IOException, ServletException {
        serveResource(request, response, true);

    }


    protected void doHead(HttpServletRequest request,
                          HttpServletResponse response)
        throws IOException, ServletException {
        serveResource(request, response, false);

    }


    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
        throws IOException, ServletException {
      // TODO: not allowed ?
      doGet(request, response);
    }




    /**
     * Check if the conditions specified in the optional If headers are
     * satisfied.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceAttributes The resource information
     * @return boolean true if the resource meets all the specified conditions,
     * and false if any of the conditions is not satisfied, in which case
     * request processing is stopped
     */
    protected boolean checkIfHeaders(HttpServletRequest request,
                                     HttpServletResponse response,
                                     File resourceAttributes)
        throws IOException {

        return checkIfMatch(request, response, resourceAttributes)
            && checkIfModifiedSince(request, response, resourceAttributes)
            && checkIfNoneMatch(request, response, resourceAttributes)
            && checkIfUnmodifiedSince(request, response, resourceAttributes);

    }


    /**
     * Get the ETag associated with a file.
     *
     * @param resourceAttributes The resource information
     */
    protected String getETag(File resourceAttributes) {
        return "W/\"" + resourceAttributes.length() + "-"
                + resourceAttributes.lastModified() + "\"";
    }

    LRUFileCache fileCache;

    public void setFileCacheSize(int size) {
        if (fileCache == null) {
            fileCache = new LRUFileCache();
        }
        fileCache.cacheSize = size;
    }
    
    public int getFileCacheSize() {
        if (fileCache ==null ) return 0;
        return fileCache.cacheSize;
    }
    
    static class LRUFileCache extends LinkedHashMap {
        int cacheSize;
        public LRUFileCache() {
        }
//        protected boolean removeEldestEntity(Map.Entry eldest) {
//            return size() > cacheSize;
//        }
    }
    
    public void renderDir(HttpServletRequest request, 
            HttpServletResponse response, 
            File resFile,
            String fileEncoding,
            boolean content,
            String relativePath) throws IOException {
        
        String contentType = "text/html;charset=" + fileEncoding;

        ServletOutputStream ostream = null;
        PrintWriter writer = null;
        
        if (content) {
            // Trying to retrieve the servlet output stream
            try {
                ostream = response.getOutputStream();
            } catch (IllegalStateException e) {
                // If it fails, we try to get a Writer instead if we're
                // trying to serve a text file
                if ( (contentType == null)
                     || (contentType.startsWith("text")) ) {
                    writer = response.getWriter();
                } else {
                    throw e;
                }
            }

        }

        // Set the appropriate output headers
        response.setContentType(contentType);
        
        InputStream renderResult = null;

        if (content) {
            // Serve the directory browser
            renderResult =
                dir2Html.render(request.getContextPath(), resFile, relativePath);
        }


        // Copy the input stream to our output stream (if requested)
        if (content) {
            try {
                response.setBufferSize(output);
            } catch (IllegalStateException e) {
                // Silent catch
            }
            if (ostream != null) {
                CopyUtils.copy(renderResult, ostream);
            } else {
                CopyUtils.copy(renderResult, writer, fileEncoding);
            }
        }
        
            
    }
    

    /**
     * Serve the specified resource, optionally including the data content.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param content Should the content be included?
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void serveResource(HttpServletRequest request,
                                 HttpServletResponse response,
                                 boolean content)
        throws IOException, ServletException {

        // Identify the requested resource path - checks include attributes
        String path = getRelativePath(request);
        
        // TODO: Check the file cache to avoid a bunch of FS accesses.
        
        File resFile = new File(basePath, path);

        if (!resFile.exists()) {
            send404(request, response);
            return;
        }

        boolean isDir = resFile.isDirectory();
        
        if (isDir) {
            getServletContext();
            // Skip directory listings if we have been configured to
            // suppress them
            if (!listings) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                   request.getRequestURI());
                return;
            }
            renderDir(request, response, resFile, fileEncoding, content,
                    path);
            
            return;
        }
        
        // If the resource is not a collection, and the resource path
        // ends with "/" or "\", return NOT FOUND
        if (path.endsWith("/") || (path.endsWith("\\"))) {
            // Check if we're included so we can return the appropriate 
            // missing resource name in the error
            String requestUri = (String) request.getAttribute(
                    INCLUDE_REQUEST_URI_ATTR);
            if (requestUri == null) {
                requestUri = request.getRequestURI();
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    requestUri);
            return;
        }

        // Check if the conditions specified in the optional If headers are
        // satisfied.

        // Checking If headers. The method will generate the
        boolean included =
            (request.getAttribute(INCLUDE_CONTEXT_PATH_ATTR) != null);
        if (!included
                && !checkIfHeaders(request, response, resFile)) {
            return;
        }


        // Find content type.
        String contentType = getServletContext().getMimeType(resFile.getName());

        long contentLength = -1L;

        // ETag header
        response.setHeader("ETag", getETag(resFile));

        // TODO: remove the sync, optimize - it's from ResourceAttribute
        String lastModifiedHttp = lastModifiedHttp(resFile);

        // Last-Modified header
        response.setHeader("Last-Modified", lastModifiedHttp);

        // Get content length
        contentLength = resFile.length();
        // Special case for zero length files, which would cause a
        // (silent) ISE when setting the output buffer size
        if (contentLength == 0L) {
            content = false;
        }

        ServletOutputStream ostream = null;
        PrintWriter writer = null;

        if (content) {

            // Trying to retrieve the servlet output stream

            try {
                ostream = response.getOutputStream();
            } catch (IllegalStateException e) {
                // If it fails, we try to get a Writer instead if we're
                // trying to serve a text file
                if ( (contentType == null)
                     || (contentType.startsWith("text")) ) {
                    writer = response.getWriter();
                } else {
                    throw e;
                }
            }
        }

        // Parse range specifier

        ArrayList ranges = parseRange(request, response, resFile);

        if ( ( ((ranges == null) || (ranges.isEmpty()))
                && (request.getHeader("Range") == null) )
                || (ranges == FULL) ) {

            processSingleRange(response, content, resFile, contentType, 
                    ostream, writer, ranges, contentLength);
        } else {

            if ((ranges == null) || (ranges.isEmpty()))
                return;

            // Partial content response.

            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            if (ranges.size() == 1) {

                processSingleRange(response, content, resFile, contentType, 
                        ostream, writer, ranges, contentLength);

            } else {

                processMultiRange(response, content, resFile, contentType, 
                                  ostream, writer, ranges);

            }

        }

    }


    private void processMultiRange(HttpServletResponse response, 
                                   boolean content, 
                                   File resFile, 
                                   String contentType, 
                                   ServletOutputStream ostream, 
                                   PrintWriter writer, 
                                   ArrayList ranges) throws IOException {
        response.setContentType("multipart/byteranges; boundary="
                                + mimeSeparation);

        if (content) {
            try {
                response.setBufferSize(output);
            } catch (IllegalStateException e) {
                // Silent catch
            }
            if (ostream != null) {
                copyRanges(resFile, ostream, ranges.iterator(),
                           contentType);
            } else {
                copyRanges(resFile, writer, ranges.iterator(),
                           contentType);
            }
        }
    }


    private void processSingleRange(HttpServletResponse response, boolean content, File resFile, String contentType, ServletOutputStream ostream, PrintWriter writer, ArrayList ranges,
            long contentLength) throws IOException {
        Range range = null;
        long length = contentLength;
        
        if (ranges != null && ranges.size() > 0) {
            range = (Range) ranges.get(0);
            response.addHeader("Content-Range", "bytes "
                    + range.start
                    + "-" + range.end + "/"
                    + range.length);
            length = range.end - range.start + 1;
        }
        if (length < Integer.MAX_VALUE) {
            response.setContentLength((int) length);
        } else {
            // Set the content-length as String to be able to use a long
            response.setHeader("content-length", "" + length);
        }

        if (contentType != null) {
            response.setContentType(contentType);
        }

        if (content) {
            try {
                response.setBufferSize(output);
            } catch (IllegalStateException e) {
                // Silent catch
            }
            InputStream is = null;
            try {
                is = new FileInputStream(resFile);
                if (ostream != null) {
                    if (range == null) {
                        CopyUtils.copy(is, ostream);
                    } else {
                        CopyUtils.copyRange(is, ostream, range.start, range.end);
                    }
                } else {Reader reader;
                    if (fileEncoding == null) {
                        reader = new InputStreamReader(is);
                    } else {
                        reader = new InputStreamReader(is,
                                                       fileEncoding);
                    }
                    if (range == null) {
                        CopyUtils.copyRange(reader, writer);                        
                    } else {
                        CopyUtils.copyRange(reader, writer, range.start, range.end);
                    }
                }
            } finally {
                is.close();
            }
        }
    }


    public static String lastModifiedHttp(File resFile) {
        String lastModifiedHttp = null;
        SimpleDateFormat format = (SimpleDateFormat)formatTL.get();
        if (format == null) {
            format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", 
                    Locale.US);
            formatTL.set(format);
        }
        lastModifiedHttp = format.format(new Date(resFile.lastModified()));
        return lastModifiedHttp;
    }


    protected static void send404(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Check if we're included so we can return the appropriate 
        // missing resource name in the error
        String requestUri = (String) request.getAttribute(
                                        INCLUDE_REQUEST_URI_ATTR);
        if (requestUri == null) {
            requestUri = request.getRequestURI();
        } else {
            // We're included, and the response.sendError() below is going
            // to be ignored by the resource that is including us.
            // Therefore, the only way we can let the including resource
            // know is by including warning message in response
            response.getWriter().write("Not found");
            // skip the URI - just to be safe.
        }

        response.sendError(HttpServletResponse.SC_NOT_FOUND,
                           requestUri);
        return;
    }



    /**
     * Parse the range header.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @return Vector of ranges
     */
    protected ArrayList parseRange(HttpServletRequest request,
                                   HttpServletResponse response,
                                   File resourceAttributes)
            throws IOException {
        // if it has an IfRange and the file is newer, retur FULL 
        ArrayList result = processIfRange(request, resourceAttributes);
        if ( result != null ) return result;

        long fileLength = resourceAttributes.length();
        if (fileLength == 0)
            return null;

        // Retrieving the range header (if any is specified
        String rangeHeader = request.getHeader("Range");

        if (rangeHeader == null)
            return null;
        // bytes is the only range unit supported (and I don't see the point
        // of adding new ones).
        if (!rangeHeader.startsWith("bytes")) {
            return sendRangeNotSatisfiable(response, fileLength);
        }

        rangeHeader = rangeHeader.substring(6);

        // Vector which will contain all the ranges which are successfully
        // parsed.
        result = Range.parseRanges(fileLength, rangeHeader);
        if (result == null) {
            sendRangeNotSatisfiable(response, fileLength);
        }
        return result;
    }


    private ArrayList sendRangeNotSatisfiable(HttpServletResponse response, 
                                              long fileLength) 
            throws IOException {
        response.addHeader("Content-Range", "bytes */" + fileLength);
        response.sendError
            (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        return null;
    }



    private ArrayList processIfRange(HttpServletRequest request, 
                                     File resourceAttributes) {
        // Checking If-Range
        String headerValue = request.getHeader("If-Range");

        if (headerValue != null) {

            long headerValueTime = (-1L);
            try {
                headerValueTime = request.getDateHeader("If-Range");
            } catch (Exception e) {
                ;
            }

            String eTag = getETag(resourceAttributes);
            long lastModified = resourceAttributes.lastModified();

            if (headerValueTime == (-1L)) {

                // If the ETag the client gave does not match the entity
                // etag, then the entire entity is returned.
                if (!eTag.equals(headerValue.trim()))
                    return FULL;

            } else {

                // If the timestamp of the entity the client got is older than
                // the last modification date of the entity, the entire entity
                // is returned.
                if (lastModified > (headerValueTime + 1000))
                    return FULL;

            }

        }
        return null;
    }


    // -------------------------------------------------------- protected Methods

    
    /**
     * Check if the if-match condition is satisfied.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceInfo File object
     * @return boolean true if the resource meets the specified condition,
     * and false if the condition is not satisfied, in which case request
     * processing is stopped
     */
    protected boolean checkIfMatch(HttpServletRequest request,
                                 HttpServletResponse response,
                                 File resourceAttributes)
        throws IOException {

        String eTag = getETag(resourceAttributes);
        String headerValue = request.getHeader("If-Match");
        if (headerValue != null) {
            if (headerValue.indexOf('*') == -1) {

                StringTokenizer commaTokenizer = new StringTokenizer
                    (headerValue, ",");
                boolean conditionSatisfied = false;

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

                // If none of the given ETags match, 412 Precodition failed is
                // sent back
                if (!conditionSatisfied) {
                    response.sendError
                        (HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }

            }
        }
        return true;

    }


    /**
     * Check if the if-modified-since condition is satisfied.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceInfo File object
     * @return boolean true if the resource meets the specified condition,
     * and false if the condition is not satisfied, in which case request
     * processing is stopped
     */
    protected boolean checkIfModifiedSince(HttpServletRequest request,
                                         HttpServletResponse response,
                                         File resourceAttributes)
        throws IOException {
        try {
            long headerValue = request.getDateHeader("If-Modified-Since");
            long lastModified = resourceAttributes.lastModified();
            if (headerValue != -1) {

                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((request.getHeader("If-None-Match") == null)
                    && (lastModified <= headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return false;
                }
            }
        } catch(IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;

    }


    /**
     * Check if the if-none-match condition is satisfied.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceInfo File object
     * @return boolean true if the resource meets the specified condition,
     * and false if the condition is not satisfied, in which case request
     * processing is stopped
     */
    protected boolean checkIfNoneMatch(HttpServletRequest request,
                                     HttpServletResponse response,
                                     File resourceAttributes)
        throws IOException {

        String eTag = getETag(resourceAttributes);
        String headerValue = request.getHeader("If-None-Match");
        if (headerValue != null) {

            boolean conditionSatisfied = false;

            if (!headerValue.equals("*")) {

                StringTokenizer commaTokenizer =
                    new StringTokenizer(headerValue, ",");

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

            } else {
                conditionSatisfied = true;
            }

            if (conditionSatisfied) {

                // For GET and HEAD, we should respond with
                // 304 Not Modified.
                // For every other method, 412 Precondition Failed is sent
                // back.
                if ( ("GET".equals(request.getMethod()))
                     || ("HEAD".equals(request.getMethod())) ) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return false;
                } else {
                    response.sendError
                        (HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        }
        return true;

    }


    /**
     * Check if the if-unmodified-since condition is satisfied.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceInfo File object
     * @return boolean true if the resource meets the specified condition,
     * and false if the condition is not satisfied, in which case request
     * processing is stopped
     */
    protected boolean checkIfUnmodifiedSince(HttpServletRequest request,
                                           HttpServletResponse response,
                                           File resourceAttributes)
        throws IOException {
        try {
            long lastModified = resourceAttributes.lastModified();
            long headerValue = request.getDateHeader("If-Unmodified-Since");
            if (headerValue != -1) {
                if ( lastModified > (headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        } catch(IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;

    }



    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param resourceInfo The ResourceInfo object
     * @param ostream The output stream to write to
     * @param ranges Enumeration of the ranges the client wanted to retrieve
     * @param contentType Content type of the resource
     * @exception IOException if an input/output error occurs
     */
    protected void copyRanges(File cacheEntry, ServletOutputStream ostream,
                              Iterator ranges, String contentType)
        throws IOException {

        IOException exception = null;

        while ( (exception == null) && (ranges.hasNext()) ) {

            InputStream resourceInputStream = new FileInputStream(cacheEntry);
            InputStream istream =
                new BufferedInputStream(resourceInputStream, input);

            Range currentRange = (Range) ranges.next();

            // Writing MIME header.
            ostream.println();
            ostream.println("--" + mimeSeparation);
            if (contentType != null)
                ostream.println("Content-Type: " + contentType);
            ostream.println("Content-Range: bytes " + currentRange.start
                           + "-" + currentRange.end + "/"
                           + currentRange.length);
            ostream.println();

            // Printing content
            exception = CopyUtils.copyRange(istream, ostream, currentRange.start,
                                            currentRange.end);

            try {
                istream.close();
            } catch (Throwable t) {
                ;
            }

        }

        ostream.println();
        ostream.print("--" + mimeSeparation + "--");

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param resourceInfo The ResourceInfo object
     * @param writer The writer to write to
     * @param ranges Enumeration of the ranges the client wanted to retrieve
     * @param contentType Content type of the resource
     * @exception IOException if an input/output error occurs
     */
    protected void copyRanges(File cacheEntry, PrintWriter writer,
                              Iterator ranges, String contentType)
        throws IOException {

        IOException exception = null;

        // quite inefficient - why not sort and open once
        while ( (exception == null) && (ranges.hasNext()) ) {

            InputStream resourceInputStream = new FileInputStream(cacheEntry);
            
            Reader reader;
            if (fileEncoding == null) {
                reader = new InputStreamReader(resourceInputStream);
            } else {
                reader = new InputStreamReader(resourceInputStream,
                                               fileEncoding);
            }

            Range currentRange = (Range) ranges.next();

            // Writing MIME header.
            writer.println();
            writer.println("--" + mimeSeparation);
            if (contentType != null)
                writer.println("Content-Type: " + contentType);
            writer.println("Content-Range: bytes " + currentRange.start
                           + "-" + currentRange.end + "/"
                           + currentRange.length);
            writer.println();

            // Printing content
            exception = CopyUtils.copyRange(reader, writer, currentRange.start,
                                            currentRange.end);

            try {
                reader.close();
            } catch (Throwable t) {
                ;
            }

        }
        writer.println();
        writer.print("--" + mimeSeparation + "--");

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;
    }
}
