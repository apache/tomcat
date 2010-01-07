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

package org.apache.el;

import java.io.File;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestELInJsp extends TomcatBaseTest {
    
    public void testBug42565() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug42565.jsp");
        
        String result = res.toString();
        assertTrue(result.indexOf("00-false") > 0);
        assertTrue(result.indexOf("01-false") > 0);
        assertTrue(result.indexOf("02-false") > 0);
        assertTrue(result.indexOf("03-false") > 0);
        assertTrue(result.indexOf("04-false") > 0);
        assertTrue(result.indexOf("05-false") > 0);
        assertTrue(result.indexOf("06-false") > 0);
        assertTrue(result.indexOf("07-false") > 0);
        assertTrue(result.indexOf("08-false") > 0);
        assertTrue(result.indexOf("09-false") > 0);
        assertTrue(result.indexOf("10-false") > 0);
        assertTrue(result.indexOf("11-false") > 0);
        assertTrue(result.indexOf("12-false") > 0);
        assertTrue(result.indexOf("13-false") > 0);
        assertTrue(result.indexOf("14-false") > 0);
        assertTrue(result.indexOf("15-false") > 0);
    }

    public void testBug44994() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug44994.jsp");
        
        String result = res.toString();
        assertTrue(result.indexOf("00-none") > 0);
        assertTrue(result.indexOf("01-one") > 0);
        assertTrue(result.indexOf("02-many") > 0);
    }

    public void testBug45427() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug45427.jsp");
        
        String result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        assertTrue(result.indexOf("00-hello world") > 0);
        assertTrue(result.indexOf("01-hello 'world") > 0);
        assertTrue(result.indexOf("02-hello \"world") > 0);
        assertTrue(result.indexOf("03-hello world") > 0);
        assertTrue(result.indexOf("04-hello 'world") > 0);
        assertTrue(result.indexOf("05-hello \"world") > 0);
        assertTrue(result.indexOf("06-hello world") > 0);
        assertTrue(result.indexOf("07-hello 'world") > 0);
        assertTrue(result.indexOf("08-hello \"world") > 0);
        assertTrue(result.indexOf("09-hello world") > 0);
        assertTrue(result.indexOf("10-hello 'world") > 0);
        assertTrue(result.indexOf("11-hello \"world") > 0);
        assertTrue(result.indexOf("12-hello world") > 0);
        assertTrue(result.indexOf("13-hello 'world") > 0);
        assertTrue(result.indexOf("14-hello \"world") > 0);
        assertTrue(result.indexOf("15-hello world") > 0);
        assertTrue(result.indexOf("16-hello 'world") > 0);
        assertTrue(result.indexOf("17-hello \"world") > 0);
    }

    public void testBug45451() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug45451a.jsp");
        
        String result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        assertTrue(result.indexOf("00-\\'hello world\\'") > 0);
        assertTrue(result.indexOf("01-\\'hello world\\'") > 0);
        assertTrue(result.indexOf("02-\\'hello world\\'") > 0);
        assertTrue(result.indexOf("03-\\'hello world\\'") > 0);
        
        res = getUrl("http://localhost:" + getPort() + "/test/bug45451b.jsp");
        result = res.toString();
        System.out.println(result);
        // Warning: JSP attribute escaping != Java String escaping
        // Warning: Attributes are always unescaped before passing to the EL
        //          processor
        assertTrue(result.indexOf("00-2") > 0);
        assertTrue(result.indexOf("01-${1+1}") > 0);
        assertTrue(result.indexOf("02-\\${1+1}") > 0);
        assertTrue(result.indexOf("03-\\\\${1+1}") > 0);
        assertTrue(result.indexOf("04-2") > 0);
        assertTrue(result.indexOf("05-${1+1}") > 0);
        assertTrue(result.indexOf("06-\\2") > 0);      // TODO Fails (bug)
        assertTrue(result.indexOf("07-\\${1+1}") > 0);
        assertTrue(result.indexOf("08-\\\\2") > 0);    // TODO Fails (bug) 
        
        res = getUrl("http://localhost:" + getPort() + "/test/bug45451c.jsp");
        result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        // TODO - Currently we allow a single unescaped \ in attribute values
        //        Review if this should cause a warning/error
        assertTrue(result.indexOf("00-${1+1}") > 0);
        assertTrue(result.indexOf("01-\\${1+1}") > 0);
        assertTrue(result.indexOf("02-\\\\${1+1}") > 0);
        assertTrue(result.indexOf("03-\\\\\\${1+1}") > 0);
        assertTrue(result.indexOf("04-${1+1}") > 0);
        assertTrue(result.indexOf("05-\\${1+1}") > 0);
        assertTrue(result.indexOf("06-\\${1+1}") > 0);
        assertTrue(result.indexOf("07-\\\\${1+1}") > 0);
        assertTrue(result.indexOf("08-\\\\${1+1}") > 0);

        res = getUrl("http://localhost:" + getPort() + "/test/bug45451d.jspx");
        result = res.toString();
        // Warning: JSP attribute escaping != Java String escaping
        // \\ Is *not* an escape sequence in XML attributes
        assertTrue(result.indexOf("00-2") > 0);
        assertTrue(result.indexOf("01-${1+1}") > 0);
        assertTrue(result.indexOf("02-\\${1+1}") > 0);
        assertTrue(result.indexOf("03-\\\\${1+1}") > 0);
        assertTrue(result.indexOf("04-2") > 0);
        assertTrue(result.indexOf("05-${1+1}") > 0);
        assertTrue(result.indexOf("06-\\${1+1}") > 0);
        assertTrue(result.indexOf("07-\\\\${1+1}") > 0);
        assertTrue(result.indexOf("08-\\\\\\${1+1}") > 0);
    }

    public void testBug45511() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug45511.jsp");
        
        String result = res.toString();
        assertTrue(result.indexOf("00-true") > 0);
        assertTrue(result.indexOf("01-false") > 0);
    }

    public void testBug46596() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug46596.jsp");
        assertTrue(res.toString().indexOf("{OK}") > 0);
    }
    
    public void testBug47413() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug47413.jsp");
        
        String result = res.toString();
        assertTrue(result.indexOf("00-hello world") > 0);
        assertTrue(result.indexOf("01-hello world") > 0);
        assertTrue(result.indexOf("02-3.22") > 0);
        assertTrue(result.indexOf("03-3.22") > 0);
        assertTrue(result.indexOf("04-17") > 0);
        assertTrue(result.indexOf("05-17") > 0);
        assertTrue(result.indexOf("06-hello world") > 0);
        assertTrue(result.indexOf("07-hello world") > 0);
        assertTrue(result.indexOf("08-0.0") > 0);
        assertTrue(result.indexOf("09-0.0") > 0);
        assertTrue(result.indexOf("10-0") > 0);
        assertTrue(result.indexOf("11-0") > 0);
    }

    public void testBug48112() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/bug48112.jsp");
        assertTrue(res.toString().indexOf("{OK}") > 0);
    }
    
    public void testELMisc() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = 
            new File("test/webapp");
        // app dir is relative to server home
        tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());
        
        tomcat.start();

        ByteChunk res = getUrl("http://localhost:" + getPort() +
                "/test/el-misc.jsp");
        String result = res.toString();
        assertTrue(result.indexOf("00-\\\\\\\"${'hello world'}") > 0);
        assertTrue(result.indexOf("01-\\\\\\\"\\${'hello world'}") > 0);
        assertTrue(result.indexOf("02-\\\"\\${'hello world'}") > 0); // TODO - bug
        assertTrue(result.indexOf("03-\\\"\\hello world") > 0);      // TODO - bug
        assertTrue(result.indexOf("2az-04") > 0);
        assertTrue(result.indexOf("05-a2z") > 0);
        assertTrue(result.indexOf("06-az2") > 0);
        assertTrue(result.indexOf("2az-07") > 0);
        assertTrue(result.indexOf("08-a2z") > 0);
        assertTrue(result.indexOf("09-az2") > 0);
        assertTrue(result.indexOf("10-${'foo'}bar") > 0);
        assertTrue(result.indexOf("11-\"}") > 0);
    }


}
