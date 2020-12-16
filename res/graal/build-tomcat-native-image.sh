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

cd $PRGDIR/../..
ant clean && ant && ant embed && ant test-compile

mkdir -p output/graal
cd output/testclasses
jar cvfM ../graal/tomcat-embedded-sample.jar org/apache/catalina/startup/EmbeddedTomcat*class

cd ../graal

native-image \
--verbose \
--no-server \
-H:EnableURLProtocols=http \
--report-unsupported-elements-at-runtime \
--initialize-at-run-time=org.apache,jakarta.servlet \
-H:+TraceClassInitialization \
-H:+PrintClassInitialization \
-H:+PrintAnalysisCallTree \
-H:Name=tc-graal-image \
-H:+ReportExceptionStackTraces \
--allow-incomplete-classpath \
--no-fallback \
-cp ../embed/tomcat-embed-programmatic.jar:tomcat-embedded-sample.jar \
org.apache.catalina.startup.EmbeddedTomcat

cd $CURDIR