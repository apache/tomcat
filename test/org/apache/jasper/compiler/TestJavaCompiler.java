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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.easymock.EasyMock;

public class TestJavaCompiler {

    private File outputDir;
    private File javaFile;

    @Before
    public void setUp() throws Exception {
        File tempDir = new File(System.getProperty("tomcat.test.temp",
                "output/tmp"));
        outputDir = new File(tempDir, "java-compiler");
        Assert.assertTrue(outputDir.mkdirs() || outputDir.isDirectory());
        javaFile = new File(outputDir, "Bad_jsp.java");
        String source = "public class Bad_jsp {\n" +
                "    public void service() {\n" +
                "        int i = ;\n" +
                "    }\n" +
                "}\n";
        Files.write(javaFile.toPath(),
                source.getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() {
        removeFile(javaFile);
        removeFile(new File(outputDir, "Bad_jsp.class"));
        outputDir.delete();
    }

    private static void removeFile(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    /*
     * The compilation errors must be dispatched to the error handler before
     * the generated source is removed, so that the errors are reported (and
     * the source retained for inspection) rather than being masked by the
     * cleanup of the generated source.
     */
    @Test
    public void testCompilationErrorsDispatchedBeforeGeneratedSourceRemoved()
            throws Exception {
        Options options = EasyMock.createMock(Options.class);
        EasyMock.expect(options.getJavaEncoding()).andReturn("UTF-8")
                .anyTimes();
        EasyMock.expect(options.getCompilerSourceVM()).andReturn("17")
                .anyTimes();
        EasyMock.expect(options.getCompilerTargetVM()).andReturn("17")
                .anyTimes();
        EasyMock.replay(options);

        JspCompilationContext ctxt = EasyMock.createMock(
                JspCompilationContext.class);
        EasyMock.expect(ctxt.getOptions()).andReturn(options).anyTimes();
        EasyMock.expect(ctxt.getServletJavaFileName())
                .andReturn(javaFile.getAbsolutePath()).anyTimes();
        EasyMock.expect(ctxt.getClassPath())
                .andReturn(System.getProperty("java.class.path")).anyTimes();
        EasyMock.expect(Boolean.valueOf(ctxt.keepGenerated())).andReturn(Boolean.FALSE).anyTimes();
        EasyMock.replay(ctxt);

        JavaCompiler compiler = new JavaCompiler();
        compiler.init(ctxt, null);
        compiler.pageNodes = new Node.Nodes();
        compiler.errDispatcher = new ErrorDispatcher(false);

        try {
            compiler.generateClass(null);
            Assert.fail("Compilation was expected to fail");
        } catch (JasperException e) {
            // The javac error must have been reported ...
            Assert.assertTrue("Unexpected message: " + e.getMessage(),
                    e.getMessage().contains("Bad_jsp.java"));
            // ... before the generated source was removed, which also means
            // the source is retained for inspection after a failure
            Assert.assertTrue(javaFile.exists());
        }

        EasyMock.verify(options, ctxt);
    }
}
