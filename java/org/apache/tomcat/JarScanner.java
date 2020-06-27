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
package org.apache.tomcat;

import jakarta.servlet.ServletContext;

/**
 * Scans a web application and classloader hierarchy for JAR files. Uses
 * include TLD scanning and web-fragment.xml scanning. Uses a call-back
 * mechanism so the caller can process each JAR found.
 */
public interface JarScanner {

    /**
     * Scan the provided ServletContext and classloader for JAR files. Each JAR
     * file found will be passed to the callback handler to be processed.
     *
     * @param scanType      The type of JAR scan to perform. This is passed to
     *                          the filter which uses it to determine how to
     *                          filter the results
     * @param context       The ServletContext - used to locate and access
     *                      WEB-INF/lib
     * @param callback      The handler to process any JARs found
     */
    public void scan(JarScanType scanType, ServletContext context,
            JarScannerCallback callback);

    public JarScanFilter getJarScanFilter();

    public void setJarScanFilter(JarScanFilter jarScanFilter);
}
