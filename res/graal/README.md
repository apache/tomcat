Introduction
===

GraalVM is a polyglot virtual machine. In addition to that, it supports Ahead of Time, AOT, compilation of Java applications 
into native executable files via its [native-image`](https://github.com/oracle/graal/tree/master/substratevm) compiler.

Reflection Directives
===

This directory contains directives to the compiler on what classes use reflection.
These are currently stored in a file called `tomcat-reflection.json` in the `META-INF/native-image/groupId/artifactId` 
location.

This directory also contains resource directives, so that resource files normally included in a JAR file 
also get compiled into the executable image. 
These are currently stored in a file called `tomcat-resource.json` in the `META-INF/native-image/groupId/artifactId` 
location.

