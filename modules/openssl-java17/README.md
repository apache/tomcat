# OpenSSL support for Apache Tomcat

## This module is experimental

It uses the incubating JEP 412 API. More details on this API are available
at `https://openjdk.java.net/jeps/412`.

## Building

The module can be built using Java 17. This will be the only Java version that
is supported as the JEP 412 API is incubating and will continue to evolve.

## Running

The module uses the OpenSSL 1.1 API. It requires an API compatible version of
OpenSSL or a compatible alternative library, that can be loaded from the JVM
library path.

Copy `openssl-java17-1.0.jar` to the Apache Tomcat `lib` folder.

Remove `AprLifecycleListener` from `server.xml`. The
`org.apache.tomcat.util.net.openssl.panama.OpenSSLLifecycleListener` can be
used as a replacement with the same configuration options (such as FIPS)
and shutdown cleanup, but is not required.

Define a `Connector` using the value
`org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation` for the
`sslImplementationName` attribute.

Example connector:
```
    <Connector port="8443" protocol="HTTP/1.1"
               SSLEnabled="true" scheme="https" secure="true"
               socket.directBuffer="true" socket.directSslBuffer="true"
               sslImplementationName="org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation">
        <SSLHostConfig certificateVerification="none">
            <Certificate certificateKeyFile="${catalina.home}/conf/localhost-rsa-key.pem"
                         certificateFile="${catalina.home}/conf/localhost-rsa-cert.pem"
                         certificateChainFile="${catalina.home}/conf/localhost-rsa-chain.pem"
                         type="RSA" />
        </SSLHostConfig>
        <UpgradeProtocol className="org.apache.coyote.http2.Http2Protocol" />
    </Connector>
```

Run Tomcat using the additional Java options that allow access to the API and
native code:
```
export JAVA_OPTS="--enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.foreign"
```

## Running the testsuite

Use the following patch for `build.xml` before running the testuite:
```
diff --git a/build.xml b/build.xml
index dc1260b..dd9fba9 100644
--- a/build.xml
+++ b/build.xml
@@ -213,6 +213,8 @@
   <defaultexcludes remove="**/.gitignore" />
   <!--<defaultexcludes echo="true" />-->

   <!-- Classpaths -->
   <path id="compile.classpath">
     <pathelement location="${bnd.jar}"/>
@@ -240,6 +242,7 @@
     <pathelement location="${derby.jar}"/>
     <pathelement location="${derby-shared.jar}"/>
     <pathelement location="${derby-tools.jar}"/>
+    <pathelement location="output/build/lib/openssl-java17-0.1.jar"/>
     <path refid="compile.classpath" />
     <path refid="tomcat.classpath" />
   </path>
@@ -1944,7 +1947,6 @@

           <jvmarg value="${test.jvmarg.egd}"/>
           <jvmarg value="-Dfile.encoding=UTF-8"/>
-          <jvmarg value="-Djava.library.path=${test.apr.loc}"/>
           <jvmarg value="${test.formatter}"/>
           <jvmarg value="-Djava.net.preferIPv4Stack=${java.net.preferIPv4Stack}"/>
           <jvmarg value="--add-opens=java.base/java.lang=ALL-UNNAMED"/>
@@ -1952,6 +1954,9 @@
           <jvmarg value="--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED"/>
           <jvmarg value="--add-opens=java.base/java.util=ALL-UNNAMED"/>
           <jvmarg value="--add-opens=java.base/java.util.concurrent=ALL-UNNAMED"/>
+          <jvmarg value="--enable-native-access=ALL-UNNAMED"/>
+          <jvmarg value="--add-modules"/>
+          <jvmarg value="jdk.incubator.foreign"/>

           <classpath refid="tomcat.test.classpath" />
```

## Generating the OpenSSL API code using jextract (optional)

This step is only useful to be able to use additional native APIs from OpenSSL
or stdlib.

Download a Java 17 compatible jextract from `http://jdk.java.net/panama/` and
extract it to a path.

Find include paths using `gcc -xc -E -v -`, on Fedora it is
`/usr/lib/gcc/x86_64-redhat-linux/11/include`. Edit `openssl-tomcat.conf`
accordingly to set the appropriate path.

```
export JAVA_HOME=<pathto_jdk17_with_jextract>
$JAVA_HOME/bin/jextract @openssl-tomcat.conf openssl.h
```

The code included was generated using OpenSSL 1.1.1. As long as things remain
API compatible, the generated code will still work.

The `openssl-tomcat.conf` will generate a trimmed down OpenSSL API. When
developing new features, the full API can be generated instead using:
```
$JAVA_HOME/bin/jextract --source -t org.apache.tomcat.util.openssl -lssl -I /usr/lib/gcc/x86_64-redhat-linux/11/include openssl.h -d src/main/java
```

The `openssl.conf` file lists all the API calls and constants that can be
generated using jextract, as a reference to what is available. Some macros are
not supported and have to be reproduced in code.

Before committing updated generated files, they need to have the license header
added. The `addlicense.sh` script can do that and process all Java source files
in the `src/main/java/org/apache/tomcat/util/openssl` directory.

