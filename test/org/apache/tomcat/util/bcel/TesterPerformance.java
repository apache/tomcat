/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.bcel;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.Test;

import org.apache.tomcat.util.bcel.classfile.ClassParser;
import org.apache.tomcat.util.scan.Jar;
import org.apache.tomcat.util.scan.JarFactory;

public class TesterPerformance {

    private static final String JAR_LOCATION = "/tmp/jira-libs";

    @Test
    public void testClassParserPerformance() throws IOException {
        File libDir = new File(JAR_LOCATION);
        String[] libs = libDir.list();

        Set<URL> jarURLs = new HashSet<>();

        for (String lib : libs) {
            if (!lib.toLowerCase(Locale.ENGLISH).endsWith(".jar")) {
                continue;
            }
            jarURLs.add(new URL("jar:" + new File (libDir, lib).toURI().toURL().toExternalForm() + "!/"));
        }

        long start = System.nanoTime();

        for (URL jarURL : jarURLs) {
            Jar jar = JarFactory.newInstance(jarURL);
            jar.nextEntry();
            String jarEntryName = jar.getEntryName();
            while (jarEntryName != null) {
                if (jarEntryName.endsWith(".class")) {
                    ClassParser cp = new ClassParser(jar.getEntryInputStream(), jarEntryName);
                    cp.parse();
                }
                jar.nextEntry();
                jarEntryName = jar.getEntryName();
            }
        }

        long duration = System.nanoTime() - start;

        System.out.println("ClassParser performance test took: " + duration + "ns");
    }
}
