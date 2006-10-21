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
 * @version $Revision$, $Date$
 */
 
#include "tcn.h"

extern int tcn_parent_pid;

TCN_IMPLEMENT_CALL(jlong, Stdlib, malloc)(TCN_STDARGS, jint size)
{
    UNREFERENCED_STDARGS;
    if (size)
        return P2J(malloc((size_t)size));
    else
        return 0;
}

TCN_IMPLEMENT_CALL(jlong, Stdlib, realloc)(TCN_STDARGS, jlong mem, jint size)
{
    void *ptr = J2P(mem, void *);
    UNREFERENCED_STDARGS;
    if (size)
        return P2J(realloc(ptr, (size_t)size));
    else
        return 0;
}

TCN_IMPLEMENT_CALL(jlong, Stdlib, calloc)(TCN_STDARGS, jint num, jint size)
{
    UNREFERENCED_STDARGS;
    if (num && size)
        return P2J(calloc((size_t)num, (size_t)size));
    else
        return 0;
}

TCN_IMPLEMENT_CALL(void, Stdlib, free)(TCN_STDARGS, jlong mem)
{
    void *ptr = J2P(mem, void *);

    UNREFERENCED_STDARGS;
    if (ptr)
        free(ptr);
}

TCN_IMPLEMENT_CALL(jboolean, Stdlib, memread)(TCN_STDARGS,
                                              jbyteArray dst,
                                              jlong src, jint sz)
{
    jbyte *s = J2P(src, jbyte *);
    jbyte *dest = (*e)->GetPrimitiveArrayCritical(e, dst, NULL);

    UNREFERENCED(o);
    if (s && dest) {
        memcpy(dest, s, (size_t)sz);
        (*e)->ReleasePrimitiveArrayCritical(e, dst, dest, 0);
        return JNI_TRUE;
    }
    else
        return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jboolean, Stdlib, memwrite)(TCN_STDARGS, jlong dst,
                                               jbyteArray src, jint sz)
{
    jbyte *dest = J2P(dst, jbyte *);
    jbyte *s = (*e)->GetPrimitiveArrayCritical(e, src, NULL);

    UNREFERENCED(o);
    if (s && dest) {
        memcpy(dest, s, (size_t)sz);
        (*e)->ReleasePrimitiveArrayCritical(e, src, s, JNI_ABORT);
        return JNI_TRUE;
    }
    else
        return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jboolean, Stdlib, memset)(TCN_STDARGS, jlong dst,
                                             jint  c, jint sz)
{
    jbyte *dest = J2P(dst, jbyte *);

    UNREFERENCED_STDARGS;
    if (memset(dest, (int)c, (size_t)sz))
        return JNI_TRUE;
    else
        return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jint, Stdlib, getpid)(TCN_STDARGS)
{
    UNREFERENCED_STDARGS;
    return (jint)getpid();
}

TCN_IMPLEMENT_CALL(jint, Stdlib, getppid)(TCN_STDARGS)
{
    UNREFERENCED_STDARGS;
    return (jint)tcn_parent_pid;
}

