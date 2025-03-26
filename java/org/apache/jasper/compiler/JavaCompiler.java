/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jasper.compiler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.jasper.JasperException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Main JSP compiler class. This class uses the Java Compiler API for compiling.
 */
public class JavaCompiler extends Compiler {

    private final Log log = LogFactory.getLog(JavaCompiler.class); // must not be static

    @Override
    protected void generateClass(Map<String, SmapStratum> smaps) throws JasperException, IOException {

        long t1 = 0;
        if (log.isDebugEnabled()) {
            t1 = System.currentTimeMillis();
        }

        javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, null, Charset.forName(ctxt.getOptions().getJavaEncoding()));
        List<File> compilationUnitsList = new ArrayList<>(1);
        compilationUnitsList.add(new File(ctxt.getServletJavaFileName()));
        Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromFiles(Collections.unmodifiableList(compilationUnitsList));
        // Perform Java compilation using the appropriate options
        List<String> compilerOptionsList = new ArrayList<>(6);
        compilerOptionsList.add("-classpath");
        compilerOptionsList.add(ctxt.getClassPath());
        compilerOptionsList.add("-source");
        compilerOptionsList.add(ctxt.getOptions().getCompilerSourceVM());
        compilerOptionsList.add("-target");
        compilerOptionsList.add(ctxt.getOptions().getCompilerTargetVM());
        List<String> compilerOptions = Collections.unmodifiableList(compilerOptionsList);
        Boolean result =
                compiler.getTask(null, fileManager, diagnostics, compilerOptions, null, compilationUnits).call();

        List<JavacErrorDetail> problemList = new ArrayList<>();
        if (!result.booleanValue()) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    try {
                        problemList.add(ErrorDispatcher.createJavacError(diagnostic.getSource().getName(), pageNodes,
                                new StringBuilder(diagnostic.getMessage(Locale.getDefault())),
                                (int) diagnostic.getLineNumber(), ctxt));
                    } catch (JasperException e) {
                        log.error(Localizer.getMessage("jsp.error.compilation.jdtProblemError"), e);
                    }
                }
            }
        }

        if (!ctxt.keepGenerated()) {
            File javaFile = new File(ctxt.getServletJavaFileName());
            if (!javaFile.delete()) {
                throw new JasperException(Localizer.getMessage("jsp.warning.compiler.javafile.delete.fail", javaFile));
            }
        }

        if (!problemList.isEmpty()) {
            JavacErrorDetail[] jeds = problemList.toArray(new JavacErrorDetail[0]);
            errDispatcher.javacError(jeds);
        }

        if (log.isDebugEnabled()) {
            long t2 = System.currentTimeMillis();
            log.debug(Localizer.getMessage("jsp.compiled", ctxt.getServletJavaFileName(), Long.valueOf(t2 - t1)));
        }

        if (ctxt.isPrototypeMode()) {
            return;
        }

        // JSR45 Support
        if (!options.isSmapSuppressed()) {
            SmapUtil.installSmap(smaps);
        }

    }

}
