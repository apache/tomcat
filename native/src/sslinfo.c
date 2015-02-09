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

/** SSL info wrapper
 *
 * @author Mladen Turk
 * @version $Id$
 */

#include "tcn.h"
#include "apr_file_io.h"
#include "apr_thread_mutex.h"
#include "apr_poll.h"

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

#define DIGIT2NUM(x) (((x)[0] - '0') * 10 + (x)[1] - '0')

static int get_days_remaining(ASN1_UTCTIME *tm)
{
    apr_time_t then, now = apr_time_now();
    apr_time_exp_t exp = {0};
    int diff;

    /* Fail if the time isn't a valid ASN.1 UTCTIME; RFC3280 mandates
     * that the seconds digits are present even though ASN.1
     * doesn't. */
    if (tm->length < 11 || !ASN1_UTCTIME_check(tm))
        return 0;

    exp.tm_year = DIGIT2NUM(tm->data);
    exp.tm_mon  = DIGIT2NUM(tm->data + 2) - 1;
    exp.tm_mday = DIGIT2NUM(tm->data + 4) + 1;
    exp.tm_hour = DIGIT2NUM(tm->data + 6);
    exp.tm_min  = DIGIT2NUM(tm->data + 8);
    exp.tm_sec  = DIGIT2NUM(tm->data + 10);

    if (exp.tm_year <= 50)
        exp.tm_year += 100;
    if (apr_time_exp_gmt_get(&then, &exp) != APR_SUCCESS)
        return 0;

    diff = (int)((apr_time_sec(then) - apr_time_sec(now)) / (60*60*24));
    return diff > 0 ? diff : 0;
}

static char *get_cert_valid(ASN1_UTCTIME *tm)
{
    char *result;
    BIO* bio;
    int n;

    if ((bio = BIO_new(BIO_s_mem())) == NULL)
        return NULL;
    ASN1_UTCTIME_print(bio, tm);
    n = BIO_pending(bio);
    result = malloc(n+1);
    n = BIO_read(bio, result, n);
    result[n] = '\0';
    BIO_free(bio);
    return result;
}

static char *get_cert_PEM(X509 *xs)
{
    char *result = NULL;
    BIO *bio;

    if ((bio = BIO_new(BIO_s_mem())) == NULL)
        return NULL;
    if (PEM_write_bio_X509(bio, xs)) {
        int n = BIO_pending(bio);
        result = malloc(n+1);
        n = BIO_read(bio, result, n);
        result[n] = '\0';
    }
    BIO_free(bio);
    return result;
}

static unsigned char *get_cert_ASN1(X509 *xs, int *len)
{
    unsigned char *result = NULL;
    BIO *bio;

    *len = 0;
    if ((bio = BIO_new(BIO_s_mem())) == NULL)
        return NULL;
    if (i2d_X509_bio(bio, xs)) {
        int n = BIO_pending(bio);
        result = malloc(n);
        n = BIO_read(bio, result, n);
        *len = n;
    }
    BIO_free(bio);
    return result;
}


static char *get_cert_serial(X509 *xs)
{
    char *result;
    BIO *bio;
    int n;

    if ((bio = BIO_new(BIO_s_mem())) == NULL)
        return NULL;
    i2a_ASN1_INTEGER(bio, X509_get_serialNumber(xs));
    n = BIO_pending(bio);
    result = malloc(n+1);
    n = BIO_read(bio, result, n);
    result[n] = '\0';
    BIO_free(bio);
    return result;
}

static const struct {
    int   fid;
    int   nid;
} info_cert_dn_rec[] = {
    { SSL_INFO_DN_COUNTRYNAME,            NID_countryName            },
    { SSL_INFO_DN_STATEORPROVINCENAME,    NID_stateOrProvinceName    },
    { SSL_INFO_DN_LOCALITYNAME,           NID_localityName           },
    { SSL_INFO_DN_ORGANIZATIONNAME,       NID_organizationName       },
    { SSL_INFO_DN_ORGANIZATIONALUNITNAME, NID_organizationalUnitName },
    { SSL_INFO_DN_COMMONNAME,             NID_commonName             },
    { SSL_INFO_DN_TITLE,                  NID_title                  },
    { SSL_INFO_DN_INITIALS,               NID_initials               },
    { SSL_INFO_DN_GIVENNAME,              NID_givenName              },
    { SSL_INFO_DN_SURNAME,                NID_surname                },
    { SSL_INFO_DN_DESCRIPTION,            NID_description            },
    { SSL_INFO_DN_UNIQUEIDENTIFIER,       NID_x500UniqueIdentifier   },
    { SSL_INFO_DN_EMAILADDRESS,           NID_pkcs9_emailAddress     },
    { 0,                                  0                          }
};

static char *lookup_ssl_cert_dn(X509_NAME *xsname, int dnidx)
{
    char *result;
    X509_NAME_ENTRY *xsne;
    int i, j, n, idx = 0;

    result = NULL;

    for (i = 0; info_cert_dn_rec[i].fid != 0; i++) {
        if (info_cert_dn_rec[i].fid == dnidx) {
            for (j = 0; j < sk_X509_NAME_ENTRY_num((STACK_OF(X509_NAME_ENTRY) *)
                                                   (xsname->entries)); j++) {
                xsne = sk_X509_NAME_ENTRY_value((STACK_OF(X509_NAME_ENTRY) *)
                                                (xsname->entries), j);

                n =OBJ_obj2nid((ASN1_OBJECT *)X509_NAME_ENTRY_get_object(xsne));
                if (n == info_cert_dn_rec[i].nid && idx-- == 0) {
                    result = malloc(xsne->value->length + 1);
                    memcpy(result, xsne->value->data,
                                   xsne->value->length);
                    result[xsne->value->length] = '\0';

#if APR_CHARSET_EBCDIC
                    ap_xlate_proto_from_ascii(result, xsne->value->length);
#endif /* APR_CHARSET_EBCDIC */
                    break;
                }
            }
            break;
        }
    }
    return result;
}

TCN_IMPLEMENT_CALL(jobject, SSLSocket, getInfoB)(TCN_STDARGS, jlong sock,
                                                 jint what)
{
    tcn_socket_t   *a = J2P(sock, tcn_socket_t *);
    tcn_ssl_conn_t *s;
    jbyteArray array = NULL;
    apr_status_t rv = APR_SUCCESS;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);

    s = (tcn_ssl_conn_t *)(a->opaque);
    switch (what) {
        case SSL_INFO_SESSION_ID:
        {
            SSL_SESSION *session  = SSL_get_session(s->ssl);
            if (session) {
                unsigned int len;
                const unsigned char *id = SSL_SESSION_get_id(session, &len);
                array = tcn_new_arrayb(e, id, len);
            }
        }
        break;
        default:
            rv = APR_EINVAL;
        break;
    }
    if (what & SSL_INFO_CLIENT_MASK) {
        X509 *xs;
        unsigned char *result;
        int len;
        if ((xs = SSL_get_peer_certificate(s->ssl)) != NULL) {
            switch (what) {
                case SSL_INFO_CLIENT_CERT:
                    if ((result = get_cert_ASN1(xs, &len))) {
                        array = tcn_new_arrayb(e, result, len);
                        free(result);
                    }
                break;
            }
            X509_free(xs);
        }
        rv = APR_SUCCESS;
    }
    else if (what & SSL_INFO_SERVER_MASK) {
        X509 *xs;
        unsigned char *result;
        int len;
        if ((xs = SSL_get_certificate(s->ssl)) != NULL) {
            switch (what) {
                case SSL_INFO_SERVER_CERT:
                    if ((result = get_cert_ASN1(xs, &len))) {
                        array = tcn_new_arrayb(e, result, len);
                        free(result);
                    }
                break;
            }
            /* XXX: No need to call the X509_free(xs); */
        }
        rv = APR_SUCCESS;
    }
    else if (what & SSL_INFO_CLIENT_CERT_CHAIN) {
        X509 *xs;
        unsigned char *result;
        STACK_OF(X509) *sk =  SSL_get_peer_cert_chain(s->ssl);
        int len, n = what & 0x0F;
        if (n < sk_X509_num(sk)) {
            xs = sk_X509_value(sk, n);
            if ((result = get_cert_ASN1(xs, &len))) {
                array = tcn_new_arrayb(e, result, len);
                free(result);
            }
        }
        rv = APR_SUCCESS;
    }
    if (rv != APR_SUCCESS)
        tcn_ThrowAPRException(e, rv);

    return array;
}

TCN_IMPLEMENT_CALL(jstring, SSLSocket, getInfoS)(TCN_STDARGS, jlong sock,
                                                 jint what)
{
    tcn_socket_t   *a = J2P(sock, tcn_socket_t *);
    tcn_ssl_conn_t *s;
    jstring value = NULL;
    apr_status_t rv = APR_SUCCESS;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);

    s = (tcn_ssl_conn_t *)(a->opaque);
    switch (what) {
        case SSL_INFO_SESSION_ID:
        {
            SSL_SESSION *session  = SSL_get_session(s->ssl);
            if (session) {
                unsigned int len;
                const unsigned char *id = SSL_SESSION_get_id(session, &len);
                char *hs = convert_to_hex(id, len);
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
                SSL_CIPHER *cipher = (SSL_CIPHER *)SSL_get_current_cipher(s->ssl);
                if (cipher) {
                    char buf[256];
                    const char *desc = SSL_CIPHER_description(cipher, buf, 256);
                    value = tcn_new_string(e, desc);
                }
            }
        break;
        default:
            rv = APR_EINVAL;
        break;
    }
    if (what & (SSL_INFO_CLIENT_S_DN | SSL_INFO_CLIENT_I_DN)) {
        X509 *xs;
        X509_NAME *xsname;
        if ((xs = SSL_get_peer_certificate(s->ssl)) != NULL) {
            char *result;
            int idx = what & 0x0F;
            if (what & SSL_INFO_CLIENT_S_DN)
                xsname = X509_get_subject_name(xs);
            else
                xsname = X509_get_issuer_name(xs);
            if (idx) {
                result = lookup_ssl_cert_dn(xsname, idx);
                if (result) {
                    value = tcn_new_string(e, result);
                    free(result);
                }
            }
            else
                value = tcn_new_string(e, X509_NAME_oneline(xsname, NULL, 0));
            X509_free(xs);
        }
        rv = APR_SUCCESS;
    }
    else if (what & (SSL_INFO_SERVER_S_DN | SSL_INFO_SERVER_I_DN)) {
        X509 *xs;
        X509_NAME *xsname;
        if ((xs = SSL_get_certificate(s->ssl)) != NULL) {
            char *result;
            int idx = what & 0x0F;
            if (what & SSL_INFO_SERVER_S_DN)
                xsname = X509_get_subject_name(xs);
            else
                xsname = X509_get_issuer_name(xs);
            if (idx) {
                result = lookup_ssl_cert_dn(xsname, what & 0x0F);
                if (result) {
                    value = tcn_new_string(e, result);
                    free(result);
                }
            }
            else
                value = tcn_new_string(e, X509_NAME_oneline(xsname, NULL, 0));
            /* XXX: No need to call the X509_free(xs); */
        }
        rv = APR_SUCCESS;
    }
    else if (what & SSL_INFO_CLIENT_MASK) {
        X509 *xs;
        char *result;
        int nid;
        if ((xs = SSL_get_peer_certificate(s->ssl)) != NULL) {
            switch (what) {
                case SSL_INFO_CLIENT_V_START:
                    if ((result = get_cert_valid(X509_get_notBefore(xs)))) {
                        value = tcn_new_string(e, result);
                        free(result);
                    }
                break;
                case SSL_INFO_CLIENT_V_END:
                    if ((result = get_cert_valid(X509_get_notAfter(xs)))) {
                        value = tcn_new_string(e, result);
                        free(result);
                    }
                break;
                case SSL_INFO_CLIENT_A_SIG:
                    nid = OBJ_obj2nid((ASN1_OBJECT *)xs->cert_info->signature->algorithm);
                    if (nid == NID_undef)
                        value = tcn_new_string(e, "UNKNOWN");
                    else
                        value = tcn_new_string(e, OBJ_nid2ln(nid));
                break;
                case SSL_INFO_CLIENT_A_KEY:
                    nid = OBJ_obj2nid((ASN1_OBJECT *)xs->cert_info->key->algor->algorithm);
                    if (nid == NID_undef)
                        value = tcn_new_string(e, "UNKNOWN");
                    else
                        value = tcn_new_string(e, OBJ_nid2ln(nid));
                break;
                case SSL_INFO_CLIENT_CERT:
                    if ((result = get_cert_PEM(xs))) {
                        value = tcn_new_string(e, result);
                        free(result);
                    }
                break;
                case SSL_INFO_CLIENT_M_SERIAL:
                    if ((result = get_cert_serial(xs))) {
                        value = tcn_new_string(e, result);
                        free(result);
                    }
                break;
            }
            X509_free(xs);
        }
        rv = APR_SUCCESS;
    }
    else if (what & SSL_INFO_SERVER_MASK) {
        X509 *xs;
        char *result;
        int nid;
        if ((xs = SSL_get_certificate(s->ssl)) != NULL) {
            switch (what) {
                case SSL_INFO_SERVER_V_START:
                    if ((result = get_cert_valid(X509_get_notBefore(xs)))) {
                        value = tcn_new_string(e, result);
                        free(result);
                    }
                break;
                case SSL_INFO_SERVER_V_END:
                    if ((result = get_cert_valid(X509_get_notAfter(xs)))) {
                        value = tcn_new_string(e, result);
                        free(result);
                    }
                break;
                case SSL_INFO_SERVER_A_SIG:
                    nid = OBJ_obj2nid((ASN1_OBJECT *)xs->cert_info->signature->algorithm);
                    if (nid == NID_undef)
                        value = tcn_new_string(e, "UNKNOWN");
                    else
                        value = tcn_new_string(e, OBJ_nid2ln(nid));
                break;
                case SSL_INFO_SERVER_A_KEY:
                    nid = OBJ_obj2nid((ASN1_OBJECT *)xs->cert_info->key->algor->algorithm);
                    if (nid == NID_undef)
                        value = tcn_new_string(e, "UNKNOWN");
                    else
                        value = tcn_new_string(e, OBJ_nid2ln(nid));
                break;
                case SSL_INFO_SERVER_CERT:
                    if ((result = get_cert_PEM(xs))) {
                        value = tcn_new_string(e, result);
                        free(result);
                    }
                break;
                case SSL_INFO_SERVER_M_SERIAL:
                    if ((result = get_cert_serial(xs))) {
                        value = tcn_new_string(e, result);
                        free(result);
                    }
                break;
            }
            /* XXX: No need to call the X509_free(xs); */
        }
        rv = APR_SUCCESS;
    }
    else if (what & SSL_INFO_CLIENT_CERT_CHAIN) {
        X509 *xs;
        char *result;
        STACK_OF(X509) *sk =  SSL_get_peer_cert_chain(s->ssl);
        int n = what & 0x0F;
        if (n < sk_X509_num(sk)) {
            xs = sk_X509_value(sk, n);
            if ((result = get_cert_PEM(xs))) {
                value = tcn_new_string(e, result);
                free(result);
            }
        }
        rv = APR_SUCCESS;
    }
    if (rv != APR_SUCCESS)
        tcn_ThrowAPRException(e, rv);

    return value;
}

TCN_IMPLEMENT_CALL(jint, SSLSocket, getInfoI)(TCN_STDARGS, jlong sock,
                                              jint what)
{
    tcn_socket_t   *a = J2P(sock, tcn_socket_t *);
    tcn_ssl_conn_t *s;
    apr_status_t rv = APR_SUCCESS;
    jint value = -1;

    UNREFERENCED(o);
    TCN_ASSERT(sock != 0);

    s = (tcn_ssl_conn_t *)(a->opaque);

    switch (what) {
        case SSL_INFO_CIPHER_USEKEYSIZE:
        case SSL_INFO_CIPHER_ALGKEYSIZE:
        {
            int usekeysize = 0;
            int algkeysize = 0;
            const SSL_CIPHER *cipher = SSL_get_current_cipher(s->ssl);
            if (cipher) {
                usekeysize = SSL_CIPHER_get_bits(cipher, &algkeysize);
                if (what == SSL_INFO_CIPHER_USEKEYSIZE)
                    value = usekeysize;
                else
                    value = algkeysize;
            }
        }
        break;
        case SSL_INFO_CLIENT_CERT_CHAIN:
        {
            STACK_OF(X509) *sk =  SSL_get_peer_cert_chain(s->ssl);
            value = sk_X509_num(sk);
        }
        break;
        default:
            rv = APR_EINVAL;
        break;
    }
    if (what & SSL_INFO_CLIENT_MASK) {
        X509 *xs;
        if ((xs = SSL_get_peer_certificate(s->ssl)) != NULL) {
            switch (what) {
                case SSL_INFO_CLIENT_V_REMAIN:
                    value = get_days_remaining(X509_get_notAfter(xs));
                    rv = APR_SUCCESS;
                break;
                default:
                    rv = APR_EINVAL;
                break;
           }
           X509_free(xs);
        }
    }

    if (rv != APR_SUCCESS)
        tcn_ThrowAPRException(e, rv);
    return value;
}

#else
/* OpenSSL is not supported.
 * Create empty stubs.
 */

TCN_IMPLEMENT_CALL(jobject, SSLSocket, getInfoB)(TCN_STDARGS, jlong sock,
                                                 jint what)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(sock);
    UNREFERENCED(what);
    return NULL;
}

TCN_IMPLEMENT_CALL(jstring, SSLSocket, getInfoS)(TCN_STDARGS, jlong sock,
                                                 jint what)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(sock);
    UNREFERENCED(what);
    return NULL;
}

TCN_IMPLEMENT_CALL(jint, SSLSocket, getInfoI)(TCN_STDARGS, jlong sock,
                                              jint what)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(sock);
    UNREFERENCED(what);
    return 0;
}

#endif
