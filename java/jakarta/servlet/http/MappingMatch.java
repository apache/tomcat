/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.servlet.http;

/**
 * Represents the ways that a request can be mapped to a servlet
 *
 * @since Servlet 4.0
 */
public enum MappingMatch {

    /**
     * The request was mapped to the servlet via the context root URL pattern of {@code ""}.
     */
    CONTEXT_ROOT,

    /**
     * The request was mapped to the servlet via the default servlet URL pattern of {@code "/"} .
     */
    DEFAULT,

    /**
     * The request was mapped to the servlet using an exact URL pattern match.
     */
    EXACT,

    /**
     * The request was mapped to the servlet using an extension URL pattern match.
     */
    EXTENSION,

    /**
     * The request was mapped to the servlet using a path URL pattern.
     */
    PATH
}
