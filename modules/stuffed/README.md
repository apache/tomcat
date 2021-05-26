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

# Apache Tomcat distribution for container platforms

## Configuration

Configuration is located in `conf/server.xml`, `conf/web.xml`, `conf/logging.properties`, all other configuration files, resources and context files are located in `conf`, identical to standalone Tomcat.

## Building

### Maven build

Update Tomcat version number in the `pom.xml`, customize Tomcat components in the dependencies to keep the ones needed (only the main `tomcat-catalina` is mandatory). Custom Tomcat components sources can be added to the usual Maven build path and will be included in the package that is built.
```
mvn clean; mvn package
```

### Docker build

```
docker build -t apache/tomcat-stuffed:1.0 -f ./Dockerfile .
```
Docker build arguments include `namespace` (default is `tomcat`) and `port` which should match the Tomcat port in `server.xml` (default is `8080`). Other ports that need to be exposed can be added in the `Dockerfile` as needed. Webapps should be added to the `webapps` folder where they will be auto deployed by the host if using the defaults. Otherwise, the `Dockerfile` command line can be edited like below to include the necessary resources and command line arguments to run a single or multiple hardcoded web applications.

## Running

Add a webapp as folder mywebapp (for this example, or specify another path), or a path from which a configured Host will auto deploy
```
--path: Specify a path the webapp will use
--war: Add the specified path (directory or war) as a webapp (if no path has been specified, it will be the root webapp)
```

The JULI logging manager configuration is optional but makes logging more readable and configurable:
`-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager -Djava.util.logging.config.file=conf/logging.properties`
The default JULI configuration uses `catalina.base`, so specifying the system property with `-Dcatalina.base=.` is also useful.

### Command line example with a single root webapp

```
java -Dcatalina.base=. -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager -Djava.util.logging.config.file=conf/logging.properties -jar target/tomcat-stuffed-1.0.jar --war myrootwebapp
```

### Command line example with three webapps

```
java -Dcatalina.base=. -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager -Djava.util.logging.config.file=conf/logging.properties -jar target/tomcat-stuffed-1.0.jar --war myrootwebapp --path /path1 --war mywebapp1 --path /path2 --war mywebapp2
```

## Cloud

### Deployment

An example `tomcat.yaml` is included which uses the Docker image. It uses the health check valve which can be added in `conf/server.xml`, or a similar service responding to requests on `/health`. It also declares the optional Jolokia and Prometheus ports for monitoring.

### Cluster

If using the Kubernetes cloud clustering membership provider, the pod needs to have the permission to view other pods. For example with Openshift, this is done with:
```
oc policy add-role-to-user view system:serviceaccount:$(oc project -q):default -n $(oc project -q)
```

## Native Image

The Tomcat documentation includes information on using the native-image tool (docs/graal.html).

Running in a container is possible, an example `DockerfileGraal` is given. To use a native image in a container that is not identical to the build platform,
the `native-image` call will need to use the additional `--static` parameter to statically link base libraries (this will then require zlib and glibc
static libraries). Due to TLS using dynamic libraries (SunEC for JSSE and tomcat-native for OpenSSL), TLS support is not available with static linking.
If TLS support is needed, the native image must instead be built on a platform identical to the target platform.
