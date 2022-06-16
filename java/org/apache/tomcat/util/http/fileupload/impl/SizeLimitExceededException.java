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
 * Thrown to indicate that the request size exceeds the configured maximum.
 */
public class SizeLimitExceededException
        extends SizeException {

    /**
     * The exceptions UID, for serializing an instance.
     */
    private static final long serialVersionUID = -2474893167098052828L;

    /**
     * Constructs a {@code SizeExceededException} with
     * the specified detail message, and actual and permitted sizes.
     *
     * @param message   The detail message.
     * @param actual    The actual request size.
     * @param permitted The maximum permitted request size.
     */
    public SizeLimitExceededException(final String message, final long actual,
            final long permitted) {
        super(message, actual, permitted);
    }

}