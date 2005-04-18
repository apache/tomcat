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
#include "apr_thread_mutex.h"
#include "tcn.h"

/* Internal poll structure for queryset
 */

typedef struct tcn_pollset {
    apr_pool_t    *pool;
    apr_int32_t   nelts;
    apr_int32_t   nadds;
    apr_int32_t   nalloc;
    apr_pollset_t *pollset;
    apr_thread_mutex_t *mutex;
    apr_pollfd_t  *query_set;
    apr_pollfd_t  *query_add;
    apr_time_t    *query_ttl;
    apr_interval_time_t max_ttl;
} tcn_pollset_t;

TCN_IMPLEMENT_CALL(jlong, Poll, create)(TCN_STDARGS, jint size,
                                        jlong pool, jint flags,
                                        jlong ttl)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_pollset_t *pollset = NULL;
    tcn_pollset_t *tps = NULL;
    apr_thread_mutex_t *mutex = NULL;
    apr_uint32_t f = (apr_uint32_t)flags;
    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);

    TCN_THROW_IF_ERR(apr_thread_mutex_create(&mutex,
                     APR_THREAD_MUTEX_DEFAULT, p),  mutex);

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
    tps->mutex   = mutex;
    tps->query_set = apr_palloc(p, size * sizeof(apr_pollfd_t));
    tps->query_add = apr_palloc(p, size * sizeof(apr_pollfd_t));
    tps->query_ttl = apr_palloc(p, size * sizeof(apr_time_t));
    tps->nelts  = 0;
    tps->nadds  = 0;
    tps->nalloc = size;
    tps->pool   = p;
    tps->max_ttl = J2T(ttl);
    return P2J(tps);
cleanup:
    if (mutex)
        apr_thread_mutex_destroy(mutex);
    return 0;
}

TCN_IMPLEMENT_CALL(jint, Poll, destroy)(TCN_STDARGS, jlong pollset)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(pollset != 0);
    apr_thread_mutex_destroy(p->mutex);
    return (jint)apr_pollset_destroy(p->pollset);
}

TCN_IMPLEMENT_CALL(jint, Poll, add)(TCN_STDARGS, jlong pollset,
                                    jlong socket, jlong data,
                                    jint reqevents)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    apr_pollfd_t fd;
    apr_status_t rv;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(socket != 0);

    if (p->nadds == p->nalloc)
        return APR_ENOMEM;
    if ((rv = apr_thread_mutex_lock(p->mutex)) != APR_SUCCESS)
        return rv;
    memset(&fd, 0, sizeof(apr_pollfd_t));
    fd.desc_type = APR_POLL_SOCKET;
    fd.reqevents = (apr_int16_t)reqevents;
    fd.desc.s = J2P(socket, apr_socket_t *);
    fd.client_data = J2P(data, void *);
    p->query_add[p->nadds] = fd;
    p->nadds++;
    apr_thread_mutex_unlock(p->mutex);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, Poll, remove)(TCN_STDARGS, jlong pollset,
                                       jlong socket)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    apr_pollfd_t fd;
    apr_int32_t i;
    apr_status_t rv;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(socket != 0);

    memset(&fd, 0, sizeof(apr_pollfd_t));
    fd.desc_type = APR_POLL_SOCKET;
    fd.desc.s = J2P(socket, apr_socket_t *);

    if ((rv = apr_thread_mutex_lock(p->mutex)) != APR_SUCCESS)
        return (jint)rv;
    for (i = 0; i < p->nelts; i++) {
        if (fd.desc.s == p->query_set[i].desc.s) {
            /* Found an instance of the fd: remove this and any other copies */
            apr_int32_t dst = i;
            apr_int32_t old_nelts = p->nelts;
            p->nelts--;
            for (i++; i < old_nelts; i++) {
                if (fd.desc.s == p->query_set[i].desc.s) {
                    p->nelts--;
                }
                else {
                    p->query_set[dst] = p->query_set[i];
                    dst++;
                }
            }
            break;
        }
    }
    /* Remove from add queue if present
     * This is unlikely to happen, but do it anyway.
     */
    for (i = 0; i < p->nadds; i++) {
        if (fd.desc.s == p->query_add[i].desc.s) {
            /* Found an instance of the fd: remove this and any other copies */
            apr_int32_t dst = i;
            apr_int32_t old_nelts = p->nadds;
            p->nadds--;
            for (i++; i < old_nelts; i++) {
                if (fd.desc.s == p->query_add[i].desc.s) {
                    p->nadds--;
                }
                else {
                    p->query_add[dst] = p->query_add[i];
                    dst++;
                }
            }
            break;
        }
    }

    rv = apr_pollset_remove(p->pollset, &fd);
    apr_thread_mutex_unlock(p->mutex);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, Poll, poll)(TCN_STDARGS, jlong pollset,
                                     jlong timeout, jlongArray set)
{
    const apr_pollfd_t *fd = NULL;
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    jlong *pset = (*e)->GetLongArrayElements(e, set, NULL);
    apr_int32_t  n, i = 0, num = 0;
    apr_status_t rv = APR_SUCCESS;

    UNREFERENCED(o);
    TCN_ASSERT(pollset != 0);

    if ((rv = apr_thread_mutex_lock(p->mutex)) != APR_SUCCESS)
        return (jint)(-rv);
    /* Add what is present in add queue */
    for (n = 0; n < p->nadds; n++) {
        apr_pollfd_t pf = p->query_add[n];
        if (p->nelts == p->nalloc) {
            /* In case the add queue is too large
             * skip adding to pollset
             */
            break;
        }
        if ((rv = apr_pollset_add(p->pollset, &pf)) != APR_SUCCESS)
            break;
        p->query_ttl[p->nelts] = apr_time_now();
        p->query_set[p->nelts] = pf;
        p->nelts++;
    }
    p->nadds = 0;
    apr_thread_mutex_unlock(p->mutex);
    if (rv != APR_SUCCESS)
        return (jint)(-rv);
    rv = apr_pollset_poll(p->pollset, J2T(timeout), &num, &fd);
    apr_thread_mutex_lock(p->mutex);
    if (rv != APR_SUCCESS)
        num = 0;

    if (num > 0) {
        for (i = 0; i < num; i++) {
            pset[i*4+0] = (jlong)(fd->rtnevents);
            pset[i*4+1] = P2J(fd->desc.s);
            pset[i*4+2] = P2J(fd->client_data);
            fd ++;
        }
    }
    /* In any case check for timeout sockets */
    if (p->max_ttl > 0) {
        apr_time_t now = apr_time_now();
        /* TODO: Add thread mutex protection
         * or make sure the Java part is synchronized.
         */
        for (n = 0; n < p->nelts; n++) {
            if ((now - p->query_ttl[n]) > p->max_ttl) {
                p->query_set[n].rtnevents = APR_POLLHUP | APR_POLLIN;
                if (num < p->nelts) {
                    pset[num*4+0] = (jlong)(p->query_set[n].rtnevents);
                    pset[num*4+1] = P2J(p->query_set[n].desc.s);
                    pset[num*4+2] = P2J(p->query_set[n].client_data);
                    num++;
                }
            }
        }
    }
    apr_thread_mutex_unlock(p->mutex);
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
