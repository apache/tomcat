/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.webbeans.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.catalina.startup.Tomcat;
import org.apache.webbeans.config.WebBeansContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenWebBeansCdi41IT {

    private static final String CONTEXT_PATH = "/owb-cdi41";

    @TempDir
    Path tempDir;

    @Test
    void cdi41Webapp() throws Exception {
        Path webappSource = Path.of(System.getProperty("owb.test.webapp"));
        Path shadedJar = Path.of(System.getProperty("owb.test.jar"));

        assertEquals(shadedJar.toRealPath(),
                Path.of(OpenWebBeansListener.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .toRealPath(),
                "The test must run against the shaded module JAR");
        assertEquals(shadedJar.toRealPath(),
                Path.of(WebBeansContext.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .toRealPath(),
                "The test must use the OpenWebBeans classes from the shaded module JAR");

        Path webapp = tempDir.resolve("webapp");
        copyTree(webappSource.resolve("WEB-INF"), webapp.resolve("WEB-INF"));
        Path classes = webapp.resolve("WEB-INF/classes");
        Files.createDirectories(classes);
        compile(webappSource.resolve("src"), classes, shadedJar);

        Path base = tempDir.resolve("tomcat");
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.setPort(0);
        tomcat.getConnector();
        tomcat.getServer().setPort(-1);

        OpenWebBeansListener listener = new OpenWebBeansListener();
        listener.setStartWithoutBeansXml(false);
        tomcat.getServer().addLifecycleListener(listener);

        // The test webapp does not use JSPs and tomcat-jasper is not required.
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.addWebapp(CONTEXT_PATH, webapp.toString());
        tomcat.start();
        try {
            int port = tomcat.getConnector().getLocalPort();
            assertTrue(port > 0, "Tomcat did not start an HTTP connector");

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            URI baseUri = URI.create("http://127.0.0.1:" + port + CONTEXT_PATH + "/");

            assertEquals("hello-owb-4.1.1", get(client, baseUri.resolve("hello")));

            String firstRequest = get(client, baseUri.resolve("request"));
            String secondRequest = get(client, baseUri.resolve("request"));
            assertNotEquals(firstRequest, secondRequest,
                    "The request scoped bean was not recreated for each request");
            assertFalse(firstRequest.isEmpty());
            assertFalse(secondRequest.isEmpty());

            assertEquals("true", get(client, baseUri.resolve("matching")));
            assertEquals("bound:ok", get(client, baseUri.resolve("interceptor")));
        } finally {
            try {
                tomcat.stop();
            } finally {
                tomcat.destroy();
            }
        }
    }

    private static String get(HttpClient client, URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Unexpected response for " + uri + ": " + response.body());
        return response.body().strip();
    }

    private static void compile(Path sourceDir, Path classesDir, Path shadedJar) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A JDK is required to run this test");

        List<Path> sources;
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            sources = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }

        String compileClasspath = System.getProperty("surefire.test.class.path");
        if (compileClasspath == null || compileClasspath.isBlank()) {
            compileClasspath = System.getProperty("java.class.path");
        }
        compileClasspath = compileClasspath + File.pathSeparator + shadedJar;

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);
            List<String> options = List.of(
                    "--release", "21",
                    "-proc:none",
                    "-classpath", compileClasspath,
                    "-d", classesDir.toString());
            boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!success) {
                StringBuilder message = new StringBuilder("Failed to compile the test webapp:");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    message.append(System.lineSeparator()).append(diagnostic);
                }
                throw new IllegalStateException(message.toString());
            }
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
