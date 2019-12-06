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
package org.apache.tomcat.util.http.fileupload.impl;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tomcat.util.http.fileupload.FileItemHeaders;
import org.apache.tomcat.util.http.fileupload.FileItemStream;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.InvalidFileNameException;
import org.apache.tomcat.util.http.fileupload.MultipartStream.ItemInputStream;
import org.apache.tomcat.util.http.fileupload.util.Closeable;
import org.apache.tomcat.util.http.fileupload.util.LimitedInputStream;
import org.apache.tomcat.util.http.fileupload.util.Streams;


/**
 * Default implementation of {@link FileItemStream}.
 */
public class FileItemStreamImpl implements FileItemStream {
    private final FileItemIteratorImpl fileItemIteratorImpl;

    /**
     * The file items content type.
     */
    private final String contentType;

    /**
     * The file items field name.
     */
    private final String fieldName;

    /**
     * The file items file name.
     */
    final String name;

    /**
     * Whether the file item is a form field.
     */
    private final boolean formField;

    /**
     * The file items input stream.
     */
    private final InputStream stream;

    /**
     * The headers, if any.
     */
    private FileItemHeaders headers;

    /**
     * Creates a new instance.
     * @param pFileItemIterator Iterator for all files in this upload
     * @param pName The items file name, or null.
     * @param pFieldName The items field name.
     * @param pContentType The items content type, or null.
     * @param pFormField Whether the item is a form field.
     * @param pContentLength The items content length, if known, or -1
     * @throws FileUploadException If an error is encountered processing the request
     * @throws IOException Creating the file item failed.
     */
    public FileItemStreamImpl(FileItemIteratorImpl pFileItemIterator, String pName, String pFieldName,
            String pContentType, boolean pFormField,
            long pContentLength) throws FileUploadException, IOException {
        fileItemIteratorImpl = pFileItemIterator;
        name = pName;
        fieldName = pFieldName;
        contentType = pContentType;
        formField = pFormField;
        final long fileSizeMax = fileItemIteratorImpl.getFileSizeMax();
        if (fileSizeMax != -1) { // Check if limit is already exceeded
            if (pContentLength != -1
                    && pContentLength > fileSizeMax) {
                FileSizeLimitExceededException e =
                        new FileSizeLimitExceededException(
                                String.format("The field %s exceeds its maximum permitted size of %s bytes.",
                                        fieldName, Long.valueOf(fileSizeMax)),
                                pContentLength, fileSizeMax);
                e.setFileName(pName);
                e.setFieldName(pFieldName);
                throw new FileUploadIOException(e);
            }
        }
        // OK to construct stream now
        final ItemInputStream itemStream = fileItemIteratorImpl.getMultiPartStream().newInputStream();
        InputStream istream = itemStream;
        if (fileSizeMax != -1) {
            istream = new LimitedInputStream(istream, fileSizeMax) {
                @Override
                protected void raiseError(long pSizeMax, long pCount)
                        throws IOException {
                    itemStream.close(true);
                    FileSizeLimitExceededException e =
                        new FileSizeLimitExceededException(
                            String.format("The field %s exceeds its maximum permitted size of %s bytes.",
                                   fieldName, Long.valueOf(pSizeMax)),
                            pCount, pSizeMax);
                    e.setFieldName(fieldName);
                    e.setFileName(name);
                    throw new FileUploadIOException(e);
                }
            };
        }
        stream = istream;
    }

    /**
     * Returns the items content type, or null.
     *
     * @return Content type, if known, or null.
     */
    @Override
    public String getContentType() {
        return contentType;
    }

    /**
     * Returns the items field name.
     *
     * @return Field name.
     */
    @Override
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Returns the items file name.
     *
     * @return File name, if known, or null.
     * @throws InvalidFileNameException The file name contains a NUL character,
     *   which might be an indicator of a security attack. If you intend to
     *   use the file name anyways, catch the exception and use
     *   InvalidFileNameException#getName().
     */
    @Override
    public String getName() {
        return Streams.checkFileName(name);
    }

    /**
     * Returns, whether this is a form field.
     *
     * @return True, if the item is a form field,
     *   otherwise false.
     */
    @Override
    public boolean isFormField() {
        return formField;
    }

    /**
     * Returns an input stream, which may be used to
     * read the items contents.
     *
     * @return Opened input stream.
     * @throws IOException An I/O error occurred.
     */
    @Override
    public InputStream openStream() throws IOException {
        if (((Closeable) stream).isClosed()) {
            throw new FileItemStream.ItemSkippedException();
        }
        return stream;
    }

    /**
     * Closes the file item.
     *
     * @throws IOException An I/O error occurred.
     */
    public void close() throws IOException {
        stream.close();
    }

    /**
     * Returns the file item headers.
     *
     * @return The items header object
     */
    @Override
    public FileItemHeaders getHeaders() {
        return headers;
    }

    /**
     * Sets the file item headers.
     *
     * @param pHeaders The items header object
     */
    @Override
    public void setHeaders(FileItemHeaders pHeaders) {
        headers = pHeaders;
    }

}