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

$Id$

To release do the following:
1 - copy mvn.properties.default to mvn.properties and adjust it.
2 - ant -f mvn-pub.xml deploy-release
    that step creates a staging in https://repository.apache.org/index.html#stagingRepositories
3 - test it and do the vote process
4 - in https://repository.apache.org/index.html#stagingRepositories close it and then promote it.
