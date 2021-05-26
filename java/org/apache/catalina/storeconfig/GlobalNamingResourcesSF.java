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

import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * store server.xml GlobalNamingResource.
 */
public class GlobalNamingResourcesSF extends StoreFactoryBase {
    private static Log log = LogFactory.getLog(GlobalNamingResourcesSF.class);

    /*
     * Store with NamingResource Factory
     *
     * @see org.apache.catalina.storeconfig.IStoreFactory#store(java.io.PrintWriter,
     *      int, java.lang.Object)
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception {

        if (aElement instanceof NamingResourcesImpl) {

            StoreDescription elementDesc = getRegistry().findDescription(
                    NamingResourcesImpl.class.getName()
                            + ".[GlobalNamingResources]");

            if (elementDesc != null) {
                getStoreAppender().printIndent(aWriter, indent + 2);
                getStoreAppender().printOpenTag(aWriter, indent + 2, aElement,
                        elementDesc);
                NamingResourcesImpl resources = (NamingResourcesImpl) aElement;
                StoreDescription resourcesdesc = getRegistry().findDescription(
                        NamingResourcesImpl.class.getName());
                if (resourcesdesc != null) {
                    resourcesdesc.getStoreFactory().store(aWriter, indent + 2,
                            resources);
                } else {
                    if(log.isWarnEnabled()) {
                        log.warn("Can't find NamingResources Store Factory!");
                    }
                }

                getStoreAppender().printIndent(aWriter, indent + 2);
                getStoreAppender().printCloseTag(aWriter, elementDesc);
            } else {
                if (log.isWarnEnabled()) {
                    log.warn("Descriptor for element" + aElement.getClass()
                            + " not configured!");
                }
            }
        } else {
            if (log.isWarnEnabled()) {
                log.warn("wrong element " + aElement.getClass());
            }

        }
    }
}

