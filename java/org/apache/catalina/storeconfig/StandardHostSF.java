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
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.Cluster;
import org.apache.catalina.Container;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.ha.ClusterValve;

/**
 * Store server.xml Element Host
 */
public class StandardHostSF extends StoreFactoryBase {

    /**
     * Store the specified Host properties and children
     * (Listener,Alias,Realm,Valve,Cluster, Context)
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent
     *            Number of spaces to indent this element
     * @param aHost
     *            Host whose properties are being stored
     *
     * @exception Exception
     *                if an exception occurs while storing
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aHost,
            StoreDescription parentDesc) throws Exception {
        if (aHost instanceof StandardHost) {
            StandardHost host = (StandardHost) aHost;
            // Store nested <Listener> elements
            LifecycleListener listeners[] = ((Lifecycle) host)
                    .findLifecycleListeners();
            storeElementArray(aWriter, indent, listeners);

            // Store nested <Alias> elements
            String aliases[] = host.findAliases();
            getStoreAppender().printTagArray(aWriter, "Alias", indent + 2,
                    aliases);

            // Store nested <Realm> element
            Realm realm = host.getRealm();
            if (realm != null) {
                Realm parentRealm = null;
                if (host.getParent() != null) {
                    parentRealm = host.getParent().getRealm();
                }
                if (realm != parentRealm) {
                    storeElement(aWriter, indent, realm);
                }
            }

            // Store nested <Valve> elements
            Valve valves[] = host.getPipeline().getValves();
            if(valves != null && valves.length > 0 ) {
                List<Valve> hostValves = new ArrayList<>() ;
                for (Valve valve : valves) {
                    if (!(valve instanceof ClusterValve)) {
                        hostValves.add(valve);
                    }
                }
                storeElementArray(aWriter, indent, hostValves.toArray());
            }

            // store all <Cluster> elements
            Cluster cluster = host.getCluster();
            if (cluster != null) {
                Cluster parentCluster = null;
                if (host.getParent() != null) {
                    parentCluster = host.getParent().getCluster();
                }
                if (cluster != parentCluster) {
                    storeElement(aWriter, indent, cluster);
                }
            }

            // store all <Context> elements
            Container children[] = host.findChildren();
            storeElementArray(aWriter, indent, children);
        }
    }

}