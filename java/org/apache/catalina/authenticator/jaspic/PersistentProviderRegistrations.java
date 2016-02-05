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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tomcat.util.digester.Digester;
import org.xml.sax.SAXException;

final class PersistentProviderRegistrations {

    private PersistentProviderRegistrations() {
        // Utility class. Hide default constructor
    }


    static Providers getProviders(File configFile) {
        try (InputStream is = new FileInputStream(configFile)) {
            // Construct a digester to read the XML input file
            Digester digester = new Digester();

            try {
                digester.setFeature("http://apache.org/xml/features/allow-java-encodings", true);
                // TODO: Configure the digester to validate the input against
                //       the XSD
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
