/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.authenticator.jaspic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.SAXException;

/**
 * Utility class for the loading and saving of JASPIC persistent provider
 * registrations.
 */
final class PersistentProviderRegistrations {

    private static final Log log = LogFactory.getLog(PersistentProviderRegistrations.class);
    private static final StringManager sm =
            StringManager.getManager(PersistentProviderRegistrations.class);


    private PersistentProviderRegistrations() {
        // Utility class. Hide default constructor
    }


    static Providers loadProviders(File configFile) {
        try (InputStream is = new FileInputStream(configFile)) {
            // Construct a digester to read the XML input file
            Digester digester = new Digester();

            try {
                digester.setFeature("http://apache.org/xml/features/allow-java-encodings", true);
                digester.setValidating(true);
                digester.setNamespaceAware(true);
            } catch (Exception e) {
                throw new SecurityException(e);
            }

            // Create an object to hold the parse results and put it on the top
            // of the digester's stack
            Providers result = new Providers();
            digester.push(result);

            // Configure the digester
            digester.addObjectCreate("jaspic-providers/provider", Provider.class.getName());
            digester.addSetProperties("jaspic-providers/provider");
            digester.addSetNext("jaspic-providers/provider", "addProvider", Provider.class.getName());

            digester.addObjectCreate("jaspic-providers/provider/property", Property.class.getName());
            digester.addSetProperties("jaspic-providers/provider/property");
            digester.addSetNext("jaspic-providers/provider/property", "addProperty", Property.class.getName());

            // Parse the input
            digester.parse(is);

            return result;
        } catch (IOException | SAXException e) {
            throw new SecurityException(e);
        }
    }


    static void writeProviders(Providers providers, File configFile) {
        File configFileOld = new File(configFile.getAbsolutePath() + ".old");
        File configFileNew = new File(configFile.getAbsolutePath() + ".new");

        // Remove left over temporary files if present
        if (configFileOld.exists()) {
            if (configFileOld.delete()) {
                throw new SecurityException(sm.getString(
                        "persistentProviderRegistrations.existsDeleteFail",
                        configFileOld.getAbsolutePath()));
            }
        }
        if (configFileNew.exists()) {
            if (configFileNew.delete()) {
                throw new SecurityException(sm.getString(
                        "persistentProviderRegistrations.existsDeleteFail",
                        configFileNew.getAbsolutePath()));
            }
        }

        // Write out the providers to the temporary new file
        try (OutputStream fos = new FileOutputStream(configFileNew);
                Writer writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(
                    "<?xml version='1.0' encoding='utf-8'?>\n" +
                    "<jaspic-providers\n" +
                    "    xmlns=\"http://tomcat.apache.org/xml\"\n" +
                    "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "    xsi:schemaLocation=\"http://tomcat.apache.org/xml jaspic-providers.xsd\"\n" +
                    "    version=\"1.0\">\n");
            for (Provider provider : providers.providers) {
                writer.write("  <provider className=\"");
                writer.write(provider.getClassName());
                writer.write("\" layer=\"");
                writer.write(provider.getLayer());
                writer.write("\" appContext=\"");
                writer.write(provider.getAppContext());
                if (provider.getDescription() != null) {
                    writer.write("\" description=\"");
                    writer.write(provider.getDescription());
                }
                writer.write("\">\n");
                for (Entry<String,String> entry : provider.getProperties().entrySet()) {
                    writer.write("    <property name=\"");
                    writer.write(entry.getKey());
                    writer.write("\" value=\"");
                    writer.write(entry.getValue());
                    writer.write("\"/>\n");
                }
                writer.write("  </provider>\n");
            }
            writer.write("</jaspic-providers>\n");
        } catch (IOException e) {
            configFileNew.delete();
            throw new SecurityException(e);
        }

        // Move the current file out of the way
        if (configFile.isFile()) {
            if (!configFile.renameTo(configFileOld)) {
                throw new SecurityException(sm.getString("persistentProviderRegistrations.moveFail",
                        configFile.getAbsolutePath(), configFileOld.getAbsolutePath()));
            }
        }

        // Move the new file into place
        if (!configFileNew.renameTo(configFile)) {
            throw new SecurityException(sm.getString("persistentProviderRegistrations.moveFail",
                    configFileNew.getAbsolutePath(), configFile.getAbsolutePath()));
        }

        // Remove the old file
        if (configFileOld.exists() && !configFileOld.delete()) {
            log.warn(sm.getString("persistentProviderRegistrations.deleteFail",
                    configFileOld.getAbsolutePath()));
        }
    }


    public static class Providers {
        private final List<Provider> providers = new ArrayList<>();

        public void addProvider(Provider provider) {
            providers.add(provider);
        }

        public List<Provider> getProviders() {
            return providers;
        }
    }


    public static class Provider {
        private String className;
        private String layer;
        private String appContext;
        private String description;
        private final Map<String,String> properties = new HashMap<>();


        public String getClassName() {
            return className;
        }
        public void setClassName(String className) {
            this.className = className;
        }


        public String getLayer() {
            return layer;
        }
        public void setLayer(String layer) {
            this.layer = layer;
        }


        public String getAppContext() {
            return appContext;
        }
        public void setAppContext(String appContext) {
            this.appContext = appContext;
        }


        public String getDescription() {
            return description;
        }
        public void setDescription(String description) {
            this.description = description;
        }


        public void addProperty(Property property) {
            properties.put(property.getName(), property.getValue());
        }
        void addProperty(String name, String value) {
            properties.put(name, value);
        }
        public Map<String,String> getProperties() {
            return properties;
        }
    }


    public static class Property {
        private String name;
        private String value;


        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }


        public String getValue() {
            return value;
        }
        public void setValue(String value) {
            this.value = value;
        }
    }
}
