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

/**
 * Exception for errors encountered while processing the request.
 */
public class FileUploadException extends IOException {

    private static final long serialVersionUID = -4222909057964038517L;

    /**
     * Constructs a new {@code FileUploadException} without message.
     */
    public FileUploadException() {
        super();
    }

    /**
     * Constructs a new {@code FileUploadException} with specified detail
     * message.
     *
     * @param msg the error message.
     */
    public FileUploadException(final String msg) {
        super(msg);
    }

    /**
     * Creates a new {@code FileUploadException} with the given
     * detail message and cause.
     *
     * @param msg The exceptions detail message.
     * @param cause The exceptions cause.
     */
    public FileUploadException(final String msg, final Throwable cause) {
        super(msg, cause);
    }

    /**
     * Creates a new {@code FileUploadException} with the given
     * cause.
     *
     * @param cause The exceptions cause.
     */
    public FileUploadException(final Throwable cause) {
        super(cause);
    }
}
