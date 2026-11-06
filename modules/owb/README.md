<!--

    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->

# Apache Tomcat CDI support module

This module packages Apache OpenWebBeans and provides CDI support for
Tomcat. The resulting shaded JAR is intended to be placed in the Tomcat
`lib` directory.

## Prerequisites

- Java 21 or later.
- Apache Maven.
- The Tomcat artifacts for `${tomcat.version}` available in the local Maven
  repository.

The default build uses `12.0.0-SNAPSHOT`, which is not published to Maven
Central. Install the required Tomcat artifacts from a local Tomcat build, or
override `tomcat.version` with a released Tomcat version.

## Building

```bash
cd modules/owb
mvn clean package
```

The shaded JAR is written to:

```text
target/tomcat-owb-<owb-version>.jar
```

`mvn package` builds the JAR but does not run the integration tests.

## Testing

Run the full integration test suite:

Run the unit tests:

```bash
mvn test
```

Run the unit and integration tests:

```bash
mvn clean verify
```

Run a single integration test class:

```bash
mvn -Dit.test=OpenWebBeansSecurityIT verify
```

The integration tests:

- Use an embedded Tomcat server.
- Use port `0` and temporary directories.
- Run against the shaded module JAR, not `target/classes`.
- Do not require an external Tomcat installation or a running server.

### JSP/EL test prerequisites

The JSP/EL integration test uses `tomcat-embed-jasper` and
`tomcat-embed-el`. For a snapshot build, install them from the local Tomcat
build:

```bash
cd $TOMCAT_SRC
ant embed-jars

mvn install:install-file \
  -Dfile=output/embed/tomcat-embed-jasper.jar \
  -DgroupId=org.apache.tomcat.embed \
  -DartifactId=tomcat-embed-jasper \
  -Dversion=12.0.0-SNAPSHOT \
  -Dpackaging=jar

mvn install:install-file \
  -Dfile=output/embed/tomcat-embed-el.jar \
  -DgroupId=org.apache.tomcat.embed \
  -DartifactId=tomcat-embed-el \
  -Dversion=12.0.0-SNAPSHOT \
  -Dpackaging=jar
```

The test suite also uses the Eclipse compiler (`ecj`) for JSP compilation.

## Test architecture

The tests are standard Maven Failsafe integration tests:

- Test classes are named `*IT` and live in `src/test/java`.
- Test webapps live under `src/test/webapp`.
- `WebAppBuilder` compiles and assembles a temporary exploded webapp.
- `OwbTestServer` starts and stops embedded Tomcat.
- `OwbTestSupport` provides HTTP, shaded-JAR and marker-file helpers.

### Classpath isolation

The Failsafe configuration deliberately:

- Points `classesDirectory` at an empty directory so `target/classes` is not
  on the test runtime classpath.
- Adds the shaded module JAR explicitly.
- Excludes the unshaded OpenWebBeans, CDI, Interceptor and XBean dependencies
  from the test runtime.

Tests assert that both `OpenWebBeansListener` and `WebBeansContext` are loaded
from the shaded JAR. This prevents the tests from accidentally validating an
unshaded build.

The test webapps do not contain the module JAR in `WEB-INF/lib`; the module is
provided through the test/common classpath, matching the production
`lib` directory model.

## Test coverage

| Test class | Coverage |
|---|---|
| `TomcatPluginTest` | Servlet spec class handling, managed-bean rejection, injection support and security service registration |
| `OpenWebBeansInstanceManagerTest` | Delegation for all `InstanceManager` creation variants and destruction fallback |
| `OpenWebBeansCdi41IT` | CDI injection, request/session scope, `isMatchingBean`, `isMatchingEvent`, `getContexts`, interceptor bindings, method invoker |
| `OpenWebBeansTomcatIntegrationIT` | Server/context listener modes, `startWithoutBeansXml` positive and negative behavior, alternate `beans.xml` location, security valve configuration, dynamic deployment, filter/listener/servlet field injection and lifecycle callbacks |
| `OpenWebBeansSecurityIT` | BASIC authentication, `@Inject Principal`, security valve principal, principal proxy modes and custom proxy APIs |
| `OpenWebBeansSecurityFilterIT` | Webapp-level `OpenWebBeansSecurityFilter` |
| `OpenWebBeansJspIT` | JSP/EL resolution through OWB |
| `OpenWebBeansRedeployIT` | Multiple webapps, classloader isolation, context restart, `@PreDestroy`, listener registration |

## Adding a test

1. Add a webapp template under `src/test/webapp/<name>`.
2. Include `WEB-INF/beans.xml` for a CDI webapp.
3. Put Java sources under `src/`.
4. Add a `*IT` test class.
5. Build the webapp with `WebAppBuilder`.
6. Start it with `OwbTestServer`.
7. Verify HTTP endpoints and, where relevant, marker files or context state.

Example skeleton:

```java
Path webapp = new WebAppBuilder(tempDir.resolve("app"), "my-template")
        .build(OwbTestSupport.shadedJar());

try (OwbTestServer server = OwbTestServer.start(webapp,
        ListenerMode.SERVER, false, true)) {
    HttpClient client = OwbTestSupport.newClient();
    assertEquals("expected", OwbTestSupport.get(client,
            server.baseUri().resolve("endpoint")));
}
```

## Known limitations

- Producer-level `@Priority` on competing alternative producers is not
  covered. OpenWebBeans reports an ambiguous resolution for that scenario,
  so no failing gate test is included.
- `ELAwareBeanManager` is not covered. OpenWebBeans provides the core
  `BeanManager`, and the supplemental CDI EL API is not part of this module.
- Classloader garbage-collection leak detection is not asserted. The redeploy
  tests verify deterministic behavior: pre-destroy callbacks, separate
  contexts, no duplicate listener registration and successful restart.

## Troubleshooting

- **Missing Tomcat artifacts**: install `tomcat-catalina` and its transitive
  Tomcat dependencies for `${tomcat.version}` into the local Maven repository.
- **Missing Jasper/EL artifacts**: run `ant embed-jars` and install
  `tomcat-embed-jasper` and `tomcat-embed-el` as described above.
- **Tests run against the wrong classes**: check the Failsafe
  `classesDirectory`, `additionalClasspathElements` and
  `classpathDependencyExcludes` settings.
- **Port or directory conflicts**: the tests use port `0` and JUnit temporary
  directories; do not hard-code either in new tests.
