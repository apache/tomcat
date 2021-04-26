/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package jakarta.servlet.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * This class represents a part as uploaded to the server as part of a
 * <code>multipart/form-data</code> request body. The part may represent either
 * an uploaded file or form data.
 *
 * @since Servlet 3.0
 */
public interface Part {

    /**
     * Obtain an <code>InputStream</code> that can be used to retrieve the
     * contents of the file.
     *
     * @return An InputStream for the contents of the file
     *
     * @throws IOException if an I/O occurs while obtaining the stream
     */
    public InputStream getInputStream() throws IOException;

    /**
     * Obtain the content type passed by the browser.
     *
     * @return The content type passed by the browser or <code>null</code> if
     *         not defined.
     */
    public String getContentType();

    /**
     * Obtain the name of the field in the multipart form corresponding to this
     * part.
     *
     * @return The name of the field in the multipart form corresponding to this
     *         part.
     */
    public String getName();

    /**
     * If this part represents an uploaded file, gets the file name submitted
     * in the upload. Returns {@code null} if no file name is available or if
     * this part is not a file upload.
     *
     * @return the submitted file name or {@code null}.
     *
     * @since Servlet 3.1
     */
    public String getSubmittedFileName();

    /**
     * Obtain the size of this part.
     *
     * @return The size of the part if bytes
     */
    public long getSize();

    /**
     * A convenience method to write an uploaded part to disk. The client code
     * is not concerned with whether or not the part is stored in memory, or on
     * disk in a temporary location. They just want to write the uploaded part
     * to a file.
     *
     *  This method is not guaranteed to succeed if called more than once for
     *  the same part. This allows a particular implementation to use, for
     *  example, file renaming, where possible, rather than copying all of the
     *  underlying data, thus gaining a significant performance benefit.
     *
     * @param fileName  The location into which the uploaded part should be
     *                  stored. Relative locations are relative to {@link
     *                  jakarta.servlet.MultipartConfigElement#getLocation()}
     *
     * @throws IOException if an I/O occurs while attempting to write the part
     */
    public void write(String fileName) throws IOException;

    /**
     * Deletes the underlying storage for a part, including deleting any
     * associated temporary disk file. Although the container will delete this
     * storage automatically this method can be used to ensure that this is done
     * at an earlier time, thus preserving system resources.
     * <p>
     * Containers are only required to delete the associated storage when the
     * Part instance is garbage collected. Apache Tomcat will delete the
     * associated storage when the associated request has finished processing.
     * Behaviour of other containers may be different.
     *
     * @throws IOException if an I/O occurs while attempting to delete the part
     */
    public void delete() throws IOException;

    /**
     * Obtains the value of the specified part header as a String. If there are
     * multiple headers with the same name, this method returns the first header
     * in the part. The header name is case insensitive.
     *
     * @param name  Header name
     * @return      The header value or <code>null</code> if the header is not
     *              present
     */
    public String getHeader(String name);

    /**
     * Obtain all the values of the specified part header.
     * @param name The name of the header of interest. The header name is case
     *             insensitive.
     * @return All the values of the specified part header. If the part did not
     *         include any headers of the specified name, this method returns an
     *         empty Collection.
     */
    public Collection<String> getHeaders(String name);

    /**
     * Get the header names provided for this part.
     * @return a Collection of all the header names provided for this part.
     */
    public Collection<String> getHeaderNames();
}
