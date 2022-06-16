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

// Generated by jextract

package org.apache.tomcat.util.openssl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.lang.foreign.*;
import static java.lang.foreign.ValueLayout.*;
class constants$16 {

    static final FunctionDescriptor SSL_SESSION_get_id$FUNC = FunctionDescriptor.of(ADDRESS,
        ADDRESS,
        ADDRESS
    );
    static final MethodHandle SSL_SESSION_get_id$MH = RuntimeHelper.downcallHandle(
        "SSL_SESSION_get_id",
        constants$16.SSL_SESSION_get_id$FUNC, false
    );
    static final FunctionDescriptor SSL_get_peer_certificate$FUNC = FunctionDescriptor.of(ADDRESS,
        ADDRESS
    );
    static final MethodHandle SSL_get_peer_certificate$MH = RuntimeHelper.downcallHandle(
        "SSL_get_peer_certificate",
        constants$16.SSL_get_peer_certificate$FUNC, false
    );
    static final FunctionDescriptor SSL_get_peer_cert_chain$FUNC = FunctionDescriptor.of(ADDRESS,
        ADDRESS
    );
    static final MethodHandle SSL_get_peer_cert_chain$MH = RuntimeHelper.downcallHandle(
        "SSL_get_peer_cert_chain",
        constants$16.SSL_get_peer_cert_chain$FUNC, false
    );
    static final FunctionDescriptor SSL_CTX_set_verify$FUNC = FunctionDescriptor.ofVoid(
        ADDRESS,
        JAVA_INT,
        ADDRESS
    );
    static final MethodHandle SSL_CTX_set_verify$MH = RuntimeHelper.downcallHandle(
        "SSL_CTX_set_verify",
        constants$16.SSL_CTX_set_verify$FUNC, false
    );
    static final FunctionDescriptor SSL_CTX_set_cert_verify_callback$cb$FUNC = FunctionDescriptor.of(JAVA_INT,
        ADDRESS,
        ADDRESS
    );
    static final MethodHandle SSL_CTX_set_cert_verify_callback$cb$MH = RuntimeHelper.downcallHandle(
        constants$16.SSL_CTX_set_cert_verify_callback$cb$FUNC, false
    );
}


