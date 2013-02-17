#!/bin/sh
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#
# BEFORE releasing don't forget to edit and commit
#        native/include/tcn_version.h
#        native/os/win32/libtcnative.rc

# Default place to look for apr source.  Can be overridden with 
#   --with-apr=[directory]
apr_src_dir=`pwd`/srclib/apr
JKJNIEXT=""
JKJNIVER=""
SVNBASE=https://svn.apache.org/repos/asf/tomcat/native
TCTRUNK_SVNBASE=https://svn.apache.org/repos/asf/tomcat/trunk

for o
do
    case "$o" in
    -*=*) a=`echo "$o" | sed 's/^[-_a-zA-Z0-9]*=//'` ;;
       *) a='' ;;
    esac
    case "$o" in
        --ver*=*    )
            JKJNIEXT="$a"
            shift
            ;;
        --with-apr=* )
            apr_src_dir="$a" ;
            shift ;;
        *  )
        echo ""
        echo "Usage: jnirelease.sh [options]"
        echo "  --ver[sion]=<version>  Tomcat Native version"
        echo "  --with-apr=<directory> APR sources directory"
    echo ""
        exit 1
        ;;
    esac
done


if [ -d "$apr_src_dir" ]; then
    echo ""
    echo "Using apr source from: \`$apr_src_dir'"
else
    echo ""
    echo "Problem finding apr source in: \`$apr_src_dir'"
    echo "Use:"
    echo "  --with-apr=<directory>" 
    echo ""
    exit 1
fi

if [ "x$JKJNIEXT" = "x" ]; then
    echo ""
    echo "Unknown SVN version"
    echo "Use:"
    echo "  --ver=<tagged-version>|trunk"
    echo ""
    exit 1
fi

# Check for links, elinks or w3m
w3m_opts="-dump -cols 80 -t 4 -S -O iso-8859-1 -T text/html"
elinks_opts="-dump -dump-width 80 -dump-charset iso-8859-1 -no-numbering -no-references -no-home"
links_opts="-dump -width 80 -codepage iso-8859-1 -no-g -html-numbered-links 0"
EXPTOOL=""
EXPOPTS=""
for i in w3m elinks links
do
    EXPTOOL="`which $i 2>/dev/null || type $i 2>&1`"
    if [ -x "$EXPTOOL" ]; then
        case ${i} in
          w3m)
            EXPOPTS="${w3m_opts}"
            ;;
          elinks)
            EXPOPTS="${elinks_opts}"
            ;;
          links)
            EXPOPTS="${links_opts}"
            ;;
        esac
        echo "Using: ${EXPTOOL} ${EXPOPTS} ..."
        break
    fi
done
if [ ! -x "$EXPTOOL" ]; then
    echo ""
    echo "Cannot find html export tool"
    echo "Make sure you have either w3m elinks or links in the PATH"
    echo ""
    exit 1
fi
PERL="`which perl 2>/dev/null || type perl 2>&1`"
if [ -x "$PERL" ]; then
  echo "Using $PERL"
else
  echo ""
  echo "Cannot find perl"
  echo "Make sure you have perl in the PATH"
  echo ""
  exit 1
fi

JKJNISVN=$SVNBASE/${JKJNIEXT}
if [ "x$JKJNIEXT" = "xtrunk" ]; then
    i="`svn info`"
    JKJNIVER=`echo "$i" | awk '$1 == "Revision:" {print $2}'`
    JKJNISVN=`echo "$i" | awk '$1 == "URL:" {print $2}'`
else
    JKJNIVER=$JKJNIEXT
    JKJNISVN="${SVNBASE}/tags/TOMCAT_NATIVE_`echo $JKJNIVER | sed 's/\./_/g'`"
fi
echo "Using SVN repo       : \`${JKJNISVN}'"
echo "Using version        : \`${JKJNIVER}'"

# Checking for recentness of svn:externals
externals_path=java/org/apache/tomcat
jni_externals=`svn propget svn:externals $JKJNISVN/$externals_path | \
    grep $externals_path/jni | \
    sed -e 's#.*@##' -e 's# .*##'`
jni_last_changed=`svn info --xml $TCTRUNK_SVNBASE/$externals_path/jni | \
    tr "\n" " " | \
    sed -e 's#.*commit  *revision="##' -e 's#".*##'`
if [ "x$jni_externals" != "x$jni_last_changed" ]; then
  echo "WARNING: svn:externals for jni in $externals_path is '$jni_externals',"
  echo "         last changed revision in TC trunk is '$jni_last_changed'."
  echo "         If you want to correct, cancel script now and run"
  echo "         'svn propedit svn:externals' on $externals_path to fix"
  echo "         the revision number."
  sleep 3
  exit 1
fi

JKJNIDIST=tomcat-native-${JKJNIVER}-src

rm -rf ${JKJNIDIST}
mkdir -p ${JKJNIDIST}/jni
for i in native java xdocs examples test build.xml build.properties.default jnirelease.sh
do
    svn export ${JKJNISVN}/${i} ${JKJNIDIST}/jni/${i}
    if [ $? -ne 0 ]; then
        echo ""
        echo "svn export ${i} failed"
        echo ""
    fi
done

# check the release if release.
if [ "x$JKJNIEXT" = "xtrunk" ]; then
   echo "Not a release"
else
   grep TCN_IS_DEV_VERSION ${JKJNIDIST}/jni/native/include/tcn_version.h | grep 0
   if [ $? -ne 0 ]; then
     echo "Check: ${JKJNIDIST}/jni/native/include/tcn_version.h it says -dev"
     exit 1
   fi
   WIN_VERSION=`grep TCN_VERSION ${JKJNIDIST}/jni/native/os/win32/libtcnative.rc | grep define | awk ' { print $3 } '`
   if [ "x\"$JKJNIVER\"" != "x$WIN_VERSION" ]; then
     echo "Check: ${JKJNIDIST}/jni/native/os/win32/libtcnative.rc says $WIN_VERSION (FILEVERSION, PRODUCTVERSION, TCN_VERSION"
     exit 1
   fi
fi

top="`pwd`"
cd ${JKJNIDIST}/jni/xdocs
ant
$EXPTOOL $EXPOPTS ../build/docs/miscellaneous/printer/changelog.html > ../../CHANGELOG.txt 2>/dev/null
if [ $? -ne 0 ]; then
    echo ""
    echo "$EXPTOOL $EXPOPTS ../build/docs/miscellaneous/printer/changelog.html failed"
    echo ""
    exit 1
fi
cd "$top"
mv ${JKJNIDIST}/jni/build/docs ${JKJNIDIST}/jni/docs
rm -rf ${JKJNIDIST}/jni/build
for i in LICENSE NOTICE README.txt TODO.txt
do
    svn cat ${JKJNISVN}/${i} > ${JKJNIDIST}/${i}
    if [ $? -ne 0 ]; then
        echo ""
        echo "svn cat ${JKJNISVN}/${i} failed"
        echo ""
        exit 1
    fi
done
#
# Prebuild
cd ${JKJNIDIST}/jni/native
./buildconf --with-apr=$apr_src_dir || exit 1
cd "$top"
# Create source distribution
tar -cf - ${JKJNIDIST} | gzip -c9 > ${JKJNIDIST}.tar.gz || exit 1
#
# Create Win32 source distribution
JKWINDIST=tomcat-native-${JKJNIVER}-win32-src
rm -rf ${JKWINDIST}
mkdir -p ${JKWINDIST}/jni
for i in native java xdocs examples test build.xml build.properties.default jnirelease.sh
do
    svn export --native-eol CRLF ${JKJNISVN}/${i} ${JKWINDIST}/jni/${i}
    if [ $? -ne 0 ]; then
        echo ""
        echo "svn export ${i} failed"
        echo ""
        exit 1
    fi
done
top="`pwd`"
cd ${JKWINDIST}/jni/xdocs
ant
if [ $? -ne 0 ]; then
    echo ""
    echo "ant (building docs failed)"
    echo ""
    exit 1
fi
$EXPTOOL $EXPOPTS ../build/docs/miscellaneous/printer/changelog.html > ../../CHANGELOG.txt 2>/dev/null
if [ $? -ne 0 ]; then
    echo ""
    echo "$EXPTOOL $EXPOPTS ../build/docs/miscellaneous/printer/changelog.html failed"
    echo ""
    exit 1
fi
cd "$top"

mv ${JKWINDIST}/jni/build/docs ${JKWINDIST}/jni/docs
rm -rf ${JKWINDIST}/jni/build
for i in LICENSE NOTICE README.txt TODO.txt
do
    svn cat ${JKJNISVN}/${i} > ${JKWINDIST}/${i}
    $PERL ${JKWINDIST}/jni/native/build/lineends.pl --cr ${JKWINDIST}/${i}
done
$PERL ${JKWINDIST}/jni/native/build/lineends.pl --cr ${JKWINDIST}/CHANGELOG.txt
zip -9rqyo ${JKWINDIST}.zip ${JKWINDIST}
