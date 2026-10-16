#!/usr/bin/env bash

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

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="${script_dir}/select-tests.sh"

fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

mkdir -p "$fixture/test/org/apache/tomcat/util/http/parser"
touch    "$fixture/test/org/apache/tomcat/util/http/parser/TestHttpParser.java"
touch    "$fixture/test/org/apache/tomcat/util/http/parser/TestMediaType.java"

run() { SELECT_TESTS_ROOT="$fixture" bash "$script"; }

fail=0
assert_eq() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "ok   - $desc"
  else
    echo "FAIL - $desc"; echo "       expected: [$expected]"; echo "       actual:   [$actual]"; fail=1
  fi
}

# 1. Source file with a mirror test package -> include for that package.
out="$(printf '%s\n' 'java/org/apache/tomcat/util/http/parser/MediaType.java' | run)"
assert_eq "maps source to mirror test package" \
  "org/apache/tomcat/util/http/parser/Test*.java" "$out"

# 2. Source file whose mirror package has no tests -> nothing.
out="$(printf '%s\n' 'java/org/apache/catalina/Host.java' | run)"
assert_eq "skips source with no mirror test package" "" "$out"

# 3. Two source files in the same package -> deduped to one include.
out="$(printf '%s\n' \
  'java/org/apache/tomcat/util/http/parser/MediaType.java' \
  'java/org/apache/tomcat/util/http/parser/HttpParser.java' | run)"
assert_eq "dedupes same package" \
  "org/apache/tomcat/util/http/parser/Test*.java" "$out"

# 4. Non-source paths are ignored.
out="$(printf '%s\n' 'build.xml' 'test/foo/TestBar.java' 'README.md' | run)"
assert_eq "ignores non-java-source paths" "" "$out"

# 5. Empty input -> empty output.
out="$(printf '' | run)"
assert_eq "empty input yields empty output" "" "$out"

exit "$fail"
