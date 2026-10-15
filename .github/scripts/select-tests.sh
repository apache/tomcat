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

# Reads changed file paths on stdin (one repo-relative path per line) and
# prints a comma-separated Ant <fileset> includes value selecting the mirror
# test package (test/<pkg>/Test*.java) of each changed java/ source file whose
# mirror package exists and contains at least one Test*.java. Prints nothing
# if none match.
#
# Usage: git diff --name-only --diff-filter=d <base>...HEAD | select-tests.sh
set -euo pipefail

repo_root="${SELECT_TESTS_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

declare -A seen=()   # dedupe set, keyed by include pattern
includes=()          # ordered list of include patterns to emit

while IFS= read -r path; do
  # Consider only source files under java/ ending in .java.
  case "$path" in
    java/*.java) ;;
    *) continue ;;
  esac

  # Package dir: strip the leading java/ and the trailing /<Class>.java.
  pkg="$(dirname "${path#java/}")"
  [ "$pkg" = "." ] && continue

  # Exact mirror package, non-recursive.
  include="${pkg}/Test*.java"
  [ -n "${seen[$include]:-}" ] && continue

  # Only select it if the mirror package actually has a Test*.java.
  if compgen -G "${repo_root}/test/${pkg}/Test*.java" > /dev/null; then
    seen[$include]=1
    includes+=("$include")
  fi
done

if [ "${#includes[@]}" -gt 0 ]; then
  printf '%s\n' "${includes[@]}" | sort | paste -sd, -
fi
