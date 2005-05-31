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


/* Initialize server context */
TCN_IMPLEMENT_CALL(jlong, SSL, initS)(TCN_STDARGS, jlong pool,
                                      jint protocol)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    tcn_ssl_ctxt_t *c = NULL;
    SSL_CTX *ctx = NULL;
    UNREFERENCED(o);

    switch (protocol) {
        case SSL_PROTOCOL_SSLV2:
            ctx = SSL_CTX_new(SSLv2_server_method());
        break;
        case SSL_PROTOCOL_SSLV3:
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

    return P2J(c);
init_failed:
    return 0;
}

/* Initialize client context */
TCN_IMPLEMENT_CALL(jlong, SSL, initC)(TCN_STDARGS, jlong pool,
                                      jint protocol)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    tcn_ssl_ctxt_t *c = NULL;
    SSL_CTX *ctx = NULL;
    UNREFERENCED(o);

    switch (protocol) {
        case SSL_PROTOCOL_SSLV2:
            ctx = SSL_CTX_new(SSLv2_client_method());
        break;
        case SSL_PROTOCOL_SSLV3:
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

    return P2J(c);
init_failed:
    return 0;
}


#else
/* OpenSSL is not supported
 * If someday we make OpenSSL optional
 * APR_ENOTIMPL will go here
 */
#error "No OpenSSL Toolkit defined."
#endif
