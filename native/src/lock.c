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
#include "apr_proc_mutex.h"
#include "apr_global_mutex.h"
#include "tcn.h"


TCN_IMPLEMENT_CALL(jlong, Lock, create)(TCN_STDARGS,
                                        jstring fname,
                                        jint mech, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_proc_mutex_t *mutex;
    TCN_ALLOC_CSTRING(fname);


    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_proc_mutex_create(&mutex, J2S(fname),
                                (apr_lockmech_e)mech, p), mutex);

cleanup:
    TCN_FREE_CSTRING(fname);
    return P2J(mutex);
}

TCN_IMPLEMENT_CALL(jlong, Lock, childInit)(TCN_STDARGS,
                                           jstring fname,
                                           jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_proc_mutex_t *mutex;
    TCN_ALLOC_CSTRING(fname);


    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_proc_mutex_child_init(&mutex,
                                   J2S(fname), p), mutex);

cleanup:
    TCN_FREE_CSTRING(fname);
    return P2J(mutex);
}

TCN_IMPLEMENT_CALL(jint, Lock, lock)(TCN_STDARGS, jlong mutex)
{
    apr_proc_mutex_t *m = J2P(mutex, apr_proc_mutex_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_proc_mutex_lock(m);
}

TCN_IMPLEMENT_CALL(jint, Lock, trylock)(TCN_STDARGS, jlong mutex)
{
    apr_proc_mutex_t *m = J2P(mutex, apr_proc_mutex_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_proc_mutex_trylock(m);
}

TCN_IMPLEMENT_CALL(jint, Lock, unlock)(TCN_STDARGS, jlong mutex)
{
    apr_proc_mutex_t *m = J2P(mutex, apr_proc_mutex_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_proc_mutex_unlock(m);
}

TCN_IMPLEMENT_CALL(jint, Lock, destoy)(TCN_STDARGS, jlong mutex)
{
    apr_proc_mutex_t *m = J2P(mutex, apr_proc_mutex_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_proc_mutex_destroy(m);
}

#if 0
/* There is bug in APR implementing that function */
TCN_IMPLEMENT_CALL(jint, Lock, cleanup)(TCN_STDARGS, jlong mutex)
{
   void *m = J2P(mutex, void *);

    UNREFERENCED_STDARGS;
    return (jint)apr_proc_mutex_cleanup(m);
}
#endif

TCN_IMPLEMENT_CALL(jstring, Lock, lockfile)(TCN_STDARGS, jlong mutex)
{
    apr_proc_mutex_t *m = J2P(mutex, apr_proc_mutex_t *);
    const char *s = apr_proc_mutex_lockfile(m);

    UNREFERENCED_STDARGS;
    if (s)
        return AJP_TO_JSTRING(s);
    else
        return NULL;
}

TCN_IMPLEMENT_CALL(jstring, Lock, name)(TCN_STDARGS, jlong mutex)
{
    apr_proc_mutex_t *m = J2P(mutex, apr_proc_mutex_t *);

    UNREFERENCED(o);
    return AJP_TO_JSTRING(apr_proc_mutex_name(m));
}

TCN_IMPLEMENT_CALL(jstring, Lock, defname)(TCN_STDARGS)
{

    UNREFERENCED(o);
    return AJP_TO_JSTRING(apr_proc_mutex_defname());
}



TCN_IMPLEMENT_CALL(jlong, Global, create)(TCN_STDARGS,
                                          jstring fname,
                                          jint mech, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_global_mutex_t *mutex;
    TCN_ALLOC_CSTRING(fname);


    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_global_mutex_create(&mutex, J2S(fname),
                                (apr_lockmech_e)mech, p), mutex);

cleanup:
    TCN_FREE_CSTRING(fname);
    return P2J(mutex);
}

TCN_IMPLEMENT_CALL(jlong, Global, childInit)(TCN_STDARGS,
                                             jstring fname,
                                             jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_global_mutex_t *mutex;
    TCN_ALLOC_CSTRING(fname);


    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_global_mutex_child_init(&mutex,
                                   J2S(fname), p), mutex);

cleanup:
    TCN_FREE_CSTRING(fname);
    return P2J(mutex);
}

TCN_IMPLEMENT_CALL(jint, Global, lock)(TCN_STDARGS, jlong mutex)
{
    apr_global_mutex_t *m = J2P(mutex, apr_global_mutex_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_global_mutex_lock(m);
}

TCN_IMPLEMENT_CALL(jint, Global, trylock)(TCN_STDARGS, jlong mutex)
{
    apr_global_mutex_t *m = J2P(mutex, apr_global_mutex_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_global_mutex_trylock(m);
}

TCN_IMPLEMENT_CALL(jint, Global, unlock)(TCN_STDARGS, jlong mutex)
{
    apr_global_mutex_t *m = J2P(mutex, apr_global_mutex_t*);

    UNREFERENCED_STDARGS;
    return (jint)apr_global_mutex_unlock(m);
}

TCN_IMPLEMENT_CALL(jint, Global, destoy)(TCN_STDARGS, jlong mutex)
{
    apr_global_mutex_t *m = J2P(mutex, apr_global_mutex_t *);

    UNREFERENCED_STDARGS;
    return (jint)apr_global_mutex_destroy(m);
}

