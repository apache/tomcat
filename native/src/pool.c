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

static apr_status_t generic_pool_cleanup(void *data)
{
    apr_status_t rv = APR_SUCCESS;
    tcn_callback_t *cb = (tcn_callback_t *)data;

    if (data) {
        if (!TCN_IS_NULL(cb->env, cb->obj)) {
            rv = (*(cb->env))->CallIntMethod(cb->env, cb->obj, cb->mid,
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
    TCN_THROW_IF_ERR(apr_pool_create(&n, p), n);
cleanup:
    return P2J(n);
}

TCN_IMPLEMENT_CALL(void, Pool, clear)(TCN_STDARGS, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    UNREFERENCED_STDARGS;
    apr_pool_clear(p);
}

TCN_IMPLEMENT_CALL(void, Pool, destroy)(TCN_STDARGS, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    UNREFERENCED_STDARGS;
    apr_pool_destroy(p);
}

TCN_IMPLEMENT_CALL(jlong, Pool, parentGet)(TCN_STDARGS, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    UNREFERENCED_STDARGS;
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
    cb->env = e;
    cb->obj = (*e)->NewGlobalRef(e, obj);
    cb->mid = (*e)->GetMethodID(e, cls, "callback", "()I");

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
    
    if ((mem = apr_pcalloc(p, sz)) != NULL)
        return (*e)->NewDirectByteBuffer(e, mem, (jlong)sz);
    else
        return NULL;
}
