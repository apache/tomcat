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

#include "apr.h"
#include "apr_pools.h"
#include "tcn.h"

#include "apr_file_io.h"
#include "apr_network_io.h"

#define DECLARE_FINFO_FIELD(name) static jfieldID _fid##name = NULL
#define FINFO_FIELD(name)         _fid##name

#define GET_FINFO_I(N)      \
    _fid##N = (*e)->GetFieldID(e, finfo, #N, "I");  \
    if (_fid##N == NULL) {                          \
        (*e)->ExceptionClear(e);                    \
        goto cleanup;                               \
    } (void *)(0)

#define GET_FINFO_J(N)      \
    _fid##N = (*e)->GetFieldID(e, finfo, #N, "J");  \
    if (_fid##N == NULL) {                          \
        (*e)->ExceptionClear(e);                    \
        goto cleanup;                               \
    } (void *)(0)

#define GET_FINFO_S(N)      \
    _fid##N = (*e)->GetFieldID(e, finfo, #N,        \
                             "Ljava/lang/String;"); \
    if (_fid##N == NULL) {                          \
        (*e)->ExceptionClear(e);                    \
        goto cleanup;                               \
    } (void *)(0)

#define SET_FINFO_I(N, V)  \
    (*e)->SetIntField(e, obj, _fid##N, (jint)(V))

#define SET_FINFO_J(N, V)  \
    (*e)->SetLongField(e, obj, _fid##N, (jlong)(V))

#define SET_FINFO_S(N, V)                 \
    (*e)->SetObjectField(e, obj, _fid##N, \
        (V) ? AJP_TO_JSTRING((V)) : NULL)


#define DECLARE_AINFO_FIELD(name) static jfieldID _aid##name = NULL
#define AINFO_FIELD(name)         _aid##name

#define GET_AINFO_I(N)      \
    _aid##N = (*e)->GetFieldID(e, ainfo, #N, "I");  \
    if (_aid##N == NULL) {                          \
        (*e)->ExceptionClear(e);                    \
        goto cleanup;                               \
    } (void *)(0)

#define GET_AINFO_J(N)      \
    _aid##N = (*e)->GetFieldID(e, ainfo, #N, "J");  \
    if (_aid##N == NULL) {                          \
        (*e)->ExceptionClear(e);                    \
        goto cleanup;                               \
    } (void *)(0)

#define GET_AINFO_S(N)      \
    _aid##N = (*e)->GetFieldID(e, ainfo, #N,        \
                             "Ljava/lang/String;"); \
    if (_aid##N == NULL) {                          \
        (*e)->ExceptionClear(e);                    \
        goto cleanup;                               \
    } (void *)(0)

#define SET_AINFO_I(N, V)  \
    (*e)->SetIntField(e, obj, _aid##N, (jint)(V))

#define SET_AINFO_J(N, V)  \
    (*e)->SetLongField(e, obj, _aid##N, (jlong)(V))

#define SET_AINFO_S(N, V)                 \
    (*e)->SetObjectField(e, obj, _aid##N, \
        (V) ? AJP_TO_JSTRING((V)) : NULL)


DECLARE_FINFO_FIELD(pool);
DECLARE_FINFO_FIELD(valid);
DECLARE_FINFO_FIELD(protection);
DECLARE_FINFO_FIELD(filetype);
DECLARE_FINFO_FIELD(user);
DECLARE_FINFO_FIELD(group);
DECLARE_FINFO_FIELD(inode);
DECLARE_FINFO_FIELD(device);
DECLARE_FINFO_FIELD(nlink);
DECLARE_FINFO_FIELD(size);
DECLARE_FINFO_FIELD(csize);
DECLARE_FINFO_FIELD(atime);
DECLARE_FINFO_FIELD(mtime);
DECLARE_FINFO_FIELD(ctime);
DECLARE_FINFO_FIELD(fname);
DECLARE_FINFO_FIELD(name);
DECLARE_FINFO_FIELD(filehand);

DECLARE_AINFO_FIELD(pool);
DECLARE_AINFO_FIELD(hostname);
DECLARE_AINFO_FIELD(servname);
DECLARE_AINFO_FIELD(port);
DECLARE_AINFO_FIELD(family);
DECLARE_AINFO_FIELD(salen);
DECLARE_AINFO_FIELD(ipaddr_len);
DECLARE_AINFO_FIELD(addr_str_len);
DECLARE_AINFO_FIELD(ipaddr_ptr);
DECLARE_AINFO_FIELD(next);

static int finfo_class_initialized = 0;
static int ainfo_class_initialized = 0;

apr_status_t tcn_load_finfo_class(JNIEnv *e)
{
    jclass finfo;

    finfo = (*e)->FindClass(e, TCN_FINFO_CLASS);
    if (finfo == NULL) {
        (*e)->ExceptionClear(e);
        return APR_EGENERAL;
    }

    GET_FINFO_J(pool);
    GET_FINFO_I(valid);
    GET_FINFO_I(protection);
    GET_FINFO_I(filetype);
    GET_FINFO_I(user);
    GET_FINFO_I(group);
    GET_FINFO_I(inode);
    GET_FINFO_I(device);
    GET_FINFO_I(nlink);
    GET_FINFO_J(size);
    GET_FINFO_J(csize);
    GET_FINFO_J(atime);
    GET_FINFO_J(mtime);
    GET_FINFO_J(ctime);
    GET_FINFO_S(fname);
    GET_FINFO_S(name);
    GET_FINFO_J(filehand);

    finfo_class_initialized = 1;
cleanup:
    (*e)->DeleteLocalRef(e, finfo);
    return APR_SUCCESS;
}

apr_status_t tcn_load_ainfo_class(JNIEnv *e)
{
    jclass ainfo;

    ainfo = (*e)->FindClass(e, TCN_AINFO_CLASS);
    if (ainfo == NULL) {
        (*e)->ExceptionClear(e);
        return APR_EGENERAL;
    }

    GET_AINFO_J(pool);
    GET_AINFO_S(hostname);
    GET_AINFO_S(servname);
    GET_AINFO_I(port);
    GET_AINFO_I(family);
    GET_AINFO_I(salen);
    GET_AINFO_I(ipaddr_len);
    GET_AINFO_I(addr_str_len);
    GET_AINFO_J(ipaddr_ptr);
    GET_AINFO_J(next);

    ainfo_class_initialized = 1;
cleanup:
    (*e)->DeleteLocalRef(e, ainfo);
    return APR_SUCCESS;
}

static void fill_finfo(JNIEnv *e, jobject obj, apr_finfo_t *info)
{

    SET_FINFO_J(pool, P2J(info->pool));
    SET_FINFO_I(valid, info->valid);
    SET_FINFO_I(protection, info->protection);
    SET_FINFO_I(filetype, info->filetype);
    SET_FINFO_I(user, info->user);
    SET_FINFO_I(group, info->group);
    SET_FINFO_I(inode, info->inode);
    SET_FINFO_I(device, info->device);
    SET_FINFO_I(nlink, info->nlink);
    SET_FINFO_J(size, info->size);
    SET_FINFO_J(csize, info->csize);
    SET_FINFO_J(atime, info->atime);
    SET_FINFO_J(mtime, info->mtime);
    SET_FINFO_J(ctime, info->ctime);
    SET_FINFO_S(fname, info->fname);
    SET_FINFO_S(name, info->name);
    SET_FINFO_J(filehand, P2J(info->filehand));
}

static void fill_ainfo(JNIEnv *e, jobject obj, apr_sockaddr_t *info)
{

    SET_AINFO_J(pool, P2J(info->pool));
    SET_AINFO_S(hostname, info->hostname);
    SET_AINFO_S(servname, info->servname);
    SET_AINFO_I(port, info->port);
    SET_AINFO_I(family, info->family);
    SET_AINFO_I(salen, info->salen);
    SET_AINFO_I(ipaddr_len, info->ipaddr_len);
    SET_AINFO_I(addr_str_len, info->addr_str_len);
    SET_AINFO_J(ipaddr_ptr, P2J(info->ipaddr_ptr));
    SET_AINFO_J(next, P2J(info->next));

}

TCN_IMPLEMENT_CALL(jint, File, stat)(TCN_STDARGS, jobject finfo,
                                     jstring fname, jint wanted,
                                     jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(fname);
    apr_status_t rv;
    apr_finfo_t info;

    UNREFERENCED(o);

    if ((rv =  apr_stat(&info, J2S(fname), wanted, p)) == APR_SUCCESS) {
        jobject io = (*e)->NewLocalRef(e, finfo);
        fill_finfo(e, io, &info);
        (*e)->DeleteLocalRef(e, io);
    }
    TCN_FREE_CSTRING(fname);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, File, infoGet)(TCN_STDARGS, jobject finfo,
                                        jint wanted, jlong file)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    apr_status_t rv;
    apr_finfo_t info;

    UNREFERENCED(o);

    if ((rv =  apr_file_info_get(&info, wanted, f)) == APR_SUCCESS) {
        jobject io = (*e)->NewLocalRef(e, finfo);
        fill_finfo(e, io, &info);
        (*e)->DeleteLocalRef(e, io);
    }
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, Directory, read)(TCN_STDARGS, jobject finfo,
                                          jint wanted, jlong dir)
{
    apr_dir_t *d = J2P(dir, apr_dir_t *);
    apr_status_t rv;
    apr_finfo_t info;

    UNREFERENCED(o);

    if ((rv =  apr_dir_read(&info, wanted, d)) == APR_SUCCESS) {
        jobject io = (*e)->NewLocalRef(e, finfo);
        fill_finfo(e, io, &info);
        if ((*e)->ExceptionCheck(e)) {
            (*e)->ExceptionClear(e);
        }
        else
            rv = APR_EGENERAL;
        (*e)->DeleteLocalRef(e, io);
    }
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jboolean, Address, fill)(TCN_STDARGS,
                                            jobject addr, jlong info)
{
    apr_sockaddr_t *i = J2P(info, apr_sockaddr_t *);
    jobject ao;
    jboolean rv = JNI_FALSE;

    UNREFERENCED(o);

    if (i) {
        ao = (*e)->NewLocalRef(e, addr);
        fill_ainfo(e, ao, i);
        if ((*e)->ExceptionCheck(e)) {
            (*e)->ExceptionClear(e);
        }
        else
            rv = JNI_TRUE;
        (*e)->DeleteLocalRef(e, ao);
    }
    return rv;
}
