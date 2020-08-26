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
 * Thrown to indicate that the request is not a multipart request.
 */
public class InvalidContentTypeException
        extends FileUploadException {

    /**
     * The exceptions UID, for serializing an instance.
     */
    private static final long serialVersionUID = -9073026332015646668L;

    /**
     * Constructs a {@code InvalidContentTypeException} with no
     * detail message.
     */
    public InvalidContentTypeException() {
        super();
    }

    /**
     * Constructs an {@code InvalidContentTypeException} with
     * the specified detail message.
     *
     * @param message The detail message.
     */
    public InvalidContentTypeException(String message) {
        super(message);
    }

    /**
     * Constructs an {@code InvalidContentTypeException} with
     * the specified detail message and cause.
     *
     * @param msg The detail message.
     * @param cause the original cause
     *
     * @since 1.3.1
     */
    public InvalidContentTypeException(String msg, Throwable cause) {
        super(msg, cause);
    }
}