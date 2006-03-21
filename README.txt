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
