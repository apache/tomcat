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

#include "apr.h"
#include "apr_pools.h"
#include "apr_network_io.h"
#include "tcn.h"


#if  !APR_HAVE_IPV6
#define APR_INET6 APR_INET
#endif

#define GET_S_FAMILY(T, F)           \
    if (F == 0) T = APR_UNSPEC;      \
    else if (F == 1) T = APR_INET;   \
    else if (F == 2) T = APR_INET6;  \
    else T = F

#define GET_S_TYPE(T, F)             \
    if (F == 0) T = SOCK_STREAM;     \
    else if (F == 1) T = SOCK_DGRAM; \
    else T = F

TCN_IMPLEMENT_CALL(jlong, Address, info)(TCN_STDARGS,
                                         jstring hostname,
                                         jint family, jint port,
                                         jint flags, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(hostname);
    apr_sockaddr_t *sa = NULL;
    apr_int32_t f;


    UNREFERENCED(o);
    GET_S_FAMILY(f, family);
    TCN_THROW_IF_ERR(apr_sockaddr_info_get(&sa,
            J2S(hostname), f, (apr_port_t)port,
            (apr_int32_t)flags, p), sa);

cleanup:
    TCN_FREE_CSTRING(hostname);
    return P2J(sa);
}

TCN_IMPLEMENT_CALL(jstring, Address, getnameinfo)(TCN_STDARGS,
                                                   jlong sa, jint flags)
{
    apr_sockaddr_t *s = J2P(sa, apr_sockaddr_t *);
    char *hostname;

    UNREFERENCED(o);
    if (apr_getnameinfo(&hostname, s, (apr_int32_t)flags) == APR_SUCCESS)
        return AJP_TO_JSTRING(hostname);
    else
        return NULL;
}

TCN_IMPLEMENT_CALL(jlong, Address, get)(TCN_STDARGS, jint which,
                                         jlong sock)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
    apr_sockaddr_t *sa;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_socket_addr_get(&sa,
                                    which, s), sa);
cleanup:
    return P2J(sa);
}

TCN_IMPLEMENT_CALL(jint, Address, equal)(TCN_STDARGS,
                                         jlong a, jlong b)
{
    apr_sockaddr_t *sa = J2P(a, apr_sockaddr_t *);
    apr_sockaddr_t *sb = J2P(b, apr_sockaddr_t *);

    UNREFERENCED_STDARGS;
    return apr_sockaddr_equal(sa, sb) ? JNI_TRUE : JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jlong, Socket, create)(TCN_STDARGS, jint family,
                                          jint type, jint protocol, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_socket_t *s = NULL;
    apr_int32_t f, t;

    UNREFERENCED(o);
    GET_S_FAMILY(f, family);
    GET_S_TYPE(t, type);

    TCN_THROW_IF_ERR(apr_socket_create(&s,
                     f, t, protocol, p), s);

cleanup:
    return P2J(s);

}

TCN_IMPLEMENT_CALL(jint, Socket, shutdown)(TCN_STDARGS, jlong sock,
                                           jint how)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_socket_shutdown(s, (apr_shutdown_how_e)how);
}

TCN_IMPLEMENT_CALL(jint, Socket, close)(TCN_STDARGS, jlong sock)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_socket_close(s);
}

TCN_IMPLEMENT_CALL(jint, Socket, bind)(TCN_STDARGS, jlong sock,
                                       jlong sa)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
    apr_sockaddr_t *a = J2P(sa, apr_sockaddr_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_socket_bind(s, a);
}

TCN_IMPLEMENT_CALL(jint, Socket, listen)(TCN_STDARGS, jlong sock,
                                         jint backlog)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_socket_listen(s, backlog);
}

TCN_IMPLEMENT_CALL(jlong, Socket, accept)(TCN_STDARGS, jlong sock,
                                          jlong pool)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
    apr_pool_t   *p = J2P(pool, apr_pool_t *);
    apr_socket_t *n = NULL;

    UNREFERENCED(o);

    TCN_THROW_IF_ERR(apr_socket_accept(&n, s, p), s);

cleanup:
    return P2J(n);
}

TCN_IMPLEMENT_CALL(jint, Socket, connect)(TCN_STDARGS, jlong sock,
                                          jlong sa)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
    apr_sockaddr_t *a = J2P(sa, apr_sockaddr_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_socket_connect(s, a);
}

TCN_IMPLEMENT_CALL(jint, Socket, send)(TCN_STDARGS, jlong sock,
                                      jbyteArray buf, jint tosend)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
    apr_size_t nbytes = (*e)->GetArrayLength(e, buf);
    jbyte *bytes;
    apr_int32_t nb;

    UNREFERENCED(o);
    apr_socket_opt_get(s, APR_SO_NONBLOCK, &nb);
    if (tosend > 0)
        nbytes = min(nbytes, (apr_size_t)tosend);
    if (nb)
         bytes = (*e)->GetPrimitiveArrayCritical(e, buf, NULL);
    else
         bytes = (*e)->GetByteArrayElements(e, buf, NULL);
    TCN_THROW_IF_ERR(apr_socket_send(s, bytes, &nbytes), nbytes);

cleanup:
    if (nb)
        (*e)->ReleasePrimitiveArrayCritical(e, buf, bytes, JNI_ABORT);
    else
        (*e)->ReleaseByteArrayElements(e, buf, bytes, JNI_ABORT);
    return (jint)nbytes;
}

TCN_IMPLEMENT_CALL(jint, Socket, sendv)(TCN_STDARGS, jlong sock,
                                        jobjectArray bufs)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
    jsize nvec = (*e)->GetArrayLength(e, bufs);
    jsize i;
    struct iovec vec[APR_MAX_IOVEC_SIZE];
    jobject ba[APR_MAX_IOVEC_SIZE];
    apr_size_t written = 0;

    UNREFERENCED(o);

    if (nvec >= APR_MAX_IOVEC_SIZE) {
        /* TODO: Throw something here */
        return 0;
    }
    for (i = 0; i < nvec; i++) {
        ba[i] = (*e)->GetObjectArrayElement(e, bufs, i);
        vec[i].iov_len  = (*e)->GetArrayLength(e, ba[i]);
        vec[i].iov_base = (*e)->GetByteArrayElements(e, ba[i], NULL);
    }

    TCN_THROW_IF_ERR(apr_socket_sendv(s, vec, nvec, &written), i);

cleanup:
    for (i = 0; i < nvec; i++) {
        (*e)->ReleaseByteArrayElements(e, ba[i], vec[i].iov_base, JNI_ABORT);
    }
    return (jint)written;
}

TCN_IMPLEMENT_CALL(jint, Socket, sendto)(TCN_STDARGS, jlong sock,
                                         jlong where, jint flag,
                                         jbyteArray buf, jint tosend)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
    apr_sockaddr_t *w = J2P(where, apr_sockaddr_t *);
    apr_size_t nbytes = (*e)->GetArrayLength(e, buf);
    jbyte *bytes = (*e)->GetByteArrayElements(e, buf, NULL);
    apr_int32_t nb;

    UNREFERENCED(o);
    apr_socket_opt_get(s, APR_SO_NONBLOCK, &nb);
    if (tosend > 0)
        nbytes = min(nbytes, (apr_size_t)tosend);
    if (nb)
         bytes = (*e)->GetPrimitiveArrayCritical(e, buf, NULL);
    else
         bytes = (*e)->GetByteArrayElements(e, buf, NULL);
    TCN_THROW_IF_ERR(apr_socket_sendto(s, w, flag, bytes, &nbytes), nbytes);

cleanup:
    if (nb)
        (*e)->ReleasePrimitiveArrayCritical(e, buf, bytes, 0);
    else
        (*e)->ReleaseByteArrayElements(e, buf, bytes, JNI_ABORT);
    return (jint)nbytes;
}

TCN_IMPLEMENT_CALL(jint, Socket, recv)(TCN_STDARGS, jlong sock,
                                       jbyteArray buf, jint toread)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
    apr_size_t nbytes = (*e)->GetArrayLength(e, buf);
    jbyte *bytes = (*e)->GetByteArrayElements(e, buf, NULL);

    UNREFERENCED(o);
    if (toread > 0)
        nbytes = min(nbytes, (apr_size_t)toread);

    TCN_THROW_IF_ERR(apr_socket_recv(s, bytes, &nbytes), nbytes);

cleanup:
    (*e)->ReleaseByteArrayElements(e, buf, bytes,
                                   nbytes ? 0 : JNI_ABORT);
    return (jint)nbytes;
}

TCN_IMPLEMENT_CALL(jint, Socket, recvfrom)(TCN_STDARGS, jlong from,
                                          jlong sock, jint flags,
                                          jbyteArray buf, jint toread)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
    apr_sockaddr_t *f = J2P(from, apr_sockaddr_t *);
    apr_size_t nbytes = (*e)->GetArrayLength(e, buf);
    jbyte *bytes = (*e)->GetByteArrayElements(e, buf, NULL);

    UNREFERENCED(o);
    if (toread > 0)
        nbytes = min(nbytes, (apr_size_t)toread);

    TCN_THROW_IF_ERR(apr_socket_recvfrom(f, s,
            (apr_int32_t)flags, bytes, &nbytes), nbytes);

cleanup:
    (*e)->ReleaseByteArrayElements(e, buf, bytes,
                                   nbytes ? 0 : JNI_ABORT);
    return (jint)nbytes;
}

TCN_IMPLEMENT_CALL(jint, Socket, optSet)(TCN_STDARGS, jlong sock,
                                         jint opt, jint on)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_socket_opt_set(s, (apr_int32_t)opt, (apr_int32_t)on);
}

TCN_IMPLEMENT_CALL(jint, Socket, optGet)(TCN_STDARGS, jlong sock,
                                         jint opt)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
    apr_int32_t on;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_socket_opt_get(s, (apr_int32_t)opt,
                                        &on), on);
cleanup:
    return (jint)on;
}

TCN_IMPLEMENT_CALL(jint, Socket, timeoutSet)(TCN_STDARGS, jlong sock,
                                             jlong timeout)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_socket_timeout_set(s, J2T(timeout));
}

TCN_IMPLEMENT_CALL(jlong, Socket, timeoutGet)(TCN_STDARGS, jlong sock)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
    apr_interval_time_t timeout;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_socket_timeout_get(s, &timeout), timeout);

cleanup:
    return (jlong)timeout;
}

TCN_IMPLEMENT_CALL(jint, Socket, atmark)(TCN_STDARGS, jlong sock)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
    apr_int32_t mark;

    UNREFERENCED_STDARGS;
    if (apr_socket_atmark(s, &mark) != APR_SUCCESS)
        return JNI_FALSE;
    return mark ? JNI_TRUE : JNI_FALSE;
}


TCN_IMPLEMENT_CALL(jint, Socket, sendfile)(TCN_STDARGS, jlong sock,
                                           jlong file,
                                           jobjectArray headers,
                                           jobjectArray trailers,
                                           jlong offset, jint len,
                                           jint flags)
{
    apr_socket_t *s = J2P(sock, apr_socket_t *);
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

    UNREFERENCED(o);

    if (headers)
        nh = (*e)->GetArrayLength(e, headers);
    if (trailers)
        nt = (*e)->GetArrayLength(e, trailers);
    /* Check for overflow */
    if (nh >= APR_MAX_IOVEC_SIZE || nt >= APR_MAX_IOVEC_SIZE) {
        /* TODO: Throw something here */
        return 0;
    }

    for (i = 0; i < nh; i++) {
        hba[i] = (*e)->GetObjectArrayElement(e, headers, i);
        hvec[i].iov_len  = (*e)->GetArrayLength(e, hba[i]);
        hvec[i].iov_base = (*e)->GetByteArrayElements(e, hba[i], NULL);
    }
    for (i = 0; i < nt; i++) {
        tba[i] = (*e)->GetObjectArrayElement(e, trailers, i);
        tvec[i].iov_len  = (*e)->GetArrayLength(e, tba[i]);
        tvec[i].iov_base = (*e)->GetByteArrayElements(e, tba[i], NULL);
    }
    hdrs.headers = &hvec[0];
    hdrs.numheaders = nh;
    hdrs.trailers = &tvec[0];
    hdrs.numtrailers = nt;

    TCN_THROW_IF_ERR(apr_socket_sendfile(s, f, &hdrs, &off,
                     &written, (apr_int32_t)flags), i);

cleanup:
    for (i = 0; i < nh; i++) {
        (*e)->ReleaseByteArrayElements(e, hba[i], hvec[i].iov_base, JNI_ABORT);
    }

    for (i = 0; i < nt; i++) {
        (*e)->ReleaseByteArrayElements(e, tba[i], tvec[i].iov_base, JNI_ABORT);
    }
    /* Return Number of bytes actually sent,
     * including headers, file, and trailers
     */
    return (jint)written;
}

