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
#include "apr_file_io.h"

#include "tcn.h"

TCN_IMPLEMENT_CALL(jint, Directory, make)(TCN_STDARGS, jstring path,
                                          jint perm, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(path);
    apr_status_t rv;

    UNREFERENCED(o);
    rv = apr_dir_make(J2S(path), (apr_fileperms_t)perm, p);
    TCN_FREE_CSTRING(path);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, Directory, makeRecursive)(TCN_STDARGS, jstring path,
                                                    jint perm, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(path);
    apr_status_t rv;

    UNREFERENCED(o);
    rv = apr_dir_make_recursive(J2S(path), (apr_fileperms_t)perm, p);
    TCN_FREE_CSTRING(path);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, Directory, remove)(TCN_STDARGS, jstring path,
                                            jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(path);
    apr_status_t rv;

    UNREFERENCED(o);
    rv = apr_dir_remove(J2S(path), p);
    TCN_FREE_CSTRING(path);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jstring, Directory, tempGet)(TCN_STDARGS, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    jstring name = NULL;
    const char *tname;

    UNREFERENCED(o);
    if (apr_temp_dir_get(&tname, p) == APR_SUCCESS)
        name = AJP_TO_JSTRING(tname);

    return name;
}

TCN_IMPLEMENT_CALL(jlong, Directory, open)(TCN_STDARGS, jstring path,
                                      jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_dir_t *d = NULL;
    TCN_ALLOC_CSTRING(path);

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_dir_open(&d, J2S(path), p), d);

cleanup:
    TCN_FREE_CSTRING(path);
    return P2J(d);
}

TCN_IMPLEMENT_CALL(jint, Directory, close)(TCN_STDARGS, jlong dir)
{
    apr_dir_t *d = J2P(dir, apr_dir_t *);
    UNREFERENCED_STDARGS;
    return (jint)apr_dir_close(d);
}

TCN_IMPLEMENT_CALL(jint, Directory, rewind)(TCN_STDARGS, jlong dir)
{
    apr_dir_t *d = J2P(dir, apr_dir_t *);
    UNREFERENCED_STDARGS;
    return (jint)apr_dir_rewind(d);
}
