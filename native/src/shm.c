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
#include "apr_shm.h"

#include "tcn.h"


TCN_IMPLEMENT_CALL(jlong, Shm, create)(TCN_STDARGS, jlong reqsize,
                                       jstring filename,
                                       jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    const char *fname = NULL;
    apr_shm_t *shm;


    UNREFERENCED(o);
    if (filename)
        fname = (const char *)((*e)->GetStringUTFChars(e, filename, 0));
    TCN_THROW_IF_ERR(apr_shm_create(&shm, (apr_size_t)reqsize,
                                    fname, p), shm);

cleanup:
    if (fname)
        (*e)->ReleaseStringUTFChars(e, filename, fname);
    return P2J(shm);
}

TCN_IMPLEMENT_CALL(jint, Shm, remove)(TCN_STDARGS,
                                      jstring filename,
                                      jlong pool)
{
    apr_status_t rv;
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(filename);


    UNREFERENCED(o);
    rv = apr_shm_remove(J2S(filename), p);
    TCN_FREE_CSTRING(filename);

    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, Shm, destroy)(TCN_STDARGS, jlong shm)
{
    apr_shm_t *s = J2P(shm, apr_shm_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_shm_destroy(s);
}

TCN_IMPLEMENT_CALL(jlong, Shm, attach)(TCN_STDARGS,
                                       jstring filename,
                                       jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    const char *fname = NULL;
    apr_shm_t *shm;


    UNREFERENCED(o);
    if (filename)
        fname = (const char *)((*e)->GetStringUTFChars(e, filename, 0));
    TCN_THROW_IF_ERR(apr_shm_attach(&shm, fname, p), shm);

cleanup:
    if (fname)
        (*e)->ReleaseStringUTFChars(e, filename, fname);
    return P2J(shm);
}

TCN_IMPLEMENT_CALL(jint, Shm, detach)(TCN_STDARGS, jlong shm)
{
    apr_shm_t *s = J2P(shm, apr_shm_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_shm_detach(s);
}

TCN_IMPLEMENT_CALL(jlong, Shm, baseaddr)(TCN_STDARGS, jlong shm)
{
    apr_shm_t *s = J2P(shm, apr_shm_t *);

    UNREFERENCED_STDARGS;
    return P2J(apr_shm_baseaddr_get(s));
}

TCN_IMPLEMENT_CALL(jlong, Shm, size)(TCN_STDARGS, jlong shm)
{
    apr_shm_t *s = J2P(shm, apr_shm_t *);

    UNREFERENCED_STDARGS;
    return (jlong)apr_shm_size_get(s);
}

TCN_IMPLEMENT_CALL(jobject, Shm, buffer)(TCN_STDARGS, jlong shm)
{
    apr_shm_t *s = J2P(shm, apr_shm_t *);
    jlong sz = (jlong)apr_shm_size_get(s);
    void *a;

    UNREFERENCED(o);

    if ((a = apr_shm_baseaddr_get(s)) != NULL)
        return (*e)->NewDirectByteBuffer(e, a, sz);
    else
        return NULL;
}
