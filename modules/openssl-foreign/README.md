# OpenSSL support for Apache Tomcat

## This module is experimental

It uses the JEP 424 API. More details on this API are available
at `https://openjdk.java.net/jeps/424`.

## Building the JDK with the JEP 424 API

Clone `https://github.com/openjdk/jdk/` in some location and
checkout the `foreign-preview` branch. This is a Java 19 development JVM
with the JEP 424 API. It may fail to build. When this happens, step back
one commit at a time until it does.

```
bash configure
make images
```

## Building

The module can now be built.
```
export JAVA_HOME=<pathto>/panama-foreign/build/linux-x86_64-server-release/images/jdk
mvn package
```
Note: The build path for the JDK will be different on other platforms.

## Running

The module uses the OpenSSL 1.1 API. It requires an API compatible version of
OpenSSL or a compatible alternative library, that can be loaded from the JVM
library path.

Copy `tomcat-coyote-openssl-1.0.jar` to the Apache Tomcat `lib` folder.

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
export JAVA_OPTS="--enable-preview --enable-native-access=ALL-UNNAMED"
```

## Generating the OpenSSL API code using jextract (optional)

jextract is now available in its own standalone repository. Clone
`https://github.com/openjdk/jextract` in some location. Please refer to the
instructions from the repository for building.
However, jextract has not been adapted yet to the JEP 424 API and the generated
code will need to be modified manually.

This step is only useful to be able to use additional native APIs from OpenSSL
or stdlib.

Find include paths using `gcc -xc -E -v -`, on Fedora it is
`/usr/lib/gcc/x86_64-redhat-linux/11/include`. Edit `openssl-tomcat.conf`
accordingly to set the appropriate path.

```
export JAVA_HOME=<pathto>/panama-foreign/build/linux-x86_64-server-release/images/jdk
$JAVA_HOME/bin/jextract @openssl-tomcat.conf openssl.h
```
Note: The build path for the JDK will be different on other platforms.

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

