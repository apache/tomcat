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
package org.apache.catalina.util;

import org.apache.catalina.Globals;
import org.apache.tomcat.util.net.SSLSupport;

public class TLSUtil {

    /**
     * Determines if the named request attribute is used to pass information
     * about the TLS configuration of the connection to the application. Both
     * the standard request attributes defined by the Servlet specification and
     * Tomcat specific attributes are supported.
     *
     * @param name  The attribute name to test
     *
     * @return {@code true} if the attribute is used to pass TLS configuration
     *         information, otherwise {@code false}
     */
    public static boolean isTLSRequestAttribute(String name) {
        switch (name) {
            case Globals.CERTIFICATES_ATTR:
            case Globals.CIPHER_SUITE_ATTR:
            case Globals.KEY_SIZE_ATTR:
            case Globals.SSL_SESSION_ID_ATTR:
            case Globals.SSL_SESSION_MGR_ATTR:
            case SSLSupport.PROTOCOL_VERSION_KEY:
            case SSLSupport.REQUESTED_PROTOCOL_VERSIONS_KEY:
            case SSLSupport.REQUESTED_CIPHERS_KEY:
                return true;
            default:
                return false;
        }
    }
}
