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
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestWarURLStreamHandlerIntegration extends TomcatBaseTest {

    @Test
    public void testToURI() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File docBase = new File("test/webresources/war-url-connection.war");
        Context context = tomcat.addWebapp("/test", docBase.getAbsolutePath());

        ((StandardHost) tomcat.getHost()).setUnpackWARs(false);

        tomcat.start();

        URL url = context.getServletContext().getResource("/index.html");
        try {
            url.toURI();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
}
