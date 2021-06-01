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
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.ha.ClusterValve;

/**
 * Store server.xml Element Engine
 */
public class StandardEngineSF extends StoreFactoryBase {

    /**
     * Store the specified Engine properties.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent
     *            Number of spaces to indent this element
     * @param aEngine
     *            Object whose properties are being stored
     *
     * @exception Exception
     *                if an exception occurs while storing
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aEngine,
            StoreDescription parentDesc) throws Exception {
        if (aEngine instanceof StandardEngine) {
            StandardEngine engine = (StandardEngine) aEngine;
            // Store nested <Listener> elements
            LifecycleListener listeners[] = engine.findLifecycleListeners();
            storeElementArray(aWriter, indent, listeners);

            // Store nested <Realm> element
            Realm realm = engine.getRealm();
            Realm parentRealm = null;
            // TODO is this case possible? (see it a old Server 5.0 impl)
            if (engine.getParent() != null) {
                parentRealm = engine.getParent().getRealm();
            }
            if (realm != parentRealm) {
                storeElement(aWriter, indent, realm);

            }

            // Store nested <Valve> elements
            Valve valves[] = engine.getPipeline().getValves();
            if(valves != null && valves.length > 0 ) {
                List<Valve> engineValves = new ArrayList<>() ;
                for (Valve valve : valves) {
                    if (!(valve instanceof ClusterValve)) {
                        engineValves.add(valve);
                    }
                }
                storeElementArray(aWriter, indent, engineValves.toArray());
            }

            // store all <Cluster> elements
            Cluster cluster = engine.getCluster();
            if (cluster != null) {
                storeElement(aWriter, indent, cluster);
            }
            // store all <Host> elements
            Container children[] = engine.findChildren();
            storeElementArray(aWriter, indent, children);

       }
    }
}