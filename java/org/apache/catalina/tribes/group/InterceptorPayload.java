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
package org.apache.catalina.tribes.group;

import org.apache.catalina.tribes.ErrorHandler;

/**
 * Payload object used to pass an {@link ErrorHandler} to interceptors.
 */
public class InterceptorPayload {
    private ErrorHandler errorHandler;

    /**
     * Constructs a new InterceptorPayload.
     */
    public InterceptorPayload() {
    }

    /**
     * Get the error handler.
     *
     * @return the error handler
     */
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    /**
     * Set the error handler.
     *
     * @param errorHandler the error handler to set
     */
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }
}