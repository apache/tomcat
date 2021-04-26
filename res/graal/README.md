<!--

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.

-->

Introduction
===

GraalVM is a polyglot virtual machine. In addition to that, it supports Ahead of Time,
AOT, compilation of Java applications into native executable files via its
[native-image`](https://github.com/oracle/graal/tree/master/substratevm) compiler.

Reflection Directives
===

This directory contains directives to the compiler on what classes use reflection.
These are currently stored in a file called `tomcat-reflection.json` in the `META-INF/native-image/groupId/artifactId`
location.

This directory also contains resource directives, so that resource files normally included in a JAR file
also get compiled into the executable image.
These are currently stored in a file called `tomcat-resource.json` in the `META-INF/native-image/groupId/artifactId`
location.

