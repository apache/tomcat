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

#include "apr.h"
#include "apr_pools.h"
#include "apr_network_io.h"
#include "apr_poll.h"

#include "tcn.h"
#if defined(__linux__)
#include <sys/sysinfo.h>
#endif

TCN_IMPLEMENT_CALL(jboolean, OS, is)(TCN_STDARGS, jint type)
{
    UNREFERENCED_STDARGS;
    if (type == 1)
        return JNI_TRUE;
#if defined(__linux__)
    else if (type == 5)
        return JNI_TRUE;
#endif
#if defined(sun)
    else if (type == 6)
        return JNI_TRUE;
#endif
#if defined(__FreeBSD__) || defined(__NetBSD__)
    else if (type == 7)
        return JNI_TRUE;
#endif
    else
        return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jint, OS, info)(TCN_STDARGS,
                                   jlongArray inf)
{
    jint rv;
    int  i;
    jsize ilen = (*e)->GetArrayLength(e, inf);
    jlong *pvals = (*e)->GetLongArrayElements(e, inf, NULL);

    UNREFERENCED(o);
    if (ilen < 16) {
        return APR_EINVAL;
    }
    for (i = 0; i < 16; i++)
        pvals[i] = 0;
#if defined(__linux__)
    {
        struct sysinfo info;
        if (sysinfo(&info))
            rv = apr_get_os_error();
        else {
            pvals[0] = (jlong)info.totalram;
            pvals[1] = (jlong)info.freeram;
            pvals[2] = (jlong)info.totalswap;
            pvals[3] = (jlong)info.freeswap;
            pvals[4] = (jlong)info.sharedram;
            pvals[5] = (jlong)info.bufferram;
            pvals[6] = (jlong)(100 - (info.freeram * 100 / info.totalram));
            rv = APR_SUCCESS;
        }
    }
#else
    rv = APR_ENOTIMPL;
#endif
   (*e)->ReleaseLongArrayElements(e, inf, pvals, 0);
    return rv;
}
