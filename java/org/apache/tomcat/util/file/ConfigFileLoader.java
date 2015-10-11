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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

/**
 * This class is used to obtain {@link InputStream}s for configuration files
 * from a given location String. This allows greater flexibility than these
 * files having to be loaded directly from a file system.
 */
public class ConfigFileLoader {

    private static final URI CATALINA_BASE_URI;

    static {
        File catalinaBase = new File(System.getProperty("catalina.base"));
        CATALINA_BASE_URI = catalinaBase.toURI();
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
    public static InputStream getInputStream(String location) throws IOException {
        // Absolute URIs will be left alone
        // Relative files will be resolved relative to catalina base
        // Absolute files will be converted to URIs
        URI uri;
        if (location != null &&
                (location.length() > 2 && ":\\".equals(location.substring(1, 3)) ||
                    location.startsWith("\\\\"))) {
            // This is an absolute file path in Windows or a UNC path
            File f = new File(location);
            uri =f.getAbsoluteFile().toURI();
        } else {
            // URL, relative path or an absolute path on a non-Windows platforms
            uri = CATALINA_BASE_URI.resolve(location);
        }
        URL url = uri.toURL();
        return url.openConnection().getInputStream();
    }
}
