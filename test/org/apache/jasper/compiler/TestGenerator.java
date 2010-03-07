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

package org.apache.jasper.compiler;

import java.io.File;
import java.io.IOException;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestGenerator extends TomcatBaseTest {
    
    public void testBug45015a() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug45015a.jsp");
        
        String result = res.toString();
        // Beware of the differences between escaping in JSP attributes and
        // in Java Strings
        assertEcho(result, "00-hello 'world'");
        assertEcho(result, "01-hello 'world");
        assertEcho(result, "02-hello world'");
        assertEcho(result, "03-hello world'");
        assertEcho(result, "04-hello world\"");
        assertEcho(result, "05-hello \"world\"");
        assertEcho(result, "06-hello \"world");
        assertEcho(result, "07-hello world\"");
        assertEcho(result, "08-hello world'");
        assertEcho(result, "09-hello world\"");
    }

    public void testBug45015b() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        Exception e = null;
        try {
            getUrl("http://localhost:" + getPort() + "/test/bug45015b.jsp");
        } catch (IOException ioe) {
            e = ioe;
        }

        // Failure is expected
        assertNotNull(e);
    }

    /** Assertion for text printed by tags:echo */
    private static void assertEcho(String result, String expected) {
        assertTrue(result.indexOf("<p>" + expected + "</p>") > 0);
    }
}
