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
#include "apr_hash.h"
#include "apr_strings.h"

#include "tcn.h"

#ifdef HAVE_OPENSSL
#include "ssl_private.h"

extern apr_hash_t  *tcn_private_keys;
extern apr_hash_t  *tcn_public_certs;


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
    *buf = '\0';
    if (c->bio_is) {
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
    else {
#ifdef WIN32
        #include <conio.h>
        int ch;
        BIO_printf(c->bio_os, "Enter password: ");
        do {
            ch = getch();
            if (ch == '\r')
                break;
            fputc('*', stdout);
            buf[rv++] = ch;
            if (rv + 1 > len)
                continue;
        } while (ch != '\n');
        buf[rv] = '\0';
        fputc('\n', stdout);
        fflush(stdout);
#endif

    }
    return rv;
}


/* ----BEGIN GENERATED SECTION-------- */

/*
** Diffie-Hellman-Parameters: (512 bit)
**     prime:
**         00:d4:bc:d5:24:06:f6:9b:35:99:4b:88:de:5d:b8:
**         96:82:c8:15:7f:62:d8:f3:36:33:ee:57:72:f1:1f:
**         05:ab:22:d6:b5:14:5b:9f:24:1e:5a:cc:31:ff:09:
**         0a:4b:c7:11:48:97:6f:76:79:50:94:e7:1e:79:03:
**         52:9f:5a:82:4b
**     generator: 2 (0x2)
** Diffie-Hellman-Parameters: (1024 bit)
**     prime:
**         00:e6:96:9d:3d:49:5b:e3:2c:7c:f1:80:c3:bd:d4:
**         79:8e:91:b7:81:82:51:bb:05:5e:2a:20:64:90:4a:
**         79:a7:70:fa:15:a2:59:cb:d5:23:a6:a6:ef:09:c4:
**         30:48:d5:a2:2f:97:1f:3c:20:12:9b:48:00:0e:6e:
**         dd:06:1c:bc:05:3e:37:1d:79:4e:53:27:df:61:1e:
**         bb:be:1b:ac:9b:5c:60:44:cf:02:3d:76:e0:5e:ea:
**         9b:ad:99:1b:13:a6:3c:97:4e:9e:f1:83:9e:b5:db:
**         12:51:36:f7:26:2e:56:a8:87:15:38:df:d8:23:c6:
**         50:50:85:e2:1f:0d:d5:c8:6b
**     generator: 2 (0x2)
*/

static unsigned char dh512_p[] =
{
    0xD4, 0xBC, 0xD5, 0x24, 0x06, 0xF6, 0x9B, 0x35, 0x99, 0x4B, 0x88, 0xDE,
    0x5D, 0xB8, 0x96, 0x82, 0xC8, 0x15, 0x7F, 0x62, 0xD8, 0xF3, 0x36, 0x33,
    0xEE, 0x57, 0x72, 0xF1, 0x1F, 0x05, 0xAB, 0x22, 0xD6, 0xB5, 0x14, 0x5B,
    0x9F, 0x24, 0x1E, 0x5A, 0xCC, 0x31, 0xFF, 0x09, 0x0A, 0x4B, 0xC7, 0x11,
    0x48, 0x97, 0x6F, 0x76, 0x79, 0x50, 0x94, 0xE7, 0x1E, 0x79, 0x03, 0x52,
    0x9F, 0x5A, 0x82, 0x4B,
};
static unsigned char dh512_g[] =
{
    0x02,
};

static DH *ssl_dh_configure(unsigned char *p, int plen,
                            unsigned char *g, int glen)
{
    DH *dh;

    if (!(dh = DH_new())) {
        return NULL;
    }

#if defined(OPENSSL_VERSION_NUMBER) || (SSLC_VERSION_NUMBER < 0x2000)
    dh->p = BN_bin2bn(p, plen, NULL);
    dh->g = BN_bin2bn(g, glen, NULL);
    if (!(dh->p && dh->g)) {
        DH_free(dh);
        return NULL;
    }
#else
    R_EITEMS_add(dh->data, PK_TYPE_DH, PK_DH_P, 0, p, plen, R_EITEMS_PF_COPY);
    R_EITEMS_add(dh->data, PK_TYPE_DH, PK_DH_G, 0, g, glen, R_EITEMS_PF_COPY);
#endif

    return dh;
}

static DH *get_dh512(void)
{
    return ssl_dh_configure(dh512_p, sizeof(dh512_p),
                            dh512_g, sizeof(dh512_g));
}

static unsigned char dh1024_p[] =
{
    0xE6, 0x96, 0x9D, 0x3D, 0x49, 0x5B, 0xE3, 0x2C, 0x7C, 0xF1, 0x80, 0xC3,
    0xBD, 0xD4, 0x79, 0x8E, 0x91, 0xB7, 0x81, 0x82, 0x51, 0xBB, 0x05, 0x5E,
    0x2A, 0x20, 0x64, 0x90, 0x4A, 0x79, 0xA7, 0x70, 0xFA, 0x15, 0xA2, 0x59,
    0xCB, 0xD5, 0x23, 0xA6, 0xA6, 0xEF, 0x09, 0xC4, 0x30, 0x48, 0xD5, 0xA2,
    0x2F, 0x97, 0x1F, 0x3C, 0x20, 0x12, 0x9B, 0x48, 0x00, 0x0E, 0x6E, 0xDD,
    0x06, 0x1C, 0xBC, 0x05, 0x3E, 0x37, 0x1D, 0x79, 0x4E, 0x53, 0x27, 0xDF,
    0x61, 0x1E, 0xBB, 0xBE, 0x1B, 0xAC, 0x9B, 0x5C, 0x60, 0x44, 0xCF, 0x02,
    0x3D, 0x76, 0xE0, 0x5E, 0xEA, 0x9B, 0xAD, 0x99, 0x1B, 0x13, 0xA6, 0x3C,
    0x97, 0x4E, 0x9E, 0xF1, 0x83, 0x9E, 0xB5, 0xDB, 0x12, 0x51, 0x36, 0xF7,
    0x26, 0x2E, 0x56, 0xA8, 0x87, 0x15, 0x38, 0xDF, 0xD8, 0x23, 0xC6, 0x50,
    0x50, 0x85, 0xE2, 0x1F, 0x0D, 0xD5, 0xC8, 0x6B,
};
static unsigned char dh1024_g[] =
{
    0x02,
};

static DH *get_dh1024(void)
{
    return ssl_dh_configure(dh1024_p, sizeof(dh1024_p),
                            dh1024_g, sizeof(dh1024_g));
}
/* ----END GENERATED SECTION---------- */

DH *SSL_dh_get_tmp_param(int nKeyLen)
{
    DH *dh;

    if (nKeyLen == 512)
        dh = get_dh512();
    else if (nKeyLen == 1024)
        dh = get_dh1024();
    else
        dh = get_dh1024();
    return dh;
}

DH *SSL_dh_get_param_from_file(const char *file)
{
    DH *dh = NULL;
    BIO *bio;

    if ((bio = BIO_new_file(file, "r")) == NULL)
        return NULL;
    dh = PEM_read_bio_DHparams(bio, NULL, NULL, NULL);
    BIO_free(bio);
    return dh;
}

/*
 * Handle out temporary RSA private keys on demand
 *
 * The background of this as the TLSv1 standard explains it:
 *
 * | D.1. Temporary RSA keys
 * |
 * |    US Export restrictions limit RSA keys used for encryption to 512
 * |    bits, but do not place any limit on lengths of RSA keys used for
 * |    signing operations. Certificates often need to be larger than 512
 * |    bits, since 512-bit RSA keys are not secure enough for high-value
 * |    transactions or for applications requiring long-term security. Some
 * |    certificates are also designated signing-only, in which case they
 * |    cannot be used for key exchange.
 * |
 * |    When the public key in the certificate cannot be used for encryption,
 * |    the server signs a temporary RSA key, which is then exchanged. In
 * |    exportable applications, the temporary RSA key should be the maximum
 * |    allowable length (i.e., 512 bits). Because 512-bit RSA keys are
 * |    relatively insecure, they should be changed often. For typical
 * |    electronic commerce applications, it is suggested that keys be
 * |    changed daily or every 500 transactions, and more often if possible.
 * |    Note that while it is acceptable to use the same temporary key for
 * |    multiple transactions, it must be signed each time it is used.
 * |
 * |    RSA key generation is a time-consuming process. In many cases, a
 * |    low-priority process can be assigned the task of key generation.
 * |    Whenever a new key is completed, the existing temporary key can be
 * |    replaced with the new one.
 *
 * XXX: base on comment above, if thread support is enabled,
 * we should spawn a low-priority thread to generate new keys
 * on the fly.
 *
 * So we generated 512 and 1024 bit temporary keys on startup
 * which we now just hand out on demand....
 */

RSA *SSL_callback_tmp_RSA(SSL *ssl, int export, int keylen)
{
    tcn_ssl_conn_t *conn = (tcn_ssl_conn_t *)SSL_get_app_data(ssl);
    int idx;

    /* doesn't matter if export flag is on,
     * we won't be asked for keylen > 512 in that case.
     * if we are asked for a keylen > 1024, it is too expensive
     * to generate on the fly.
     * XXX: any reason not to generate 2048 bit keys at startup?
     */

    switch (keylen) {
        case 512:
            idx = SSL_TMP_KEY_RSA_512;
        break;
        case 1024:
        default:
            idx = SSL_TMP_KEY_RSA_1024;
        break;
    }
    return (RSA *)conn->ctx->temp_keys[idx];
}

/*
 * Hand out the already generated DH parameters...
 */
DH *SSL_callback_tmp_DH(SSL *ssl, int export, int keylen)
{
    tcn_ssl_conn_t *conn = (tcn_ssl_conn_t *)SSL_get_app_data(ssl);
    int idx;
    switch (keylen) {
        case 512:
            idx = SSL_TMP_KEY_DH_512;
        break;
        case 1024:
        default:
            idx = SSL_TMP_KEY_DH_1024;
        break;
    }
    return (DH *)conn->ctx->temp_keys[idx];
}

void SSL_vhost_algo_id(const unsigned char *vhost_id, unsigned char *md, int algo)
{
    MD5_CTX c;
    MD5_Init(&c);
    MD5_Update(&c, vhost_id, MD5_DIGEST_LENGTH);
    switch (algo) {
        case SSL_ALGO_UNKNOWN:
            MD5_Update(&c, "UNKNOWN", 7);
        break;
        case SSL_ALGO_RSA:
            MD5_Update(&c, "RSA", 3);
        break;
        case SSL_ALGO_DSA:
            MD5_Update(&c, "DSA", 3);
        break;
    }
    MD5_Final(md, &c);
}

/*
 * Read a file that optionally contains the server certificate in PEM
 * format, possibly followed by a sequence of CA certificates that
 * should be sent to the peer in the SSL Certificate message.
 */
int SSL_CTX_use_certificate_chain(SSL_CTX *ctx, char *file,
                                  int skipfirst)
{
    BIO *bio;
    X509 *x509;
    unsigned long err;
    int n;
    STACK *extra_certs;

    if ((bio = BIO_new(BIO_s_file_internal())) == NULL)
        return -1;
    if (BIO_read_filename(bio, file) <= 0) {
        BIO_free(bio);
        return -1;
    }
    /* optionally skip a leading server certificate */
    if (skipfirst) {
        if ((x509 = PEM_read_bio_X509(bio, NULL, NULL, NULL)) == NULL) {
            BIO_free(bio);
            return -1;
        }
        X509_free(x509);
    }
    /* free a perhaps already configured extra chain */
    extra_certs=SSL_CTX_get_extra_certs(ctx);
    if (extra_certs != NULL) {
        sk_X509_pop_free((STACK_OF(X509) *)extra_certs, X509_free);
        SSL_CTX_set_extra_certs(ctx,NULL);
    }
    /* create new extra chain by loading the certs */
    n = 0;
    while ((x509 = PEM_read_bio_X509(bio, NULL, NULL, NULL)) != NULL) {
        if (!SSL_CTX_add_extra_chain_cert(ctx, x509)) {
            X509_free(x509);
            BIO_free(bio);
            return -1;
        }
        n++;
    }
    /* Make sure that only the error is just an EOF */
    if ((err = ERR_peek_error()) > 0) {
        if (!(   ERR_GET_LIB(err) == ERR_LIB_PEM
              && ERR_GET_REASON(err) == PEM_R_NO_START_LINE)) {
            BIO_free(bio);
            return -1;
        }
        while (ERR_get_error() > 0) ;
    }
    BIO_free(bio);
    return n;
}

static int ssl_init_X509NameCmp(X509_NAME **a, X509_NAME **b)
{
    return (X509_NAME_cmp(*a, *b));
}

static void ssl_init_pushCAList(STACK_OF(X509_NAME) *ca_list,
                                const char *file)
{
    int n;
    STACK_OF(X509_NAME) *sk;

    sk = (STACK_OF(X509_NAME) *)
             SSL_load_client_CA_file((char *)file);

    if (!sk) {
        return;
    }

    for (n = 0; n < sk_X509_NAME_num(sk); n++) {
        X509_NAME *name = sk_X509_NAME_value(sk, n);
        /*
         * note that SSL_load_client_CA_file() checks for duplicates,
         * but since we call it multiple times when reading a directory
         * we must also check for duplicates ourselves.
         */

        if (sk_X509_NAME_find(ca_list, name) < 0) {
            /* this will be freed when ca_list is */
            sk_X509_NAME_push(ca_list, name);
        }
        else {
            /* need to free this ourselves, else it will leak */
            X509_NAME_free(name);
        }
    }
    sk_X509_NAME_free(sk);
}

STACK_OF(X509_NAME) *SSL_init_findCAList(tcn_ssl_ctxt_t *c,
                                         const char *ca_file,
                                         const char *ca_path)
{
    STACK_OF(X509_NAME) *ca_list;

    /*
     * Start with a empty stack/list where new
     * entries get added in sorted order.
     */
    ca_list = sk_X509_NAME_new(ssl_init_X509NameCmp);

    /*
     * Process CA certificate bundle file
     */
    if (ca_file)
        ssl_init_pushCAList(ca_list, ca_file);

    /*
     * Process CA certificate path files
     */
    if (ca_path) {
        apr_dir_t *dir;
        apr_finfo_t direntry;
        apr_int32_t finfo_flags = APR_FINFO_TYPE|APR_FINFO_NAME;
        apr_status_t rv;
        apr_pool_t *ptemp;

        if ((apr_pool_create(&ptemp, c->pool)) != APR_SUCCESS)
            return NULL;
        if ((rv = apr_dir_open(&dir, ca_path, c->pool)) != APR_SUCCESS) {
            BIO_printf(c->bio_os, "[ERROR] "
                       "Failed to open Certificate Path `%s'",
                       ca_path);
            apr_pool_destroy(ptemp);
            return NULL;
        }

        while ((apr_dir_read(&direntry, finfo_flags, dir)) == APR_SUCCESS) {
            const char *file;
            if (direntry.filetype == APR_DIR)
                continue; /* don't try to load directories */
            file = apr_pstrcat(ptemp, ca_path, "/", direntry.name, NULL);
            ssl_init_pushCAList(ca_list, file);
        }

        apr_dir_close(dir);
        apr_pool_destroy(ptemp);
    }

    /*
     * Cleanup
     */
    sk_X509_NAME_set_cmp_func(ca_list, NULL);
    return ca_list;
}


/*
 * This OpenSSL callback function is called when OpenSSL
 * does client authentication and verifies the certificate chain.
 */
int SSL_callback_SSL_verify(int ok, X509_STORE_CTX *ctx)
{

    /* TODO: Dummy function for now */
    return 1;
}

#else
/* OpenSSL is not supported
 * If someday we make OpenSSL optional
 * APR_ENOTIMPL will go here
 */
#error "No OpenSSL Toolkit defined."
#endif
