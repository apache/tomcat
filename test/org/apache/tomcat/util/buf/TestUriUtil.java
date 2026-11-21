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
package org.apache.tomcat.util.buf;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory;

public class TestUriUtil {

    @Test
    public void testResolve01() throws URISyntaxException, MalformedURLException {
        URI base = new URI("file:/aaa/bbb/base.xml");
        String target = "target.xml";
        URI result = UriUtil.resolve(base, target);
        Assert.assertEquals(new URI("file:/aaa/bbb/target.xml"), result);
    }

    @Test
    public void testResolve02() throws URISyntaxException, MalformedURLException {
        URI base = new URI("file:/aaa/bbb/base.xml");
        String target = "../target.xml";
        URI result = UriUtil.resolve(base, target);
        Assert.assertEquals(new URI("file:/aaa/target.xml"), result);
    }

    @Test
    public void testResolve03() throws URISyntaxException, MalformedURLException {
        URI base = new URI("jar:file:/aaa/bbb!/ccc/ddd/base.xml");
        String target = "target.xml";
        URI result = UriUtil.resolve(base, target);
        Assert.assertEquals(new URI("jar:file:/aaa/bbb!/ccc/ddd/target.xml"), result);
    }

    @Test
    public void testResolve04() throws URISyntaxException, MalformedURLException {
        URI base = new URI("jar:file:/aaa/bbb!/ccc/ddd/base.xml");
        String target = "../target.xml";
        URI result = UriUtil.resolve(base, target);
        Assert.assertEquals(new URI("jar:file:/aaa/bbb!/ccc/target.xml"), result);
    }

    @Test
    public void testResolve05() throws URISyntaxException, MalformedURLException {
        URI base = new URI("jar:file:/aaa/bbb!/ccc/ddd/base.xml");
        String target = "../../target.xml";
        URI result = UriUtil.resolve(base, target);
        Assert.assertEquals(new URI("jar:file:/aaa/bbb!/target.xml"), result);
    }


    @Test
    public void testWarToJar() throws Exception {
        doTestWarToJar("war:file:/aaa/bbb.war*/WEB-INF/lib/ccc.jar", "jar:file:/aaa/bbb.war!/WEB-INF/lib/ccc.jar");
    }


    @Test
    public void testWarToJarPercentEncoded() throws Exception {
        // The path is already percent-encoded and must not be encoded again
        doTestWarToJar("war:file:/aaa%20bbb/ccc.war*/WEB-INF/lib/ddd.jar",
                "jar:file:/aaa%20bbb/ccc.war!/WEB-INF/lib/ddd.jar");
    }


    @SuppressWarnings("deprecation")
    private void doTestWarToJar(String warUrl, String expected) throws Exception {
        TomcatURLStreamHandlerFactory.getInstance();
        URL result = UriUtil.warToJar(new URL(warUrl));
        Assert.assertEquals(expected, result.toString());
    }
}
