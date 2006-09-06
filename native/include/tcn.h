/* Copyright 2000-2005 The Apache Software Foundation
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

#ifndef TCN_H
#define TCN_H

#include "apr.h"
#include "apr_general.h"
#include "apr_pools.h"
#include "apr_portable.h"
#include "apr_network_io.h"
#include "apr_strings.h"

#ifndef APR_HAS_THREADS
#error "Missing APR_HAS_THREADS support from APR."
#endif

#if defined(DEBUG) || defined(_DEBUG)
/* On -DDEBUG use the statistics */
#ifndef TCN_DO_STATISTICS
#define TCN_DO_STATISTICS
#endif
#endif
#include <stdio.h>
#include <stdlib.h>
#if defined(_WIN32) && !defined(__CYGWIN__)
#include <process.h>
#else
#include <unistd.h>
#endif

#include "tcn_api.h"


#if defined(_DEBUG) || defined(DEBUG)
#include <assert.h>
#define TCN_ASSERT(x)  assert((x))
#else
#define TCN_ASSERT(x) (void)0
#endif

#ifndef APR_MAX_IOVEC_SIZE
#define APR_MAX_IOVEC_SIZE 1024
#endif

#define TCN_TIMEUP      APR_OS_START_USERERR + 1
#define TCN_EAGAIN      APR_OS_START_USERERR + 2
#define TCN_EINTR       APR_OS_START_USERERR + 3
#define TCN_EINPROGRESS APR_OS_START_USERERR + 4
#define TCN_ETIMEDOUT   APR_OS_START_USERERR + 5

#define TCN_LOG_EMERG  1
#define TCN_LOG_ERROR  2
#define TCN_LOG_NOTICE 3
#define TCN_LOG_WARN   4
#define TCN_LOG_INFO   5
#define TCN_LOG_DEBUG  6

#define TCN_ERROR_WRAP(E)                   \
    if (APR_STATUS_IS_TIMEUP(E))            \
        (E) = TCN_TIMEUP;                   \
    else if (APR_STATUS_IS_EAGAIN(E))       \
        (E) = TCN_EAGAIN;                   \
    else if (APR_STATUS_IS_EINTR(E))        \
        (E) = TCN_EINTR;                    \
    else if (APR_STATUS_IS_EINPROGRESS(E))  \
        (E) = TCN_EINPROGRESS;              \
    else if (APR_STATUS_IS_ETIMEDOUT(E))    \
        (E) = TCN_ETIMEDOUT;                \
    else                                    \
        (E) = (E)

#define TCN_CLASS_PATH  "org/apache/tomcat/jni/"
#define TCN_FINFO_CLASS TCN_CLASS_PATH "FileInfo"
#define TCN_AINFO_CLASS TCN_CLASS_PATH "Sockaddr"
#define TCN_ERROR_CLASS TCN_CLASS_PATH "Error"
#define TCN_PARENT_IDE  "TCN_PARENT_ID"

#define UNREFERENCED(P)      (P) = (P)
#define UNREFERENCED_STDARGS e = e; o = o
#ifdef WIN32
#define LLT(X) (X)
#else
#define LLT(X) ((long)(X))
#endif
#define P2J(P)          ((jlong)LLT(P))
#define J2P(P, T)       ((T)LLT((jlong)P))
/* On stack buffer size */
#define TCN_BUFFER_SZ   8192
#define TCN_STDARGS     JNIEnv *e, jobject o
#define TCN_IMPARGS     JNIEnv *e, jobject o, void *sock
#define TCN_IMPCALL(X)  e, o, X->opaque

#define TCN_IMPLEMENT_CALL(RT, CL, FN)  \
    JNIEXPORT RT JNICALL Java_org_apache_tomcat_jni_##CL##_##FN

#define TCN_IMPLEMENT_METHOD(RT, FN)    \
    static RT method_##FN

#define TCN_GETNET_METHOD(FN)  method_##FN

#define TCN_SOCKET_UNKNOWN  0
#define TCN_SOCKET_APR      1
#define TCN_SOCKET_SSL      2
#define TCN_SOCKET_UNIX     3
#define TCN_SOCKET_NTPIPE   4

#define TCN_SOCKET_GET_POOL 0
#define TCN_SOCKET_GET_IMPL 1
#define TCN_SOCKET_GET_APRS 2
#define TCN_SOCKET_GET_TYPE 3

typedef struct {
    int type;
    apr_status_t (*cleanup)(void *);
    apr_status_t (APR_THREAD_FUNC *close) (apr_socket_t *);
    apr_status_t (APR_THREAD_FUNC *shutdown) (apr_socket_t *, apr_shutdown_how_e);
    apr_status_t (APR_THREAD_FUNC *opt_get)(apr_socket_t *, apr_int32_t, apr_int32_t *);
    apr_status_t (APR_THREAD_FUNC *opt_set)(apr_socket_t *, apr_int32_t, apr_int32_t);
    apr_status_t (APR_THREAD_FUNC *timeout_get)(apr_socket_t *, apr_interval_time_t *);
    apr_status_t (APR_THREAD_FUNC *timeout_set)(apr_socket_t *, apr_interval_time_t);
    apr_status_t (APR_THREAD_FUNC *send) (apr_socket_t *, const char *, apr_size_t *);
    apr_status_t (APR_THREAD_FUNC *sendv)(apr_socket_t *, const struct iovec *, apr_int32_t, apr_size_t *);
    apr_status_t (APR_THREAD_FUNC *recv) (apr_socket_t *, char *, apr_size_t *);
} tcn_nlayer_t;

typedef struct {
    apr_pool_t   *pool;
    apr_socket_t *sock;
    void         *opaque;
    char         *jsbbuff;
    char         *jrbbuff;
    tcn_nlayer_t *net;
} tcn_socket_t;

/* Private helper functions */
void            tcn_Throw(JNIEnv *, const char *, ...);
void            tcn_ThrowException(JNIEnv *, const char *);
void            tcn_ThrowMemoryException(JNIEnv *, const char *, int, const char *);
void            tcn_ThrowAPRException(JNIEnv *, apr_status_t);
jstring         tcn_new_string(JNIEnv *, const char *);
jstring         tcn_new_stringn(JNIEnv *, const char *, size_t);
jbyteArray      tcn_new_arrayb(JNIEnv *, const unsigned char *, size_t);
jobjectArray    tcn_new_arrays(JNIEnv *env, size_t len);
char           *tcn_get_string(JNIEnv *, jstring);
char           *tcn_strdup(JNIEnv *, jstring);
char           *tcn_pstrdup(JNIEnv *, jstring, apr_pool_t *);
apr_status_t    tcn_load_finfo_class(JNIEnv *, jclass);
apr_status_t    tcn_load_ainfo_class(JNIEnv *, jclass);

#define J2S(V)  c##V
#define J2L(V)  p##V

#define J2T(T) (apr_time_t)((T))

#define TCN_BEGIN_MACRO     if (1) {
#define TCN_END_MACRO       } else (void)(0)

#define TCN_ALLOC_CSTRING(V)     \
    const char *c##V = V ? (const char *)((*e)->GetStringUTFChars(e, V, 0)) : NULL

#define TCN_FREE_CSTRING(V)      \
    if (c##V) (*e)->ReleaseStringUTFChars(e, V, c##V)

#define TCN_ALLOC_JSTRING(V)     \
    char *c##V = tcn_get_string(e, (V))

#define AJP_TO_JSTRING(V)   (*e)->NewStringUTF((e), (V))

#define TCN_FREE_JSTRING(V)      \
    TCN_BEGIN_MACRO              \
        if (c##V)                \
            free(c##V);          \
    TCN_END_MACRO

#define TCN_CHECK_ALLOCATED(x)                              \
        if (x == NULL) {                                    \
            tcn_ThrowMemoryException(e, __FILE__, __LINE__, \
            "APR memory allocation failed");                \
            goto cleanup;                                   \
        } else (void)(0)

#define TCN_THROW_IF_ERR(x, r)                  \
    TCN_BEGIN_MACRO                             \
        apr_status_t R = (x);                   \
        if (R != APR_SUCCESS) {                 \
            tcn_ThrowAPRException(e, R);        \
            (r) = 0;                            \
            goto cleanup;                       \
        }                                       \
    TCN_END_MACRO

#define TCN_THROW_OS_ERROR(E)   \
    tcn_ThrowAPRException((E), apr_get_os_error())

#define TCN_LOAD_CLASS(E, C, N, R)                  \
    TCN_BEGIN_MACRO                                 \
        jclass _##C = (*(E))->FindClass((E), N);    \
        if (_##C == NULL) {                         \
            (*(E))->ExceptionClear((E));            \
            return R;                               \
        }                                           \
        C = (*(E))->NewGlobalRef((E), _##C);        \
        (*(E))->DeleteLocalRef((E), _##C);          \
    TCN_END_MACRO

#define TCN_UNLOAD_CLASS(E, C)                      \
        (*(E))->DeleteGlobalRef((E), (C))

#define TCN_IS_NULL(E, O)                           \
        ((*(E))->IsSameObject((E), (O), NULL) == JNI_TRUE)

#define TCN_GET_METHOD(E, C, M, N, S, R)            \
    TCN_BEGIN_MACRO                                 \
        M = (*(E))->GetMethodID((E), C, N, S);      \
        if (M == NULL) {                            \
            return R;                               \
        }                                           \
    TCN_END_MACRO

#define TCN_MAX_METHODS 8

typedef struct {
    jobject     obj;
    jmethodID   mid[TCN_MAX_METHODS];
    void        *opaque;
} tcn_callback_t;

#define TCN_MIN(a, b) ((a) < (b) ? (a) : (b))
#define TCN_MAX(a, b) ((a) > (b) ? (a) : (b))

#ifdef WIN32
#define TCN_ALLOC_WSTRING(V)     \
    jsize wl##V = (*e)->GetStringLength(e, V);   \
    const jchar *ws##V = V ? (const jchar *)((*e)->GetStringChars(e, V, 0)) : NULL; \
    jchar *w##V = NULL

#define TCN_INIT_WSTRING(V)                                     \
        w##V = (jchar *)malloc((wl##V + 1) * sizeof(jchar));    \
        wcsncpy(w##V, ws##V, wl##V);                        \
        w##V[wl##V] = 0

#define TCN_FREE_WSTRING(V)      \
    if (ws##V) (*e)->ReleaseStringChars(e, V, ws##V); \
    if (ws##V) free (w##V)

#define J2W(V)  w##V

#endif

#if  !APR_HAVE_IPV6
#define APR_INET6 APR_INET
#endif

#define GET_S_FAMILY(T, F)           \
    if (F == 0) T = APR_UNSPEC;      \
    else if (F == 1) T = APR_INET;   \
    else if (F == 2) T = APR_INET6;  \
    else T = F

#define GET_S_TYPE(T, F)             \
    if (F == 0) T = SOCK_STREAM;     \
    else if (F == 1) T = SOCK_DGRAM; \
    else T = F

#endif /* TCN_H */
