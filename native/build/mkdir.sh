#!/bin/sh

#
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

## 
##  mkdir.sh -- make directory hierarchy
##
##  Based on `mkinstalldirs' from Noah Friedman <friedman@prep.ai.mit.edu>
##  as of 1994-03-25, which was placed in the Public Domain.
##  Cleaned up for Apache's Autoconf-style Interface (APACI)
##  by Ralf S. Engelschall <rse@apache.org>
##


umask 022
errstatus=0
for file in ${1+"$@"} ; do 
    set fnord `echo ":$file" |\
               sed -e 's/^:\//%/' -e 's/^://' -e 's/\// /g' -e 's/^%/\//'`
    shift
    pathcomp=
    for d in ${1+"$@"}; do
        pathcomp="$pathcomp$d"
        case "$pathcomp" in
            -* ) pathcomp=./$pathcomp ;;
            ?: ) pathcomp="$pathcomp/" 
                 continue ;;
        esac
        if test ! -d "$pathcomp"; then
            echo "mkdir $pathcomp" 1>&2
            mkdir "$pathcomp" || errstatus=$?
        fi
        pathcomp="$pathcomp/"
    done
done
exit $errstatus

