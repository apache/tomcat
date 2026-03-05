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

/**
 * Methods used present in older OpenSSL versions but not in the current major version or OpenSSL derivatives.
 */
@SuppressWarnings("javadoc")
public class openssl_h_Compatibility {

    public static final boolean OPENSSL;
    public static final boolean OPENSSL1;
    public static final boolean OPENSSL3;
    public static final boolean BORINGSSL;
    public static final boolean LIBRESSL;

    public static final int MAJOR;
    public static final int MINOR;

    static {
        String versionString = OpenSSL_version(0).getString(0);
        OPENSSL = versionString.contains("OpenSSL");
        OPENSSL1 = OPENSSL && OpenSSL_version_num() < 0x3000000fL;
        OPENSSL3 = OPENSSL && OpenSSL_version_num() >= 0x3000000fL;
        BORINGSSL = versionString.contains("BoringSSL");
        LIBRESSL = versionString.contains("LibreSSL");
        int majorVersion = 0;
        int minorVersion = 0;
        try {
            String[] blocks = versionString.split("\\s");
            if (blocks.length >= 2) {
                versionString = blocks[1];
            }
            String[] versionNumberStrings = versionString.split("\\.");
            if (versionNumberStrings.length >= 2) {
                majorVersion = Integer.parseInt(versionNumberStrings[0]);
                minorVersion = Integer.parseInt(versionNumberStrings[1]);
            }
        } catch (Exception e) {
            // Ignore, default to 0
        } finally {
            MAJOR = majorVersion;
            MINOR = minorVersion;
        }
    }

    public static boolean isLibreSSLPre35() {
        return LIBRESSL && ((MAJOR == 3 && MINOR < 5) || MAJOR < 3);
    }

    // OpenSSL 1.1 FIPS_mode
    public static int FIPS_mode() {
        if (isLibreSSLPre35()) {
            return 0;
        }
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
        if (isLibreSSLPre35()) {
            return 0;
        }
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
            // This could be using SSL_get1_peer_certificate instead, as all the other implementations
            // use SSL_get_peer_certificate which is equivalent to SSL_get1_peer_certificate
            return MemorySegment.NULL;
        }
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

    private static class X509_STORE_CTX_get0_current_issuer {
        public static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_POINTER, openssl_h.C_POINTER);

        public static final MemorySegment ADDR = openssl_h.findOrThrow("X509_STORE_CTX_get0_current_issuer");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    /**
     * OpenSSL 1.1 X509_STORE_CTX_get0_current_issuer
     * Function descriptor for:
     * {@snippet lang = c : * X509 *X509_STORE_CTX_get0_current_issuer(const X509_STORE_CTX *ctx)
     * }
     */
    public static FunctionDescriptor X509_STORE_CTX_get0_current_issuer$descriptor() {
        return X509_STORE_CTX_get0_current_issuer.DESC;
    }

    /**
     * Downcall method handle for:
     * {@snippet lang = c : * X509 *X509_STORE_CTX_get0_current_issuer(const X509_STORE_CTX *ctx)
     * }
     */
    public static MethodHandle X509_STORE_CTX_get0_current_issuer$handle() {
        return X509_STORE_CTX_get0_current_issuer.HANDLE;
    }

    /**
     * Address for:
     * {@snippet lang = c : * X509 *X509_STORE_CTX_get0_current_issuer(const X509_STORE_CTX *ctx)
     * }
     */
    public static MemorySegment X509_STORE_CTX_get0_current_issuer$address() {
        return X509_STORE_CTX_get0_current_issuer.ADDR;
    }

    /**
     * {@snippet lang = c : * X509 *X509_STORE_CTX_get0_current_issuer(const X509_STORE_CTX *ctx)
     * }
     */
    public static MemorySegment X509_STORE_CTX_get0_current_issuer(MemorySegment ctx) {
        var mh$ = X509_STORE_CTX_get0_current_issuer.HANDLE;
        try {
            if (openssl_h.TRACE_DOWNCALLS) {
                openssl_h.traceDowncall("X509_STORE_CTX_get0_current_issuer", ctx);
            }
            return (MemorySegment) mh$.invokeExact(ctx);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
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
            return SSL_ctrl(s, SSL_CTRL_OPTIONS, 0, MemorySegment.NULL);
        } else {
            return openssl_h.SSL_get_options(s);
        }
    }

    // LibreSSL SSL_set_options
    public static long SSL_set_options(MemorySegment s, long op) {
        if (LIBRESSL) {
            return SSL_ctrl(s, SSL_CTRL_OPTIONS, op, MemorySegment.NULL);
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
                static final FunctionDescriptor DESC =
                        FunctionDescriptor.of(openssl_h.C_POINTER, openssl_h.C_POINTER, openssl_h.C_INT);
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

    /**
     * {@snippet lang = c : * long SSL_ctrl(SSL *ssl, int cmd, long larg, void *parg)
     * }
     */
    public static long SSL_ctrl(MemorySegment ssl, int cmd, long larg, MemorySegment parg) {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_LONG, openssl_h.C_POINTER,
                    openssl_h.C_INT, openssl_h.C_LONG, openssl_h.C_POINTER);

            static final MethodHandle MH =
                    Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow("SSL_ctrl"), DESC);
        }
        var mh$ = Holder.MH;
        try {
            return (long) mh$.invokeExact(ssl, cmd, larg, parg);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    // OpenSSL 1.x engine APIs

    /**
     * {@snippet lang = c : * ENGINE *ENGINE_by_id(const char *id)
     * }
     */
    public static MemorySegment ENGINE_by_id(MemorySegment id) {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_POINTER, openssl_h.C_POINTER);

            static final MethodHandle MH =
                    Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow("ENGINE_by_id"), DESC);
        }
        var mh$ = Holder.MH;
        try {
            return (MemorySegment) mh$.invokeExact(id);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * int ENGINE_register_all_complete(void)
     * }
     */
    public static int ENGINE_register_all_complete() {
        try {
            return (int) Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow("ENGINE_register_all_complete"),
                    FunctionDescriptor.of(JAVA_INT)).invokeExact();
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c
     * : * int ENGINE_ctrl_cmd_string(ENGINE *e, const char *cmd_name, const char *arg, int cmd_optional)
     * }
     */
    public static int ENGINE_ctrl_cmd_string(MemorySegment e, MemorySegment cmd_name, MemorySegment arg,
            int cmd_optional) {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_INT, openssl_h.C_POINTER,
                    openssl_h.C_POINTER, openssl_h.C_POINTER, openssl_h.C_INT);

            static final MethodHandle MH =
                    Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow("ENGINE_ctrl_cmd_string"), DESC);
        }
        var mh$ = Holder.MH;
        try {
            return (int) mh$.invokeExact(e, cmd_name, arg, cmd_optional);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * int ENGINE_free(ENGINE *e)
     * }
     */
    public static int ENGINE_free(MemorySegment e) {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_INT, openssl_h.C_POINTER);

            static final MethodHandle MH =
                    Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow("ENGINE_free"), DESC);
        }
        var mh$ = Holder.MH;
        try {
            return (int) mh$.invokeExact(e);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c
     * : * EVP_PKEY *ENGINE_load_private_key(ENGINE *e, const char *key_id, UI_METHOD *ui_method, void *callback_data)
     * }
     */
    public static MemorySegment ENGINE_load_private_key(MemorySegment e, MemorySegment key_id, MemorySegment ui_method,
            MemorySegment callback_data) {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_POINTER, openssl_h.C_POINTER,
                    openssl_h.C_POINTER, openssl_h.C_POINTER, openssl_h.C_POINTER);

            static final MethodHandle MH =
                    Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow("ENGINE_load_private_key"), DESC);
        }
        var mh$ = Holder.MH;
        try {
            return (MemorySegment) mh$.invokeExact(e, key_id, ui_method, callback_data);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * int ENGINE_set_default(ENGINE *e, unsigned int flags)
     * }
     */
    public static int ENGINE_set_default(MemorySegment e, int flags) {
        class Holder {
            static final FunctionDescriptor DESC =
                    FunctionDescriptor.of(openssl_h.C_INT, openssl_h.C_POINTER, openssl_h.C_INT);

            static final MethodHandle MH =
                    Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow("ENGINE_set_default"), DESC);
        }
        var mh$ = Holder.MH;
        try {
            return (int) mh$.invokeExact(e, flags);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    private static final int ENGINE_METHOD_ALL = (int) 65535L;

    /**
     * {@snippet lang = c : * #define ENGINE_METHOD_ALL 65535
     * }
     */
    public static int ENGINE_METHOD_ALL() {
        return ENGINE_METHOD_ALL;
    }

}
