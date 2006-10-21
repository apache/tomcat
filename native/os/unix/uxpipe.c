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

/** UNIX AF_LOCAL network wrapper
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */


#include "tcn.h"
#include "apr_thread_mutex.h"
#include "apr_poll.h"

/* ### should be tossed in favor of APR */
#include <sys/stat.h>
#include <sys/un.h> /* for sockaddr_un */

#ifdef TCN_DO_STATISTICS
#include "apr_atomic.h"

static volatile apr_uint32_t uxp_created  = 0;
static volatile apr_uint32_t uxp_closed   = 0;
static volatile apr_uint32_t uxp_cleared  = 0;
static volatile apr_uint32_t uxp_accepted = 0;

void uxp_network_dump_statistics()
{
    fprintf(stderr, "NT Network Statistics ..\n");
    fprintf(stderr, "Sockets created         : %d\n", uxp_created);
    fprintf(stderr, "Sockets accepted        : %d\n", uxp_accepted);
    fprintf(stderr, "Sockets closed          : %d\n", uxp_closed);
    fprintf(stderr, "Sockets cleared         : %d\n", uxp_cleared);
}

#endif

#define DEFNAME     "/var/run/tomcatnativesock"
#define DEFNAME_FMT "/var/run/tomcatnativesock%08x%08x"
#define DEFSIZE     8192
#define DEFTIMEOUT  60000

#define TCN_UXP_UNKNOWN     0
#define TCN_UXP_CLIENT      1
#define TCN_UXP_ACCEPTED    2
#define TCN_UXP_SERVER      3

#define TCN_UNIX_MAXPATH    1024
typedef struct {
    apr_pool_t          *pool;
    apr_socket_t        *sock;               /* APR socket */
    int                 sd;
    struct sockaddr_un  uxaddr;
    int                 timeout;
    int                 mode;                 /* Client or server mode */
    char                name[TCN_UNIX_MAXPATH+1];
} tcn_uxp_conn_t;

static apr_status_t APR_THREAD_FUNC
uxp_socket_timeout_set(apr_socket_t *sock, apr_interval_time_t t)
{
    tcn_uxp_conn_t *con = (tcn_uxp_conn_t *)sock;
    if (t < 0)
        con->timeout = -1;
    else
        con->timeout = (int)(apr_time_as_msec(t));
    return APR_SUCCESS;
}

static apr_status_t APR_THREAD_FUNC
uxp_socket_timeout_get(apr_socket_t *sock, apr_interval_time_t *t)
{
    tcn_uxp_conn_t *con = (tcn_uxp_conn_t*)sock;
    if (con->timeout < 0)
        *t = -1;
    else
        *t = con->timeout * 1000;
    return APR_SUCCESS;
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
uxp_socket_opt_set(apr_socket_t *sock, apr_int32_t opt, apr_int32_t on)
{
    tcn_uxp_conn_t *con = (tcn_uxp_conn_t *)sock;
    return apr_socket_opt_set(con->sock, opt, on);
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
uxp_socket_opt_get(apr_socket_t *sock, apr_int32_t opt, apr_int32_t *on)
{
    tcn_uxp_conn_t *con = (tcn_uxp_conn_t *)sock;
    return apr_socket_opt_get(con->sock, opt, on);
}

static apr_status_t uxp_cleanup(void *data)
{
    tcn_uxp_conn_t *con = (tcn_uxp_conn_t *)data;

    if (con) {
        if (con->sock) {
            apr_socket_close(con->sock);
            con->sock = NULL;
        }
        if (con->mode == TCN_UXP_SERVER) {
            unlink(con->name);
            con->mode = TCN_UXP_UNKNOWN;
        }
    }

#ifdef TCN_DO_STATISTICS
    apr_atomic_inc32(&uxp_cleared);
#endif
    return APR_SUCCESS;
}

static apr_status_t APR_THREAD_FUNC
uxp_socket_shutdown(apr_socket_t *sock, apr_shutdown_how_e how)
{
    tcn_uxp_conn_t *con = (tcn_uxp_conn_t *)sock;
    return apr_socket_shutdown(con->sock, how);
}

static apr_status_t APR_THREAD_FUNC
uxp_socket_close(apr_socket_t *sock)
{
#ifdef TCN_DO_STATISTICS
    apr_atomic_inc32(&uxp_closed);
#endif
    return uxp_cleanup(sock);
}

static apr_status_t APR_THREAD_FUNC
uxp_socket_recv(apr_socket_t *sock, char *buf, apr_size_t *len)
{
    tcn_uxp_conn_t *con = (tcn_uxp_conn_t *)sock;
    return apr_socket_recv(con->sock, buf, len);
}


static apr_status_t APR_THREAD_FUNC
uxp_socket_send(apr_socket_t *sock, const char *buf,
                apr_size_t *len)
{
    tcn_uxp_conn_t *con = (tcn_uxp_conn_t *)sock;
    return apr_socket_send(con->sock, buf, len);
}

static apr_status_t APR_THREAD_FUNC
uxp_socket_sendv(apr_socket_t *sock,
                 const struct iovec *vec,
                 apr_int32_t nvec, apr_size_t *len)
{
    tcn_uxp_conn_t *con = (tcn_uxp_conn_t *)sock;
    return apr_socket_sendv(con->sock, vec, nvec, len);
}

static apr_status_t uxp_socket_cleanup(void *data)
{
    tcn_socket_t *s = (tcn_socket_t *)data;

    if (s->net->cleanup) {
        (*s->net->cleanup)(s->opaque);
        s->net->cleanup = NULL;
    }
#ifdef TCN_DO_STATISTICS
    apr_atomic_inc32(&uxp_cleared);
#endif
    return APR_SUCCESS;
}

static tcn_nlayer_t uxp_socket_layer = {
    TCN_SOCKET_UNIX,
    uxp_cleanup,
    uxp_socket_close,
    uxp_socket_shutdown,
    uxp_socket_opt_get,
    uxp_socket_opt_set,
    uxp_socket_timeout_get,
    uxp_socket_timeout_set,
    uxp_socket_send,
    uxp_socket_sendv,
    uxp_socket_recv
};

TCN_IMPLEMENT_CALL(jlong, Local, create)(TCN_STDARGS, jstring name,
                                         jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    tcn_socket_t   *s   = NULL;
    tcn_uxp_conn_t *con = NULL;
    int sd;
    TCN_ALLOC_CSTRING(name);

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);

    if ((sd = socket(AF_UNIX, SOCK_STREAM, 0)) < 0) {
        tcn_ThrowAPRException(e, apr_get_netos_error());
        return 0;
    }
#ifdef TCN_DO_STATISTICS
    uxp_created++;
#endif
    con = (tcn_uxp_conn_t *)apr_pcalloc(p, sizeof(tcn_uxp_conn_t));
    con->pool = p;
    con->mode = TCN_UXP_UNKNOWN;
    con->timeout = DEFTIMEOUT;
    con->sd = sd;
    con->uxaddr.sun_family = AF_UNIX;
    if (J2S(name)) {
        strcpy(con->uxaddr.sun_path, J2S(name));
        TCN_FREE_CSTRING(name);
    }
    else
        strcpy(con->uxaddr.sun_path, DEFNAME);
    s = (tcn_socket_t *)apr_pcalloc(p, sizeof(tcn_socket_t));
    s->pool   = p;
    s->net    = &uxp_socket_layer;
    s->opaque = con;
    apr_pool_cleanup_register(p, (const void *)s,
                              uxp_socket_cleanup,
                              apr_pool_cleanup_null);

    apr_os_sock_put(&(con->sock), &(con->sd), p);

    return P2J(s);

}

TCN_IMPLEMENT_CALL(jint, Local, bind)(TCN_STDARGS, jlong sock,
                                      jlong sa)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    UNREFERENCED_STDARGS;
    UNREFERENCED(sa);
    TCN_ASSERT(sock != 0);
    if (s->net->type == TCN_SOCKET_UNIX) {
        int rc;
        tcn_uxp_conn_t *c = (tcn_uxp_conn_t *)s->opaque;
        c->mode = TCN_UXP_SERVER;
        rc = bind(c->sd, (struct sockaddr *)&(c->uxaddr), sizeof(c->uxaddr));
        if (rc < 0)
            return errno;
        else
            return APR_SUCCESS;
    }
    else
        return APR_EINVAL;
}

TCN_IMPLEMENT_CALL(jint, Local, listen)(TCN_STDARGS, jlong sock,
                                        jint backlog)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    UNREFERENCED_STDARGS;

    TCN_ASSERT(sock != 0);
    if (s->net->type == TCN_SOCKET_UNIX) {
        tcn_uxp_conn_t *c = (tcn_uxp_conn_t *)s->opaque;
        c->mode = TCN_UXP_SERVER;
        return apr_socket_listen(c->sock, (apr_int32_t)backlog);
    }
    else
        return APR_EINVAL;
}

TCN_IMPLEMENT_CALL(jlong, Local, accept)(TCN_STDARGS, jlong sock)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_pool_t   *p = NULL;
    tcn_socket_t *a = NULL;
    tcn_uxp_conn_t *con = NULL;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);

    TCN_THROW_IF_ERR(apr_pool_create(&p, s->pool), p);
    if (s->net->type == TCN_SOCKET_UNIX) {
        apr_socklen_t len;
        tcn_uxp_conn_t *c = (tcn_uxp_conn_t *)s->opaque;
        con = (tcn_uxp_conn_t *)apr_pcalloc(p, sizeof(tcn_uxp_conn_t));
        con->pool = p;
        con->mode = TCN_UXP_ACCEPTED;
        con->timeout = c->timeout;
        len = sizeof(c->uxaddr);
        /* Block until a client connects */
        con->sd = accept(c->sd, (struct sockaddr *)&(con->uxaddr), &len);
        if (con->sd < 0) {
            tcn_ThrowAPRException(e, apr_get_os_error());
            goto cleanup;
        }
    }
    else {
        tcn_ThrowAPRException(e, APR_ENOTIMPL);
        goto cleanup;
    }
    if (con) {
#ifdef TCN_DO_STATISTICS
        apr_atomic_inc32(&uxp_accepted);
#endif
        a = (tcn_socket_t *)apr_pcalloc(p, sizeof(tcn_socket_t));
        a->pool   = p;
	    a->net    = &uxp_socket_layer;
        a->opaque = con;
        apr_pool_cleanup_register(p, (const void *)a,
                                  uxp_socket_cleanup,
                                  apr_pool_cleanup_null);
        apr_os_sock_put(&(con->sock), &(con->sd), p);
    }
    return P2J(a);
cleanup:
    if (p)
        apr_pool_destroy(p);
    return 0;
}

TCN_IMPLEMENT_CALL(jint, Local, connect)(TCN_STDARGS, jlong sock,
                                         jlong sa)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    tcn_uxp_conn_t *con = NULL;
    int rc;

    UNREFERENCED(o);
    UNREFERENCED(sa);
    TCN_ASSERT(sock != 0);
    if (s->net->type != TCN_SOCKET_UNIX)
        return APR_ENOTSOCK;
    con = (tcn_uxp_conn_t *)s->opaque;
    if (con->mode != TCN_UXP_UNKNOWN)
        return APR_EINVAL;
    do {
        rc = connect(con->sd, (const struct sockaddr *)&(con->uxaddr),
                     sizeof(con->uxaddr));
    } while (rc == -1 && errno == EINTR);

    if (rc == -1 && errno != EISCONN)
        return errno;
    con->mode = TCN_UXP_CLIENT;

    return APR_SUCCESS;
}
