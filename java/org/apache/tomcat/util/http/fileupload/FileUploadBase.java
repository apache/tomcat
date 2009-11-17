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
package org.apache.tomcat.util.http.fileupload;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.servlet.http.HttpServletRequest;

import org.apache.tomcat.util.http.fileupload.MultipartStream.ItemInputStream;


/**
 * <p>High level API for processing file uploads.</p>
 *
 * <p>This class handles multiple files per single HTML widget, sent using
 * <code>multipart/mixed</code> encoding type, as specified by
 * <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>.  Use {@link
 * #parseRequest(HttpServletRequest)} to acquire a list of {@link
 * org.apache.commons.fileupload.FileItem}s associated with a given HTML
 * widget.</p>
 *
 * <p>How the data for individual parts is stored is determined by the factory
 * used to create them; a given part may be in memory, on disk, or somewhere
 * else.</p>
 *
 * @author <a href="mailto:Rafal.Krzewski@e-point.pl">Rafal Krzewski</a>
 * @author <a href="mailto:dlr@collab.net">Daniel Rall</a>
 * @author <a href="mailto:jvanzyl@apache.org">Jason van Zyl</a>
 * @author <a href="mailto:jmcnally@collab.net">John McNally</a>
 * @author <a href="mailto:martinc@apache.org">Martin Cooper</a>
 * @author Sean C. Sullivan
 *
 * @version $Id$
 */
public abstract class FileUploadBase {

    // ---------------------------------------------------------- Class methods


    /**
     * <p>Utility method that determines whether the request contains multipart
     * content.</p>
     *
     * <p><strong>NOTE:</strong>This method will be moved to the
     * <code>ServletFileUpload</code> class after the FileUpload 1.1 release.
     * Unfortunately, since this method is static, it is not possible to
     * provide its replacement until this method is removed.</p>
     *
     * @param ctx The request context to be evaluated. Must be non-null.
     *
     * @return <code>true</code> if the request is multipart;
     *         <code>false</code> otherwise.
     */
    public static final boolean isMultipartContent(RequestContext ctx) {
        String contentType = ctx.getContentType();
        if (contentType == null) {
            return false;
        }
        if (contentType.toLowerCase().startsWith(MULTIPART)) {
            return true;
        }
        return false;
    }


    /**
     * Utility method that determines whether the request contains multipart
     * content.
     *
     * @param req The servlet request to be evaluated. Must be non-null.
     *
     * @return <code>true</code> if the request is multipart;
     *         <code>false</code> otherwise.
     *
     * @deprecated Use the method on <code>ServletFileUpload</code> instead.
     */
    public static boolean isMultipartContent(HttpServletRequest req) {
        return ServletFileUpload.isMultipartContent(req);
    }


    // ----------------------------------------------------- Manifest constants


    /**
     * HTTP content type header name.
     */
    public static final String CONTENT_TYPE = "Content-type";


    /**
     * HTTP content disposition header name.
     */
    public static final String CONTENT_DISPOSITION = "Content-disposition";

    /**
     * HTTP content length header name.
     */
    public static final String CONTENT_LENGTH = "Content-length";


    /**
     * Content-disposition value for form data.
     */
    public static final String FORM_DATA = "form-data";


    /**
     * Content-disposition value for file attachment.
     */
    public static final String ATTACHMENT = "attachment";


    /**
     * Part of HTTP content type header.
     */
    public static final String MULTIPART = "multipart/";


    /**
     * HTTP content type header for multipart forms.
     */
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";


    /**
     * HTTP content type header for multiple uploads.
     */
    public static final String MULTIPART_MIXED = "multipart/mixed";


    /**
     * The maximum length of a single header line that will be parsed
     * (1024 bytes).
     * @deprecated This constant is no longer used. As of commons-fileupload
     *   1.2, the only applicable limit is the total size of a parts headers,
     *   {@link MultipartStream#HEADER_PART_SIZE_MAX}.
     */
    public static final int MAX_HEADER_SIZE = 1024;


    // ----------------------------------------------------------- Data members


    /**
     * The maximum size permitted for the complete request, as opposed to
     * {@link #fileSizeMax}. A value of -1 indicates no maximum.
     */
    private long sizeMax = -1;

    /**
     * The maximum size permitted for a single uploaded file, as opposed
     * to {@link #sizeMax}. A value of -1 indicates no maximum.
     */
    private long fileSizeMax = -1;

    /**
     * The content encoding to use when reading part headers.
     */
    private String headerEncoding;

    /**
     * The progress listener.
     */
    private ProgressListener listener;

    // ----------------------------------------------------- Property accessors


    /**
     * Returns the factory class used when creating file items.
     *
     * @return The factory class for new file items.
     */
    public abstract FileItemFactory getFileItemFactory();


    /**
     * Sets the factory class to use when creating file items.
     *
     * @param factory The factory class for new file items.
     */
    public abstract void setFileItemFactory(FileItemFactory factory);


    /**
     * Returns the maximum allowed size of a complete request, as opposed
     * to {@link #getFileSizeMax()}.
     *
     * @return The maximum allowed size, in bytes. The default value of
     *   -1 indicates, that there is no limit.
     *
     * @see #setSizeMax(long)
     *
     */
    public long getSizeMax() {
        return sizeMax;
    }


    /**
     * Sets the maximum allowed size of a complete request, as opposed
     * to {@link #setFileSizeMax(long)}.
     *
     * @param sizeMax The maximum allowed size, in bytes. The default value of
     *   -1 indicates, that there is no limit.
     *
     * @see #getSizeMax()
     *
     */
    public void setSizeMax(long sizeMax) {
        this.sizeMax = sizeMax;
    }

    /**
     * Returns the maximum allowed size of a single uploaded file,
     * as opposed to {@link #getSizeMax()}.
     *
     * @see #setFileSizeMax(long)
     * @return Maximum size of a single uploaded file.
     */
    public long getFileSizeMax() {
        return fileSizeMax;
    }

    /**
     * Sets the maximum allowed size of a single uploaded file,
     * as opposed to {@link #getSizeMax()}.
     *
     * @see #getFileSizeMax()
     * @param fileSizeMax Maximum size of a single uploaded file.
     */
    public void setFileSizeMax(long fileSizeMax) {
        this.fileSizeMax = fileSizeMax;
    }

    /**
     * Retrieves the character encoding used when reading the headers of an
     * individual part. When not specified, or <code>null</code>, the request
     * encoding is used. If that is also not specified, or <code>null</code>,
     * the platform default encoding is used.
     *
     * @return The encoding used to read part headers.
     */
    public String getHeaderEncoding() {
        return headerEncoding;
    }


    /**
     * Specifies the character encoding to be used when reading the headers of
     * individual part. When not specified, or <code>null</code>, the request
     * encoding is used. If that is also not specified, or <code>null</code>,
     * the platform default encoding is used.
     *
     * @param encoding The encoding used to read part headers.
     */
    public void setHeaderEncoding(String encoding) {
        headerEncoding = encoding;
    }


    // --------------------------------------------------------- Public methods


    /**
     * Processes an <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>
     * compliant <code>multipart/form-data</code> stream.
     *
     * @param req The servlet request to be parsed.
     *
     * @return A list of <code>FileItem</code> instances parsed from the
     *         request, in the order that they were transmitted.
     *
     * @throws FileUploadException if there are problems reading/parsing
     *                             the request or storing files.
     *
     * @deprecated Use the method in <code>ServletFileUpload</code> instead.
     */
    public List /* FileItem */ parseRequest(HttpServletRequest req)
    throws FileUploadException {
        return parseRequest(new ServletRequestContext(req));
    }

    /**
     * Processes an <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>
     * compliant <code>multipart/form-data</code> stream.
     *
     * @param ctx The context for the request to be parsed.
     *
     * @return An iterator to instances of <code>FileItemStream</code>
     *         parsed from the request, in the order that they were
     *         transmitted.
     *
     * @throws FileUploadException if there are problems reading/parsing
     *                             the request or storing files.
     * @throws IOException An I/O error occurred. This may be a network
     *   error while communicating with the client or a problem while
     *   storing the uploaded content.
     */
    public FileItemIterator getItemIterator(RequestContext ctx)
    throws FileUploadException, IOException {
        return new FileItemIteratorImpl(ctx);
    }

    /**
     * Processes an <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>
     * compliant <code>multipart/form-data</code> stream.
     *
     * @param ctx The context for the request to be parsed.
     *
     * @return A list of <code>FileItem</code> instances parsed from the
     *         request, in the order that they were transmitted.
     *
     * @throws FileUploadException if there are problems reading/parsing
     *                             the request or storing files.
     */
    public List /* FileItem */ parseRequest(RequestContext ctx)
            throws FileUploadException {
        try {
            FileItemIterator iter = getItemIterator(ctx);
            List items = new ArrayList();
            FileItemFactory fac = getFileItemFactory();
            if (fac == null) {
                throw new NullPointerException(
                    "No FileItemFactory has been set.");
            }
            while (iter.hasNext()) {
                FileItemStream item = iter.next();
                FileItem fileItem = fac.createItem(item.getFieldName(),
                        item.getContentType(), item.isFormField(),
                        item.getName());
                try {
                    Streams.copy(item.openStream(), fileItem.getOutputStream(),
                            true);
                } catch (FileUploadIOException e) {
                    throw (FileUploadException) e.getCause();
                } catch (IOException e) {
                    throw new IOFileUploadException(
                            "Processing of " + MULTIPART_FORM_DATA
                            + " request failed. " + e.getMessage(), e);
                }
                if (fileItem instanceof FileItemHeadersSupport) {
                    final FileItemHeaders fih = item.getHeaders();
                    ((FileItemHeadersSupport) fileItem).setHeaders(fih);
                }
                items.add(fileItem);
            }
            return items;
        } catch (FileUploadIOException e) {
            throw (FileUploadException) e.getCause();
        } catch (IOException e) {
            throw new FileUploadException(e.getMessage(), e);
        }
    }


    // ------------------------------------------------------ Protected methods


    /**
     * Retrieves the boundary from the <code>Content-type</code> header.
     *
     * @param contentType The value of the content type header from which to
     *                    extract the boundary value.
     *
     * @return The boundary, as a byte array.
     */
    protected byte[] getBoundary(String contentType) {
        ParameterParser parser = new ParameterParser();
        parser.setLowerCaseNames(true);
        // Parameter parser can handle null input
        Map params = parser.parse(contentType, new char[] {';', ','});
        String boundaryStr = (String) params.get("boundary");

        if (boundaryStr == null) {
            return null;
        }
        byte[] boundary;
        try {
            boundary = boundaryStr.getBytes("ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            boundary = boundaryStr.getBytes();
        }
        return boundary;
    }


    /**
     * Retrieves the file name from the <code>Content-disposition</code>
     * header.
     *
     * @param headers A <code>Map</code> containing the HTTP request headers.
     *
     * @return The file name for the current <code>encapsulation</code>.
     * @deprecated Use {@link #getFileName(FileItemHeaders)}.
     */
    protected String getFileName(Map /* String, String */ headers) {
        return getFileName(getHeader(headers, CONTENT_DISPOSITION));
    }

    /**
     * Retrieves the file name from the <code>Content-disposition</code>
     * header.
     *
     * @param headers The HTTP headers object.
     *
     * @return The file name for the current <code>encapsulation</code>.
     */
    protected String getFileName(FileItemHeaders headers) {
        return getFileName(headers.getHeader(CONTENT_DISPOSITION));
    }

    /**
     * Returns the given content-disposition headers file name.
     * @param pContentDisposition The content-disposition headers value.
     * @return The file name
     */
    private String getFileName(String pContentDisposition) {
        String fileName = null;
        if (pContentDisposition != null) {
            String cdl = pContentDisposition.toLowerCase();
            if (cdl.startsWith(FORM_DATA) || cdl.startsWith(ATTACHMENT)) {
                ParameterParser parser = new ParameterParser();
                parser.setLowerCaseNames(true);
                // Parameter parser can handle null input
                Map params = parser.parse(pContentDisposition, ';');
                if (params.containsKey("filename")) {
                    fileName = (String) params.get("filename");
                    if (fileName != null) {
                        fileName = fileName.trim();
                    } else {
                        // Even if there is no value, the parameter is present,
                        // so we return an empty file name rather than no file
                        // name.
                        fileName = "";
                    }
                }
            }
        }
        return fileName;
    }


    /**
     * Retrieves the field name from the <code>Content-disposition</code>
     * header.
     *
     * @param headers A <code>Map</code> containing the HTTP request headers.
     *
     * @return The field name for the current <code>encapsulation</code>.
     */
    protected String getFieldName(FileItemHeaders headers) {
        return getFieldName(headers.getHeader(CONTENT_DISPOSITION));
    }

    /**
     * Returns the field name, which is given by the content-disposition
     * header.
     * @param pContentDisposition The content-dispositions header value.
     * @return The field jake
     */
    private String getFieldName(String pContentDisposition) {
        String fieldName = null;
        if (pContentDisposition != null
                && pContentDisposition.toLowerCase().startsWith(FORM_DATA)) {
            ParameterParser parser = new ParameterParser();
            parser.setLowerCaseNames(true);
            // Parameter parser can handle null input
            Map params = parser.parse(pContentDisposition, ';');
            fieldName = (String) params.get("name");
            if (fieldName != null) {
                fieldName = fieldName.trim();
            }
        }
        return fieldName;
    }

    /**
     * Retrieves the field name from the <code>Content-disposition</code>
     * header.
     *
     * @param headers A <code>Map</code> containing the HTTP request headers.
     *
     * @return The field name for the current <code>encapsulation</code>.
     * @deprecated Use {@link #getFieldName(FileItemHeaders)}.
     */
    protected String getFieldName(Map /* String, String */ headers) {
        return getFieldName(getHeader(headers, CONTENT_DISPOSITION));
    }


    /**
     * Creates a new {@link FileItem} instance.
     *
     * @param headers       A <code>Map</code> containing the HTTP request
     *                      headers.
     * @param isFormField   Whether or not this item is a form field, as
     *                      opposed to a file.
     *
     * @return A newly created <code>FileItem</code> instance.
     *
     * @throws FileUploadException if an error occurs.
     * @deprecated This method is no longer used in favour of
     *   internally created instances of {@link FileItem}.
     */
    protected FileItem createItem(Map /* String, String */ headers,
                                  boolean isFormField)
        throws FileUploadException {
        return getFileItemFactory().createItem(getFieldName(headers),
                getHeader(headers, CONTENT_TYPE),
                isFormField,
                getFileName(headers));
    }

    /**
     * <p> Parses the <code>header-part</code> and returns as key/value
     * pairs.
     *
     * <p> If there are multiple headers of the same names, the name
     * will map to a comma-separated list containing the values.
     *
     * @param headerPart The <code>header-part</code> of the current
     *                   <code>encapsulation</code>.
     *
     * @return A <code>Map</code> containing the parsed HTTP request headers.
     */
    protected FileItemHeaders getParsedHeaders(String headerPart) {
        final int len = headerPart.length();
        FileItemHeadersImpl headers = newFileItemHeaders();
        int start = 0;
        for (;;) {
            int end = parseEndOfLine(headerPart, start);
            if (start == end) {
                break;
            }
            String header = headerPart.substring(start, end);
            start = end + 2;
            while (start < len) {
                int nonWs = start;
                while (nonWs < len) {
                    char c = headerPart.charAt(nonWs);
                    if (c != ' '  &&  c != '\t') {
                        break;
                    }
                    ++nonWs;
                }
                if (nonWs == start) {
                    break;
                }
                // Continuation line found
                end = parseEndOfLine(headerPart, nonWs);
                header += " " + headerPart.substring(nonWs, end);
                start = end + 2;
            }
            parseHeaderLine(headers, header);
        }
        return headers;
    }

    /**
     * Creates a new instance of {@link FileItemHeaders}.
     * @return The new instance.
     */
    protected FileItemHeadersImpl newFileItemHeaders() {
        return new FileItemHeadersImpl();
    }

    /**
     * <p> Parses the <code>header-part</code> and returns as key/value
     * pairs.
     *
     * <p> If there are multiple headers of the same names, the name
     * will map to a comma-separated list containing the values.
     *
     * @param headerPart The <code>header-part</code> of the current
     *                   <code>encapsulation</code>.
     *
     * @return A <code>Map</code> containing the parsed HTTP request headers.
     * @deprecated Use {@link #getParsedHeaders(String)}
     */
    protected Map /* String, String */ parseHeaders(String headerPart) {
        FileItemHeaders headers = getParsedHeaders(headerPart);
        Map result = new HashMap();
        for (Iterator iter = headers.getHeaderNames();  iter.hasNext();) {
            String headerName = (String) iter.next();
            Iterator iter2 = headers.getHeaders(headerName);
            String headerValue = (String) iter2.next();
            while (iter2.hasNext()) {
                headerValue += "," + iter2.next();
            }
            result.put(headerName, headerValue);
        }
        return result;
    }

    /**
     * Skips bytes until the end of the current line.
     * @param headerPart The headers, which are being parsed.
     * @param end Index of the last byte, which has yet been
     *   processed.
     * @return Index of the \r\n sequence, which indicates
     *   end of line.
     */
    private int parseEndOfLine(String headerPart, int end) {
        int index = end;
        for (;;) {
            int offset = headerPart.indexOf('\r', index);
            if (offset == -1  ||  offset + 1 >= headerPart.length()) {
                throw new IllegalStateException(
                    "Expected headers to be terminated by an empty line.");
            }
            if (headerPart.charAt(offset + 1) == '\n') {
                return offset;
            }
            index = offset + 1;
        }
    }

    /**
     * Reads the next header line.
     * @param headers String with all headers.
     * @param header Map where to store the current header.
     */
    private void parseHeaderLine(FileItemHeadersImpl headers, String header) {
        final int colonOffset = header.indexOf(':');
        if (colonOffset == -1) {
            // This header line is malformed, skip it.
            return;
        }
        String headerName = header.substring(0, colonOffset).trim();
        String headerValue =
            header.substring(header.indexOf(':') + 1).trim();
        headers.addHeader(headerName, headerValue);
    }

    /**
     * Returns the header with the specified name from the supplied map. The
     * header lookup is case-insensitive.
     *
     * @param headers A <code>Map</code> containing the HTTP request headers.
     * @param name    The name of the header to return.
     *
     * @return The value of specified header, or a comma-separated list if
     *         there were multiple headers of that name.
     * @deprecated Use {@link FileItemHeaders#getHeader(String)}.
     */
    protected final String getHeader(Map /* String, String */ headers,
            String name) {
        return (String) headers.get(name.toLowerCase());
    }

    /**
     * The iterator, which is returned by
     * {@link FileUploadBase#getItemIterator(RequestContext)}.
     */
    private class FileItemIteratorImpl implements FileItemIterator {
        /**
         * Default implementation of {@link FileItemStream}.
         */
        private class FileItemStreamImpl implements FileItemStream {
            /** The file items content type.
             */
            private final String contentType;
            /** The file items field name.
             */
            private final String fieldName;
            /** The file items file name.
             */
            private final String name;
            /** Whether the file item is a form field.
             */
            private final boolean formField;
            /** The file items input stream.
             */
            private final InputStream stream;
            /** Whether the file item was already opened.
             */
            private boolean opened;
            /** The headers, if any.
             */
            private FileItemHeaders headers;

            /**
             * Creates a new instance.
             * @param pName The items file name, or null.
             * @param pFieldName The items field name.
             * @param pContentType The items content type, or null.
             * @param pFormField Whether the item is a form field.
             * @param pContentLength The items content length, if known, or -1
             * @throws IOException Creating the file item failed.
             */
            FileItemStreamImpl(String pName, String pFieldName,
                    String pContentType, boolean pFormField,
                    long pContentLength) throws IOException {
                name = pName;
                fieldName = pFieldName;
                contentType = pContentType;
                formField = pFormField;
                final ItemInputStream itemStream = multi.newInputStream();
                InputStream istream = itemStream;
                if (fileSizeMax != -1) {
                    if (pContentLength != -1
                            &&  pContentLength > fileSizeMax) {
                        FileUploadException e =
                            new FileSizeLimitExceededException(
                                "The field " + fieldName
                                + " exceeds its maximum permitted "
                                + " size of " + fileSizeMax
                                + " characters.",
                                pContentLength, fileSizeMax);
                        throw new FileUploadIOException(e);
                    }
                    istream = new LimitedInputStream(istream, fileSizeMax) {
                        protected void raiseError(long pSizeMax, long pCount)
                                throws IOException {
                            itemStream.close(true);
                            FileUploadException e =
                                new FileSizeLimitExceededException(
                                    "The field " + fieldName
                                    + " exceeds its maximum permitted "
                                    + " size of " + pSizeMax
                                    + " characters.",
                                    pCount, pSizeMax);
                            throw new FileUploadIOException(e);
                        }
                    };
                }
                stream = istream;
            }

            /**
             * Returns the items content type, or null.
             * @return Content type, if known, or null.
             */
            public String getContentType() {
                return contentType;
            }

            /**
             * Returns the items field name.
             * @return Field name.
             */
            public String getFieldName() {
                return fieldName;
            }

            /**
             * Returns the items file name.
             * @return File name, if known, or null.
             */
            public String getName() {
                return name;
            }

            /**
             * Returns, whether this is a form field.
             * @return True, if the item is a form field,
             *   otherwise false.
             */
            public boolean isFormField() {
                return formField;
            }

            /**
             * Returns an input stream, which may be used to
             * read the items contents.
             * @return Opened input stream.
             * @throws IOException An I/O error occurred.
             */
            public InputStream openStream() throws IOException {
                if (opened) {
                    throw new IllegalStateException(
                            "The stream was already opened.");
                }
                if (((Closeable) stream).isClosed()) {
                    throw new FileItemStream.ItemSkippedException();
                }
                return stream;
            }

            /**
             * Closes the file item.
             * @throws IOException An I/O error occurred.
             */
            void close() throws IOException {
                stream.close();
            }

            /**
             * Returns the file item headers.
             * @return The items header object
             */
            public FileItemHeaders getHeaders() {
                return headers;
            }

            /**
             * Sets the file item headers.
             * @param pHeaders The items header object
             */
            public void setHeaders(FileItemHeaders pHeaders) {
                headers = pHeaders;
            }
        }

        /**
         * The multi part stream to process.
         */
        private final MultipartStream multi;
        /**
         * The notifier, which used for triggering the
         * {@link ProgressListener}.
         */
        private final MultipartStream.ProgressNotifier notifier;
        /**
         * The boundary, which separates the various parts.
         */
        private final byte[] boundary;
        /**
         * The item, which we currently process.
         */
        private FileItemStreamImpl currentItem;
        /**
         * The current items field name.
         */
        private String currentFieldName;
        /**
         * Whether we are currently skipping the preamble.
         */
        private boolean skipPreamble;
        /**
         * Whether the current item may still be read.
         */
        private boolean itemValid;
        /**
         * Whether we have seen the end of the file.
         */
        private boolean eof;

        /**
         * Creates a new instance.
         * @param ctx The request context.
         * @throws FileUploadException An error occurred while
         *   parsing the request.
         * @throws IOException An I/O error occurred.
         */
        FileItemIteratorImpl(RequestContext ctx)
                throws FileUploadException, IOException {
            if (ctx == null) {
                throw new NullPointerException("ctx parameter");
            }

            String contentType = ctx.getContentType();
            if ((null == contentType)
                    || (!contentType.toLowerCase().startsWith(MULTIPART))) {
                throw new InvalidContentTypeException(
                        "the request doesn't contain a "
                        + MULTIPART_FORM_DATA
                        + " or "
                        + MULTIPART_MIXED
                        + " stream, content type header is "
                        + contentType);
            }

            InputStream input = ctx.getInputStream();

            if (sizeMax >= 0) {
                int requestSize = ctx.getContentLength();
                if (requestSize == -1) {
                    input = new LimitedInputStream(input, sizeMax) {
                        protected void raiseError(long pSizeMax, long pCount)
                                throws IOException {
                            FileUploadException ex =
                                new SizeLimitExceededException(
                                    "the request was rejected because"
                                    + " its size (" + pCount
                                    + ") exceeds the configured maximum"
                                    + " (" + pSizeMax + ")",
                                    pCount, pSizeMax);
                            throw new FileUploadIOException(ex);
                        }
                    };
                } else {
                    if (sizeMax >= 0 && requestSize > sizeMax) {
                        throw new SizeLimitExceededException(
                                "the request was rejected because its size ("
                                + requestSize
                                + ") exceeds the configured maximum ("
                                + sizeMax + ")",
                                requestSize, sizeMax);
                    }
                }
            }

            String charEncoding = headerEncoding;
            if (charEncoding == null) {
                charEncoding = ctx.getCharacterEncoding();
            }

            boundary = getBoundary(contentType);
            if (boundary == null) {
                throw new FileUploadException(
                        "the request was rejected because "
                        + "no multipart boundary was found");
            }

            notifier = new MultipartStream.ProgressNotifier(listener,
                    ctx.getContentLength());
            multi = new MultipartStream(input, boundary, notifier);
            multi.setHeaderEncoding(charEncoding);

            skipPreamble = true;
            findNextItem();
        }

        /**
         * Called for finding the nex item, if any.
         * @return True, if an next item was found, otherwise false.
         * @throws IOException An I/O error occurred.
         */
        private boolean findNextItem() throws IOException {
            if (eof) {
                return false;
            }
            if (currentItem != null) {
                currentItem.close();
                currentItem = null;
            }
            for (;;) {
                boolean nextPart;
                if (skipPreamble) {
                    nextPart = multi.skipPreamble();
                } else {
                    nextPart = multi.readBoundary();
                }
                if (!nextPart) {
                    if (currentFieldName == null) {
                        // Outer multipart terminated -> No more data
                        eof = true;
                        return false;
                    }
                    // Inner multipart terminated -> Return to parsing the outer
                    multi.setBoundary(boundary);
                    currentFieldName = null;
                    continue;
                }
                FileItemHeaders headers = getParsedHeaders(multi.readHeaders());
                if (currentFieldName == null) {
                    // We're parsing the outer multipart
                    String fieldName = getFieldName(headers);
                    if (fieldName != null) {
                        String subContentType = headers.getHeader(CONTENT_TYPE);
                        if (subContentType != null
                                &&  subContentType.toLowerCase()
                                        .startsWith(MULTIPART_MIXED)) {
                            currentFieldName = fieldName;
                            // Multiple files associated with this field name
                            byte[] subBoundary = getBoundary(subContentType);
                            multi.setBoundary(subBoundary);
                            skipPreamble = true;
                            continue;
                        }
                        String fileName = getFileName(headers);
                        currentItem = new FileItemStreamImpl(fileName,
                                fieldName, headers.getHeader(CONTENT_TYPE),
                                fileName == null, getContentLength(headers));
                        notifier.noteItem();
                        itemValid = true;
                        return true;
                    }
                } else {
                    String fileName = getFileName(headers);
                    if (fileName != null) {
                        currentItem = new FileItemStreamImpl(fileName,
                                currentFieldName,
                                headers.getHeader(CONTENT_TYPE),
                                false, getContentLength(headers));
                        notifier.noteItem();
                        itemValid = true;
                        return true;
                    }
                }
                multi.discardBodyData();
            }
        }

        private long getContentLength(FileItemHeaders pHeaders) {
            try {
                return Long.parseLong(pHeaders.getHeader(CONTENT_LENGTH));
            } catch (Exception e) {
                return -1;
            }
        }

        /**
         * Returns, whether another instance of {@link FileItemStream}
         * is available.
         * @throws FileUploadException Parsing or processing the
         *   file item failed.
         * @throws IOException Reading the file item failed.
         * @return True, if one or more additional file items
         *   are available, otherwise false.
         */
        public boolean hasNext() throws FileUploadException, IOException {
            if (eof) {
                return false;
            }
            if (itemValid) {
                return true;
            }
            return findNextItem();
        }

        /**
         * Returns the next available {@link FileItemStream}.
         * @throws java.util.NoSuchElementException No more items are
         *   available. Use {@link #hasNext()} to prevent this exception.
         * @throws FileUploadException Parsing or processing the
         *   file item failed.
         * @throws IOException Reading the file item failed.
         * @return FileItemStream instance, which provides
         *   access to the next file item.
         */
        public FileItemStream next() throws FileUploadException, IOException {
            if (eof  ||  (!itemValid && !hasNext())) {
                throw new NoSuchElementException();
            }
            itemValid = false;
            return currentItem;
        }
    }

    /**
     * This exception is thrown for hiding an inner
     * {@link FileUploadException} in an {@link IOException}.
     */
    public static class FileUploadIOException extends IOException {
        /** The exceptions UID, for serializing an instance.
         */
        private static final long serialVersionUID = -7047616958165584154L;
        /** The exceptions cause; we overwrite the parent
         * classes field, which is available since Java
         * 1.4 only.
         */
        private final FileUploadException cause;

        /**
         * Creates a <code>FileUploadIOException</code> with the
         * given cause.
         * @param pCause The exceptions cause, if any, or null.
         */
        public FileUploadIOException(FileUploadException pCause) {
            // We're not doing super(pCause) cause of 1.3 compatibility.
            cause = pCause;
        }

        /**
         * Returns the exceptions cause.
         * @return The exceptions cause, if any, or null.
         */
        public Throwable getCause() {
            return cause;
        }
    }

    /**
     * Thrown to indicate that the request is not a multipart request.
     */
    public static class InvalidContentTypeException
            extends FileUploadException {
        /** The exceptions UID, for serializing an instance.
         */
        private static final long serialVersionUID = -9073026332015646668L;

        /**
         * Constructs a <code>InvalidContentTypeException</code> with no
         * detail message.
         */
        public InvalidContentTypeException() {
            // Nothing to do.
        }

        /**
         * Constructs an <code>InvalidContentTypeException</code> with
         * the specified detail message.
         *
         * @param message The detail message.
         */
        public InvalidContentTypeException(String message) {
            super(message);
        }
    }

    /**
     * Thrown to indicate an IOException.
     */
    public static class IOFileUploadException extends FileUploadException {
        /** The exceptions UID, for serializing an instance.
         */
        private static final long serialVersionUID = 1749796615868477269L;
        /** The exceptions cause; we overwrite the parent
         * classes field, which is available since Java
         * 1.4 only.
         */
        private final IOException cause;

        /**
         * Creates a new instance with the given cause.
         * @param pMsg The detail message.
         * @param pException The exceptions cause.
         */
        public IOFileUploadException(String pMsg, IOException pException) {
            super(pMsg);
            cause = pException;
        }

        /**
         * Returns the exceptions cause.
         * @return The exceptions cause, if any, or null.
         */
        public Throwable getCause() {
            return cause;
        }
    }

    /** This exception is thrown, if a requests permitted size
     * is exceeded.
     */
    protected abstract static class SizeException extends FileUploadException {
        /**
         * The actual size of the request.
         */
        private final long actual;

        /**
         * The maximum permitted size of the request.
         */
        private final long permitted;

        /**
         * Creates a new instance.
         * @param message The detail message.
         * @param actual The actual number of bytes in the request.
         * @param permitted The requests size limit, in bytes.
         */
        protected SizeException(String message, long actual, long permitted) {
            super(message);
            this.actual = actual;
            this.permitted = permitted;
        }

        /**
         * Retrieves the actual size of the request.
         *
         * @return The actual size of the request.
         */
        public long getActualSize() {
            return actual;
        }

        /**
         * Retrieves the permitted size of the request.
         *
         * @return The permitted size of the request.
         */
        public long getPermittedSize() {
            return permitted;
        }
    }

    /**
     * Thrown to indicate that the request size is not specified. In other
     * words, it is thrown, if the content-length header is missing or
     * contains the value -1.
     * @deprecated As of commons-fileupload 1.2, the presence of a
     *   content-length header is no longer required.
     */
    public static class UnknownSizeException
        extends FileUploadException {
        /** The exceptions UID, for serializing an instance.
         */
        private static final long serialVersionUID = 7062279004812015273L;

        /**
         * Constructs a <code>UnknownSizeException</code> with no
         * detail message.
         */
        public UnknownSizeException() {
            super();
        }

        /**
         * Constructs an <code>UnknownSizeException</code> with
         * the specified detail message.
         *
         * @param message The detail message.
         */
        public UnknownSizeException(String message) {
            super(message);
        }
    }

    /**
     * Thrown to indicate that the request size exceeds the configured maximum.
     */
    public static class SizeLimitExceededException
            extends SizeException {
        /** The exceptions UID, for serializing an instance.
         */
        private static final long serialVersionUID = -2474893167098052828L;

        /**
         * @deprecated Replaced by
         * {@link #SizeLimitExceededException(String, long, long)}
         */
        public SizeLimitExceededException() {
            this(null, 0, 0);
        }

        /**
         * @deprecated Replaced by
         * {@link #SizeLimitExceededException(String, long, long)}
         * @param message The exceptions detail message.
         */
        public SizeLimitExceededException(String message) {
            this(message, 0, 0);
        }

        /**
         * Constructs a <code>SizeExceededException</code> with
         * the specified detail message, and actual and permitted sizes.
         *
         * @param message   The detail message.
         * @param actual    The actual request size.
         * @param permitted The maximum permitted request size.
         */
        public SizeLimitExceededException(String message, long actual,
                long permitted) {
            super(message, actual, permitted);
        }
    }

    /**
     * Thrown to indicate that A files size exceeds the configured maximum.
     */
    public static class FileSizeLimitExceededException
            extends SizeException {
        /** The exceptions UID, for serializing an instance.
         */
        private static final long serialVersionUID = 8150776562029630058L;

        /**
         * Constructs a <code>SizeExceededException</code> with
         * the specified detail message, and actual and permitted sizes.
         *
         * @param message   The detail message.
         * @param actual    The actual request size.
         * @param permitted The maximum permitted request size.
         */
        public FileSizeLimitExceededException(String message, long actual,
                long permitted) {
            super(message, actual, permitted);
        }
    }

    /**
     * Returns the progress listener.
     * @return The progress listener, if any, or null.
     */
    public ProgressListener getProgressListener() {
        return listener;
    }

    /**
     * Sets the progress listener.
     * @param pListener The progress listener, if any. Defaults to null.
     */
    public void setProgressListener(ProgressListener pListener) {
        listener = pListener;
    }
}
