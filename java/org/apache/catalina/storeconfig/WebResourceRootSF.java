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

package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.WebResourceRoot;

/**
 * Generate Resources element
 */
public class WebResourceRootSF extends StoreFactoryBase {

    /**
     * Store the specified Resources childs.
     * 
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent
     *            Number of spaces to indent this element
     * @param aCluster
     *            Cluster whose properties are being stored
     * 
     * @exception Exception
     *                if an exception occurs while storing
     */
    public void storeChilds(PrintWriter aWriter, int indent, Object aResourceRoot,
            StoreDescription parentDesc) throws Exception {
        if (aResourceRoot instanceof WebResourceRoot) {
            // FIXME: No getter to access PreResource, JarResources and PostResources
        }
    }
}