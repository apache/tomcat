#!/bin/sh

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

# This script creates the directory structure required for running Tomcat
# in a separate directory by pointing $CATALINA_BASE to it. It copies the
# conf directory from $CATALINA_HOME, and creates empty directories for
# bin, lib, logs, temp, webapps, and work.
#
# If the file $CATALINA_HOME/bin/setenv.sh exists then it is copied to
# the target directory as well.
#
# Usage: makebase <path-to-target-directory>

# first arg is the target directory
BASE_TGT=$1

if [ -z ${BASE_TGT} ]; then
    # target directory not provided; exit
    echo "Usage: makebase <path-to-target-directory>"
    exit 1
fi

HOME_DIR="$(dirname $(dirname $0))"

if [ -d ${BASE_TGT} ]; then
  # target directory exists
  echo "Target directory exists"

    # exit if target directory is not empty
    [ "$(ls -A ${BASE_TGT})" ] && \
        echo "Target directory is not empty" && \
        exit 1
else
    # create the target directory
    mkdir -p ${BASE_TGT}
fi

for dir in bin lib logs temp webapps work;
do
    # copy directory with permissions and delete contents if any
    mkdir "${BASE_TGT}/${dir}"
done

# copy conf directory recursively and preserve permissions
cp -a "${HOME_DIR}/conf" "${BASE_TGT}/"

# copy setenv.sh if exists
[ -f "${HOME_DIR}/bin/setenv.sh" ] && \
    cp "${HOME_DIR}/bin/setenv.sh" "${BASE_TGT}/bin/"

echo "Created CATALINA_BASE directory at $BASE_TGT"

echo "Attention: The ports in conf/server.xml might be bound by a "
echo "           different instance. Please review your config files "
echo "           and update them where necessary."
