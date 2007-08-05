#!/bin/sh

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# extract version numbers from a header file
#
# USAGE: get-version.sh CMD VERSION_HEADER PREFIX
#   where CMD is one of: all, major, libtool
#   where PREFIX is the prefix to {MAJOR|MINOR|PATCH}_VERSION defines
#
#   get-version.sh all returns a dotted version number
#   get-version.sh major returns just the major version number
#   get-version.sh libtool returns a version "libtool -version-info" format
#

if test $# != 3; then
  echo "USAGE: $0 CMD VERSION_HEADER PREFIX"
  echo "  where CMD is one of: all, major, libtool"
  exit 1
fi

major_sed="/#define.*$3_MAJOR_VERSION/s/^[^0-9]*\([0-9]*\).*$/\1/p"
minor_sed="/#define.*$3_MINOR_VERSION/s/^[^0-9]*\([0-9]*\).*$/\1/p"
patch_sed="/#define.*$3_PATCH_VERSION/s/^[^0-9]*\([0-9]*\).*$/\1/p"
major="`sed -n $major_sed $2`"
minor="`sed -n $minor_sed $2`"
patch="`sed -n $patch_sed $2`"

if test "$1" = "all"; then
  echo ${major}.${minor}.${patch}
elif test "$1" = "major"; then
  echo ${major}
elif test "$1" = "libtool"; then
  # Yes, ${minor}:${patch}:${minor} is correct due to libtool idiocy.
  echo ${minor}:${patch}:${minor}
else
  echo "ERROR: unknown version CMD ($1)"
  exit 1
fi
