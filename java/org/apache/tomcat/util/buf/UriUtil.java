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
 */
package org.apache.tomcat.util.buf;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Utility class for working with URIs and URLs.
 */
public final class UriUtil {

    private static final char[] HEX =
            { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    private static final Pattern PATTERN_EXCLAMATION_MARK = Pattern.compile("!/");
    private static final Pattern PATTERN_ASTERISK = Pattern.compile("\\*/");
    private static final Pattern PATTERN_CUSTOM;
    private static final String REPLACE_CUSTOM;

    private static final String WAR_SEPARATOR;

    static {
        String custom = System.getProperty("org.apache.tomcat.util.buf.UriUtil.WAR_SEPARATOR");
        if (custom == null) {
            WAR_SEPARATOR = "*/";
            PATTERN_CUSTOM = null;
            REPLACE_CUSTOM = null;
        } else {
            WAR_SEPARATOR = custom + "/";
            PATTERN_CUSTOM = Pattern.compile(Pattern.quote(WAR_SEPARATOR));
            StringBuilder sb = new StringBuilder(custom.length() * 3);
            // Deliberately use the platform's default encoding
            byte[] ba = custom.getBytes();
            for (byte toEncode : ba) {
                // Converting each byte in the buffer
                sb.append('%');
                int low = toEncode & 0x0f;
                int high = (toEncode & 0xf0) >> 4;
                sb.append(HEX[high]);
                sb.append(HEX[low]);
            }
            REPLACE_CUSTOM = sb.toString();
        }
    }


    private UriUtil() {
        // Utility class. Hide default constructor
    }


    /**
     * Determine if the character is allowed in the scheme of a URI. See RFC 2396, Section 3.1
     *
     * @param c The character to test
     *
     * @return {@code true} if a the character is allowed, otherwise {@code
     *         false}
     */
    private static boolean isSchemeChar(char c) {
        return Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.';
    }


    /**
     * Determine if a URI string has a <code>scheme</code> component.
     *
     * @param uri The URI to test
     *
     * @return {@code true} if a scheme is present, otherwise {code @false}
     */
    public static boolean hasScheme(CharSequence uri) {
        int len = uri.length();
        for (int i = 0; i < len; i++) {
            char c = uri.charAt(i);
            if (c == ':') {
                return i > 0;
            } else if (!isSchemeChar(c)) {
                return false;
            }
        }
        return false;
    }


    public static URL buildJarUrl(File jarFile) throws IOException {
        return buildJarUrl(jarFile, null);
    }


    public static URL buildJarUrl(File jarFile, String entryPath) throws IOException {
        return buildJarUrl(jarFile.toURI().toString(), entryPath);
    }


    public static URL buildJarUrl(String fileUrlString) throws IOException {
        return buildJarUrl(fileUrlString, null);
    }


    public static URL buildJarUrl(String fileUrlString, String entryPath) throws IOException {
        String safeString = makeSafeForJarUrl(fileUrlString);
        StringBuilder sb = new StringBuilder();
        sb.append(safeString);
        sb.append("!/");
        if (entryPath != null) {
            sb.append(makeSafeForJarUrl(entryPath));
        }
        URI uri;
        try {
            // Have to use the single argument constructor as that is the only one that doesn't escape input.
            uri = new URI("jar:" + sb.toString());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        return uri.toURL();
    }


    public static URL buildJarSafeUrl(File file) throws IOException {
        String safe = makeSafeForJarUrl(file.toURI().toString());
        URI uri;
        try {
            uri = new URI(safe);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        return uri.toURL();
    }


    /*
     * When testing on markt's desktop each iteration was taking ~1420ns when using String.replaceAll().
     *
     * Switching the implementation to use pre-compiled patterns and Pattern.matcher(input).replaceAll(replacement)
     * reduced this by ~10%.
     *
     * Note: Given the very small absolute time of a single iteration, even for a web application with 1000 JARs this is
     * only going to add ~3ms. It is therefore unlikely that further optimisation will be necessary.
     */
    /*
     * Pulled out into a separate method in case we need to handle other unusual sequences in the future.
     */
    private static String makeSafeForJarUrl(String input) {
        // Since "!/" has a special meaning in a JAR URL, make sure that the
        // sequence is properly escaped if present.
        String tmp = PATTERN_EXCLAMATION_MARK.matcher(input).replaceAll("%21/");
        // Tomcat's custom jar:war: URL handling treats */ as special
        tmp = PATTERN_ASTERISK.matcher(tmp).replaceAll("%2a/");
        if (PATTERN_CUSTOM != null) {
            tmp = PATTERN_CUSTOM.matcher(tmp).replaceAll(REPLACE_CUSTOM);
        }
        return tmp;
    }


    /**
     * Convert a URL of the form <code>war:file:...</code> to <code>jar:file:...</code>.
     *
     * @param warUrl The WAR URL to convert
     *
     * @return The equivalent JAR URL
     *
     * @throws IOException If the conversion fails
     */
    public static URL warToJar(URL warUrl) throws IOException {
        // Assumes that the spec is absolute and starts war:file:/...
        String file = warUrl.getFile();
        if (file.contains("*/")) {
            file = file.replaceFirst("\\*/", "!/");
        } else if (PATTERN_CUSTOM != null) {
            file = file.replaceFirst(PATTERN_CUSTOM.pattern(), "!/");
        }
        URI uri;
        try {
            uri = new URI("jar", file, null);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        return uri.toURL();
    }


    public static String getWarSeparator() {
        return WAR_SEPARATOR;
    }


    /**
     * Does the provided path start with <code>file:/</code> or <code>&lt;protocol&gt;://</code>.
     *
     * @param path The path to test
     *
     * @return {@code true} if the supplied path starts with once of the recognised sequences.
     */
    public static boolean isAbsoluteURI(String path) {
        // Special case as only a single /
        if (path.startsWith("file:/")) {
            return true;
        }

        // Start at the beginning of the path and skip over any valid protocol
        // characters
        int i = 0;
        while (i < path.length() && isSchemeChar(path.charAt(i))) {
            i++;
        }
        // Need at least one protocol character. False positives with Windows
        // drives such as C:/... will be caught by the later test for "://"
        if (i == 0) {
            return false;
        }
        // path starts with something that might be a protocol. Look for a
        // following "://"
        if (i + 2 < path.length() && path.charAt(i++) == ':' && path.charAt(i++) == '/' && path.charAt(i) == '/') {
            return true;
        }
        return false;
    }


    /**
     * Replicates the behaviour of {@link URI#resolve(String)} and adds support for URIs of the form
     * {@code jar:file:/... }.
     *
     * @param base   The base URI to resolve against
     * @param target The path to resolve
     *
     * @return The resulting URI as per {@link URI#resolve(String)}
     *
     * @throws MalformedURLException If the base URI cannot be converted to a URL
     * @throws URISyntaxException    If the resulting URL cannot be converted to a URI
     */
    public static URI resolve(URI base, String target) throws MalformedURLException, URISyntaxException {
        if (base.getScheme().equals("jar")) {
            /*
             * Previously used: new URL(base.toURL(), target).toURI() This delegated the work to the jar stream handler
             * which correctly resolved the target against the base.
             *
             * Deprecation of all the URL constructors mean a different approach is required.
             */
            URI fileUri = new URI(base.getSchemeSpecificPart());
            URI fileUriResolved = fileUri.resolve(target);

            return new URI("jar:" + fileUriResolved.toString());
        } else {
            return base.resolve(target);
        }
    }
}
