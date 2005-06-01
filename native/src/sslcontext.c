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
    c->bio_is = BIO_new(BIO_s_file());
    if (c->bio_os != NULL)
        BIO_set_fp(c->bio_os, stderr, BIO_NOCLOSE | BIO_FP_TEXT);
    if (c->bio_is != NULL) {
        BIO_set_fp(c->bio_is, stdin, BIO_NOCLOSE | BIO_FP_TEXT);
        c->bio_is->flags = SSL_BIO_FLAG_RDONLY;
    }
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
    c->bio_is = BIO_new(BIO_s_file());
    if (c->bio_os != NULL)
        BIO_set_fp(c->bio_os, stderr, BIO_NOCLOSE | BIO_FP_TEXT);
    if (c->bio_is != NULL) {
        BIO_set_fp(c->bio_is, stdin, BIO_NOCLOSE | BIO_FP_TEXT);
        c->bio_is->flags = SSL_BIO_FLAG_RDONLY;
    }
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
    if (J2S(id))
        MD5((const unsigned char *)J2S(id), (unsigned long)strlen(J2S(id)),
            &(c->vhost_id[0]));

    TCN_FREE_CSTRING(id);
}

TCN_IMPLEMENT_CALL(void, SSLContext, setErrBIO)(TCN_STDARGS, jlong ctx,
                                                jlong bio)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    BIO *bio_os       = J2P(bio, BIO *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(ctx != 0);
    if (c->bio_os && c->bio_os != bio_os)
        SSL_BIO_close(c->bio_os);
    SSL_BIO_doref(bio_os);
    c->bio_os = bio_os;
}

TCN_IMPLEMENT_CALL(void, SSLContext, setPPromptBIO)(TCN_STDARGS, jlong ctx,
                                                    jlong bio)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    BIO *bio_is       = J2P(bio, BIO *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(ctx != 0);
    if (c->bio_is && c->bio_is != bio_is)
        SSL_BIO_close(c->bio_is);
    SSL_BIO_doref(bio_is);
    c->bio_is = bio_is;
}


#else
/* OpenSSL is not supported
 * If someday we make OpenSSL optional
 * APR_ENOTIMPL will go here
 */
#error "No OpenSSL Toolkit defined."
#endif
