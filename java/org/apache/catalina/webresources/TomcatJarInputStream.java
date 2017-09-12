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

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

/**
 * The purpose of this sub-class is to obtain references to the JarEntry objects
 * for META-INF/ and META-INF/MANIFEST.MF that are otherwise swallowed by the
 * JarInputStream implementation.
 */
public class TomcatJarInputStream extends JarInputStream {

    private JarEntry metaInfEntry;
    private JarEntry manifestEntry;


    TomcatJarInputStream(InputStream in) throws IOException {
        super(in);
    }


    @Override
    protected ZipEntry createZipEntry(String name) {
        ZipEntry ze = super.createZipEntry(name);
        if (metaInfEntry == null && "META-INF/".equals(name)) {
            metaInfEntry = (JarEntry) ze;
        } else if (manifestEntry == null && "META-INF/MANIFESR.MF".equals(name)) {
            manifestEntry = (JarEntry) ze;
        }
        return ze;
    }


    JarEntry getMetaInfEntry() {
        return metaInfEntry;
    }


    JarEntry getManifestEntry() {
        return manifestEntry;
    }
}
