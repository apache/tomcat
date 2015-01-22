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
import org.apache.catalina.WebResourceSet;

public class TestResourceJars {

    @Test
    public void testNonStaticResources() {
        File empty = new File("test/webresources/dir3");
        File jar = new File("test/webresources/non-static-resources.jar");

        TesterWebResourceRoot root = new TesterWebResourceRoot();

        // Use empty dir for root of web app.
        WebResourceSet webResourceSet = new DirResourceSet(root, "/", empty.getAbsolutePath(), "/");
        root.setMainResources(webResourceSet);

        // If this JAR was in a web application, this is equivalent to how it
        // would be added
        JarResourceSet test =
                new JarResourceSet(root, "/", jar.getAbsolutePath(), "/META-INF/resources");
        test.setStaticOnly(true);
        root.addJarResources(test);

        WebResource resource = root.getClassLoaderResource("/org/apache/tomcat/unittest/foo.txt");

        Assert.assertFalse(resource.exists());
    }
}
