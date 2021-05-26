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
import org.apache.tomcat.util.descriptor.web.ContextEjb;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextLocalEjb;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef;
import org.apache.tomcat.util.descriptor.web.ContextResourceLink;

/**
 * Store server.xml elements Resources at context and GlobalNamingResources
 */
public class NamingResourcesSF extends StoreFactoryBase {
    private static Log log = LogFactory.getLog(NamingResourcesSF.class);

    /**
     * Store the only the NamingResources elements
     *
     * @see NamingResourcesSF#storeChildren(PrintWriter, int, Object, StoreDescription)
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception {
        StoreDescription elementDesc = getRegistry().findDescription(
                aElement.getClass());
        if (elementDesc != null) {
            if (log.isDebugEnabled()) {
                log.debug("store " + elementDesc.getTag() + "( " + aElement
                        + " )");
            }
            storeChildren(aWriter, indent, aElement, elementDesc);
        } else {
            if (log.isWarnEnabled()) {
                log.warn("Descriptor for element" + aElement.getClass()
                        + " not configured!");
            }
        }
    }

    /**
     * Store the specified NamingResources properties.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent
     *            Number of spaces to indent this element
     * @param aElement
     *            Object whose properties are being stored
     * @param elementDesc
     *            element descriptor
     *
     * @exception Exception
     *                if an exception occurs while storing
     *
     * @see org.apache.catalina.storeconfig.StoreFactoryBase#storeChildren(java.io.PrintWriter,
     *      int, java.lang.Object, StoreDescription)
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aElement,
            StoreDescription elementDesc) throws Exception {

        if (aElement instanceof NamingResourcesImpl) {
            NamingResourcesImpl resources = (NamingResourcesImpl) aElement;
            // Store nested <Ejb> elements
            ContextEjb[] ejbs = resources.findEjbs();
            storeElementArray(aWriter, indent, ejbs);
            // Store nested <Environment> elements
            ContextEnvironment[] envs = resources.findEnvironments();
            storeElementArray(aWriter, indent, envs);
            // Store nested <LocalEjb> elements
            ContextLocalEjb[] lejbs = resources.findLocalEjbs();
            storeElementArray(aWriter, indent, lejbs);

            // Store nested <Resource> elements
            ContextResource[] dresources = resources.findResources();
            storeElementArray(aWriter, indent, dresources);

            // Store nested <ResourceEnvRef> elements
            ContextResourceEnvRef[] resEnv = resources.findResourceEnvRefs();
            storeElementArray(aWriter, indent, resEnv);

            // Store nested <ResourceLink> elements
            ContextResourceLink[] resourceLinks = resources.findResourceLinks();
            storeElementArray(aWriter, indent, resourceLinks);
        }
    }
}

