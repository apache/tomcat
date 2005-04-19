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
#include "apr_poll.h"
#include "tcn.h"

/* Internal poll structure for queryset
 */

typedef struct tcn_pollset {
    apr_pool_t    *pool;
    apr_int32_t   nelts;
    apr_int32_t   nalloc;
    apr_pollset_t *pollset;
    apr_pollfd_t  *socket_set;
    apr_interval_time_t *socket_ttl;
    apr_interval_time_t max_ttl;
} tcn_pollset_t;

TCN_IMPLEMENT_CALL(jlong, Poll, create)(TCN_STDARGS, jint size,
                                        jlong pool, jint flags,
                                        jlong ttl)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_pollset_t *pollset = NULL;
    tcn_pollset_t *tps = NULL;
    apr_uint32_t f = (apr_uint32_t)flags;
    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);

    if (f & APR_POLLSET_THREADSAFE) {
        apr_status_t rv = apr_pollset_create(&pollset, (apr_uint32_t)size, p, f);
        if (rv == APR_ENOTIMPL)
            f &= ~APR_POLLSET_THREADSAFE;
        else if (rv != APR_SUCCESS) {
            tcn_ThrowAPRException(e, rv);
            goto cleanup;
        }
    }
    if (pollset == NULL) {
        TCN_THROW_IF_ERR(apr_pollset_create(&pollset,
                         (apr_uint32_t)size, p, f), pollset);
    }
    tps = apr_palloc(p, sizeof(tcn_pollset_t));
    tps->pollset = pollset;
    tps->socket_set = apr_palloc(p, size * sizeof(apr_pollfd_t));
    tps->socket_ttl = apr_palloc(p, size * sizeof(apr_interval_time_t));
    tps->nelts  = 0;
    tps->nalloc = size;
    tps->pool   = p;
    tps->max_ttl = J2T(ttl);
cleanup:
    return P2J(tps);
}

TCN_IMPLEMENT_CALL(jint, Poll, destroy)(TCN_STDARGS, jlong pollset)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(pollset != 0);
    return (jint)apr_pollset_destroy(p->pollset);
}

TCN_IMPLEMENT_CALL(jint, Poll, add)(TCN_STDARGS, jlong pollset,
                                    jlong socket, jlong data,
                                    jint reqevents)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    apr_pollfd_t fd;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(socket != 0);

    if (p->nelts == p->nalloc)
        return APR_ENOMEM;

    memset(&fd, 0, sizeof(apr_pollfd_t));
    fd.desc_type = APR_POLL_SOCKET;
    fd.reqevents = (apr_int16_t)reqevents;
    fd.desc.s = J2P(socket, apr_socket_t *);
    fd.client_data = J2P(data, void *);
    p->socket_set[p->nelts++] = fd;
    return (jint)apr_pollset_add(p->pollset, &fd);
}

static apr_status_t do_remove(tcn_pollset_t *p, const apr_pollfd_t *fd)
{
    apr_int32_t i;

    for (i = 0; i < p->nelts; i++) {
        if (fd->desc.s == p->socket_set[i].desc.s) {
            /* Found an instance of the fd: remove this and any other copies */
            apr_int32_t dst = i;
            apr_int32_t old_nelts = p->nelts;
            p->nelts--;
            for (i++; i < old_nelts; i++) {
                if (fd->desc.s == p->socket_set[i].desc.s) {
                    p->nelts--;
                }
                else {
                    p->socket_set[dst] = p->socket_set[i];
                    dst++;
                }
            }
            break;
        }
    }
    return apr_pollset_remove(p->pollset, fd);
}

TCN_IMPLEMENT_CALL(jint, Poll, remove)(TCN_STDARGS, jlong pollset,
                                       jlong socket)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    apr_pollfd_t fd;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(socket != 0);

    memset(&fd, 0, sizeof(apr_pollfd_t));
    fd.desc_type = APR_POLL_SOCKET;
    fd.desc.s = J2P(socket, apr_socket_t *);

    return (jint)do_remove(p, &fd);
}


TCN_IMPLEMENT_CALL(jint, Poll, poll)(TCN_STDARGS, jlong pollset,
                                     jlong timeout, jlongArray set,
                                     jboolean remove)
{
    const apr_pollfd_t *fd = NULL;
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    jlong *pset = (*e)->GetLongArrayElements(e, set, NULL);
    apr_int32_t  i, num = 0;
    apr_status_t rv = APR_SUCCESS;

    UNREFERENCED(o);
    TCN_ASSERT(pollset != 0);

    if (rv != APR_SUCCESS)
        return (jint)(-rv);
    if (apr_pollset_poll(p->pollset, J2T(timeout), &num, &fd) != APR_SUCCESS)
        num = 0;

    if (num > 0) {
        for (i = 0; i < num; i++) {
            pset[i*4+0] = (jlong)(fd->rtnevents);
            pset[i*4+1] = P2J(fd->desc.s);
            pset[i*4+2] = P2J(fd->client_data);
            if (remove)
                do_remove(p, fd);
            fd ++;
        }
        (*e)->ReleaseLongArrayElements(e, set, pset, 0);
    }
    else
        (*e)->ReleaseLongArrayElements(e, set, pset, JNI_ABORT);

    return (jint)num;
}

TCN_IMPLEMENT_CALL(jint, Poll, maintain)(TCN_STDARGS, jlong pollset,
                                         jlongArray set, jboolean remove)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    jlong *pset = (*e)->GetLongArrayElements(e, set, NULL);
    apr_int32_t  i = 0, num = 0;
    apr_time_t now = apr_time_now();
    apr_pollfd_t fd;

    UNREFERENCED(o);
    TCN_ASSERT(pollset != 0);

    /* Check for timeout sockets */
    if (p->max_ttl > 0) {
        for (i = 0; i < p->nelts; i++) {
            if ((now - p->socket_ttl[i]) > p->max_ttl) {
                p->socket_set[i].rtnevents = APR_POLLHUP | APR_POLLIN;
                if (num < p->nelts) {
                    fd = p->socket_set[i];
                    pset[num*4+0] = (jlong)(fd.rtnevents);
                    pset[num*4+1] = P2J(fd.desc.s);
                    pset[num*4+2] = P2J(fd.client_data);
                    num++;
                }
            }
        }
        if (remove && num) {
            memset(&fd, 0, sizeof(apr_pollfd_t));
            for (i = 0; i < num; i++) {
                fd.desc_type = APR_POLL_SOCKET;
                fd.desc.s = (apr_socket_t *)pset[i*4+1];
                do_remove(p, &fd);
            }
        }
    }
    if (num)
        (*e)->ReleaseLongArrayElements(e, set, pset, 0);
    else
        (*e)->ReleaseLongArrayElements(e, set, pset, JNI_ABORT);
    return (jint)num;
}

TCN_IMPLEMENT_CALL(void, Poll, setTtl)(TCN_STDARGS, jlong pollset,
                                       jlong ttl)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    UNREFERENCED_STDARGS;
    p->max_ttl = J2T(ttl);
}

TCN_IMPLEMENT_CALL(jlong, Poll, getTtl)(TCN_STDARGS, jlong pollset)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    UNREFERENCED_STDARGS;
    return (jlong)p->max_ttl;
}
