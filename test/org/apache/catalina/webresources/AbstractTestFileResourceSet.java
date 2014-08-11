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

import org.junit.Test;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;

public abstract class AbstractTestFileResourceSet extends AbstractTestResourceSet {

    private final boolean readOnly;

    protected AbstractTestFileResourceSet(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public WebResourceRoot getWebResourceRoot() {
        File f = new File(getBaseDir());
        TesterWebResourceRoot root = new TesterWebResourceRoot();
        WebResourceSet webResourceSet = new DirResourceSet(root, "/", f.getAbsolutePath(), "/");
        webResourceSet.setReadOnly(readOnly);
        root.setMainResources(webResourceSet);

        WebResourceSet f1 = new FileResourceSet(root, "/f1.txt",
                "test/webresources/dir1/f1.txt", "/");
        f1.setReadOnly(readOnly);
        root.addPreResources(f1);

        WebResourceSet f2 = new FileResourceSet(root, "/f2.txt",
                "test/webresources/dir1/f2.txt", "/");
        f2.setReadOnly(readOnly);
        root.addPreResources(f2);

        WebResourceSet d1f1 = new FileResourceSet(root, "/d1/d1-f1.txt",
                "test/webresources/dir1/d1/d1-f1.txt", "/");
        d1f1.setReadOnly(readOnly);
        root.addPreResources(d1f1);

        WebResourceSet d2f1 = new FileResourceSet(root, "/d2/d2-f1.txt",
                "test/webresources/dir1/d2/d2-f1.txt", "/");
        d2f1.setReadOnly(readOnly);
        root.addPreResources(d2f1);

        return root;
    }

    @Override
    protected boolean isWriteable() {
        return !readOnly;
    }

    @Override
    public String getBaseDir() {
        return "test/webresources/dir2";
    }

    @Override
    @Test
    public void testNoArgConstructor() {
        @SuppressWarnings("unused")
        Object obj = new FileResourceSet();
    }
}
