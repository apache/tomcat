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

/** NT Pipes network wrapper
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */


#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0500
#endif
#define STRICT
#include <winsock2.h>
#include <mswsock.h>
#include <ws2tcpip.h>
#include <sddl.h>

#include "tcn.h"
#include "apr_thread_mutex.h"
#include "apr_poll.h"

#ifdef TCN_DO_STATISTICS
#include "apr_atomic.h"

static volatile apr_uint32_t ntp_created  = 0;
static volatile apr_uint32_t ntp_closed   = 0;
static volatile apr_uint32_t ntp_cleared  = 0;
static volatile apr_uint32_t ntp_accepted = 0;

void ntp_network_dump_statistics()
{
    fprintf(stderr, "NT Network Statistics ..\n");
    fprintf(stderr, "Sockets created         : %d\n", ntp_created);
    fprintf(stderr, "Sockets accepted        : %d\n", ntp_accepted);
    fprintf(stderr, "Sockets closed          : %d\n", ntp_closed);
    fprintf(stderr, "Sockets cleared         : %d\n", ntp_cleared);
}

#endif

#define DEFNAME     "\\\\.\\PIPE\\TOMCATNATIVEPIPE"
#define DEFNAME_FMT "\\\\.\\PIPE\\TOMCATNATIVEPIPE%08X%08X"
#define DEFSIZE     8192
#define DEFTIMEOUT  60000

#define TCN_NTP_UNKNOWN 0
#define TCN_NTP_CLIENT  1
#define TCN_NTP_SERVER  2

typedef struct {
    apr_pool_t     *pool;
    apr_socket_t   *sock;               /* Dummy socket */
    OVERLAPPED     rd_o;
    OVERLAPPED     wr_o;
    HANDLE         h_pipe;
    HANDLE         rd_event;
    HANDLE         wr_event;
    DWORD          timeout;
    int            mode;                 /* Client or server mode */
    int            nmax;
    DWORD          sndbuf;
    DWORD          rcvbuf;
    char           name[MAX_PATH+1];
    SECURITY_ATTRIBUTES sa;
} tcn_ntp_conn_t;

static const char *NTSD_STRING = "D:"     /* Discretionary ACL */
                   "(D;OICI;GA;;;BG)"     /* Deny access to Built-in Guests */
                   "(D;OICI;GA;;;AN)"     /* Deny access to Anonymous Logon */
                   "(A;OICI;GRGWGX;;;AU)" /* Allow read/write/execute to Authenticated Users */
                   "(A;OICI;GA;;;BA)"     /* Allow full control to Administrators */
                   "(A;OICI;GA;;;LS)"     /* Allow full control to Local service account */
                   "(A;OICI;GA;;;SY)";    /* Allow full control to Local system */



static apr_status_t APR_THREAD_FUNC
ntp_socket_timeout_set(apr_socket_t *sock, apr_interval_time_t t)
{
    tcn_ntp_conn_t *con = (tcn_ntp_conn_t *)sock;
    if (t < 0)
        con->timeout = INFINITE;
    else
        con->timeout = (DWORD)(apr_time_as_msec(t));
    return APR_SUCCESS;
}

static apr_status_t APR_THREAD_FUNC
ntp_socket_timeout_get(apr_socket_t *sock, apr_interval_time_t *t)
{
    tcn_ntp_conn_t *con = (tcn_ntp_conn_t*)sock;
    if (con->timeout == INFINITE)
        *t = -1;
    else
        *t = con->timeout * 1000;
    return APR_SUCCESS;
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
ntp_socket_opt_set(apr_socket_t *sock, apr_int32_t opt, apr_int32_t on)
{
    tcn_ntp_conn_t *con = (tcn_ntp_conn_t *)sock;
    apr_status_t rv = APR_SUCCESS;
    switch (opt) {
        case APR_SO_SNDBUF:
            con->sndbuf = (DWORD)on;
        break;
        case APR_SO_RCVBUF:
            con->rcvbuf = (DWORD)on;
        break;
        default:
            rv = APR_EINVAL;
        break;
    }
    return rv;
}

static APR_INLINE apr_status_t APR_THREAD_FUNC
ntp_socket_opt_get(apr_socket_t *sock, apr_int32_t opt, apr_int32_t *on)
{
    tcn_ntp_conn_t *con = (tcn_ntp_conn_t *)sock;
    apr_status_t rv = APR_SUCCESS;
    switch (opt) {
        case APR_SO_SNDBUF:
            *on = con->sndbuf;
        break;
        case APR_SO_RCVBUF:
            *on = con->rcvbuf;
        break;
        default:
            rv = APR_EINVAL;
        break;
    }
    return rv;
}

static apr_status_t ntp_cleanup(void *data)
{
    tcn_ntp_conn_t *con = (tcn_ntp_conn_t *)data;

    if (con) {
        if (con->h_pipe) {
            FlushFileBuffers(con->h_pipe);
            CloseHandle(con->h_pipe);
            con->h_pipe = NULL;
        }
        if (con->rd_event) {
            CloseHandle(con->rd_event);
            con->rd_event = NULL;
        }
        if (con->wr_event) {
            CloseHandle(con->wr_event);
            con->wr_event= NULL;
        }
    }

#ifdef TCN_DO_STATISTICS
    apr_atomic_inc32(&ntp_cleared);
#endif
    return APR_SUCCESS;
}

static apr_status_t APR_THREAD_FUNC
ntp_socket_shutdown(apr_socket_t *sock, apr_shutdown_how_e how)
{
    UNREFERENCED(how);
    return ntp_cleanup(sock);;
}

static apr_status_t APR_THREAD_FUNC
ntp_socket_close(apr_socket_t *sock)
{
#ifdef TCN_DO_STATISTICS
    apr_atomic_inc32(&ntp_closed);
#endif
    return ntp_cleanup(sock);;
}

static apr_status_t APR_THREAD_FUNC
ntp_socket_recv(apr_socket_t *sock, char *buf, apr_size_t *len)
{
    tcn_ntp_conn_t *con = (tcn_ntp_conn_t *)sock;
    DWORD readed;

    if (!ReadFile(con->h_pipe, buf, *len, &readed, &con->rd_o)) {
        DWORD err = GetLastError();
        if (err == ERROR_IO_PENDING) {
            DWORD r = WaitForSingleObject(con->rd_event, con->timeout);
            if (r == WAIT_TIMEOUT)
                return APR_TIMEUP;
            else if (r != WAIT_OBJECT_0)
                return APR_EOF;
        }
        else if (err == ERROR_BROKEN_PIPE || err == ERROR_NO_DATA) {
            /* Server closed the pipe */
            return APR_EOF;
        }
        GetOverlappedResult(con->h_pipe, &con->rd_o, &readed, FALSE);
    }
    *len = readed;
    return APR_SUCCESS;
}

static apr_status_t APR_THREAD_FUNC
ntp_socket_send(apr_socket_t *sock, const char *buf,
                apr_size_t *len)
{
    tcn_ntp_conn_t *con = (tcn_ntp_conn_t *)sock;
    DWORD written;

    if (!WriteFile(con->h_pipe, buf, *len, &written, &con->wr_o)) {
        DWORD err = GetLastError();
        if (err == ERROR_IO_PENDING) {
            DWORD r = WaitForSingleObject(con->wr_event, con->timeout);
            if (r == WAIT_TIMEOUT)
                return APR_TIMEUP;
            else if (r != WAIT_OBJECT_0)
                return APR_EOF;
        }
        else if (err == ERROR_BROKEN_PIPE || err == ERROR_NO_DATA) {
            /* Server closed the pipe */
            return APR_EOF;
        }
        GetOverlappedResult(con->h_pipe, &con->wr_o, &written, FALSE);
    }
    *len = written;
    return APR_SUCCESS;
}

static apr_status_t APR_THREAD_FUNC
ntp_socket_sendv(apr_socket_t *sock,
                 const struct iovec *vec,
                 apr_int32_t nvec, apr_size_t *len)
{
    tcn_ntp_conn_t *con = (tcn_ntp_conn_t *)sock;
    apr_status_t rv;
    apr_size_t written = 0;
    apr_int32_t i;

    for (i = 0; i < nvec; i++) {
        apr_size_t rd = vec[i].iov_len;
        if ((rv = ntp_socket_send((apr_socket_t *)con,
                                  vec[i].iov_base, &rd)) != APR_SUCCESS) {
            *len = written;
            return rv;
        }
        written += rd;
    }
    *len = written;
    return APR_SUCCESS;
}

static apr_status_t ntp_socket_cleanup(void *data)
{
    tcn_socket_t *s = (tcn_socket_t *)data;

    if (s->net->cleanup) {
        (*s->net->cleanup)(s->opaque);
        s->net->cleanup = NULL;
    }
#ifdef TCN_DO_STATISTICS
    apr_atomic_inc32(&ntp_cleared);
#endif
    return APR_SUCCESS;
}

static tcn_nlayer_t ntp_socket_layer = {
    TCN_SOCKET_NTPIPE,
    ntp_cleanup,
    ntp_socket_close,
    ntp_socket_shutdown,
    ntp_socket_opt_get,
    ntp_socket_opt_set,
    ntp_socket_timeout_get,
    ntp_socket_timeout_set,
    ntp_socket_send,
    ntp_socket_sendv,
    ntp_socket_recv
};

static BOOL create_DACL(LPSECURITY_ATTRIBUTES psa)
{

    return ConvertStringSecurityDescriptorToSecurityDescriptor(
                NTSD_STRING,
                SDDL_REVISION_1,
                &(psa->lpSecurityDescriptor),
                NULL);
}

TCN_IMPLEMENT_CALL(jlong, Local, create)(TCN_STDARGS, jstring name,
                                         jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    tcn_socket_t   *s   = NULL;
    tcn_ntp_conn_t *con = NULL;
    TCN_ALLOC_CSTRING(name);

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);

#ifdef TCN_DO_STATISTICS
    ntp_created++;
#endif
    con = (tcn_ntp_conn_t *)apr_pcalloc(p, sizeof(tcn_ntp_conn_t));
    con->pool = p;
    con->mode = TCN_NTP_UNKNOWN;
    con->nmax = PIPE_UNLIMITED_INSTANCES;
    con->timeout = DEFTIMEOUT;
    con->sndbuf  = DEFSIZE;
    con->rcvbuf  = DEFSIZE;
    if (J2S(name)) {
        strncpy(con->name, J2S(name), MAX_PATH);
        con->name[MAX_PATH] = '\0';
        TCN_FREE_CSTRING(name);
    }
    else
        strcpy(con->name, DEFNAME);
    con->sa.nLength = sizeof(con->sa);
    con->sa.bInheritHandle = TRUE;
    if (!create_DACL(&con->sa)) {
        tcn_ThrowAPRException(e, apr_get_os_error());
        return 0;
    }

    s = (tcn_socket_t *)apr_pcalloc(p, sizeof(tcn_socket_t));
    s->pool   = p;
    s->net    = &ntp_socket_layer;
    s->opaque = con;
    apr_pool_cleanup_register(p, (const void *)s,
                              ntp_socket_cleanup,
                              apr_pool_cleanup_null);

    fflush(stderr);
    return P2J(s);

}

TCN_IMPLEMENT_CALL(jint, Local, bind)(TCN_STDARGS, jlong sock,
                                      jlong sa)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    UNREFERENCED_STDARGS;
    UNREFERENCED(sa);
    TCN_ASSERT(sock != 0);
    if (s->net->type == TCN_SOCKET_NTPIPE) {
        tcn_ntp_conn_t *c = (tcn_ntp_conn_t *)s->opaque;
        c->mode = TCN_NTP_SERVER;
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
    if (s->net->type == TCN_SOCKET_NTPIPE) {
        tcn_ntp_conn_t *c = (tcn_ntp_conn_t *)s->opaque;
        c->mode = TCN_NTP_SERVER;
        if (backlog > 0)
            c->nmax = backlog;
        else
            c->nmax = PIPE_UNLIMITED_INSTANCES;
        return APR_SUCCESS;
    }
    else
        return APR_EINVAL;
}

TCN_IMPLEMENT_CALL(jlong, Local, accept)(TCN_STDARGS, jlong sock)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_pool_t   *p = NULL;
    tcn_socket_t *a = NULL;
    tcn_ntp_conn_t *con = NULL;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);

    TCN_THROW_IF_ERR(apr_pool_create(&p, s->pool), p);
    if (s->net->type == TCN_SOCKET_NTPIPE) {
        tcn_ntp_conn_t *c = (tcn_ntp_conn_t *)s->opaque;
        con = (tcn_ntp_conn_t *)apr_pcalloc(p, sizeof(tcn_ntp_conn_t));
        con->pool = p;
        con->mode = TCN_NTP_SERVER;
        con->nmax = c->nmax;
        con->timeout = c->timeout;
        strcpy(con->name, c->name);
        con->h_pipe = CreateNamedPipe(con->name,
                                      PIPE_ACCESS_DUPLEX | FILE_FLAG_OVERLAPPED,
                                      PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT,
                                      con->nmax,
                                      con->sndbuf,
                                      con->rcvbuf,
                                      con->timeout,
                                      &c->sa);
        if (con->h_pipe == INVALID_HANDLE_VALUE) {
            tcn_ThrowAPRException(e, apr_get_os_error());
            goto cleanup;
        }
        /* Block until a client connects */
        if (!ConnectNamedPipe(con->h_pipe, NULL)) {
            DWORD err = GetLastError();
            if (err != ERROR_PIPE_CONNECTED) {
                CloseHandle(con->h_pipe);
                tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(err));
                goto cleanup;
            }
        }
        /* Create overlapped events */
        con->rd_event    = CreateEvent(NULL, TRUE, FALSE, NULL);
        con->rd_o.hEvent = con->rd_event;
        con->wr_event    = CreateEvent(NULL, TRUE, FALSE, NULL);
        con->wr_o.hEvent = con->wr_event;
    }
    else {
        tcn_ThrowAPRException(e, APR_ENOTIMPL);
        goto cleanup;
    }
    if (con) {
#ifdef TCN_DO_STATISTICS
        apr_atomic_inc32(&ntp_accepted);
#endif
        a = (tcn_socket_t *)apr_pcalloc(p, sizeof(tcn_socket_t));
        a->pool   = p;
        a->net    = &ntp_socket_layer;
        a->opaque = con;
        apr_pool_cleanup_register(p, (const void *)a,
                                  ntp_socket_cleanup,
                                  apr_pool_cleanup_null);
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
    apr_pool_t   *p = NULL;
    tcn_socket_t *a = NULL;
    tcn_ntp_conn_t *con = NULL;

    UNREFERENCED(o);
    UNREFERENCED(sa);
    TCN_ASSERT(sock != 0);
    if (s->net->type != TCN_SOCKET_NTPIPE)
        return APR_ENOTSOCK;
    con = (tcn_ntp_conn_t *)s->opaque;
    if (con->mode == TCN_NTP_SERVER)
        return APR_EINVAL;
    con->mode = TCN_NTP_CLIENT;

    while (TRUE) {
        con->h_pipe = CreateFile(con->name,
                                 GENERIC_WRITE | GENERIC_READ,
                                 FILE_SHARE_READ | FILE_SHARE_WRITE ,
                                 NULL,
                                 OPEN_EXISTING,
                                 FILE_FLAG_OVERLAPPED,
                                 NULL);
        if (con->h_pipe != INVALID_HANDLE_VALUE)
            break;
        if (GetLastError() == ERROR_PIPE_BUSY) {
            /* All pipe instances are busy, so wait for
             * timeout value specified by the server process in
             * the CreateNamedPipe function.
             */
            if (!WaitNamedPipe(con->name, NMPWAIT_USE_DEFAULT_WAIT))
                return apr_get_os_error();
        }
        else
            return apr_get_os_error();
    }

    /* Create overlapped events */
    con->rd_event    = CreateEvent(NULL, TRUE, FALSE, NULL);
    con->rd_o.hEvent = con->rd_event;
    con->wr_event    = CreateEvent(NULL, TRUE, FALSE, NULL);
    con->wr_o.hEvent = con->wr_event;

    return APR_SUCCESS;
}
