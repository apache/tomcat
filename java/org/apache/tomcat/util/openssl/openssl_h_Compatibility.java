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
import static org.apache.tomcat.util.openssl.openssl_h.OpenSSL_version;
import static org.apache.tomcat.util.openssl.openssl_h.OpenSSL_version_num;
import static org.apache.tomcat.util.openssl.openssl_h.SSL_get1_peer_certificate;

/**
 * Methods used present in older OpenSSL versions but not in the current major version or OpenSSL derivatives.
 */
public class openssl_h_Compatibility {

    public static final boolean OPENSSL;
    public static final boolean OPENSSL3;
    public static final boolean BORINGSSL;
    public static final boolean LIBRESSL;
    static {
        String versionString = OpenSSL_version(0).getString(0);
        OPENSSL = versionString.contains("OpenSSL");
        OPENSSL3 = OPENSSL && OpenSSL_version_num() >= 0x3000000fL;
        BORINGSSL = versionString.contains("BoringSSL");
        LIBRESSL = versionString.contains("LibreSSL");
    }

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
        if (OPENSSL3) {
            return SSL_get1_peer_certificate(s);
        } else {
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

    // LibreSSL SSL_CTRL_OPTIONS
    public static final int SSL_CTRL_OPTIONS = 32;

    // LibreSSL SSL_CTX_get_options
    public static long SSL_CTX_get_options(MemorySegment ctx) {
        if (LIBRESSL) {
            return openssl_h.SSL_CTX_ctrl(ctx, SSL_CTRL_OPTIONS, 0, MemorySegment.NULL);
        } else {
            return openssl_h.SSL_CTX_get_options(ctx);
        }
    }

    // LibreSSL SSL_CTX_set_options
    public static long SSL_CTX_set_options(MemorySegment ctx, long op) {
        if (LIBRESSL) {
            return openssl_h.SSL_CTX_ctrl(ctx, SSL_CTRL_OPTIONS, op, MemorySegment.NULL);
        } else {
            return openssl_h.SSL_CTX_set_options(ctx, op);
        }
    }

    // LibreSSL SSL_get_options
    public static long SSL_get_options(MemorySegment s) {
        if (LIBRESSL) {
            return openssl_h.SSL_ctrl(s, SSL_CTRL_OPTIONS, 0, MemorySegment.NULL);
        } else {
            return openssl_h.SSL_get_options(s);
        }
    }

    // LibreSSL SSL_set_options
    public static long SSL_set_options(MemorySegment s, long op) {
        if (LIBRESSL) {
            return openssl_h.SSL_ctrl(s, SSL_CTRL_OPTIONS, op, MemorySegment.NULL);
        } else {
            return openssl_h.SSL_set_options(s, op);
        }
    }

    // LibreSSL SSL_CTRL_CLEAR_OPTIONS
    public static final int SSL_CTRL_CLEAR_OPTIONS = 77;

    // LibreSSL SSL_CTX_set_options
    public static long SSL_CTX_clear_options(MemorySegment ctx, long op) {
        if (LIBRESSL) {
            return openssl_h.SSL_CTX_ctrl(ctx, SSL_CTRL_CLEAR_OPTIONS, op, MemorySegment.NULL);
        } else {
            return openssl_h.SSL_CTX_clear_options(ctx, op);
        }
    }

    // LibreSSL OPENSSL_sk_num
    public static int OPENSSL_sk_num(MemorySegment x0) {
        if (LIBRESSL) {
            class Holder {
                static final String NAME = "sk_num";
                static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_INT, openssl_h.C_POINTER);
                static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
            }
            var mh$ = Holder.MH;
            try {
                if (openssl_h.TRACE_DOWNCALLS) {
                    openssl_h.traceDowncall(Holder.NAME, x0);
                }
                return (int) mh$.invokeExact(x0);
            } catch (Throwable ex$) {
                throw new AssertionError("should not reach here", ex$);
            }
        } else {
            return openssl_h.OPENSSL_sk_num(x0);
        }
    }

    // LibreSSL OPENSSL_sk_value
    public static MemorySegment OPENSSL_sk_value(MemorySegment x0, int x1) {
        if (LIBRESSL) {
            class Holder {
                static final String NAME = "sk_value";
                static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_POINTER, openssl_h.C_POINTER, openssl_h.C_INT);
                static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
            }
            var mh$ = Holder.MH;
            try {
                if (openssl_h.TRACE_DOWNCALLS) {
                    openssl_h.traceDowncall(Holder.NAME, x0, Integer.valueOf(x1));
                }
                return (MemorySegment) mh$.invokeExact(x0, x1);
            } catch (Throwable ex$) {
                throw new AssertionError("should not reach here", ex$);
            }
        } else {
            return openssl_h.OPENSSL_sk_value(x0, x1);
        }
    }

    // BoringSSL removed SSL_set_verify_result which does not do anything in OpenSSL
    public static void SSL_set_verify_result(MemorySegment ssl, long v) {
        if (!BORINGSSL) {
            openssl_h.SSL_set_verify_result(ssl, v);
        }
    }

}
