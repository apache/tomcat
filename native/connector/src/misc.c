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
#include "apr_time.h"

TCN_IMPLEMENT_CALL(void, Time, sleep)(TCN_STDARGS, jlong t)
{

    UNREFERENCED_STDARGS;
    apr_sleep((apr_interval_time_t)t);
}

TCN_IMPLEMENT_CALL(jint, OS, random)(TCN_STDARGS, jbyteArray buf,
                                     jint len)
{
#if APR_HAS_RANDOM
    apr_status_t rv;
    jbyte *b = (*e)->GetPrimitiveArrayCritical(e, buf, NULL);

    UNREFERENCED(o);
    if ((rv = apr_generate_random_bytes((unsigned char *)b,
            (apr_size_t)len)) == APR_SUCCESS)
        (*e)->ReleasePrimitiveArrayCritical(e, buf, b, 0);
    else
        (*e)->ReleasePrimitiveArrayCritical(e, buf, b, JNI_ABORT);

    if ((*e)->ExceptionCheck(e)) {
        (*e)->ExceptionClear(e);
        rv = APR_EGENERAL;
    }
    return (jint)rv;
#else
    return APR_ENOTIMPL;
#endif
}

TCN_IMPLEMENT_CALL(jlong, Time, now)(TCN_STDARGS)
{
    UNREFERENCED_STDARGS;
    return (jlong)apr_time_now();
}

TCN_IMPLEMENT_CALL(jstring, Time, rfc822)(TCN_STDARGS, jlong t)
{
    char ts[APR_RFC822_DATE_LEN];
    UNREFERENCED(o);
    if (apr_rfc822_date(ts, J2T(t)) == APR_SUCCESS)
        return AJP_TO_JSTRING(ts);
    else
        return NULL;
}

TCN_IMPLEMENT_CALL(jstring, Time, ctime)(TCN_STDARGS, jlong t)
{
    char ts[APR_CTIME_LEN];
    UNREFERENCED(o);
    if (apr_ctime(ts, J2T(t)) == APR_SUCCESS)
        return AJP_TO_JSTRING(ts);
    else
        return NULL;
}
