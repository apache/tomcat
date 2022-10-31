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
import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.webresources.StandardRoot.BaseLocation;

public class TestStandardRoot {

    private static final File file;
    private static final String fileUrl;

    static {
        file = new File("/foo");
        String url = null;
        try {
            url = file.toURI().toURL().toExternalForm();
        } catch (MalformedURLException e) {
            // Ignore
        }
        fileUrl = url;
    }

    @Test
    public void testBaseLocation01() throws Exception {
        doTestBaseLocation(new URL (fileUrl),
                file.getAbsolutePath(), null);
    }

    @Test
    public void testBaseLocation02() throws Exception {
        doTestBaseLocation(new URL ("jar:" + fileUrl + "!/"),
                file.getAbsolutePath(), null);
    }

    @Test
    public void testBaseLocation03() throws Exception {
        doTestBaseLocation(new URL ("jar:" + fileUrl + "!/bar"),
                file.getAbsolutePath(), "bar");
    }

    @Test
    public void testBaseLocation04() throws Exception {
        doTestBaseLocation(new URL ("jar:" + fileUrl + "!/bar/bar"),
                file.getAbsolutePath(), "bar/bar");
    }

    @Test(expected=IllegalArgumentException.class)
    public void testBaseLocation05() throws Exception {
        doTestBaseLocation(new URL ("http://localhost:8080/foo"),
                null, null);
    }

    private void doTestBaseLocation(URL url, String expectedBasePath,
            String expectedArchivePath) {
        BaseLocation baseLocation = new BaseLocation(url);
        Assert.assertEquals(expectedBasePath, baseLocation.getBasePath());
        Assert.assertEquals(expectedArchivePath, baseLocation.getArchivePath());
    }

    @Test
    public void testArchiveIndexStrategy() {
        WebResourceRoot root = new StandardRoot();
        root.setArchiveIndexStrategy(WebResourceRoot.ArchiveIndexStrategy.BLOOM.name());
        Assert.assertEquals(WebResourceRoot.ArchiveIndexStrategy.BLOOM.name(), root.getArchiveIndexStrategy());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArchiveIndexStrategyUnrecognized() {
        WebResourceRoot root = new StandardRoot();
        root.setArchiveIndexStrategy("UNRECOGNIZED");
    }
}
