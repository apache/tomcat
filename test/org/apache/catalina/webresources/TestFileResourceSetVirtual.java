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
package org.apache.catalina.webresources;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import org.apache.catalina.startup.ExpandWar;
import org.apache.catalina.startup.TomcatBaseTest;

/**
 * Mounts file resources in sub directories that do not exist in the main
 * resoucres.
 */
public class TestFileResourceSetVirtual extends TestFileResourceSet {

    private static Path tempDir;
    private static File dir1;

    @BeforeClass
    public static void before() throws IOException {
        tempDir = Files.createTempDirectory("test", new FileAttribute[0]);
        dir1 = new File(tempDir.toFile(), "dir1");
        TomcatBaseTest.recursiveCopy(new File("test/webresources/dir1").toPath(), dir1.toPath());
    }

    @AfterClass
    public static void after() {
        ExpandWar.delete(tempDir.toFile());
    }


    @Override
    public File getBaseDir() {
        return new File("test/webresources/dir3");
    }

    @Override
    protected String getDir1() {
        return dir1.getAbsolutePath();
    }
}
