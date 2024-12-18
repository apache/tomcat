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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assume;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.net.TesterSupport;
import org.apache.tomcat.util.net.openssl.OpenSSLImplementation;

public class TestAprLifecycleListener {

    @Test
    public void testMultipleServerInstancesUsingTomcatNativeLibrary01() throws Exception {
        doTestMultipleServerInstancesUsingTomcatNativeLibrary(false, false);
    }


    @Test
    public void testMultipleServerInstancesUsingTomcatNativeLibrary02() throws Exception {
        doTestMultipleServerInstancesUsingTomcatNativeLibrary(true, false);
    }


    @Test
    public void testMultipleServerInstancesUsingFFM01() throws Exception {
        Assume.assumeTrue(JreCompat.isJre22Available());
        doTestMultipleServerInstancesUsingTomcatNativeLibrary(false, true);
    }


    @Test
    public void testMultipleServerInstancesUsingFFM02() throws Exception {
        Assume.assumeTrue(JreCompat.isJre22Available());
        doTestMultipleServerInstancesUsingTomcatNativeLibrary(true, true);
    }


    private void doTestMultipleServerInstancesUsingTomcatNativeLibrary(boolean reverseShutdownOrder, boolean ffm) throws Exception {
        Path tmpDir = Paths.get(System.getProperty("tomcat.test.temp", "output/tmp"));
        Files.createDirectories(tmpDir);

        Tomcat tomcat1 = new Tomcat();
        Path base1 = Files.createTempDirectory(tmpDir, "tomcat1-");
        tomcat1.setBaseDir(base1.toAbsolutePath().toString());
        tomcat1.setPort(0);
        TesterSupport.initSsl(tomcat1);
        TesterSupport.configureSSLImplementation(tomcat1,
                ffm ? "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation" : OpenSSLImplementation.class.getName(), true);
        tomcat1.init();

        Tomcat tomcat2 = new Tomcat();
        Path base2 = Files.createTempDirectory(tmpDir, "tomcat2-");
        tomcat2.setBaseDir(base2.toAbsolutePath().toString());
        tomcat2.setPort(0);
        TesterSupport.initSsl(tomcat2);
        TesterSupport.configureSSLImplementation(tomcat2,
                ffm ? "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation" : OpenSSLImplementation.class.getName(), true);
        tomcat2.init();

        // Start 1, then 2
        tomcat1.start();
        tomcat2.start();

        if (reverseShutdownOrder) {
            // Stop and destroy 2 then 1
            tomcat2.stop();
            tomcat2.destroy();
        }

        tomcat1.stop();
        tomcat1.destroy();

        if (!reverseShutdownOrder) {
            // Stop and destroy 1 then 2
            tomcat2.stop();
            tomcat2.destroy();
        }
    }
}
