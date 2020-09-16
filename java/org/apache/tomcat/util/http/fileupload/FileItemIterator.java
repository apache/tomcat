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
import java.util.List;

import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;

/**
 * An iterator, as returned by
 * {@link FileUploadBase#getItemIterator(RequestContext)}.
 */
public interface FileItemIterator {
    /** Returns the maximum size of a single file. An {@link FileSizeLimitExceededException}
     * will be thrown, if there is an uploaded file, which is exceeding this value.
     * By default, this value will be copied from the {@link FileUploadBase#getFileSizeMax()
     * FileUploadBase} object, however, the user may replace the default value with a
     * request specific value by invoking {@link #setFileSizeMax(long)} on this object.
     * @return The maximum size of a single, uploaded file. The value -1 indicates "unlimited".
     */
    public long getFileSizeMax();

    /** Sets the maximum size of a single file. An {@link FileSizeLimitExceededException}
     * will be thrown, if there is an uploaded file, which is exceeding this value.
     * By default, this value will be copied from the {@link FileUploadBase#getFileSizeMax()
     * FileUploadBase} object, however, the user may replace the default value with a
     * request specific value by invoking {@link #setFileSizeMax(long)} on this object, so
     * there is no need to configure it here.
     * <em>Note:</em>Changing this value doesn't affect files, that have already been uploaded.
     * @param pFileSizeMax The maximum size of a single, uploaded file. The value -1 indicates "unlimited".
     */
    public void setFileSizeMax(long pFileSizeMax);

    /** Returns the maximum size of the complete HTTP request. A {@link SizeLimitExceededException}
     * will be thrown, if the HTTP request will exceed this value.
     * By default, this value will be copied from the {@link FileUploadBase#getSizeMax()
     * FileUploadBase} object, however, the user may replace the default value with a
     * request specific value by invoking {@link #setSizeMax(long)} on this object.
     * @return The maximum size of the complete HTTP request. The value -1 indicates "unlimited".
     */
    public long getSizeMax();

    /** Returns the maximum size of the complete HTTP request. A {@link SizeLimitExceededException}
     * will be thrown, if the HTTP request will exceed this value.
     * By default, this value will be copied from the {@link FileUploadBase#getSizeMax()
     * FileUploadBase} object, however, the user may replace the default value with a
     * request specific value by invoking {@link #setSizeMax(long)} on this object.
     * <em>Note:</em> Setting the maximum size on this object will work only, if the iterator is not
     * yet initialized. In other words: If the methods {@link #hasNext()}, {@link #next()} have not
     * yet been invoked.
     * @param pSizeMax The maximum size of the complete HTTP request. The value -1 indicates "unlimited".
     */
    public void setSizeMax(long pSizeMax);

    /**
     * Returns, whether another instance of {@link FileItemStream}
     * is available.
     *
     * @throws FileUploadException Parsing or processing the
     *   file item failed.
     * @throws IOException Reading the file item failed.
     * @return True, if one or more additional file items
     *   are available, otherwise false.
     */
    public boolean hasNext() throws FileUploadException, IOException;

    /**
     * Returns the next available {@link FileItemStream}.
     *
     * @throws java.util.NoSuchElementException No more items are available. Use
     * {@link #hasNext()} to prevent this exception.
     * @throws FileUploadException Parsing or processing the
     *   file item failed.
     * @throws IOException Reading the file item failed.
     * @return FileItemStream instance, which provides
     *   access to the next file item.
     */
    public FileItemStream next() throws FileUploadException, IOException;

    public List<FileItem> getFileItems() throws FileUploadException, IOException;
}
