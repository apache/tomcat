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

/*
 *
 * @author Mladen Turk
 * @version $Id$
 */

#include "tcn.h"

#ifdef TCN_DO_STATISTICS

#include "apr_atomic.h"

static volatile apr_uint32_t sp_created  = 0;
static volatile apr_uint32_t sp_closed   = 0;
static volatile apr_uint32_t sp_cleared  = 0;
static volatile apr_uint32_t sp_accepted = 0;
static volatile apr_uint32_t sp_max_send = 0;
static volatile apr_uint32_t sp_min_send = 10000000;
static volatile apr_uint32_t sp_num_send = 0;
static volatile apr_off_t    sp_tot_send = 0;
static volatile apr_uint32_t sp_max_recv = 0;
static volatile apr_uint32_t sp_min_recv = 10000000;
static volatile apr_uint32_t sp_num_recv = 0;
static volatile apr_off_t    sp_tot_recv = 0;
static volatile apr_uint32_t sp_err_recv = 0;
static volatile apr_uint32_t sp_tmo_recv = 0;
static volatile apr_uint32_t sp_rst_recv = 0;
static volatile apr_status_t sp_erl_recv = 0;

static volatile apr_size_t   sf_max_send = 0;
static volatile apr_size_t   sf_min_send = 10000000;
static volatile apr_uint32_t sf_num_send = 0;
static volatile apr_off_t    sf_tot_send = 0;

void sp_network_dump_statistics()
{
    fprintf(stderr, "Network Statistics ......\n");
    fprintf(stderr, "Sockets created         : %d\n", sp_created);
    fprintf(stderr, "Sockets accepted        : %d\n", sp_accepted);
    fprintf(stderr, "Sockets closed          : %d\n", sp_closed);
    fprintf(stderr, "Sockets cleared         : %d\n", sp_cleared);
    fprintf(stderr, "Total send calls        : %d\n", sp_num_send);
    fprintf(stderr, "Minimum send length     : %d\n", sp_min_send);
    fprintf(stderr, "Maximum send length     : %d\n", sp_max_send);
    fprintf(stderr, "Average send length     : %.2f\n", (double)sp_tot_send/(double)sp_num_send);
    fprintf(stderr, "Total recv calls        : %d\n", sp_num_recv);
    fprintf(stderr, "Minimum recv length     : %d\n", sp_min_recv);
    fprintf(stderr, "Maximum recv length     : %d\n", sp_max_recv);
    fprintf(stderr, "Average recv length     : %.2f\n", (double)sp_tot_recv/(double)sp_num_recv);
    fprintf(stderr, "Receive timeouts        : %d\n", sp_tmo_recv);
    fprintf(stderr, "Receive errors          : %d\n", sp_err_recv);
    fprintf(stderr, "Receive resets          : %d\n", sp_rst_recv);
    fprintf(stderr, "Last receive error      : %d\n", sp_erl_recv);

    fprintf(stderr, "Total sendfile calls    : %d\n", sf_num_send);
    fprintf(stderr, "Minimum sendfile length : %" APR_SIZE_T_FMT "\n", sf_min_send);
    fprintf(stderr, "Maximum sendfile length : %" APR_SIZE_T_FMT "\n", sf_max_send);

}

#endif /* TCN_DO_STATISTICS */

extern apr_pool_t *tcn_global_pool;
static apr_status_t sp_socket_cleanup(void *data)
{
    tcn_socket_t *s = (tcn_socket_t *)data;

    if (s->net && s->net->cleanup)
        (*s->net->cleanup)(s->opaque);
    if (s->sock) {
        apr_socket_t *as = s->sock;
        s->sock = NULL;
        apr_socket_close(as);
    }
#ifdef TCN_DO_STATISTICS
    apr_atomic_inc32(&sp_cleared);
#endif
    return APR_SUCCESS;
}

#if defined(DEBUG) || defined(_DEBUG)
static APR_INLINE apr_status_t APR_THREAD_FUNC
APR_socket_send(apr_socket_t *sock, const char *buf, apr_size_t *len)
{
    return apr_socket_send(sock, buf, len);
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
APR_socket_recv(apr_socket_t *sock, char *buf, apr_size_t *len)
{
    return apr_socket_recv(sock, buf, len);
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
APR_socket_sendv(apr_socket_t *sock, const struct iovec *vec,
                 apr_int32_t nvec, apr_size_t *len)
{
    return apr_socket_sendv(sock, vec, nvec, len);
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
APR_socket_shutdown(apr_socket_t *sock, apr_shutdown_how_e how)
{
    return apr_socket_shutdown(sock, how);
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
APR_socket_timeout_set(apr_socket_t *sock, apr_interval_time_t t)
{
    return apr_socket_timeout_set(sock, t);
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
APR_socket_timeout_get(apr_socket_t *sock, apr_interval_time_t *t)
{
    return apr_socket_timeout_get(sock, t);
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
APR_socket_opt_set(apr_socket_t *sock, apr_int32_t opt, apr_int32_t on)
{
    return apr_socket_opt_set(sock, opt, on);
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
APR_socket_opt_get(apr_socket_t *sock, apr_int32_t opt, apr_int32_t *on)
{
    return apr_socket_opt_get(sock, opt, on);
}

#else
#define APR_socket_send         apr_socket_send
#define APR_socket_recv         apr_socket_recv
#define APR_socket_sendv        apr_socket_sendv
#define APR_socket_shutdown     apr_socket_shutdown
#define APR_socket_timeout_set  apr_socket_timeout_set
#define APR_socket_timeout_get  apr_socket_timeout_get
#define APR_socket_opt_set      apr_socket_opt_set
#define APR_socket_opt_get      apr_socket_opt_get
#endif

static tcn_nlayer_t apr_socket_layer = {
    TCN_SOCKET_APR,
    NULL,
    NULL,
    APR_socket_shutdown,
    APR_socket_opt_get,
    APR_socket_opt_set,
    APR_socket_timeout_get,
    APR_socket_timeout_set,
    APR_socket_send,
    APR_socket_sendv,
    APR_socket_recv
};

TCN_IMPLEMENT_CALL(jlong, Socket, create)(TCN_STDARGS, jint family,
                                          jint type, jint protocol,
                                          jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_pool_t *c = NULL;
    apr_socket_t *s = NULL;
    tcn_socket_t *a = NULL;
    apr_int32_t f, t;

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);
    GET_S_FAMILY(f, family);
    GET_S_TYPE(t, type);

    TCN_THROW_IF_ERR(apr_pool_create(&c, p), c);

    a = (tcn_socket_t *)apr_pcalloc(c, sizeof(tcn_socket_t));
    TCN_CHECK_ALLOCATED(a);
    TCN_THROW_IF_ERR(apr_pool_create(&a->child, c), a->child);
    a->pool = c;

    if (family >= 0) {
        a->net = &apr_socket_layer;
        TCN_THROW_IF_ERR(apr_socket_create(&s,
                         f, t, protocol, c), a);
    }
    apr_pool_cleanup_register(c, (const void *)a,
                              sp_socket_cleanup,
                              apr_pool_cleanup_null);

#ifdef TCN_DO_STATISTICS
    sp_created++;
#endif
    a->sock = s;
    if (family >= 0)
        a->net = &apr_socket_layer;
    a->opaque  = s;
    return P2J(a);
cleanup:
    if (c)
        apr_pool_destroy(c);
    return 0;

}

TCN_IMPLEMENT_CALL(void, Socket, destroy)(TCN_STDARGS, jlong sock)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_socket_t *as;
    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);

    as = s->sock;
    s->sock = NULL;
    apr_pool_cleanup_kill(s->pool, s, sp_socket_cleanup);
    if (s->net && s->net->cleanup) {
        (*s->net->cleanup)(s->opaque);
        s->net = NULL;
    }
    if (as) {
        apr_socket_close(as);
    }

    apr_pool_destroy(s->pool);
}

TCN_IMPLEMENT_CALL(jlong, Socket, pool)(TCN_STDARGS, jlong sock)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_pool_t *n;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);
    TCN_THROW_IF_ERR(apr_pool_create(&n, s->pool), n);
cleanup:
    return P2J(n);
}

TCN_IMPLEMENT_CALL(jlong, Socket, get)(TCN_STDARGS, jlong sock, jint what)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);

    switch (what) {
        case TCN_SOCKET_GET_POOL:
            return P2J(s->pool);
        break;
        case TCN_SOCKET_GET_IMPL:
            return P2J(s->opaque);
        break;
        case TCN_SOCKET_GET_APRS:
            return P2J(s->sock);
        break;
        case TCN_SOCKET_GET_TYPE:
            return (jlong)(s->net->type);
        break;
    }
    return 0;
}

TCN_IMPLEMENT_CALL(jint, Socket, shutdown)(TCN_STDARGS, jlong sock,
                                           jint how)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);
    return (jint)(*s->net->shutdown)(s->opaque, how);
}

TCN_IMPLEMENT_CALL(jint, Socket, close)(TCN_STDARGS, jlong sock)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    jint rv = APR_SUCCESS;
    apr_socket_t *as;
    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);

    as = s->sock;
    s->sock = NULL;
    apr_pool_cleanup_kill(s->pool, s, sp_socket_cleanup);
    if (s->child) {
        apr_pool_clear(s->child);
    }
#ifdef TCN_DO_STATISTICS
    apr_atomic_inc32(&sp_closed);
#endif
    if (s->net && s->net->close) {
        rv = (*s->net->close)(s->opaque);
        s->net = NULL;
    }
    if (as) {
        rv = (jint)apr_socket_close(as);
    }
    return rv;
}

TCN_IMPLEMENT_CALL(jint, Socket, bind)(TCN_STDARGS, jlong sock,
                                       jlong sa)
{
    jint rv = APR_SUCCESS;
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_sockaddr_t *a = J2P(sa, apr_sockaddr_t *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);
    TCN_ASSERT(s->sock != NULL);
    rv = (jint)apr_socket_bind(s->sock, a);
    return rv;
}

TCN_IMPLEMENT_CALL(jint, Socket, listen)(TCN_STDARGS, jlong sock,
                                         jint backlog)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);
    TCN_ASSERT(s->sock != NULL);
    return (jint)apr_socket_listen(s->sock, backlog);
}

TCN_IMPLEMENT_CALL(jlong, Socket, acceptx)(TCN_STDARGS, jlong sock,
                                           jlong pool)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_pool_t   *p = J2P(pool, apr_pool_t *);
    apr_socket_t *n = NULL;
    tcn_socket_t *a = NULL;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);

    if (s->net->type == TCN_SOCKET_APR) {
        TCN_ASSERT(s->sock != NULL);
        a = (tcn_socket_t *)apr_pcalloc(p, sizeof(tcn_socket_t));
        TCN_CHECK_ALLOCATED(a);
        a->pool   = p;
        apr_pool_cleanup_register(a->pool, (const void *)a,
                                  sp_socket_cleanup,
                                  apr_pool_cleanup_null);

        TCN_THROW_IF_ERR(apr_socket_accept(&n, s->sock, p), n);
    }
    else {
        tcn_ThrowAPRException(e, APR_ENOTIMPL);
        goto cleanup;
    }
    if (n) {
#ifdef TCN_DO_STATISTICS
        apr_atomic_inc32(&sp_accepted);
#endif
        a->net    = &apr_socket_layer;
        a->sock   = n;
        a->opaque = n;
    }

cleanup:
    return P2J(a);
}

TCN_IMPLEMENT_CALL(jlong, Socket, accept)(TCN_STDARGS, jlong sock)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_pool_t   *p = NULL;
    apr_socket_t *n = NULL;
    tcn_socket_t *a = NULL;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);

    TCN_THROW_IF_ERR(apr_pool_create(&p, s->child), p);
    if (s->net->type == TCN_SOCKET_APR) {
        TCN_ASSERT(s->sock != NULL);
        a = (tcn_socket_t *)apr_pcalloc(p, sizeof(tcn_socket_t));
        TCN_CHECK_ALLOCATED(a);
        TCN_THROW_IF_ERR(apr_socket_accept(&n, s->sock, p), n);

        a->pool = p;
        apr_pool_cleanup_register(a->pool, (const void *)a,
                                  sp_socket_cleanup,
                                  apr_pool_cleanup_null);

    }
    else {
        tcn_ThrowAPRException(e, APR_ENOTIMPL);
        goto cleanup;
    }
    if (n) {
#ifdef TCN_DO_STATISTICS
        apr_atomic_inc32(&sp_accepted);
#endif
        a->net    = &apr_socket_layer;
        a->sock   = n;
        a->opaque = n;
    }
    return P2J(a);
cleanup:
    if (tcn_global_pool && p && s->sock)
        apr_pool_destroy(p);
    return 0;
}

TCN_IMPLEMENT_CALL(jint, Socket, connect)(TCN_STDARGS, jlong sock,
                                          jlong sa)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_sockaddr_t *a = J2P(sa, apr_sockaddr_t *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);
    TCN_ASSERT(s->sock != NULL);
    return (jint)apr_socket_connect(s->sock, a);
}

TCN_IMPLEMENT_CALL(jint, Socket, send)(TCN_STDARGS, jlong sock,
                                      jbyteArray buf, jint offset, jint tosend)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_size_t nbytes = (apr_size_t)tosend;
    apr_status_t ss;

    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return -(jint)APR_ENOTSOCK;
    }
    TCN_ASSERT(s->opaque != NULL);
    if(!s->net) {
        tcn_ThrowAPRException(e, APR_EINVALSOCK);
        return -(jint)APR_EINVALSOCK;
    }
#ifdef TCN_DO_STATISTICS
    sp_max_send = TCN_MAX(sp_max_send, nbytes);
    sp_min_send = TCN_MIN(sp_min_send, nbytes);
    sp_tot_send += nbytes;
    sp_num_send++;
#endif

    if (tosend <= TCN_BUFFER_SZ) {
        jbyte sb[TCN_BUFFER_SZ];
        (*e)->GetByteArrayRegion(e, buf, offset, tosend, &sb[0]);
        ss = (*s->net->send)(s->opaque, (const char *)&sb[0], &nbytes);
    }
    else {
        jbyte *sb = (jbyte *)malloc(nbytes);
        if (sb == NULL)
            return -APR_ENOMEM;
        (*e)->GetByteArrayRegion(e, buf, offset, tosend, sb);
        ss = (*s->net->send)(s->opaque, (const char *)sb, &nbytes);
        free(sb);
    }
    if (ss == APR_SUCCESS || ((APR_STATUS_IS_EAGAIN(ss) || ss == TCN_EAGAIN) && nbytes > 0))
        return (jint)nbytes;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(void, Socket, setsbb)(TCN_STDARGS, jlong sock,
                                         jobject buf)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return;
    }
    TCN_ASSERT(s->opaque != NULL);
    if (buf)
        s->jsbbuff = (char *)(*e)->GetDirectBufferAddress(e, buf);
    else
        s->jsbbuff = NULL;
}

TCN_IMPLEMENT_CALL(void, Socket, setrbb)(TCN_STDARGS, jlong sock,
                                         jobject buf)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return;
    }
    TCN_ASSERT(s->opaque != NULL);
    if (buf)
        s->jrbbuff = (char *)(*e)->GetDirectBufferAddress(e, buf);
    else
        s->jrbbuff = NULL;
}

TCN_IMPLEMENT_CALL(jint, Socket, sendb)(TCN_STDARGS, jlong sock,
                                        jobject buf, jint offset, jint len)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_size_t nbytes = (apr_size_t)len;
    apr_size_t sent = 0;
    char *bytes;
    apr_status_t ss = APR_SUCCESS;

    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return -(jint)APR_ENOTSOCK;
    }
    TCN_ASSERT(s->opaque != NULL);
    TCN_ASSERT(buf != NULL);
    if(!s->net) {
        tcn_ThrowAPRException(e, APR_EINVALSOCK);
        return -(jint)APR_EINVALSOCK;
    }
#ifdef TCN_DO_STATISTICS
    sp_max_send = TCN_MAX(sp_max_send, nbytes);
    sp_min_send = TCN_MIN(sp_min_send, nbytes);
    sp_tot_send += nbytes;
    sp_num_send++;
#endif

    bytes  = (char *)(*e)->GetDirectBufferAddress(e, buf);

    while (sent < nbytes) {
        apr_size_t wr = nbytes - sent;
        ss = (*s->net->send)(s->opaque, bytes + offset + sent, &wr);
        if (ss != APR_SUCCESS)
            break;
        sent += wr;
    }

    if (ss == APR_SUCCESS || ((APR_STATUS_IS_EAGAIN(ss) || ss == TCN_EAGAIN) && sent > 0))
        return (jint)sent;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, sendib)(TCN_STDARGS, jlong sock,
                                         jobject buf, jint offset, jint len)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_size_t nbytes = (apr_size_t)len;
    char *bytes;
    apr_status_t ss = APR_SUCCESS;

    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return -(jint)APR_ENOTSOCK;
    }
    TCN_ASSERT(s->opaque != NULL);
    TCN_ASSERT(buf != NULL);
    if(!s->net) {
        tcn_ThrowAPRException(e, APR_EINVALSOCK);
        return -(jint)APR_EINVALSOCK;
    }
#ifdef TCN_DO_STATISTICS
    sp_max_send = TCN_MAX(sp_max_send, nbytes);
    sp_min_send = TCN_MIN(sp_min_send, nbytes);
    sp_tot_send += nbytes;
    sp_num_send++;
#endif

    bytes  = (char *)(*e)->GetDirectBufferAddress(e, buf);

    ss = (*s->net->send)(s->opaque, bytes + offset, &nbytes);

    if (ss == APR_SUCCESS || ((APR_STATUS_IS_EAGAIN(ss) || ss == TCN_EAGAIN) && nbytes > 0))
        return (jint)nbytes;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, sendbb)(TCN_STDARGS, jlong sock,
                                         jint offset, jint len)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_size_t nbytes = (apr_size_t)len;
    apr_size_t sent = 0;
    apr_status_t ss = APR_SUCCESS;

    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return -(jint)APR_ENOTSOCK;
    }
    TCN_ASSERT(s->opaque != NULL);
    TCN_ASSERT(s->jsbbuff != NULL);
    if(!s->net) {
        tcn_ThrowAPRException(e, APR_EINVALSOCK);
        return -(jint)APR_EINVALSOCK;
    }
#ifdef TCN_DO_STATISTICS
    sp_max_send = TCN_MAX(sp_max_send, nbytes);
    sp_min_send = TCN_MIN(sp_min_send, nbytes);
    sp_tot_send += nbytes;
    sp_num_send++;
#endif

    while (sent < nbytes) {
        apr_size_t wr = nbytes - sent;
        ss = (*s->net->send)(s->opaque, s->jsbbuff + offset + sent, &wr);
        if (ss != APR_SUCCESS || wr == 0)
            break;
        sent += wr;
    }
    if (ss == APR_SUCCESS || ((APR_STATUS_IS_EAGAIN(ss) || ss == TCN_EAGAIN) && sent > 0))
        return (jint)sent;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, sendibb)(TCN_STDARGS, jlong sock,
                                          jint offset, jint len)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_size_t nbytes = (apr_size_t)len;
    apr_status_t ss = APR_SUCCESS;

    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return -(jint)APR_ENOTSOCK;
    }
    TCN_ASSERT(s->opaque != NULL);
    TCN_ASSERT(s->jsbbuff != NULL);
    if(!s->net) {
        tcn_ThrowAPRException(e, APR_EINVALSOCK);
        return -(jint)APR_EINVALSOCK;
    }
#ifdef TCN_DO_STATISTICS
    sp_max_send = TCN_MAX(sp_max_send, nbytes);
    sp_min_send = TCN_MIN(sp_min_send, nbytes);
    sp_tot_send += nbytes;
    sp_num_send++;
#endif

    ss = (*s->net->send)(s->opaque, s->jsbbuff + offset, &nbytes);

    if (ss == APR_SUCCESS || ((APR_STATUS_IS_EAGAIN(ss) || ss == TCN_EAGAIN) && nbytes > 0))
        return (jint)nbytes;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, sendv)(TCN_STDARGS, jlong sock,
                                        jobjectArray bufs)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    jsize nvec;
    jsize i;
    struct iovec vec[APR_MAX_IOVEC_SIZE];
    jobject ba[APR_MAX_IOVEC_SIZE];
    apr_size_t written = 0;
    apr_status_t ss;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);
    TCN_ASSERT(s->opaque != NULL);
    if(!s->net) {
        tcn_ThrowAPRException(e, APR_EINVALSOCK);
        return -(jint)APR_EINVALSOCK;
    }

    nvec = (*e)->GetArrayLength(e, bufs);
    if (nvec >= APR_MAX_IOVEC_SIZE)
        return (jint)(-APR_ENOMEM);

    for (i = 0; i < nvec; i++) {
        ba[i] = (*e)->GetObjectArrayElement(e, bufs, i);
        vec[i].iov_len  = (*e)->GetArrayLength(e, ba[i]);
        vec[i].iov_base = (void *)((*e)->GetByteArrayElements(e, ba[i], NULL));
    }

    ss = (*s->net->sendv)(s->opaque, vec, nvec, &written);

    for (i = 0; i < nvec; i++) {
        (*e)->ReleaseByteArrayElements(e, ba[i], (jbyte*)vec[i].iov_base, JNI_ABORT);
    }
    if (ss == APR_SUCCESS || ((APR_STATUS_IS_EAGAIN(ss) || ss == TCN_EAGAIN) && written > 0))
        return (jint)written;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, sendto)(TCN_STDARGS, jlong sock,
                                         jlong where, jint flag,
                                         jbyteArray buf, jint offset, jint tosend)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_sockaddr_t *w = J2P(where, apr_sockaddr_t *);
    apr_size_t nbytes = (apr_size_t)tosend;
    jbyte *bytes;
    apr_int32_t nb;
    apr_status_t ss;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);
    TCN_ASSERT(s->sock != NULL);

    bytes = (*e)->GetByteArrayElements(e, buf, NULL);
    TCN_ASSERT(bytes != NULL);
    apr_socket_opt_get(s->sock, APR_SO_NONBLOCK, &nb);
    if (nb)
         bytes = (*e)->GetPrimitiveArrayCritical(e, buf, NULL);
    else
         bytes = (*e)->GetByteArrayElements(e, buf, NULL);
    ss = apr_socket_sendto(s->sock, w, flag, (char *)(bytes + offset), &nbytes);

    if (nb)
        (*e)->ReleasePrimitiveArrayCritical(e, buf, bytes, 0);
    else
        (*e)->ReleaseByteArrayElements(e, buf, bytes, JNI_ABORT);
    if (ss == APR_SUCCESS)
        return (jint)nbytes;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, recv)(TCN_STDARGS, jlong sock,
                                       jbyteArray buf, jint offset, jint toread)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_size_t nbytes = (apr_size_t)toread;
    apr_status_t ss;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);
    TCN_ASSERT(s->opaque != NULL);
    if(!s->net) {
        tcn_ThrowAPRException(e, APR_EINVALSOCK);
        return -(jint)APR_EINVALSOCK;
    }

    if (toread <= TCN_BUFFER_SZ) {
        char sb[TCN_BUFFER_SZ];

        if ((ss = (*s->net->recv)(s->opaque, sb, &nbytes)) == APR_SUCCESS)
            (*e)->SetByteArrayRegion(e, buf, offset, (jsize)nbytes, (jbyte*)&sb[0]);
    }
    else {
        jbyte *bytes = (*e)->GetByteArrayElements(e, buf, NULL);
        if ((ss = (*s->net->recv)(s->opaque, (char*)(bytes + offset),
                                  &nbytes)) == APR_SUCCESS)
            (*e)->ReleaseByteArrayElements(e, buf, bytes,
                                           nbytes ? 0 : JNI_ABORT);
    }
#ifdef TCN_DO_STATISTICS
    if (ss == APR_SUCCESS) {
        sp_max_recv = TCN_MAX(sp_max_recv, nbytes);
        sp_min_recv = TCN_MIN(sp_min_recv, nbytes);
        sp_tot_recv += nbytes;
        sp_num_recv++;
    }
    else {
        if (APR_STATUS_IS_ETIMEDOUT(ss) ||
            APR_STATUS_IS_TIMEUP(ss))
            sp_tmo_recv++;
        else if (APR_STATUS_IS_ECONNABORTED(ss) ||
                 APR_STATUS_IS_ECONNRESET(ss) ||
                 APR_STATUS_IS_EOF(ss))
            sp_rst_recv++;
        else {
            sp_err_recv++;
            sp_erl_recv = ss;
        }
    }
#endif
    if (ss == APR_SUCCESS)
        return (jint)nbytes;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, recvt)(TCN_STDARGS, jlong sock,
                                        jbyteArray buf, jint offset,
                                        jint toread, jlong timeout)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_size_t nbytes = (apr_size_t)toread;
    apr_status_t ss;
    apr_interval_time_t pt;
    apr_interval_time_t nt = J2T(timeout);

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);
    TCN_ASSERT(s->opaque != NULL);
    TCN_ASSERT(buf != NULL);
    if(!s->net) {
        tcn_ThrowAPRException(e, APR_EINVALSOCK);
        return -(jint)APR_EINVALSOCK;
    }

    if ((ss = (*s->net->timeout_get)(s->opaque, &pt)) != APR_SUCCESS) {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
    if (pt != nt) {
        if ((ss = (*s->net->timeout_set)(s->opaque, nt)) != APR_SUCCESS)
            goto cleanup;
    }
    if (toread <= TCN_BUFFER_SZ) {
        jbyte sb[TCN_BUFFER_SZ];
        if ((ss = (*s->net->recv)(s->opaque, (char *)&sb[0], &nbytes)) == APR_SUCCESS)
            (*e)->SetByteArrayRegion(e, buf, offset, (jsize)nbytes, &sb[0]);
    }
    else {
        jbyte *sb = (jbyte *)malloc(nbytes);
        if (sb == NULL)
            return -APR_ENOMEM;
        if ((ss = (*s->net->recv)(s->opaque, (char *)sb, &nbytes)) == APR_SUCCESS)
            (*e)->SetByteArrayRegion(e, buf, offset, (jsize)nbytes, &sb[0]);
        free(sb);
    }
    if (pt != nt) {
        if ((ss = (*s->net->timeout_set)(s->opaque, pt)) != APR_SUCCESS)
            goto cleanup;
    }

#ifdef TCN_DO_STATISTICS
    if (ss == APR_SUCCESS) {
        sp_max_recv = TCN_MAX(sp_max_recv, nbytes);
        sp_min_recv = TCN_MIN(sp_min_recv, nbytes);
        sp_tot_recv += nbytes;
        sp_num_recv++;
    }
    else {
        if (APR_STATUS_IS_ETIMEDOUT(ss) ||
            APR_STATUS_IS_TIMEUP(ss))
            sp_tmo_recv++;
        else if (APR_STATUS_IS_ECONNABORTED(ss) ||
                 APR_STATUS_IS_ECONNRESET(ss) ||
                 APR_STATUS_IS_EOF(ss))
            sp_rst_recv++;
        else {
            sp_err_recv++;
            sp_erl_recv = ss;
        }
    }
#endif
cleanup:
    if (ss == APR_SUCCESS)
        return (jint)nbytes;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, recvb)(TCN_STDARGS, jlong sock,
                                        jobject buf, jint offset, jint len)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_status_t ss;
    apr_size_t nbytes = (apr_size_t)len;
    char *bytes;

    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return -(jint)APR_ENOTSOCK;
    }
    TCN_ASSERT(s->opaque != NULL);
    TCN_ASSERT(buf != NULL);
    if(!s->net) {
        tcn_ThrowAPRException(e, APR_EINVALSOCK);
        return -(jint)APR_EINVALSOCK;
    }

    bytes  = (char *)(*e)->GetDirectBufferAddress(e, buf);
    TCN_ASSERT(bytes != NULL);
    ss = (*s->net->recv)(s->opaque, bytes + offset, &nbytes);
#ifdef TCN_DO_STATISTICS
    if (ss == APR_SUCCESS) {
        sp_max_recv = TCN_MAX(sp_max_recv, nbytes);
        sp_min_recv = TCN_MIN(sp_min_recv, nbytes);
        sp_tot_recv += nbytes;
        sp_num_recv++;
    }
    else {
        if (APR_STATUS_IS_ETIMEDOUT(ss) ||
            APR_STATUS_IS_TIMEUP(ss))
            sp_tmo_recv++;
        else if (APR_STATUS_IS_ECONNABORTED(ss) ||
                 APR_STATUS_IS_ECONNRESET(ss) ||
                 APR_STATUS_IS_EOF(ss))
            sp_rst_recv++;
        else {
            sp_err_recv++;
            sp_erl_recv = ss;
        }
    }
#endif
    if (ss == APR_SUCCESS)
        return (jint)nbytes;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, recvbb)(TCN_STDARGS, jlong sock,
                                         jint offset, jint len)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_status_t ss;
    apr_size_t nbytes = (apr_size_t)len;

    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return -(jint)APR_ENOTSOCK;
    }
    TCN_ASSERT(sock != 0);
    TCN_ASSERT(s->opaque != NULL);
    TCN_ASSERT(s->jrbbuff != NULL);
    if(!s->net) {
        tcn_ThrowAPRException(e, APR_EINVALSOCK);
        return -(jint)APR_EINVALSOCK;
    }

    ss = (*s->net->recv)(s->opaque, s->jrbbuff + offset, &nbytes);
#ifdef TCN_DO_STATISTICS
    if (ss == APR_SUCCESS) {
        sp_max_recv = TCN_MAX(sp_max_recv, nbytes);
        sp_min_recv = TCN_MIN(sp_min_recv, nbytes);
        sp_tot_recv += nbytes;
        sp_num_recv++;
    }
    else {
        if (APR_STATUS_IS_ETIMEDOUT(ss) ||
            APR_STATUS_IS_TIMEUP(ss))
            sp_tmo_recv++;
        else if (APR_STATUS_IS_ECONNABORTED(ss) ||
                 APR_STATUS_IS_ECONNRESET(ss) ||
                 APR_STATUS_IS_EOF(ss))
            sp_rst_recv++;
        else {
            sp_err_recv++;
            sp_erl_recv = ss;
        }
    }
#endif
    if (ss == APR_SUCCESS)
        return (jint)nbytes;
    else if (APR_STATUS_IS_EOF(ss))
        return 0;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, recvbt)(TCN_STDARGS, jlong sock,
                                         jobject buf, jint offset,
                                         jint len, jlong timeout)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_status_t ss;
    apr_size_t nbytes = (apr_size_t)len;
    char *bytes;
    apr_interval_time_t pt;
    apr_interval_time_t nt = J2T(timeout);

    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return -(jint)APR_ENOTSOCK;
    }
    TCN_ASSERT(buf != NULL);
    TCN_ASSERT(s->opaque != NULL);
    if(!s->net) {
        tcn_ThrowAPRException(e, APR_EINVALSOCK);
        return -(jint)APR_EINVALSOCK;
    }

    bytes  = (char *)(*e)->GetDirectBufferAddress(e, buf);
    TCN_ASSERT(bytes != NULL);

    if ((ss = (*s->net->timeout_get)(s->opaque, &pt)) != APR_SUCCESS) {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
    if (pt != nt) {
        if ((ss = (*s->net->timeout_set)(s->opaque, nt)) != APR_SUCCESS) {
            TCN_ERROR_WRAP(ss);
            return -(jint)ss;
        }
    }
    ss = (*s->net->recv)(s->opaque, bytes + offset, &nbytes);
    if (pt != nt) {
        if ((ss = (*s->net->timeout_set)(s->opaque, pt)) != APR_SUCCESS) {
            TCN_ERROR_WRAP(ss);
            return -(jint)ss;
        }
    }

#ifdef TCN_DO_STATISTICS
    if (ss == APR_SUCCESS) {
        sp_max_recv = TCN_MAX(sp_max_recv, nbytes);
        sp_min_recv = TCN_MIN(sp_min_recv, nbytes);
        sp_tot_recv += nbytes;
        sp_num_recv++;
    }
    else {
        if (APR_STATUS_IS_ETIMEDOUT(ss) ||
            APR_STATUS_IS_TIMEUP(ss))
            sp_tmo_recv++;
        else if (APR_STATUS_IS_ECONNABORTED(ss) ||
                 APR_STATUS_IS_ECONNRESET(ss) ||
                 APR_STATUS_IS_EOF(ss))
            sp_rst_recv++;
        else {
            sp_err_recv++;
            sp_erl_recv = ss;
        }
    }
#endif
    if (ss == APR_SUCCESS)
        return (jint)nbytes;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, recvbbt)(TCN_STDARGS, jlong sock,
                                          jint offset,
                                          jint len, jlong timeout)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_status_t ss;
    apr_size_t nbytes = (apr_size_t)len;
    apr_interval_time_t pt;
    apr_interval_time_t nt = J2T(timeout);

    UNREFERENCED_STDARGS;
    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return -(jint)APR_ENOTSOCK;
    }
    TCN_ASSERT(s->jrbbuff != NULL);
    TCN_ASSERT(s->opaque != NULL);
    if(!s->net) {
        tcn_ThrowAPRException(e, APR_EINVALSOCK);
        return -(jint)APR_EINVALSOCK;
    }

    if ((ss = (*s->net->timeout_get)(s->opaque, &pt)) != APR_SUCCESS) {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
    if (pt != nt) {
        if ((ss = (*s->net->timeout_set)(s->opaque, nt)) != APR_SUCCESS) {
            TCN_ERROR_WRAP(ss);
            return -(jint)ss;
        }
    }
    ss = (*s->net->recv)(s->opaque, s->jrbbuff + offset, &nbytes);
    if (pt != nt) {
        if ((ss = (*s->net->timeout_set)(s->opaque, pt)) != APR_SUCCESS) {
            TCN_ERROR_WRAP(ss);
            return -(jint)ss;
        }
    }

#ifdef TCN_DO_STATISTICS
    if (ss == APR_SUCCESS) {
        sp_max_recv = TCN_MAX(sp_max_recv, nbytes);
        sp_min_recv = TCN_MIN(sp_min_recv, nbytes);
        sp_tot_recv += nbytes;
        sp_num_recv++;
    }
    else {
        if (APR_STATUS_IS_ETIMEDOUT(ss) ||
            APR_STATUS_IS_TIMEUP(ss))
            sp_tmo_recv++;
        else if (APR_STATUS_IS_ECONNABORTED(ss) ||
                 APR_STATUS_IS_ECONNRESET(ss) ||
                 APR_STATUS_IS_EOF(ss))
            sp_rst_recv++;
        else {
            sp_err_recv++;
            sp_erl_recv = ss;
        }
    }
#endif
    if (ss == APR_SUCCESS)
        return (jint)nbytes;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, recvfrom)(TCN_STDARGS, jlong from,
                                          jlong sock, jint flags,
                                          jbyteArray buf, jint offset, jint toread)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_sockaddr_t *f = J2P(from, apr_sockaddr_t *);
    apr_size_t nbytes = (apr_size_t)toread;
    jbyte *bytes = (*e)->GetByteArrayElements(e, buf, NULL);
    apr_status_t ss;

    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return -(jint)APR_ENOTSOCK;
    }
    TCN_ASSERT(s->sock != NULL);
    TCN_ASSERT(buf != NULL);
    ss = apr_socket_recvfrom(f, s->sock, (apr_int32_t)flags, (char*)(bytes + offset), &nbytes);

    (*e)->ReleaseByteArrayElements(e, buf, bytes,
                                   nbytes ? 0 : JNI_ABORT);
    if (ss == APR_SUCCESS)
        return (jint)nbytes;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jint)ss;
    }
}

TCN_IMPLEMENT_CALL(jint, Socket, optSet)(TCN_STDARGS, jlong sock,
                                         jint opt, jint on)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);

    UNREFERENCED(o);
    if (!s->sock) {
        return APR_ENOTSOCK;
    }
    else
        return (jint)(*s->net->opt_set)(s->opaque, (apr_int32_t)opt, (apr_int32_t)on);
}

TCN_IMPLEMENT_CALL(jint, Socket, optGet)(TCN_STDARGS, jlong sock,
                                         jint opt)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_int32_t on = 0;

    UNREFERENCED(o);
    if (!s->sock)
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
    else {
        TCN_THROW_IF_ERR((*s->net->opt_get)(s->opaque, (apr_int32_t)opt,
                                            &on), on);
    }
cleanup:
    return (jint)on;
}

TCN_IMPLEMENT_CALL(jint, Socket, timeoutSet)(TCN_STDARGS, jlong sock,
                                             jlong timeout)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);

    UNREFERENCED(o);
    TCN_ASSERT(s->opaque != NULL);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return APR_ENOTSOCK;
    }
    return (jint)(*s->net->timeout_set)(s->opaque, J2T(timeout));
}

TCN_IMPLEMENT_CALL(jlong, Socket, timeoutGet)(TCN_STDARGS, jlong sock)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_interval_time_t timeout;

    UNREFERENCED(o);
    if (!sock) {
        tcn_ThrowAPRException(e, APR_ENOTSOCK);
        return 0;
    }
    TCN_ASSERT(s->opaque != NULL);

    TCN_THROW_IF_ERR((*s->net->timeout_get)(s->opaque, &timeout), timeout);
cleanup:
    return (jlong)timeout;
}

TCN_IMPLEMENT_CALL(jboolean, Socket, atmark)(TCN_STDARGS, jlong sock)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_int32_t mark;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);
    TCN_ASSERT(s->sock != NULL);

    if (apr_socket_atmark(s->sock, &mark) != APR_SUCCESS)
        return JNI_FALSE;
    return mark ? JNI_TRUE : JNI_FALSE;
}

#if APR_HAS_SENDFILE

TCN_IMPLEMENT_CALL(jlong, Socket, sendfile)(TCN_STDARGS, jlong sock,
                                            jlong file,
                                            jobjectArray headers,
                                            jobjectArray trailers,
                                            jlong offset, jlong len,
                                            jint flags)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_file_t *f = J2P(file, apr_file_t *);
    jsize nh = 0;
    jsize nt = 0;
    jsize i;
    struct iovec hvec[APR_MAX_IOVEC_SIZE];
    struct iovec tvec[APR_MAX_IOVEC_SIZE];
    jobject hba[APR_MAX_IOVEC_SIZE];
    jobject tba[APR_MAX_IOVEC_SIZE];
    apr_off_t off = (apr_off_t)offset;
    apr_size_t written = (apr_size_t)len;
    apr_hdtr_t hdrs;
    apr_status_t ss;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);
    TCN_ASSERT(file != 0);

    if (s->net->type != TCN_SOCKET_APR)
        return (jint)(-APR_ENOTIMPL);
    if (headers)
        nh = (*e)->GetArrayLength(e, headers);
    if (trailers)
        nt = (*e)->GetArrayLength(e, trailers);
    /* Check for overflow */
    if (nh >= APR_MAX_IOVEC_SIZE || nt >= APR_MAX_IOVEC_SIZE)
        return (jint)(-APR_ENOMEM);

    for (i = 0; i < nh; i++) {
        hba[i] = (*e)->GetObjectArrayElement(e, headers, i);
        hvec[i].iov_len  = (*e)->GetArrayLength(e, hba[i]);
        hvec[i].iov_base = (void *)((*e)->GetByteArrayElements(e, hba[i], NULL));
    }
    for (i = 0; i < nt; i++) {
        tba[i] = (*e)->GetObjectArrayElement(e, trailers, i);
        tvec[i].iov_len  = (*e)->GetArrayLength(e, tba[i]);
        tvec[i].iov_base = (void *)((*e)->GetByteArrayElements(e, tba[i], NULL));
    }
    hdrs.headers = &hvec[0];
    hdrs.numheaders = nh;
    hdrs.trailers = &tvec[0];
    hdrs.numtrailers = nt;


    ss = apr_socket_sendfile(s->sock, f, &hdrs, &off, &written, (apr_int32_t)flags);

#ifdef TCN_DO_STATISTICS
    sf_max_send = TCN_MAX(sf_max_send, written);
    sf_min_send = TCN_MIN(sf_min_send, written);
    sf_tot_send += written;
    sf_num_send++;
#endif

    for (i = 0; i < nh; i++) {
        (*e)->ReleaseByteArrayElements(e, hba[i], (jbyte*)hvec[i].iov_base, JNI_ABORT);
    }

    for (i = 0; i < nt; i++) {
        (*e)->ReleaseByteArrayElements(e, tba[i], (jbyte*)tvec[i].iov_base, JNI_ABORT);
    }
    /* Return Number of bytes actually sent,
     * including headers, file, and trailers
     */
    if (ss == APR_SUCCESS)
        return (jlong)written;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jlong)ss;
    }
}

TCN_IMPLEMENT_CALL(jlong, Socket, sendfilen)(TCN_STDARGS, jlong sock,
                                             jlong file,
                                             jlong offset, jlong len,
                                             jint flags)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_file_t *f = J2P(file, apr_file_t *);
    apr_off_t off = (apr_off_t)offset;
    apr_size_t written = (apr_size_t)len;
    apr_hdtr_t hdrs;
    apr_status_t ss;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(sock != 0);
    TCN_ASSERT(file != 0);

    if (s->net->type != TCN_SOCKET_APR)
        return (jint)(-APR_ENOTIMPL);

    hdrs.headers = NULL;
    hdrs.numheaders = 0;
    hdrs.trailers = NULL;
    hdrs.numtrailers = 0;


    ss = apr_socket_sendfile(s->sock, f, &hdrs, &off, &written, (apr_int32_t)flags);

#ifdef TCN_DO_STATISTICS
    sf_max_send = TCN_MAX(sf_max_send, written);
    sf_min_send = TCN_MIN(sf_min_send, written);
    sf_tot_send += written;
    sf_num_send++;
#endif

    /* Return Number of bytes actually sent,
     * including headers, file, and trailers
     */
    if (ss == APR_SUCCESS)
        return (jlong)written;
    else {
        TCN_ERROR_WRAP(ss);
        return -(jlong)ss;
    }
}

#else /* APR_HAS_SENDIFLE */

TCN_IMPLEMENT_CALL(jlong, Socket, sendfile)(TCN_STDARGS, jlong sock,
                                            jlong file,
                                            jobjectArray headers,
                                            jobjectArray trailers,
                                            jlong offset, jlong len,
                                            jint flags)
{

    UNREFERENCED_STDARGS;
    UNREFERENCED(sock);
    UNREFERENCED(file);
    UNREFERENCED(headers);
    UNREFERENCED(trailers);
    UNREFERENCED(offset);
    UNREFERENCED(len);
    UNREFERENCED(flags);
    return -(jlong)APR_ENOTIMPL;
}

TCN_IMPLEMENT_CALL(jlong, Socket, sendfilen)(TCN_STDARGS, jlong sock,
                                             jlong file,
                                             jlong offset, jlong len,
                                             jint flags)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(sock);
    UNREFERENCED(file);
    UNREFERENCED(offset);
    UNREFERENCED(len);
    UNREFERENCED(flags);
    return -(jlong)APR_ENOTIMPL;
}

#endif  /* APR_HAS_SENDIFLE */


TCN_IMPLEMENT_CALL(jint, Socket, acceptfilter)(TCN_STDARGS,
                                               jlong sock,
                                               jstring name,
                                               jstring args)
{
#if APR_HAS_SO_ACCEPTFILTER
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    TCN_ALLOC_CSTRING(name);
    TCN_ALLOC_CSTRING(args);
    apr_status_t rv;


    UNREFERENCED(o);
    rv = apr_socket_accept_filter(s->sock, J2S(name),
                                  J2S(args) ? J2S(args) : "");
    TCN_FREE_CSTRING(name);
    TCN_FREE_CSTRING(args);
    return (jint)rv;
#else
    UNREFERENCED_STDARGS;
    UNREFERENCED(sock);
    UNREFERENCED(name);
    UNREFERENCED(args);
    return (jint)APR_ENOTIMPL;
#endif
}

TCN_IMPLEMENT_CALL(jint, Socket, dataSet)(TCN_STDARGS, jlong sock,
                                          jstring key, jobject data)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_status_t rv = APR_SUCCESS;
    TCN_ALLOC_CSTRING(key);

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);

    rv = apr_socket_data_set(s->sock, data, J2S(key), NULL);
    TCN_FREE_CSTRING(key);
    return rv;
}

TCN_IMPLEMENT_CALL(jobject, Socket, dataGet)(TCN_STDARGS, jlong socket,
                                             jstring key)
{
    tcn_socket_t *s = J2P(socket, tcn_socket_t *);
    TCN_ALLOC_CSTRING(key);
    void *rv = NULL;

    UNREFERENCED(o);
    TCN_ASSERT(socket != 0);

    if (apr_socket_data_get(&rv, J2S(key), s->sock) != APR_SUCCESS) {
        rv = NULL;
    }
    TCN_FREE_CSTRING(key);
    return rv;
}
