# OpenSSL support for Apache Tomcat

## This module is experimental

It uses an incubating Java API, a specific JDK, and is not supported
at this time.

## Building the panama-foreign JDK

Clone `https://github.com/openjdk/panama-foreign` in some location. This is a
forked Java 18 development JVM with the added Panama API and tools. It will
often fail to build. When this happens, step back one commit at a time until
it does. This is the only way to obtain the jextract tool, that is more or less
required for large libraries. The Panama API from this branch is also
different from the API present in Java 17.

Clang is a dependency for jextract, and ideally Clang from LLVM 12 should be
used. It may need explicit declaration to the configure script, using something
like `--with-libclang=/usr/lib64/llvm12 --with-libclang-version=12`.

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

## Running in Tomcat

Copy `tomcat-openssl-X.X.jar` to Tomcat lib folder.

Remove `AprLifecycleListener` from `server.xml`.

Use a connector like:
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
Run Tomcat using:
```
export JAVA_HOME=<pathto>/panama-foreign/build/linux-x86_64-server-release/images/jdk
export JAVA_OPTS="--enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.foreign"
```

## Generating OpenSSL API code using jextract (optional)

This step is only useful to be able to use additional native APIs from OpenSSL
or stdlib.

Find include paths using `gcc -xc -E -v -`, on Fedora it is
`/usr/lib/gcc/x86_64-redhat-linux/11/include`. Edit `openssl-tomcat.conf`
accordingly.

```
export JAVA_HOME=<pathto>/panama-foreign/build/linux-x86_64-server-release/images/jdk
$JAVA_HOME/bin/jextract @openssl-tomcat.conf openssl.h
```
Note: The build path for the JDK will be different on other platforms.

The code included was generated for OpenSSL 1.1.1. As long as things remain API
compatible, this will still work.

The `openssl-tomcat.conf` will generate a trimmed down OpenSSL API. When
developing new features, the full API can be generated instead using:
```
$JAVA_HOME/bin/jextract --source -t org.apache.tomcat.util.openssl -lssl -I /usr/lib/gcc/x86_64-redhat-linux/11/include openssl.h -d src/main/java
```

The `openssl.conf` file lists all the API calls and constants that can be
generated using jextract, as a reference to what is available. Macros are not
supported and have to be reproduced in code.

Before committing updated generated files, they need to have the license header
added. The `addlicense.sh` script can do that and process all Java source files
in the `src/main/java/org/apache/tomcat/util/openssl` directory.

