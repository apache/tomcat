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

/*
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

#ifndef SSL_PRIVATE_H
#define SSL_PRIVATE_H

/* OpenSSL headers */
#include <openssl/ssl.h>
#include <openssl/err.h>
#include <openssl/x509.h>
#include <openssl/pem.h>
#include <openssl/crypto.h>
#include <openssl/evp.h>
#include <openssl/rand.h>
#include <openssl/x509v3.h>
/* Avoid tripping over an engine build installed globally and detected
 * when the user points at an explicit non-engine flavor of OpenSSL
 */
#ifndef OPENSSL_NO_ENGINE
#include <openssl/engine.h>
#endif

#ifndef RAND_MAX
#include <limits.h>
#define RAND_MAX INT_MAX
#endif

#define SSL_ALGO_UNKNOWN (0)
#define SSL_ALGO_RSA     (1<<0)
#define SSL_ALGO_DSA     (1<<1)
#define SSL_ALGO_ALL     (SSL_ALGO_RSA|SSL_ALGO_DSA)

#define SSL_AIDX_RSA     (0)
#define SSL_AIDX_DSA     (1)
#define SSL_AIDX_MAX     (2)

/*
 * Define IDs for the temporary RSA keys and DH params
 */

#define SSL_TMP_KEY_RSA_512  (0)
#define SSL_TMP_KEY_RSA_1024 (1)
#define SSL_TMP_KEY_DH_512   (2)
#define SSL_TMP_KEY_DH_1024  (3)
#define SSL_TMP_KEY_MAX      (4)

/*
 * Define the SSL options
 */
#define SSL_OPT_NONE           (0)
#define SSL_OPT_RELSET         (1<<0)
#define SSL_OPT_STDENVVARS     (1<<1)
#define SSL_OPT_EXPORTCERTDATA (1<<3)
#define SSL_OPT_FAKEBASICAUTH  (1<<4)
#define SSL_OPT_STRICTREQUIRE  (1<<5)
#define SSL_OPT_OPTRENEGOTIATE (1<<6)
#define SSL_OPT_ALL            (SSL_OPT_STDENVVARS|SSL_OPT_EXPORTCERTDATA|SSL_OPT_FAKEBASICAUTH|SSL_OPT_STRICTREQUIRE|SSL_OPT_OPTRENEGOTIATE)

/*
 * Define the SSL Protocol options
 */
#define SSL_PROTOCOL_NONE  (0)
#define SSL_PROTOCOL_SSLV2 (1<<0)
#define SSL_PROTOCOL_SSLV3 (1<<1)
#define SSL_PROTOCOL_TLSV1 (1<<2)
#define SSL_PROTOCOL_ALL   (SSL_PROTOCOL_SSLV2|SSL_PROTOCOL_SSLV3|SSL_PROTOCOL_TLSV1)

#define SSL_BIO_FLAG_RDONLY     (1<<0)
#define SSL_BIO_FLAG_CALLBACK   (1<<1)
#define SSL_DEFAULT_CACHE_SIZE  (256)
#define SSL_DEFAULT_VHOST_NAME  ("_default_:443")
#define SSL_MAX_STR_LEN         2048

#define SSL_CVERIFY_UNSET           (-1)
#define SSL_CVERIFY_NONE            (0)
#define SSL_CVERIFY_OPTIONAL        (1)
#define SSL_CVERIFY_REQUIRE         (2)
#define SSL_CVERIFY_OPTIONAL_NO_CA  (3)
#define SSL_VERIFY_PEER_STRICT      (SSL_VERIFY_PEER|SSL_VERIFY_FAIL_IF_NO_PEER_CERT)

/* public cert/private key */
typedef struct {
    /*
     * server only has 1-2 certs/keys
     * 1 RSA and/or 1 DSA
     */
    const char  *cert_files[SSL_AIDX_MAX];
    const char  *key_files[SSL_AIDX_MAX];
    X509        *certs[SSL_AIDX_MAX];
    EVP_PKEY    *keys[SSL_AIDX_MAX];

    /* Certificates which specify the set of CA names which should be
     * sent in the CertificateRequest message: */
    const char  *ca_name_path;
    const char  *ca_name_file;
} ssl_pks_t;

typedef struct {
    /* client can have any number of cert/key pairs */
    const char  *cert_file;
    const char  *cert_path;
    STACK_OF(X509_INFO) *certs;
} ssl_pkc_t;

struct tcn_ssl_ctxt {
    apr_pool_t      *pool;
    SSL_CTX         *ctx;
    BIO             *bio_os;
    BIO             *bio_is;
    unsigned char   vhost_id[MD5_DIGEST_LENGTH];

    int             protocol;
    /* we are one or the other */
    int             mode;
    union {
        ssl_pks_t   s;
        ssl_pkc_t   c;
    } pk;

    const char      *cert_chain;
    /* certificate revocation list */
    X509_STORE      *crl;

    /* known/trusted CAs */
    const char      *ca_cert_path;
    const char      *ca_cert_file;
    const char      *cipher_suite;
    /* for client or downstream server authentication */
    int             verify_depth;
    int             verify_mode;
    void            *temp_keys[SSL_TMP_KEY_MAX];
};

typedef struct tcn_ssl_ctxt tcn_ssl_ctxt_t;

struct tcn_ssl_conn {
    tcn_ssl_ctxt_t *ctx;
    SSL            *ssl;
};

typedef struct tcn_ssl_conn tcn_ssl_conn_t;

#define SSL_CTX_get_extra_certs(ctx)       (ctx->extra_certs)
#define SSL_CTX_set_extra_certs(ctx,value) {ctx->extra_certs = value;}

/*
 *  Additional Functions
 */
void        SSL_init_app_data2_idx(void);
void       *SSL_get_app_data2(SSL *);
void        SSL_set_app_data2(SSL *, void *);
int         SSL_password_prompt(tcn_ssl_ctxt_t *, char *, size_t);
void        SSL_BIO_close(BIO *);
void        SSL_BIO_doref(BIO *);
DH         *SSL_dh_get_tmp_param(int);
DH         *SSL_dh_get_param_from_file(const char *);
RSA        *SSL_callback_tmp_RSA(SSL *, int, int);
DH         *SSL_callback_tmp_DH(SSL *, int, int);
void        SSL_vhost_algo_id(const unsigned char *, unsigned char *, int);
int         SSL_callback_SSL_verify(int, X509_STORE_CTX *);
STACK_OF(X509_NAME)
            *SSL_init_findCAList(tcn_ssl_ctxt_t *, const char *, const char *);

#endif /* SSL_PRIVATE_H */
