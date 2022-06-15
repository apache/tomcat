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
package jakarta.servlet;

import jakarta.servlet.annotation.MultipartConfig;

/**
 * The programmatic equivalent of
 * {@link jakarta.servlet.annotation.MultipartConfig} used to configure
 * multi-part handling for a Servlet when registering a Servlet via code.
 *
 * @since Servlet 3.0
 */
public class MultipartConfigElement {

    private final String location;// = "";
    private final long maxFileSize;// = -1;
    private final long maxRequestSize;// = -1;
    private final int fileSizeThreshold;// = 0;

    /**
     * Create a programmatic multi-part configuration with a specific location
     * and defaults for the remaining configuration elements.
     *
     * @param location          The temporary location to store files
     */
    public MultipartConfigElement(String location) {
        // Keep empty string default if location is null
        if (location != null) {
            this.location = location;
        } else {
            this.location = "";
        }
        this.maxFileSize = -1;
        this.maxRequestSize = -1;
        this.fileSizeThreshold = 0;
    }

    /**
     * Create a programmatic multi-part configuration from the individual
     * configuration elements.
     *
     * @param location          The temporary location to store files
     * @param maxFileSize       The maximum permitted size for a single file
     * @param maxRequestSize    The maximum permitted size for a request
     * @param fileSizeThreshold The size above which the file is save in the
     *                              temporary location rather than retained in
     *                              memory.
     */
    public MultipartConfigElement(String location, long maxFileSize,
            long maxRequestSize, int fileSizeThreshold) {
        // Keep empty string default if location is null
        if (location != null) {
            this.location = location;
        } else {
            this.location = "";
        }
        this.maxFileSize = maxFileSize;
        this.maxRequestSize = maxRequestSize;
        // Avoid threshold values of less than zero as they cause trigger NPEs
        // in the Commons FileUpload port for fields that have no data.
        if (fileSizeThreshold > 0) {
            this.fileSizeThreshold = fileSizeThreshold;
        } else {
            this.fileSizeThreshold = 0;
        }
    }

    /**
     * Create a programmatic configuration from an annotation.
     *
     * @param annotation The source annotation to copy to create the
     *                   programmatic equivalent.
     */
    public MultipartConfigElement(MultipartConfig annotation) {
        location = annotation.location();
        maxFileSize = annotation.maxFileSize();
        maxRequestSize = annotation.maxRequestSize();
        fileSizeThreshold = annotation.fileSizeThreshold();
    }

    /**
     * Obtain the location where temporary files should be stored.
     *
     * @return the location where temporary files should be stored.
     */
    public String getLocation() {
        return location;
    }

    /**
     * Obtain the maximum permitted size for a single file.
     *
     * @return the maximum permitted size for a single file.
     */
    public long getMaxFileSize() {
        return maxFileSize;
    }

    /**
     * Obtain the maximum permitted size for a single request.
     *
     * @return the maximum permitted size for a single request.
     */
    public long getMaxRequestSize() {
        return maxRequestSize;
    }

    /**
     * Obtain the size above which the file is save in the temporary location
     * rather than retained in memory.
     *
     * @return the size above which the file is save in the temporary location
     * rather than retained in memory.
     */
    public int getFileSizeThreshold() {
        return fileSizeThreshold;
    }
}