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

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Service;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.deploy.NamingResourcesImpl;

/**
 * Store server.xml Server element and children (
 * Listener,GlobalNamingResource,Service)
 */
public class StandardServerSF extends StoreFactoryBase {

    /**
     * Store the specified Server properties.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent
     *            Number of spaces to indent this element
     * @param aServer
     *            Object to be stored
     *
     * @exception Exception
     *                if an exception occurs while storing
     * @see org.apache.catalina.storeconfig.IStoreFactory#store(java.io.PrintWriter,
     *      int, java.lang.Object)
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aServer)
            throws Exception {
        storeXMLHead(aWriter);
        super.store(aWriter, indent, aServer);
    }

    /**
     * Store Children from this StandardServer description
     *
     * @param aWriter
     * @param indent
     * @param aObject
     * @param parentDesc
     * @throws Exception
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aObject,
            StoreDescription parentDesc) throws Exception {
        if (aObject instanceof StandardServer) {
            StandardServer server = (StandardServer) aObject;
            // Store nested <Listener> elements
            LifecycleListener listeners[] = ((Lifecycle) server)
                    .findLifecycleListeners();
            storeElementArray(aWriter, indent, listeners);
            /*LifecycleListener listener = null;
            for (int i = 0; listener == null && i < listeners.length; i++)
                if (listeners[i] instanceof ServerLifecycleListener)
                    listener = listeners[i];
            if (listener != null) {
                StoreDescription elementDesc = getRegistry()
                        .findDescription(
                                StandardServer.class.getName()
                                        + ".[ServerLifecycleListener]");
                if (elementDesc != null) {
                    elementDesc.getStoreFactory().store(aWriter, indent,
                            listener);
                }
            }*/
            // Store nested <GlobalNamingResources> element
            NamingResourcesImpl globalNamingResources = server
                    .getGlobalNamingResources();
            StoreDescription elementDesc = getRegistry().findDescription(
                    NamingResourcesImpl.class.getName()
                            + ".[GlobalNamingResources]");
            if (elementDesc != null) {
                elementDesc.getStoreFactory().store(aWriter, indent,
                        globalNamingResources);
            }
            // Store nested <Service> elements
            Service services[] = server.findServices();
            storeElementArray(aWriter, indent, services);
        }
    }

}
