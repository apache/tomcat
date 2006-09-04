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

#ifndef TCN_API_H
#define TCN_API_H

#include "apr.h"
#include "apr_general.h"
#include "apr_pools.h"
#include "apr_portable.h"
#include "apr_network_io.h"
#include "apr_strings.h"

#ifndef APR_HAS_THREADS
#error "Missing APR_HAS_THREADS support from APR."
#endif
#include <jni.h>

/**
 * TCN_DECLARE_EXPORT is defined when building the TCN dynamic library,
 * so that all public symbols are exported.
 *
 * TCN_DECLARE_STATIC is defined when including the TCN public headers,
 * to provide static linkage when the dynamic library may be unavailable.
 *
 * TCN_DECLARE_STATIC and TCN_DECLARE_EXPORT are left undefined when
 * including the TCN public headers, to import and link the symbols from
 * the dynamic TCN library and assure appropriate indirection and calling
 * conventions at compile time.
 */

#if !defined(WIN32)
/**
 * The public TCN functions are declared with TCN_DECLARE(), so they may
 * use the most appropriate calling convention.  Public APR functions with
 * variable arguments must use TCN_DECLARE_NONSTD().
 *
 * @deffunc TCN_DECLARE(rettype) apr_func(args);
 */
#define TCN_DECLARE(type)            type
/**
 * The public TCN functions using variable arguments are declared with
 * TCN_DECLARE_NONSTD(), as they must use the C language calling convention.
 *
 * @deffunc TCN_DECLARE_NONSTD(rettype) apr_func(args, ...);
 */
#define TCN_DECLARE_NONSTD(type)     type
/**
 * The public TCN variables are declared with TCN_DECLARE_DATA.
 * This assures the appropriate indirection is invoked at compile time.
 *
 * @deffunc TCN_DECLARE_DATA type apr_variable;
 * @tip extern TCN_DECLARE_DATA type apr_variable; syntax is required for
 * declarations within headers to properly import the variable.
 */
#define TCN_DECLARE_DATA
#elif defined(TCN_DECLARE_STATIC)
#define TCN_DECLARE(type)            type __stdcall
#define TCN_DECLARE_NONSTD(type)     type
#define TCN_DECLARE_DATA
#elif defined(TCN_DECLARE_EXPORT)
#define TCN_DECLARE(type)            __declspec(dllexport) type __stdcall
#define TCN_DECLARE_NONSTD(type)     __declspec(dllexport) type
#define TCN_DECLARE_DATA             __declspec(dllexport)
#else
/**
 * The public TCN functions are declared with TCN_DECLARE(), so they may
 * use the most appropriate calling convention.  Public APR functions with
 * variable arguments must use TCN_DECLARE_NONSTD().
 *
 */
#define TCN_DECLARE(type)            __declspec(dllimport) type __stdcall
/**
 * The public TCN functions using variable arguments are declared with
 * TCN_DECLARE_NONSTD(), as they must use the C language calling convention.
 *
 */
#define TCN_DECLARE_NONSTD(type)     __declspec(dllimport) type
/**
 * The public TCN variables are declared with TCN_DECLARE_DATA.
 * This assures the appropriate indirection is invoked at compile time.
 *
 * @remark extern TCN_DECLARE_DATA type apr_variable; syntax is required for
 * declarations within headers to properly import the variable.
 */
#define TCN_DECLARE_DATA             __declspec(dllimport)
#endif

#if !defined(WIN32) || defined(TCN_MODULE_DECLARE_STATIC)
/**
 * Declare a dso module's exported module structure as TCN_MODULE_DECLARE_DATA.
 *
 * Unless TCN_MODULE_DECLARE_STATIC is defined at compile time, symbols
 * declared with TCN_MODULE_DECLARE_DATA are always exported.
 * @code
 * module TCN_MODULE_DECLARE_DATA mod_tag
 * @endcode
 */
#if defined(WIN32)
#define TCN_MODULE_DECLARE(type)            type __stdcall
#else
#define TCN_MODULE_DECLARE(type)            type
#endif
#define TCN_MODULE_DECLARE_NONSTD(type)     type
#define TCN_MODULE_DECLARE_DATA
#else
/**
 * TCN_MODULE_DECLARE_EXPORT is a no-op.  Unless contradicted by the
 * TCN_MODULE_DECLARE_STATIC compile-time symbol, it is assumed and defined.
 */
#define TCN_MODULE_DECLARE_EXPORT
#define TCN_MODULE_DECLARE(type)          __declspec(dllexport) type __stdcall
#define TCN_MODULE_DECLARE_NONSTD(type)   __declspec(dllexport) type
#define TCN_MODULE_DECLARE_DATA           __declspec(dllexport)
#endif

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @file tcn_api.h
 * @brief
 *
 * Tomcat Native Public API
 */

/* Return global apr pool
 */
TCN_DECLARE(apr_pool_t *) tcn_get_global_pool(void);

/* Return global String class
 */
TCN_DECLARE(jclass) tcn_get_string_class(void);

/* Return global JVM initalized on JNI_OnLoad
 */
TCN_DECLARE(JavaVM *) tcn_get_java_vm(void);

/* Get current thread JNIEnv
 */
TCN_DECLARE(jint) tcn_get_java_env(JNIEnv **);

#ifdef __cplusplus
}
#endif

#endif /* TCN_API_H */
