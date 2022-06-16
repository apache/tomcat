/**
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

import org.apache.catalina.LifecycleListener;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.UpgradeProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;

/**
 * Store Connector and Listeners
 */
public class ConnectorSF extends StoreFactoryBase {

    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aConnector,
            StoreDescription parentDesc) throws Exception {

        if (aConnector instanceof Connector) {
            Connector connector = (Connector) aConnector;
            // Store nested <Listener> elements
            LifecycleListener listeners[] = connector.findLifecycleListeners();
            storeElementArray(aWriter, indent, listeners);
            // Store nested <UpgradeProtocol> elements
            UpgradeProtocol[] upgradeProtocols = connector.findUpgradeProtocols();
            storeElementArray(aWriter, indent, upgradeProtocols);
            if (Boolean.TRUE.equals(connector.getProperty("SSLEnabled"))) {
                // Store nested <SSLHostConfig> elements
                SSLHostConfig[] hostConfigs = connector.findSslHostConfigs();
                storeElementArray(aWriter, indent, hostConfigs);
            }
        }
    }

    protected void printOpenTag(PrintWriter aWriter, int indent, Object bean,
            StoreDescription aDesc) throws Exception {
        aWriter.print("<");
        aWriter.print(aDesc.getTag());
        storeConnectorAttributes(aWriter, indent, bean, aDesc);
        aWriter.println(">");
    }

    protected void storeConnectorAttributes(PrintWriter aWriter, int indent,
            Object bean, StoreDescription aDesc) throws Exception {
        if (aDesc.isAttributes()) {
            getStoreAppender().printAttributes(aWriter, indent, false, bean,
                    aDesc);
        }
    }

    protected void printTag(PrintWriter aWriter, int indent, Object bean,
            StoreDescription aDesc) throws Exception {
        aWriter.print("<");
        aWriter.print(aDesc.getTag());
        storeConnectorAttributes(aWriter, indent, bean, aDesc);
        aWriter.println("/>");
    }

}