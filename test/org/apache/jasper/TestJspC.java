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
package org.apache.jasper;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestJspC {

    private JspC jspc;
    private File outputDir;

    @Before
    public void init() {
        File tempDir = new File(System.getProperty("tomcat.test.temp",
                "output/tmp"));
        outputDir = new File(tempDir, "jspc");
        jspc = new JspC();
    }

    @After
    public void cleanup() throws IOException {
        remove(outputDir);
    }

    @Test
    public void precompileWebapp_2_2() throws IOException {
        File appDir = new File("test/webapp-2.2");
        File webappOut = new File(outputDir, appDir.getName());
        precompile(appDir, webappOut);
        verify(webappOut);
    }

    @Test
    public void precompileWebapp_2_3() throws IOException {
        File appDir = new File("test/webapp-2.3");
        File webappOut = new File(outputDir, appDir.getName());
        precompile(appDir, webappOut);
        verify(webappOut);
    }

    @Test
    public void precompileWebapp_2_4() throws IOException {
        File appDir = new File("test/webapp-2.4");
        File webappOut = new File(outputDir, appDir.getName());
        precompile(appDir, webappOut);
        verify(webappOut);
    }

    @Test
    public void precompileWebapp_2_5() throws IOException {
        File appDir = new File("test/webapp-2.5");
        File webappOut = new File(outputDir, appDir.getName());
        precompile(appDir, webappOut);
        verify(webappOut);
    }

    @Test
    public void precompileWebapp_3_0() throws IOException {
        File appDir = new File("test/webapp-3.0");
        File webappOut = new File(outputDir, appDir.getName());
        precompile(appDir, webappOut);
        verify(webappOut);
    }

    @Test
    public void precompileWebapp_3_1() throws IOException {
        File appDir = new File("test/webapp-3.1");
        File webappOut = new File(outputDir, appDir.getName());
        precompile(appDir, webappOut);
        verify(webappOut);
    }

    private void verify(File webappOut) {
        // for now, just check some expected files exist
        Assert.assertTrue(new File(webappOut, "generated_web.xml").exists());
        Assert.assertTrue(new File(webappOut,
                "org/apache/jsp/el_002das_002dliteral_jsp.java").exists());
        Assert.assertTrue(new File(webappOut,
                "org/apache/jsp/tld_002dversions_jsp.java").exists());
    }

    private void precompile(File appDir, File webappOut) throws IOException {
        remove(webappOut);
        Assert.assertTrue("Failed to create [" + webappOut + "]", webappOut.mkdirs());
        jspc.setUriroot(appDir.toString());
        jspc.setOutputDir(webappOut.toString());
        jspc.setValidateTld(false);
        jspc.setWebXml(new File(webappOut, "generated_web.xml").toString());
        jspc.execute();
    }


    private void remove(File base) throws IOException{
        if (!base.exists()) {
            return;
        }
        Files.walkFileTree(base.toPath(), new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir,
                    IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
