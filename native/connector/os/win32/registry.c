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

#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0500
#endif
#include <winsock2.h>
#include <mswsock.h>
#include <ws2tcpip.h>
#include <shlwapi.h>

#include "apr.h"
#include "apr_pools.h"
#include "apr_arch_misc.h"   /* for apr_os_level */
#include "apr_arch_atime.h"  /* for FileTimeToAprTime */

#include "tcn.h"

#define SAFE_CLOSE_KEY(k)                               \
    if ((k) != NULL && (k) != INVALID_HANDLE_VALUE) {   \
        RegCloseKey((k));                               \
        (k) = NULL;                                     \
    }

typedef struct {
    apr_pool_t     *pool;
    HKEY           root;
    HKEY           key;
} tcn_nt_registry_t;


#define TCN_HKEY_CLASSES_ROOT       1
#define TCN_HKEY_CURRENT_CONFIG     2
#define TCN_HKEY_CURRENT_USER       3
#define TCN_HKEY_LOCAL_MACHINE      4
#define TCN_HKEY_USERS              5

static const struct {
    HKEY k;
} TCN_KEYS[] = {
    INVALID_HANDLE_VALUE,
    HKEY_CLASSES_ROOT,
    HKEY_CURRENT_CONFIG,
    HKEY_CURRENT_USER,
    HKEY_LOCAL_MACHINE,
    HKEY_USERS,
    INVALID_HANDLE_VALUE
};

#define TCN_KEY_ALL_ACCESS          0x0001
#define TCN_KEY_CREATE_LINK         0x0002
#define TCN_KEY_CREATE_SUB_KEY      0x0004
#define TCN_KEY_ENUMERATE_SUB_KEYS  0x0008
#define TCN_KEY_EXECUTE             0x0010
#define TCN_KEY_NOTIFY              0x0020
#define TCN_KEY_QUERY_VALUE         0x0040
#define TCN_KEY_READ                0x0080
#define TCN_KEY_SET_VALUE           0x0100
#define TCN_KEY_WOW64_64KEY         0x0200
#define TCN_KEY_WOW64_32KEY         0x0400
#define TCN_KEY_WRITE               0x0800

#define TCN_REGSAM(s, x)                    \
        s = 0;                              \
        if (x & TCN_KEY_ALL_ACCESS)         \
            s |= KEY_ALL_ACCESS;            \
        if (x & TCN_KEY_CREATE_LINK)        \
            s |= KEY_CREATE_LINK;           \
        if (x & TCN_KEY_CREATE_SUB_KEY)     \
            s |= KEY_CREATE_SUB_KEY;        \
        if (x & TCN_KEY_ENUMERATE_SUB_KEYS) \
            s |= KEY_ENUMERATE_SUB_KEYS;    \
        if (x & TCN_KEY_EXECUTE)            \
            s |= KEY_EXECUTE;               \
        if (x & TCN_KEY_NOTIFY)             \
            s |= KEY_NOTIFY;                \
        if (x & TCN_KEY_READ)               \
            s |= KEY_READ;                  \
        if (x & TCN_KEY_SET_VALUE)          \
            s |= KEY_SET_VALUE;             \
        if (x & TCN_KEY_WOW64_64KEY)        \
            s |= KEY_WOW64_64KEY;           \
        if (x & TCN_KEY_WOW64_32KEY)        \
            s |= KEY_WOW64_32KEY;           \
        if (x & TCN_KEY_WRITE)              \
            s |= KEY_WRITE

#define TCN_REG_BINARY              1
#define TCN_REG_DWORD               2
#define TCN_REG_EXPAND_SZ           3
#define TCN_REG_MULTI_SZ            4
#define TCN_REG_QWORD               5
#define TCN_REG_SZ                  6

static const struct {
    DWORD t;
} TCN_REGTYPES[] = {
    REG_NONE,
    REG_BINARY,
    REG_DWORD,
    REG_EXPAND_SZ,
    REG_MULTI_SZ,
    REG_QWORD,
    REG_SZ,
    REG_NONE
};

static apr_status_t registry_cleanup(void *data)
{
    tcn_nt_registry_t *reg = (tcn_nt_registry_t *)data;

    if (reg) {
        SAFE_CLOSE_KEY(reg->key);
    }
    return APR_SUCCESS;
}

TCN_IMPLEMENT_CALL(jlong, Registry, create)(TCN_STDARGS, jint root, jstring name,
                                            jint sam, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    tcn_nt_registry_t *reg = NULL;
    TCN_ALLOC_WSTRING(name);
    HKEY key;
    LONG rc;
    REGSAM s;

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);

    if (root < TCN_HKEY_CLASSES_ROOT || root > TCN_HKEY_USERS) {
        tcn_ThrowException(e, "Invalid Registry Root Key");
        goto cleanup;
    }
    if (sam < TCN_KEY_ALL_ACCESS || root > TCN_KEY_WRITE) {
        tcn_ThrowException(e, "Invalid Registry Key Security");
        goto cleanup;
    }
    reg = (tcn_nt_registry_t *)apr_palloc(p, sizeof(tcn_nt_registry_t));
    reg->pool = p;
    reg->root = TCN_KEYS[root].k;
    reg->key  = NULL;
    TCN_INIT_WSTRING(name);
    TCN_REGSAM(s, sam);
    rc = RegCreateKeyExW(reg->root, J2W(name), 0, NULL, REG_OPTION_NON_VOLATILE,
                         s, NULL, &key, NULL);
    if (rc !=  ERROR_SUCCESS) {
        tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
        goto cleanup;
    }
    reg->key = key;
    apr_pool_cleanup_register(p, (const void *)reg,
                              registry_cleanup,
                              apr_pool_cleanup_null);

cleanup:
    TCN_FREE_WSTRING(name);
    return P2J(reg);
}

TCN_IMPLEMENT_CALL(jlong, Registry, open)(TCN_STDARGS, jint root, jstring name,
                                          jint sam, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    tcn_nt_registry_t *reg = NULL;
    TCN_ALLOC_WSTRING(name);
    HKEY key;
    LONG rc;
    REGSAM s;

    UNREFERENCED(o);
    TCN_ASSERT(pool != 0);

    if (root < TCN_HKEY_CLASSES_ROOT || root > TCN_HKEY_USERS) {
        tcn_ThrowException(e, "Invalid Registry Root Key");
        goto cleanup;
    }
    if (sam < TCN_KEY_ALL_ACCESS || root > TCN_KEY_WRITE) {
        tcn_ThrowException(e, "Invalid Registry Key Security");
        goto cleanup;
    }
    reg = (tcn_nt_registry_t *)apr_palloc(p, sizeof(tcn_nt_registry_t));
    reg->pool = p;
    reg->root = TCN_KEYS[root].k;
    reg->key  = NULL;
    TCN_INIT_WSTRING(name);
    TCN_REGSAM(s, sam);
    rc = RegOpenKeyExW(reg->root, J2W(name), 0, s, &key);
    if (rc !=  ERROR_SUCCESS) {
        tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
        goto cleanup;
    }
    reg->key = key;
    apr_pool_cleanup_register(p, (const void *)reg,
                              registry_cleanup,
                              apr_pool_cleanup_null);

cleanup:
    TCN_FREE_WSTRING(name);
    return P2J(reg);
}

TCN_IMPLEMENT_CALL(jint, Registry, close)(TCN_STDARGS, jlong reg)
{
    tcn_nt_registry_t *r = J2P(reg, tcn_nt_registry_t *);
    UNREFERENCED_STDARGS;

    TCN_ASSERT(reg != 0);

    registry_cleanup(r);
    apr_pool_cleanup_kill(r->pool, r, registry_cleanup);
    return APR_SUCCESS;
}

TCN_IMPLEMENT_CALL(jint, Registry, getType)(TCN_STDARGS, jlong key,
                                            jstring name)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    LONG rc;
    DWORD v;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    TCN_INIT_WSTRING(name);
    rc = RegQueryValueExW(k->key, J2W(name), NULL, &v, NULL, NULL);
    if (rc != ERROR_SUCCESS)
        v = -rc;
    TCN_FREE_WSTRING(name);
    switch (v) {
        case REG_BINARY:
            v = TCN_REG_BINARY;
            break;
        case REG_DWORD:
            v = TCN_REG_DWORD;
            break;
        case REG_EXPAND_SZ:
            v = TCN_REG_EXPAND_SZ;
            break;
        case REG_MULTI_SZ:
            v = TCN_REG_MULTI_SZ;
            break;
        case REG_QWORD:
            v = TCN_REG_QWORD;
            break;
        case REG_SZ:
            v = TCN_REG_SZ;
            break;
        case REG_DWORD_BIG_ENDIAN:
            v = 0;
            break;
    }
    return v;
}

TCN_IMPLEMENT_CALL(jint, Registry, getSize)(TCN_STDARGS, jlong key,
                                            jstring name)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    LONG rc;
    DWORD v;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    TCN_INIT_WSTRING(name);
    rc = RegQueryValueExW(k->key, J2W(name), NULL, NULL, NULL, &v);
    if (rc != ERROR_SUCCESS)
        v = -rc;
    TCN_FREE_WSTRING(name);
    return v;
}

TCN_IMPLEMENT_CALL(jint, Registry, getValueI)(TCN_STDARGS, jlong key,
                                              jstring name)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    LONG rc;
    DWORD t, l;
    DWORD v = 0;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    TCN_INIT_WSTRING(name);
    rc = RegQueryValueExW(k->key, J2W(name), NULL, &t, NULL, &l);
    if (rc != ERROR_SUCCESS) {
        tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
        goto cleanup;
    }
    if (t == REG_DWORD) {
        l = sizeof(DWORD);
        rc = RegQueryValueExW(k->key, J2W(name), NULL, NULL, (LPBYTE)&v, &l);
        if (rc != ERROR_SUCCESS) {
            tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
            goto cleanup;
        }
    }
    else if (t == REG_SZ || t == REG_BINARY ||
             t == REG_MULTI_SZ || t == REG_EXPAND_SZ)
        v = l;
    else {
        v = 0;
        tcn_ThrowException(e, "Unable to convert the value to integer");
    }
cleanup:
    TCN_FREE_WSTRING(name);
    return v;
}

TCN_IMPLEMENT_CALL(jlong, Registry, getValueJ)(TCN_STDARGS, jlong key,
                                               jstring name)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    LONG rc;
    DWORD t, l;
    UINT64 v = 0;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    TCN_INIT_WSTRING(name);
    rc = RegQueryValueExW(k->key, J2W(name), NULL, &t, NULL, &l);
    if (rc != ERROR_SUCCESS) {
        tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
        goto cleanup;
    }
    if (t == REG_DWORD) {
        DWORD tv;
        l = sizeof(DWORD);
        rc = RegQueryValueExW(k->key, J2W(name), NULL, NULL, (LPBYTE)&tv, &l);
        if (rc != ERROR_SUCCESS) {
            tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
            goto cleanup;
        }
        v = tv;
    }
    else if (t == REG_QWORD) {
        l = sizeof(UINT64);
        rc = RegQueryValueExW(k->key, J2W(name), NULL, NULL, (LPBYTE)&v, &l);
        if (rc != ERROR_SUCCESS) {
            tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
            goto cleanup;
        }
    }
    else if (t == REG_SZ || t == REG_BINARY ||
             t == REG_MULTI_SZ || t == REG_EXPAND_SZ)
        v = l;
    else {
        v = 0;
        tcn_ThrowException(e, "Unable to convert the value to long");
    }
cleanup:
    TCN_FREE_WSTRING(name);
    return v;
}

TCN_IMPLEMENT_CALL(jstring, Registry, getValueS)(TCN_STDARGS, jlong key,
                                                 jstring name)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    LONG rc;
    DWORD t, l;
    jstring v = NULL;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    TCN_INIT_WSTRING(name);
    rc = RegQueryValueExW(k->key, J2W(name), NULL, &t, NULL, &l);
    if (rc != ERROR_SUCCESS) {
        tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
        goto cleanup;
    }
    if (t == REG_SZ || t == REG_EXPAND_SZ) {
        jchar *vw = (jchar *)malloc(l);
        rc = RegQueryValueExW(k->key, J2W(name), NULL, NULL, (LPBYTE)vw, &l);
        if (rc != ERROR_SUCCESS) {
            tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
            free(vw);
            goto cleanup;
        }
        v = (*e)->NewString((e), vw, wcslen(vw));
        free(vw);
    }
cleanup:
    TCN_FREE_WSTRING(name);
    return v;
}

TCN_IMPLEMENT_CALL(jbyteArray, Registry, getValueB)(TCN_STDARGS, jlong key,
                                                    jstring name)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    LONG rc;
    DWORD t, l;
    jbyteArray v = NULL;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    TCN_INIT_WSTRING(name);
    rc = RegQueryValueExW(k->key, J2W(name), NULL, &t, NULL, &l);
    if (rc != ERROR_SUCCESS) {
        tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
        goto cleanup;
    }
    if (t == REG_BINARY) {
        BYTE *b = (BYTE *)malloc(l);
        rc = RegQueryValueExW(k->key, J2W(name), NULL, NULL, b, &l);
        if (rc != ERROR_SUCCESS) {
            tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
            free(b);
            goto cleanup;
        }
        v = tcn_new_arrayb(e, b, l);
        free(b);
    }
cleanup:
    TCN_FREE_WSTRING(name);
    return v;
}

static jsize get_multi_sz_count(LPCWSTR str)
{
    LPCWSTR p = str;
    jsize   cnt = 0;
    for ( ; p && *p; p++) {
        cnt++;
        while (*p)
            p++;
    }
    return cnt;
}

TCN_IMPLEMENT_CALL(jobjectArray, Registry, getValueA)(TCN_STDARGS, jlong key,
                                                      jstring name)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    LONG rc;
    DWORD t, l;
    jobjectArray v = NULL;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    TCN_INIT_WSTRING(name);
    rc = RegQueryValueExW(k->key, J2W(name), NULL, &t, NULL, &l);
    if (rc != ERROR_SUCCESS) {
        tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
        goto cleanup;
    }
    if (t == REG_MULTI_SZ) {
        jsize cnt = 0;
        jchar *p;
        jchar *vw = (jchar *)malloc(l);
        rc = RegQueryValueExW(k->key, J2W(name), NULL, NULL, (LPBYTE)vw, &l);
        if (rc != ERROR_SUCCESS) {
            tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
            free(vw);
            goto cleanup;
        }
        cnt = get_multi_sz_count(vw);
        if (cnt) {
            jsize idx = 0;
            v = tcn_new_arrays(e, cnt);
            for (p = vw ; p && *p; p++) {
                jstring s;
                jchar *b = p;
                while (*p)
                    p++;
                s = (*e)->NewString((e), b, (jsize)(p - b));
                (*e)->SetObjectArrayElement((e), v, idx++, s);
            }
        }
        free(vw);
    }
cleanup:
    TCN_FREE_WSTRING(name);
    return v;
}

TCN_IMPLEMENT_CALL(jint, Registry, setValueI)(TCN_STDARGS, jlong key,
                                              jstring name, jint val)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    LONG rc;
    DWORD v = (DWORD)val;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    TCN_INIT_WSTRING(name);
    rc = RegSetValueExW(k->key, J2W(name), 0, REG_DWORD, (CONST BYTE *)&v, sizeof(DWORD));
    TCN_FREE_WSTRING(name);
    return v;
}

TCN_IMPLEMENT_CALL(jint, Registry, setValueJ)(TCN_STDARGS, jlong key,
                                              jstring name, jlong val)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    LONG rc;
    UINT64 v = (UINT64)val;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    TCN_INIT_WSTRING(name);
    rc = RegSetValueExW(k->key, J2W(name), 0, REG_QWORD, (CONST BYTE *)&v, sizeof(UINT64));
    TCN_FREE_WSTRING(name);
    return rc;
}

TCN_IMPLEMENT_CALL(jint, Registry, setValueS)(TCN_STDARGS, jlong key,
                                              jstring name, jstring val)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    TCN_ALLOC_WSTRING(val);
    LONG rc;
    DWORD len;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    TCN_INIT_WSTRING(name);
    TCN_INIT_WSTRING(val);
    len = wcslen(J2W(val));
    rc = RegSetValueExW(k->key, J2W(name), 0, REG_SZ,
                        (CONST BYTE *)J2W(val), (len + 1) * 2);
    TCN_FREE_WSTRING(name);
    TCN_FREE_WSTRING(val);
    return rc;
}

TCN_IMPLEMENT_CALL(jint, Registry, setValueE)(TCN_STDARGS, jlong key,
                                              jstring name, jstring val)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    TCN_ALLOC_WSTRING(val);
    LONG rc;
    DWORD len;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    TCN_INIT_WSTRING(name);
    TCN_INIT_WSTRING(val);
    len = wcslen(J2W(val));
    rc = RegSetValueExW(k->key, J2W(name), 0, REG_EXPAND_SZ,
                        (CONST BYTE *)J2W(val), (len + 1) * 2);
    TCN_FREE_WSTRING(name);
    TCN_FREE_WSTRING(val);
    return rc;
}

TCN_IMPLEMENT_CALL(jint, Registry, setValueA)(TCN_STDARGS, jlong key,
                                              jstring name,
                                              jobjectArray vals)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    LONG rc;
    jsize i, len;
    jsize sl = 0;
    jchar *msz, *p;
    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    TCN_INIT_WSTRING(name);
    len = (*e)->GetArrayLength((e), vals);
    for (i = 0; i < len; i++) {
        jstring s = (jstring)(*e)->GetObjectArrayElement((e), vals, i);
        sl += (*e)->GetStringLength((e), s) + 1;
    }
    sl = (sl + 1) * 2;
    p = msz = (jchar *)calloc(1, sl);
    for (i = 0; i < len; i++) {
        jsize   l;
        jstring s = (jstring)(*e)->GetObjectArrayElement((e), vals, i);
        l = (*e)->GetStringLength((e), s);
        wcsncpy(p, (*e)->GetStringChars(e, s, 0), l);
        p += l + 1;
    }
    rc = RegSetValueExW(k->key, J2W(name), 0, REG_MULTI_SZ,
                        (CONST BYTE *)msz, sl);
    TCN_FREE_WSTRING(name);
    free(msz);
    return rc;
}

TCN_IMPLEMENT_CALL(jint, Registry, setValueB)(TCN_STDARGS, jlong key,
                                              jstring name,
                                              jbyteArray val)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    TCN_ALLOC_WSTRING(name);
    jsize nbytes = (*e)->GetArrayLength(e, val);
    jbyte *bytes = (*e)->GetByteArrayElements(e, val, NULL);
    LONG rc;

    rc = RegSetValueExW(k->key, J2W(name), 0, REG_BINARY,
                        bytes, (DWORD)nbytes);
    (*e)->ReleaseByteArrayElements(e, val, bytes, JNI_ABORT);
    TCN_FREE_WSTRING(name);
    return rc;
}

#define MAX_VALUE_NAME 4096

TCN_IMPLEMENT_CALL(jobjectArray, Registry, enumKeys)(TCN_STDARGS, jlong key)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    LONG rc;
    jobjectArray v = NULL;
    jsize cnt = 0;

    WCHAR    achKey[MAX_PATH];
    WCHAR    achClass[MAX_PATH] = L"";
    DWORD    cchClassName = MAX_PATH;
    DWORD    cSubKeys;
    DWORD    cbMaxSubKey;
    DWORD    cchMaxClass;
    DWORD    cValues;
    DWORD    cchMaxValue;
    DWORD    cbMaxValueData;
    DWORD    cbSecurityDescriptor;
    FILETIME ftLastWriteTime;

    DWORD cchValue = MAX_VALUE_NAME;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    rc = RegQueryInfoKeyW(k->key,
                          achClass,
                          &cchClassName,
                          NULL,
                          &cSubKeys,
                          &cbMaxSubKey,
                          &cchMaxClass,
                          &cValues,
                          &cchMaxValue,
                          &cbMaxValueData,
                          &cbSecurityDescriptor,
                          &ftLastWriteTime);
    if (rc != ERROR_SUCCESS) {
        tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
        goto cleanup;
    }
    cnt = cSubKeys;
    if (cnt) {
        jsize idx = 0;
        v = tcn_new_arrays(e, cnt);
        for (idx = 0; idx < cnt; idx++) {
            jstring s;
            DWORD achKeyLen = MAX_PATH;
            rc = RegEnumKeyExW(k->key,
                               idx,
                               achKey,
                               &achKeyLen,
                               NULL,
                               NULL,
                               NULL,
                               &ftLastWriteTime);
            if (rc == (DWORD)ERROR_SUCCESS) {
                s = (*e)->NewString((e), achKey, wcslen(achKey));
                (*e)->SetObjectArrayElement((e), v, idx, s);
            }
        }
    }
cleanup:
    return v;
}

TCN_IMPLEMENT_CALL(jobjectArray, Registry, enumValues)(TCN_STDARGS, jlong key)
{
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);
    LONG rc;
    jobjectArray v = NULL;
    jsize cnt = 0;

    WCHAR    achClass[MAX_PATH] = L"";
    DWORD    cchClassName = MAX_PATH;
    DWORD    cSubKeys;
    DWORD    cbMaxSubKey;
    DWORD    cchMaxClass;
    DWORD    cValues;
    DWORD    cchMaxValue;
    DWORD    cbMaxValueData;
    DWORD    cbSecurityDescriptor;
    FILETIME ftLastWriteTime;

    WCHAR  achValue[MAX_VALUE_NAME];
    DWORD  cchValue = MAX_VALUE_NAME;

    UNREFERENCED(o);
    TCN_ASSERT(key != 0);
    /* Get the class name and the value count. */
    rc = RegQueryInfoKeyW(k->key,
                          achClass,
                          &cchClassName,
                          NULL,
                          &cSubKeys,
                          &cbMaxSubKey,
                          &cchMaxClass,
                          &cValues,
                          &cchMaxValue,
                          &cbMaxValueData,
                          &cbSecurityDescriptor,
                          &ftLastWriteTime);
    if (rc != ERROR_SUCCESS) {
        tcn_ThrowAPRException(e, APR_FROM_OS_ERROR(rc));
        goto cleanup;
    }
    cnt = cValues;
    if (cnt) {
        jsize idx = 0;
        v = tcn_new_arrays(e, cnt);
        for (idx = 0; idx < cnt; idx++) {
            jstring s;
            cchValue = MAX_VALUE_NAME;
            achValue[0] = '\0';
            rc = RegEnumValueW(k->key, idx,
                               achValue,
                               &cchValue,
                               NULL,
                               NULL,    // &dwType,
                               NULL,    // &bData,
                               NULL);   // &bcData
            if (rc == (DWORD)ERROR_SUCCESS) {
                s = (*e)->NewString((e), achValue, wcslen(achValue));
                (*e)->SetObjectArrayElement((e), v, idx, s);
            }
        }
    }
cleanup:
    return v;
}

TCN_IMPLEMENT_CALL(jint, Registry, deleteKey)(TCN_STDARGS, jint root, jstring name,
                                              jboolean only_if_empty)
{
    DWORD rv;
    TCN_ALLOC_WSTRING(name);

    UNREFERENCED(o);
    if (root < TCN_HKEY_CLASSES_ROOT || root > TCN_HKEY_USERS) {
        rv = EBADF;
        goto cleanup;
    }
    if (only_if_empty)
        rv = SHDeleteEmptyKeyW(TCN_KEYS[root].k, J2W(name));
    else
        rv = SHDeleteKeyW(TCN_KEYS[root].k, J2W(name));
cleanup:
    TCN_FREE_WSTRING(name);
    return rv;
}

TCN_IMPLEMENT_CALL(jint, Registry, deleteValue)(TCN_STDARGS, jlong key,
                                                jstring name)
{
    LONG rv;
    TCN_ALLOC_WSTRING(name);
    tcn_nt_registry_t *k = J2P(key, tcn_nt_registry_t *);

    UNREFERENCED(o);
    rv = RegDeleteValueW(k->key, J2W(name));
    TCN_FREE_WSTRING(name);
    return (jint)rv;
}
