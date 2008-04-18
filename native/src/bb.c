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
 * @version $Revision: $, $Date: $
 */

#include "tcn.h"

/**
 * DirectByteBuffer utilities
 */

TCN_IMPLEMENT_CALL(jobject, Buffer, malloc)(TCN_STDARGS, jint size)
{
    void *mem;
    size_t sz = (size_t)APR_ALIGN_DEFAULT(size);

    UNREFERENCED(o);

    if ((mem = malloc(sz)) != NULL) {
        jobject rv = (*e)->NewDirectByteBuffer(e, mem, (jlong)sz);
        if (rv == NULL)
            free(mem);
        return rv;
    }
    else {
        return NULL;
    }
}

TCN_IMPLEMENT_CALL(jobject, Buffer, calloc)(TCN_STDARGS, jint num, jint size)
{
    size_t sz = (size_t)APR_ALIGN_DEFAULT((size * num));
    void *mem;

    UNREFERENCED(o);

    if ((mem = calloc(1, sz)) != NULL) {
        jobject rv = (*e)->NewDirectByteBuffer(e, mem, (jlong)sz);
        if (rv == NULL)
            free(mem);
        return rv;
    }
    else {
        return NULL;
    }
}

TCN_IMPLEMENT_CALL(jobject, Buffer, palloc)(TCN_STDARGS, jlong pool,
                                            jint size)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_size_t sz = (apr_size_t)APR_ALIGN_DEFAULT(size);
    void *mem;

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);

    if ((mem = apr_palloc(p, sz)) != NULL)
        return (*e)->NewDirectByteBuffer(e, mem, (jlong)sz);
    else
        return NULL;
}

TCN_IMPLEMENT_CALL(jobject, Buffer, pcalloc)(TCN_STDARGS, jlong pool,
                                             jint size)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_size_t sz = (apr_size_t)APR_ALIGN_DEFAULT(size);
    void *mem;

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);

    if ((mem = apr_pcalloc(p, sz)) != NULL)
        return (*e)->NewDirectByteBuffer(e, mem, (jlong)sz);
    else
        return NULL;
}

TCN_IMPLEMENT_CALL(jobject, Buffer, create)(TCN_STDARGS, jlong addr,
                                            jint size)
{
    void *mem = J2P(addr, void *);

    UNREFERENCED(o);
    TCN_ASSERT(mem  != 0);
    TCN_ASSERT(size != 0);

    if (mem && size)
        return (*e)->NewDirectByteBuffer(e, mem, (jlong)size);
    else
        return NULL;
}

TCN_IMPLEMENT_CALL(void, Buffer, free)(TCN_STDARGS, jobject bb)
{
    void *mem;

    UNREFERENCED(o);
    if ((mem = (*e)->GetDirectBufferAddress(e, bb)) != NULL) {
        /* This can cause core dump if address was
         * allocated from the APR pool.
         */
        free(mem);
    }
}

TCN_IMPLEMENT_CALL(jlong, Buffer, address)(TCN_STDARGS, jobject bb)
{
    UNREFERENCED(o);
    return P2J((*e)->GetDirectBufferAddress(e, bb));
}

TCN_IMPLEMENT_CALL(jlong, Buffer, size)(TCN_STDARGS, jobject bb)
{
    UNREFERENCED(o);
    return (*e)->GetDirectBufferCapacity(e, bb);
}
