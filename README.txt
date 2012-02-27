
            Apache Tomcat Native Library


What is it?
-----------

The Apache Tomcat Native Library provides portable API for features
not found in contemporary JDK's. It uses Apache Portable Runtime as
operating system abstraction layer and OpenSSL for SSL networking and
allows optimal performance in production environments.


Licensing
---------

Please see the file called LICENSE.

The Latest Version
------------------

Details of the latest version can be found on the Apache Tomcat
project page under http://tomcat.apache.org/.

Documentation
-------------

The documentation available as of the date of this release is
included in HTML format in the docs directory.
The most up-to-date documentation can be found at
http://tomcat.apache.org/tomcat-7.0-doc/apr.html.


Buliding
--------

To build the Java API
> ant

To build the native part see native/BUILDING


Running the examples
--------------------

Before running the examples you may have to set LD_LIBRARY_PATH, something like
LD_LIBRARY_PATH=/opt/SMAWoIS/openssl/lib; export LD_LIBRARY_PATH
1 - echo: (port in examples/org/apache/tomcat/jni/Echo.properties).
    ant echo-example
2 - ssl server:
    (see parameters in ./examples/org/apache/tomcat/jni/SSL.properties)
    The certificate and key should be in dist/classes/examples.
    ant server-example


Cryptographic Software Notice
-----------------------------

This distribution may include software that has been designed for use
with cryptographic software.  The country in which you currently reside
may have restrictions on the import, possession, use, and/or re-export
to another country, of encryption software.  BEFORE using any encryption
software, please check your country's laws, regulations and policies
concerning the import, possession, or use, and re-export of encryption
software, to see if this is permitted.  See <http://www.wassenaar.org/>
for more information.

The U.S. Government Department of Commerce, Bureau of Industry and
Security (BIS), has classified this software as Export Commodity
Control Number (ECCN) 5D002.C.1, which includes information security
software using or performing cryptographic functions with asymmetric
algorithms.  The form and manner of this Apache Software Foundation
distribution makes it eligible for export under the License Exception
ENC Technology Software Unrestricted (TSU) exception (see the BIS
Export Administration Regulations, Section 740.13) for both object
code and source code.

Apache Tomcat Native uses cryptographic software for configuring and
listening to connections over SSL encrypted network sockets by
performing calls to a general-purpose encryption library, such as
OpenSSL or the operating system's platform-specific SSL facilities.
