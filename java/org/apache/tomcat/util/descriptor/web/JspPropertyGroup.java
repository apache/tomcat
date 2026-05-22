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
package org.apache.tomcat.util.descriptor.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.tomcat.util.buf.UDecoder;

/**
 * Representation of a jsp-property-group element in web.xml.
 */
public class JspPropertyGroup extends XmlEncodingBase {

    /**
     * Creates a new JspPropertyGroup instance with default settings.
     */
    public JspPropertyGroup() {
        super();
    }

    private Boolean deferredSyntax = null;

    /**
     * Sets whether deferred syntax is allowed as literal.
     *
     * @param deferredSyntax the deferred syntax setting as a string boolean
     */
    public void setDeferredSyntax(String deferredSyntax) {
        this.deferredSyntax = Boolean.valueOf(deferredSyntax);
    }

    /**
     * Returns whether deferred syntax is allowed as literal.
     *
     * @return the deferred syntax setting, or null if not set
     */
    public Boolean getDeferredSyntax() {
        return deferredSyntax;
    }

    private Boolean errorOnELNotFound = null;

    /**
     * Sets whether to error on EL not found.
     *
     * @param errorOnELNotFound the setting as a string boolean
     */
    public void setErrorOnELNotFound(String errorOnELNotFound) {
        this.errorOnELNotFound = Boolean.valueOf(errorOnELNotFound);
    }

    /**
     * Returns whether to error on EL not found.
     *
     * @return the error on EL not found setting, or null if not set
     */
    public Boolean getErrorOnELNotFound() {
        return errorOnELNotFound;
    }

    private Boolean elIgnored = null;

    /**
     * Sets whether EL is ignored.
     *
     * @param elIgnored the EL ignored setting as a string boolean
     */
    public void setElIgnored(String elIgnored) {
        this.elIgnored = Boolean.valueOf(elIgnored);
    }

    /**
     * Returns whether EL is ignored.
     *
     * @return the EL ignored setting, or null if not set
     */
    public Boolean getElIgnored() {
        return elIgnored;
    }

    private final Collection<String> includeCodas = new ArrayList<>();

    /**
     * Adds an include coda file path.
     *
     * @param includeCoda the include coda file path
     */
    public void addIncludeCoda(String includeCoda) {
        includeCodas.add(includeCoda);
    }

    /**
     * Returns the collection of include coda file paths.
     *
     * @return the include codas collection
     */
    public Collection<String> getIncludeCodas() {
        return includeCodas;
    }

    private final Collection<String> includePreludes = new ArrayList<>();

    /**
     * Adds an include prelude file path.
     *
     * @param includePrelude the include prelude file path
     */
    public void addIncludePrelude(String includePrelude) {
        includePreludes.add(includePrelude);
    }

    /**
     * Returns the collection of include prelude file paths.
     *
     * @return the include preludes collection
     */
    public Collection<String> getIncludePreludes() {
        return includePreludes;
    }

    private Boolean isXml = null;

    /**
     * Sets whether the pages in this group are XML.
     *
     * @param isXml the XML setting as a string boolean
     */
    public void setIsXml(String isXml) {
        this.isXml = Boolean.valueOf(isXml);
    }

    /**
     * Returns whether the pages in this group are XML.
     *
     * @return the XML setting, or null if not set
     */
    public Boolean getIsXml() {
        return isXml;
    }

    private String pageEncoding = null;

    /**
     * Sets the page encoding for pages in this group.
     *
     * @param pageEncoding the page encoding
     */
    public void setPageEncoding(String pageEncoding) {
        this.pageEncoding = pageEncoding;
    }

    /**
     * Returns the page encoding for pages in this group.
     *
     * @return the page encoding, or null if not set
     */
    public String getPageEncoding() {
        return this.pageEncoding;
    }

    private Boolean scriptingInvalid = null;

    /**
     * Sets whether scripting is invalid for pages in this group.
     *
     * @param scriptingInvalid the scripting invalid setting as a string boolean
     */
    public void setScriptingInvalid(String scriptingInvalid) {
        this.scriptingInvalid = Boolean.valueOf(scriptingInvalid);
    }

    /**
     * Returns whether scripting is invalid for pages in this group.
     *
     * @return the scripting invalid setting, or null if not set
     */
    public Boolean getScriptingInvalid() {
        return scriptingInvalid;
    }

    private Boolean trimWhitespace = null;

    /**
     * Sets whether to trim directive whitespaces.
     *
     * @param trimWhitespace the trim whitespace setting as a string boolean
     */
    public void setTrimWhitespace(String trimWhitespace) {
        this.trimWhitespace = Boolean.valueOf(trimWhitespace);
    }

    /**
     * Returns whether to trim directive whitespaces.
     *
     * @return the trim whitespace setting, or null if not set
     */
    public Boolean getTrimWhitespace() {
        return trimWhitespace;
    }

    private final LinkedHashSet<String> urlPattern = new LinkedHashSet<>();

    /**
     * Adds a URL pattern (URL-decoded) to this property group.
     *
     * @param urlPattern the URL pattern to add
     */
    public void addUrlPattern(String urlPattern) {
        addUrlPatternDecoded(UDecoder.URLDecode(urlPattern, getCharset()));
    }

    /**
     * Adds a pre-decoded URL pattern to this property group.
     *
     * @param urlPattern the decoded URL pattern to add
     */
    public void addUrlPatternDecoded(String urlPattern) {
        this.urlPattern.add(urlPattern);
    }

    /**
     * Returns the set of URL patterns for this property group.
     *
     * @return the URL patterns
     */
    public Set<String> getUrlPatterns() {
        return this.urlPattern;
    }

    private String defaultContentType = null;

    /**
     * Sets the default content type for pages in this group.
     *
     * @param defaultContentType the default content type
     */
    public void setDefaultContentType(String defaultContentType) {
        this.defaultContentType = defaultContentType;
    }

    /**
     * Returns the default content type for pages in this group.
     *
     * @return the default content type, or null if not set
     */
    public String getDefaultContentType() {
        return this.defaultContentType;
    }

    private String buffer = null;

    /**
     * Sets the buffer size for pages in this group.
     *
     * @param buffer the buffer size setting
     */
    public void setBuffer(String buffer) {
        this.buffer = buffer;
    }

    /**
     * Returns the buffer size for pages in this group.
     *
     * @return the buffer size, or null if not set
     */
    public String getBuffer() {
        return this.buffer;
    }

    private Boolean errorOnUndeclaredNamespace = null;

    /**
     * Sets whether to error on undeclared namespace.
     *
     * @param errorOnUndeclaredNamespace the setting as a string boolean
     */
    public void setErrorOnUndeclaredNamespace(String errorOnUndeclaredNamespace) {
        this.errorOnUndeclaredNamespace = Boolean.valueOf(errorOnUndeclaredNamespace);
    }

    /**
     * Returns whether to error on undeclared namespace.
     *
     * @return the error on undeclared namespace setting, or null if not set
     */
    public Boolean getErrorOnUndeclaredNamespace() {
        return this.errorOnUndeclaredNamespace;
    }
}
