/* Copyright 2000-2006 The Apache Software Foundation
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
 * @version $Revision: 416783 $, $Date: 2006-06-23 20:06:15 +0200 (pet, 23 lip 2006) $
 */

#include "tcn.h"

TCN_IMPLEMENT_CALL(jlong, Address, info)(TCN_STDARGS,
                                         jstring hostname,
                                         jint family, jint port,
                                         jint flags, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(hostname);
    apr_sockaddr_t *sa = NULL;
    apr_int32_t f;


    UNREFERENCED(o);
    GET_S_FAMILY(f, family);
    TCN_THROW_IF_ERR(apr_sockaddr_info_get(&sa,
            J2S(hostname), f, (apr_port_t)port,
            (apr_int32_t)flags, p), sa);

cleanup:
    TCN_FREE_CSTRING(hostname);
    return P2J(sa);
}

TCN_IMPLEMENT_CALL(jstring, Address, getnameinfo)(TCN_STDARGS,
                                                  jlong sa, jint flags)
{
    apr_sockaddr_t *s = J2P(sa, apr_sockaddr_t *);
    char *hostname;

    UNREFERENCED(o);
    if (apr_getnameinfo(&hostname, s, (apr_int32_t)flags) == APR_SUCCESS)
        return AJP_TO_JSTRING(hostname);
    else
        return NULL;
}

TCN_IMPLEMENT_CALL(jstring, Address, getip)(TCN_STDARGS, jlong sa)
{
    apr_sockaddr_t *s = J2P(sa, apr_sockaddr_t *);
    char *ipaddr;

    UNREFERENCED(o);
    if (apr_sockaddr_ip_get(&ipaddr, s) == APR_SUCCESS)
        return AJP_TO_JSTRING(ipaddr);
    else
        return NULL;
}

TCN_IMPLEMENT_CALL(jlong, Address, get)(TCN_STDARGS, jint which,
                                        jlong sock)
{
    tcn_socket_t *s = J2P(sock, tcn_socket_t *);
    apr_sockaddr_t *sa = NULL;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_socket_addr_get(&sa,
                        (apr_interface_e)which, s->sock), sa);
cleanup:
    return P2J(sa);
}

TCN_IMPLEMENT_CALL(jint, Address, equal)(TCN_STDARGS,
                                         jlong a, jlong b)
{
    apr_sockaddr_t *sa = J2P(a, apr_sockaddr_t *);
    apr_sockaddr_t *sb = J2P(b, apr_sockaddr_t *);

    UNREFERENCED_STDARGS;
    return apr_sockaddr_equal(sa, sb) ? JNI_TRUE : JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jint, Address, getservbyname)(TCN_STDARGS,
                                                 jlong sa, jstring servname)
{
    apr_sockaddr_t *s = J2P(sa, apr_sockaddr_t *);
    TCN_ALLOC_CSTRING(servname);
    apr_status_t rv;

    UNREFERENCED(o);
    rv = apr_getservbyname(s, J2S(servname));
    TCN_FREE_CSTRING(servname);
    return (jint)rv;
}
