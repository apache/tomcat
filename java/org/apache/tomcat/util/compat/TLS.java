/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.compat;

import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

/**
 * This class checks for the availability of TLS features.
 *
 * @deprecated Unused. This will be removed in Tomcat 10.
 */
@Deprecated
public class TLS {

    private static final boolean tlsv13Available;

    static {
        boolean ok = false;
        try {
            // Don't use org.apache.tomcat.util.net.SSL_PROTO_TLSv1_3 as that
            // creates an unwanted dependency
            SSLContext.getInstance("TLSv1.3");
            ok = true;
        } catch (NoSuchAlgorithmException ex) {
        }
        tlsv13Available = ok;
    }

    public static boolean isTlsv13Available() {
        return tlsv13Available;
    }

}
