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

static const char *hex_basis = "0123456789ABCDEF";

static char *convert_to_hex(const void *buf, size_t len)
{
    const unsigned char *p = ( const unsigned char *)buf;
    char *str, *s;
    size_t i;

    if ((len < 1) || ((str = malloc(len * 2 + 1)) == NULL))
        return NULL;
    for (i = 0, s = str; i < len; i++) {
        unsigned char c = *p++;
        *s++ = hex_basis[c >> 4];
        *s++ = hex_basis[c & 0x0F];
    }
    *s = '\0';
    return str;
}

TCN_IMPLEMENT_CALL(jobject, SSLSocket, getInfoB)(TCN_STDARGS, jlong sock,
                                                 jint what)
{
    tcn_ssl_conn_t *s = J2P(sock, tcn_ssl_conn_t *);
    jbyteArray array = NULL;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);

    switch (what) {
        case SSL_INFO_SESSION_ID:
        {
            SSL_SESSION *session  = SSL_get_session(s->ssl);
            if (session) {
                array = tcn_new_arrayb(e, &session->session_id[0],
                                       session->session_id_length); 
            }
        }
        break;
        default:
            tcn_ThrowAPRException(e, APR_EINVAL);
        break;
    }

    return array;
}

TCN_IMPLEMENT_CALL(jstring, SSLSocket, getInfoS)(TCN_STDARGS, jlong sock,
                                                 jint what)
{
    tcn_ssl_conn_t *s = J2P(sock, tcn_ssl_conn_t *);
    jstring value = NULL;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);

    switch (what) {
        case SSL_INFO_SESSION_ID:
        {
            SSL_SESSION *session  = SSL_get_session(s->ssl);
            if (session) {
                char *hs = convert_to_hex(&session->session_id[0],
                                          session->session_id_length);
                if (hs) {
                    value = tcn_new_string(e, hs);
                    free(hs);
                }
            }
        }
        break;
        case SSL_INFO_PROTOCOL:
            value = tcn_new_string(e, SSL_get_version(s->ssl));
        break;
        case SSL_INFO_CIPHER:
            value = tcn_new_string(e, SSL_get_cipher_name(s->ssl));
        break;
        case SSL_INFO_CIPHER_VERSION:
            value = tcn_new_string(e, SSL_get_cipher_version(s->ssl));
        break;
        case SSL_INFO_CIPHER_DESCRIPTION:
            {
                SSL_CIPHER *cipher = SSL_get_current_cipher(s->ssl);
                if (cipher) {
                    char buf[256];
                    char *desc = SSL_CIPHER_description(cipher, buf, 256);
                    value = tcn_new_string(e, desc);
                }
            }
        break;
        default:
            tcn_ThrowAPRException(e, APR_EINVAL);
        break;
    }

    return value;
}

TCN_IMPLEMENT_CALL(jint, SSLSocket, getInfoI)(TCN_STDARGS, jlong sock,
                                              jint what)
{
    tcn_ssl_conn_t *s = J2P(sock, tcn_ssl_conn_t *);
    jint value = -1;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);

    switch (what) {
        case SSL_INFO_CIPHER_USEKEYSIZE:
        case SSL_INFO_CIPHER_ALGKEYSIZE:
        {
            int usekeysize = 0;
            int algkeysize = 0;
            SSL_CIPHER *cipher = SSL_get_current_cipher(s->ssl);
            if (cipher) {
                usekeysize = SSL_CIPHER_get_bits(cipher, &algkeysize);
                if (what == SSL_INFO_CIPHER_USEKEYSIZE)
                    value = usekeysize;
                else
                    value = algkeysize;
            }
        }
        break;
        default:
            tcn_ThrowAPRException(e, APR_EINVAL);
        break;
    }

    return value;
}

#else
/* OpenSSL is not supported
 * If someday we make OpenSSL optional
 * APR_ENOTIMPL will go here
 */
#error "No OpenSSL Toolkit defined."
#endif
