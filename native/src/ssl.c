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

/*
 *
 * @author Mladen Turk
 * @version $Id$
 */

#include "tcn.h"
#include "apr_file_io.h"
#include "apr_thread_mutex.h"
#include "apr_atomic.h"
#include "apr_poll.h"

#ifdef HAVE_OPENSSL
#include "ssl_private.h"

static int ssl_initialized = 0;
static char *ssl_global_rand_file = NULL;
extern apr_pool_t *tcn_global_pool;

ENGINE *tcn_ssl_engine = NULL;
void *SSL_temp_keys[SSL_TMP_KEY_MAX];
tcn_pass_cb_t tcn_password_callback;

/* Global reference to the pool used by the dynamic mutexes */
static apr_pool_t *dynlockpool = NULL;

/* Dynamic lock structure */
struct CRYPTO_dynlock_value {
    apr_pool_t *pool;
    const char* file;
    int line;
    apr_thread_mutex_t *mutex;
};


/*
 * Handle the Temporary RSA Keys and DH Params
 */

#define SSL_TMP_KEY_FREE(type, idx)                     \
    if (SSL_temp_keys[idx]) {                           \
        type##_free((type *)SSL_temp_keys[idx]);        \
        SSL_temp_keys[idx] = NULL;                      \
    } else (void)(0)

#define SSL_TMP_KEYS_FREE(type) \
    SSL_TMP_KEY_FREE(type, SSL_TMP_KEY_##type##_512);   \
    SSL_TMP_KEY_FREE(type, SSL_TMP_KEY_##type##_1024);  \
    SSL_TMP_KEY_FREE(type, SSL_TMP_KEY_##type##_2048);  \
    SSL_TMP_KEY_FREE(type, SSL_TMP_KEY_##type##_4096)

#define SSL_TMP_KEY_INIT_RSA(bits) \
    ssl_tmp_key_init_rsa(bits, SSL_TMP_KEY_RSA_##bits)

#define SSL_TMP_KEY_INIT_DH(bits)  \
    ssl_tmp_key_init_dh(bits, SSL_TMP_KEY_DH_##bits)

#define SSL_TMP_KEYS_INIT(R)                    \
    SSL_temp_keys[SSL_TMP_KEY_RSA_2048] = NULL; \
    SSL_temp_keys[SSL_TMP_KEY_RSA_4096] = NULL; \
    R |= SSL_TMP_KEY_INIT_RSA(512);             \
    R |= SSL_TMP_KEY_INIT_RSA(1024);            \
    R |= SSL_TMP_KEY_INIT_DH(512);              \
    R |= SSL_TMP_KEY_INIT_DH(1024);             \
    R |= SSL_TMP_KEY_INIT_DH(2048);             \
    R |= SSL_TMP_KEY_INIT_DH(4096)

/*
 * supported_ssl_opts is a bitmask that contains all supported SSL_OP_*
 * options at compile-time. This is used in hasOp to determine which
 * SSL_OP_* options are available at runtime.
 *
 * Note that at least up through OpenSSL 0.9.8o, checking SSL_OP_ALL will
 * return JNI_FALSE because SSL_OP_ALL is a mask that covers all bug
 * workarounds for OpenSSL including future workarounds that are defined
 * to be in the least-significant 3 nibbles of the SSL_OP_* bit space.
 *
 * This implementation has chosen NOT to simply set all those lower bits
 * so that the return value for SSL_OP_FUTURE_WORKAROUND will only be
 * reported by versions that actually support that specific workaround.
 */
static const jint supported_ssl_opts = 0
/*
  Specifically skip SSL_OP_ALL
#ifdef SSL_OP_ALL
     | SSL_OP_ALL
#endif
*/
#ifdef SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION
     | SSL_OP_ALLOW_UNSAFE_LEGACY_RENEGOTIATION
#endif

#ifdef SSL_OP_CIPHER_SERVER_PREFERENCE
     | SSL_OP_CIPHER_SERVER_PREFERENCE
#endif

#ifdef SSL_OP_CRYPTOPRO_TLSEXT_BUG
     | SSL_OP_CRYPTOPRO_TLSEXT_BUG
#endif

#ifdef SSL_OP_DONT_INSERT_EMPTY_FRAGMENTS
     | SSL_OP_DONT_INSERT_EMPTY_FRAGMENTS
#endif

#ifdef SSL_OP_EPHEMERAL_RSA
     | SSL_OP_EPHEMERAL_RSA
#endif

#ifdef SSL_OP_LEGACY_SERVER_CONNECT
     | SSL_OP_LEGACY_SERVER_CONNECT
#endif

#ifdef SSL_OP_MICROSOFT_BIG_SSLV3_BUFFER
     | SSL_OP_MICROSOFT_BIG_SSLV3_BUFFER
#endif

#ifdef SSL_OP_MICROSOFT_SESS_ID_BUG
     | SSL_OP_MICROSOFT_SESS_ID_BUG
#endif

#ifdef SSL_OP_MSIE_SSLV2_RSA_PADDING
     | SSL_OP_MSIE_SSLV2_RSA_PADDING
#endif

#ifdef SSL_OP_NETSCAPE_CA_DN_BUG
     | SSL_OP_NETSCAPE_CA_DN_BUG
#endif

#ifdef SSL_OP_NETSCAPE_CHALLENGE_BUG
     | SSL_OP_NETSCAPE_CHALLENGE_BUG
#endif

#ifdef SSL_OP_NETSCAPE_DEMO_CIPHER_CHANGE_BUG
     | SSL_OP_NETSCAPE_DEMO_CIPHER_CHANGE_BUG
#endif

#ifdef SSL_OP_NETSCAPE_REUSE_CIPHER_CHANGE_BUG
     | SSL_OP_NETSCAPE_REUSE_CIPHER_CHANGE_BUG
#endif

#ifdef SSL_OP_NO_COMPRESSION
     | SSL_OP_NO_COMPRESSION
#endif

#ifdef SSL_OP_NO_QUERY_MTU
     | SSL_OP_NO_QUERY_MTU
#endif

#ifdef SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION
     | SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION
#endif

#ifdef SSL_OP_NO_SSLv2
     | SSL_OP_NO_SSLv2
#endif

#ifdef SSL_OP_NO_SSLv3
     | SSL_OP_NO_SSLv3
#endif

#ifdef SSL_OP_NO_TICKET
     | SSL_OP_NO_TICKET
#endif

#ifdef SSL_OP_NO_TLSv1
     | SSL_OP_NO_TLSv1
#endif

#ifdef SSL_OP_PKCS1_CHECK_1
     | SSL_OP_PKCS1_CHECK_1
#endif

#ifdef SSL_OP_PKCS1_CHECK_2
     | SSL_OP_PKCS1_CHECK_2
#endif

#ifdef SSL_OP_SINGLE_DH_USE
     | SSL_OP_SINGLE_DH_USE
#endif

#ifdef SSL_OP_SINGLE_ECDH_USE
     | SSL_OP_SINGLE_ECDH_USE
#endif

#ifdef SSL_OP_SSLEAY_080_CLIENT_DH_BUG
     | SSL_OP_SSLEAY_080_CLIENT_DH_BUG
#endif

#ifdef SSL_OP_SSLREF2_REUSE_CERT_TYPE_BUG
     | SSL_OP_SSLREF2_REUSE_CERT_TYPE_BUG
#endif

#ifdef SSL_OP_TLS_BLOCK_PADDING_BUG
     | SSL_OP_TLS_BLOCK_PADDING_BUG
#endif

#ifdef SSL_OP_TLS_D5_BUG
     | SSL_OP_TLS_D5_BUG
#endif

#ifdef SSL_OP_TLS_ROLLBACK_BUG
     | SSL_OP_TLS_ROLLBACK_BUG
#endif
     | 0;

static int ssl_tmp_key_init_rsa(int bits, int idx)
{
    if (!(SSL_temp_keys[idx] =
          RSA_generate_key(bits, RSA_F4, NULL, NULL)))
        return 1;
    else
        return 0;
}

static int ssl_tmp_key_init_dh(int bits, int idx)
{
    if (!(SSL_temp_keys[idx] =
          SSL_dh_get_tmp_param(bits)))
        return 1;
    else
        return 0;
}


TCN_IMPLEMENT_CALL(jint, SSL, version)(TCN_STDARGS)
{
    UNREFERENCED_STDARGS;
    return OPENSSL_VERSION_NUMBER;
}

TCN_IMPLEMENT_CALL(jstring, SSL, versionString)(TCN_STDARGS)
{
    UNREFERENCED(o);
    return AJP_TO_JSTRING(OPENSSL_VERSION_TEXT);
}

/*
 *  the various processing hooks
 */
static apr_status_t ssl_init_cleanup(void *data)
{
    UNREFERENCED(data);

    if (!ssl_initialized)
        return APR_SUCCESS;
    ssl_initialized = 0;

    if (tcn_password_callback.cb.obj) {
        JNIEnv *env;
        tcn_get_java_env(&env);
        TCN_UNLOAD_CLASS(env,
                         tcn_password_callback.cb.obj);
    }

    SSL_TMP_KEYS_FREE(RSA);
    SSL_TMP_KEYS_FREE(DH);
    /*
     * Try to kill the internals of the SSL library.
     */
#if OPENSSL_VERSION_NUMBER >= 0x00907001
    /* Corresponds to OPENSSL_load_builtin_modules():
     * XXX: borrowed from apps.h, but why not CONF_modules_free()
     * which also invokes CONF_modules_finish()?
     */
    CONF_modules_unload(1);
#endif
    /* Corresponds to SSL_library_init: */
    EVP_cleanup();
#if HAVE_ENGINE_LOAD_BUILTIN_ENGINES
    ENGINE_cleanup();
#endif
#if OPENSSL_VERSION_NUMBER >= 0x00907001
    CRYPTO_cleanup_all_ex_data();
#endif
    ERR_remove_state(0);

    /* Don't call ERR_free_strings here; ERR_load_*_strings only
     * actually load the error strings once per process due to static
     * variable abuse in OpenSSL. */

    /*
     * TODO: determine somewhere we can safely shove out diagnostics
     *       (when enabled) at this late stage in the game:
     * CRYPTO_mem_leaks_fp(stderr);
     */
    return APR_SUCCESS;
}

#ifndef OPENSSL_NO_ENGINE
/* Try to load an engine in a shareable library */
static ENGINE *ssl_try_load_engine(const char *engine)
{
    ENGINE *e = ENGINE_by_id("dynamic");
    if (e) {
        if (!ENGINE_ctrl_cmd_string(e, "SO_PATH", engine, 0)
            || !ENGINE_ctrl_cmd_string(e, "LOAD", NULL, 0)) {
            ENGINE_free(e);
            e = NULL;
        }
    }
    return e;
}
#endif

/*
 * To ensure thread-safetyness in OpenSSL
 */

static apr_thread_mutex_t **ssl_lock_cs;
static int                  ssl_lock_num_locks;

static void ssl_thread_lock(int mode, int type,
                            const char *file, int line)
{
    UNREFERENCED(file);
    UNREFERENCED(line);
    if (type < ssl_lock_num_locks) {
        if (mode & CRYPTO_LOCK) {
            apr_thread_mutex_lock(ssl_lock_cs[type]);
        }
        else {
            apr_thread_mutex_unlock(ssl_lock_cs[type]);
        }
    }
}

static unsigned long ssl_thread_id(void)
{
    /* OpenSSL needs this to return an unsigned long.  On OS/390, the pthread
     * id is a structure twice that big.  Use the TCB pointer instead as a
     * unique unsigned long.
     */
#ifdef __MVS__
    struct PSA {
        char unmapped[540];
        unsigned long PSATOLD;
    } *psaptr = 0;

    return psaptr->PSATOLD;
#elif defined(WIN32)
    return (unsigned long)GetCurrentThreadId();
#else
    return (unsigned long)(apr_os_thread_current());
#endif
}

static apr_status_t ssl_thread_cleanup(void *data)
{
    UNREFERENCED(data);
    CRYPTO_set_locking_callback(NULL);
    CRYPTO_set_id_callback(NULL);
    CRYPTO_set_dynlock_create_callback(NULL);
    CRYPTO_set_dynlock_lock_callback(NULL);
    CRYPTO_set_dynlock_destroy_callback(NULL);

    dynlockpool = NULL;

    /* Let the registered mutex cleanups do their own thing
     */
    return APR_SUCCESS;
}

/*
 * Dynamic lock creation callback
 */
static struct CRYPTO_dynlock_value *ssl_dyn_create_function(const char *file,
                                                     int line)
{
    struct CRYPTO_dynlock_value *value;
    apr_pool_t *p;
    apr_status_t rv;

    /*
     * We need a pool to allocate our mutex.  Since we can't clear
     * allocated memory from a pool, create a subpool that we can blow
     * away in the destruction callback.
     */
    rv = apr_pool_create(&p, dynlockpool);
    if (rv != APR_SUCCESS) {
        /* TODO log that fprintf(stderr, "Failed to create subpool for dynamic lock"); */
        return NULL;
    }

    value = (struct CRYPTO_dynlock_value *)apr_palloc(p,
                                                      sizeof(struct CRYPTO_dynlock_value));
    if (!value) {
        /* TODO log that fprintf(stderr, "Failed to allocate dynamic lock structure"); */
        return NULL;
    }

    value->pool = p;
    /* Keep our own copy of the place from which we were created,
       using our own pool. */
    value->file = apr_pstrdup(p, file);
    value->line = line;
    rv = apr_thread_mutex_create(&(value->mutex), APR_THREAD_MUTEX_DEFAULT,
                                p);
    if (rv != APR_SUCCESS) {
        /* TODO log that fprintf(stderr, "Failed to create thread mutex for dynamic lock"); */
        apr_pool_destroy(p);
        return NULL;
    }
    return value;
}

/*
 * Dynamic locking and unlocking function
 */

static void ssl_dyn_lock_function(int mode, struct CRYPTO_dynlock_value *l,
                           const char *file, int line)
{


    if (mode & CRYPTO_LOCK) {
        apr_thread_mutex_lock(l->mutex);
    }
    else {
        apr_thread_mutex_unlock(l->mutex);
    }
}

/*
 * Dynamic lock destruction callback
 */
static void ssl_dyn_destroy_function(struct CRYPTO_dynlock_value *l,
                          const char *file, int line)
{
    apr_status_t rv;
    rv = apr_thread_mutex_destroy(l->mutex);
    if (rv != APR_SUCCESS) {
        /* TODO log that fprintf(stderr, "Failed to destroy mutex for dynamic lock %s:%d", l->file, l->line); */
    }

    /* Trust that whomever owned the CRYPTO_dynlock_value we were
     * passed has no future use for it...
     */
    apr_pool_destroy(l->pool);
}
static void ssl_thread_setup(apr_pool_t *p)
{
    int i;

    ssl_lock_num_locks = CRYPTO_num_locks();
    ssl_lock_cs = apr_palloc(p, ssl_lock_num_locks * sizeof(*ssl_lock_cs));

    for (i = 0; i < ssl_lock_num_locks; i++) {
        apr_thread_mutex_create(&(ssl_lock_cs[i]),
                                APR_THREAD_MUTEX_DEFAULT, p);
    }

    CRYPTO_set_id_callback(ssl_thread_id);
    CRYPTO_set_locking_callback(ssl_thread_lock);

    /* Set up dynamic locking scaffolding for OpenSSL to use at its
     * convenience.
     */
    dynlockpool = p;
    CRYPTO_set_dynlock_create_callback(ssl_dyn_create_function);
    CRYPTO_set_dynlock_lock_callback(ssl_dyn_lock_function);
    CRYPTO_set_dynlock_destroy_callback(ssl_dyn_destroy_function);

    apr_pool_cleanup_register(p, NULL, ssl_thread_cleanup,
                              apr_pool_cleanup_null);
}

static int ssl_rand_choosenum(int l, int h)
{
    int i;
    char buf[50];

    apr_snprintf(buf, sizeof(buf), "%.0f",
                 (((double)(rand()%RAND_MAX)/RAND_MAX)*(h-l)));
    i = atoi(buf)+1;
    if (i < l) i = l;
    if (i > h) i = h;
    return i;
}

static int ssl_rand_load_file(const char *file)
{
    char buffer[APR_PATH_MAX];
    int n;

    if (file == NULL)
        file = ssl_global_rand_file;
    if (file && (strcmp(file, "builtin") == 0))
        return -1;
    if (file == NULL)
        file = RAND_file_name(buffer, sizeof(buffer));
    if (file) {
        if (strncmp(file, "egd:", 4) == 0) {
            if ((n = RAND_egd(file + 4)) > 0)
                return n;
            else
                return -1;
        }
        if ((n = RAND_load_file(file, -1)) > 0)
            return n;
    }
    return -1;
}

/*
 * writes a number of random bytes (currently 1024) to
 * file which can be used to initialize the PRNG by calling
 * RAND_load_file() in a later session
 */
static int ssl_rand_save_file(const char *file)
{
    char buffer[APR_PATH_MAX];
    int n;

    if (file == NULL)
        file = RAND_file_name(buffer, sizeof(buffer));
    else if ((n = RAND_egd(file)) > 0) {
        return 0;
    }
    if (file == NULL || !RAND_write_file(file))
        return 0;
    else
        return 1;
}

int SSL_rand_seed(const char *file)
{
    unsigned char stackdata[256];
    static volatile apr_uint32_t counter = 0;

    if (ssl_rand_load_file(file) < 0) {
        int n;
        struct {
            apr_time_t    t;
            pid_t         p;
            unsigned long i;
            apr_uint32_t  u;
        } _ssl_seed;
        if (counter == 0) {
            apr_generate_random_bytes(stackdata, 256);
            RAND_seed(stackdata, 128);
        }
        _ssl_seed.t = apr_time_now();
        _ssl_seed.p = getpid();
        _ssl_seed.i = ssl_thread_id();
        apr_atomic_inc32(&counter);
        _ssl_seed.u = counter;
        RAND_seed((unsigned char *)&_ssl_seed, sizeof(_ssl_seed));
        /*
         * seed in some current state of the run-time stack (128 bytes)
         */
        n = ssl_rand_choosenum(0, sizeof(stackdata)-128-1);
        RAND_seed(stackdata + n, 128);
    }
    return RAND_status();
}

static int ssl_rand_make(const char *file, int len, int base64)
{
    int r;
    int num = len;
    BIO *out = NULL;

    out = BIO_new(BIO_s_file());
    if (out == NULL)
        return 0;
    if ((r = BIO_write_filename(out, (char *)file)) < 0) {
        BIO_free_all(out);
        return 0;
    }
    if (base64) {
        BIO *b64 = BIO_new(BIO_f_base64());
        if (b64 == NULL) {
            BIO_free_all(out);
            return 0;
        }
        out = BIO_push(b64, out);
    }
    while (num > 0) {
        unsigned char buf[4096];
        int len = num;
        if (len > sizeof(buf))
            len = sizeof(buf);
        r = RAND_bytes(buf, len);
        if (r <= 0) {
            BIO_free_all(out);
            return 0;
        }
        BIO_write(out, buf, len);
        num -= len;
    }
    r = BIO_flush(out);
    BIO_free_all(out);
    return r > 0 ? 1 : 0;
}

TCN_IMPLEMENT_CALL(jint, SSL, initialize)(TCN_STDARGS, jstring engine)
{
    int r = 0;
    TCN_ALLOC_CSTRING(engine);

    UNREFERENCED(o);
    if (!tcn_global_pool) {
        TCN_FREE_CSTRING(engine);
        tcn_ThrowAPRException(e, APR_EINVAL);
        return (jint)APR_EINVAL;
    }
    /* Check if already initialized */
    if (ssl_initialized++) {
        TCN_FREE_CSTRING(engine);
        return (jint)APR_SUCCESS;
    }
    if (SSLeay() < 0x0090700L) {
        TCN_FREE_CSTRING(engine);
        tcn_ThrowAPRException(e, APR_EINVAL);
        ssl_initialized = 0;
        return (jint)APR_EINVAL;
    }
    /* We must register the library in full, to ensure our configuration
     * code can successfully test the SSL environment.
     */
    CRYPTO_malloc_init();
    ERR_load_crypto_strings();
    SSL_load_error_strings();
    SSL_library_init();
    OpenSSL_add_all_algorithms();
#if HAVE_ENGINE_LOAD_BUILTIN_ENGINES
    ENGINE_load_builtin_engines();
#endif
#if OPENSSL_VERSION_NUMBER >= 0x00907001
    OPENSSL_load_builtin_modules();
#endif

    /* Initialize thread support */
    ssl_thread_setup(tcn_global_pool);

#ifndef OPENSSL_NO_ENGINE
    if (J2S(engine)) {
        ENGINE *ee = NULL;
        apr_status_t err = APR_SUCCESS;
        if(strcmp(J2S(engine), "auto") == 0) {
            ENGINE_register_all_complete();
        }
        else {
            if ((ee = ENGINE_by_id(J2S(engine))) == NULL
                && (ee = ssl_try_load_engine(J2S(engine))) == NULL)
                err = APR_ENOTIMPL;
            else {
                if (strcmp(J2S(engine), "chil") == 0)
                    ENGINE_ctrl(ee, ENGINE_CTRL_CHIL_SET_FORKCHECK, 1, 0, 0);
                if (!ENGINE_set_default(ee, ENGINE_METHOD_ALL))
                    err = APR_ENOTIMPL;
            }
            /* Free our "structural" reference. */
            if (ee)
                ENGINE_free(ee);
        }
        if (err != APR_SUCCESS) {
            TCN_FREE_CSTRING(engine);
            ssl_init_cleanup(NULL);
            tcn_ThrowAPRException(e, err);
            return (jint)err;
        }
        tcn_ssl_engine = ee;
    }
#endif

    memset(&tcn_password_callback, 0, sizeof(tcn_pass_cb_t));
    /* Initialize PRNG
     * This will in most cases call the builtin
     * low entropy seed.
     */
    SSL_rand_seed(NULL);
    /* For SSL_get_app_data2() at request time */
    SSL_init_app_data2_idx();

    SSL_TMP_KEYS_INIT(r);
    if (r) {
        TCN_FREE_CSTRING(engine);
        ssl_init_cleanup(NULL);
        tcn_ThrowAPRException(e, APR_ENOTIMPL);
        return APR_ENOTIMPL;
    }
    /*
     * Let us cleanup the ssl library when the library is unloaded
     */
    apr_pool_cleanup_register(tcn_global_pool, NULL,
                              ssl_init_cleanup,
                              apr_pool_cleanup_null);
    TCN_FREE_CSTRING(engine);
    return (jint)APR_SUCCESS;
}

TCN_IMPLEMENT_CALL(jboolean, SSL, randLoad)(TCN_STDARGS, jstring file)
{
    TCN_ALLOC_CSTRING(file);
    int r;
    UNREFERENCED(o);
    r = SSL_rand_seed(J2S(file));
    TCN_FREE_CSTRING(file);
    return r ? JNI_TRUE : JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jboolean, SSL, randSave)(TCN_STDARGS, jstring file)
{
    TCN_ALLOC_CSTRING(file);
    int r;
    UNREFERENCED(o);
    r = ssl_rand_save_file(J2S(file));
    TCN_FREE_CSTRING(file);
    return r ? JNI_TRUE : JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jboolean, SSL, randMake)(TCN_STDARGS, jstring file,
                                            jint length, jboolean base64)
{
    TCN_ALLOC_CSTRING(file);
    int r;
    UNREFERENCED(o);
    r = ssl_rand_make(J2S(file), length, base64);
    TCN_FREE_CSTRING(file);
    return r ? JNI_TRUE : JNI_FALSE;
}

TCN_IMPLEMENT_CALL(void, SSL, randSet)(TCN_STDARGS, jstring file)
{
    TCN_ALLOC_CSTRING(file);
    UNREFERENCED(o);
    if (J2S(file)) {
        ssl_global_rand_file = apr_pstrdup(tcn_global_pool, J2S(file));
    }
    TCN_FREE_CSTRING(file);
}

TCN_IMPLEMENT_CALL(jint, SSL, fipsModeGet)(TCN_STDARGS)
{
    UNREFERENCED(o);
#ifdef OPENSSL_FIPS
    return FIPS_mode();
#else
    /* FIPS is unavailable */
    tcn_ThrowException(e, "FIPS was not available to tcnative at build time. You will need to re-build tcnative against an OpenSSL with FIPS.");

    return 0;
#endif
}

TCN_IMPLEMENT_CALL(jint, SSL, fipsModeSet)(TCN_STDARGS, jint mode)
{
    int r = 0;
    UNREFERENCED(o);

#ifdef OPENSSL_FIPS
    if(1 != (r = (jint)FIPS_mode_set((int)mode))) {
      /* arrange to get a human-readable error message */
      unsigned long err = ERR_get_error();
      char msg[256];

      /* ERR_load_crypto_strings() already called in initialize() */

      ERR_error_string_n(err, msg, 256);

      tcn_ThrowException(e, msg);
    }
#else
    /* FIPS is unavailable */
    tcn_ThrowException(e, "FIPS was not available to tcnative at build time. You will need to re-build tcnative against an OpenSSL with FIPS.");
#endif

    return r;
}

/* OpenSSL Java Stream BIO */

typedef struct  {
    int            refcount;
    apr_pool_t     *pool;
    tcn_callback_t cb;
} BIO_JAVA;


static apr_status_t generic_bio_cleanup(void *data)
{
    BIO *b = (BIO *)data;

    if (b) {
        BIO_free(b);
    }
    return APR_SUCCESS;
}

void SSL_BIO_close(BIO *bi)
{
    if (bi == NULL)
        return;
    if (bi->ptr != NULL && (bi->flags & SSL_BIO_FLAG_CALLBACK)) {
        BIO_JAVA *j = (BIO_JAVA *)bi->ptr;
        j->refcount--;
        if (j->refcount == 0) {
            if (j->pool)
                apr_pool_cleanup_run(j->pool, bi, generic_bio_cleanup);
            else
                BIO_free(bi);
        }
    }
    else
        BIO_free(bi);
}

void SSL_BIO_doref(BIO *bi)
{
    if (bi == NULL)
        return;
    if (bi->ptr != NULL && (bi->flags & SSL_BIO_FLAG_CALLBACK)) {
        BIO_JAVA *j = (BIO_JAVA *)bi->ptr;
        j->refcount++;
    }
}


static int jbs_new(BIO *bi)
{
    BIO_JAVA *j;

    if ((j = OPENSSL_malloc(sizeof(BIO_JAVA))) == NULL)
        return 0;
    j->pool      = NULL;
    j->refcount  = 1;
    bi->shutdown = 1;
    bi->init     = 0;
    bi->num      = -1;
    bi->ptr      = (char *)j;

    return 1;
}

static int jbs_free(BIO *bi)
{
    if (bi == NULL)
        return 0;
    if (bi->ptr != NULL) {
        BIO_JAVA *j = (BIO_JAVA *)bi->ptr;
        if (bi->init) {
            JNIEnv   *e = NULL;
            bi->init = 0;
            tcn_get_java_env(&e);
            TCN_UNLOAD_CLASS(e, j->cb.obj);
        }
        OPENSSL_free(bi->ptr);
    }
    bi->ptr = NULL;
    return 1;
}

static int jbs_write(BIO *b, const char *in, int inl)
{
    jint ret = 0;
    if (b->init && in != NULL) {
        BIO_JAVA *j = (BIO_JAVA *)b->ptr;
        JNIEnv   *e = NULL;
        jbyteArray jb = (*e)->NewByteArray(e, inl);
        tcn_get_java_env(&e);
        if (!(*e)->ExceptionOccurred(e)) {
            (*e)->SetByteArrayRegion(e, jb, 0, inl, (jbyte *)in);
            ret = (*e)->CallIntMethod(e, j->cb.obj,
                                      j->cb.mid[0], jb);
            (*e)->ReleaseByteArrayElements(e, jb, (jbyte *)in, JNI_ABORT);
            (*e)->DeleteLocalRef(e, jb);
        }
    }
    return ret;
}

static int jbs_read(BIO *b, char *out, int outl)
{
    jint ret = 0;
    if (b->init && out != NULL) {
        BIO_JAVA *j = (BIO_JAVA *)b->ptr;
        JNIEnv   *e = NULL;
        jbyteArray jb = (*e)->NewByteArray(e, outl);
        tcn_get_java_env(&e);
        if (!(*e)->ExceptionOccurred(e)) {
            ret = (*e)->CallIntMethod(e, j->cb.obj,
                                      j->cb.mid[1], jb);
            if (ret > 0) {
                jbyte *jout = (*e)->GetPrimitiveArrayCritical(e, jb, NULL);
                memcpy(out, jout, ret);
                (*e)->ReleasePrimitiveArrayCritical(e, jb, jout, 0);
            }
            (*e)->DeleteLocalRef(e, jb);
        }
    }
    return ret;
}

static int jbs_puts(BIO *b, const char *in)
{
    int ret = 0;
    if (b->init && in != NULL) {
        BIO_JAVA *j = (BIO_JAVA *)b->ptr;
        JNIEnv   *e = NULL;
        tcn_get_java_env(&e);
        ret = (*e)->CallIntMethod(e, j->cb.obj,
                                  j->cb.mid[2],
                                  tcn_new_string(e, in));
    }
    return ret;
}

static int jbs_gets(BIO *b, char *out, int outl)
{
    int ret = 0;
    if (b->init && out != NULL) {
        BIO_JAVA *j = (BIO_JAVA *)b->ptr;
        JNIEnv   *e = NULL;
        jobject  o;
        tcn_get_java_env(&e);
        if ((o = (*e)->CallObjectMethod(e, j->cb.obj,
                            j->cb.mid[3], (jint)(outl - 1)))) {
            TCN_ALLOC_CSTRING(o);
            if (J2S(o)) {
                int l = (int)strlen(J2S(o));
                if (l < outl) {
                    strcpy(out, J2S(o));
                    ret = outl;
                }
            }
            TCN_FREE_CSTRING(o);
        }
    }
    return ret;
}

static long jbs_ctrl(BIO *b, int cmd, long num, void *ptr)
{
    return 0;
}

static BIO_METHOD jbs_methods = {
    BIO_TYPE_FILE,
    "Java Callback",
    jbs_write,
    jbs_read,
    jbs_puts,
    jbs_gets,
    jbs_ctrl,
    jbs_new,
    jbs_free,
    NULL
};

static BIO_METHOD *BIO_jbs()
{
    return(&jbs_methods);
}

TCN_IMPLEMENT_CALL(jlong, SSL, newBIO)(TCN_STDARGS, jlong pool,
                                       jobject callback)
{
    BIO *bio = NULL;
    BIO_JAVA *j;
    jclass cls;

    UNREFERENCED(o);

    if ((bio = BIO_new(BIO_jbs())) == NULL) {
        tcn_ThrowException(e, "Create BIO failed");
        goto init_failed;
    }
    j = (BIO_JAVA *)bio->ptr;
    if ((j = (BIO_JAVA *)bio->ptr) == NULL) {
        tcn_ThrowException(e, "Create BIO failed");
        goto init_failed;
    }
    j->pool = J2P(pool, apr_pool_t *);
    if (j->pool) {
        apr_pool_cleanup_register(j->pool, (const void *)bio,
                                  generic_bio_cleanup,
                                  apr_pool_cleanup_null);
    }

    cls = (*e)->GetObjectClass(e, callback);
    j->cb.mid[0] = (*e)->GetMethodID(e, cls, "write", "([B)I");
    j->cb.mid[1] = (*e)->GetMethodID(e, cls, "read",  "([B)I");
    j->cb.mid[2] = (*e)->GetMethodID(e, cls, "puts",  "(Ljava/lang/String;)I");
    j->cb.mid[3] = (*e)->GetMethodID(e, cls, "gets",  "(I)Ljava/lang/String;");
    /* TODO: Check if method id's are valid */
    j->cb.obj    = (*e)->NewGlobalRef(e, callback);

    bio->init  = 1;
    bio->flags = SSL_BIO_FLAG_CALLBACK;
    return P2J(bio);
init_failed:
    return 0;
}

TCN_IMPLEMENT_CALL(jint, SSL, closeBIO)(TCN_STDARGS, jlong bio)
{
    BIO *b = J2P(bio, BIO *);
    UNREFERENCED_STDARGS;
    SSL_BIO_close(b);
    return APR_SUCCESS;
}

TCN_IMPLEMENT_CALL(void, SSL, setPasswordCallback)(TCN_STDARGS,
                                                   jobject callback)
{
    jclass cls;

    UNREFERENCED(o);
    if (tcn_password_callback.cb.obj) {
        TCN_UNLOAD_CLASS(e,
                         tcn_password_callback.cb.obj);
    }
    cls = (*e)->GetObjectClass(e, callback);
    tcn_password_callback.cb.mid[0] = (*e)->GetMethodID(e, cls, "callback",
                           "(Ljava/lang/String;)Ljava/lang/String;");
    /* TODO: Check if method id is valid */
    tcn_password_callback.cb.obj    = (*e)->NewGlobalRef(e, callback);

}

TCN_IMPLEMENT_CALL(void, SSL, setPassword)(TCN_STDARGS, jstring password)
{
    TCN_ALLOC_CSTRING(password);
    UNREFERENCED(o);
    if (J2S(password)) {
        strncpy(tcn_password_callback.password, J2S(password), SSL_MAX_PASSWORD_LEN);
        tcn_password_callback.password[SSL_MAX_PASSWORD_LEN-1] = '\0';
    }
    TCN_FREE_CSTRING(password);
}

TCN_IMPLEMENT_CALL(jboolean, SSL, generateRSATempKey)(TCN_STDARGS, jint idx)
{
    int r = 1;
    UNREFERENCED_STDARGS;
    SSL_TMP_KEY_FREE(RSA, idx);
    switch (idx) {
        case SSL_TMP_KEY_RSA_512:
            r = SSL_TMP_KEY_INIT_RSA(512);
        break;
        case SSL_TMP_KEY_RSA_1024:
            r = SSL_TMP_KEY_INIT_RSA(1024);
        break;
        case SSL_TMP_KEY_RSA_2048:
            r = SSL_TMP_KEY_INIT_RSA(2048);
        break;
        case SSL_TMP_KEY_RSA_4096:
            r = SSL_TMP_KEY_INIT_RSA(4096);
        break;
    }
    return r ? JNI_FALSE : JNI_TRUE;
}

TCN_IMPLEMENT_CALL(jboolean, SSL, loadDSATempKey)(TCN_STDARGS, jint idx,
                                                  jstring file)
{
    jboolean r = JNI_FALSE;
    TCN_ALLOC_CSTRING(file);
    DH *dh;
    UNREFERENCED(o);

    if (!J2S(file))
        return JNI_FALSE;
    SSL_TMP_KEY_FREE(DSA, idx);
    if ((dh = SSL_dh_get_param_from_file(J2S(file)))) {
        SSL_temp_keys[idx] = dh;
        r = JNI_TRUE;
    }
    TCN_FREE_CSTRING(file);
    return r;
}

TCN_IMPLEMENT_CALL(jstring, SSL, getLastError)(TCN_STDARGS)
{
    char buf[256];
    UNREFERENCED(o);
    ERR_error_string(ERR_get_error(), buf);
    return tcn_new_string(e, buf);
}

TCN_IMPLEMENT_CALL(jboolean, SSL, hasOp)(TCN_STDARGS, jint op)
{
    return op == (op & supported_ssl_opts) ? JNI_TRUE : JNI_FALSE;
}

#else
/* OpenSSL is not supported.
 * Create empty stubs.
 */

TCN_IMPLEMENT_CALL(jint, SSL, version)(TCN_STDARGS)
{
    UNREFERENCED_STDARGS;
    return 0;
}

TCN_IMPLEMENT_CALL(jstring, SSL, versionString)(TCN_STDARGS)
{
    UNREFERENCED_STDARGS;
    return NULL;
}

TCN_IMPLEMENT_CALL(jint, SSL, initialize)(TCN_STDARGS, jstring engine)
{
    UNREFERENCED(o);
    UNREFERENCED(engine);
    tcn_ThrowAPRException(e, APR_ENOTIMPL);
    return (jint)APR_ENOTIMPL;
}

TCN_IMPLEMENT_CALL(jboolean, SSL, randLoad)(TCN_STDARGS, jstring file)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(file);
    return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jboolean, SSL, randSave)(TCN_STDARGS, jstring file)
{
    UNREFERENCED_STDARGS;
    return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jboolean, SSL, randMake)(TCN_STDARGS, jstring file,
                                            jint length, jboolean base64)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(file);
    UNREFERENCED(length);
    UNREFERENCED(base64);
    return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(void, SSL, randSet)(TCN_STDARGS, jstring file)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(file);
}

TCN_IMPLEMENT_CALL(jint, SSL, fipsModeSet)(TCN_STDARGS, jint mode)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(mode);

    return 0;
}

TCN_IMPLEMENT_CALL(jlong, SSL, newBIO)(TCN_STDARGS, jlong pool,
                                       jobject callback)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(pool);
    UNREFERENCED(callback);
    return 0;
}

TCN_IMPLEMENT_CALL(jint, SSL, closeBIO)(TCN_STDARGS, jlong bio)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(bio);
    return (jint)APR_ENOTIMPL;
}

TCN_IMPLEMENT_CALL(void, SSL, setPasswordCallback)(TCN_STDARGS,
                                                   jobject callback)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(callback);
}

TCN_IMPLEMENT_CALL(void, SSL, setPassword)(TCN_STDARGS, jstring password)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(password);
}

TCN_IMPLEMENT_CALL(jboolean, SSL, generateRSATempKey)(TCN_STDARGS, jint idx)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(idx);
    return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jboolean, SSL, loadDSATempKey)(TCN_STDARGS, jint idx,
                                                  jstring file)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(idx);
    UNREFERENCED(file);
    return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jstring, SSL, getLastError)(TCN_STDARGS)
{
    UNREFERENCED_STDARGS;
    return NULL;
}

TCN_IMPLEMENT_CALL(jboolean, SSL, hasOp)(TCN_STDARGS, jint op)
{
    UNREFERENCED_STDARGS;
    UNREFERENCED(op);
    return JNI_FALSE;
}
#endif
