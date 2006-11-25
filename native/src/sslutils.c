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

/** SSL Utilities
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

#include "tcn.h"
#include "apr_thread_mutex.h"
#include "apr_poll.h"

#ifdef HAVE_OPENSSL
#include "ssl_private.h"

#ifdef WIN32
extern int WIN32_SSL_password_prompt(tcn_pass_cb_t *data);
#endif

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

/* Simple echo password prompting */
int SSL_password_prompt(tcn_pass_cb_t *data)
{
    int rv = 0;
    data->password[0] = '\0';
    if (data->cb.obj) {
        JNIEnv *e;
        jobject  o;
        jstring  prompt;
        tcn_get_java_env(&e);
        prompt = AJP_TO_JSTRING(data->prompt);
        if ((o = (*e)->CallObjectMethod(e, data->cb.obj,
                            data->cb.mid[0], prompt))) {
            TCN_ALLOC_CSTRING(o);
            if (J2S(o)) {
                strncpy(data->password, J2S(o), SSL_MAX_PASSWORD_LEN);
                data->password[SSL_MAX_PASSWORD_LEN-1] = '\0';
                rv = (int)strlen(data->password);
            }
            TCN_FREE_CSTRING(o);
        }
    }
    else {
#ifdef WIN32
        rv = WIN32_SSL_password_prompt(data);
#else
        EVP_read_pw_string(data->password, SSL_MAX_PASSWORD_LEN,
                           data->prompt, 0);
#endif
        rv = (int)strlen(data->password);
    }
    if (rv > 0) {
        /* Remove LF char if present */
        char *r = strchr(data->password, '\n');
        if (r) {
            *r = '\0';
            rv--;
        }
#ifdef WIN32
        if ((r = strchr(data->password, '\r'))) {
            *r = '\0';
            rv--;
        }
#endif
    }
    return rv;
}

int SSL_password_callback(char *buf, int bufsiz, int verify,
                          void *cb)
{
    tcn_pass_cb_t *cb_data = (tcn_pass_cb_t *)cb;

    if (buf == NULL)
        return 0;
    *buf = '\0';
    if (cb_data == NULL)
        cb_data = &tcn_password_callback;
    if (!cb_data->prompt)
        cb_data->prompt = SSL_DEFAULT_PASS_PROMPT;
    if (cb_data->password[0]) {
        /* Return already obtained password */
        strncpy(buf, cb_data->password, bufsiz);
        buf[bufsiz - 1] = '\0';
        return (int)strlen(buf);
    }
    else {
        if (SSL_password_prompt(cb_data) > 0)
            strncpy(buf, cb_data->password, bufsiz);
    }
    buf[bufsiz - 1] = '\0';
    return (int)strlen(buf);
}

static unsigned char dh0512_p[]={
    0xD9,0xBA,0xBF,0xFD,0x69,0x38,0xC9,0x51,0x2D,0x19,0x37,0x39,
    0xD7,0x7D,0x7E,0x3E,0x25,0x58,0x55,0x94,0x90,0x60,0x93,0x7A,
    0xF2,0xD5,0x61,0x5F,0x06,0xE8,0x08,0xB4,0x57,0xF4,0xCF,0xB4,
    0x41,0xCC,0xC4,0xAC,0xD4,0xF0,0x45,0x88,0xC9,0xD1,0x21,0x4C,
    0xB6,0x72,0x48,0xBD,0x73,0x80,0xE0,0xDD,0x88,0x41,0xA0,0xF1,
    0xEA,0x4B,0x71,0x13
};
static unsigned char dh1024_p[]={
    0xA2,0x95,0x7E,0x7C,0xA9,0xD5,0x55,0x1D,0x7C,0x77,0x11,0xAC,
    0xFD,0x48,0x8C,0x3B,0x94,0x1B,0xC5,0xC0,0x99,0x93,0xB5,0xDC,
    0xDC,0x06,0x76,0x9E,0xED,0x1E,0x3D,0xBB,0x9A,0x29,0xD6,0x8B,
    0x1F,0xF6,0xDA,0xC9,0xDF,0xD5,0x02,0x4F,0x09,0xDE,0xEC,0x2C,
    0x59,0x1E,0x82,0x32,0x80,0x9B,0xED,0x51,0x68,0xD2,0xFB,0x1E,
    0x25,0xDB,0xDF,0x9C,0x11,0x70,0xDF,0xCA,0x19,0x03,0x3D,0x3D,
    0xC1,0xAC,0x28,0x88,0x4F,0x13,0xAF,0x16,0x60,0x6B,0x5B,0x2F,
    0x56,0xC7,0x5B,0x5D,0xDE,0x8F,0x50,0x08,0xEC,0xB1,0xB9,0x29,
    0xAA,0x54,0xF4,0x05,0xC9,0xDF,0x95,0x9D,0x79,0xC6,0xEA,0x3F,
    0xC9,0x70,0x42,0xDA,0x90,0xC7,0xCC,0x12,0xB9,0x87,0x86,0x39,
    0x1E,0x1A,0xCE,0xF7,0x3F,0x15,0xB5,0x2B
};
static unsigned char dh2048_p[]={
    0xF2,0x4A,0xFC,0x7E,0x73,0x48,0x21,0x03,0xD1,0x1D,0xA8,0x16,
    0x87,0xD0,0xD2,0xDC,0x42,0xA8,0xD2,0x73,0xE3,0xA9,0x21,0x31,
    0x70,0x5D,0x69,0xC7,0x8F,0x95,0x0C,0x9F,0xB8,0x0E,0x37,0xAE,
    0xD1,0x6F,0x36,0x1C,0x26,0x63,0x2A,0x36,0xBA,0x0D,0x2A,0xF5,
    0x1A,0x0F,0xE8,0xC0,0xEA,0xD1,0xB5,0x52,0x47,0x1F,0x9A,0x0C,
    0x0F,0xED,0x71,0x51,0xED,0xE6,0x62,0xD5,0xF8,0x81,0x93,0x55,
    0xC1,0x0F,0xB4,0x72,0x64,0xB3,0x73,0xAA,0x90,0x9A,0x81,0xCE,
    0x03,0xFD,0x6D,0xB1,0x27,0x7D,0xE9,0x90,0x5E,0xE2,0x10,0x74,
    0x4F,0x94,0xC3,0x05,0x21,0x73,0xA9,0x12,0x06,0x9B,0x0E,0x20,
    0xD1,0x5F,0xF7,0xC9,0x4C,0x9D,0x4F,0xFA,0xCA,0x4D,0xFD,0xFF,
    0x6A,0x62,0x9F,0xF0,0x0F,0x3B,0xA9,0x1D,0xF2,0x69,0x29,0x00,
    0xBD,0xE9,0xB0,0x9D,0x88,0xC7,0x4A,0xAE,0xB0,0x53,0xAC,0xA2,
    0x27,0x40,0x88,0x58,0x8F,0x26,0xB2,0xC2,0x34,0x7D,0xA2,0xCF,
    0x92,0x60,0x9B,0x35,0xF6,0xF3,0x3B,0xC3,0xAA,0xD8,0x58,0x9C,
    0xCF,0x5D,0x9F,0xDB,0x14,0x93,0xFA,0xA3,0xFA,0x44,0xB1,0xB2,
    0x4B,0x0F,0x08,0x70,0x44,0x71,0x3A,0x73,0x45,0x8E,0x6D,0x9C,
    0x56,0xBC,0x9A,0xB5,0xB1,0x3D,0x8B,0x1F,0x1E,0x2B,0x0E,0x93,
    0xC2,0x9B,0x84,0xE2,0xE8,0xFC,0x29,0x85,0x83,0x8D,0x2E,0x5C,
    0xDD,0x9A,0xBB,0xFD,0xF0,0x87,0xBF,0xAF,0xC4,0xB6,0x1D,0xE7,
    0xF9,0x46,0x50,0x7F,0xC3,0xAC,0xFD,0xC9,0x8C,0x9D,0x66,0x6B,
    0x4C,0x6A,0xC9,0x3F,0x0C,0x0A,0x74,0x94,0x41,0x85,0x26,0x8F,
    0x9F,0xF0,0x7C,0x0B
};
static unsigned char dh4096_p[] = {
    0x8D,0xD3,0x8F,0x77,0x6F,0x6F,0xB0,0x74,0x3F,0x22,0xE9,0xD1,
    0x17,0x15,0x69,0xD8,0x24,0x85,0xCD,0xC4,0xE4,0x0E,0xF6,0x52,
    0x40,0xF7,0x1C,0x34,0xD0,0xA5,0x20,0x77,0xE2,0xFC,0x7D,0xA1,
    0x82,0xF1,0xF3,0x78,0x95,0x05,0x5B,0xB8,0xDB,0xB3,0xE4,0x17,
    0x93,0xD6,0x68,0xA7,0x0A,0x0C,0xC5,0xBB,0x9C,0x5E,0x1E,0x83,
    0x72,0xB3,0x12,0x81,0xA2,0xF5,0xCD,0x44,0x67,0xAA,0xE8,0xAD,
    0x1E,0x8F,0x26,0x25,0xF2,0x8A,0xA0,0xA5,0xF4,0xFB,0x95,0xAE,
    0x06,0x50,0x4B,0xD0,0xE7,0x0C,0x55,0x88,0xAA,0xE6,0xB8,0xF6,
    0xE9,0x2F,0x8D,0xA7,0xAD,0x84,0xBC,0x8D,0x4C,0xFE,0x76,0x60,
    0xCD,0xC8,0xED,0x7C,0xBF,0xF3,0xC1,0xF8,0x6A,0xED,0xEC,0xE9,
    0x13,0x7D,0x4E,0x72,0x20,0x77,0x06,0xA4,0x12,0xF8,0xD2,0x34,
    0x6F,0xDC,0x97,0xAB,0xD3,0xA0,0x45,0x8E,0x7D,0x21,0xA9,0x35,
    0x6E,0xE4,0xC9,0xC4,0x53,0xFF,0xE5,0xD9,0x72,0x61,0xC4,0x8A,
    0x75,0x78,0x36,0x97,0x1A,0xAB,0x92,0x85,0x74,0x61,0x7B,0xE0,
    0x92,0xB8,0xC6,0x12,0xA1,0x72,0xBB,0x5B,0x61,0xAA,0xE6,0x2C,
    0x2D,0x9F,0x45,0x79,0x9E,0xF4,0x41,0x93,0x93,0xEF,0x8B,0xEF,
    0xB7,0xBF,0x6D,0xF0,0x91,0x11,0x4F,0x7C,0x71,0x84,0xB5,0x88,
    0xA3,0x8C,0x1A,0xD5,0xD0,0x81,0x9C,0x50,0xAC,0xA9,0x2B,0xE9,
    0x92,0x2D,0x73,0x7C,0x0A,0xA3,0xFA,0xD3,0x6C,0x91,0x43,0xA6,
    0x80,0x7F,0xD7,0xC4,0xD8,0x6F,0x85,0xF8,0x15,0xFD,0x08,0xA6,
    0xF8,0x7B,0x3A,0xF4,0xD3,0x50,0xB4,0x2F,0x75,0xC8,0x48,0xB8,
    0xA8,0xFD,0xCA,0x8F,0x62,0xF1,0x4C,0x89,0xB7,0x18,0x67,0xB2,
    0x93,0x2C,0xC4,0xD4,0x71,0x29,0xA9,0x26,0x20,0xED,0x65,0x37,
    0x06,0x87,0xFC,0xFB,0x65,0x02,0x1B,0x3C,0x52,0x03,0xA1,0xBB,
    0xCF,0xE7,0x1B,0xA4,0x1A,0xE3,0x94,0x97,0x66,0x06,0xBF,0xA9,
    0xCE,0x1B,0x07,0x10,0xBA,0xF8,0xD4,0xD4,0x05,0xCF,0x53,0x47,
    0x16,0x2C,0xA1,0xFC,0x6B,0xEF,0xF8,0x6C,0x23,0x34,0xEF,0xB7,
    0xD3,0x3F,0xC2,0x42,0x5C,0x53,0x9A,0x00,0x52,0xCF,0xAC,0x42,
    0xD3,0x3B,0x2E,0xB6,0x04,0x32,0xE1,0x09,0xED,0x64,0xCD,0x6A,
    0x63,0x58,0xB8,0x43,0x56,0x5A,0xBE,0xA4,0x9F,0x68,0xD4,0xF7,
    0xC9,0x04,0xDF,0xCD,0xE5,0x93,0xB0,0x2F,0x06,0x19,0x3E,0xB8,
    0xAB,0x7E,0xF8,0xE7,0xE7,0xC8,0x53,0xA2,0x06,0xC3,0xC7,0xF9,
    0x18,0x3B,0x51,0xC3,0x9B,0xFF,0x8F,0x00,0x0E,0x87,0x19,0x68,
    0x2F,0x40,0xC0,0x68,0xFA,0x12,0xAE,0x57,0xB5,0xF0,0x97,0xCA,
    0x78,0x23,0x31,0xAB,0x67,0x7B,0x10,0x6B,0x59,0x32,0x9C,0x64,
    0x20,0x38,0x1F,0xC5,0x07,0x84,0x9E,0xC4,0x49,0xB1,0xDF,0xED,
    0x7A,0x8A,0xC3,0xE0,0xDD,0x30,0x55,0xFF,0x95,0x45,0xA6,0xEE,
    0xCB,0xE4,0x26,0xB9,0x8E,0x89,0x37,0x63,0xD4,0x02,0x3D,0x5B,
    0x4F,0xE5,0x90,0xF6,0x72,0xF8,0x10,0xEE,0x31,0x04,0x54,0x17,
    0xE3,0xD5,0x63,0x84,0x80,0x62,0x54,0x46,0x85,0x6C,0xD2,0xC1,
    0x3E,0x19,0xBD,0xE2,0x80,0x11,0x86,0xC7,0x4B,0x7F,0x67,0x86,
    0x47,0xD2,0x38,0xCD,0x8F,0xFE,0x65,0x3C,0x11,0xCD,0x96,0x99,
    0x4E,0x45,0xEB,0xEC,0x1D,0x94,0x8C,0x53,
};
static unsigned char dhxxx2_g[]={
    0x02
};

static DH *get_dh(int idx)
{
    DH *dh;

    if ((dh = DH_new()) == NULL)
        return NULL;
    switch (idx) {
        case SSL_TMP_KEY_DH_512:
            dh->p = BN_bin2bn(dh0512_p, sizeof(dh0512_p), NULL);
        break;
        case SSL_TMP_KEY_DH_1024:
            dh->p = BN_bin2bn(dh1024_p, sizeof(dh1024_p), NULL);
        break;
        case SSL_TMP_KEY_DH_2048:
            dh->p = BN_bin2bn(dh2048_p, sizeof(dh2048_p), NULL);
        break;
        case SSL_TMP_KEY_DH_4096:
            dh->p = BN_bin2bn(dh4096_p, sizeof(dh2048_p), NULL);
        break;
    }
    dh->g = BN_bin2bn(dhxxx2_g, sizeof(dhxxx2_g), NULL);
    if ((dh->p == NULL) || (dh->g == NULL)) {
        DH_free(dh);
        return NULL;
    }
    else
        return dh;
}

DH *SSL_dh_get_tmp_param(int key_len)
{
    DH *dh;

    if (key_len == 512)
        dh = get_dh(SSL_TMP_KEY_DH_512);
    else if (key_len == 1024)
        dh = get_dh(SSL_TMP_KEY_DH_1024);
    else if (key_len == 2048)
        dh = get_dh(SSL_TMP_KEY_DH_2048);
    else if (key_len == 4096)
        dh = get_dh(SSL_TMP_KEY_DH_4096);
    else
        dh = get_dh(SSL_TMP_KEY_DH_1024);
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
    int idx;

    /* doesn't matter if export flag is on,
     * we won't be asked for keylen > 512 in that case.
     * if we are asked for a keylen > 1024, it is too expensive
     * to generate on the fly.
     */

    switch (keylen) {
        case 512:
            idx = SSL_TMP_KEY_RSA_512;
        break;
        case 2048:
            idx = SSL_TMP_KEY_RSA_2048;
            if (SSL_temp_keys[idx] == NULL)
                idx = SSL_TMP_KEY_RSA_1024;
        break;
        case 4096:
            idx = SSL_TMP_KEY_RSA_4096;
            if (SSL_temp_keys[idx] == NULL)
                idx = SSL_TMP_KEY_RSA_2048;
        break;
        case 1024:
        default:
            idx = SSL_TMP_KEY_RSA_1024;
        break;
    }
    return (RSA *)SSL_temp_keys[idx];
}

/*
 * Hand out the already generated DH parameters...
 */
DH *SSL_callback_tmp_DH(SSL *ssl, int export, int keylen)
{
    int idx;
    switch (keylen) {
        case 512:
            idx = SSL_TMP_KEY_DH_512;
        break;
        case 2048:
            idx = SSL_TMP_KEY_DH_2048;
        break;
        case 4096:
            idx = SSL_TMP_KEY_DH_4096;
        break;
        case 1024:
        default:
            idx = SSL_TMP_KEY_DH_1024;
        break;
    }
    return (DH *)SSL_temp_keys[idx];
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
int SSL_CTX_use_certificate_chain(SSL_CTX *ctx, const char *file,
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
    extra_certs = SSL_CTX_get_extra_certs(ctx);
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

static int ssl_X509_STORE_lookup(X509_STORE *store, int yype,
                                 X509_NAME *name, X509_OBJECT *obj)
{
    X509_STORE_CTX ctx;
    int rc;

    X509_STORE_CTX_init(&ctx, store, NULL, NULL);
    rc = X509_STORE_get_by_subject(&ctx, yype, name, obj);
    X509_STORE_CTX_cleanup(&ctx);
    return rc;
}

static int ssl_verify_CRL(int ok, X509_STORE_CTX *ctx, tcn_ssl_conn_t *con)
{
    X509_OBJECT obj;
    X509_NAME *subject, *issuer;
    X509 *cert;
    X509_CRL *crl;
    EVP_PKEY *pubkey;
    int i, n, rc;

    /*
     * Determine certificate ingredients in advance
     */
    cert    = X509_STORE_CTX_get_current_cert(ctx);
    subject = X509_get_subject_name(cert);
    issuer  = X509_get_issuer_name(cert);

    /*
     * OpenSSL provides the general mechanism to deal with CRLs but does not
     * use them automatically when verifying certificates, so we do it
     * explicitly here. We will check the CRL for the currently checked
     * certificate, if there is such a CRL in the store.
     *
     * We come through this procedure for each certificate in the certificate
     * chain, starting with the root-CA's certificate. At each step we've to
     * both verify the signature on the CRL (to make sure it's a valid CRL)
     * and it's revocation list (to make sure the current certificate isn't
     * revoked).  But because to check the signature on the CRL we need the
     * public key of the issuing CA certificate (which was already processed
     * one round before), we've a little problem. But we can both solve it and
     * at the same time optimize the processing by using the following
     * verification scheme (idea and code snippets borrowed from the GLOBUS
     * project):
     *
     * 1. We'll check the signature of a CRL in each step when we find a CRL
     *    through the _subject_ name of the current certificate. This CRL
     *    itself will be needed the first time in the next round, of course.
     *    But we do the signature processing one round before this where the
     *    public key of the CA is available.
     *
     * 2. We'll check the revocation list of a CRL in each step when
     *    we find a CRL through the _issuer_ name of the current certificate.
     *    This CRLs signature was then already verified one round before.
     *
     * This verification scheme allows a CA to revoke its own certificate as
     * well, of course.
     */

    /*
     * Try to retrieve a CRL corresponding to the _subject_ of
     * the current certificate in order to verify it's integrity.
     */
    memset((char *)&obj, 0, sizeof(obj));
    rc = ssl_X509_STORE_lookup(con->ctx->crl,
                               X509_LU_CRL, subject, &obj);
    crl = obj.data.crl;

    if ((rc > 0) && crl) {
        /*
         * Log information about CRL
         * (A little bit complicated because of ASN.1 and BIOs...)
         */
        /*
         * Verify the signature on this CRL
         */
        pubkey = X509_get_pubkey(cert);
        rc = X509_CRL_verify(crl, pubkey);
        /* Only refcounted in OpenSSL */
        if (pubkey)
            EVP_PKEY_free(pubkey);
        if (rc <= 0) {
            /* TODO: Log Invalid signature on CRL */
            X509_STORE_CTX_set_error(ctx, X509_V_ERR_CRL_SIGNATURE_FAILURE);
            X509_OBJECT_free_contents(&obj);
            return 0;
        }

        /*
         * Check date of CRL to make sure it's not expired
         */
        i = X509_cmp_current_time(X509_CRL_get_nextUpdate(crl));

        if (i == 0) {
            /* TODO: Log Found CRL has invalid nextUpdate field */

            X509_STORE_CTX_set_error(ctx,
                                     X509_V_ERR_ERROR_IN_CRL_NEXT_UPDATE_FIELD);
            X509_OBJECT_free_contents(&obj);
            return 0;
        }

        if (i < 0) {
            /* TODO: Log Found CRL is expired */
            X509_STORE_CTX_set_error(ctx, X509_V_ERR_CRL_HAS_EXPIRED);
            X509_OBJECT_free_contents(&obj);

            return 0;
        }

        X509_OBJECT_free_contents(&obj);
    }

    /*
     * Try to retrieve a CRL corresponding to the _issuer_ of
     * the current certificate in order to check for revocation.
     */
    memset((char *)&obj, 0, sizeof(obj));
    rc = ssl_X509_STORE_lookup(con->ctx->crl,
                               X509_LU_CRL, issuer, &obj);

    crl = obj.data.crl;
    if ((rc > 0) && crl) {
        /*
         * Check if the current certificate is revoked by this CRL
         */
        n = sk_X509_REVOKED_num(X509_CRL_get_REVOKED(crl));

        for (i = 0; i < n; i++) {
            X509_REVOKED *revoked =
                sk_X509_REVOKED_value(X509_CRL_get_REVOKED(crl), i);

            ASN1_INTEGER *sn = revoked->serialNumber;

            if (!ASN1_INTEGER_cmp(sn, X509_get_serialNumber(cert))) {
                X509_STORE_CTX_set_error(ctx, X509_V_ERR_CERT_REVOKED);
                X509_OBJECT_free_contents(&obj);

                return 0;
            }
        }

        X509_OBJECT_free_contents(&obj);
    }

    return ok;
}

/*
 * This OpenSSL callback function is called when OpenSSL
 * does client authentication and verifies the certificate chain.
 */
int SSL_callback_SSL_verify(int ok, X509_STORE_CTX *ctx)
{
   /* Get Apache context back through OpenSSL context */
    SSL *ssl = X509_STORE_CTX_get_ex_data(ctx,
                                          SSL_get_ex_data_X509_STORE_CTX_idx());
    tcn_ssl_conn_t *con = (tcn_ssl_conn_t *)SSL_get_app_data(ssl);
    /* Get verify ingredients */
    int errnum   = X509_STORE_CTX_get_error(ctx);
    int errdepth = X509_STORE_CTX_get_error_depth(ctx);
    int verify   = con->ctx->verify_mode;
    int depth    = con->ctx->verify_depth;

    if (verify == SSL_CVERIFY_UNSET ||
        verify == SSL_CVERIFY_NONE)
        return 1;

    if (SSL_VERIFY_ERROR_IS_OPTIONAL(errnum) &&
        (verify == SSL_CVERIFY_OPTIONAL_NO_CA)) {
        ok = 1;
        SSL_set_verify_result(ssl, X509_V_OK);
    }
    /*
     * Additionally perform CRL-based revocation checks
     */
    if (ok && con->ctx->crl) {
        if (!(ok = ssl_verify_CRL(ok, ctx, con))) {
            errnum = X509_STORE_CTX_get_error(ctx);
            /* TODO: Log something */
        }
    }
    /*
     * If we already know it's not ok, log the real reason
     */
    if (!ok) {
        /* TODO: Some logging
         * Certificate Verification: Error
         */
        if (con->peer) {
            X509_free(con->peer);
            con->peer = NULL;
        }
    }
    if (errdepth > depth) {
        /* TODO: Some logging
         * Certificate Verification: Certificate Chain too long
         */
        ok = 0;
    }
    return ok;
}

#endif
