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
import org.apache.catalina.WebResourceSet;

/**
 * Generate Resources element
 */
public class WebResourceRootSF extends StoreFactoryBase {

    /**
     * Store the specified Resources children.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent
     *            Number of spaces to indent this element
     *
     * @exception Exception
     *                if an exception occurs while storing
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aResourceRoot,
            StoreDescription parentDesc) throws Exception {
        if (aResourceRoot instanceof WebResourceRoot) {
            WebResourceRoot resourceRoot = (WebResourceRoot) aResourceRoot;

            // Store nested <PreResources> elements
            WebResourceSet[] preResourcesArray = resourceRoot.getPreResources();
            StoreDescription preResourcesElementDesc = getRegistry().findDescription(
                    WebResourceSet.class.getName()
                            + ".[PreResources]");
            if (preResourcesElementDesc != null) {
                for (WebResourceSet preResources : preResourcesArray) {
                    preResourcesElementDesc.getStoreFactory().store(aWriter, indent,
                            preResources);
                }
            }

            // Store nested <JarResources> elements
            WebResourceSet[] jarResourcesArray = resourceRoot.getJarResources();
            StoreDescription jarResourcesElementDesc = getRegistry().findDescription(
                    WebResourceSet.class.getName()
                            + ".[JarResources]");
            if (jarResourcesElementDesc != null) {
                for (WebResourceSet jarResources : jarResourcesArray) {
                    jarResourcesElementDesc.getStoreFactory().store(aWriter, indent,
                            jarResources);
                }
            }

            // Store nested <PostResources> elements
            WebResourceSet[] postResourcesArray = resourceRoot.getPostResources();
            StoreDescription postResourcesElementDesc = getRegistry().findDescription(
                    WebResourceSet.class.getName()
                            + ".[PostResources]");
            if (postResourcesElementDesc != null) {
                for (WebResourceSet postResources : postResourcesArray) {
                    postResourcesElementDesc.getStoreFactory().store(aWriter, indent,
                            postResources);
                }
            }

        }
    }
}