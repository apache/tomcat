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

TCN_IMPLEMENT_CALL(jlong, Address, info)(TCN_STDARGS,
                                         jstring hostname,
                                         jint family, jint port,
                                         jint flags, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(hostname);
    apr_sockaddr_t *sa = NULL;
    apr_sockaddr_t *sl = NULL;
    apr_int32_t f;


    UNREFERENCED(o);
    GET_S_FAMILY(f, family);
    TCN_THROW_IF_ERR(apr_sockaddr_info_get(&sa,
            J2S(hostname), f, (apr_port_t)port,
            (apr_int32_t)flags, p), sa);
    sl = sa;
    /* 
     * apr_sockaddr_info_get may return several address so this is not
     * go to work in some cases (but as least it works for Linux)
     * XXX: with AP_ENABLE_V4_MAPPED it is going to work otherwise it won't.
     */
#if APR_HAVE_IPV6
    if (hostname == NULL) {
        /* Try all address using IPV6 one */
        while (sl) {
            if (sl->family == APR_INET6)
                break; /* Done */
            sl = sl->next;
        }
    }
#endif

cleanup:
    TCN_FREE_CSTRING(hostname);
    return P2J(sl);
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
