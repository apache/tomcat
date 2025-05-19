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

import jakarta.servlet.http.HttpServletRequest;

/**
 * Factory for {@link ProgressListener}. Users can specify this via an init parameter to a filter
 * or a servlet. With parameter name <em>fileUploadProgressListenerFactory</em>
 */
public interface ProgressListenerFactory {

    /**
     * Constant for the servlet init parameter that can be used to specify a factory for ProgressListeners
     */
    String FACTORY_NAME = "fileUploadProgressListenerFactory";


    /**
     * Creaste a new {@link ProgressListener} for the current multipart request.
     *
     * @param servletRequest The {@link HttpServletRequest}
     *
     * @return returns a new {@link ProgressListener} for current request.
     */
    ProgressListener newProgressListener(HttpServletRequest servletRequest);

}
