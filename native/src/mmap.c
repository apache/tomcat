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
#include "apr_mmap.h"
#include "tcn.h"

TCN_IMPLEMENT_CALL(jlong, Mmap, create)(TCN_STDARGS, jlong file,
                                        jlong offset, jlong size,
                                        jint flag, jlong pool)
{
#if APR_HAS_MMAP
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_file_t *f = J2P(file, apr_file_t *);
    apr_mmap_t *m = NULL;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_mmap_create(&m, f, (apr_off_t)offset,
                                     (apr_size_t)size,
                                     (apr_uint32_t)flag, p), m);

cleanup:
    return P2J(m);
#else
    UNREFERENCED(o);
    tcn_ThrowAPRException(e, APR_ENOTIMPL);
    return 0;
#endif
}

TCN_IMPLEMENT_CALL(jlong, Mmap, dup)(TCN_STDARGS, jlong mmap,
                                     jlong pool)
{
#if APR_HAS_MMAP
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_mmap_t *m = J2P(mmap, apr_mmap_t *);
    apr_mmap_t *newm = NULL;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_mmap_dup(&newm, m, p), newm);

cleanup:
    return P2J(newm);
#else
    UNREFERENCED(o);
    tcn_ThrowAPRException(e, APR_ENOTIMPL);
    return 0;
#endif
}

TCN_IMPLEMENT_CALL(jint, Mmap, delete)(TCN_STDARGS, jlong mmap)
{
#if APR_HAS_MMAP
    apr_mmap_t *m = J2P(mmap, apr_mmap_t *);

    UNREFERENCED_STDARGS;
    return apr_mmap_delete(m);

#else
    UNREFERENCED_STDARGS;
    UNREFERENCED(mmap);
    return APR_ENOTIMPL;
#endif
}

TCN_IMPLEMENT_CALL(jlong, Mmap, offset)(TCN_STDARGS, jlong mmap,
                                        jlong offset)
{
#if APR_HAS_MMAP
    apr_mmap_t *m = J2P(mmap, apr_mmap_t *);
    void *r;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_mmap_offset(&r, m, (apr_off_t)offset), r);

cleanup:
    return P2J(r);

#else
    UNREFERENCED(o);
    tcn_ThrowAPRException(e, APR_ENOTIMPL);
    return 0;
#endif
}
