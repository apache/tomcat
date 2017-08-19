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

package org.apache.tomcat.jni;

/** SSL Conf
 */
public final class SSLConf {

    /**
     * Create a new SSL_CONF context.
     *
     * @param pool The pool to use.
     * @param flags The SSL_CONF flags to use. It can be any combination of
     * the following:
     * <PRE>
     * {@link SSL#SSL_CONF_FLAG_CMDLINE}
     * {@link SSL#SSL_CONF_FLAG_FILE}
     * {@link SSL#SSL_CONF_FLAG_CLIENT}
     * {@link SSL#SSL_CONF_FLAG_SERVER}
     * {@link SSL#SSL_CONF_FLAG_SHOW_ERRORS}
     * {@link SSL#SSL_CONF_FLAG_CERTIFICATE}
     * </PRE>
     *
     * @return The Java representation of a pointer to the newly created
     *         SSL_CONF Context
     *
     * @throws Exception If the SSL_CONF context could not be created
     *
     * @see <a href="https://www.openssl.org/docs/man1.0.2/ssl/SSL_CONF_CTX_new.html">OpenSSL SSL_CONF_CTX_new</a>
     * @see <a href="https://www.openssl.org/docs/man1.0.2/ssl/SSL_CONF_CTX_set_flags.html">OpenSSL SSL_CONF_CTX_set_flags</a>
     */
    public static native long make(long pool, int flags) throws Exception;

    /**
     * Free the resources used by the context
     *
     * @param cctx SSL_CONF context to free.
     *
     * @see <a href="https://www.openssl.org/docs/man1.0.2/ssl/SSL_CONF_CTX_new.html">OpenSSL SSL_CONF_CTX_free</a>
     */
    public static native void free(long cctx);

    /**
     * Check a command with an SSL_CONF context.
     *
     * @param cctx SSL_CONF context to use.
     * @param name command name.
     * @param value command value.
     *
     * @return The result of the check based on the {@code SSL_CONF_cmd_value_type}
     * call. Unknown types will result in an exception, as well as
     * file and directory types with invalid file or directory names.
     *
     * @throws Exception If the check fails.
     *
     * @see <a href="https://www.openssl.org/docs/man1.0.2/ssl/SSL_CONF_cmd.html">OpenSSL SSL_CONF_cmd_value_type</a>
     */
    public static native int check(long cctx, String name, String value) throws Exception;

    /**
     * Assign an SSL context to a SSL_CONF context.
     * All following calls to {@link #apply(long, String, String)} will be
     * applied to this SSL context.
     *
     * @param cctx SSL_CONF context to use.
     * @param ctx SSL context to assign to the given SSL_CONF context.
     *
     * @see <a href="https://www.openssl.org/docs/man1.0.2/ssl/SSL_CONF_CTX_set_ssl_ctx.html">OpenSSL SSL_CONF_CTX_set_ssl_ctx</a>
     */
    public static native void assign(long cctx, long ctx);

    /**
     * Apply a command to an SSL_CONF context.
     *
     * @param cctx SSL_CONF context to use.
     * @param name command name.
     * @param value command value.
     *
     * @return The result of the native {@code SSL_CONF_cmd} call
     *
     * @throws Exception If the SSL_CONF context is {@code 0}
     *
     * @see <a href="https://www.openssl.org/docs/man1.0.2/ssl/SSL_CONF_cmd.html">OpenSSL SSL_CONF_cmd</a>
     */
    public static native int apply(long cctx, String name, String value) throws Exception;

    /**
     * Finish commands for an SSL_CONF context.
     *
     * @param cctx SSL_CONF context to use.
     *
     * @return The result of the native {@code SSL_CONF_CTX_finish} call
     *
     * @see <a href="https://www.openssl.org/docs/man1.0.2/ssl/SSL_CONF_CTX_set_flags.html">OpenSSL SSL_CONF_CTX_finish</a>
     */
    public static native int finish(long cctx);

}
