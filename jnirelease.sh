#/bin/sh
#
# Copyright 1999-2006 The Apache Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Default place to look for apr source.  Can be overridden with 
#   --with-apr=[directory]
apr_src_dir=../apr

while test $# -gt 0 
do
  # Normalize
  case "$1" in
  -*=*) optarg=`echo "$1" | sed 's/[-_a-zA-Z0-9]*=//'` ;;
  *) optarg= ;;
  esac

  case "$1" in
  --with-apr=*)
  apr_src_dir=$optarg
  ;;
  esac

  shift
done

if test -d "$apr_src_dir"
then
  echo ""
  echo "Looking for apr source in $apr_src_dir"
else
  echo ""
  echo "Problem finding apr source in $apr_src_dir."
  echo "Use:"
  echo "  --with-apr=[directory]" 
  exit 1
fi

# Replace JKJNIEXT with branch/or tag
# and JKJNIVER by the version like 1.1.0
JKJNIEXT=trunk
JKJNIVER=current
# JKJNIVER="1.1.6"
SVNBASE=https://svn.apache.org/repos/asf/tomcat/connectors/
JKJNIDIST=tomcat-connectors-${JKJNIVER}-src
rm -rf ${JKJNIDIST}
mkdir -p ${JKJNIDIST}/jni
svn export $SVNBASE/${JKJNIEXT}/jni/native ${JKJNIDIST}/jni/native
svn cat $SVNBASE/${JKJNIEXT}/KEYS > ${JKJNIDIST}/KEYS
svn cat $SVNBASE/${JKJNIEXT}/LICENSE > ${JKJNIDIST}/LICENSE
svn cat $SVNBASE/${JKJNIEXT}/NOTICE > ${JKJNIDIST}/NOTICE
svn cat $SVNBASE/${JKJNIEXT}/jni/NOTICE.txt > ${JKJNIDIST}/NOTICE.txt
svn cat $SVNBASE/${JKJNIEXT}/jni/README.txt > ${JKJNIDIST}/README.txt
#
# Prebuild
cd ${JKJNIDIST}/jni/native
./buildconf --with-apr=$apr_src_dir
cd ../../../
# Create source distribution
tar cfz ${JKJNIDIST}.tar.gz ${JKJNIDIST}
#
# Create Win32 source distribution
JKJNIDIST=tomcat-connectors-${JKJNIVER}-win32-src
rm -rf ${JKJNIDIST}
mkdir -p ${JKJNIDIST}/jni
svn export --native-eol CRLF $SVNBASE/${JKJNIEXT}/jni/native ${JKJNIDIST}/jni/native
svn cat $SVNBASE/${JKJNIEXT}/KEYS > ${JKJNIDIST}/KEYS
svn cat $SVNBASE/${JKJNIEXT}/LICENSE > ${JKJNIDIST}/LICENSE
svn cat $SVNBASE/${JKJNIEXT}/NOTICE > ${JKJNIDIST}/NOTICE
svn cat $SVNBASE/${JKJNIEXT}/jni/NOTICE.txt > ${JKJNIDIST}/NOTICE.txt
svn cat $SVNBASE/${JKJNIEXT}/jni/README.txt > ${JKJNIDIST}/README.txt
zip -9rqo ${JKJNIDIST}.zip ${JKJNIDIST}
