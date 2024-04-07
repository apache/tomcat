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
package org.apache.catalina.authenticator;

public class Constants {
    // Authentication methods for login configuration
    // Servlet spec schemes are defined in HttpServletRequest
    // Vendor specific schemes
    public static final String SPNEGO_METHOD = "SPNEGO";

    // Form based authentication constants
    public static final String FORM_ACTION = "/j_security_check";
    public static final String FORM_PASSWORD = "j_password";
    public static final String FORM_USERNAME = "j_username";

    // SPNEGO authentication constants
    public static final String KRB5_CONF_PROPERTY = "java.security.krb5.conf";
    public static final String DEFAULT_KRB5_CONF = "conf/krb5.ini";
    public static final String JAAS_CONF_PROPERTY = "java.security.auth.login.config";
    public static final String DEFAULT_JAAS_CONF = "conf/jaas.conf";
    public static final String DEFAULT_LOGIN_MODULE_NAME = "com.sun.security.jgss.krb5.accept";

    // Cookie name for single sign on support
    public static final String SINGLE_SIGN_ON_COOKIE = "JSESSIONIDSSO";

    /**
     * The name of the attribute used to indicate a partitioned cookie as part of
     * <a href="https://developers.google.com/privacy-sandbox/3pcd#partitioned">CHIPS</a>. This cookie attribute is not
     * defined by an RFC and may change in a non-backwards compatible way once equivalent functionality is included in
     * an RFC.
     */
    public static final String COOKIE_PARTITIONED_ATTR =
            org.apache.tomcat.util.descriptor.web.Constants.COOKIE_PARTITIONED_ATTR;


    // --------------------------------------------------------- Request Notes

    /**
     * The notes key to track the single-sign-on identity with which this request is associated.
     */
    public static final String REQ_SSOID_NOTE = "org.apache.catalina.request.SSOID";

    public static final String REQ_JASPIC_SUBJECT_NOTE = "org.apache.catalina.authenticator.jaspic.SUBJECT";


    // ---------------------------------------------------------- Session Notes

    /**
     * The session id used as a CSRF marker when redirecting a user's request.
     */
    public static final String SESSION_ID_NOTE = "org.apache.catalina.authenticator.SESSION_ID";


    /**
     * If the <code>cache</code> property of the authenticator is set, and the current request is part of a session, the
     * password used to authenticate this user will be cached under this key to avoid the need for repeated calls to
     * <code>Realm.authenticate()</code>.
     */
    public static final String SESS_PASSWORD_NOTE = "org.apache.catalina.session.PASSWORD";

    /**
     * If the <code>cache</code> property of the authenticator is set, and the current request is part of a session, the
     * user name used to authenticate this user will be cached under this key to avoid the need for repeated calls to
     * <code>Realm.authenticate()</code>.
     */
    public static final String SESS_USERNAME_NOTE = "org.apache.catalina.session.USERNAME";


    /**
     * The original request information, to which the user will be redirected if authentication succeeds, is cached in
     * the notes under this key during the authentication process.
     */
    public static final String FORM_REQUEST_NOTE = "org.apache.catalina.authenticator.REQUEST";
}
