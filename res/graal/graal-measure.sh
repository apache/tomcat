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

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

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

EXECUTABLE=${PRGDIR}/../../output/graal/tc-graal-image

./${EXECUTABLE} "$@" 2>&1 &
PID=$!
sleep 2
RESPONSE=`curl -s localhost:8080/`
if [[ "$RESPONSE" == 'OK: http://localhost:8080/[1]' ]]; then
  printf "\n${GREEN}SUCCESS${NC}: the servlet is working\n"
else
  printf "\n${RED}FAILURE${NC}: the HTTP response of the application does not contain the expected value\n"
fi

RSS=`ps -o rss ${PID} | tail -n1`
RSS=`bc <<< "scale=1; ${RSS}/1024"`
echo "RSS memory: ${RSS}M"
SIZE=`wc -c <"${EXECUTABLE}"`/1024
SIZE=`bc <<< "scale=1; ${SIZE}/1024"`
echo "Image size: ${SIZE}M"

kill -9 $PID