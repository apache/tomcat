/* Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/** SSL network wrapper
 *
 * @author Mladen Turk
 * @version $Id$
 */

#include "tcn.h"
#include "apr_thread_mutex.h"
#include "apr_poll.h"


#ifdef HAVE_OPENSSL
#include "ssl_private.h"

#ifdef TCN_DO_STATISTICS
#include "apr_atomic.h"

static volatile apr_uint32_t ssl_created  = 0;
static volatile apr_uint32_t ssl_closed   = 0;
static volatile apr_uint32_t ssl_cleared  = 0;
static volatile apr_uint32_t ssl_accepted = 0;

void ssl_network_dump_statistics()
{
    fprintf(stderr, "SSL Network Statistics ..\n");
    fprintf(stderr, "Sockets created         : %d\n", ssl_created);
    fprintf(stderr, "Sockets accepted        : %d\n", ssl_accepted);
    fprintf(stderr, "Sockets closed          : %d\n", ssl_closed);
    fprintf(stderr, "Sockets cleared         : %d\n", ssl_cleared);
}

#endif

static int ssl_smart_shutdown(SSL *ssl, int shutdown_type)
{
    int i;
    int rc = 0;

    switch (shutdown_type) {
        case SSL_SHUTDOWN_TYPE_UNCLEAN:
            /* perform no close notify handshake at all
             * (violates the SSL/TLS standard!)
             */
            shutdown_type = SSL_SENT_SHUTDOWN|SSL_RECEIVED_SHUTDOWN;
        break;
        case SSL_SHUTDOWN_TYPE_ACCURATE:
            /* send close notify and wait for clients close notify
             * (standard compliant, but usually causes connection hangs)
             */
            shutdown_type = 0;
        break;
        default:
            /*
             * case SSL_SHUTDOWN_TYPE_UNSET:
             * case SSL_SHUTDOWN_TYPE_STANDARD:
             * send close notify, but don't wait for clients close notify
             * (standard compliant and safe, so it's the DEFAULT!)
             */
            shutdown_type = SSL_RECEIVED_SHUTDOWN;
        break;
    }

    SSL_set_shutdown(ssl, shutdown_type);
    /*
     * Repeat the calls, because SSL_shutdown internally dispatches through a
     * little state machine. Usually only one or two interation should be
     * needed, so we restrict the total number of restrictions in order to
     * avoid process hangs in case the client played bad with the socket
     * connection and OpenSSL cannot recognize it.
     *  max 2x pending + 2x data = 4
     */
    for (i = 0; i < 4; i++) {
        if ((rc = SSL_shutdown(ssl)))
            break;
    }
    return rc;
}

static apr_status_t ssl_cleanup(void *data)
{
    tcn_ssl_conn_t *con = (tcn_ssl_conn_t *)data;

    if (con) {
        /* Pollset was already destroyed by
         * the pool cleanup/destroy.
         */
        con->pollset = NULL;
        if (con->ssl) {
            SSL *ssl = con->ssl;
            con->ssl = NULL;
            ssl_smart_shutdown(ssl, con->shutdown_type);
            SSL_free(ssl);
        }
        if (con->peer) {
            X509_free(con->peer);
            con->peer = NULL;
        }
    }

#ifdef TCN_DO_STATISTICS
    apr_atomic_inc32(&ssl_cleared);
#endif
    return APR_SUCCESS;
}

static tcn_ssl_conn_t *ssl_create(JNIEnv *env, tcn_ssl_ctxt_t *ctx, apr_pool_t *pool)
{
    tcn_ssl_conn_t *con;
    SSL *ssl;

    if ((con = apr_pcalloc(pool, sizeof(tcn_ssl_conn_t))) == NULL) {
        tcn_ThrowAPRException(env, apr_get_os_error());
        return NULL;
    }
    if ((ssl = SSL_new(ctx->ctx)) == NULL) {
        char err[256];
        ERR_error_string(ERR_get_error(), err);
        tcn_Throw(env, "SSL_new failed (%s)", err);
        con = NULL;
        return NULL;
    }
    SSL_clear(ssl);
    con->pool = pool;
    con->ctx  = ctx;
    con->ssl  = ssl;
    con->shutdown_type = ctx->shutdown_type;
    apr_pollset_create(&(con->pollset), 1, pool, 0);

    SSL_set_app_data(ssl, (void *)con);

    if (ctx->mode) {
        /*
         *  Configure callbacks for SSL connection
         */
        SSL_set_tmp_rsa_callback(ssl, SSL_callback_tmp_RSA);
        SSL_set_tmp_dh_callback(ssl,  SSL_callback_tmp_DH);
        SSL_set_session_id_context(ssl, &(ctx->context_id[0]),
                                   sizeof ctx->context_id);
    }
    SSL_set_verify_result(ssl, X509_V_OK);
    SSL_rand_seed(ctx->rand_file);

#ifdef TCN_DO_STATISTICS
    ssl_created++;
#endif
    return con;
}

#ifdef WIN32
#define APR_INVALID_SOCKET  INVALID_SOCKET
#else
#define APR_INVALID_SOCKET  -1
#endif

static apr_status_t wait_for_io_or_timeout(tcn_ssl_conn_t *con,
                                           int for_what,
                                           apr_interval_time_t timeout)
{
    apr_pollfd_t pfd;
    int type;
    apr_status_t status;
    apr_os_sock_t sock;

    if (!con->pollset)
        return APR_ENOPOLL;
    if (!con->sock)
        return APR_ENOTSOCK;
    if (con->reneg_state == RENEG_ABORT) {
        con->shutdown_type = SSL_SHUTDOWN_TYPE_UNCLEAN;
        return APR_ECONNABORTED;
    }

    /* Check if the socket was already closed
     */
    apr_os_sock_get(&sock, con->sock);
    if (sock == APR_INVALID_SOCKET)
        return APR_ENOTSOCK;

    /* Figure out the the poll direction */
    switch (for_what) {
        case SSL_ERROR_WANT_WRITE:
        case SSL_ERROR_WANT_CONNECT:
        case SSL_ERROR_WANT_ACCEPT:
            type = APR_POLLOUT;
        break;
        case SSL_ERROR_WANT_READ:
            type = APR_POLLIN;
        break;
        default:
            return APR_EINVAL;
        break;
    }
    if (timeout <= 0) {
        /* Waiting on zero or infinite timeouts is not allowed
         */
        return APR_EAGAIN;
    }
    pfd.desc_type = APR_POLL_SOCKET;
    pfd.desc.s    = con->sock;
    pfd.reqevents = type;

    /* Remove the object if it was in the pollset, then add in the new
     * object with the correct reqevents value. Ignore the status result
     * on the remove, because it might not be in there (yet).
     */
    apr_pollset_remove(con->pollset, &pfd);

    /* ### check status code */
    apr_pollset_add(con->pollset, &pfd);

    do {
        int numdesc;
        const apr_pollfd_t *pdesc;

        status = apr_pollset_poll(con->pollset, timeout, &numdesc, &pdesc);
        if (numdesc == 1 && (pdesc[0].rtnevents & type) != 0)
            return APR_SUCCESS;
    } while (APR_STATUS_IS_EINTR(status));

    return status;
}

static apr_status_t APR_THREAD_FUNC
ssl_socket_timeout_set(apr_socket_t *sock, apr_interval_time_t t)
{
    tcn_ssl_conn_t *con = (tcn_ssl_conn_t *)sock;
    return apr_socket_timeout_set(con->sock, t);
}

static apr_status_t APR_THREAD_FUNC
ssl_socket_timeout_get(apr_socket_t *sock, apr_interval_time_t *t)
{
    tcn_ssl_conn_t *con = (tcn_ssl_conn_t *)sock;
    return apr_socket_timeout_get(con->sock, t);
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
ssl_socket_opt_set(apr_socket_t *sock, apr_int32_t opt, apr_int32_t on)
{
    tcn_ssl_conn_t *con = (tcn_ssl_conn_t *)sock;
    return apr_socket_opt_set(con->sock, opt, on);
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
ssl_socket_opt_get(apr_socket_t *sock, apr_int32_t opt, apr_int32_t *on)
{
    tcn_ssl_conn_t *con = (tcn_ssl_conn_t *)sock;
    return apr_socket_opt_get(con->sock, opt, on);
}

static apr_status_t APR_THREAD_FUNC
ssl_socket_shutdown(apr_socket_t *sock, apr_shutdown_how_e how)
{
    apr_status_t rv = APR_SUCCESS;
    tcn_ssl_conn_t *con = (tcn_ssl_conn_t *)sock;

    if (con->ssl) {
        SSL *ssl = con->ssl;
        con->ssl = NULL;
        if (how < 1)
            how = con->shutdown_type;
        rv = ssl_smart_shutdown(ssl, how);
        /* TODO: Translate OpenSSL Error codes */
        SSL_free(ssl);
    }
    return rv;
}

static apr_status_t APR_THREAD_FUNC
ssl_socket_close(apr_socket_t *sock)
{
    tcn_ssl_conn_t *con = (tcn_ssl_conn_t *)sock;
    apr_status_t rv = APR_SUCCESS;

#ifdef TCN_DO_STATISTICS
    apr_atomic_inc32(&ssl_closed);
#endif
    if (con->ssl) {
        SSL *ssl = con->ssl;
        con->ssl = NULL;
        rv = ssl_smart_shutdown(ssl, con->shutdown_type);
        SSL_free(ssl);
    }
    if (con->peer) {
        X509_free(con->peer);
        con->peer = NULL;
    }
    return rv;
}

TCN_IMPLEMENT_CALL(jint, SSLSocket, handshake)(TCN_STDARGS, jlong sock)
{
    tcn_socket_t *ss = J2P(sock, tcn_socket_t *);
    tcn_ssl_conn_t *con;
    apr_interval_time_t timeout;
    int s, i;
    long vr;
    apr_status_t rv;
    X509 *peer;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);
    if (ss->net->type != TCN_SOCKET_SSL)
        return APR_EINVAL;
    con = (tcn_ssl_conn_t *)ss->opaque;

    apr_socket_timeout_get(con->sock, &timeout);
    while (!SSL_is_init_finished(con->ssl)) {
        ERR_clear_error();
        if ((s = SSL_do_handshake(con->ssl)) <= 0) {
            if (!con->ssl)
                return APR_ENOTSOCK;
            rv = apr_get_netos_error();
            i  = SSL_get_error(con->ssl, s);
            switch (i) {
                case SSL_ERROR_NONE:
                    con->shutdown_type = SSL_SHUTDOWN_TYPE_STANDARD;
                    return APR_SUCCESS;
                break;
                case SSL_ERROR_WANT_READ:
                case SSL_ERROR_WANT_WRITE:
                    if ((rv = wait_for_io_or_timeout(con, i, timeout)) != APR_SUCCESS) {
                        con->shutdown_type = SSL_SHUTDOWN_TYPE_UNCLEAN;
                        return rv;
                    }
                break;
                case SSL_ERROR_SYSCALL:
#if !defined(_WIN32)
                      if (APR_STATUS_IS_EINTR(rv)) {
                          /* Interrupted by signal */
                          continue;
                      }
#endif
                    /* Fall trough */
                default:
                    /*
                     * Anything else is a fatal error
                     */
                    con->shutdown_type = SSL_SHUTDOWN_TYPE_UNCLEAN;
                    return SSL_TO_APR_ERROR(i);
                break;
            }
        }
        if (!con->ssl)
            return APR_ENOTSOCK;

        /*
        * Check for failed client authentication
        */
        if ((vr = SSL_get_verify_result(con->ssl)) != X509_V_OK) {
            if (SSL_VERIFY_ERROR_IS_OPTIONAL(vr) &&
                con->ctx->verify_mode == SSL_CVERIFY_OPTIONAL_NO_CA) {
                /* TODO: Log optionalNoCA */
            }
            else {
                /* TODO: Log SSL client authentication failed */
                con->shutdown_type = SSL_SHUTDOWN_TYPE_UNCLEAN;
                /* TODO: Figure out the correct return value */
                return APR_EGENERAL;
            }
        }

        /*
         * Remember the peer certificate
         */
        if ((peer = SSL_get_peer_certificate(con->ssl)) != NULL) {
            if (con->peer)
                X509_free(con->peer);
            con->peer = peer;
        }
    }
    return APR_SUCCESS;
}

static apr_status_t APR_THREAD_FUNC
ssl_socket_recv(apr_socket_t *sock, char *buf, apr_size_t *len)
{
    tcn_ssl_conn_t *con = (tcn_ssl_conn_t *)sock;
    int s, i, rd = (int)(*len);
    apr_status_t rv;
    apr_interval_time_t timeout;

    *len = 0;
    if (con->reneg_state == RENEG_ABORT) {
        con->shutdown_type = SSL_SHUTDOWN_TYPE_UNCLEAN;
        return APR_ECONNABORTED;
    }
    apr_socket_timeout_get(con->sock, &timeout);
    for (;;) {
        ERR_clear_error();
        if ((s = SSL_read(con->ssl, buf, rd)) <= 0) {
            if (!con->ssl)
                return APR_ENOTSOCK;
            rv  = apr_get_netos_error();
            i   = SSL_get_error(con->ssl, s);
            /* Special case if the "close notify" alert send by peer */
            if (s == 0 && (SSL_get_shutdown(con->ssl) & SSL_RECEIVED_SHUTDOWN)) {
                con->shutdown_type = SSL_SHUTDOWN_TYPE_STANDARD;
                return APR_EOF;
            }
            switch (i) {
                case SSL_ERROR_WANT_READ:
                case SSL_ERROR_WANT_WRITE:
                    if ((rv = wait_for_io_or_timeout(con, i, timeout)) != APR_SUCCESS) {
                        con->shutdown_type = SSL_SHUTDOWN_TYPE_UNCLEAN;
                        return rv;
                    }
                break;
                case SSL_ERROR_SYSCALL:
                    if (APR_STATUS_IS_EPIPE(rv) || APR_STATUS_IS_ECONNRESET(rv)) {
                        con->shutdown_type = SSL_SHUTDOWN_TYPE_STANDARD;
                        return APR_EOF;
                    }
#if !defined(_WIN32)
                    else if (APR_STATUS_IS_EINTR(rv)) {
                        /* Interrupted by signal
                         */
                        continue;
                    }
#endif
                    /* Fall trough */
                case SSL_ERROR_ZERO_RETURN:
                    if (s == 0) {
                        con->shutdown_type = SSL_SHUTDOWN_TYPE_STANDARD;
                        return APR_EOF;
                    }
                    /* Fall trough */
                default:
                    con->shutdown_type = SSL_SHUTDOWN_TYPE_UNCLEAN;
                    return APR_EGENERAL;
                break;
            }
        }
        else {
            *len = s;
            con->shutdown_type = SSL_SHUTDOWN_TYPE_STANDARD;
            break;
        }
    }
    return APR_SUCCESS;
}

static apr_status_t APR_THREAD_FUNC
ssl_socket_send(apr_socket_t *sock, const char *buf,
                apr_size_t *len)
{
    tcn_ssl_conn_t *con = (tcn_ssl_conn_t *)sock;
    int s, i, wr = (int)(*len);
    apr_status_t rv;
    apr_interval_time_t timeout;

    *len = 0;
    if (con->reneg_state == RENEG_ABORT) {
        con->shutdown_type = SSL_SHUTDOWN_TYPE_UNCLEAN;
        return APR_ECONNABORTED;
    }
    if (!SSL_is_init_finished(con->ssl)) {
        /* XXX: Is this a correct retval ? */ 
        return APR_EINPROGRESS;
    }
    if (wr == 0) {
        /* According to docs calling SSL_write() with num=0 bytes
         * to be sent the behaviour is undefined.
         */
        return APR_EINVAL;
    }
    apr_socket_timeout_get(con->sock, &timeout);
    for (;;) {
        ERR_clear_error();
        if ((s = SSL_write(con->ssl, buf, wr)) <= 0) {
            if (!con->ssl)
                return APR_ENOTSOCK;
            rv  = apr_get_netos_error();
            i   = SSL_get_error(con->ssl, s);
            switch (i) {
                case SSL_ERROR_WANT_READ:
                case SSL_ERROR_WANT_WRITE:
                    if ((rv = wait_for_io_or_timeout(con, i, timeout)) != APR_SUCCESS) {
                        con->shutdown_type = SSL_SHUTDOWN_TYPE_UNCLEAN;
                        return rv;
                    }
                break;
                case SSL_ERROR_SYSCALL:
                    if (s == -1) {
                        if (APR_STATUS_IS_EPIPE(rv) || APR_STATUS_IS_ECONNRESET(rv)) {
                            con->shutdown_type = SSL_SHUTDOWN_TYPE_STANDARD;
                            return APR_EOF;
                        }
#if !defined(_WIN32)
                        else if (APR_STATUS_IS_EINTR(rv)) {
                            /* Interrupted by signal
                             */
                            continue;
                        }
#endif
                    }
                    con->shutdown_type = SSL_SHUTDOWN_TYPE_UNCLEAN;
                    return rv;
                break;
                case SSL_ERROR_SSL:
                    /* Probably caused by buffer missmatch */
                    rv = APR_EINVAL;
                case SSL_ERROR_ZERO_RETURN:
                    if (s == 0) {
                        con->shutdown_type = SSL_SHUTDOWN_TYPE_STANDARD;
                        return APR_EOF;
                    }
                    /* Fall trough */
                default:
                    con->shutdown_type = SSL_SHUTDOWN_TYPE_UNCLEAN;
                    return rv;
                break;
            }
        }
        else {
            *len = s;
            break;
        }
    }
    return APR_SUCCESS;
}

static apr_status_t APR_THREAD_FUNC
ssl_socket_sendv(apr_socket_t *sock,
                 const struct iovec *vec,
                 apr_int32_t nvec, apr_size_t *len)
{
    tcn_ssl_conn_t *con = (tcn_ssl_conn_t *)sock;
    apr_status_t rv;
    apr_size_t written = 0;
    apr_int32_t i;

    for (i = 0; i < nvec; i++) {
        apr_size_t rd = vec[i].iov_len;
        if ((rv = ssl_socket_send((apr_socket_t *)con,
                                  vec[i].iov_base, &rd)) != APR_SUCCESS) {
            *len = written;
            return rv;
        }
        written += rd;
    }
    *len = written;
    return APR_SUCCESS;
}

static tcn_nlayer_t ssl_socket_layer = {
    TCN_SOCKET_SSL,
    ssl_cleanup,
    ssl_socket_close,
    ssl_socket_shutdown,
    ssl_socket_opt_get,
    ssl_socket_opt_set,
    ssl_socket_timeout_get,
    ssl_socket_timeout_set,
    ssl_socket_send,
    ssl_socket_sendv,
    ssl_socket_recv
};


TCN_IMPLEMENT_CALL(jint, SSLSocket, attach)(TCN_STDARGS, jlong ctx,
                                            jlong sock)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    tcn_socket_t *s   = J2P(sock, tcn_socket_t *);
    tcn_ssl_conn_t *con;
    apr_os_sock_t  oss;
    apr_status_t rv;

    UNREFERENCED(o);
    TCN_ASSERT(ctx != 0);
    TCN_ASSERT(sock != 0);

    if (!s->sock)
        return APR_ENOTSOCK;

    if ((rv = apr_os_sock_get(&oss, s->sock)) != APR_SUCCESS)
        return rv;
    if (oss == APR_INVALID_SOCKET)
        return APR_ENOTSOCK;

    if ((con = ssl_create(e, c, s->pool)) == NULL)
        return APR_EGENERAL;
    con->sock = s->sock;

    SSL_set_fd(con->ssl, (int)oss);
    if (c->mode)
        SSL_set_accept_state(con->ssl);
    else
        SSL_set_connect_state(con->ssl);
    /* Change socket type */
    s->net    = &ssl_socket_layer;
    s->opaque = con;

    return APR_SUCCESS;
}

TCN_IMPLEMENT_CALL(jint, SSLSocket, renegotiate)(TCN_STDARGS,
                                                 jlong sock)
{
    tcn_socket_t *s   = J2P(sock, tcn_socket_t *);
    tcn_ssl_conn_t *con;
    int retVal;
    int ecode = SSL_ERROR_WANT_READ;
    apr_status_t rv;
    apr_interval_time_t timeout;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);
    con = (tcn_ssl_conn_t *)s->opaque;

    /* Sequence to renegotiate is
     *  SSL_renegotiate()
     *  SSL_do_handshake()
     *  ssl->state = SSL_ST_ACCEPT
     *  SSL_do_handshake()
     */

    /* Toggle the renegotiation state to allow the new
     * handshake to proceed.
     */
    con->reneg_state = RENEG_ALLOW;
    retVal = SSL_renegotiate(con->ssl);
    if (retVal <= 0)
        return APR_EGENERAL;

    retVal = SSL_do_handshake(con->ssl);
    if (retVal <= 0)
        return APR_EGENERAL;

    if (SSL_get_state(con->ssl) != SSL_ST_OK) {
        return APR_EGENERAL;
    }
    SSL_set_state(con->ssl, SSL_ST_ACCEPT);

    apr_socket_timeout_get(con->sock, &timeout);
    ecode = SSL_ERROR_WANT_READ;
    while (ecode == SSL_ERROR_WANT_READ) {
        retVal = SSL_do_handshake(con->ssl);
        if (retVal <= 0) {
            ecode = SSL_get_error(con->ssl, retVal);
            if (ecode == SSL_ERROR_WANT_READ) {
                if ((rv = wait_for_io_or_timeout(con, ecode, timeout)) != APR_SUCCESS)
                    return rv; /* Can't wait */
                continue; /* It should be ok now */
            }
            else
                return APR_EGENERAL;
        } else
            break;
    }
    con->reneg_state = RENEG_REJECT;

    if (SSL_get_state(con->ssl) != SSL_ST_OK) {
        return APR_EGENERAL;
    }

    return APR_SUCCESS;
}

TCN_IMPLEMENT_CALL(void, SSLSocket, setVerify)(TCN_STDARGS,
                                               jlong sock,
                                               jint cverify,
                                               jint depth)
{
    tcn_socket_t *s   = J2P(sock, tcn_socket_t *);
    tcn_ssl_conn_t *con;
    int verify = SSL_VERIFY_NONE;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);
    con = (tcn_ssl_conn_t *)s->opaque;

    if (cverify == SSL_CVERIFY_UNSET)
        cverify = SSL_CVERIFY_NONE;
    if (depth > 0)
        SSL_set_verify_depth(con->ssl, depth);

    if (cverify == SSL_CVERIFY_REQUIRE)
        verify |= SSL_VERIFY_PEER_STRICT;
    if ((cverify == SSL_CVERIFY_OPTIONAL) ||
        (cverify == SSL_CVERIFY_OPTIONAL_NO_CA))
        verify |= SSL_VERIFY_PEER;

    SSL_set_verify(con->ssl, verify, NULL);
}

#else
/* OpenSSL is not supported.
 * Create empty stubs.
 */

TCN_IMPLEMENT_CALL(jint, SSLSocket, handshake)(TCN_STDARGS, jlong sock)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(sock);
    return (jint)APR_ENOTIMPL;
}

TCN_IMPLEMENT_CALL(jint, SSLSocket, attach)(TCN_STDARGS, jlong ctx,
                                            jlong sock)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(ctx);
    UNREFERENCED(sock);
    return (jint)APR_ENOTIMPL;
}

TCN_IMPLEMENT_CALL(jint, SSLSocket, renegotiate)(TCN_STDARGS,
                                                 jlong sock)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(sock);
    return (jint)APR_ENOTIMPL;
}

#endif
