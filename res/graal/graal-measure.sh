#!/bin/bash

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

EXECUTABLE=${PRGDIR}/output/graal/tc-graal-image

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