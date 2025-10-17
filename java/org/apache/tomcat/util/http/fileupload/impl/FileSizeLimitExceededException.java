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

/**
 * Thrown to indicate that A files size exceeds the configured maximum.
 */
public class FileSizeLimitExceededException
        extends SizeException {

    /**
     * The exceptions UID, for serializing an instance.
     */
    private static final long serialVersionUID = 8150776562029630058L;

    /**
     * File name of the item, which caused the exception.
     */
    private String fileName;

    /**
     * Field name of the item, which caused the exception.
     */
    private String fieldName;

    /**
     * Constructs a {@code SizeExceededException} with
     * the specified detail message, and actual and permitted sizes.
     *
     * @param message   The detail message.
     * @param actual    The actual request size.
     * @param permitted The maximum permitted request size.
     */
    public FileSizeLimitExceededException(final String message, final long actual,
            final long permitted) {
        super(message, actual, permitted);
    }

    /**
     * Returns the file name of the item, which caused the
     * exception.
     *
     * @return File name, if known, or null.
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the file name of the item, which caused the
     * exception.
     *
     * @param pFileName the file name of the item, which caused the exception.
     */
    public void setFileName(final String pFileName) {
        fileName = pFileName;
    }

    /**
     * Returns the field name of the item, which caused the
     * exception.
     *
     * @return Field name, if known, or null.
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Sets the field name of the item, which caused the
     * exception.
     *
     * @param pFieldName the field name of the item,
     *        which caused the exception.
     */
    public void setFieldName(final String pFieldName) {
        fieldName = pFieldName;
    }

}