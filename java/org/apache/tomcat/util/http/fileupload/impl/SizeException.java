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
 * This exception is thrown, if a requests permitted size
 * is exceeded.
 */
public abstract class SizeException extends FileUploadException {

    /**
     * Serial version UID, being used, if serialized.
     */
    private static final long serialVersionUID = -8776225574705254126L;

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
     *
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
     * @since 1.3
     */
    public long getActualSize() {
        return actual;
    }

    /**
     * Retrieves the permitted size of the request.
     *
     * @return The permitted size of the request.
     * @since 1.3
     */
    public long getPermittedSize() {
        return permitted;
    }

}