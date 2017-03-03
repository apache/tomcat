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
package org.apache.tomcat.util.scan;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.util.IOTools;
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory;
import org.apache.tomcat.Jar;

public class TestAbstractInputStreamJar {

    @Before
    public void register() {
        TomcatURLStreamHandlerFactory.register();
    }


    @Test
    public void testNestedJarGetInputStream() throws Exception {
        File f = new File("test/webresources/war-url-connection.war");
        StringBuilder sb = new StringBuilder("war:");
        sb.append(f.toURI().toURL());
        sb.append("*/WEB-INF/lib/test.jar");

        Jar jar = JarFactory.newInstance(new URL(sb.toString()));

        InputStream is1 = jar.getInputStream("META-INF/resources/index.html");
        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        IOTools.flow(is1, baos1);

        InputStream is2 = jar.getInputStream("META-INF/resources/index.html");
        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        IOTools.flow(is2, baos2);

        Assert.assertArrayEquals(baos1.toByteArray(), baos2.toByteArray());
    }
}
