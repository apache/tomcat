#/bin/sh
#
# Copyright 1999-2005 The Apache Software Foundation
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
# APRDIR have to be the location of the APR sources
APRDIR=$HOME/apr
#
# Replace JKJNIEXT with branche/or tag
#  and JKJNIVER by the version like -1.1.0
JKJNIEXT="trunk"
JKJNIVER="-dev"
SVNBASE=https://svn.apache.org/repos/asf/tomcat/connectors/
JKJNIDIST=tomcat-connectors${JKJNIVER}
rm -rf ${JKJNIDIST}
mkdir -p ${JKJNIDIST}/jni
svn export $SVNBASE/${JKJNIEXT}/jni/native ${JKJNIDIST}/jni/native
svn cat $SVNBASE/${JKJNIEXT}/KEYS > ${JKJNIDIST}/KEYS
svn cat $SVNBASE/${JKJNIEXT}/LICENSE > ${JKJNIDIST}/LICENSE
svn cat $SVNBASE/${JKJNIEXT}/NOTICE > ${JKJNIDIST}/NOTICE
svn cat $SVNBASE/${JKJNIEXT}/jni/NOTICE.txt > ${JKJNIDIST}/NOTICE.txt
svn cat $SVNBASE/${JKJNIEXT}/jni/README.txt > ${JKJNIDIST}/README.txt

# Prebuild
cd ${JKJNIDIST}/jni/native
# Adjust the location of APR sources
./buildconf --with-apr=$APRDIR
cd ../../../
# Create distribution
tar cvf ${JKJNIDIST}.tar ${JKJNIDIST}
gzip ${JKJNIDIST}.tar
# Convert lineends to DOS
perl $APRDIR/build/lineends.pl --cr ${JKJNIDIST}
zip -9 -r  ${JKJNIDIST}.zip ${JKJNIDIST}
