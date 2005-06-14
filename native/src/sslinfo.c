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

/** SSL info wrapper
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

TCN_IMPLEMENT_CALL(jobject, SSLSocket, getSessionId)(TCN_STDARGS, jlong sock)
{
    tcn_ssl_conn_t *s = J2P(sock, tcn_ssl_conn_t *);
    SSL_SESSION *session;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);
    if ((session = SSL_get_session(s->ssl)) != NULL) {
        jbyteArray array;
        jsize      len = (jsize)session->session_id_length;
        array = (*e)->NewByteArray(e, len);
        if (array) {
            (*e)->SetByteArrayRegion(e, array, 0, len,
                                     (jbyte *)(&session->session_id[0]));
        }
        return array;
    }
    else
        return NULL;
}


#else
/* OpenSSL is not supported
 * If someday we make OpenSSL optional
 * APR_ENOTIMPL will go here
 */
#error "No OpenSSL Toolkit defined."
#endif
