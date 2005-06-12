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

/** SSL network wrapper
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

#include "apr.h"
#include "apr_pools.h"
#include "apr_file_io.h"
#include "apr_portable.h"
#include "apr_thread_mutex.h"
#include "apr_poll.h"

#include "tcn.h"

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

static apr_status_t ssl_socket_cleanup(void *data)
{
    tcn_ssl_conn_t *con = (tcn_ssl_conn_t *)data;

    if (con) {
        if (con->ssl) {
            ssl_smart_shutdown(con->ssl, con->shutdown_type);
            SSL_free(con->ssl);
            con->ssl = NULL;
        }
        if (con->peer) {
            X509_free(con->peer);
            con->peer = NULL;
        }
        if (con->sock) {
            apr_socket_close(con->sock);
            con->sock = NULL;
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

    apr_pool_cleanup_register(pool, (const void *)con,
                              ssl_socket_cleanup,
                              apr_pool_cleanup_null);
    SSL_set_app_data(ssl, (void *)con);

    if (ctx->mode) {
        /*
         *  Configure callbacks for SSL connection
         */
        SSL_set_tmp_rsa_callback(ssl, SSL_callback_tmp_RSA);
        SSL_set_tmp_dh_callback(ssl,  SSL_callback_tmp_DH);
        SSL_set_session_id_context(ssl, &(ctx->context_id[0]),
                                   MD5_DIGEST_LENGTH);
    }
    SSL_set_verify_result(ssl, X509_V_OK);


#ifdef TCN_DO_STATISTICS
    ssl_created++;
#endif
    return con;
}

static apr_status_t wait_for_io_or_timeout(tcn_ssl_conn_t *con,
                                           int for_what)
{
    apr_interval_time_t timeout;
    apr_pollfd_t pfd;
    int type = for_what == SSL_ERROR_WANT_WRITE ? APR_POLLOUT : APR_POLLIN;
    apr_status_t status;

    apr_socket_timeout_get(con->sock, &timeout);
    pfd.desc_type = APR_POLL_SOCKET;
    pfd.desc.s = con->sock;
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

TCN_IMPLEMENT_CALL(jint, SSLSocket, shutdown)(TCN_STDARGS, jlong sock,
                                              jint how)
{
    apr_status_t rv = APR_SUCCESS;
    tcn_ssl_conn_t *con = J2P(sock, tcn_ssl_conn_t *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);
    if (con->ssl) {
        if (how < 1)
            how = con->shutdown_type;
        rv = ssl_smart_shutdown(con->ssl, how);
        /* TODO: Translate OpenSSL Error codes */
        SSL_free(con->ssl);
        con->ssl = NULL;
    }
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, SSLSocket, close)(TCN_STDARGS, jlong sock)
{
    tcn_ssl_conn_t *con = J2P(sock, tcn_ssl_conn_t *);
    apr_status_t rv = APR_SUCCESS;
    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);

#ifdef TCN_DO_STATISTICS
    apr_atomic_inc32(&ssl_closed);
#endif
    apr_pool_cleanup_kill(con->pool, con, ssl_socket_cleanup);
    if (con->ssl) {
        rv = ssl_smart_shutdown(con->ssl, con->shutdown_type);
        SSL_free(con->ssl);
        con->ssl = NULL;
    }
    if (con->peer) {
        X509_free(con->peer);
        con->peer = NULL;
    }
    if (con->sock) {
        apr_status_t rc;
        if ((rc = apr_socket_close(con->sock)) != APR_SUCCESS)
            rv = rc;
        con->sock = NULL;
    }
    return (jint)rv;
}

#define JFC_TEST 0
#if JFC_TEST
/*
 * Use APR sockets directly
 */

static int jbs_apr_new(BIO *bi)
{
    printf("jbs_apr_new\n");
    fflush(stdout);
    bi->shutdown = 1;
    bi->init     = 0;
    bi->num      = -1;
    bi->ptr      = NULL;
    return 1;
}

static int jbs_apr_free(BIO *bi)
{
    if (bi == NULL)
        return 0;
    else
        return 1;
}

static int jbs_apr_write(BIO *b, const char *in, int inl)
{
    apr_size_t j = inl;
    apr_socket_t *sock=b->ptr;
    printf("jbs_apr_write\n");
    fflush(stdout);
    return(apr_socket_send(sock, in, &j));
}

static int jbs_apr_read(BIO *b, char *out, int outl)
{
    apr_size_t j = outl;
    apr_socket_t *sock=b->ptr;
    int ret;
    printf("jbs_apr_read\n");
    fflush(stdout);
    ret = apr_socket_recv(sock, out, &j);
    if (ret == APR_SUCCESS)
      return(j);
    return(-1);
}

static int jbs_apr_puts(BIO *b, const char *in)
{
    return 0;
}

static int jbs_apr_gets(BIO *b, char *out, int outl)
{
    return 0;
}

static long jbs_apr_ctrl(BIO *b, int cmd, long num, void *ptr)
{
    printf("jbs_apr_ctrl\n");
    fflush(stdout);
    if (cmd==BIO_CTRL_FLUSH || cmd==BIO_CTRL_DUP)
      return 1;
    else
      return 0;
}
static BIO_METHOD jbs_apr_methods = {
    BIO_TYPE_FILE,
    "APR Callback",
    jbs_apr_write,
    jbs_apr_read,
    jbs_apr_puts,
    jbs_apr_gets,
    jbs_apr_ctrl,
    jbs_apr_new,
    jbs_apr_free,
    NULL
};
static BIO_METHOD *BIO_jbs_apr()
{
    return(&jbs_apr_methods);
}

TCN_IMPLEMENT_CALL(jint, SSLSocket, geterror)(TCN_STDARGS, jlong ctx, jint retcode)
{
    tcn_ssl_conn_t *c = J2P(ctx, tcn_ssl_conn_t *);
    UNREFERENCED_STDARGS;
    TCN_ASSERT(ctx != 0);
    printf("geterror for %d state: %.08x\n", retcode, c->ssl->state);
    perror("geterror");
    fflush(stdout);
    return SSL_get_error(c->ssl, retcode);
}

TCN_IMPLEMENT_CALL(jlong, SSLSocket, accept)(TCN_STDARGS, jlong ctx,
                                             jlong sock, jlong pool)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    apr_socket_t *s   = J2P(sock, apr_socket_t *);
    apr_pool_t *p     = J2P(pool, apr_pool_t *);
    tcn_ssl_conn_t *con;
    BIO *bio = NULL;
    int retcode;

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);
    TCN_ASSERT(ctx != 0);
    TCN_ASSERT(sock != 0);

    if ((con = ssl_create(e, c, p)) == NULL) {
        tcn_ThrowException(e, "Create SSL failed");
        return 0;
    }
    con->sock = s;

    if ((bio = BIO_new(BIO_jbs_apr())) == NULL) {
        tcn_ThrowException(e, "Create BIO failed");
        return 0;
    }
    bio->ptr = s;

    /* XXX cleanup ??? */

    /* the bio */
    SSL_set_bio(con->ssl, bio, bio);

    /* do the handshake*/
    retcode = SSL_accept(con->ssl);
    if (retcode<=0) {
        printf("SSL_accept failed %d state: %.08x\n", retcode, con->ssl->state);
        printf("SSL_accept %p cert\n", con->ssl->cert);
        tcn_ThrowException(e, "Create SSL_accept failed");
        return 0;
    }

cleanup:
    return P2J(con);
}

#endif /* JFC_TEST */

TCN_IMPLEMENT_CALL(jlong, SSLSocket, attach)(TCN_STDARGS, jlong ctx,
                                             jlong sock, jlong pool)
{
    tcn_ssl_ctxt_t *c = J2P(ctx, tcn_ssl_ctxt_t *);
    apr_socket_t *s   = J2P(sock, apr_socket_t *);
    apr_pool_t *p     = J2P(pool, apr_pool_t *);
    tcn_ssl_conn_t *con;
    apr_os_sock_t  oss;

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);
    TCN_ASSERT(ctx != 0);
    TCN_ASSERT(sock != 0);

    if ((con = ssl_create(e, c, p)) == NULL)
        return 0;
    TCN_THROW_IF_ERR(apr_os_sock_get(&oss, s), c);
    con->sock = s;

    SSL_set_fd(con->ssl, (int)oss);
    if (c->mode)
        SSL_set_accept_state(con->ssl);
    else
        SSL_set_connect_state(con->ssl);

cleanup:
    return P2J(con);
}

TCN_IMPLEMENT_CALL(jint, SSLSocket, handshake)(TCN_STDARGS, jlong sock)
{
    tcn_ssl_conn_t *con = J2P(sock, tcn_ssl_conn_t *);
    int s;
    apr_status_t rv;
    X509 *peer;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);

    while (!SSL_is_init_finished(con->ssl)) {
        if ((s = SSL_do_handshake(con->ssl)) <= 0) {
            int i = SSL_get_error(con->ssl, s);
            switch (i) {
                case SSL_ERROR_NONE:
                    return APR_SUCCESS;
                break;
                case SSL_ERROR_WANT_READ:
                case SSL_ERROR_WANT_WRITE:
                    if ((rv = wait_for_io_or_timeout(con, i)) != APR_SUCCESS) {
                        return rv;
                    }
                break;
                case SSL_ERROR_SYSCALL:
                    s = apr_get_netos_error();
                    if (!APR_STATUS_IS_EAGAIN(s) &&
                        !APR_STATUS_IS_EINTR(s)) {
                        con->shutdown_type = SSL_SHUTDOWN_TYPE_STANDARD;
                        return s;
                    }
                break;
                default:
                    /*
                    * Anything else is a fatal error
                    */
                    con->shutdown_type = SSL_SHUTDOWN_TYPE_STANDARD;
                    return SSL_TO_APR_ERROR(i);
                break;
            }
        }
        /*
        * Check for failed client authentication
        */
        if (SSL_get_verify_result(con->ssl) != X509_V_OK) {
            /* TODO: Log SSL client authentication failed */
            con->shutdown_type = SSL_SHUTDOWN_TYPE_STANDARD;
            /* TODO: Figure out the correct return value */
            return APR_EGENERAL;
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
    fprintf(stderr, "Handshake done\n");
    fflush(stderr);
    return APR_SUCCESS;
}

static apr_status_t ssl_socket_recv(tcn_ssl_conn_t *con, char *buf, apr_size_t *len)
{
    int s, rd = (int)(*len);
    apr_status_t rv = APR_SUCCESS;

    for (;;) {
        if ((s = SSL_read(con->ssl, buf, rd)) <= 0) {
            int i = SSL_get_error(con->ssl, s);
            /* Special case if the "close notify" alert send by peer */
            if (s == 0 && (con->ssl->shutdown & SSL_RECEIVED_SHUTDOWN)) {
                *len = 0;
                return APR_EOF;
            }
            switch (i) {
                case SSL_ERROR_ZERO_RETURN:
                    *len = 0;
                    return APR_EOF;
                break;
                case SSL_ERROR_WANT_READ:
                case SSL_ERROR_WANT_WRITE:
                    if ((rv = wait_for_io_or_timeout(con, i)) != APR_SUCCESS) {
                        return rv;
                    }
                break;
                case SSL_ERROR_SYSCALL:
                    s = apr_get_netos_error();
                    if (!APR_STATUS_IS_EAGAIN(s) &&
                        !APR_STATUS_IS_EINTR(s)) {
                        con->shutdown_type = SSL_SHUTDOWN_TYPE_STANDARD;
                        return s;
                    }
                break;
                default:
                    return apr_get_netos_error();
                break;
            }
        }
        else {
            *len = s;
            break;
        }
    }
    return rv;
}

static apr_status_t ssl_socket_send(tcn_ssl_conn_t *con, const char *buf,
                                    apr_size_t *len)
{
    int s, rd = (int)(*len);
    apr_status_t rv = APR_SUCCESS;

    for (;;) {
        if ((s = SSL_write(con->ssl, buf, rd)) <= 0) {
            int i = SSL_get_error(con->ssl, s);
            switch (i) {
                case SSL_ERROR_ZERO_RETURN:
                    *len = 0;
                    return APR_EOF;
                break;
                case SSL_ERROR_WANT_READ:
                case SSL_ERROR_WANT_WRITE:
                    if ((rv = wait_for_io_or_timeout(con, i)) != APR_SUCCESS) {
                        return rv;
                    }
                break;
                case SSL_ERROR_SYSCALL:
                    s = apr_get_netos_error();
                    if (!APR_STATUS_IS_EAGAIN(s) &&
                        !APR_STATUS_IS_EINTR(s)) {
                        con->shutdown_type = SSL_SHUTDOWN_TYPE_STANDARD;
                        return s;
                    }
                break;
                default:
                    return apr_get_netos_error();
                break;
            }
        }
        else {
            *len = s;
            break;
        }
    }
    return rv;
}

TCN_IMPLEMENT_CALL(jint, SSLSocket, send)(TCN_STDARGS, jlong sock,
                                          jbyteArray buf, jint offset,
                                          jint tosend)
{
    tcn_ssl_conn_t *s = J2P(sock, tcn_ssl_conn_t *);
    apr_size_t nbytes = (apr_size_t)tosend;
    jbyte *bytes;
    apr_int32_t nb;
    apr_status_t ss;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);
    apr_socket_opt_get(s->sock, APR_SO_NONBLOCK, &nb);
    if (nb)
         bytes = (*e)->GetPrimitiveArrayCritical(e, buf, NULL);
    else
         bytes = (*e)->GetByteArrayElements(e, buf, NULL);
    ss = ssl_socket_send(s, bytes + offset, &nbytes);

    if (nb)
        (*e)->ReleasePrimitiveArrayCritical(e, buf, bytes, JNI_ABORT);
    else
        (*e)->ReleaseByteArrayElements(e, buf, bytes, JNI_ABORT);
    if (ss == APR_SUCCESS)
        return (jint)nbytes;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, SSLSocket, recv)(TCN_STDARGS, jlong sock,
                                          jbyteArray buf, jint offset,
                                          jint toread)
{
    tcn_ssl_conn_t *s = J2P(sock, tcn_ssl_conn_t *);
    apr_size_t nbytes = (apr_size_t)toread;
    jbyte *bytes = (*e)->GetByteArrayElements(e, buf, NULL);
    apr_status_t ss;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);
    TCN_ASSERT(bytes != NULL);
    ss = ssl_socket_recv(s, bytes + offset, &nbytes);

    (*e)->ReleaseByteArrayElements(e, buf, bytes,
                                   nbytes ? 0 : JNI_ABORT);
    if (ss == APR_SUCCESS)
        return (jint)nbytes;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

#else
/* OpenSSL is not supported
 * If someday we make OpenSSL optional
 * APR_ENOTIMPL will go here
 */
#error "No OpenSSL Toolkit defined."
#endif
