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
#include "apr_network_io.h"
#include "apr_arch_misc.h"  /* for apr_os_level */

#include "tcn.h"


TCN_IMPLEMENT_CALL(jboolean, OS, is)(TCN_STDARGS, jint type)
{
    UNREFERENCED_STDARGS;
#ifdef _WIN64
    if (type == 4)
        return JNI_TRUE;
    else
#endif
    if (type == 3)
        return JNI_TRUE;
    else
        return JNI_FALSE;
}
