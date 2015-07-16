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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;
import org.xml.sax.SAXException;

/**
 * <b>XML Format </b>
 *
 * <pre>
 *
 *       &lt;Registry name=&quot;&quot; encoding=&quot;UTF8&quot; &gt;
 *       &lt;Description tag=&quot;Server&quot; standard=&quot;true&quot; default=&quot;true&quot;/&gt;
 *          tagClass=&quot;org.apache.catalina.core.StandardServer&quot;
 *          storeFactory=&quot;org.apache.catalina.storeconfig.StandardServerSF&quot;&gt;
 *        &lt;TransientAttributes&gt;
 *          &lt;Attribute&gt;&lt;/Attribute&gt;
 *        &lt;/TransientAttributes&gt;
 *        &lt;TransientChildren&gt;
 *          &lt;Child&gt;&lt;/Child&gt;
 *        &lt;/TransientChildren&gt;
 *       &lt;/Description&gt;
 *   ...
 *       &lt;/Tegistry&gt;
 *
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
    private static Log log = LogFactory.getLog(StoreLoader.class);

    /**
     * The <code>Digester</code> instance used to parse registry descriptors.
     */
    protected static Digester digester = createDigester();

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
     */
    protected static Digester createDigester() {
        long t1 = System.currentTimeMillis();
        // Initialize the digester
        Digester digester = new Digester();
        digester.setValidating(false);
        digester.setClassLoader(StoreRegistry.class.getClassLoader());

        // Configure the actions we will be using
        digester.addObjectCreate("Registry",
                "org.apache.catalina.storeconfig.StoreRegistry", "className");
        digester.addSetProperties("Registry");
        digester
                .addObjectCreate("Registry/Description",
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

        long t2 = System.currentTimeMillis();
        if (log.isDebugEnabled())
            log.debug("Digester for server-registry.xml created " + (t2 - t1));
        return (digester);

    }

    /**
     *
     * @param aFile
     * @return The server file
     */
    protected File serverFile(String aFile) {

        if (aFile == null || aFile.length() < 1)
            aFile = "server-registry.xml";
        File file = new File(aFile);
        if (!file.isAbsolute())
            file = new File(System.getProperty("catalina.base") + "/conf",
                    aFile);
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {
            log.error(e);
        }
        return (file);
    }

    /**
     * Load Description from external source
     *
     * @param aURL
     */
    public void load(String aURL) {
        synchronized (digester) {
            File aRegistryFile = serverFile(aURL);
            try {
                registry = (StoreRegistry) digester.parse(aRegistryFile);
                registryResource = aRegistryFile.toURI().toURL();
            } catch (IOException e) {
                log.error(e);
            } catch (SAXException e) {
                log.error(e);
            }
        }

    }

    /**
     * Load from defaults
     * <ul>
     * <li>System Property URL catalina.storeregistry</li>
     * <li>File $catalina.base/conf/server-registry.xml</li>
     * <li>class resource org/apache/catalina/storeconfig/server-registry.xml
     * </li>
     * </ul>
     */
    public void load() {

        InputStream is = null;
        Throwable error = null;
        registryResource = null ;
        try {
            String configUrl = getConfigUrl();
            if (configUrl != null) {
                is = (new URL(configUrl)).openStream();
                if (log.isDebugEnabled())
                    log.debug("Find registry server-registry.xml from system property at url "
                            + configUrl);
                registryResource = new URL(configUrl);
            }
        } catch (Throwable t) {
            // Ignore
        }
        if (is == null) {
            try {
                File home = new File(getCatalinaBase());
                File conf = new File(home, "conf");
                File reg = new File(conf, "server-registry.xml");
                is = new FileInputStream(reg);
                if (log.isInfoEnabled())
                    log.info("Find registry server-registry.xml at file "
                            + reg.getCanonicalPath());
                registryResource = reg.toURI().toURL();
            } catch (Throwable t) {
                // Ignore
            }
        }
        if (is == null) {
            try {
                is = StoreLoader.class
                        .getResourceAsStream("/org/apache/catalina/storeconfig/server-registry.xml");
                if (log.isInfoEnabled())
                    log
                            .info("Find registry server-registry.xml at classpath resource");
                registryResource = StoreLoader.class
                    .getResource("/org/apache/catalina/storeconfig/server-registry.xml");

            } catch (Throwable t) {
                // Ignore
            }
        }
        if (is != null) {
            try {
                synchronized (digester) {
                    registry = (StoreRegistry) digester.parse(is);
                }
            } catch (Throwable t) {
                error = t;
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
        if ((is == null) || (error != null)) {
            log.error(error);
        }
    }

    /**
     * Get the value of the catalina.home environment variable.
     */
    private static String getCatalinaHome() {
        return System.getProperty("catalina.home", System
                .getProperty("user.dir"));
    }

    /**
     * Get the value of the catalina.base environment variable.
     */
    private static String getCatalinaBase() {
        return System.getProperty("catalina.base", getCatalinaHome());
    }

    /**
     * Get the value of the configuration URL.
     */
    private static String getConfigUrl() {
        return System.getProperty("catalina.storeconfig");
    }

    /**
     * @return Returns the registryResource.
     */
    public URL getRegistryResource() {
        return registryResource;
    }
}
