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

package org.apache.tomcat.util.openssl;

import java.lang.invoke.MethodHandle;
import java.lang.foreign.*;
import static java.lang.foreign.ValueLayout.*;

/**
 * Methods used present in older OpenSSL versions but not in the current major version.
 */
public class openssl_h_Compatibility {

    // OpenSSL 1.1 FIPS_mode
    public static int FIPS_mode() {
        class Holder {
            static final String NAME = "FIPS_mode";
            static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT);
            static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
        }
        var mh$ = Holder.MH;
        try {
            if (openssl_h.TRACE_DOWNCALLS) {
                openssl_h.traceDowncall(Holder.NAME);
            }
            return (int) mh$.invokeExact();
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    // OpenSSL 1.1 FIPS_mode_set
    public static int FIPS_mode_set(int r) {
        class Holder {
            static final String NAME = "FIPS_mode_set";
            static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT, JAVA_INT);
            static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
        }
        var mh$ = Holder.MH;
        try {
            if (openssl_h.TRACE_DOWNCALLS) {
                openssl_h.traceDowncall(Holder.NAME, Integer.valueOf(r));
            }
            return (int) mh$.invokeExact(r);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    // OpenSSL 1.1 EVP_PKEY_base_id
    public static int EVP_PKEY_base_id(MemorySegment pkey) {
        class Holder {
            static final String NAME = "EVP_PKEY_base_id";
            static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT, openssl_h.C_POINTER);
            static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
        }
        var mh$ = Holder.MH;
        try {
            if (openssl_h.TRACE_DOWNCALLS) {
                openssl_h.traceDowncall(Holder.NAME, pkey);
            }
            return (int) mh$.invokeExact(pkey);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    // OpenSSL 1.1 EVP_PKEY_bits
    public static int EVP_PKEY_bits(MemorySegment pkey) {
        class Holder {
            static final String NAME = "EVP_PKEY_bits";
            static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT, openssl_h.C_POINTER);
            static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
        }
        var mh$ = Holder.MH;
        try {
            if (openssl_h.TRACE_DOWNCALLS) {
                openssl_h.traceDowncall(Holder.NAME, pkey);
            }
            return (int) mh$.invokeExact(pkey);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    // OpenSSL 1.1 SSL_get_peer_certificate
    public static MemorySegment SSL_get_peer_certificate(MemorySegment s) {
        class Holder {
            static final String NAME = "SSL_get_peer_certificate";
            static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_POINTER, openssl_h.C_POINTER);
            static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
        }
        var mh$ = Holder.MH;
        try {
            if (openssl_h.TRACE_DOWNCALLS) {
                openssl_h.traceDowncall(Holder.NAME, s);
            }
            return (java.lang.foreign.MemorySegment) mh$.invokeExact(s);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

}

