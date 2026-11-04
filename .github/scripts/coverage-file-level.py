#!/usr/bin/env python3
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
#
# Reads a JaCoCo XML report (argv[1]) and a list of changed repo-relative paths
# on stdin, and prints a Markdown table of per-file LINE coverage for the changed
# java/ source files. Files present in the report show covered/total and a
# round-half-up percentage; files with no executable lines (interfaces, marker
# types) are labelled "no executable lines"; files absent from the report (new
# files, or never exercised) show "not in report". Non-java/ or non-.java paths
# are ignored. Prints nothing if the report cannot be parsed or no changed file
# qualifies, so a bad/empty nightly report degrades gracefully and never fails
# the PR check.
#
# Usage: coverage-file-level.py <jacoco.xml> < changed-files.txt
import sys
import xml.etree.ElementTree as ET

# Sentinel: a source file present in the report but with no executable lines
# (e.g. an interface or annotation type) -- distinct from "absent from report".
NO_EXECUTABLE_LINES = object()


def line_coverage(xml_path):
    """Map repo path -> (covered, total), or NO_EXECUTABLE_LINES for files with
    no executable lines. Returns None if the report cannot be parsed."""
    try:
        root = ET.parse(xml_path).getroot()
    except (ET.ParseError, OSError) as exc:
        sys.stderr.write(
            "coverage-file-level: could not parse {}: {}\n".format(xml_path, exc))
        return None
    result = {}
    for package in root.iter("package"):
        pkg = package.get("name")
        for sf in package.findall("sourcefile"):
            repo_path = "java/{}/{}".format(pkg, sf.get("name"))
            line = sf.find("counter[@type='LINE']")
            if line is None:
                result[repo_path] = NO_EXECUTABLE_LINES
                continue
            covered = int(line.get("covered"))
            total = covered + int(line.get("missed"))
            result[repo_path] = (covered, total) if total else NO_EXECUTABLE_LINES
    return result


def main():
    xml_path = sys.argv[1]
    changed = [ln.strip() for ln in sys.stdin if ln.strip()]
    cov = line_coverage(xml_path)
    if cov is None:
        return  # unparseable report: emit nothing, never fail the step

    rows = []
    for path in changed:
        if not (path.startswith("java/") and path.endswith(".java")):
            continue
        if path not in cov:
            rows.append("| `{}` | — | not in report |".format(path))
        elif cov[path] is NO_EXECUTABLE_LINES:
            rows.append("| `{}` | — | no executable lines |".format(path))
        else:
            covered, total = cov[path]
            pct = int(100.0 * covered / total + 0.5)  # round half up
            rows.append("| `{}` | {}/{} | {}% |".format(path, covered, total, pct))

    if not rows:
        return
    print("| File | Lines covered | Full-suite coverage |")
    print("| --- | --- | --- |")
    print("\n".join(rows))


if __name__ == "__main__":
    main()
