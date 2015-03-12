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
#include "apr_version.h"
#include "apr_file_io.h"
#include "apr_mmap.h"
#include "apr_atomic.h"

#include "tcn_version.h"

#ifdef TCN_DO_STATISTICS
extern void sp_poll_dump_statistics();
extern void sp_network_dump_statistics();
extern void ssl_network_dump_statistics();
#endif

apr_pool_t *tcn_global_pool = NULL;
static JavaVM     *tcn_global_vm = NULL;

static jclass    jString_class;
static jclass    jFinfo_class;
static jclass    jAinfo_class;
static jmethodID jString_init;
static jmethodID jString_getBytes;

int tcn_parent_pid = 0;

/* Called by the JVM when APR_JAVA is loaded */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv *env;
    apr_version_t apv;
    int apvn;

    UNREFERENCED(reserved);
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_4)) {
        return JNI_ERR;
    }
    tcn_global_vm = vm;

    /* Before doing anything else check if we have a valid
     * APR version.
     */
    apr_version(&apv);
    apvn = apv.major * 1000 + apv.minor * 100 + apv.patch;
    if (apvn < 1201) {
        tcn_Throw(env, "Unupported APR version (%s)",
                  apr_version_string());
        return JNI_ERR;
    }


    /* Initialize global java.lang.String class */
    TCN_LOAD_CLASS(env, jString_class, "java/lang/String", JNI_ERR);
    TCN_LOAD_CLASS(env, jFinfo_class, TCN_FINFO_CLASS, JNI_ERR);
    TCN_LOAD_CLASS(env, jAinfo_class, TCN_AINFO_CLASS, JNI_ERR);

    TCN_GET_METHOD(env, jString_class, jString_init,
                   "<init>", "([B)V", JNI_ERR);
    TCN_GET_METHOD(env, jString_class, jString_getBytes,
                   "getBytes", "()[B", JNI_ERR);

    if(tcn_load_finfo_class(env, jFinfo_class) != APR_SUCCESS)
        return JNI_ERR;
    if(tcn_load_ainfo_class(env, jAinfo_class) != APR_SUCCESS)
        return JNI_ERR;
#ifdef WIN32
    {
        char *ppid = getenv(TCN_PARENT_IDE);
        if (ppid)
            tcn_parent_pid = atoi(ppid);
    }
#else
    tcn_parent_pid = getppid();
#endif

    return  JNI_VERSION_1_4;
}


/* Called by the JVM before the APR_JAVA is unloaded */
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved)
{
    JNIEnv *env;

    UNREFERENCED(reserved);

    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_2)) {
        return;
    }
    if (tcn_global_pool) {
        TCN_UNLOAD_CLASS(env, jString_class);
        TCN_UNLOAD_CLASS(env, jFinfo_class);
        TCN_UNLOAD_CLASS(env, jAinfo_class);
        apr_terminate();
    }
}

jstring tcn_new_stringn(JNIEnv *env, const char *str, size_t l)
{
    jstring result;
    jbyteArray bytes = 0;

    if (!str)
        return NULL;
    if ((*env)->EnsureLocalCapacity(env, 2) < 0) {
        return NULL; /* out of memory error */
    }
    bytes = (*env)->NewByteArray(env, l);
    if (bytes != NULL) {
        (*env)->SetByteArrayRegion(env, bytes, 0, l, (jbyte *)str);
        result = (*env)->NewObject(env, jString_class, jString_init, bytes);
        (*env)->DeleteLocalRef(env, bytes);
        return result;
    } /* else fall through */
    return NULL;
}

jbyteArray tcn_new_arrayb(JNIEnv *env, const unsigned char *data, size_t len)
{
    jbyteArray bytes = (*env)->NewByteArray(env, (jsize)len);
    if (bytes != NULL) {
        (*env)->SetByteArrayRegion(env, bytes, 0, (jint)len, (jbyte *)data);
    }
    return bytes;
}

jobjectArray tcn_new_arrays(JNIEnv *env, size_t len)
{
    return (*env)->NewObjectArray(env, (jsize)len, jString_class, NULL);
}

jstring tcn_new_string(JNIEnv *env, const char *str)
{
    if (!str)
        return NULL;
    else
        return (*env)->NewStringUTF(env, str);
}

char *tcn_get_string(JNIEnv *env, jstring jstr)
{
    jbyteArray bytes = NULL;
    jthrowable exc;
    char *result = NULL;

    if ((*env)->EnsureLocalCapacity(env, 2) < 0) {
        return NULL; /* out of memory error */
    }
    bytes = (*env)->CallObjectMethod(env, jstr, jString_getBytes);
    exc = (*env)->ExceptionOccurred(env);
    if (!exc) {
        jint len = (*env)->GetArrayLength(env, bytes);
        result = (char *)malloc(len + 1);
        if (result == NULL) {
            TCN_THROW_OS_ERROR(env);
            (*env)->DeleteLocalRef(env, bytes);
            return 0;
        }
        (*env)->GetByteArrayRegion(env, bytes, 0, len, (jbyte *)result);
        result[len] = '\0'; /* NULL-terminate */
    }
    else {
        (*env)->DeleteLocalRef(env, exc);
    }
    (*env)->DeleteLocalRef(env, bytes);

    return result;
}

char *tcn_strdup(JNIEnv *env, jstring jstr)
{
    char *result = NULL;
    const char *cjstr;

    cjstr = (const char *)((*env)->GetStringUTFChars(env, jstr, 0));
    if (cjstr) {
        result = strdup(cjstr);
        (*env)->ReleaseStringUTFChars(env, jstr, cjstr);
    }
    return result;
}

char *tcn_pstrdup(JNIEnv *env, jstring jstr, apr_pool_t *pool)
{
    char *result = NULL;
    const char *cjstr;

    cjstr = (const char *)((*env)->GetStringUTFChars(env, jstr, 0));
    if (cjstr) {
        result = apr_pstrdup(pool, cjstr);
        (*env)->ReleaseStringUTFChars(env, jstr, cjstr);
    }
    return result;
}

TCN_IMPLEMENT_CALL(jboolean, Library, initialize)(TCN_STDARGS)
{

    UNREFERENCED_STDARGS;
    if (!tcn_global_pool) {
        apr_initialize();
        if (apr_pool_create(&tcn_global_pool, NULL) != APR_SUCCESS) {
            return JNI_FALSE;
        }
        apr_atomic_init(tcn_global_pool);
    }
    return JNI_TRUE;
}

TCN_IMPLEMENT_CALL(void, Library, terminate)(TCN_STDARGS)
{

    UNREFERENCED_STDARGS;
    if (tcn_global_pool) {        
        apr_pool_t *p = tcn_global_pool;
        tcn_global_pool = NULL;
#ifdef TCN_DO_STATISTICS
        fprintf(stderr, "APR Statistical data ....\n");
#endif
        apr_pool_destroy(p);
#ifdef TCN_DO_STATISTICS
        sp_poll_dump_statistics();
        sp_network_dump_statistics();
        ssl_network_dump_statistics();
        fprintf(stderr, "APR Terminated\n");
#endif
        apr_terminate();
    }
}

TCN_IMPLEMENT_CALL(jlong, Library, globalPool)(TCN_STDARGS)
{
    UNREFERENCED_STDARGS;
    return P2J(tcn_global_pool);
}

TCN_IMPLEMENT_CALL(jint, Library, version)(TCN_STDARGS, jint what)
{
    apr_version_t apv;

    UNREFERENCED_STDARGS;
    apr_version(&apv);

    switch (what) {
        case 0x01:
            return TCN_MAJOR_VERSION;
        break;
        case 0x02:
            return TCN_MINOR_VERSION;
        break;
        case 0x03:
            return TCN_PATCH_VERSION;
        break;
        case 0x04:
            return TCN_IS_DEV_VERSION;
        break;
        case 0x11:
            return apv.major;
        break;
        case 0x12:
            return apv.minor;
        break;
        case 0x13:
            return apv.patch;
        break;
        case 0x14:
            return apv.is_dev;
        break;
    }
    return 0;
}

TCN_IMPLEMENT_CALL(jstring, Library, versionString)(TCN_STDARGS)
{
    UNREFERENCED(o);
    return AJP_TO_JSTRING(TCN_VERSION_STRING);
}

TCN_IMPLEMENT_CALL(jstring, Library, aprVersionString)(TCN_STDARGS)
{
    UNREFERENCED(o);
    return AJP_TO_JSTRING(apr_version_string());
}

TCN_IMPLEMENT_CALL(jboolean, Library, has)(TCN_STDARGS, jint what)
{
    jboolean rv = JNI_FALSE;
    UNREFERENCED_STDARGS;
    switch (what) {
        case 0:
#if APR_HAVE_IPV6
            rv = JNI_TRUE;
#endif
        break;
        case 1:
#if APR_HAS_SHARED_MEMORY
            rv = JNI_TRUE;
#endif
        break;
        case 2:
#if APR_HAS_THREADS
            rv = JNI_TRUE;
#endif
        break;
        case 3:
#if APR_HAS_SENDFILE
            rv = JNI_TRUE;
#endif
        break;
        case 4:
#if APR_HAS_MMAP
            rv = JNI_TRUE;
#endif
        break;
        case 5:
#if APR_HAS_FORK
            rv = JNI_TRUE;
#endif
        break;
        case 6:
#if APR_HAS_RANDOM
            rv = JNI_TRUE;
#endif
        break;
        case 7:
#if APR_HAS_OTHER_CHILD
            rv = JNI_TRUE;
#endif
        break;
        case 8:
#if APR_HAS_DSO
            rv = JNI_TRUE;
#endif
        break;
        case 9:
#if APR_HAS_SO_ACCEPTFILTER
            rv = JNI_TRUE;
#endif
        break;
        case 10:
#if APR_HAS_UNICODE_FS
            rv = JNI_TRUE;
#endif
        break;
        case 11:
#if APR_HAS_PROC_INVOKED
            rv = JNI_TRUE;
#endif
        break;
        case 12:
#if APR_HAS_USER
            rv = JNI_TRUE;
#endif
        break;
        case 13:
#if APR_HAS_LARGE_FILES
            rv = JNI_TRUE;
#endif
        break;
        case 14:
#if APR_HAS_XTHREAD_FILES
            rv = JNI_TRUE;
#endif
        break;
        case 15:
#if APR_HAS_OS_UUID
            rv = JNI_TRUE;
#endif
        break;
        case 16:
#if APR_IS_BIGENDIAN
            rv = JNI_TRUE;
#endif
        break;

        case 17:
#if APR_FILES_AS_SOCKETS
            rv = JNI_TRUE;
#endif
        break;
        case 18:
#if APR_CHARSET_EBCDIC
            rv = JNI_TRUE;
#endif
        break;
        case 19:
#if APR_TCP_NODELAY_INHERITED
            rv = JNI_TRUE;
#endif
        break;
        case 20:
#if APR_O_NONBLOCK_INHERITED
            rv = JNI_TRUE;
#endif
        break;
    }
    return rv;
}

TCN_IMPLEMENT_CALL(jint, Library, size)(TCN_STDARGS, jint what)
{

    UNREFERENCED_STDARGS;

    switch (what) {
        case 1:
            return APR_SIZEOF_VOIDP;
        break;
        case 2:
            return APR_PATH_MAX;
        break;
        case 3:
            return APRMAXHOSTLEN;
        break;
        case 4:
            return APR_MAX_IOVEC_SIZE;
        break;
        case 5:
            return APR_MAX_SECS_TO_LINGER;
        break;
        case 6:
            return APR_MMAP_THRESHOLD;
        break;
        case 7:
            return APR_MMAP_LIMIT;
        break;

    }
    return 0;
}

apr_pool_t *tcn_get_global_pool()
{
    if (!tcn_global_pool) {
        if (apr_pool_create(&tcn_global_pool, NULL) != APR_SUCCESS) {
            return NULL;
        }
        apr_atomic_init(tcn_global_pool);
    }
    return tcn_global_pool;
}

jclass tcn_get_string_class()
{
    return jString_class;
}

JavaVM * tcn_get_java_vm()
{
    return tcn_global_vm;
}

jint tcn_get_java_env(JNIEnv **env)
{
    if ((*tcn_global_vm)->GetEnv(tcn_global_vm, (void **)env,
                                 JNI_VERSION_1_4)) {
        return JNI_ERR;
    }
    return JNI_OK;
}
