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
package org.apache.tomcat.util.descriptor.tld;

import java.io.File;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.res.StringManager;

public class TestTldResourcePath {

    @Test(expected = IllegalArgumentException.class)
    public void testNullUrlRejectedTwoArg() {
        @SuppressWarnings("unused")
        TldResourcePath path = new TldResourcePath(null, "/WEB-INF/tld/test.tld");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullUrlRejectedThreeArg() {
        @SuppressWarnings("unused")
        TldResourcePath path = new TldResourcePath(null, "/WEB-INF/lib/test.jar", "META-INF/test.tld");
    }

    @Test
    public void testNullUrlMessage() {
        try {
            @SuppressWarnings("unused")
            TldResourcePath path = new TldResourcePath(null, "/WEB-INF/tld/test.tld");
            Assert.fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            String expected = StringManager.getManager(TldResourcePath.class)
                    .getString("tldResourcePath.nullUrl");
            Assert.assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void testNonNullUrlAccepted() throws Exception {
        URL url = new File("/tmp/test.tld").toURI().toURL();
        TldResourcePath path = new TldResourcePath(url, "/WEB-INF/tld/test.tld");
        Assert.assertEquals(url, path.getUrl());
        Assert.assertEquals("/WEB-INF/tld/test.tld", path.getWebappPath());
        Assert.assertNull(path.getEntryName());
        // The methods the null URL check exists to protect.
        Assert.assertEquals(url.toExternalForm(), path.toExternalForm());
        Assert.assertEquals(new TldResourcePath(url, "/WEB-INF/tld/test.tld"), path);
        Assert.assertEquals(new TldResourcePath(url, "/WEB-INF/tld/test.tld").hashCode(), path.hashCode());
    }
}
