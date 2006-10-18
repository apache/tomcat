#!/bin/sh
## 
##  mkdir.sh -- make directory hierarchy
##
##  Based on `mkinstalldirs' from Noah Friedman <friedman@prep.ai.mit.edu>
##  as of 1994-03-25, which was placed in the Public Domain.
##  Cleaned up for Apache's Autoconf-style Interface (APACI)
##  by Ralf S. Engelschall <rse@apache.org>
##
#
# This script falls under the Apache License.
# See http://www.apache.org/docs/LICENSE


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

