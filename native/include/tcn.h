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


#ifndef TCN_H
#define TCN_H

#include <jni.h>

#ifdef min
#undef min
#endif
#define min(a, b)  ((a) < (b) ? (a) : (b))

#ifndef APR_MAX_IOVEC_SIZE
#define APR_MAX_IOVEC_SIZE 1024
#endif

#define TCN_FINFO_CLASS "org/apache/tomcat/jni/FileInfo"
#define TCN_AINFO_CLASS "org/apache/tomcat/jni/Sockaddr"
#define TCN_ERROR_CLASS "org/apache/tomcat/jni/Error"

#define UNREFERENCED(P) (P)
#define P2J(P)          ((jlong)(long)(void *)P)
#define J2P(P, T)       ((T)(void *)(long)P)
/* On stack buffer size */
#define TCN_BUFFER_SZ   8192
#define TCN_STDARGS     JNIEnv *e, jobject o
#define UNREFERENCED_STDARGS    { e; o; }

#define TCN_IMPLEMENT_CALL(RT, CL, FN)  \
    JNIEXPORT RT JNICALL Java_org_apache_tomcat_jni_##CL##_##FN


void tcn_Throw(JNIEnv *env, const char *cname, const char *msg);
void tcn_ThrowException(JNIEnv *env, const char *msg);
void tcn_ThrowAPRException(JNIEnv *env, apr_status_t err);
jstring tcn_new_string(JNIEnv *env, const char *str);
char *tcn_get_string(JNIEnv *env, jstring jstr);
char *tcn_dup_string(JNIEnv *env, jstring jstr);
apr_status_t tcn_load_finfo_class(JNIEnv *env);
apr_status_t tcn_load_ainfo_class(JNIEnv *env);

#define J2S(V)  c##V
#define J2L(V)  p##V

#define J2T(T) (apr_time_t)((T))

#if 1
#define TCN_BEGIN_MACRO     {
#define TCN_END_MACRO       } (void *)(0)
#else
#define TCN_BEGIN_MACRO     do {
#define TCN_END_MACRO       } while (0)
#endif

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


struct tcn_callback {
    JNIEnv      *env;
    jobject     obj;
    jmethodID   mid;
    void        *opaque;
};

typedef struct tcn_callback tcn_callback_t;


#endif /* TCN_H */
