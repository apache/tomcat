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

import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTestJUnit4;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestJspDocumentParser extends TomcatBaseTestJUnit4 {

    @Test
    public void testBug47977() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug47977.jspx", new ByteChunk(), null);
        
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }

    @Test
    public void testBug48827() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp-3.0");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        Exception e = null;
        try {
            getUrl("http://localhost:" + getPort() + "/test/bug48nnn/bug48827.jspx");
        } catch (IOException ioe) {
            e = ioe;
        }

        // Should not fail
        assertNull(e);
    }
}
