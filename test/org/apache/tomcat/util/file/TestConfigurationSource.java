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
package org.apache.tomcat.util.file;

import java.io.File;
import java.net.URI;

import org.junit.Assert;
import org.junit.Test;

public class TestConfigurationSource {

    private static final ConfigurationSource DEFAULT = ConfigurationSource.DEFAULT;


    /*
     * getURI() is documented to return a URI even when no resource exists. A relative name that contains characters
     * illegal in a URI (a space here) but valid in a file name must not trigger an IllegalArgumentException from
     * URI.resolve().
     */
    @Test
    public void testGetUriMissingFileWithIllegalUriCharacter() {
        String name = "conf/does not exist.xml";

        URI uri = DEFAULT.getURI(name);

        Assert.assertNotNull(uri);
        // The file name is recovered unchanged by decoding the path.
        Assert.assertEquals(new File(System.getProperty("user.dir"), name), new File(uri));
    }


    @Test
    public void testGetUriMissingFile() {
        String name = "conf/does-not-exist.xml";

        URI uri = DEFAULT.getURI(name);

        Assert.assertEquals(new File(System.getProperty("user.dir"), name).toURI(), uri);
    }


    @Test
    public void testGetUriExistingFile() {
        File file = new File("test/webresources/dir1/f1.txt");
        Assert.assertTrue(file.isFile());

        URI uri = DEFAULT.getURI(file.getPath());

        Assert.assertEquals(file.toURI(), uri);
    }


    /*
     * Scheme based names that are not absolute URIs per UriUtil (classpath: and jar: have no "://") keep the
     * URI.resolve() behaviour, which returns the name as a URI unchanged, as before the fix.
     */
    @Test
    public void testGetUriSchemeNameIsPreserved() {
        String name = "classpath:org/apache/catalina/mbeans-descriptors.xml";

        URI uri = DEFAULT.getURI(name);

        Assert.assertEquals(URI.create(name), uri);
    }


    @Test
    public void testGetUriAbsoluteUriIsReturnedAsIs() {
        String name = "file:/tmp/no-such-config.xml";

        URI uri = DEFAULT.getURI(name);

        Assert.assertEquals(URI.create(name), uri);
    }
}
