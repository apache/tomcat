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


TCN_IMPLEMENT_CALL(jlong, Poll, create)(TCN_STDARGS, jint size,
                                        jlong pool, jint flags)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_pollset_t *pollset = NULL;

    UNREFERENCED(o);

    TCN_THROW_IF_ERR(apr_pollset_create(&pollset,
                     (apr_uint32_t)size, p, (apr_uint32_t)flags),
                     pollset);

cleanup:
    return P2J(pollset);

}

TCN_IMPLEMENT_CALL(jint, Poll, destroy)(TCN_STDARGS, jlong pollset)
{
    apr_pollset_t *p = J2P(pollset,  apr_pollset_t *);

    UNREFERENCED_STDARGS;;
    return (jint)apr_pollset_destroy(p);
}

TCN_IMPLEMENT_CALL(jint, Poll, add)(TCN_STDARGS, jlong pollset,
                                    jlong socket, jlong data,
                                    jint reqevents, jint rtnevents)
{
    apr_pollset_t *p = J2P(pollset,  apr_pollset_t *);
    apr_pollfd_t fd;

    UNREFERENCED_STDARGS;

    memset(&fd, 0, sizeof(apr_pollfd_t));
    fd.desc_type = APR_POLL_SOCKET;
    fd.reqevents = (apr_int16_t)reqevents;
    fd.rtnevents = (apr_int16_t)rtnevents;
    fd.desc.s = J2P(socket, apr_socket_t *);
    fd.client_data = J2P(data, void *);

    return (jint)apr_pollset_add(p, &fd);
}

TCN_IMPLEMENT_CALL(jint, Poll, remove)(TCN_STDARGS, jlong pollset,
                                       jlong socket)
{
    apr_pollset_t *p = J2P(pollset,  apr_pollset_t *);
    apr_pollfd_t fd;

    UNREFERENCED_STDARGS;;

    memset(&fd, 0, sizeof(apr_pollfd_t));
    fd.desc_type = APR_POLL_SOCKET;
    fd.desc.s = J2P(socket, apr_socket_t *);

    return (jint)apr_pollset_remove(p, &fd);
}

TCN_IMPLEMENT_CALL(jint, Poll, poll)(TCN_STDARGS, jlong pollset,
                                     jlong timeout, jlongArray set)
{
    const apr_pollfd_t *fd = NULL;
    apr_pollset_t *p = J2P(pollset,  apr_pollset_t *);
    jlong *pset = (*e)->GetLongArrayElements(e, set, NULL);
    apr_int32_t i, num = 0;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_pollset_poll(p, J2T(timeout),
                        &num, &fd), num);

cleanup:
    if (num) {
        for (i = 0; i < num; i++) {
            pset[i] = P2J(fd);
            fd ++;
        }
        (*e)->ReleaseLongArrayElements(e, set, pset, 0);
    }
    else
        (*e)->ReleaseLongArrayElements(e, set, pset, JNI_ABORT);

    return (jint)num;
}

TCN_IMPLEMENT_CALL(jlong, Poll, socket)(TCN_STDARGS, jlong pollfd)
{
    apr_pollfd_t *fd = J2P(pollfd,  apr_pollfd_t *);
    UNREFERENCED_STDARGS;;
    return P2J(fd->desc.s);
}

TCN_IMPLEMENT_CALL(jlong, Poll, data)(TCN_STDARGS, jlong pollfd)
{
    apr_pollfd_t *fd = J2P(pollfd,  apr_pollfd_t *);
    UNREFERENCED_STDARGS;;
    return P2J(fd->client_data);
}

TCN_IMPLEMENT_CALL(jint, Poll, events)(TCN_STDARGS, jlong pollfd)
{
    apr_pollfd_t *fd = J2P(pollfd,  apr_pollfd_t *);
    UNREFERENCED_STDARGS;;
    return (jint)fd->rtnevents;
}
