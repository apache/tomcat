/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote;

/**
 * Enum defining policies on responding requests that contain a '100-continue'
 * expectations.
 */
public enum ContinueHandlingResponsePolicy {
    /**
     * Tomcat will automatically send the 100 intermediate response before
     * sending the request to the servlet
     *
     * This is the default behavior
     */
    IMMEDIATELY,

    /**
     * Send the 100 intermediate response only when the servlet attempts to
     * read the request's body by either:
     * - calling read on the InputStream returned by
     *   HttpServletRequest.getInputStream
     * - calling read on the BufferedReader returned by
     *   HttpServletRequest.getReader
     *
     * This allows the servlet to process the request headers and possibly
     * respond before reading the request body
     */
    ON_REQUEST_BODY_READ
}