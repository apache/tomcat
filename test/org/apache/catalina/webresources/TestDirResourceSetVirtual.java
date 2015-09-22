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

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;

public class TestDirResourceSetVirtual extends TestDirResourceSet {

    @Override
    public WebResourceRoot getWebResourceRoot() {
        TesterWebResourceRoot root = new TesterWebResourceRoot();
        WebResourceSet webResourceSet =
                new DirResourceSet(new TesterWebResourceRoot(), "/",
                        getBaseDir().getAbsolutePath(), "/");
        root.setMainResources(webResourceSet);

        WebResourceSet f1 = new FileResourceSet(root, "/f1.txt",
                "test/webresources/dir1/f1.txt", "/");
        root.addPreResources(f1);

        WebResourceSet f2 = new FileResourceSet(root, "/f2.txt",
                "test/webresources/dir1/f2.txt", "/");
        root.addPreResources(f2);

        WebResourceSet d1 = new DirResourceSet(root, "/d1",
                "test/webresources/dir1/d1", "/");
        root.addPreResources(d1);

        WebResourceSet d2 = new DirResourceSet(root, "/d2",
                "test/webresources/dir1/d2", "/");
        root.addPreResources(d2);

        return root;
    }

    @Override
    protected boolean isWriteable() {
        return true;
    }

    @Override
    public File getBaseDir() {
        return new File("test/webresources/dir3");
    }
}
