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

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;

public class TestJarResourceSet extends AbstractTestResourceSet {

    @Override
    public WebResourceRoot getWebResourceRoot() {
        File f = new File("test/webresources/dir1.jar");
        TesterWebResourceRoot root = new TesterWebResourceRoot();
        WebResourceSet webResourceSet =
                new JarResourceSet(root, "/", f.getAbsolutePath(), "/");
        root.setMainResources(webResourceSet);
        return root;
    }

    @Override
    protected boolean isWritable() {
        return false;
    }

    @Override
    public File getBaseDir() {
        return new File("test/webresources");
    }

    @Override
    @Test
    public void testNoArgConstructor() {
        @SuppressWarnings("unused")
        Object obj = new JarResourceSet();
    }

    @Test
    public void testGetResourceRootAfterBloomFilterBuilt() {
        // Regression test: the archive root must still be found once the
        // archive (and therefore the bloom filter) has been opened by a
        // previous lookup.
        WebResource file = resourceRoot.getResource("/d1/d1-f1.txt");
        Assert.assertTrue(file.isFile());
        WebResource webResource = resourceRoot.getResource("/");
        Assert.assertTrue(webResource.isDirectory());
        Assert.assertEquals("", webResource.getName());
        Assert.assertEquals("/", webResource.getWebappPath());
    }

    @Override
    protected String getNewDirName() {
        return "test-dir-08";
    }

    @Override
    protected String getNewFileNameNull() {
        return "test-null-08";
    }

    @Override
    protected String getNewFileName() {
        return "test-file-08";
    }
}
