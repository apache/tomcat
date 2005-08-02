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
# Replace JKJNITAG with real tag, like TOMCAT_NATIVE_1_1_0
JKJNITAG=HEAD
# Replace JKJNIEXT with tagged version number, like 1.1.0
JKJNIEXT="current"
JKJNIVER="-${JKJNIEXT}"
JKJNICVST=jakarta-tomcat-connectors
export CVSROOT=:pserver:anoncvs@cvs.apache.org:/home/cvspublic
JKJNIDIST=tomcat-native${JKJNIVER}
rm -rf ${JKJNIDIST}
rm -f ${JKJNIDIST}.*
cvs export -N -r $JKJNITAG jakarta-tomcat-connectors/KEYS
cvs export -N -r $JKJNITAG jakarta-tomcat-connectors/LICENSE
cvs export -N -r $JKJNITAG jakarta-tomcat-connectors/NOTICE
cvs export -N -r $JKJNITAG jakarta-tomcat-connectors/jni/NOTICE.txt
cvs export -N -r $JKJNITAG jakarta-tomcat-connectors/jni/README.txt
cvs export -N -r $JKJNITAG jakarta-tomcat-connectors/jni/native
mv ${JKJNICVST} ${JKJNIDIST}

# Prebuild
cd ${JKJNIDIST}/jni/native
# Adjust the location of APR sources
./buildconf --with-apr=../../../srclib/apr
cd ../../../
# Create distribution
tar cvf ${JKJNIDIST}.tar ${JKJNIDIST}
gzip ${JKJNIDIST}.tar
# Convert lineends to DOS
perl srclib/apr/lineends.pl --cr ${JKJNIDIST}
zip -9 -r  ${JKJNIDIST}.zip ${JKJNIDIST}
