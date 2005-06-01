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

/** SSL Utilities
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

#include "apr.h"
#include "apr_pools.h"
#include "apr_file_io.h"
#include "apr_portable.h"
#include "apr_thread_mutex.h"

#include "tcn.h"

#ifdef HAVE_OPENSSL
#include "ssl_private.h"


/*  _________________________________________________________________
**
**  Additional High-Level Functions for OpenSSL
**  _________________________________________________________________
*/

/* we initialize this index at startup time
 * and never write to it at request time,
 * so this static is thread safe.
 * also note that OpenSSL increments at static variable when
 * SSL_get_ex_new_index() is called, so we _must_ do this at startup.
 */
static int SSL_app_data2_idx = -1;

void SSL_init_app_data2_idx(void)
{
    int i;

    if (SSL_app_data2_idx > -1) {
        return;
    }

    /* we _do_ need to call this twice */
    for (i = 0; i <= 1; i++) {
        SSL_app_data2_idx =
            SSL_get_ex_new_index(0,
                                 "Second Application Data for SSL",
                                 NULL, NULL, NULL);
    }
}

void *SSL_get_app_data2(SSL *ssl)
{
    return (void *)SSL_get_ex_data(ssl, SSL_app_data2_idx);
}

void SSL_set_app_data2(SSL *ssl, void *arg)
{
    SSL_set_ex_data(ssl, SSL_app_data2_idx, (char *)arg);
    return;
}

/*
 * Return APR_SUCCESS if the named file exists and is readable
 */
static apr_status_t exists_and_readable(const char *fname, apr_pool_t *pool,
                                        apr_time_t *mtime)
{
    apr_status_t stat;
    apr_finfo_t sbuf;
    apr_file_t *fd;

    if ((stat = apr_stat(&sbuf, fname, APR_FINFO_MIN, pool)) != APR_SUCCESS)
        return stat;

    if (sbuf.filetype != APR_REG)
        return APR_EGENERAL;

    if ((stat = apr_file_open(&fd, fname, APR_READ, 0, pool)) != APR_SUCCESS)
        return stat;

    if (mtime) {
        *mtime = sbuf.mtime;
    }

    apr_file_close(fd);
    return APR_SUCCESS;
}

/* Simple echo password prompting */
int SSL_password_prompt(tcn_ssl_ctxt_t *c, char *buf, int len)
{
    int rv = 0;
    if (c && c->bio_is) {
        if (c->bio_is->flags & SSL_BIO_FLAG_RDONLY) {
            /* Use error BIO in case of stdin */
            BIO_printf(c->bio_os, "Enter password: ");
        }
        rv = BIO_gets(c->bio_is, buf, len);
        if (rv > 0) {
            /* Remove LF chars */
            char *r = strchr(buf, '\n');
            if (r) {
                *r = '\0';
                rv--;
            }
            /* Remove CR chars */
            r = strchr(buf, '\r');
            if (r) {
                *r = '\0';
                rv--;
            }
        }
    }
    return rv;
}

#else
/* OpenSSL is not supported
 * If someday we make OpenSSL optional
 * APR_ENOTIMPL will go here
 */
#error "No OpenSSL Toolkit defined."
#endif
