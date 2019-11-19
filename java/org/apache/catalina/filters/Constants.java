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
package org.apache.catalina.filters;


/**
 * Manifest constants for this Java package.
 *
 *
 * @author Craig R. McClanahan
 */
public final class Constants {

    /**
     * The session attribute key under which the CSRF nonce
     * cache will be stored.
     */
    public static final String CSRF_NONCE_SESSION_ATTR_NAME =
        "org.apache.catalina.filters.CSRF_NONCE";

    /**
     * The request attribute key under which the current
     * requests's CSRF nonce can be found.
     */
    public static final String CSRF_NONCE_REQUEST_ATTR_NAME =
        "org.apache.catalina.filters.CSRF_REQUEST_NONCE";

    /**
     * The name of the request parameter which carries CSRF nonces
     * from the client to the server for validation.
     */
    public static final String CSRF_NONCE_REQUEST_PARAM =
        "org.apache.catalina.filters.CSRF_NONCE";

    /**
     * The servlet context attribute key under which the
     * CSRF request parameter name can be found.
     */
    public static final String CSRF_NONCE_REQUEST_PARAM_NAME_KEY =
        "org.apache.catalina.filters.CSRF_NONCE_PARAM_NAME";

    public static final String METHOD_GET = "GET";

    public static final String CSRF_REST_NONCE_HEADER_NAME = "X-CSRF-Token";

    public static final String CSRF_REST_NONCE_HEADER_FETCH_VALUE = "Fetch";

    public static final String CSRF_REST_NONCE_HEADER_REQUIRED_VALUE = "Required";

    /**
     * The session attribute key under which the CSRF REST nonce
     * cache will be stored.
     */
    public static final String CSRF_REST_NONCE_SESSION_ATTR_NAME =
        "org.apache.catalina.filters.CSRF_REST_NONCE";

    /**
     * The servlet context attribute key under which the
     * CSRF REST header name can be found.
     */
    public static final String CSRF_REST_NONCE_HEADER_NAME_KEY =
        "org.apache.catalina.filters.CSRF_REST_NONCE_HEADER_NAME";
}
