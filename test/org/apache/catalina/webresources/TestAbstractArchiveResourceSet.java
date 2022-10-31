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
import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;

public class TestAbstractArchiveResourceSet {

    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=65586
     */
    @Test
    public void testBloomFilterWithDirectoryVerifyCleanup() throws Exception {
        WebResourceRoot root = new TesterWebResourceRoot();

        root.setArchiveIndexStrategy(WebResourceRoot.ArchiveIndexStrategy.BLOOM.name());

        File file = new File("webapps/examples/WEB-INF/lib/taglibs-standard-impl-1.2.5-migrated-0.0.1.jar");

        JarResourceSet jarResourceSet = new JarResourceSet(root, "/WEB-INF/classes", file.getAbsolutePath(), "/");
        jarResourceSet.getArchiveEntries(false);
        Assert.assertNotNull(getJarContents(jarResourceSet));

        WebResource r1 = jarResourceSet.getResource("/WEB-INF/classes/org/");
        Assert.assertTrue(r1.isDirectory());
        Assert.assertNotNull(getJarContents(jarResourceSet));

        WebResource r2 = jarResourceSet.getResource("/WEB-INF/classes/org");
        Assert.assertTrue(r2.isDirectory());
        Assert.assertNotNull(getJarContents(jarResourceSet));

        jarResourceSet.gc();
        Assert.assertNotNull(getJarContents(jarResourceSet));
    }

    @Test
    public void testPurgedBloomFilterWithDirectoryVerifyCleanup() throws Exception {
        WebResourceRoot root = new TesterWebResourceRoot();

        root.setArchiveIndexStrategy(WebResourceRoot.ArchiveIndexStrategy.PURGED.name());

        File file = new File("webapps/examples/WEB-INF/lib/taglibs-standard-impl-1.2.5-migrated-0.0.1.jar");

        JarResourceSet jarResourceSet = new JarResourceSet(root, "/WEB-INF/classes", file.getAbsolutePath(), "/");
        jarResourceSet.getArchiveEntries(false);
        Assert.assertNotNull(getJarContents(jarResourceSet));

        WebResource r1 = jarResourceSet.getResource("/WEB-INF/classes/org/");
        Assert.assertTrue(r1.isDirectory());
        Assert.assertNotNull(getJarContents(jarResourceSet));

        WebResource r2 = jarResourceSet.getResource("/WEB-INF/classes/org");
        Assert.assertTrue(r2.isDirectory());
        Assert.assertNotNull(getJarContents(jarResourceSet));

        jarResourceSet.gc();
        Assert.assertNull(getJarContents(jarResourceSet));
    }

    private JarContents getJarContents(Object target)
        throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
        Field field = AbstractArchiveResourceSet.class.getDeclaredField("jarContents");
        field.setAccessible(true);
        JarContents contents = (JarContents) field.get(target);

        return contents;
    }
}
