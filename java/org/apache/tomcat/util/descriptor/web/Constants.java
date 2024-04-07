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
package org.apache.tomcat.util.descriptor.web;

public class Constants {

    public static final String PACKAGE_NAME =
            Constants.class.getPackage().getName();

    public static final String WEB_XML_LOCATION = "/WEB-INF/web.xml";

    // -------------------------------------------------- Cookie attribute names
    public static final String COOKIE_COMMENT_ATTR = "Comment";
    public static final String COOKIE_DOMAIN_ATTR = "Domain";
    public static final String COOKIE_MAX_AGE_ATTR = "Max-Age";
    public static final String COOKIE_PATH_ATTR = "Path";
    public static final String COOKIE_SECURE_ATTR = "Secure";
    public static final String COOKIE_HTTP_ONLY_ATTR = "HttpOnly";
    public static final String COOKIE_SAME_SITE_ATTR = "SameSite";
    /**
     * The name of the attribute used to indicate a partitioned cookie as part of
     * <a href="https://developers.google.com/privacy-sandbox/3pcd#partitioned">CHIPS</a>. This cookie attribute is not
     * defined by an RFC and may change in a non-backwards compatible way once equivalent functionality is included in
     * an RFC.
     */
    public static final String COOKIE_PARTITIONED_ATTR = "Partitioned";
}
