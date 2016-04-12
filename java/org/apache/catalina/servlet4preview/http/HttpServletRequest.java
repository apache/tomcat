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
package org.apache.catalina.servlet4preview.http;

/**
 * Provides early access to some parts of the proposed Servlet 4.0 API.
 */
public interface HttpServletRequest extends javax.servlet.http.HttpServletRequest {

    public Mapping getMapping();

    /**
     * Does the current request allow push requests. This will return {@code
     * true} only if the underlying protocol supports server push and if pushes
     * are permitted from the current request.
     *
     * @return {@code true} if server push is supported for the current request
     *         otherwise {@code false}
     */
    public boolean isPushSupported();

    /**
     * Obtain a builder for generating push requests. {@link PushBuilder}
     * documents how this request will be used as the basis for a push request.
     * Each call to this method will return a new instance, independent of any
     * previous instance obtained.
     *
     * @return A builder that can be used to generate push requests based on
     *         this request.
     *
     * @since Servlet 4.0
     */
    public PushBuilder getPushBuilder();
}
