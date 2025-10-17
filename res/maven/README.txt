================================================================================
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
================================================================================

General preparations before any publishing:
1 - Generate a standard Tomcat release.
    This will generate a mvn.properties.release file as part of the tag. It
    should include the property settings required to complete the release.
2 - Should any of the properties need to be overridden, create a
    mvn.properties and override as necessary.

To publish a snapshot do the following:
1 - ant -f mvn-pub.xml deploy-snapshot
    This populates
    https://repository.apache.org/content/repositories/snapshots/org/apache/tomcat/

To release do the following:
1 - ant -f mvn-pub.xml deploy-release
    that step creates a staging area in
    https://repository.apache.org/index.html#stagingRepositories
2 - check the upload and then close the repository
3 - include the repository in the VOTE thread
4 - in https://repository.apache.org/index.html#stagingRepositories release it

To install in your maven repo:
1 - ant -f mvn-pub.xml generic-install
