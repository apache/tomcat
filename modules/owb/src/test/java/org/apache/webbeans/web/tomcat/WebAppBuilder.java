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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Builds a temporary exploded webapp from a test template.
 */
final class WebAppBuilder {

    private final Path destination;
    private final Path template;
    private final Set<Path> sourceRoots = new LinkedHashSet<>();
    private boolean includeBeansXml = true;
    private boolean includeWebXml = true;
    private boolean beansXmlInClasses;

    WebAppBuilder(Path destination, String templateName) {
        this.destination = destination;
        this.template = Path.of(Objects.requireNonNull(System.getProperty("owb.test.webapp.root"),
                "owb.test.webapp.root system property is not set")).resolve(templateName);
        sourceRoots.add(template.resolve("src"));
    }

    WebAppBuilder includeBeansXml(boolean include) {
        includeBeansXml = include;
        return this;
    }

    WebAppBuilder includeWebXml(boolean include) {
        includeWebXml = include;
        return this;
    }

    WebAppBuilder beansXmlInClasses() {
        beansXmlInClasses = true;
        return this;
    }

    WebAppBuilder sources(Path... roots) {
        for (Path root : roots) {
            sourceRoots.add(root);
        }
        return this;
    }

    Path build(Path shadedJar) throws IOException {
        Path webInf = template.resolve("WEB-INF");
        if (Files.isDirectory(webInf)) {
            copyTree(webInf, destination.resolve("WEB-INF"));
        }

        // Copy root-level web resources such as JSP pages.
        try (Stream<Path> stream = Files.list(template)) {
            for (Path child : stream.toList()) {
                String name = child.getFileName().toString();
                if ("WEB-INF".equals(name) || "src".equals(name)) {
                    continue;
                }
                copyTree(child, destination.resolve(name));
            }
        }

        Path classes = destination.resolve("WEB-INF/classes");
        Files.createDirectories(classes);
        compile(classes, shadedJar);

        if (beansXmlInClasses) {
            Path source = template.resolve("WEB-INF/beans.xml");
            Path target = classes.resolve("META-INF/beans.xml");
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return destination;
    }

    private void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                if (!includeBeansXml && relative.equals(Path.of("beans.xml"))) {
                    continue;
                }
                if (!includeWebXml && relative.equals(Path.of("web.xml"))) {
                    continue;
                }
                Path destinationPath = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destinationPath);
                } else {
                    Files.createDirectories(destinationPath.getParent());
                    Files.copy(path, destinationPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void compile(Path classes, Path shadedJar) throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path sourceRoot : sourceRoots) {
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(sourceRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(sources::add);
            }
        }
        if (sources.isEmpty()) {
            return;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK is required to run the integration tests");
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
                    "-d", classes.toString());
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
}
