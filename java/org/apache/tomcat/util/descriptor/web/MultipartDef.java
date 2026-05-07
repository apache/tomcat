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
package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;


/**
 * Representation of the multipart configuration for a servlet.
 */
public class MultipartDef implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     */
    public MultipartDef() {
    }

    // ------------------------------------------------------------- Properties
    private String location;

    /**
     * Get the location.
     * @return the location
     */
    public String getLocation() {
        return location;
    }

    /**
     * Set the location.
     * @param location the location
     */
    public void setLocation(String location) {
        this.location = location;
    }


    private String maxFileSize;

    /**
     * Get the maximum file size.
     * @return the maximum file size
     */
    public String getMaxFileSize() {
        return maxFileSize;
    }

    /**
     * Set the maximum file size.
     * @param maxFileSize the maximum file size
     */
    public void setMaxFileSize(String maxFileSize) {
        this.maxFileSize = maxFileSize;
    }


    private String maxRequestSize;

    /**
     * Get the maximum request size.
     * @return the maximum request size
     */
    public String getMaxRequestSize() {
        return maxRequestSize;
    }

    /**
     * Set the maximum request size.
     * @param maxRequestSize the maximum request size
     */
    public void setMaxRequestSize(String maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }


    private String fileSizeThreshold;

    /**
     * Get the file size threshold.
     * @return the file size threshold
     */
    public String getFileSizeThreshold() {
        return fileSizeThreshold;
    }

    /**
     * Set the file size threshold.
     * @param fileSizeThreshold the file size threshold
     */
    public void setFileSizeThreshold(String fileSizeThreshold) {
        this.fileSizeThreshold = fileSizeThreshold;
    }


    // ---------------------------------------------------------- Object methods

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fileSizeThreshold == null) ? 0 : fileSizeThreshold.hashCode());
        result = prime * result + ((location == null) ? 0 : location.hashCode());
        result = prime * result + ((maxFileSize == null) ? 0 : maxFileSize.hashCode());
        result = prime * result + ((maxRequestSize == null) ? 0 : maxRequestSize.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof MultipartDef)) {
            return false;
        }
        MultipartDef other = (MultipartDef) obj;
        if (fileSizeThreshold == null) {
            if (other.fileSizeThreshold != null) {
                return false;
            }
        } else if (!fileSizeThreshold.equals(other.fileSizeThreshold)) {
            return false;
        }
        if (location == null) {
            if (other.location != null) {
                return false;
            }
        } else if (!location.equals(other.location)) {
            return false;
        }
        if (maxFileSize == null) {
            if (other.maxFileSize != null) {
                return false;
            }
        } else if (!maxFileSize.equals(other.maxFileSize)) {
            return false;
        }
        if (maxRequestSize == null) {
            return other.maxRequestSize == null;
        } else {
            return maxRequestSize.equals(other.maxRequestSize);
        }
    }

}
