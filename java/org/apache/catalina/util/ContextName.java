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
package org.apache.catalina.util;

import java.util.Locale;

/**
 * Utility class to manage context names so there is one place where the
 * conversions between baseName, path and version take place.
 */
public final class ContextName {
    private final String ROOT_NAME = "ROOT";
    private final String VERSION_MARKER = "##";
    private final String FWD_SLASH_REPLACEMENT = "#";

    private final String baseName;
    private final String path;
    private final String version;
    private final String name;
    
    /**
     * Creates an instance from a base name, directory name, WAR name or
     * context.xml name.
     * 
     * @param name  The name to use as the basis for this object
     */
    public ContextName(String name) {
        // Remove file extension, if any
        if (name.toLowerCase(Locale.ENGLISH).endsWith(".war") ||
                name.toLowerCase(Locale.ENGLISH).endsWith(".xml")) {
            baseName = name.substring(0, name.length() -4);
        } else {
            baseName = name;
        }

        // Extract version number
        int versionIndex = baseName.indexOf(VERSION_MARKER);
        if (versionIndex > -1) {
            version = baseName.substring(versionIndex + 2);
        } else {
            version = "";
        }

        if (ROOT_NAME.equals(baseName)) {
            path = "";
        } else if (versionIndex > -1) {
            path = "/" + baseName.substring(0, versionIndex).replaceAll(
                    FWD_SLASH_REPLACEMENT, "/");
        } else {
            path = "/" + baseName.replaceAll(FWD_SLASH_REPLACEMENT, "/");
        }
        
        if (versionIndex > -1) {
            this.name = path + VERSION_MARKER + version;
        } else {
            this.name = path;
        }
    }
    
    /**
     * Construct and instance from a path and version.
     * 
     * @param path      Context path to use
     * @param version   Context version to use
     */
    public ContextName(String path, String version) {
        // Path should never be null or '/'
        if (path == null || "/".equals(path)) {
            this.path = "";
        } else {
            this.path = path;
        }

        // Version should never be null
        if (version == null) {
            this.version = "";
        } else {
            this.version = version;
        }
        
        // Name is path + version
        if ("".equals(this.version)) {
            name = this.path;
        } else {
            name = this.path + VERSION_MARKER + this.version;
        }

        // Base name is converted path + version
        StringBuilder tmp = new StringBuilder();
        if ("".equals(path)) {
            tmp.append(ROOT_NAME);
        } else {
            tmp.append(this.path.substring(1).replaceAll("/",
                    FWD_SLASH_REPLACEMENT));
        }
        if (this.version.length() > 0) {
            tmp.append(VERSION_MARKER);
            tmp.append(this.version);
        }
        this.baseName = tmp.toString();
    }

    public String getBaseName() {
        return baseName;
    }
    
    public String getPath() {
        return path;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getName() {
        return name;
    }
}
