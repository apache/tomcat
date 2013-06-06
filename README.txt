
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
included in HTML format in the jni/docs directory.
The most up-to-date documentation can be found at
http://tomcat.apache.org/native-doc/

Documentation about the Tomcat APR connector which is based
on this library can be found at
http://tomcat.apache.org/tomcat-7.0-doc/apr.html.


Building
--------

To build the Java API. Note that Java 1.7 is required to build the Java API.
> ant

To build the native part see native/BUILDING


Running the tests
-----------------

First run "ant download" to retrieve junit. It will be placed
in the directory given by "base.path". The path can be changed
by adjusting "base.path" in the file build.properties.default
or overwrite it in a new file build.properties.

Now run "ant test".


Running the examples
--------------------

Before running the examples you may have to set LD_LIBRARY_PATH, something like
LD_LIBRARY_PATH=/opt/SMAWoIS/openssl/lib; export LD_LIBRARY_PATH

1) echo example:
   - Choose some free port in
     dist/classes/examples/org/apache/tomcat/jni/Echo.properties
   - run: ant run-echo

2) ssl server example:
   - Change parameters in dist/classes/examples/org/apache/tomcat/jni/SSL.properties
     according to your needs. The certificate and key should be in
     dist/classes/examples.
   - run: ant run-ssl-server


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
