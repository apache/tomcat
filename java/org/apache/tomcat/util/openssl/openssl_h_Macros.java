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

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import static org.apache.tomcat.util.openssl.openssl_h.*;

/**
 * Functional macros not handled by jextract.
 */
public class openssl_h_Macros {


    /**
     * Set maximum protocol version on the given context. # define SSL_CTX_set_max_proto_version(sslCtx, version) \
     * SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_MAX_PROTO_VERSION, version, NULL)
     *
     * @param sslCtx  the SSL context
     * @param version the maximum version
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_max_proto_version(MemorySegment sslCtx, long version) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_MAX_PROTO_VERSION(), version, MemorySegment.NULL);
    }


    /**
     * Set minimum protocol version on the given context. # define SSL_CTX_set_min_proto_version(sslCtx, version) \
     * SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_MIN_PROTO_VERSION, version, NULL)
     *
     * @param sslCtx  the SSL context
     * @param version the maximum version
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_min_proto_version(MemorySegment sslCtx, long version) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_MIN_PROTO_VERSION(), version, MemorySegment.NULL);
    }


    /**
     * Get the session cache size. # define SSL_CTX_sess_get_cache_size(sslCtx) \ SSL_CTX_ctrl(sslCtx,
     * SSL_CTRL_GET_SESS_CACHE_SIZE, 0, NULL)
     *
     * @param sslCtx the SSL context
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_sess_get_cache_size(MemorySegment sslCtx) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_GET_SESS_CACHE_SIZE(), 0, MemorySegment.NULL);
    }


    /**
     * Set the session cache size. # define SSL_CTX_sess_set_cache_size(sslCtx, t) \ SSL_CTX_ctrl(sslCtx,
     * SSL_CTRL_SET_SESS_CACHE_SIZE, t, NULL)
     *
     * @param sslCtx    the SSL context
     * @param cacheSize the session cache size
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_sess_set_cache_size(MemorySegment sslCtx, long cacheSize) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_SESS_CACHE_SIZE(), cacheSize, MemorySegment.NULL);
    }


    /**
     * Get the session cache mode. # define SSL_CTX_get_session_cache_mode(sslCtx) \ SSL_CTX_ctrl(sslCtx,
     * SSL_CTRL_GET_SESS_CACHE_MODE, 0, NULL)
     *
     * @param sslCtx the SSL context
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_get_session_cache_mode(MemorySegment sslCtx) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_GET_SESS_CACHE_MODE(), 0, MemorySegment.NULL);
    }


    /**
     * Set the session cache mode. # define SSL_CTX_set_session_cache_mode(sslCtx, m) \ SSL_CTX_ctrl(sslCtx,
     * SSL_CTRL_SET_SESS_CACHE_MODE, m, NULL)
     *
     * @param sslCtx    the SSL context
     * @param cacheMode the cache mode, SSL_SESS_CACHE_OFF to disable
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_session_cache_mode(MemorySegment sslCtx, long cacheMode) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_SESS_CACHE_MODE(), cacheMode, MemorySegment.NULL);
    }


    /**
     * Set the certificate. # define SSL_CTX_add0_chain_cert(sslCtx,x509) \ SSL_CTX_ctrl(sslCtx, SSL_CTRL_CHAIN_CERT, 0,
     * (char *)(x509))
     *
     * @param sslCtx the SSL context
     * @param x509   the certificate
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_add0_chain_cert(MemorySegment sslCtx, MemorySegment x509) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_CHAIN_CERT(), 0, x509);
    }


    /**
     * Set ticket keys. # define SSL_CTX_set_tlsext_ticket_keys(ctx, keys, keylen) \
     * SSL_CTX_ctrl((ctx),SSL_CTRL_SET_TLSEXT_TICKET_KEYS, (keylen), (keys))
     *
     * @param sslCtx    the SSL context
     * @param keys      the keys
     * @param keyLength the length
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_tlsext_ticket_keys(MemorySegment sslCtx, MemorySegment keys, long keyLength) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_TLSEXT_TICKET_KEYS(), keyLength, keys);
    }


    /**
     * Read the specified file. # define BIO_read_filename(b,name) \ (int)BIO_ctrl(b,BIO_C_SET_FILENAME,
     * BIO_CLOSE|BIO_FP_READ,(char *)(name))
     *
     * @param bio  The BIO to read into
     * @param name the file name
     *
     * @return > 0 if successful
     */
    public static long BIO_read_filename(MemorySegment bio, MemorySegment name) {
        return BIO_ctrl(bio, BIO_C_SET_FILENAME(), BIO_CLOSE() | BIO_FP_READ(), name);
    }


    /**
     * Set tmp dh. # define SSL_CTX_set_tmp_dh(sslCtx,dh) \ SSL_CTX_ctrl(sslCtx,SSL_CTRL_SET_TMP_DH,0,(char *)(dh))
     *
     * @param sslCtx the SSL context
     * @param dh     the dh
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_tmp_dh(MemorySegment sslCtx, MemorySegment dh) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_TMP_DH(), 0, dh);
    }


    /**
     * Set tmp ecdh. # define SSL_CTX_set_tmp_ecdh(sslCtx,ecdh) \ SSL_CTX_ctrl(sslCtx,SSL_CTRL_SET_TMP_ECDH,0,(char
     * *)(ecdh))
     *
     * @param sslCtx the SSL context
     * @param ecdh   the ecdh
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_tmp_ecdh(MemorySegment sslCtx, MemorySegment ecdh) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_TMP_ECDH(), 0, ecdh);
    }


    /**
     * Free memory. # define OPENSSL_free(addr) \ CRYPTO_free(addr, OPENSSL_FILE, OPENSSL_LINE)
     *
     * @param segment The address to free
     */
    public static void OPENSSL_free(MemorySegment segment) {
        CRYPTO_free(segment, MemorySegment.NULL, 0);
    }


    /**
     * Reset BIO position. # define BIO_reset(b) \ (int)BIO_ctrl(b,BIO_CTRL_RESET,0,NULL)
     *
     * @param bio The BIO to reset
     *
     * @return > 0 if successful
     */
    public static long BIO_reset(MemorySegment bio) {
        return BIO_ctrl(bio, BIO_CTRL_RESET(), 0, MemorySegment.NULL);
    }


    /**
     * Set NIDs of groups in preference order. # define SSL_CTX_set1_curves SSL_CTX_set1_groups # define
     * SSL_CTX_set1_groups(ctx, glist, glistlen) \ SSL_CTX_ctrl(ctx,SSL_CTRL_SET_GROUPS,glistlen,(int *)(glist))
     *
     * @param sslCtx     the SSL context
     * @param groupsList the groups list
     * @param listLength the list length
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set1_groups(MemorySegment sslCtx, MemorySegment groupsList, int listLength) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_GROUPS(), listLength, groupsList);
    }


    /**
     * Pass a path from which certificates are loaded into the store. # define X509_LOOKUP_add_dir(x,name,type) \
     * X509_LOOKUP_ctrl((x),X509_L_ADD_DIR,(name),(long)(type),NULL)
     *
     * @param x509Lookup the X509 lookup
     * @param name       the path name
     * @param type       the type used
     *
     * @return > 0 if successful
     */
    public static long X509_LOOKUP_add_dir(MemorySegment x509Lookup, MemorySegment name, long type) {
        return X509_LOOKUP_ctrl(x509Lookup, X509_L_ADD_DIR(), name, X509_FILETYPE_PEM(), MemorySegment.NULL);
    }

    /**
     * Pass a file which will be loaded into the store. # define X509_LOOKUP_load_file(x,name,type) \
     * X509_LOOKUP_ctrl((x),X509_L_FILE_LOAD,(name),(long)(type),NULL)
     *
     * @param x509Lookup the X509 lookup
     * @param name       the path name
     * @param type       the type used
     *
     * @return > 0 if successful
     */
    public static long X509_LOOKUP_load_file(MemorySegment x509Lookup, MemorySegment name, long type) {
        return X509_LOOKUP_ctrl(x509Lookup, X509_L_FILE_LOAD(), name, X509_FILETYPE_PEM(), MemorySegment.NULL);
    }

    // Other symbols that have questionable visibility from jextract

    /**
     * {@snippet lang = c : * void X509_free(X509* a);
     * }
     */
    public static MethodHandle X509_free$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("X509_free", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "X509_free");
    }

    public static void X509_free(MemorySegment a) {
        var mh$ = X509_free$MH();
        try {
            mh$.invokeExact(a);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * X509* d2i_X509(X509** a, unsigned char** in, long len);
     * }
     */
    public static MethodHandle d2i_X509$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(RuntimeHelper.POINTER, RuntimeHelper.POINTER,
                    RuntimeHelper.POINTER, ValueLayout.JAVA_LONG);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("d2i_X509", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "d2i_X509");
    }

    public static MemorySegment d2i_X509(MemorySegment a, MemorySegment in, long len) {
        var mh$ = d2i_X509$MH();
        try {
            return (MemorySegment) mh$.invokeExact(a, in, len);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * int i2d_X509(const X509* a, unsigned char** out);
     * }
     */
    public static MethodHandle i2d_X509$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(ValueLayout.JAVA_INT, RuntimeHelper.POINTER,
                    RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("i2d_X509", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "i2d_X509");
    }

    public static int i2d_X509(MemorySegment a, MemorySegment out) {
        var mh$ = i2d_X509$MH();
        try {
            return (int) mh$.invokeExact(a, out);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * X509* PEM_read_bio_X509_AUX(BIO* out, X509** x, pem_password_cb* cb, void* u);
     * }
     */
    public static MethodHandle PEM_read_bio_X509_AUX$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(RuntimeHelper.POINTER, RuntimeHelper.POINTER,
                    RuntimeHelper.POINTER, RuntimeHelper.POINTER, RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("PEM_read_bio_X509_AUX", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "PEM_read_bio_X509_AUX");
    }

    public static MemorySegment PEM_read_bio_X509_AUX(MemorySegment out, MemorySegment x, MemorySegment cb,
            MemorySegment u) {
        var mh$ = PEM_read_bio_X509_AUX$MH();
        try {
            return (MemorySegment) mh$.invokeExact(out, x, cb, u);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c
     * : * EC_GROUP* PEM_read_bio_ECPKParameters(BIO* out, EC_GROUP** x, pem_password_cb* cb, void* u);
     * }
     */
    public static MethodHandle PEM_read_bio_ECPKParameters$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(RuntimeHelper.POINTER, RuntimeHelper.POINTER,
                    RuntimeHelper.POINTER, RuntimeHelper.POINTER, RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("PEM_read_bio_ECPKParameters", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "PEM_read_bio_ECPKParameters");
    }

    @Deprecated
    public static MemorySegment PEM_read_bio_ECPKParameters(MemorySegment out, MemorySegment x, MemorySegment cb,
            MemorySegment u) {
        var mh$ = PEM_read_bio_ECPKParameters$MH();
        try {
            return (MemorySegment) mh$.invokeExact(out, x, cb, u);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * DH* PEM_read_bio_DHparams(BIO* out, DH** x, pem_password_cb* cb, void* u);
     * }
     */
    public static MethodHandle PEM_read_bio_DHparams$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(RuntimeHelper.POINTER, RuntimeHelper.POINTER,
                    RuntimeHelper.POINTER, RuntimeHelper.POINTER, RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("PEM_read_bio_DHparams", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "PEM_read_bio_DHparams");
    }

    @Deprecated
    public static MemorySegment PEM_read_bio_DHparams(MemorySegment out, MemorySegment x, MemorySegment cb,
            MemorySegment u) {
        var mh$ = PEM_read_bio_DHparams$MH();
        try {
            return (MemorySegment) mh$.invokeExact(out, x, cb, u);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * EVP_PKEY* PEM_read_bio_PrivateKey(BIO* out, EVP_PKEY** x, pem_password_cb* cb, void* u);
     * }
     */
    public static MethodHandle PEM_read_bio_PrivateKey$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(RuntimeHelper.POINTER, RuntimeHelper.POINTER,
                    RuntimeHelper.POINTER, RuntimeHelper.POINTER, RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("PEM_read_bio_PrivateKey", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "PEM_read_bio_PrivateKey");
    }

    public static MemorySegment PEM_read_bio_PrivateKey(MemorySegment out, MemorySegment x, MemorySegment cb,
            MemorySegment u) {
        var mh$ = PEM_read_bio_PrivateKey$MH();
        try {
            return (MemorySegment) mh$.invokeExact(out, x, cb, u);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * void PKCS12_free(PKCS12* a);
     * }
     */
    public static MethodHandle PKCS12_free$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("PKCS12_free", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "PKCS12_free");
    }

    public static void PKCS12_free(MemorySegment a) {
        var mh$ = PKCS12_free$MH();
        try {
            mh$.invokeExact(a);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }


    /**
     * {@snippet lang = c : * void OCSP_BASICRESP_free(OCSP_BASICRESP* a);
     * }
     */
    public static MethodHandle OCSP_BASICRESP_free$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("OCSP_BASICRESP_free", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "OCSP_BASICRESP_free");
    }

    public static void OCSP_BASICRESP_free(MemorySegment a) {
        var mh$ = OCSP_BASICRESP_free$MH();
        try {
            mh$.invokeExact(a);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * void OCSP_RESPONSE_free(OCSP_RESPONSE* a);
     * }
     */
    public static MethodHandle OCSP_RESPONSE_free$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("OCSP_RESPONSE_free", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "OCSP_RESPONSE_free");
    }

    public static void OCSP_RESPONSE_free(MemorySegment a) {
        var mh$ = OCSP_RESPONSE_free$MH();
        try {
            mh$.invokeExact(a);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * OCSP_RESPONSE* d2i_OCSP_RESPONSE(OCSP_RESPONSE** a, unsigned char** in, long len);
     * }
     */
    public static MethodHandle d2i_OCSP_RESPONSE$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(RuntimeHelper.POINTER, RuntimeHelper.POINTER,
                    RuntimeHelper.POINTER, ValueLayout.JAVA_LONG);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("d2i_OCSP_RESPONSE", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "d2i_OCSP_RESPONSE");
    }

    public static MemorySegment d2i_OCSP_RESPONSE(MemorySegment a, MemorySegment in, long len) {
        var mh$ = d2i_OCSP_RESPONSE$MH();
        try {
            return (MemorySegment) mh$.invokeExact(a, in, len);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * void OCSP_CERTID_free(OCSP_CERTID* a);
     * }
     */
    public static MethodHandle OCSP_CERTID_free$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("OCSP_CERTID_free", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "OCSP_CERTID_free");
    }

    public static void OCSP_CERTID_free(MemorySegment a) {
        var mh$ = OCSP_CERTID_free$MH();
        try {
            mh$.invokeExact(a);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * OCSP_REQUEST* OCSP_REQUEST_new();
     * }
     */
    public static MethodHandle OCSP_REQUEST_new$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("OCSP_REQUEST_new", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "OCSP_REQUEST_new");
    }

    public static MemorySegment OCSP_REQUEST_new() {
        var mh$ = OCSP_REQUEST_new$MH();
        try {
            return (MemorySegment) mh$.invokeExact();
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * void OCSP_REQUEST_free(OCSP_REQUEST* a);
     * }
     */
    public static MethodHandle OCSP_REQUEST_free$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("OCSP_REQUEST_free", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "OCSP_REQUEST_free");
    }

    public static void OCSP_REQUEST_free(MemorySegment a) {
        var mh$ = OCSP_REQUEST_free$MH();
        try {
            mh$.invokeExact(a);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    /**
     * {@snippet lang = c : * int i2d_OCSP_REQUEST(const OCSP_REQUEST* a, unsigned char** out);
     * }
     */
    public static MethodHandle i2d_OCSP_REQUEST$MH() {
        class Holder {
            static final FunctionDescriptor DESC = FunctionDescriptor.of(ValueLayout.JAVA_INT, RuntimeHelper.POINTER,
                    RuntimeHelper.POINTER);

            static final MethodHandle MH = RuntimeHelper.downcallHandle("i2d_OCSP_REQUEST", DESC);
        }
        return RuntimeHelper.requireNonNull(Holder.MH, "i2d_OCSP_REQUEST");
    }

    public static int i2d_OCSP_REQUEST(MemorySegment a, MemorySegment out) {
        var mh$ = i2d_OCSP_REQUEST$MH();
        try {
            return (int) mh$.invokeExact(a, out);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }
}

