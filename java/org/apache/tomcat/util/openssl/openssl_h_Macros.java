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
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static org.apache.tomcat.util.openssl.openssl_h.*;

/**
 * Functional macros not handled by jextract.
 */
@SuppressWarnings("javadoc")
public class openssl_h_Macros {


    /**
     * Set maximum protocol version on the given context.
     * {@snippet lang = c : # define SSL_CTX_set_max_proto_version(sslCtx, version) \
     *    SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_MAX_PROTO_VERSION, version, NULL)
     * }
     *
     * @param sslCtx  the SSL context
     * @param version the maximum version
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_max_proto_version(MemorySegment sslCtx, long version) {
        if (openssl_h_Compatibility.BORINGSSL) {
            class Holder {
                static final String NAME = "SSL_CTX_set_max_proto_version";
                static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_LONG, openssl_h.C_POINTER, openssl_h.C_LONG);
                static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
            }
            var mh$ = Holder.MH;
            try {
                if (openssl_h.TRACE_DOWNCALLS) {
                    openssl_h.traceDowncall(Holder.NAME, sslCtx, Long.valueOf(version));
                }
                return (long) mh$.invokeExact(sslCtx, version);
            } catch (Throwable ex$) {
                throw new AssertionError("should not reach here", ex$);
            }
        } else {
            return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_MAX_PROTO_VERSION(), version, MemorySegment.NULL);
        }
    }


    /**
     * Set minimum protocol version on the given context.
     * {@snippet lang = c : # define SSL_CTX_set_min_proto_version(sslCtx, version) \
     *    SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_MIN_PROTO_VERSION, version, NULL)
     * }
     *
     * @param sslCtx  the SSL context
     * @param version the maximum version
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_min_proto_version(MemorySegment sslCtx, long version) {
        if (openssl_h_Compatibility.BORINGSSL) {
            class Holder {
                static final String NAME = "SSL_CTX_set_min_proto_version";
                static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_LONG, openssl_h.C_POINTER, openssl_h.C_LONG);
                static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
            }
            var mh$ = Holder.MH;
            try {
                if (openssl_h.TRACE_DOWNCALLS) {
                    openssl_h.traceDowncall(Holder.NAME, sslCtx, Long.valueOf(version));
                }
                return (long) mh$.invokeExact(sslCtx, version);
            } catch (Throwable ex$) {
                throw new AssertionError("should not reach here", ex$);
            }
        } else {
            return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_MIN_PROTO_VERSION(), version, MemorySegment.NULL);
        }
    }


    /**
     * Get the session cache size.
     * {@snippet lang = c : # define SSL_CTX_sess_get_cache_size(sslCtx) \
     *    SSL_CTX_ctrl(sslCtx, SSL_CTRL_GET_SESS_CACHE_SIZE, 0, NULL)
     * }
     *
     * @param sslCtx the SSL context
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_sess_get_cache_size(MemorySegment sslCtx) {
        if (openssl_h_Compatibility.BORINGSSL) {
            class Holder {
                static final String NAME = "SSL_CTX_sess_get_cache_size";
                static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_LONG, openssl_h.C_POINTER);
                static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
            }
            var mh$ = Holder.MH;
            try {
                if (openssl_h.TRACE_DOWNCALLS) {
                    openssl_h.traceDowncall(Holder.NAME, sslCtx);
                }
                return (long) mh$.invokeExact(sslCtx);
            } catch (Throwable ex$) {
                throw new AssertionError("should not reach here", ex$);
            }
        } else {
            return SSL_CTX_ctrl(sslCtx, SSL_CTRL_GET_SESS_CACHE_SIZE(), 0, MemorySegment.NULL);
        }
    }


    /**
     * Set the session cache size.
     * {@snippet lang = c : # define SSL_CTX_sess_set_cache_size(sslCtx, t) \
     *    SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_SESS_CACHE_SIZE, t, NULL)
     * }
     *
     * @param sslCtx    the SSL context
     * @param cacheSize the session cache size
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_sess_set_cache_size(MemorySegment sslCtx, long cacheSize) {
        if (openssl_h_Compatibility.BORINGSSL) {
            class Holder {
                static final String NAME = "SSL_CTX_sess_set_cache_size";
                static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_LONG, openssl_h.C_POINTER, openssl_h.C_LONG);
                static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
            }
            var mh$ = Holder.MH;
            try {
                if (openssl_h.TRACE_DOWNCALLS) {
                    openssl_h.traceDowncall(Holder.NAME, sslCtx, Long.valueOf(cacheSize));
                }
                return (long) mh$.invokeExact(sslCtx, cacheSize);
            } catch (Throwable ex$) {
                throw new AssertionError("should not reach here", ex$);
            }
        } else {
            return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_SESS_CACHE_SIZE(), cacheSize, MemorySegment.NULL);
        }
    }


    /**
     * Get the session cache mode.
     * {@snippet lang = c : # define SSL_CTX_get_session_cache_mode(sslCtx) \
     *    SSL_CTX_ctrl(sslCtx, SSL_CTRL_GET_SESS_CACHE_MODE, 0, NULL)
     * }
     *
     * @param sslCtx the SSL context
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_get_session_cache_mode(MemorySegment sslCtx) {
        if (openssl_h_Compatibility.BORINGSSL) {
            class Holder {
                static final String NAME = "SSL_CTX_get_session_cache_mode";
                static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_LONG, openssl_h.C_POINTER);
                static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
            }
            var mh$ = Holder.MH;
            try {
                if (openssl_h.TRACE_DOWNCALLS) {
                    openssl_h.traceDowncall(Holder.NAME, sslCtx);
                }
                return (long) mh$.invokeExact(sslCtx);
            } catch (Throwable ex$) {
                throw new AssertionError("should not reach here", ex$);
            }
        } else {
            return SSL_CTX_ctrl(sslCtx, SSL_CTRL_GET_SESS_CACHE_MODE(), 0, MemorySegment.NULL);
        }
    }


    /**
     * Set the session cache mode.
     * {@snippet lang = c : # define SSL_CTX_set_session_cache_mode(sslCtx, m) \
     *    SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_SESS_CACHE_MODE, m, NULL)
     * }
     *
     * @param sslCtx    the SSL context
     * @param cacheMode the cache mode, SSL_SESS_CACHE_OFF to disable
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_session_cache_mode(MemorySegment sslCtx, long cacheMode) {
        if (openssl_h_Compatibility.BORINGSSL) {
            class Holder {
                static final String NAME = "SSL_CTX_set_session_cache_mode";
                static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_LONG, openssl_h.C_POINTER, openssl_h.C_LONG);
                static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
            }
            var mh$ = Holder.MH;
            try {
                if (openssl_h.TRACE_DOWNCALLS) {
                    openssl_h.traceDowncall(Holder.NAME, sslCtx, Long.valueOf(cacheMode));
                }
                return (long) mh$.invokeExact(sslCtx, cacheMode);
            } catch (Throwable ex$) {
                throw new AssertionError("should not reach here", ex$);
            }
        } else {
            return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_SESS_CACHE_MODE(), cacheMode, MemorySegment.NULL);
        }
    }


    /**
     * Set the certificate.
     * {@snippet lang = c : # define SSL_CTX_add0_chain_cert(sslCtx,x509) \
     *    SSL_CTX_ctrl(sslCtx, SSL_CTRL_CHAIN_CERT, 0, (char *)(x509))
     * }
     *
     * @param sslCtx the SSL context
     * @param x509   the certificate
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_add0_chain_cert(MemorySegment sslCtx, MemorySegment x509) {
        if (openssl_h_Compatibility.BORINGSSL) {
            class Holder {
                static final String NAME = "SSL_CTX_add0_chain_cert";
                static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_LONG, openssl_h.C_POINTER, openssl_h.C_POINTER);
                static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
            }
            var mh$ = Holder.MH;
            try {
                if (openssl_h.TRACE_DOWNCALLS) {
                    openssl_h.traceDowncall(Holder.NAME, sslCtx, x509);
                }
                return (long) mh$.invokeExact(sslCtx, x509);
            } catch (Throwable ex$) {
                throw new AssertionError("should not reach here", ex$);
            }
        } else {
            return SSL_CTX_ctrl(sslCtx, SSL_CTRL_CHAIN_CERT(), 0, x509);
        }
    }


    /**
     * Set ticket keys.
     * {@snippet lang = c : # define SSL_CTX_set_tlsext_ticket_keys(ctx, keys, keylen) \
     *    SSL_CTX_ctrl((ctx),SSL_CTRL_SET_TLSEXT_TICKET_KEYS, (keylen), (keys))
     * }
     *
     * @param sslCtx    the SSL context
     * @param keys      the keys
     * @param keyLength the length
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_tlsext_ticket_keys(MemorySegment sslCtx, MemorySegment keys, long keyLength) {
        if (openssl_h_Compatibility.BORINGSSL) {
            class Holder {
                static final String NAME = "SSL_CTX_set_tlsext_ticket_keys";
                static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_LONG, openssl_h.C_POINTER, openssl_h.C_POINTER, openssl_h.C_LONG);
                static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
            }
            var mh$ = Holder.MH;
            try {
                if (openssl_h.TRACE_DOWNCALLS) {
                    openssl_h.traceDowncall(Holder.NAME, sslCtx, keys, Long.valueOf(keyLength));
                }
                return (long) mh$.invokeExact(sslCtx, keys, keyLength);
            } catch (Throwable ex$) {
                throw new AssertionError("should not reach here", ex$);
            }
        } else {
            return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_TLSEXT_TICKET_KEYS(), keyLength, keys);
        }
    }


    /**
     * Read the specified file.
     * {@snippet lang = c : # define BIO_read_filename(b,name) \
     *    (int)BIO_ctrl(b,BIO_C_SET_FILENAME,BIO_CLOSE|BIO_FP_READ,(char *)(name))
     * }
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
     * Set tmp dh.
     * {@snippet lang = c : # define SSL_CTX_set_tmp_dh(sslCtx,dh) \
     *    SSL_CTX_ctrl(sslCtx,SSL_CTRL_SET_TMP_DH,0,(char *)(dh))
     * }
     *
     * @param sslCtx the SSL context
     * @param dh     the dh
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_tmp_dh(MemorySegment sslCtx, MemorySegment dh) {
        if (openssl_h_Compatibility.BORINGSSL) {
            return 1;
        } else {
            return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_TMP_DH(), 0, dh);
        }
    }


    /**
     * Set tmp ecdh.
     * {@snippet lang = c : # define SSL_CTX_set_tmp_ecdh(sslCtx,ecdh) \
     *    SSL_CTX_ctrl(sslCtx,SSL_CTRL_SET_TMP_ECDH,0,(char *)(ecdh))
     * }
     *
     * @param sslCtx the SSL context
     * @param ecdh   the ecdh
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_tmp_ecdh(MemorySegment sslCtx, MemorySegment ecdh) {
        if (openssl_h_Compatibility.BORINGSSL) {
            class Holder {
                static final String NAME = "SSL_CTX_set_tmp_ecdh";
                static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_LONG, openssl_h.C_POINTER, openssl_h.C_POINTER);
                static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
            }
            var mh$ = Holder.MH;
            try {
                if (openssl_h.TRACE_DOWNCALLS) {
                    openssl_h.traceDowncall(Holder.NAME, sslCtx, ecdh);
                }
                return (long) mh$.invokeExact(sslCtx, ecdh);
            } catch (Throwable ex$) {
                throw new AssertionError("should not reach here", ex$);
            }
        } else {
            return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_TMP_ECDH(), 0, ecdh);
        }
    }


    /**
     * Free memory.
     * {@snippet lang = c : # define OPENSSL_free(addr) CRYPTO_free(addr, OPENSSL_FILE, OPENSSL_LINE)
     * }
     *
     * @param segment The address to free
     */
    public static void OPENSSL_free(MemorySegment segment) {
        CRYPTO_free(segment, MemorySegment.NULL, 0);
    }


    /**
     * Reset BIO position.
     * {@snippet lang = c : # define BIO_reset(b) (int)BIO_ctrl(b,BIO_CTRL_RESET,0,NULL)
     * }
     *
     * @param bio The BIO to reset
     *
     * @return > 0 if successful
     */
    public static long BIO_reset(MemorySegment bio) {
        return BIO_ctrl(bio, BIO_CTRL_RESET(), 0, MemorySegment.NULL);
    }


    /**
     * Set NIDs of groups in preference order.
     * {@snippet lang = c : # define SSL_CTX_set1_curves SSL_CTX_set1_groups
     *    # define SSL_CTX_set1_groups(ctx, glist, glistlen) \
     *       SSL_CTX_ctrl(ctx,SSL_CTRL_SET_GROUPS,glistlen,(int *)(glist))
     * }
     *
     * @param sslCtx     the SSL context
     * @param groupsList the groups list
     * @param listLength the list length
     *
     * @return > 0 if successful
     */
    public static long SSL_CTX_set1_groups(MemorySegment sslCtx, MemorySegment groupsList, int listLength) {
        if (openssl_h_Compatibility.BORINGSSL) {
            class Holder {
                static final String NAME = "SSL_CTX_set1_groups";
                static final FunctionDescriptor DESC = FunctionDescriptor.of(openssl_h.C_LONG, openssl_h.C_POINTER, openssl_h.C_POINTER, openssl_h.C_INT);
                static final MethodHandle MH = Linker.nativeLinker().downcallHandle(openssl_h.findOrThrow(NAME), DESC);
            }
            var mh$ = Holder.MH;
            try {
                if (openssl_h.TRACE_DOWNCALLS) {
                    openssl_h.traceDowncall(Holder.NAME, sslCtx, groupsList, Integer.valueOf(listLength));
                }
                return (long) mh$.invokeExact(sslCtx, groupsList, listLength);
            } catch (Throwable ex$) {
                throw new AssertionError("should not reach here", ex$);
            }
        } else {
            return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_GROUPS(), listLength, groupsList);
        }
    }


    /**
     * Pass a path from which certificates are loaded into the store.
     * {@snippet lang = c : # define X509_LOOKUP_add_dir(x,name,type) \
     *    X509_LOOKUP_ctrl((x),X509_L_ADD_DIR,(name),(long)(type),NULL)
     * }
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
     * Pass a file which will be loaded into the store.
     * {@snippet lang = c : # define X509_LOOKUP_load_file(x,name,type) \
     *    X509_LOOKUP_ctrl((x),X509_L_FILE_LOAD,(name),(long)(type),NULL)
     * }
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


    /**
     * Return the d2i_ECPKParameters symbol.
     * @return the symbol
     */
    public static MemorySegment d2i_ECPKParameters$SYMBOL() {
        return openssl_h.findOrThrow("d2i_ECPKParameters");
    }

}

