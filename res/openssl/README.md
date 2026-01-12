# OpenSSL support for Apache Tomcat

## Building

The OpenSSL API support classes can be built using jextract from Java 22+.
jextract can be downloaded from `https://jdk.java.net/jextract/`. Extract the
download and set the path as the `JEXTRACT_HOME` environment variable for
ease of use.

This step is only useful to be able to use additional native APIs from OpenSSL
or stdlib.

Find include paths using `gcc -xc -E -v -`, on Fedora it is
`/usr/lib/gcc/x86_64-redhat-linux/14/include`. Edit `openssl-tomcat.conf`
accordingly to set the appropriate path.

```
$JEXTRACT_HOME/bin/jextract @openssl-tomcat.conf openssl.h
```
Note: The build path for the JDK will be different on other platforms.

The code included was generated using OpenSSL 3.0. As long as things remain
API compatible, the generated code will still work.

The `openssl-tomcat.conf` will generate a trimmed down OpenSSL API. When
developing new features, the full API can be generated instead using:
```
$JEXTRACT_HOME/bin/jextract --source -t org.apache.tomcat.util.openssl -lssl -I /usr/lib/gcc/x86_64-redhat-linux/14/include openssl.h --output src/main/java
```

The `openssl.conf` file lists all the API calls and constants that can be
generated using jextract, as a reference to what is available. Some macros are
not supported and have to be reproduced in code.

Before committing updated generated files, they need to have the license header
added. The `addlicense.sh` script can do that and process all Java source files
in the `src/main/java/org/apache/tomcat/util/openssl` directory.

