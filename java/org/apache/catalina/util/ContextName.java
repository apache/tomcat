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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to manage context names so there is one place where the
 * conversions between baseName, path and version take place.
 */
public final class ContextName implements Comparable {
    public static final String ROOT_NAME = "ROOT";
    private static final String VERSION_MARKER = "##";
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?:\\d+\\.)*\\d+");
    private static final Pattern VERSION_DOT_PATTERN = Pattern.compile("\\.");
    private static final Pattern VERSION_SUFFIX_PATTERN = Pattern.compile("((?:\\d+\\.)*\\d+)[.\\-](.*)");
    private static final String FWD_SLASH_REPLACEMENT = "#";

    private final String baseName;
    private final String path;
    private final String version;
    private final String name;
    private String versionCode;
    private String versionSuffix;


    /**
     * Creates an instance from a context name, display name, base name,
     * directory name, WAR name or context.xml name.
     *
     * @param name  The name to use as the basis for this object
     * @param stripFileExtension    If a .war or .xml file extension is present
     *                              at the end of the provided name should it be
     *                              removed?
     */
    public ContextName(String name, boolean stripFileExtension) {

        String tmp1 = name;

        // Convert Context names and display names to base names

        // Strip off any leading "/"
        if (tmp1.startsWith("/")) {
            tmp1 = tmp1.substring(1);
        }

        // Replace any remaining /
        tmp1 = tmp1.replaceAll("/", FWD_SLASH_REPLACEMENT);

        // Insert the ROOT name if required
        if (tmp1.startsWith(VERSION_MARKER) || "".equals(tmp1)) {
            tmp1 = ROOT_NAME + tmp1;
        }

        // Remove any file extensions
        if (stripFileExtension &&
                (tmp1.toLowerCase(Locale.ENGLISH).endsWith(".war") ||
                        tmp1.toLowerCase(Locale.ENGLISH).endsWith(".xml"))) {
            tmp1 = tmp1.substring(0, tmp1.length() -4);
        }

        baseName = tmp1;

        String tmp2;
        // Extract version number
        int versionIndex = baseName.indexOf(VERSION_MARKER);
        if (versionIndex > -1) {
            version = baseName.substring(versionIndex + 2);
            tmp2 = baseName.substring(0, versionIndex);
        } else {
            version = "";
            tmp2 = baseName;
        }

        buildVersionCode();

        if (ROOT_NAME.equals(tmp2)) {
            path = "";
        } else {
            path = "/" + tmp2.replaceAll(FWD_SLASH_REPLACEMENT, "/");
        }

        if (versionIndex > -1) {
            this.name = path + VERSION_MARKER + version;
        } else {
            this.name = path;
        }
    }

    /**
     * Construct an instance from a path and version.
     *
     * @param path      Context path to use
     * @param version   Context version to use
     */
    public ContextName(String path, String version) {
        // Path should never be null, '/' or '/ROOT'
        if (path == null || "/".equals(path) || "/ROOT".equals(path)) {
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

        buildVersionCode();

        // Name is path + version
        if ("".equals(this.version)) {
            name = this.path;
        } else {
            name = this.path + VERSION_MARKER + this.version;
        }

        // Base name is converted path + version
        StringBuilder tmp = new StringBuilder();
        if ("".equals(this.path)) {
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

    /**
     * Build a condensed string of common version numbers, e.g. 1.2.34
     *
     * This converts individual components of a version number into UTF-16
     * code points and concatenates them into a string. This string can be
     * used for comparison later on.
     */
    private void buildVersionCode() {
        if (VERSION_PATTERN.matcher(version).matches()) {
            StringBuilder versionCodeBuilder = new StringBuilder();
            for (String versionPart : VERSION_DOT_PATTERN.split(version)) {
                versionCodeBuilder.append(Character.toChars(Integer.valueOf(versionPart)));
            }
            versionCode = versionCodeBuilder.toString();
        } else {
            Matcher matcher = VERSION_SUFFIX_PATTERN.matcher(version);
            if (matcher.matches()) {
                String version = matcher.group(1);
                String suffix = matcher.group(2);
                StringBuilder versionCodeBuilder = new StringBuilder();
                for (String versionPart : VERSION_DOT_PATTERN.split(version)) {
                    versionCodeBuilder.append(Character.toChars(Integer.valueOf(versionPart)));
                }
                versionCode = versionCodeBuilder.toString();
                versionSuffix = suffix;
            } else {
                versionCode = null;
            }
        }
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

    public String getDisplayName() {
        StringBuilder tmp = new StringBuilder();
        if ("".equals(path)) {
            tmp.append('/');
        } else {
            tmp.append(path);
        }

        if (!"".equals(version)) {
            tmp.append(VERSION_MARKER);
            tmp.append(version);
        }

        return tmp.toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    /**
     * Compares this context name with another object
     *
     * <p>If the argument is not a {@code ContextName} the result is
     * {@literal 1}.</p>
     *
     * <p>Otherwise different aspects of the context name are compared</p>
     *
     * <p>If the context path is different the result is equal to the
     * comparison of the paths.</p>
     * <p>If the version is equal to the other context name the result is
     * {@literal 0}.</p>
     * <p>If both versions are a common version identifier like
     * {@literal “x.y.z.…”} individual parts of the version number are
     * compared and the first difference determines the result.</p>
     * <p>Otherwise the context names are compared literally.</p>
     */
    @Override
    public int compareTo(Object o) {
        if (!(o instanceof ContextName)) {
            return 1;
        }

        ContextName other = (ContextName) o;

        int pathResult = path.compareTo(other.path);
        if (pathResult != 0) {
            return pathResult;
        }

        if (versionCode != null && other.versionCode != null) {
            int versionResult = versionCode.compareTo(other.versionCode);

            if (versionResult == 0) {
                if (versionSuffix == null && other.versionSuffix != null) {
                    return 1;
                } else if (versionSuffix != null && other.versionSuffix == null) {
                    return -1;
                }
            }

            return versionResult;
        }

        return version.compareTo(other.version);
    }


    /**
     * Extract the final component of the given path which is assumed to be a
     * base name and generate a {@link ContextName} from that base name.
     *
     * @param path The path that ends in a base name
     *
     * @return the {@link ContextName} generated from the given base name
     */
    public static ContextName extractFromPath(String path) {
        // Convert '\' to '/'
        path = path.replaceAll("\\\\", "/");
        // Remove trailing '/'. Use while just in case a value ends in ///
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        int lastSegment = path.lastIndexOf('/');
        if (lastSegment > 0) {
            path = path.substring(lastSegment + 1);
        }

        return new ContextName(path, true);
    }
}
