/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.tomcat.util.file;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * This class is used to obtain {@link InputStream}s for configuration files
 * from a given location String. This allows greater flexibility than these
 * files having to be loaded directly from a file system.
 */
public class ConfigFileLoader {

    private static ConfigurationSource source;

    public static final ConfigurationSource getSource() {
        if (ConfigFileLoader.source == null) {
            return ConfigurationSource.DEFAULT;
        }
        return source;
    }

    public static final void setSource(ConfigurationSource source) {
        if (ConfigFileLoader.source == null) {
            ConfigFileLoader.source = source;
        }
    }

    private ConfigFileLoader() {
        // Utility class. Hide the default constructor.
    }


    /**
     * Load the resource from the specified location.
     *
     * @param location The location for the resource of interest. The location
     *                 may be a URL or a file path. Relative paths will be
     *                 resolved against CATALINA_BASE.
     *
     * @return The InputStream for the given resource. The caller is responsible
     *         for closing this stream when it is no longer used.
     *
     * @throws IOException If an InputStream cannot be created using the
     *                     provided location
     */
    @Deprecated
    public static InputStream getInputStream(String location) throws IOException {
        return getSource().getResource(location).getInputStream();
    }


    @Deprecated
    public static URI getURI(String location) {
        return getSource().getURI(location);
    }

}
