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

import org.apache.tomcat.util.http.fileupload.FileUploadException;

/**
 * This exception is thrown if a request contains more files than the specified
 * limit.
 */
public class FileCountLimitExceededException extends FileUploadException {

    private static final long serialVersionUID = 2408766352570556046L;

    private final long limit;

    /**
     * Creates a new instance.
     *
     * @param message The detail message
     * @param limit The limit that was exceeded
     */
    public FileCountLimitExceededException(final String message, final long limit) {
        super(message);
        this.limit = limit;
    }

    /**
     * Retrieves the limit that was exceeded.
     *
     * @return The limit that was exceeded by the request
     */
    public long getLimit() {
        return limit;
    }
}
