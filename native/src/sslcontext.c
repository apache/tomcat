/* Copyright 2000-2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/** SSL Context wrapper
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

#include "apr.h"
#include "apr_pools.h"
#include "apr_file_io.h"
#include "apr_portable.h"
#include "apr_thread_mutex.h"

#include "tcn.h"

#ifdef HAVE_OPENSSL
#include "ssl_private.h"

/*
 * Handle the Temporary RSA Keys and DH Params
 */

#define SSL_TMP_KEY_FREE(ctx, type, idx) \
    if (ctx->temp_keys[idx]) { \
        type##_free((type *)ctx->temp_keys[idx]); \
        ctx->temp_keys[idx] = NULL; \
    }

#define SSL_TMP_KEYS_FREE(ctx, type) \
    SSL_TMP_KEY_FREE(ctx, type, SSL_TMP_KEY_##type##_512); \
    SSL_TMP_KEY_FREE(ctx, type, SSL_TMP_KEY_##type##_1024)

static void ssl_tmp_keys_free(tcn_ssl_ctxt_t *ctx)
{

    SSL_TMP_KEYS_FREE(ctx, RSA);
    SSL_TMP_KEYS_FREE(ctx, DH);
}

static int ssl_tmp_key_init_rsa(tcn_ssl_ctxt_t *ctx,
                                int bits, int idx)
{
    if (!(ctx->temp_keys[idx] =
          RSA_generate_key(bits, RSA_F4, NULL, NULL))) {
        BIO_printf(ctx->bio_os, "[ERROR] "
                   "Init: Failed to generate temporary "
                   "%d bit RSA private key", bits);
        return 0;
    }
    return 1;
}

static int ssl_tmp_key_init_dh(tcn_ssl_ctxt_t *ctx,
                               int bits, int idx)
{
    if (!(ctx->temp_keys[idx] =
          SSL_dh_get_tmp_param(bits))) {
        BIO_printf(ctx->bio_os, "[ERROR] "
                   "Init: Failed to generate temporary "
                   "%d bit DH parameters", bits);
        return 0;
    }
    return 1;
}

static apr_status_t ssl_context_cleanup(void *data)
{
    tcn_ssl_ctxt_t *c = (tcn_ssl_ctxt_t *)data;
    if (c) {
        if (c->crl)
            X509_STORE_free(c->crl);
        c->crl = NULL;
        if (c->ctx)
            SSL_CTX_free(c->ctx);
        c->ctx = NULL;
        if (c->mode) {
            int i;
            for (i = 0; i < SSL_AIDX_MAX; i++) {
                if (c->pk.s.certs[i]) {
                    X509_free(c->pk.s.certs[i]);
                    c->pk.s.certs[i] = NULL;
                }
                if (c->pk.s.keys[i]) {
                    EVP_PKEY_free(c->pk.s.keys[i]);
                    c->pk.s.keys[i] = NULL;
                }
            }
        }
        else if (c->pk.c.certs) {
            sk_X509_INFO_pop_free(c->pk.c.certs, X509_INFO_free);
            c->pk.c.certs = NULL;
        }

        if (c->bio_is)
            SSL_BIO_close(c->bio_is);
        c->bio_is = NULL;
        if (c->bio_os)
            SSL_BIO_close(c->bio_os);
        c->bio_os = NULL;
    }
    return APR_SUCCESS;
}

/* Initialize server context */
TCN_IMPLEMENT_CALL(jlong, SSLContext, initS)(TCN_STDARGS, jlong pool,
                                             jint protocol)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    tcn_ssl_ctxt_t *c = NULL;
    SSL_CTX *ctx = NULL;
    UNREFERENCED(o);

    switch (protocol) {
        case SSL_PROTOCOL_SSLV2:
        case SSL_PROTOCOL_SSLV2 | SSL_PROTOCOL_TLSV1:
            ctx = SSL_CTX_new(SSLv2_server_method());
        break;
        case SSL_PROTOCOL_SSLV3:
        case SSL_PROTOCOL_SSLV3 | SSL_PROTOCOL_TLSV1:
            ctx = SSL_CTX_new(SSLv3_server_method());
        break;
        case SSL_PROTOCOL_SSLV2 | SSL_PROTOCOL_SSLV3:
        case SSL_PROTOCOL_ALL:
            ctx = SSL_CTX_new(SSLv23_server_method());
        break;
        case SSL_PROTOCOL_TLSV1:
            ctx = SSL_CTX_new(TLSv1_server_method());
        break;
    }
    if (!ctx) {
        tcn_ThrowException(e, "Invalid Server SSL Protocol");
        goto init_failed;
    }
    if ((c = apr_pcalloc(p, sizeof(tcn_ssl_ctxt_t))) == NULL) {
        tcn_ThrowAPRException(e, apr_get_os_error());
        goto init_failed;
    }
    /* server mode */
    c->mode = 1;
    c->ctx  = ctx;
    c->pool = p;
    c->bio_os = BIO_new(BIO_s_file());
    if (c->bio_os != NULL)
        BIO_set_fp(c->bio_os, stderr, BIO_NOCLOSE | BIO_FP_TEXT);
    SSL_CTX_set_options(c->ctx, SSL_OP_ALL);
    if (!(protocol & SSL_PROTOCOL_SSLV2))
        SSL_CTX_set_options(c->ctx, SSL_OP_NO_SSLv2);
    if (!(protocol & SSL_PROTOCOL_SSLV3))
        SSL_CTX_set_options(c->ctx, SSL_OP_NO_SSLv3);
    if (!(protocol & SSL_PROTOCOL_TLSV1))
        SSL_CTX_set_options(c->ctx, SSL_OP_NO_TLSv1);
    /*
     * Configure additional context ingredients
     */
    SSL_CTX_set_options(c->ctx, SSL_OP_SINGLE_DH_USE);

#ifdef SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION
    /*
     * Disallow a session from being resumed during a renegotiation,
     * so that an acceptable cipher suite can be negotiated.
     */
    SSL_CTX_set_options(c->ctx, SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION);
#endif
    /* Default vhost id and cache size */
    SSL_CTX_sess_set_cache_size(c->ctx, SSL_DEFAULT_CACHE_SIZE);
    MD5((const unsigned char *)SSL_DEFAULT_VHOST_NAME,
        (unsigned long)(sizeof(SSL_DEFAULT_VHOST_NAME) - 1),
        &(c->vhost_id[0]));

    SSL_CTX_set_tmp_rsa_callback(c->ctx, SSL_callback_tmp_RSA);
    SSL_CTX_set_tmp_dh_callback(c->ctx,  SSL_callback_tmp_DH);

    /* Set default Certificate verification level
     * and depth for the Client Authentication
     */
    c->verify_depth = 1;
    c->verify_mode  = SSL_CVERIFY_UNSET;
    /*
     * Let us cleanup the ssl context when the pool is destroyed
     */
    apr_pool_cleanup_register(p, (const void *)c,
                              ssl_context_cleanup,
                              apr_pool_cleanup_null);

    return P2J(c);
init_failed:
    return 0;
}

/* Initialize client context */
TCN_IMPLEMENT_CALL(jlong, SSLContext, initC)(TCN_STDARGS, jlong pool,
                                             jint protocol)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    tcn_ssl_ctxt_t *c = NULL;
    SSL_CTX *ctx = NULL;
    UNREFERENCED(o);

    switch (protocol) {
        case SSL_PROTOCOL_SSLV2:
        case SSL_PROTOCOL_SSLV2 | SSL_PROTOCOL_TLSV1:
            ctx = SSL_CTX_new(SSLv2_client_method());
        break;
        case SSL_PROTOCOL_SSLV3:
        case SSL_PROTOCOL_SSLV3 | SSL_PROTOCOL_TLSV1:
            ctx = SSL_CTX_new(SSLv3_client_method());
        break;
        case SSL_PROTOCOL_SSLV2 | SSL_PROTOCOL_SSLV3:
        case SSL_PROTOCOL_ALL:
            ctx = SSL_CTX_new(SSLv23_client_method());
        break;
        case SSL_PROTOCOL_TLSV1:
            ctx = SSL_CTX_new(TLSv1_client_method());
        break;
    }
    if (!ctx) {
        tcn_ThrowException(e, "Invalid Client SSL Protocol");
        goto init_failed;
    }
    if ((c = apr_pcalloc(p, sizeof(tcn_ssl_ctxt_t))) == NULL) {
        tcn_ThrowAPRException(e, apr_get_os_error());
        goto init_failed;
    }
    /* client mode */
    c->mode = 0;
    c->ctx  = ctx;
    c->pool = p;
    c->bio_os = BIO_new(BIO_s_file());
    if (c->bio_os != NULL)
        BIO_set_fp(c->bio_os, stderr, BIO_NOCLOSE | BIO_FP_TEXT);
    SSL_CTX_set_options(c->ctx, SSL_OP_ALL);
    if (!(protocol & SSL_PROTOCOL_SSLV2))
        SSL_CTX_set_options(c->ctx, SSL_OP_NO_SSLv2);
    if (!(protocol & SSL_PROTOCOL_SSLV3))
        SSL_CTX_set_options(c->ctx, SSL_OP_NO_SSLv3);
    if (!(protocol & SSL_PROTOCOL_TLSV1))
        SSL_CTX_set_options(c->ctx, SSL_OP_NO_TLSv1);
    /*
     * Configure additional context ingredients
     */
    SSL_CTX_set_options(c->ctx, SSL_OP_SINGLE_DH_USE);

#ifdef SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION
    /*
     * Disallow a session from being resumed during a renegotiation,
     * so that an acceptable cipher suite can be negotiated.
     */
    SSL_CTX_set_options(c->ctx, SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION);
#endif
    /* Default vhost id and cache size */
    SSL_CTX_sess_set_cache_size(c->ctx, SSL_DEFAULT_CACHE_SIZE);
    MD5((const unsigned char *)SSL_DEFAULT_VHOST_NAME,
        (unsigned long)(sizeof(SSL_DEFAULT_VHOST_NAME) - 1),
        &(c->vhost_id[0]));
    /*
     * Let us cleanup the ssl context when the pool is destroyed
     */
    apr_pool_cleanup_register(p, (const void *)c,
                              ssl_context_cleanup,
                              apr_pool_cleanup_null);

    return P2J(c);
init_failed:
    return 0;
}

TCN_IMPLEMENT_CALL(jint, SSLContext, free)(TCN_STDARGS, jlong ctx)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    UNREFERENCED_STDARGS;
    TCN_ASSERT(ctx != 0);
    /* Run and destroy the cleanup callback */
    return apr_pool_cleanup_run(c->pool, c, ssl_context_cleanup);
}

TCN_IMPLEMENT_CALL(void, SSLContext, setVhostId)(TCN_STDARGS, jlong ctx,
                                                 jstring id)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    TCN_ALLOC_CSTRING(id);

    TCN_ASSERT(ctx != 0);
    UNREFERENCED(o);
    if (J2S(id)) {
        MD5((const unsigned char *)J2S(id),
            (unsigned long)strlen(J2S(id)),
            &(c->vhost_id[0]));
    }
    TCN_FREE_CSTRING(id);
}

TCN_IMPLEMENT_CALL(void, SSLContext, setBIO)(TCN_STDARGS, jlong ctx,
                                             jlong bio, jint dir)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    BIO *bio_handle   = J2P(bio, BIO *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(ctx != 0);
    if (dir == 0) {
        if (c->bio_os && c->bio_os != bio_handle)
            SSL_BIO_close(c->bio_os);
        c->bio_os = bio_handle;
    }
    else if (dir == 1) {
        if (c->bio_os && c->bio_is != bio_handle)
            SSL_BIO_close(c->bio_is);
        c->bio_os = bio_handle;
    }
    else
        return;
    SSL_BIO_doref(bio_handle);
}

TCN_IMPLEMENT_CALL(void, SSLContext, setOptions)(TCN_STDARGS, jlong ctx,
                                                 jint opt)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(ctx != 0);
    SSL_CTX_set_options(c->ctx, opt);
}

TCN_IMPLEMENT_CALL(void, SSLContext, setQuietShutdown)(TCN_STDARGS, jlong ctx,
                                                       jboolean mode)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(ctx != 0);
    SSL_CTX_set_quiet_shutdown(c->ctx, mode ? 1 : 0);
}

TCN_IMPLEMENT_CALL(jboolean, SSLContext, setCipherSuite)(TCN_STDARGS, jlong ctx,
                                                         jstring ciphers)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    TCN_ALLOC_CSTRING(ciphers);
    jboolean rv = JNI_TRUE;

    UNREFERENCED(o);
    TCN_ASSERT(ctx != 0);
    if (!J2S(ciphers))
        return JNI_FALSE;

    if (!SSL_CTX_set_cipher_list(c->ctx, J2S(ciphers))) {
        BIO_printf(c->bio_os,
                   "[ERROR] Unable to configure permitted SSL ciphers");
        rv = JNI_FALSE;
    }
    TCN_FREE_CSTRING(ciphers);
    return rv;
}

TCN_IMPLEMENT_CALL(jboolean, SSLContext, setCARevocationFile)(TCN_STDARGS, jlong ctx,
                                                              jstring file)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    TCN_ALLOC_CSTRING(file);
    jboolean rv = JNI_FALSE;
    X509_LOOKUP *lookup;

    UNREFERENCED(o);
    TCN_ASSERT(ctx != 0);
    if (!J2S(file))
        return JNI_FALSE;

    if (!c->crl) {
        if ((c->crl = X509_STORE_new()) == NULL)
            goto cleanup;
    }
    lookup = X509_STORE_add_lookup(c->crl, X509_LOOKUP_file());
    if (lookup == NULL) {
        X509_STORE_free(c->crl);
        c->crl = NULL;
        goto cleanup;
    }
    X509_LOOKUP_load_file(lookup, J2S(file), X509_FILETYPE_PEM);
    rv = JNI_TRUE;
cleanup:
    TCN_FREE_CSTRING(file);
    return rv;
}

TCN_IMPLEMENT_CALL(jboolean, SSLContext, setCARevocationPath)(TCN_STDARGS, jlong ctx,
                                                              jstring path)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    TCN_ALLOC_CSTRING(path);
    jboolean rv = JNI_FALSE;
    X509_LOOKUP *lookup;

    UNREFERENCED(o);
    TCN_ASSERT(ctx != 0);
    if (!J2S(path))
        return JNI_FALSE;

    if (!c->crl) {
        if ((c->crl = X509_STORE_new()) == NULL)
            goto cleanup;
    }
    lookup = X509_STORE_add_lookup(c->crl, X509_LOOKUP_hash_dir());
    if (lookup == NULL) {
        X509_STORE_free(c->crl);
        c->crl = NULL;
        goto cleanup;
    }
    X509_LOOKUP_add_dir(lookup, J2S(path), X509_FILETYPE_PEM);
    rv = JNI_TRUE;
cleanup:
    TCN_FREE_CSTRING(path);
    return rv;
}

TCN_IMPLEMENT_CALL(jboolean, SSLContext, setCertificateChainFile)(TCN_STDARGS, jlong ctx,
                                                                  jstring file)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    jboolean rv = JNI_TRUE;

    UNREFERENCED(o);
    TCN_ASSERT(ctx != 0);
    if (!file)
        return JNI_FALSE;
    if ((c->cert_chain = tcn_pstrdup(e, file, c->pool)) == NULL)
        rv = JNI_FALSE;

    return rv;
}

TCN_IMPLEMENT_CALL(jboolean, SSLContext, setCertificateFile)(TCN_STDARGS, jlong ctx,
                                                             jstring file)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    int i;

    UNREFERENCED(o);
    TCN_ASSERT(ctx != 0);
    if (!file)
        return JNI_FALSE;
    for (i = 0; i < SSL_AIDX_MAX; i++) {
        if (!c->pk.s.cert_files[i]) {
            c->pk.s.cert_files[i] = tcn_pstrdup(e, file, c->pool);
            return JNI_TRUE;
        }
    }
    BIO_printf(c->bio_os, "[ERROR] Only up to %d "
               "different certificates per virtual host allowed",
               SSL_AIDX_MAX);
    return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jboolean, SSLContext, setCertificateKeyFile)(TCN_STDARGS, jlong ctx,
                                                                jstring file)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    int i;

    UNREFERENCED(o);
    TCN_ASSERT(ctx != 0);
    if (!file)
        return JNI_FALSE;
    for (i = 0; i < SSL_AIDX_MAX; i++) {
        if (!c->pk.s.key_files[i]) {
            c->pk.s.key_files[i] = tcn_pstrdup(e, file, c->pool);
            return JNI_TRUE;
        }
    }
    BIO_printf(c->bio_os, "[ERROR] Only up to %d "
               "different private keys per virtual host allowed",
               SSL_AIDX_MAX);
    return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jboolean, SSLContext, setCACertificateFile)(TCN_STDARGS, jlong ctx,
                                                               jstring file)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    jboolean rv = JNI_TRUE;

    UNREFERENCED(o);
    TCN_ASSERT(ctx != 0);
    if (!file)
        return JNI_FALSE;
    if ((c->ca_cert_file = tcn_pstrdup(e, file, c->pool)) == NULL)
        rv = JNI_FALSE;

    return rv;
}

TCN_IMPLEMENT_CALL(jboolean, SSLContext, setCACertificatePath)(TCN_STDARGS, jlong ctx,
                                                               jstring path)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    jboolean rv = JNI_TRUE;

    UNREFERENCED(o);
    TCN_ASSERT(ctx != 0);
    if (!path)
        return JNI_FALSE;
    if ((c->ca_cert_path = tcn_pstrdup(e, path, c->pool)) == NULL)
        rv = JNI_FALSE;

    return rv;
}

TCN_IMPLEMENT_CALL(jboolean, SSLContext, setCADNRequestFile)(TCN_STDARGS, jlong ctx,
                                                             jstring file)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    jboolean rv = JNI_TRUE;

    UNREFERENCED(o);
    TCN_ASSERT(ctx != 0);
    if (!file)
        return JNI_FALSE;
    if ((c->pk.s.ca_name_file = tcn_pstrdup(e, file, c->pool)) == NULL)
        rv = JNI_FALSE;

    return rv;
}

TCN_IMPLEMENT_CALL(jboolean, SSLContext, setCADNRequestPath)(TCN_STDARGS, jlong ctx,
                                                             jstring path)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    jboolean rv = JNI_TRUE;

    UNREFERENCED(o);
    TCN_ASSERT(ctx != 0);
    if (!path)
        return JNI_FALSE;
    if ((c->pk.s.ca_name_path = tcn_pstrdup(e, path, c->pool)) == NULL)
        rv = JNI_FALSE;

    return rv;
}

TCN_IMPLEMENT_CALL(void, SSLContext, setVerifyDepth)(TCN_STDARGS, jlong ctx,
                                                     jint depth)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(ctx != 0);
    c->verify_depth = depth;
}

TCN_IMPLEMENT_CALL(jboolean, SSLContext, setVerifyClient)(TCN_STDARGS, jlong ctx,
                                                          jint level)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    int verify = SSL_VERIFY_NONE;
    STACK_OF(X509_NAME) *ca_list;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(ctx != 0);
    c->verify_mode = level;

    if (c->verify_mode == SSL_CVERIFY_UNSET)
        c->verify_mode = SSL_CVERIFY_NONE;

    /*
     *  Configure callbacks for SSL context
     */
    if (c->verify_mode == SSL_CVERIFY_REQUIRE)
        verify |= SSL_VERIFY_PEER_STRICT;
    if ((c->verify_mode == SSL_CVERIFY_OPTIONAL) ||
        (c->verify_mode == SSL_CVERIFY_OPTIONAL_NO_CA))
        verify |= SSL_VERIFY_PEER;

    SSL_CTX_set_verify(c->ctx, verify, SSL_callback_SSL_verify);
   /*
     * Configure Client Authentication details
     */
    if (c->ca_cert_file || c->ca_cert_path) {
        if (!SSL_CTX_load_verify_locations(c->ctx,
                         c->ca_cert_file,
                         c->ca_cert_path)) {
            BIO_printf(c->bio_os, "[ERROR] "
                       "Unable to configure verify locations "
                       "for client authentication");
            return JNI_FALSE;
        }

        if (c->mode && (c->pk.s.ca_name_file || c->pk.s.ca_name_path)) {
            ca_list = SSL_init_findCAList(c,
                                          c->pk.s.ca_name_file,
                                          c->pk.s.ca_name_path);
        }
        else {
            ca_list = SSL_init_findCAList(c,
                                          c->ca_cert_file,
                                          c->ca_cert_path);
        }
        if (!ca_list) {
            BIO_printf(c->bio_os, "[ERROR] "
                       "Unable to determine list of acceptable "
                       "CA certificates for client authentication");
            return JNI_FALSE;
        }
        SSL_CTX_set_client_CA_list(c->ctx, (STACK *)ca_list);
    }

    /*
     * Give a warning when no CAs were configured but client authentication
     * should take place. This cannot work.
     */
    if (c->verify_mode == SSL_CVERIFY_REQUIRE) {
        ca_list = (STACK_OF(X509_NAME) *)SSL_CTX_get_client_CA_list(c->ctx);

        if (sk_X509_NAME_num(ca_list) == 0) {
            BIO_printf(c->bio_os,
                       "[WARN] Oops, you want to request client "
                       "authentication, but no CAs are known for "
                       "verification!?  [Hint: setCACertificate*]");
        }
    }

    return JNI_TRUE;
}

#else
/* OpenSSL is not supported
 * If someday we make OpenSSL optional
 * APR_ENOTIMPL will go here
 */
#error "No OpenSSL Toolkit defined."
#endif
