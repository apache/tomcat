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

#ifndef TCN_VERSION_H
#define TCN_VERSION_H

#include "apr_version.h"

#include "tcn.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @file tcn_version.h
 * @brief
 *
 * Tomcat Native Version
 *
 * There are several different mechanisms for accessing the version. There
 * is a string form, and a set of numbers; in addition, there are constants
 * which can be compiled into your application, and you can query the library
 * being used for its actual version.
 *
 * Note that it is possible for an application to detect that it has been
 * compiled against a different version of APU by use of the compile-time
 * constants and the use of the run-time query function.
 *
 * TCN version numbering follows the guidelines specified in:
 *
 *     http://apr.apache.org/versioning.html
 */

/* The numeric compile-time version constants. These constants are the
 * authoritative version numbers for TCN.
 */

/** major version
 * Major API changes that could cause compatibility problems for older
 * programs such as structure size changes.  No binary compatibility is
 * possible across a change in the major version.
 */
#define TCN_MAJOR_VERSION       1

/**
 * Minor API changes that do not cause binary compatibility problems.
 * Should be reset to 0 when upgrading TCN_MAJOR_VERSION
 */
#define TCN_MINOR_VERSION       1

/** patch level */
#define TCN_PATCH_VERSION       33

/**
 *  This symbol is defined for internal, "development" copies of TCN. This
 *  symbol will be #undef'd for releases.
 */
#define TCN_IS_DEV_VERSION      1


/** The formatted string of APU's version */
#define TCN_VERSION_STRING \
     APR_STRINGIFY(TCN_MAJOR_VERSION) "."\
     APR_STRINGIFY(TCN_MINOR_VERSION) "."\
     APR_STRINGIFY(TCN_PATCH_VERSION)\
     TCN_IS_DEV_STRING

/** Internal: string form of the "is dev" flag */
#if TCN_IS_DEV_VERSION
#define TCN_IS_DEV_STRING "-dev"
#else
#define TCN_IS_DEV_STRING ""
#endif

#ifdef __cplusplus
}
#endif

#endif /* TCN_VERSION_H */

