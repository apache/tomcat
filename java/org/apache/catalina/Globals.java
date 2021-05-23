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
package org.apache.catalina;

/**
 * Global constants that are applicable to multiple packages within Catalina.
 *
 * @author Craig R. McClanahan
 */
public final class Globals {

    // ------------------------------------------------- Request attribute names

    public static final String ASYNC_SUPPORTED_ATTR = "org.apache.catalina.ASYNC_SUPPORTED";


    public static final String GSS_CREDENTIAL_ATTR = "org.apache.catalina.realm.GSS_CREDENTIAL";


    /**
     * Request dispatcher state.
     */
    public static final String DISPATCHER_TYPE_ATTR = "org.apache.catalina.core.DISPATCHER_TYPE";


    /**
     * Request dispatcher path.
     */
    public static final String DISPATCHER_REQUEST_PATH_ATTR = "org.apache.catalina.core.DISPATCHER_REQUEST_PATH";


    /**
     * The request attribute under which we store the servlet name on a
     * named dispatcher request.
     */
    public static final String NAMED_DISPATCHER_ATTR = "org.apache.catalina.NAMED";


    /**
     * The request attribute used to expose the current connection ID associated
     * with the request, if any. Used with multiplexing protocols such as
     * HTTTP/2.
     */
    public static final String CONNECTION_ID = "org.apache.coyote.connectionID";


    /**
     * The request attribute used to expose the current stream ID associated
     * with the request, if any. Used with multiplexing protocols such as
     * HTTTP/2.
     */
    public static final String STREAM_ID = "org.apache.coyote.streamID";


    /**
     * The request attribute that is set to {@code Boolean.TRUE} if some request
     * parameters have been ignored during request parameters parsing. It can
     * happen, for example, if there is a limit on the total count of parseable
     * parameters, or if parameter cannot be decoded, or any other error
     * happened during parameter parsing.
     */
    public static final String PARAMETER_PARSE_FAILED_ATTR = "org.apache.catalina.parameter_parse_failed";


    /**
     * The reason that the parameter parsing failed.
     */
    public static final String PARAMETER_PARSE_FAILED_REASON_ATTR = "org.apache.catalina.parameter_parse_failed_reason";


    /**
     * The request attribute set by the RemoteIpFilter, RemoteIpValve (and may
     * be set by other similar components) that identifies for the connector the
     * remote IP address claimed to be associated with this request when a
     * request is received via one or more proxies. It is typically provided via
     * the X-Forwarded-For HTTP header.
     *
     * Duplicated here for neater code in the catalina packages.
     */
    public static final String REMOTE_ADDR_ATTRIBUTE = org.apache.coyote.Constants.REMOTE_ADDR_ATTRIBUTE;


    /**
     * The request attribute that is set to the value of {@code Boolean.TRUE}
     * by the RemoteIpFilter, RemoteIpValve (and other similar components) that identifies
     * a request which been forwarded via one or more proxies.
     */
    public static final String REQUEST_FORWARDED_ATTRIBUTE = "org.apache.tomcat.request.forwarded";


    /**
     * The request attribute that is set to the value of {@code Boolean.TRUE}
     * if connector processing this request supports use of sendfile.
     *
     * Duplicated here for neater code in the catalina packages.
     */
    public static final String SENDFILE_SUPPORTED_ATTR = org.apache.coyote.Constants.SENDFILE_SUPPORTED_ATTR;


    /**
     * The request attribute that can be used by a servlet to pass
     * to the connector the name of the file that is to be served
     * by sendfile. The value should be {@code java.lang.String}
     * that is {@code File.getCanonicalPath()} of the file to be served.
     *
     * Duplicated here for neater code in the catalina packages.
     */
    public static final String SENDFILE_FILENAME_ATTR = org.apache.coyote.Constants.SENDFILE_FILENAME_ATTR;


    /**
     * The request attribute that can be used by a servlet to pass
     * to the connector the start offset of the part of a file
     * that is to be served by sendfile. The value should be
     * {@code java.lang.Long}. To serve complete file
     * the value should be {@code Long.valueOf(0)}.
     *
     * Duplicated here for neater code in the catalina packages.
     */
    public static final String SENDFILE_FILE_START_ATTR = org.apache.coyote.Constants.SENDFILE_FILE_START_ATTR;


    /**
     * The request attribute that can be used by a servlet to pass
     * to the connector the end offset (not including) of the part
     * of a file that is to be served by sendfile. The value should be
     * {@code java.lang.Long}. To serve complete file
     * the value should be equal to the length of the file.
     *
     * Duplicated here for neater code in the catalina packages.
     */
    public static final String SENDFILE_FILE_END_ATTR = org.apache.coyote.Constants.SENDFILE_FILE_END_ATTR;


    /**
     * The request attribute under which we store the array of X509Certificate
     * objects representing the certificate chain presented by our client,
     * if any.
     */
    public static final String CERTIFICATES_ATTR = "javax.servlet.request.X509Certificate";


    /**
     * The request attribute under which we store the name of the cipher suite
     * being used on an SSL connection (as an object of type
     * java.lang.String).
     */
    public static final String CIPHER_SUITE_ATTR = "javax.servlet.request.cipher_suite";


    /**
     * The request attribute under which we store the key size being used for
     * this SSL connection (as an object of type java.lang.Integer).
     */
    public static final String KEY_SIZE_ATTR = "javax.servlet.request.key_size";


    /**
     * The request attribute under which we store the session id being used
     * for this SSL connection (as an object of type java.lang.String).
     */
    public static final String SSL_SESSION_ID_ATTR = "javax.servlet.request.ssl_session_id";


    /**
     * The request attribute key for the session manager.
     * This one is a Tomcat extension to the Servlet spec.
     */
    public static final String SSL_SESSION_MGR_ATTR = "javax.servlet.request.ssl_session_mgr";


    // ------------------------------------------------- Session attribute names

    /**
     * The subject under which the AccessControlContext is running.
     */
    public static final String SUBJECT_ATTR = "javax.security.auth.subject";


    // ------------------------------------------ ServletContext attribute names

    /**
     * The servlet context attribute under which we store the alternate
     * deployment descriptor for this web application
     */
    public static final String ALT_DD_ATTR = "org.apache.catalina.deploy.alt_dd";


    /**
     * The servlet context attribute under which we store the class path
     * for our application class loader (as an object of type String),
     * delimited with the appropriate path delimiter for this platform.
     */
    public static final String CLASS_PATH_ATTR =  "org.apache.catalina.jsp_classpath";


    /**
     * Name of the ServletContext attribute under which we store the context
     * Realm's CredentialHandler (if both the Realm and the CredentialHandler
     * exist).
     */
    public static final String CREDENTIAL_HANDLER = "org.apache.catalina.CredentialHandler";


    /**
     * The WebResourceRoot which is associated with the context. This can be
     * used to manipulate static files.
     */
    public static final String RESOURCES_ATTR = "org.apache.catalina.resources";


    /**
     * Name of the ServletContext attribute under which we store the web
     * application version string (the text that appears after ## when parallel
     * deployment is used).
     */
    public static final String WEBAPP_VERSION = "org.apache.catalina.webappVersion";


    /**
     * The servlet context attribute under which we store a flag used
     * to mark this request as having been processed by the SSIServlet.
     * We do this because of the pathInfo mangling happening when using
     * the CGIServlet in conjunction with the SSI servlet. (value stored
     * as an object of type String)
     *
     * @deprecated Unused. This is no longer used as the CGIO servlet now has
     *             generic handling for when it is used as an include.
     *             This will be removed in Tomcat 10
     */
    @Deprecated
    public static final String SSI_FLAG_ATTR = "org.apache.catalina.ssi.SSIServlet";


    // --------------------------- ServletContext initialisation parameter names

    /**
     * Name of the ServletContext init-param that determines if the JSP engine
     * should validate *.tld files when parsing them.
     * <p>
     * This must be kept in sync with org.apache.jasper.Constants
     */
    public static final String JASPER_XML_VALIDATION_TLD_INIT_PARAM = "org.apache.jasper.XML_VALIDATE_TLD";


    /**
     * Name of the ServletContext init-param that determines if the JSP engine
     * will block external entities from being used in *.tld, *.jspx, *.tagx and
     * tagplugin.xml files.
     * <p>
     * This must be kept in sync with org.apache.jasper.Constants
     */
    public static final String JASPER_XML_BLOCK_EXTERNAL_INIT_PARAM = "org.apache.jasper.XML_BLOCK_EXTERNAL";


    // --------------------------------------------------- System property names

    /**
     * Name of the system property containing
     * the tomcat product installation path
     */
    public static final String CATALINA_HOME_PROP = org.apache.catalina.startup.Constants.CATALINA_HOME_PROP;


    /**
     * Name of the system property containing
     * the tomcat instance installation path
     */
    public static final String CATALINA_BASE_PROP = org.apache.catalina.startup.Constants.CATALINA_BASE_PROP;


    // -------------------------------------------------------- Global constants

    /**
     * The flag which controls strict servlet specification compliance. Setting
     * this flag to {@code true} will change the defaults for other settings.
     */
    public static final boolean STRICT_SERVLET_COMPLIANCE =
            Boolean.parseBoolean(System.getProperty("org.apache.catalina.STRICT_SERVLET_COMPLIANCE", "false"));


    /**
     * Has security been turned on?
     */
    public static final boolean IS_SECURITY_ENABLED = (System.getSecurityManager() != null);


    /**
     * Default domain for MBeans if none can be determined
     */
    public static final String DEFAULT_MBEAN_DOMAIN = "Catalina";
}
