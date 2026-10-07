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
package org.apache.tomcat.manager2;


/**
 * Common constants for the Manager2 API.
 */
public final class Constants {


    /**
     * The name of this package. Used to obtain the StringManager instance.
     */
    public static final String Package = "org.apache.tomcat.manager2";


    /**
     * The character set used for all JSON responses.
     */
    public static final String CHARSET = "UTF-8";


    /**
     * The name of the request and response header that carries the CSRF token.
     */
    public static final String CSRF_HEADER = "X-CSRF-Token";


    /**
     * The session attribute that holds the current CSRF token.
     */
    public static final String CSRF_TOKEN_SESSION_KEY = "org.apache.tomcat.manager2.CsrfFilter.token";


    private Constants() {
        // Utility class, do not instantiate
    }
}
