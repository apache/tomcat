/* Copyright 2000-2006 The Apache Software Foundation
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

/*
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

#include "tcn.h"
#include "apr_poll.h"


#ifdef TCN_DO_STATISTICS
static int sp_created       = 0;
static int sp_destroyed     = 0;
static int sp_cleared       = 0;
#endif

/* Internal poll structure for queryset
 */

typedef struct tcn_pollset {
    apr_pool_t    *pool;
    apr_int32_t   nelts;
    apr_int32_t   nalloc;
    apr_pollset_t *pollset;
    jlong         *set;
    apr_pollfd_t  *socket_set;
    apr_interval_time_t *socket_ttl;
    apr_interval_time_t max_ttl;
#ifdef TCN_DO_STATISTICS
    int sp_added;
    int sp_max_count;
    int sp_poll;
    int sp_polled;
    int sp_max_polled;
    int sp_remove;
    int sp_removed;
    int sp_maintained;
    int sp_max_maintained;
    int sp_err_poll;
    int sp_poll_timeout;
    int sp_overflow;
    int sp_equals;
    int sp_eintr;
#endif
} tcn_pollset_t;

#ifdef TCN_DO_STATISTICS
static void sp_poll_statistics(tcn_pollset_t *p)
{
    fprintf(stderr, "Pollset Statistics ......\n");
    fprintf(stderr, "Number of added sockets : %d\n", p->sp_added);
    fprintf(stderr, "Max. number of sockets  : %d\n", p->sp_max_count);
    fprintf(stderr, "Poll calls              : %d\n", p->sp_poll);
    fprintf(stderr, "Poll timeouts           : %d\n", p->sp_poll_timeout);
    fprintf(stderr, "Poll errors             : %d\n", p->sp_err_poll);
    fprintf(stderr, "Poll overflows          : %d\n", p->sp_overflow);
    fprintf(stderr, "Polled sockets          : %d\n", p->sp_polled);
    fprintf(stderr, "Max. Polled sockets     : %d\n", p->sp_max_polled);
    fprintf(stderr, "Poll remove             : %d\n", p->sp_remove);
    fprintf(stderr, "Total removed           : %d\n", p->sp_removed);
    fprintf(stderr, "Maintained              : %d\n", p->sp_maintained);
    fprintf(stderr, "Max. maintained         : %d\n", p->sp_max_maintained);
    fprintf(stderr, "Number of duplicates    : %d\n", p->sp_equals);
    fprintf(stderr, "Number of interrupts    : %d\n", p->sp_eintr);

}

static apr_status_t sp_poll_cleanup(void *data)
{
    sp_cleared++;
    sp_poll_statistics(data);
    return APR_SUCCESS;
}

void sp_poll_dump_statistics()
{
    fprintf(stderr, "Poll Statistics .........\n");
    fprintf(stderr, "Polls created           : %d\n", sp_created);
    fprintf(stderr, "Polls destroyed         : %d\n", sp_destroyed);
    fprintf(stderr, "Polls cleared           : %d\n", sp_cleared);
}
#endif

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
    tps = apr_pcalloc(p, sizeof(tcn_pollset_t));
    TCN_CHECK_ALLOCATED(tps);
    tps->pollset = pollset;
    tps->set        = apr_palloc(p, size * sizeof(jlong) * 2);
    TCN_CHECK_ALLOCATED(tps->set);
    tps->socket_set = apr_palloc(p, size * sizeof(apr_pollfd_t));
    TCN_CHECK_ALLOCATED(tps->socket_set);
    tps->socket_ttl = apr_palloc(p, size * sizeof(apr_interval_time_t));
    TCN_CHECK_ALLOCATED(tps->socket_ttl);
    tps->nelts  = 0;
    tps->nalloc = size;
    tps->pool   = p;
    tps->max_ttl = J2T(ttl);
#ifdef TCN_DO_STATISTICS
    sp_created++;
    apr_pool_cleanup_register(p, (const void *)tps,
                              sp_poll_cleanup,
                              apr_pool_cleanup_null);
#endif
cleanup:
    return P2J(tps);
}

TCN_IMPLEMENT_CALL(jint, Poll, destroy)(TCN_STDARGS, jlong pollset)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);

    UNREFERENCED_STDARGS;
    TCN_ASSERT(pollset != 0);
#ifdef TCN_DO_STATISTICS
    sp_destroyed++;
    apr_pool_cleanup_kill(p->pool, p, sp_poll_cleanup);
    sp_poll_statistics(p);
#endif
    return (jint)apr_pollset_destroy(p->pollset);
}

TCN_IMPLEMENT_CALL(jint, Poll, add)(TCN_STDARGS, jlong pollset,
                                    jlong socket, jint reqevents)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    tcn_socket_t *s  = J2P(socket, tcn_socket_t *);
    apr_pollfd_t fd;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(socket != 0);

    if (p->nelts == p->nalloc) {
#ifdef TCN_DO_STATISTICS
        p->sp_overflow++;
#endif
        return APR_ENOMEM;
    }
    memset(&fd, 0, sizeof(apr_pollfd_t));
    fd.desc_type = APR_POLL_SOCKET;
    fd.reqevents = (apr_int16_t)reqevents;
    fd.desc.s    = s->sock;
    fd.client_data = s;
    if (p->max_ttl > 0)
        p->socket_ttl[p->nelts] = apr_time_now();
    else
        p->socket_ttl[p->nelts] = 0;

    p->socket_set[p->nelts] = fd;
    p->nelts++;
#ifdef TCN_DO_STATISTICS
    p->sp_added++;
    p->sp_max_count = TCN_MAX(p->sp_max_count, p->sp_added);
#endif
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
#ifdef TCN_DO_STATISTICS
            p->sp_removed++;
#endif
            for (i++; i < old_nelts; i++) {
                if (fd->desc.s == p->socket_set[i].desc.s) {
#ifdef TCN_DO_STATISTICS
                    p->sp_equals++;
#endif
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

static void remove_all(tcn_pollset_t *p)
{
    apr_int32_t i;
    for (i = 0; i < p->nelts; i++) {
        apr_pollset_remove(p->pollset, &(p->socket_set[i]));
#ifdef TCN_DO_STATISTICS
        p->sp_removed++;
#endif
    }
    p->nelts = 0;
}

TCN_IMPLEMENT_CALL(jint, Poll, remove)(TCN_STDARGS, jlong pollset,
                                       jlong socket)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    tcn_socket_t *s  = J2P(socket, tcn_socket_t *);
    apr_pollfd_t fd;

    UNREFERENCED_STDARGS;
    TCN_ASSERT(socket != 0);

    memset(&fd, 0, sizeof(apr_pollfd_t));
    fd.desc_type = APR_POLL_SOCKET;
    fd.desc.s    = s->sock;
    fd.reqevents = APR_POLLIN | APR_POLLOUT;
#ifdef TCN_DO_STATISTICS
    p->sp_remove++;
#endif

    return (jint)do_remove(p, &fd);
}


TCN_IMPLEMENT_CALL(jint, Poll, poll)(TCN_STDARGS, jlong pollset,
                                     jlong timeout, jlongArray set,
                                     jboolean remove)
{
    const apr_pollfd_t *fd = NULL;
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    apr_int32_t  i, num = 0;
    apr_status_t rv = APR_SUCCESS;
    apr_interval_time_t ptime = J2T(timeout);
    UNREFERENCED(o);
    TCN_ASSERT(pollset != 0);

#ifdef TCN_DO_STATISTICS
     p->sp_poll++;
#endif

    if (ptime > 0 && p->max_ttl >= 0) {
        apr_time_t now = apr_time_now();

        /* Find the minimum timeout */
        for (i = 0; i < p->nelts; i++) {
            apr_interval_time_t t = now - p->socket_ttl[i];
            if (t >= p->max_ttl) {
                ptime = 0;
                break;
            }
            else {
                ptime = TCN_MIN(p->max_ttl - t, ptime);
            }
        }
    }
    else if (ptime < 0)
        ptime = 0;
    for (;;) {
        rv = apr_pollset_poll(p->pollset, ptime, &num, &fd);
        if (rv != APR_SUCCESS) {
            if (APR_STATUS_IS_EINTR(rv)) {
#ifdef TCN_DO_STATISTICS
                p->sp_eintr++;
#endif
                continue;
            }
            TCN_ERROR_WRAP(rv);
#ifdef TCN_DO_STATISTICS
            if (rv == TCN_TIMEUP)
                p->sp_poll_timeout++;
            else
                p->sp_err_poll++;
#endif
            num = (apr_int32_t)(-rv);
        }
        break;
    }
    if (num > 0) {
#ifdef TCN_DO_STATISTICS
         p->sp_polled += num;
         p->sp_max_polled = TCN_MAX(p->sp_max_polled, num);
#endif
        for (i = 0; i < num; i++) {
            p->set[i*2+0] = (jlong)(fd->rtnevents);
            p->set[i*2+1] = P2J(fd->client_data);
            if (remove)
                do_remove(p, fd);
            fd ++;
        }
        (*e)->SetLongArrayRegion(e, set, 0, num * 2, p->set);
    }

    return (jint)num;
}

TCN_IMPLEMENT_CALL(jint, Poll, maintain)(TCN_STDARGS, jlong pollset,
                                         jlongArray set, jboolean remove)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    apr_int32_t  i = 0, num = 0;
    apr_time_t now = apr_time_now();
    apr_pollfd_t fd;

    UNREFERENCED(o);
    TCN_ASSERT(pollset != 0);

    /* Check for timeout sockets */
    if (p->max_ttl > 0) {
        for (i = 0; i < p->nelts; i++) {
            if ((now - p->socket_ttl[i]) >= p->max_ttl) {
                fd = p->socket_set[i];
                p->set[num++] = P2J(fd.client_data);
            }
        }
        if (remove && num) {
            memset(&fd, 0, sizeof(apr_pollfd_t));
#ifdef TCN_DO_STATISTICS
             p->sp_maintained += num;
             p->sp_max_maintained = TCN_MAX(p->sp_max_maintained, num);
#endif
            for (i = 0; i < num; i++) {
                fd.desc_type = APR_POLL_SOCKET;
                fd.reqevents = APR_POLLIN | APR_POLLOUT;
                fd.desc.s = (J2P(p->set[i], tcn_socket_t *))->sock;
                do_remove(p, &fd);
            }
        }
    }
    else if (p->max_ttl == 0) {
        for (i = 0; i < p->nelts; i++) {
            fd = p->socket_set[i];
            p->set[num++] = P2J(fd.client_data);
        }
        if (remove) {
            remove_all(p);
#ifdef TCN_DO_STATISTICS
            p->sp_maintained += num;
            p->sp_max_maintained = TCN_MAX(p->sp_max_maintained, num);
#endif
        }
    }
    if (num)
        (*e)->SetLongArrayRegion(e, set, 0, num, p->set);
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

TCN_IMPLEMENT_CALL(jint, Poll, pollset)(TCN_STDARGS, jlong pollset,
                                        jlongArray set)
{
    tcn_pollset_t *p = J2P(pollset,  tcn_pollset_t *);
    apr_int32_t  i = 0;
    apr_pollfd_t fd;

    UNREFERENCED(o);
    TCN_ASSERT(pollset != 0);

    for (i = 0; i < p->nelts; i++) {
        p->socket_set[i].rtnevents = APR_POLLHUP | APR_POLLIN;
        fd = p->socket_set[i];
        p->set[i*2+0] = (jlong)(fd.rtnevents);
        p->set[i*2+1] = P2J(fd.client_data);
    }
    if (p->nelts)
        (*e)->SetLongArrayRegion(e, set, 0, p->nelts * 2, p->set);
    return (jint)p->nelts;
}
