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

This directory contains both the native and java-side code for
Tomcat Native Library.

Building
========
ant
To build the native part see native/BUILDING

Running the examples
====================
before running the examples you may have to set LD_LIBRARY_PATH, something like
LD_LIBRARY_PATH=/opt/SMAWoIS/openssl/lib; export LD_LIBRARY_PATH
1 - echo: (port in examples/org/apache/tomcat/jni/Echo.properties).
    ant echo-example
2 - ssl server:
    (see parameters in ./examples/org/apache/tomcat/jni/SSL.properties)
    The certificate and key should be in dist/classes/examples.
    ant server-example
