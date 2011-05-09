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
 * @version $Id$
 */

public final class Globals {

    /**
     * The servlet context attribute under which we store the alternate
     * deployment descriptor for this web application 
     */
    public static final String ALT_DD_ATTR = 
        "org.apache.catalina.deploy.alt_dd";


    /**
     * The request attribute under which we store the array of X509Certificate
     * objects representing the certificate chain presented by our client,
     * if any.
     */
    public static final String CERTIFICATES_ATTR =
        "javax.servlet.request.X509Certificate";


    /**
     * The request attribute under which we store the name of the cipher suite
     * being used on an SSL connection (as an object of type
     * java.lang.String).
     */
    public static final String CIPHER_SUITE_ATTR =
        "javax.servlet.request.cipher_suite";


    /**
     * Request dispatcher state.
     */
    public static final String DISPATCHER_TYPE_ATTR = 
        "org.apache.catalina.core.DISPATCHER_TYPE";


    /**
     * Request dispatcher path.
     */
    public static final String DISPATCHER_REQUEST_PATH_ATTR = 
        "org.apache.catalina.core.DISPATCHER_REQUEST_PATH";


    /**
     * The JNDI directory context which is associated with the context. This
     * context can be used to manipulate static files.
     */
    public static final String RESOURCES_ATTR =
        "org.apache.catalina.resources";


    /**
     * The servlet context attribute under which we store the class path
     * for our application class loader (as an object of type String),
     * delimited with the appropriate path delimiter for this platform.
     */
    public static final String CLASS_PATH_ATTR =
        "org.apache.catalina.jsp_classpath";


    /**
     * The request attribute under which we store the key size being used for
     * this SSL connection (as an object of type java.lang.Integer).
     */
    public static final String KEY_SIZE_ATTR =
        "javax.servlet.request.key_size";


    /**
     * The request attribute under which we store the session id being used
     * for this SSL connection (as an object of type java.lang.String).
     */
    public static final String SSL_SESSION_ID_ATTR =
        "javax.servlet.request.ssl_session";


    /**
     * The request attribute key for the session manager.
     * This one is a Tomcat extension to the Servlet spec.
     */
    public static final String SSL_SESSION_MGR_ATTR =
        "javax.servlet.request.ssl_session_mgr";


    /**
     * The servlet context attribute under which the managed bean Registry
     * will be stored for privileged contexts (if enabled).
     */
    public static final String MBEAN_REGISTRY_ATTR =
        "org.apache.catalina.Registry";


    /**
     * The servlet context attribute under which the MBeanServer will be stored
     * for privileged contexts (if enabled).
     */
    public static final String MBEAN_SERVER_ATTR =
        "org.apache.catalina.MBeanServer";


    /**
     * The request attribute under which we store the servlet name on a
     * named dispatcher request.
     */
    public static final String NAMED_DISPATCHER_ATTR =
        "org.apache.catalina.NAMED";


    /**
     * The servlet context attribute under which we store a flag used
     * to mark this request as having been processed by the SSIServlet.
     * We do this because of the pathInfo mangling happening when using
     * the CGIServlet in conjunction with the SSI servlet. (value stored
     * as an object of type String)
     */
     public static final String SSI_FLAG_ATTR =
         "org.apache.catalina.ssi.SSIServlet";


    /**
     * The subject under which the AccessControlContext is running.
     */
    public static final String SUBJECT_ATTR =
        "javax.security.auth.subject";

    
    public static final String GSS_CREDENTIAL_ATTR =
        "org.apache.catalina.realm.GSS_CREDENTIAL";


    /**
     * The master flag which controls strict servlet specification 
     * compliance.
     */
    public static final boolean STRICT_SERVLET_COMPLIANCE =
        Boolean.valueOf(System.getProperty("org.apache.catalina.STRICT_SERVLET_COMPLIANCE", "false")).booleanValue();


    /**
     * Has security been turned on?
     */
    public static final boolean IS_SECURITY_ENABLED =
        (System.getSecurityManager() != null);
    
    /**
     * 
     */
    public static final String ASYNC_SUPPORTED_ATTR = 
        "org.apache.catalina.ASYNC_SUPPORTED";

    
    /**
     * Default domain for MBeans if none can be determined
     */
    public static final String DEFAULT_MBEAN_DOMAIN = "Catalina";


    /**
     * Name of the system property containing
     * the tomcat product installation path
     */
    public static final String CATALINA_HOME_PROP = "catalina.home";


    /**
     * Name of the system property containing
     * the tomcat instance installation path
     */
    public static final String CATALINA_BASE_PROP = "catalina.base";
}
