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

import org.apache.tomcat.util.http.fileupload.FileUploadException;

/**
 * Thrown to indicate an IOException.
 */
public class IOFileUploadException extends FileUploadException {

    /**
     * The exceptions UID, for serializing an instance.
     */
    private static final long serialVersionUID = 1749796615868477269L;

    /**
     * The exceptions cause; we overwrite the parent
     * classes field, which is available since Java
     * 1.4 only.
     */
    private final IOException cause;

    /**
     * Creates a new instance with the given cause.
     *
     * @param pMsg The detail message.
     * @param pException The exceptions cause.
     */
    public IOFileUploadException(String pMsg, IOException pException) {
        super(pMsg);
        cause = pException;
    }

    /**
     * Returns the exceptions cause.
     *
     * @return The exceptions cause, if any, or null.
     */
    @SuppressWarnings("sync-override") // Field is final
    @Override
    public Throwable getCause() {
        return cause;
    }

}