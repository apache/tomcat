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
 * @version $Id$
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
 * @file tcn_api.h
 * @brief
 *
 * Tomcat Native Public API
 */

/* Return global apr pool
 */
apr_pool_t *tcn_get_global_pool(void);

/* Return global String class
 */
jclass tcn_get_string_class(void);

/* Return global JVM initalized on JNI_OnLoad
 */
JavaVM *tcn_get_java_vm(void);

/* Get current thread JNIEnv
 */
jint tcn_get_java_env(JNIEnv **);

#ifdef __cplusplus
}
#endif

#endif /* TCN_API_H */
