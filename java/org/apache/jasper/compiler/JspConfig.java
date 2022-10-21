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
package org.apache.jasper.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.ServletContext;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.descriptor.JspPropertyGroupDescriptor;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Handles the jsp-config element in WEB_INF/web.xml.  This is used
 * for specifying the JSP configuration information on a JSP page
 *
 * @author Kin-man Chung
 * @author Remy Maucherat
 */

public class JspConfig {

    // Logger
    private final Log log = LogFactory.getLog(JspConfig.class); // must not be static

    private List<JspPropertyGroup> jspProperties = null;
    private final ServletContext ctxt;
    private volatile boolean initialized = false;

    private static final String defaultIsXml = null;    // unspecified
    private String defaultIsELIgnored = null;           // unspecified
    private String defaultErrorOnELNotFound = "false";
    private static final String defaultIsScriptingInvalid = null;
    private String defaultDeferedSyntaxAllowedAsLiteral = null;
    private static final String defaultTrimDirectiveWhitespaces = null;
    private static final String defaultDefaultContentType = null;
    private static final String defaultBuffer = null;
    private static final String defaultErrorOnUndeclaredNamespace = "false";
    private JspProperty defaultJspProperty;

    public JspConfig(ServletContext ctxt) {
        this.ctxt = ctxt;
    }

    private void processWebDotXml() {

        // Very, very unlikely but just in case...
        if (ctxt.getEffectiveMajorVersion() < 2) {
            defaultIsELIgnored = "true";
            defaultDeferedSyntaxAllowedAsLiteral = "true";
            return;
        }
        if (ctxt.getEffectiveMajorVersion() == 2) {
            if (ctxt.getEffectiveMinorVersion() < 5) {
                defaultDeferedSyntaxAllowedAsLiteral = "true";
            }
            if (ctxt.getEffectiveMinorVersion() < 4) {
                defaultIsELIgnored = "true";
                return;
            }
        }

        JspConfigDescriptor jspConfig = ctxt.getJspConfigDescriptor();

        if (jspConfig == null) {
            return;
        }

        jspProperties = new ArrayList<>();
        Collection<JspPropertyGroupDescriptor> jspPropertyGroups =
                jspConfig.getJspPropertyGroups();

        for (JspPropertyGroupDescriptor jspPropertyGroup : jspPropertyGroups) {

            Collection<String> urlPatterns = jspPropertyGroup.getUrlPatterns();

            if (urlPatterns.size() == 0) {
                continue;
            }

            JspProperty property = new JspProperty(jspPropertyGroup.getIsXml(),
                    jspPropertyGroup.getElIgnored(),
                    jspPropertyGroup.getErrorOnELNotFound(),
                    jspPropertyGroup.getScriptingInvalid(),
                    jspPropertyGroup.getPageEncoding(),
                    jspPropertyGroup.getIncludePreludes(),
                    jspPropertyGroup.getIncludeCodas(),
                    jspPropertyGroup.getDeferredSyntaxAllowedAsLiteral(),
                    jspPropertyGroup.getTrimDirectiveWhitespaces(),
                    jspPropertyGroup.getDefaultContentType(),
                    jspPropertyGroup.getBuffer(),
                    jspPropertyGroup.getErrorOnUndeclaredNamespace());

            // Add one JspPropertyGroup for each URL Pattern.  This makes
            // the matching logic easier.
            for (String urlPattern : urlPatterns) {
                String path = null;
                String extension = null;

                if (urlPattern.indexOf('*') < 0) {
                    // Exact match
                    path = urlPattern;
                } else {
                    int i = urlPattern.lastIndexOf('/');
                    String file;
                    if (i >= 0) {
                        path = urlPattern.substring(0,i+1);
                        file = urlPattern.substring(i+1);
                    } else {
                        file = urlPattern;
                    }

                    // pattern must be "*", or of the form "*.jsp"
                    if (file.equals("*")) {
                        extension = "*";
                    } else if (file.startsWith("*.")) {
                        extension = file.substring(file.indexOf('.')+1);
                    }

                    // The url patterns are reconstructed as the following:
                    // path != null, extension == null:  / or /foo/bar.ext
                    // path == null, extension != null:  *.ext
                    // path != null, extension == "*":   /foo/*
                    boolean isStar = "*".equals(extension);
                    if ((path == null && (extension == null || isStar))
                            || (path != null && !isStar)) {
                        if (log.isWarnEnabled()) {
                            log.warn(Localizer.getMessage(
                                    "jsp.warning.bad.urlpattern.propertygroup",
                                    urlPattern));
                        }
                        continue;
                    }
                }

                JspPropertyGroup propertyGroup =
                    new JspPropertyGroup(path, extension, property);

                jspProperties.add(propertyGroup);
            }
        }
    }

    private void init() {

        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    processWebDotXml();
                    defaultJspProperty = new JspProperty(defaultIsXml,
                            defaultIsELIgnored,
                            defaultErrorOnELNotFound,
                            defaultIsScriptingInvalid,
                            null, null, null,
                            defaultDeferedSyntaxAllowedAsLiteral,
                            defaultTrimDirectiveWhitespaces,
                            defaultDefaultContentType,
                            defaultBuffer,
                            defaultErrorOnUndeclaredNamespace);
                    initialized = true;
                }
            }
        }
    }

    /**
     * Select the property group that has more restrictive url-pattern.
     * In case of tie, select the first.
     */
    @SuppressWarnings("null") // NPE not possible
    private JspPropertyGroup selectProperty(JspPropertyGroup prev,
            JspPropertyGroup curr) {
        if (prev == null) {
            return curr;
        }
        if (prev.getExtension() == null) {
            // exact match
            return prev;
        }
        if (curr.getExtension() == null) {
            // exact match
            return curr;
        }
        String prevPath = prev.getPath();
        String currPath = curr.getPath();
        if (prevPath == null && currPath == null) {
            // Both specifies a *.ext, keep the first one
            return prev;
        }
        if (prevPath == null && currPath != null) {
            return curr;
        }
        if (prevPath != null && currPath == null) {
            return prev;
        }
        if (prevPath.length() >= currPath.length()) {
            return prev;
        }
        return curr;
    }


    /**
     * Find a property that best matches the supplied resource.
     * @param uri the resource supplied.
     * @return a JspProperty indicating the best match, or some default.
     */
    public JspProperty findJspProperty(String uri) {

        init();

        // JSP Configuration settings do not apply to tag files
        if (jspProperties == null || uri.endsWith(".tag")
                || uri.endsWith(".tagx")) {
            return defaultJspProperty;
        }

        String uriPath = null;
        int index = uri.lastIndexOf('/');
        if (index >=0 ) {
            uriPath = uri.substring(0, index+1);
        }
        String uriExtension = null;
        index = uri.lastIndexOf('.');
        if (index >=0) {
            uriExtension = uri.substring(index+1);
        }

        Collection<String> includePreludes = new ArrayList<>();
        Collection<String> includeCodas = new ArrayList<>();

        JspPropertyGroup isXmlMatch = null;
        JspPropertyGroup elIgnoredMatch = null;
        JspPropertyGroup errorOnELNotFoundMatch = null;
        JspPropertyGroup scriptingInvalidMatch = null;
        JspPropertyGroup pageEncodingMatch = null;
        JspPropertyGroup deferedSyntaxAllowedAsLiteralMatch = null;
        JspPropertyGroup trimDirectiveWhitespacesMatch = null;
        JspPropertyGroup defaultContentTypeMatch = null;
        JspPropertyGroup bufferMatch = null;
        JspPropertyGroup errorOnUndeclaredNamespaceMatch = null;

        for (JspPropertyGroup jpg : jspProperties) {
            JspProperty jp = jpg.getJspProperty();

            // (arrays will be the same length)
            String extension = jpg.getExtension();
            String path = jpg.getPath();

            if (extension == null) {
                // exact match pattern: /a/foo.jsp
                if (!uri.equals(path)) {
                    // not matched;
                    continue;
                }
            } else {
                // Matching patterns *.ext or /p/*
                if (path != null && uriPath != null &&
                        ! uriPath.startsWith(path)) {
                    // not matched
                    continue;
                }
                if (!extension.equals("*") &&
                        !extension.equals(uriExtension)) {
                    // not matched
                    continue;
                }
            }
            // We have a match
            // Add include-preludes and include-codas
            if (jp.getIncludePrelude() != null) {
                includePreludes.addAll(jp.getIncludePrelude());
            }
            if (jp.getIncludeCoda() != null) {
                includeCodas.addAll(jp.getIncludeCoda());
            }

            // If there is a previous match for the same property, remember
            // the one that is more restrictive.
            if (jp.isXml() != null) {
                isXmlMatch = selectProperty(isXmlMatch, jpg);
            }
            if (jp.isELIgnored() != null) {
                elIgnoredMatch = selectProperty(elIgnoredMatch, jpg);
            }
            if (jp.getErrorOnELNotFound() != null) {
                errorOnELNotFoundMatch = selectProperty(errorOnELNotFoundMatch, jpg);
            }
            if (jp.isScriptingInvalid() != null) {
                scriptingInvalidMatch =
                    selectProperty(scriptingInvalidMatch, jpg);
            }
            if (jp.getPageEncoding() != null) {
                pageEncodingMatch = selectProperty(pageEncodingMatch, jpg);
            }
            if (jp.isDeferedSyntaxAllowedAsLiteral() != null) {
                deferedSyntaxAllowedAsLiteralMatch =
                    selectProperty(deferedSyntaxAllowedAsLiteralMatch, jpg);
            }
            if (jp.isTrimDirectiveWhitespaces() != null) {
                trimDirectiveWhitespacesMatch =
                    selectProperty(trimDirectiveWhitespacesMatch, jpg);
            }
            if (jp.getDefaultContentType() != null) {
                defaultContentTypeMatch =
                    selectProperty(defaultContentTypeMatch, jpg);
            }
            if (jp.getBuffer() != null) {
                bufferMatch = selectProperty(bufferMatch, jpg);
            }
            if (jp.isErrorOnUndeclaredNamespace() != null) {
                errorOnUndeclaredNamespaceMatch =
                    selectProperty(errorOnUndeclaredNamespaceMatch, jpg);
            }
        }


        String isXml = defaultIsXml;
        String isELIgnored = defaultIsELIgnored;
        String errorOnELNotFound = defaultErrorOnELNotFound;
        String isScriptingInvalid = defaultIsScriptingInvalid;
        String pageEncoding = null;
        String isDeferedSyntaxAllowedAsLiteral = defaultDeferedSyntaxAllowedAsLiteral;
        String isTrimDirectiveWhitespaces = defaultTrimDirectiveWhitespaces;
        String defaultContentType = defaultDefaultContentType;
        String buffer = defaultBuffer;
        String errorOnUndeclaredNamespace = defaultErrorOnUndeclaredNamespace;

        if (isXmlMatch != null) {
            isXml = isXmlMatch.getJspProperty().isXml();
        }
        if (errorOnELNotFoundMatch != null) {
            errorOnELNotFound = errorOnELNotFoundMatch.getJspProperty().getErrorOnELNotFound();
        }
        if (elIgnoredMatch != null) {
            isELIgnored = elIgnoredMatch.getJspProperty().isELIgnored();
        }
        if (scriptingInvalidMatch != null) {
            isScriptingInvalid =
                scriptingInvalidMatch.getJspProperty().isScriptingInvalid();
        }
        if (pageEncodingMatch != null) {
            pageEncoding = pageEncodingMatch.getJspProperty().getPageEncoding();
        }
        if (deferedSyntaxAllowedAsLiteralMatch != null) {
            isDeferedSyntaxAllowedAsLiteral =
                deferedSyntaxAllowedAsLiteralMatch.getJspProperty().isDeferedSyntaxAllowedAsLiteral();
        }
        if (trimDirectiveWhitespacesMatch != null) {
            isTrimDirectiveWhitespaces =
                trimDirectiveWhitespacesMatch.getJspProperty().isTrimDirectiveWhitespaces();
        }
        if (defaultContentTypeMatch != null) {
            defaultContentType =
                defaultContentTypeMatch.getJspProperty().getDefaultContentType();
        }
        if (bufferMatch != null) {
            buffer = bufferMatch.getJspProperty().getBuffer();
        }
        if (errorOnUndeclaredNamespaceMatch != null) {
            errorOnUndeclaredNamespace =
                errorOnUndeclaredNamespaceMatch.getJspProperty().isErrorOnUndeclaredNamespace();
        }

        return new JspProperty(isXml, isELIgnored, errorOnELNotFound, isScriptingInvalid,
                pageEncoding, includePreludes, includeCodas,
                isDeferedSyntaxAllowedAsLiteral, isTrimDirectiveWhitespaces,
                defaultContentType, buffer, errorOnUndeclaredNamespace);
    }

    /**
     * To find out if a uri matches a url pattern in jsp config.  If so,
     * then the uri is a JSP page.  This is used primarily for jspc.
     * @param uri The path to check
     * @return <code>true</code> if the path denotes a JSP page
     */
    public boolean isJspPage(String uri) {

        init();
        if (jspProperties == null) {
            return false;
        }

        String uriPath = null;
        int index = uri.lastIndexOf('/');
        if (index >=0 ) {
            uriPath = uri.substring(0, index+1);
        }
        String uriExtension = null;
        index = uri.lastIndexOf('.');
        if (index >=0) {
            uriExtension = uri.substring(index+1);
        }

        for (JspPropertyGroup jpg : jspProperties) {

            String extension = jpg.getExtension();
            String path = jpg.getPath();

            if (extension == null) {
                if (uri.equals(path)) {
                    // There is an exact match
                    return true;
                }
            } else {
                if ((path == null || path.equals(uriPath)) &&
                        (extension.equals("*") || extension.equals(uriExtension))) {
                    // Matches *, *.ext, /p/*, or /p/*.ext
                    return true;
                }
            }
        }
        return false;
    }

    public static class JspPropertyGroup {
        private final String path;
        private final String extension;
        private final JspProperty jspProperty;

        JspPropertyGroup(String path, String extension,
                JspProperty jspProperty) {
            this.path = path;
            this.extension = extension;
            this.jspProperty = jspProperty;
        }

        public String getPath() {
            return path;
        }

        public String getExtension() {
            return extension;
        }

        public JspProperty getJspProperty() {
            return jspProperty;
        }
    }

    public static class JspProperty {

        private final String isXml;
        private final String elIgnored;
        private final String errorOnELNotFound;
        private final String scriptingInvalid;
        private final String pageEncoding;
        private final Collection<String> includePrelude;
        private final Collection<String> includeCoda;
        private final String deferedSyntaxAllowedAsLiteral;
        private final String trimDirectiveWhitespaces;
        private final String defaultContentType;
        private final String buffer;
        private final String errorOnUndeclaredNamespace;

        public JspProperty(String isXml, String elIgnored, String errorOnELNotFound,
                String scriptingInvalid, String pageEncoding,
                Collection<String> includePrelude, Collection<String> includeCoda,
                String deferedSyntaxAllowedAsLiteral,
                String trimDirectiveWhitespaces,
                String defaultContentType,
                String buffer,
                String errorOnUndeclaredNamespace) {

            this.isXml = isXml;
            this.elIgnored = elIgnored;
            this.errorOnELNotFound = errorOnELNotFound;
            this.scriptingInvalid = scriptingInvalid;
            this.pageEncoding = pageEncoding;
            this.includePrelude = includePrelude;
            this.includeCoda = includeCoda;
            this.deferedSyntaxAllowedAsLiteral = deferedSyntaxAllowedAsLiteral;
            this.trimDirectiveWhitespaces = trimDirectiveWhitespaces;
            this.defaultContentType = defaultContentType;
            this.buffer = buffer;
            this.errorOnUndeclaredNamespace = errorOnUndeclaredNamespace;
        }

        public String isXml() {
            return isXml;
        }

        public String isELIgnored() {
            return elIgnored;
        }

        public String getErrorOnELNotFound() {
            return errorOnELNotFound;
        }

        public String isScriptingInvalid() {
            return scriptingInvalid;
        }

        public String getPageEncoding() {
            return pageEncoding;
        }

        public Collection<String> getIncludePrelude() {
            return includePrelude;
        }

        public Collection<String> getIncludeCoda() {
            return includeCoda;
        }

        public String isDeferedSyntaxAllowedAsLiteral() {
            return deferedSyntaxAllowedAsLiteral;
        }

        public String isTrimDirectiveWhitespaces() {
            return trimDirectiveWhitespaces;
        }

        public String getDefaultContentType() {
            return defaultContentType;
        }

        public String getBuffer() {
            return buffer;
        }

        public String isErrorOnUndeclaredNamespace() {
            return errorOnUndeclaredNamespace;
        }
    }
}
