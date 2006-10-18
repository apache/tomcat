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

TCN_IMPLEMENT_CALL(jstring, OS, defaultEncoding)(TCN_STDARGS, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);

    UNREFERENCED(o);
    return AJP_TO_JSTRING(apr_os_default_encoding(p));
}

TCN_IMPLEMENT_CALL(jstring, OS, localeEncoding)(TCN_STDARGS, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);

    UNREFERENCED(o);
    return AJP_TO_JSTRING(apr_os_locale_encoding(p));
}

