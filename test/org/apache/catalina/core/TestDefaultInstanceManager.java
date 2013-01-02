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
package org.apache.catalina.core;

import java.io.File;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;


public class TestDefaultInstanceManager extends TomcatBaseTest {

    @Test
    public void testClassUnloading() throws Exception {

        DefaultInstanceManager instanceManager = doClassUnloadingPrep();

        // Request a JSP page (that doesn't load any tag libraries etc.)
        // This page does use @PostConstruct to ensure that the cache does not
        // retain strong references
        getUrl("http://localhost:" + getPort() + "/test/annotations.jsp");
        // Request a second JSP (again, no tag libraries etc.)
        getUrl("http://localhost:" + getPort() + "/test/bug36923.jsp");

        // Check the number of classes in the cache
        int count = instanceManager.getAnnotationCacheSize();

        // Request a third JSP (again, no tag libraries etc.)
        getUrl("http://localhost:" + getPort() + "/test/bug5nnnn/bug51544.jsp");

        // Force a GC to clear out unloaded class (first JSP)
        System.gc();

        // Spin a while until GC happens or we wait too long
        int loop = 0;
        while (loop < 10) {
            if (instanceManager.getAnnotationCacheSize() == count) {
                break;
            }
            Thread.sleep(100);
            loop++;
        }

        // First JSP should be unloaded and replaced by third (second left
        // alone) so no change in overall count
        assertEquals(count, instanceManager.getAnnotationCacheSize());
    }

    private DefaultInstanceManager doClassUnloadingPrep() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Create the context (don't use addWebapp as we want to modify the
        // JSP Servlet settings).
        File appDir = new File("test/webapp-3.0");
        StandardContext ctxt = (StandardContext) tomcat.addContext(
                null, "/test", appDir.getAbsolutePath());

        // Configure the defaults and then tweak the JSP servlet settings
        // Note: Min value for maxLoadedJsps is 2
        Tomcat.initWebappDefaults(ctxt);
        Wrapper w = (Wrapper) ctxt.findChild("jsp");
        w.addInitParameter("maxLoadedJsps", "2");

        tomcat.start();

        return (DefaultInstanceManager) ctxt.getInstanceManager();
    }
}
