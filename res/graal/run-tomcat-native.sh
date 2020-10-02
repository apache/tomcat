#!/bin/bash

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

set -e

CURDIR=`pwd`

# resolve links - $0 may be a softlink
PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# directory of this script
PRGDIR=`dirname "$PRG"`
PRGDIR=`cd "$PRGDIR" >/dev/null; pwd`

if [ "$1" == "build" ]; then
  $PRGDIR/build-tomcat-native-image.sh
  shift
fi

echo "Script Directory: $PRGDIR"
if [ "$1" == "jvm" ]; then
  cd $PRGDIR/../..
  if [ ! -f output/graal/tomcat-embedded-sample.jar ]; then
    $PRGDIR/build-tomcat-native-image.sh
  fi
  cd $PRGDIR/../../output/graal
  java -Dorg.graalvm.nativeimage.imagecode=true \
    -cp ../embed/tomcat-embed-programmatic.jar:tomcat-embedded-sample.jar \
    org.apache.catalina.startup.EmbeddedTomcat
elif [ "$1" == "native" ]; then
  if [ ! -f $PRGDIR/../../output/graal/tc-graal-image ]; then
    $PRGDIR/build-tomcat-native-image.sh
  fi
  $PRGDIR/../../output/graal/tc-graal-image
else
  echo "Usage: run-tomcat-native.sh [build] jvm|native"
  exit 1
fi

cd $CURDIR