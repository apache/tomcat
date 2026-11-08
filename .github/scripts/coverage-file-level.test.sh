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
script="${script_dir}/coverage-file-level.py"

fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

cat > "$fixture/jacoco.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<report name="Test">
  <package name="org/apache/tomcat/util/http/parser">
    <sourcefile name="MediaType.java">
      <counter type="LINE" missed="3" covered="9"/>
    </sourcefile>
    <sourcefile name="HttpParser.java">
      <counter type="LINE" missed="0" covered="10"/>
    </sourcefile>
    <sourcefile name="Rounding.java">
      <counter type="LINE" missed="3" covered="5"/>
    </sourcefile>
  </package>
  <package name="org/apache/tomcat">
    <sourcefile name="Marker.java">
      <counter type="CLASS" missed="0" covered="1"/>
    </sourcefile>
  </package>
</report>
XML

out="$(printf '%s\n' \
  'java/org/apache/tomcat/util/http/parser/MediaType.java' \
  'java/org/apache/tomcat/util/http/parser/Rounding.java' \
  'java/org/apache/tomcat/Marker.java' \
  'java/org/apache/catalina/Newbie.java' \
  'docs/foo.xml' \
  | python3 "$script" "$fixture/jacoco.xml")"

echo "--- output ---"; echo "$out"; echo "--------------"

fail=0
check() { if echo "$out" | grep -qF "$1"; then echo "ok   - $2"; else echo "FAIL - $2"; fail=1; fi }
reject() { if echo "$out" | grep -qF "$1"; then echo "FAIL - $2"; fail=1; else echo "ok   - $2"; fi }

check  '9/12 | 75%'          "MediaType computed as 9/12 = 75%"
check  '5/8 | 63%'           "Rounding uses round-half-up (5/8 -> 63%, not 62%)"
check  'no executable lines' "sourcefile with no LINE counter labelled distinctly"
check  'not in report'       "file absent from report is flagged"
check  'Newbie.java'         "absent file still listed"
reject 'docs/foo.xml'        "non-java path ignored"
reject 'HttpParser.java'     "unchanged file not listed"

# A malformed / truncated report must degrade gracefully: no crash, no table, exit 0
# (the feature's guarantee is "report-only, never fail the PR").
printf '%s' '<report><package name="org/apache/foo"><sourcefile name="Bar.jav' > "$fixture/bad.xml"
if bad_out="$(printf 'java/org/apache/foo/Bar.java\n' | python3 "$script" "$fixture/bad.xml" 2>/dev/null)"; then
  if [ -z "$bad_out" ]; then
    echo "ok   - malformed XML degrades gracefully (empty output, exit 0)"
  else
    echo "FAIL - malformed XML should produce no table"; fail=1
  fi
else
  echo "FAIL - malformed XML must not crash the step (expected exit 0)"; fail=1
fi

exit "$fail"
