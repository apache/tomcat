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

public interface JarScanFilter {

    /**
     *
     * @param jarScanType   The type of JAR scan currently being performed
     * @param jarName       The name of the JAR file (without any path
     *                          information) to be checked to see if it should
     *                          be included in the results or not
     * @return <code>true</code> if the JAR should be returned in the results,
     *             <code>false</code> if it should be excluded
     */
    boolean check(JarScanType jarScanType, String jarName);
}
