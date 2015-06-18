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
package org.apache.tomcat.util.net.openssl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.tomcat.util.net.Constants;

/**
 * Get SSL protocols in the right preference order
 */
public class OpenSSLProtocols {

    private List<String> openSSLProtocols = new ArrayList<>();

    public OpenSSLProtocols(String preferredJSSEProto) {
        Collections.addAll(openSSLProtocols, Constants.SSL_PROTO_TLSv1_2,
                Constants.SSL_PROTO_TLSv1_1, Constants.SSL_PROTO_TLSv1,
                Constants.SSL_PROTO_SSLv3, Constants.SSL_PROTO_SSLv2);
        if(openSSLProtocols.contains(preferredJSSEProto)) {
            openSSLProtocols.remove(preferredJSSEProto);
            openSSLProtocols.add(0, preferredJSSEProto);
        }
    }

    public String[] getProtocols() {
        return openSSLProtocols.toArray(new String[openSSLProtocols.size()]);
    }
}
