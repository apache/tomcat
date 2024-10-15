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
package org.apache.tomcat.util.http.fileupload.disk;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tomcat.util.http.fileupload.DeferredFileOutputStream;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemHeaders;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.apache.tomcat.util.http.fileupload.ParameterParser;
import org.apache.tomcat.util.http.fileupload.util.Streams;

/**
 * <p> The default implementation of the
 * {@link org.apache.tomcat.util.http.fileupload.FileItem FileItem} interface.
 *
 * <p> After retrieving an instance of this class from a {@link
 * org.apache.tomcat.util.http.fileupload.FileUpload FileUpload} instance (see
 * {@link org.apache.tomcat.util.http.fileupload.FileUpload
 * #parseRequest(org.apache.tomcat.util.http.fileupload.RequestContext)}), you
 * may either request all contents of file at once using {@link #get()} or
 * request an {@link java.io.InputStream InputStream} with
 * {@link #getInputStream()} and process the file without attempting to load
 * it into memory, which may come handy with large files.
 *
 * <p>Temporary files, which are created for file items, will be deleted when
 * the associated request is recycled.</p>
 *
 * @since FileUpload 1.1
 */
public class DiskFileItem
    implements FileItem {

    // ----------------------------------------------------- Manifest constants

    /**
     * Default content charset to be used when no explicit charset
     * parameter is provided by the sender. Media subtypes of the
     * "text" type are defined to have a default charset value of
     * "ISO-8859-1" when received via HTTP.
     */
    public static final String DEFAULT_CHARSET = "ISO-8859-1";

    // ----------------------------------------------------------- Data members

    /**
     * UID used in unique file name generation.
     */
    private static final String UID =
            UUID.randomUUID().toString().replace('-', '_');

    /**
     * Counter used in unique identifier generation.
     */
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    /**
     * The name of the form field as provided by the browser.
     */
    private String fieldName;

    /**
     * The content type passed by the browser, or {@code null} if
     * not defined.
     */
    private final String contentType;

    /**
     * Whether or not this item is a simple form field.
     */
    private boolean isFormField;

    /**
     * The original file name in the user's file system.
     */
    private final String fileName;

    /**
     * The size of the item, in bytes. This is used to cache the size when a
     * file item is moved from its original location.
     */
    private long size = -1;


    /**
     * The threshold above which uploads will be stored on disk.
     */
    private final int sizeThreshold;

    /**
     * The directory in which uploaded files will be stored, if stored on disk.
     */
    private final File repository;

    /**
     * Cached contents of the file.
     */
    private byte[] cachedContent;

    /**
     * Output stream for this item.
     */
    private transient DeferredFileOutputStream dfos;

    /**
     * The temporary file to use.
     */
    private transient File tempFile;

    /**
     * The file items headers.
     */
    private FileItemHeaders headers;

    /**
     * Default content charset to be used when no explicit charset
     * parameter is provided by the sender.
     */
    private String defaultCharset = DEFAULT_CHARSET;

    // ----------------------------------------------------------- Constructors

    /**
     * Constructs a new {@code DiskFileItem} instance.
     *
     * @param fieldName     The name of the form field.
     * @param contentType   The content type passed by the browser or
     *                      {@code null} if not specified.
     * @param isFormField   Whether or not this item is a plain form field, as
     *                      opposed to a file upload.
     * @param fileName      The original file name in the user's file system, or
     *                      {@code null} if not specified.
     * @param sizeThreshold The threshold, in bytes, below which items will be
     *                      retained in memory and above which they will be
     *                      stored as a file.
     * @param repository    The data repository, which is the directory in
     *                      which files will be created, should the item size
     *                      exceed the threshold.
     */
    public DiskFileItem(final String fieldName,
            final String contentType, final boolean isFormField, final String fileName,
            final int sizeThreshold, final File repository) {
        this.fieldName = fieldName;
        this.contentType = contentType;
        this.isFormField = isFormField;
        this.fileName = fileName;
        this.sizeThreshold = sizeThreshold;
        this.repository = repository;
    }

    // ------------------------------- Methods from javax.activation.DataSource

    /**
     * Returns an {@link java.io.InputStream InputStream} that can be
     * used to retrieve the contents of the file.
     *
     * @return An {@link java.io.InputStream InputStream} that can be
     *         used to retrieve the contents of the file.
     *
     * @throws IOException if an error occurs.
     */
    @Override
    public InputStream getInputStream()
        throws IOException {
        if (!isInMemory()) {
            return Files.newInputStream(dfos.getFile().toPath());
        }

        if (cachedContent == null) {
            cachedContent = dfos.getData();
        }
        return new ByteArrayInputStream(cachedContent);
    }

    /**
     * Returns the content type passed by the agent or {@code null} if
     * not defined.
     *
     * @return The content type passed by the agent or {@code null} if
     *         not defined.
     */
    @Override
    public String getContentType() {
        return contentType;
    }

    /**
     * Returns the content charset passed by the agent or {@code null} if
     * not defined.
     *
     * @return The content charset passed by the agent or {@code null} if
     *         not defined.
     */
    public String getCharSet() {
        final ParameterParser parser = new ParameterParser();
        parser.setLowerCaseNames(true);
        // Parameter parser can handle null input
        final Map<String, String> params = parser.parse(getContentType(), ';');
        return params.get("charset");
    }

    /**
     * Returns the original file name in the client's file system.
     *
     * @return The original file name in the client's file system.
     * @throws org.apache.tomcat.util.http.fileupload.InvalidFileNameException
     *   The file name contains a NUL character, which might be an indicator of
     *   a security attack. If you intend to use the file name anyways, catch
     *   the exception and use {@link
     *   org.apache.tomcat.util.http.fileupload.InvalidFileNameException#getName()}.
     */
    @Override
    public String getName() {
        return Streams.checkFileName(fileName);
    }

    // ------------------------------------------------------- FileItem methods

    /**
     * Provides a hint as to whether or not the file contents will be read
     * from memory.
     *
     * @return {@code true} if the file contents will be read
     *         from memory; {@code false} otherwise.
     */
    @Override
    public boolean isInMemory() {
        if (cachedContent != null) {
            return true;
        }
        return dfos.isInMemory();
    }

    /**
     * Returns the size of the file.
     *
     * @return The size of the file, in bytes.
     */
    @Override
    public long getSize() {
        if (size >= 0) {
            return size;
        }
        if (cachedContent != null) {
            return cachedContent.length;
        }
        if (dfos.isInMemory()) {
            return dfos.getData().length;
        }
        return dfos.getFile().length();
    }

    /**
     * Returns the contents of the file as an array of bytes.  If the
     * contents of the file were not yet cached in memory, they will be
     * loaded from the disk storage and cached.
     *
     * @return The contents of the file as an array of bytes
     * or {@code null} if the data cannot be read
     *
     * @throws UncheckedIOException if an I/O error occurs
     * @throws ArithmeticException if the file {@code size} overflows an int
     */
    @Override
    public byte[] get() throws UncheckedIOException {
        if (isInMemory()) {
            if (cachedContent == null && dfos != null) {
                cachedContent = dfos.getData();
            }
            return cachedContent != null ? cachedContent.clone() : new byte[0];
        }

        final byte[] fileData = new byte[Math.toIntExact(getSize())];

        try (InputStream fis = Files.newInputStream(dfos.getFile().toPath())) {
            IOUtils.readFully(fis, fileData);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return fileData;
    }

    /**
     * Returns the contents of the file as a String, using the specified
     * encoding.  This method uses {@link #get()} to retrieve the
     * contents of the file.
     *
     * @param charset The charset to use.
     *
     * @return The contents of the file, as a string.
     *
     * @throws UnsupportedEncodingException if the requested character
     *                                      encoding is not available.
     */
    @Override
    public String getString(final String charset)
        throws UnsupportedEncodingException, IOException {
        return new String(get(), charset);
    }

    /**
     * Returns the contents of the file as a String, using the default
     * character encoding.  This method uses {@link #get()} to retrieve the
     * contents of the file.
     *
     * <b>TODO</b> Consider making this method throw UnsupportedEncodingException.
     *
     * @return The contents of the file, as a string.
     */
    @Override
    public String getString() {
        try {
            final byte[] rawData = get();
            String charset = getCharSet();
            if (charset == null) {
                charset = defaultCharset;
            }
            return new String(rawData, charset);
        } catch (final IOException e) {
            return "";
        }
    }

    /**
     * A convenience method to write an uploaded item to disk. The client code
     * is not concerned with whether or not the item is stored in memory, or on
     * disk in a temporary location. They just want to write the uploaded item
     * to a file.
     * <p>
     * This implementation first attempts to rename the uploaded item to the
     * specified destination file, if the item was originally written to disk.
     * Otherwise, the data will be copied to the specified file.
     * <p>
     * This method is only guaranteed to work <em>once</em>, the first time it
     * is invoked for a particular item. This is because, in the event that the
     * method renames a temporary file, that file will no longer be available
     * to copy or rename again at a later time.
     *
     * @param file The {@code File} into which the uploaded item should
     *             be stored.
     *
     * @throws Exception if an error occurs.
     */
    @Override
    public void write(final File file) throws Exception {
        if (isInMemory()) {
            try (OutputStream fout = Files.newOutputStream(file.toPath())) {
                fout.write(get());
            }
        } else {
            final File outputFile = getStoreLocation();
            if (outputFile == null) {
                /*
                 * For whatever reason we cannot write the
                 * file to disk.
                 */
                throw new FileUploadException(
                    "Cannot write uploaded file to disk!");
            }
            // Save the length of the file
            size = outputFile.length();
            /*
             * The uploaded file is being stored on disk
             * in a temporary location so move it to the
             * desired file.
             */
            if (file.exists() && !file.delete()) {
                throw new FileUploadException("Cannot write uploaded file to disk!");
            }
            if (!outputFile.renameTo(file)) {
                BufferedInputStream in = null;
                BufferedOutputStream out = null;
                try {
                    in = new BufferedInputStream(new FileInputStream(outputFile));
                    out = new BufferedOutputStream(new FileOutputStream(file));
                    IOUtils.copy(in, out);
                    out.close();
                } finally {
                    IOUtils.closeQuietly(in);
                    IOUtils.closeQuietly(out);
                }
            }
        }
    }

    /**
     * Deletes the underlying storage for a file item, including deleting any associated temporary disk file.
     * This method can be used to ensure that this is done at an earlier time, thus preserving system resources.
     */
    @Override
    public void delete() {
        cachedContent = null;
        final File outputFile = getStoreLocation();
        if (outputFile != null && !isInMemory() && outputFile.exists()) {
            if (!outputFile.delete()) {
                final String desc = "Cannot delete " + outputFile.toString();
                throw new UncheckedIOException(desc, new IOException(desc));
            }
        }
    }

    /**
     * Returns the name of the field in the multipart form corresponding to
     * this file item.
     *
     * @return The name of the form field.
     *
     * @see #setFieldName(String)
     *
     */
    @Override
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Sets the field name used to reference this file item.
     *
     * @param fieldName The name of the form field.
     *
     * @see #getFieldName()
     *
     */
    @Override
    public void setFieldName(final String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * Determines whether or not a {@code FileItem} instance represents
     * a simple form field.
     *
     * @return {@code true} if the instance represents a simple form
     *         field; {@code false} if it represents an uploaded file.
     *
     * @see #setFormField(boolean)
     *
     */
    @Override
    public boolean isFormField() {
        return isFormField;
    }

    /**
     * Specifies whether or not a {@code FileItem} instance represents
     * a simple form field.
     *
     * @param state {@code true} if the instance represents a simple form
     *              field; {@code false} if it represents an uploaded file.
     *
     * @see #isFormField()
     *
     */
    @Override
    public void setFormField(final boolean state) {
        isFormField = state;
    }

    /**
     * Returns an {@link java.io.OutputStream OutputStream} that can
     * be used for storing the contents of the file.
     *
     * @return An {@link java.io.OutputStream OutputStream} that can be used
     *         for storing the contents of the file.
     *
     */
    @Override
    public OutputStream getOutputStream() {
        if (dfos == null) {
            final File outputFile = getTempFile();
            dfos = new DeferredFileOutputStream(sizeThreshold, outputFile);
        }
        return dfos;
    }

    // --------------------------------------------------------- Public methods

    /**
     * Returns the {@link java.io.File} object for the {@code FileItem}'s
     * data's temporary location on the disk. Note that for
     * {@code FileItem}s that have their data stored in memory,
     * this method will return {@code null}. When handling large
     * files, you can use {@link java.io.File#renameTo(java.io.File)} to
     * move the file to new location without copying the data, if the
     * source and destination locations reside within the same logical
     * volume.
     *
     * @return The data file, or {@code null} if the data is stored in
     *         memory.
     */
    public File getStoreLocation() {
        if (dfos == null) {
            return null;
        }
        if (isInMemory()) {
            return null;
        }
        return dfos.getFile();
    }

    // ------------------------------------------------------ Protected methods

    /**
     * Creates and returns a {@link java.io.File File} representing a uniquely
     * named temporary file in the configured repository path. The lifetime of
     * the file is tied to the lifetime of the {@code FileItem} instance;
     * the file will be deleted when the instance is garbage collected.
     * <p>
     * <b>Note: Subclasses that override this method must ensure that they return the
     * same File each time.</b>
     *
     * @return The {@link java.io.File File} to be used for temporary storage.
     */
    protected File getTempFile() {
        if (tempFile == null) {
            File tempDir = repository;
            if (tempDir == null) {
                tempDir = new File(System.getProperty("java.io.tmpdir"));
            }

            final String tempFileName = String.format("upload_%s_%s.tmp", UID, getUniqueId());

            tempFile = new File(tempDir, tempFileName);
        }
        return tempFile;
    }

    // -------------------------------------------------------- Private methods

    /**
     * Returns an identifier that is unique within the class loader used to
     * load this class, but does not have random-like appearance.
     *
     * @return A String with the non-random looking instance identifier.
     */
    private static String getUniqueId() {
        final int limit = 100000000;
        final int current = COUNTER.getAndIncrement();
        String id = Integer.toString(current);

        // If you manage to get more than 100 million of ids, you'll
        // start getting ids longer than 8 characters.
        if (current < limit) {
            id = ("00000000" + id).substring(id.length());
        }
        return id;
    }

    /**
     * Returns a string representation of this object.
     *
     * @return a string representation of this object.
     */
    @Override
    public String toString() {
        return String.format("name=%s, StoreLocation=%s, size=%s bytes, isFormField=%s, FieldName=%s",
                getName(), getStoreLocation(), Long.valueOf(getSize()), Boolean.valueOf(isFormField()), getFieldName());
    }

    /**
     * Returns the file item headers.
     * @return The file items headers.
     */
    @Override
    public FileItemHeaders getHeaders() {
        return headers;
    }

    /**
     * Sets the file item headers.
     * @param pHeaders The file items headers.
     */
    @Override
    public void setHeaders(final FileItemHeaders pHeaders) {
        headers = pHeaders;
    }

    /**
     * Returns the default charset for use when no explicit charset
     * parameter is provided by the sender.
     * @return the default charset
     */
    public String getDefaultCharset() {
        return defaultCharset;
    }

    /**
     * Sets the default charset for use when no explicit charset
     * parameter is provided by the sender.
     * @param charset the default charset
     */
    public void setDefaultCharset(final String charset) {
        defaultCharset = charset;
    }
}
