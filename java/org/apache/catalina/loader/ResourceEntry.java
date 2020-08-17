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
package org.apache.catalina.loader;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.jar.JarFile;
/**
 * Resource entry.
 *
 * @author Remy Maucherat
 */
public class ResourceEntry {

    /** Weak references here allow the key-value pair to be garbage collected if the key-value pair is no longer
     *  referenced outside the Hashmap. In that case the setSourceFile will set the JarFile in the WeakHashmap again whenever the JarFile is referenced.
    */
    private static final Map<String, JarFile> JAR_FILE_CACHE = new WeakHashMap<>();

    /**
     * The "last modified" time of the origin file at the time this resource
     * was loaded, in milliseconds since the epoch.
     */
    public long lastModified = -1;


    /**
     * Loaded class.
     */
    public volatile Class<?> loadedClass = null;

    /**
     * Single, reusable jar file for this resource. Avoids duplication.
     */
    private JarFile sourceJar;

    /**
     * Deduplicates the jar file, then stores for reuse.
     *
     * @param sourceJar
     */
    public void setSourceJar(JarFile sourceJar) {
        String jarName = sourceJar.getName();

        JarFile uniqueJarFile;
        synchronized (JAR_FILE_CACHE) {
            uniqueJarFile = JAR_FILE_CACHE.get(jarName);
            if (uniqueJarFile == null) {
                uniqueJarFile = sourceJar;
                JAR_FILE_CACHE.put(jarName, uniqueJarFile);
            }
        }

        this.sourceJar = uniqueJarFile;
    }

    // Call the setSourceFile if it returns null. Example in WebappClassLoaderBase
    public JarFile getSourceJar() {
        return sourceJar;
    }
}

