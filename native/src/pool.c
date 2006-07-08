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

#include "tcn.h"

extern apr_pool_t *tcn_global_pool;

static apr_status_t generic_pool_cleanup(void *data)
{
    apr_status_t rv = APR_SUCCESS;
    tcn_callback_t *cb = (tcn_callback_t *)data;

    if (data) {
        if (!TCN_IS_NULL(cb->env, cb->obj)) {
            rv = (*(cb->env))->CallIntMethod(cb->env, cb->obj, cb->mid[0],
                                             NULL);
            TCN_UNLOAD_CLASS(cb->env, cb->obj);
        }
        free(cb);
    }
    return rv;
}

TCN_IMPLEMENT_CALL(jlong, Pool, create)(TCN_STDARGS, jlong parent)
{
    apr_pool_t *p = J2P(parent, apr_pool_t *);
    apr_pool_t *n;

    UNREFERENCED(o);
    /* Make sure our global pool is accessor for all pools */
    if (p == NULL)
        p = tcn_global_pool;
    TCN_THROW_IF_ERR(apr_pool_create(&n, p), n);
cleanup:
    return P2J(n);
}

TCN_IMPLEMENT_CALL(void, Pool, clear)(TCN_STDARGS, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    UNREFERENCED_STDARGS;
    TCN_ASSERT(pool != 0);
    apr_pool_clear(p);
}

TCN_IMPLEMENT_CALL(void, Pool, destroy)(TCN_STDARGS, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    UNREFERENCED_STDARGS;
    TCN_ASSERT(pool != 0);
    apr_pool_destroy(p);
}

TCN_IMPLEMENT_CALL(jlong, Pool, parentGet)(TCN_STDARGS, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    UNREFERENCED_STDARGS;
    TCN_ASSERT(pool != 0);
    return P2J(apr_pool_parent_get(p));
}

TCN_IMPLEMENT_CALL(jboolean, Pool, isAncestor)(TCN_STDARGS, jlong a, jlong b)
{
    apr_pool_t *pa = J2P(a, apr_pool_t *);
    apr_pool_t *pb = J2P(b, apr_pool_t *);
    UNREFERENCED_STDARGS;
    return apr_pool_is_ancestor(pa, pb) ? JNI_TRUE : JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jlong, Pool, palloc)(TCN_STDARGS, jlong pool, jint size)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    UNREFERENCED_STDARGS;
    return P2J(apr_palloc(p, (apr_size_t)size));
}

TCN_IMPLEMENT_CALL(jlong, Pool, pcalloc)(TCN_STDARGS, jlong pool, jint size)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    UNREFERENCED_STDARGS;
    return P2J(apr_pcalloc(p, (apr_size_t)size));
}

TCN_IMPLEMENT_CALL(jlong, Pool, cleanupRegister)(TCN_STDARGS, jlong pool,
                                                 jobject obj)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    tcn_callback_t *cb = (tcn_callback_t *)malloc(sizeof(tcn_callback_t));
    jclass cls;

    UNREFERENCED(o);

    if (cb == NULL) {
       TCN_THROW_OS_ERROR(e);
       return 0;
    }
    cls = (*e)->GetObjectClass(e, obj);
    cb->env    = e;
    cb->obj    = (*e)->NewGlobalRef(e, obj);
    cb->mid[0] = (*e)->GetMethodID(e, cls, "callback", "()I");

    apr_pool_cleanup_register(p, (const void *)cb,
                              generic_pool_cleanup,
                              apr_pool_cleanup_null);

    return P2J(cb);
}

TCN_IMPLEMENT_CALL(void, Pool, cleanupKill)(TCN_STDARGS, jlong pool,
                                            jlong data)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    tcn_callback_t *cb = J2P(data, tcn_callback_t *);

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);
    apr_pool_cleanup_kill(p, cb, generic_pool_cleanup);
    (*e)->DeleteGlobalRef(e, cb->obj);
    free(cb);
}

TCN_IMPLEMENT_CALL(jobject, Pool, alloc)(TCN_STDARGS, jlong pool,
                                         jint size)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_size_t sz = (apr_size_t)size;
    void *mem;

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);

    if ((mem = apr_palloc(p, sz)) != NULL)
        return (*e)->NewDirectByteBuffer(e, mem, (jlong)sz);
    else
        return NULL;
}

TCN_IMPLEMENT_CALL(jobject, Pool, calloc)(TCN_STDARGS, jlong pool,
                                          jint size)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_size_t sz = (apr_size_t)size;
    void *mem;

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);

    if ((mem = apr_pcalloc(p, sz)) != NULL)
        return (*e)->NewDirectByteBuffer(e, mem, (jlong)sz);
    else
        return NULL;
}

static apr_status_t generic_pool_data_cleanup(void *data)
{
    apr_status_t rv = APR_SUCCESS;
    tcn_callback_t *cb = (tcn_callback_t *)data;

    if (data) {
        if (!TCN_IS_NULL(cb->env, cb->obj)) {
            TCN_UNLOAD_CLASS(cb->env, cb->obj);
        }
        free(cb);
    }
    return rv;
}

TCN_IMPLEMENT_CALL(jint, Pool, dataSet)(TCN_STDARGS, jlong pool,
                                        jstring key, jobject data)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_status_t rv = APR_SUCCESS;
    void *old = NULL;
    TCN_ALLOC_CSTRING(key);

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);

    if (apr_pool_userdata_get(&old, J2S(key), p) == APR_SUCCESS) {
        if (old)
            apr_pool_cleanup_run(p, old, generic_pool_data_cleanup);
    }
    if (data) {
        tcn_callback_t *cb = (tcn_callback_t *)malloc(sizeof(tcn_callback_t));
        cb->env = e;
        cb->obj = (*e)->NewGlobalRef(e, data);
        if ((rv = apr_pool_userdata_set(cb, J2S(key), generic_pool_data_cleanup,
                                        p)) != APR_SUCCESS) {
            (*e)->DeleteGlobalRef(e, cb->obj);
            free(cb);
        }
    }
    else {
        /* Clear the exiting user data */
        rv = apr_pool_userdata_set(NULL, J2S(key), NULL, p);
    }
    TCN_FREE_CSTRING(key);
    return rv;
}

TCN_IMPLEMENT_CALL(jobject, Pool, dataGet)(TCN_STDARGS, jlong pool,
                                           jstring key)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    void *old = NULL;
    TCN_ALLOC_CSTRING(key);
    jobject rv = NULL;

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);

    if (apr_pool_userdata_get(&old, J2S(key), p) == APR_SUCCESS) {
        if (old) {
            tcn_callback_t *cb = (tcn_callback_t *)old;
            rv = cb->obj;
        }
    }
    TCN_FREE_CSTRING(key);
    return rv;
}

TCN_IMPLEMENT_CALL(void, Pool, cleanupForExec)(TCN_STDARGS)
{
    UNREFERENCED_STDARGS;
    apr_pool_cleanup_for_exec();
}
