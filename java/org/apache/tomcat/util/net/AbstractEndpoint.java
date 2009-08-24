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
package org.apache.tomcat.util.net;

import org.apache.tomcat.util.res.StringManager;
/**
 * 
 * @author fhanik
 * @author Mladen Turk
 * @author Remy Maucherat
 */
public abstract class AbstractEndpoint {
    
    // -------------------------------------------------------------- Constants
    protected StringManager sm = StringManager.getManager("org.apache.tomcat.util.net.res");

    /**
     * The Request attribute key for the cipher suite.
     */
    public static final String CIPHER_SUITE_KEY = "javax.servlet.request.cipher_suite";

    /**
     * The Request attribute key for the key size.
     */
    public static final String KEY_SIZE_KEY = "javax.servlet.request.key_size";

    /**
     * The Request attribute key for the client certificate chain.
     */
    public static final String CERTIFICATE_KEY = "javax.servlet.request.X509Certificate";

    /**
     * The Request attribute key for the session id.
     * This one is a Tomcat extension to the Servlet spec.
     */
    public static final String SESSION_ID_KEY = "javax.servlet.request.ssl_session";

    /**
     * The request attribute key for the session manager.
     * This one is a Tomcat extension to the Servlet spec.
     */
    public static final String SESSION_MGR = "javax.servlet.request.ssl_session_mgr";

}
