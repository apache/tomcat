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
#include "apr_user.h"
#include "tcn.h"


TCN_IMPLEMENT_CALL(jlong, User, uidCurrent)(TCN_STDARGS, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_uid_t uid;
    apr_gid_t gid;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_uid_current(&uid, &gid, p), uid);

cleanup:
    return (jlong)uid;
}

TCN_IMPLEMENT_CALL(jlong, User, gidCurrent)(TCN_STDARGS, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_uid_t uid;
    apr_gid_t gid;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_uid_current(&uid, &gid, p), gid);

cleanup:
    return (jlong)gid;
}

TCN_IMPLEMENT_CALL(jlong, User, uid)(TCN_STDARGS, jstring uname, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_uid_t uid;
    apr_gid_t gid;
    TCN_ALLOC_CSTRING(uname);

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_uid_get(&uid, &gid, J2S(uname), p), uid);

cleanup:
    TCN_FREE_CSTRING(uname);
    return (jlong)uid;
}

TCN_IMPLEMENT_CALL(jlong, User, usergid)(TCN_STDARGS, jstring uname,
                                         jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_uid_t uid;
    apr_gid_t gid;
    TCN_ALLOC_CSTRING(uname);

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_uid_get(&uid, &gid, J2S(uname), p), gid);

cleanup:
    TCN_FREE_CSTRING(uname);
    return (jlong)gid;
}

TCN_IMPLEMENT_CALL(jlong, User, gid)(TCN_STDARGS, jstring gname, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_gid_t gid;
    TCN_ALLOC_CSTRING(gname);

    UNREFERENCED(o);
    TCN_THROW_IF_ERR( apr_gid_get(&gid, J2S(gname), p), gid);

cleanup:
    TCN_FREE_CSTRING(gname);
    return (jlong)gid;
}

TCN_IMPLEMENT_CALL(jstring, User, username)(TCN_STDARGS, jlong userid, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_uid_t uid = (apr_uid_t)(long)userid;
    char *uname = NULL;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_uid_name_get(&uname, uid, p), uname);

cleanup:
    if (uname)
        return AJP_TO_JSTRING(uname);
    else
        return NULL;
}

TCN_IMPLEMENT_CALL(jstring, User, groupname)(TCN_STDARGS, jlong grpid, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_gid_t gid = (apr_uid_t)(long)grpid;
    char *gname = NULL;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_gid_name_get(&gname, gid, p), gname);

cleanup:
    if (gname)
        return AJP_TO_JSTRING(gname);
    else
        return NULL;
}

TCN_IMPLEMENT_CALL(jint, User,uidcompare)(TCN_STDARGS, jlong left, jlong right)
{

    UNREFERENCED_STDARGS;
    return (int)apr_uid_compare((apr_uid_t)(long)left,
                                (apr_uid_t)(long)right);
}

TCN_IMPLEMENT_CALL(jint, User,gidcompare)(TCN_STDARGS, jlong left, jlong right)
{

    UNREFERENCED_STDARGS;
    return (int)apr_gid_compare((apr_gid_t)(long)left,
                                (apr_gid_t)(long)right);
}

TCN_IMPLEMENT_CALL(jstring, User, homepath)(TCN_STDARGS, jstring uname, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    char *dirname = NULL;
    TCN_ALLOC_CSTRING(uname);

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_uid_homepath_get(&dirname, J2S(uname),
                                          p), dirname);

cleanup:
    TCN_FREE_CSTRING(uname);
    if (dirname)
        return AJP_TO_JSTRING(dirname);
    else
        return NULL;
}

