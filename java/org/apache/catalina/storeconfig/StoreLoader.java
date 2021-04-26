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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.file.ConfigurationSource.Resource;

/**
 * <b>XML Format </b>
 *
 * <pre>
 * {@code
 *       <Registry name="" encoding="UTF-8" >
 *         <Description
 *             tag="Server"
 *             standard="true"
 *             default="true"
 *             tagClass="org.apache.catalina.core.StandardServer"
 *             storeFactoryClass="org.apache.catalina.storeconfig.StandardServerSF">
 *           <TransientAttributes>
 *             <Attribute></Attribute>
 *           </TransientAttributes>
 *           <TransientChildren>
 *             <Child></Child>
 *           </TransientChildren>
 *         </Description>
 *   ...
 *       </Registry>
 * }
 * </pre>
 *
 *
 * Convention:
 * <ul>
 * <li>Factories at subpackage <i>org.apache.catalina.core.storeconfig.xxxSF
 * </i>.</li>
 * <li>Element name are the unique Class name</li>
 * <li>SF for StoreFactory</li>
 * <li>standard implementation is false</li>
 * </ul>
 * other things:
 * <ul>
 * <li>Registry XML format is a very good option</li>
 * <li>Store format is not fix</li>
 * <li>We hope with the parent declaration we can build recursive child store
 * operation //dream</li>
 * <li>Problem is to access child data from array,collections or normal detail
 * object</li>
 * <li>Default definitions for Listener, Valve Resource? - Based on interface
 * type!</li>
 * </ul>
 */
public class StoreLoader {

    /**
     * The <code>Digester</code> instance used to parse registry descriptors.
     */
    protected static final Digester digester = createDigester();

    private StoreRegistry registry;

    private URL registryResource ;

    /**
     * @return Returns the registry.
     */
    public StoreRegistry getRegistry() {
        return registry;
    }

    /**
     * @param registry
     *            The registry to set.
     */
    public void setRegistry(StoreRegistry registry) {
        this.registry = registry;
    }

    /**
     * Create and configure the Digester we will be using for setup store
     * registry.
     * @return the XML digester that will be used to parse the configuration
     */
    protected static Digester createDigester() {
        // Initialize the digester
        Digester digester = new Digester();
        digester.setValidating(false);
        digester.setClassLoader(StoreRegistry.class.getClassLoader());

        // Configure the actions we will be using
        digester.addObjectCreate("Registry",
                "org.apache.catalina.storeconfig.StoreRegistry", "className");
        digester.addSetProperties("Registry");
        digester.addObjectCreate("Registry/Description",
                        "org.apache.catalina.storeconfig.StoreDescription",
                        "className");
        digester.addSetProperties("Registry/Description");
        digester.addRule("Registry/Description", new StoreFactoryRule(
                "org.apache.catalina.storeconfig.StoreFactoryBase",
                "storeFactoryClass",
                "org.apache.catalina.storeconfig.StoreAppender",
                "storeAppenderClass"));
        digester.addSetNext("Registry/Description", "registerDescription",
                "org.apache.catalina.storeconfig.StoreDescription");
        digester.addCallMethod("Registry/Description/TransientAttribute",
                "addTransientAttribute", 0);
        digester.addCallMethod("Registry/Description/TransientChild",
                "addTransientChild", 0);

        return digester;

    }

    /**
     * Load registry configuration.
     *
     * @param path Path to the configuration file, may be null to use the default
     *  name server-registry.xml
     * @throws Exception when the configuration file isn't found or a parse error occurs
     */
    public void load(String path) throws Exception {
        try (Resource resource = (path == null) ?
                ConfigFileLoader.getSource().getConfResource("server-registry.xml")
                : ConfigFileLoader.getSource().getResource(path);
                InputStream is = resource.getInputStream()) {
            registryResource = resource.getURI().toURL();
            synchronized (digester) {
                registry = (StoreRegistry) digester.parse(is);
            }
        } catch (IOException e) {
            // Try default classloader location
            try (InputStream is = StoreLoader.class
                    .getResourceAsStream("/org/apache/catalina/storeconfig/server-registry.xml")) {
                if (is != null) {
                    registryResource = StoreLoader.class
                            .getResource("/org/apache/catalina/storeconfig/server-registry.xml");
                    synchronized (digester) {
                        registry = (StoreRegistry) digester.parse(is);
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * @return the registryResource.
     */
    public URL getRegistryResource() {
        return registryResource;
    }
}
