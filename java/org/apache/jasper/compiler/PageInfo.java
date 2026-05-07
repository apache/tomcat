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
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.el.ExpressionFactory;
import jakarta.servlet.jsp.tagext.TagLibraryInfo;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;

/**
 * A repository for various info about the translation unit under compilation.
 */
public class PageInfo {

    private final List<String> imports;
    private final Map<String,Long> dependants;

    private final BeanRepository beanRepository;
    private final Set<String> varInfoNames;
    private final HashMap<String,TagLibraryInfo> taglibsMap;
    private final HashMap<String,String> jspPrefixMapper;
    private final HashMap<String,Deque<String>> xmlPrefixMapper;
    private final HashMap<String,Mark> nonCustomTagPrefixMap;
    private final String jspFile;
    private static final String defaultLanguage = "java";
    private String language;
    private final String defaultExtends;
    private String xtends;
    private String contentType = null;
    private String session;
    private boolean isSession = true;
    private String bufferValue;
    private int buffer = 8 * 1024;
    private String autoFlush;
    private boolean isAutoFlush = true;
    private String isThreadSafeValue;
    private boolean isThreadSafe = true;
    private String isErrorPageValue;
    private boolean isErrorPage = false;
    private String errorPage = null;
    private String info;

    private boolean scriptless = false;
    private boolean scriptingInvalid = false;

    private String isELIgnoredValue;
    private boolean isELIgnored = false;

    // JSP 2.1
    private String deferredSyntaxAllowedAsLiteralValue;
    private boolean deferredSyntaxAllowedAsLiteral = false;
    private final ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
    private String trimDirectiveWhitespacesValue;
    private boolean trimDirectiveWhitespaces = false;

    private String omitXmlDecl = null;
    private String doctypeName = null;
    private String doctypePublic = null;
    private String doctypeSystem = null;

    private boolean isJspPrefixHijacked;

    // Set of all element and attribute prefixes used in this translation unit
    private final HashSet<String> prefixes;

    private boolean hasJspRoot = false;
    private Collection<String> includePrelude;
    private Collection<String> includeCoda;
    private final List<String> pluginDcls; // Id's for tagplugin declarations

    // JSP 2.2
    private boolean errorOnUndeclaredNamespace = false;

    // JSP 3.1
    private String errorOnELNotFoundValue;
    private boolean errorOnELNotFound = false;

    private final boolean isTagFile;

    PageInfo(BeanRepository beanRepository, JspCompilationContext ctxt) {
        isTagFile = ctxt.isTagFile();
        jspFile = ctxt.getJspFile();
        defaultExtends = ctxt.getOptions().getJspServletBase();
        this.beanRepository = beanRepository;
        this.varInfoNames = new HashSet<>();
        this.taglibsMap = new HashMap<>();
        this.jspPrefixMapper = new HashMap<>();
        this.xmlPrefixMapper = new HashMap<>();
        this.nonCustomTagPrefixMap = new HashMap<>();
        this.dependants = new LinkedHashMap<>();
        this.includePrelude = new ArrayList<>();
        this.includeCoda = new ArrayList<>();
        this.pluginDcls = new ArrayList<>();
        this.prefixes = new HashSet<>();

        // Enter standard imports
        this.imports = new ArrayList<>(Constants.STANDARD_IMPORTS);
    }

    /**
     * Checks if this is a tag file.
     *
     * @return true if this is a tag file
     */
    public boolean isTagFile() {
        return isTagFile;
    }

    /**
     * Check if the plugin ID has been previously declared. Make a note that this Id is now declared.
     *
     * @param id The plugin ID to check
     *
     * @return true if Id has been declared.
     */
    public boolean isPluginDeclared(String id) {
        if (pluginDcls.contains(id)) {
            return true;
        }
        pluginDcls.add(id);
        return false;
    }

    /**
     * Adds multiple imports to this page.
     *
     * @param imports The imports to add
     */
    public void addImports(List<String> imports) {
        this.imports.addAll(imports);
    }

    /**
     * Adds a single import to this page.
     *
     * @param imp The import to add
     */
    public void addImport(String imp) {
        this.imports.add(imp);
    }

    /**
     * Returns the list of imports for this page.
     *
     * @return The imports list
     */
    public List<String> getImports() {
        return imports;
    }

    /**
     * Returns the JSP file path.
     *
     * @return The JSP file path
     */
    public String getJspFile() {
        return jspFile;
    }

    /**
     * Adds a dependent file to this page.
     *
     * @param d The dependent file path
     * @param lastModified The last modified timestamp
     */
    public void addDependant(String d, Long lastModified) {
        if (!dependants.containsKey(d) && !jspFile.equals(d)) {
            dependants.put(d, lastModified);
        }
    }

    /**
     * Returns the map of dependent files.
     *
     * @return The dependants map
     */
    public Map<String,Long> getDependants() {
        return dependants;
    }

    /**
     * Returns the bean repository for this page.
     *
     * @return The bean repository
     */
    public BeanRepository getBeanRepository() {
        return beanRepository;
    }

    /**
     * Sets whether this page is scriptless.
     *
     * @param s true if the page is scriptless
     */
    public void setScriptless(boolean s) {
        scriptless = s;
    }

    /**
     * Checks if this page is scriptless.
     *
     * @return true if the page is scriptless
     */
    public boolean isScriptless() {
        return scriptless;
    }

    /**
     * Sets whether scripting is invalid for this page.
     *
     * @param s true if scripting is invalid
     */
    public void setScriptingInvalid(boolean s) {
        scriptingInvalid = s;
    }

    /**
     * Checks if scripting is invalid for this page.
     *
     * @return true if scripting is invalid
     */
    public boolean isScriptingInvalid() {
        return scriptingInvalid;
    }

    /**
     * Returns the collection of include prelude files.
     *
     * @return The include prelude files
     */
    public Collection<String> getIncludePrelude() {
        return includePrelude;
    }

    /**
     * Sets the collection of include prelude files.
     *
     * @param prelude The include prelude files
     */
    public void setIncludePrelude(Collection<String> prelude) {
        includePrelude = prelude;
    }

    /**
     * Returns the collection of include coda files.
     *
     * @return The include coda files
     */
    public Collection<String> getIncludeCoda() {
        return includeCoda;
    }

    /**
     * Sets the collection of include coda files.
     *
     * @param coda The include coda files
     */
    public void setIncludeCoda(Collection<String> coda) {
        includeCoda = coda;
    }

    /**
     * Sets whether this page has a JSP root.
     *
     * @param s true if the page has a JSP root
     */
    public void setHasJspRoot(boolean s) {
        hasJspRoot = s;
    }

    /**
     * Checks if this page has a JSP root.
     *
     * @return true if the page has a JSP root
     */
    public boolean hasJspRoot() {
        return hasJspRoot;
    }

    /**
     * Returns the omit XML declaration setting.
     *
     * @return The omit XML declaration value
     */
    public String getOmitXmlDecl() {
        return omitXmlDecl;
    }

    /**
     * Sets the omit XML declaration setting.
     *
     * @param omit The omit XML declaration value
     */
    public void setOmitXmlDecl(String omit) {
        omitXmlDecl = omit;
    }

    /**
     * Returns the DOCTYPE name.
     *
     * @return The DOCTYPE name
     */
    public String getDoctypeName() {
        return doctypeName;
    }

    /**
     * Sets the DOCTYPE name.
     *
     * @param doctypeName The DOCTYPE name
     */
    public void setDoctypeName(String doctypeName) {
        this.doctypeName = doctypeName;
    }

    /**
     * Returns the DOCTYPE system identifier.
     *
     * @return The DOCTYPE system identifier
     */
    public String getDoctypeSystem() {
        return doctypeSystem;
    }

    /**
     * Sets the DOCTYPE system identifier.
     *
     * @param doctypeSystem The DOCTYPE system identifier
     */
    public void setDoctypeSystem(String doctypeSystem) {
        this.doctypeSystem = doctypeSystem;
    }

    /**
     * Returns the DOCTYPE public identifier.
     *
     * @return The DOCTYPE public identifier
     */
    public String getDoctypePublic() {
        return doctypePublic;
    }

    /**
     * Sets the DOCTYPE public identifier.
     *
     * @param doctypePublic The DOCTYPE public identifier
     */
    public void setDoctypePublic(String doctypePublic) {
        this.doctypePublic = doctypePublic;
    }

    /* Tag library and XML namespace management methods */

    /**
     * Sets whether the JSP prefix has been hijacked.
     *
     * @param isHijacked true if the JSP prefix has been hijacked
     */
    public void setIsJspPrefixHijacked(boolean isHijacked) {
        isJspPrefixHijacked = isHijacked;
    }

    /**
     * Checks if the JSP prefix has been hijacked.
     *
     * @return true if the JSP prefix has been hijacked
     */
    public boolean isJspPrefixHijacked() {
        return isJspPrefixHijacked;
    }

    /**
     * Adds the given prefix to the set of prefixes of this translation unit.
     *
     * @param prefix The prefix to add
     */
    public void addPrefix(String prefix) {
        prefixes.add(prefix);
    }

    /**
     * Checks to see if this translation unit contains the given prefix.
     *
     * @param prefix The prefix to check
     * @return true if this translation unit contains the given prefix
     */
    public boolean containsPrefix(String prefix) {
        return prefixes.contains(prefix);
    }

    /**
     * Maps the given URI to the given tag library.
     *
     * @param uri The URI to map
     * @param info The tag library to be associated with the given URI
     */
    public void addTaglib(String uri, TagLibraryInfo info) {
        taglibsMap.put(uri, info);
    }

    /**
     * Gets the tag library corresponding to the given URI.
     *
     * @param uri The URI to look up
     * @return Tag library corresponding to the given URI
     */
    public TagLibraryInfo getTaglib(String uri) {
        return taglibsMap.get(uri);
    }

    /**
     * Gets the collection of tag libraries that are associated with a URI.
     *
     * @return Collection of tag libraries
     */
    public Collection<TagLibraryInfo> getTaglibs() {
        return taglibsMap.values();
    }

    /**
     * Checks to see if the given URI is mapped to a tag library.
     *
     * @param uri The URI to check
     * @return true if the given URI is mapped to a tag library
     */
    public boolean hasTaglib(String uri) {
        return taglibsMap.containsKey(uri);
    }

    /**
     * Maps the given prefix to the given URI.
     *
     * @param prefix The prefix to map
     * @param uri The URI to be associated with the given prefix
     */
    public void addPrefixMapping(String prefix, String uri) {
        jspPrefixMapper.put(prefix, uri);
    }

    /**
     * Pushes the given URI onto the stack of URIs to which the given prefix is mapped.
     *
     * @param prefix The prefix whose stack of URIs is to be pushed
     * @param uri The URI to be pushed onto the stack
     */
    public void pushPrefixMapping(String prefix, String uri) {
        // Must be LinkedList as it needs to accept nulls
        xmlPrefixMapper.computeIfAbsent(prefix, k -> new LinkedList<>()).addFirst(uri);
    }

    /**
     * Removes the URI at the top of the stack of URIs to which the given prefix is mapped.
     *
     * @param prefix The prefix whose stack of URIs is to be popped
     */
    public void popPrefixMapping(String prefix) {
        Deque<String> stack = xmlPrefixMapper.get(prefix);
        stack.removeFirst();
    }

    /**
     * Returns the URI to which the given prefix maps.
     *
     * @param prefix The prefix whose URI is sought
     * @return The URI to which the given prefix maps
     */
    public String getURI(String prefix) {

        String uri;

        Deque<String> stack = xmlPrefixMapper.get(prefix);
        if (stack == null || stack.isEmpty()) {
            uri = jspPrefixMapper.get(prefix);
        } else {
            uri = stack.getFirst();
        }

        return uri;
    }


    /* Page/Tag directive attributes */

    /**
     * Sets the language attribute.
     *
     * @param value The language value
     * @param n The node
     * @param err The error dispatcher
     * @param pagedir Whether this is a page directive
     * @throws JasperException if the language is not Java
     */
    public void setLanguage(String value, Node n, ErrorDispatcher err, boolean pagedir) throws JasperException {

        if (!"java".equalsIgnoreCase(value)) {
            if (pagedir) {
                err.jspError(n, "jsp.error.page.language.nonjava");
            } else {
                err.jspError(n, "jsp.error.tag.language.nonjava");
            }
        }

        language = value;
    }

    /**
     * Returns the language attribute value.
     *
     * @param useDefault Whether to use the default if not set
     * @return The language value
     */
    public String getLanguage(boolean useDefault) {
        return (language == null && useDefault ? defaultLanguage : language);
    }

    /*
     * extends
     */
    /**
     * Sets the extends attribute value.
     *
     * @param value The extends value
     */
    public void setExtends(String value) {
        xtends = value;
    }

    /**
     * Gets the value of the 'extends' page directive attribute.
     *
     * @param useDefault TRUE if the default (org.apache.jasper.runtime.HttpJspBase) should be returned if this
     *                       attribute has not been set, FALSE otherwise
     *
     * @return The value of the 'extends' page directive attribute, or the default
     *             (org.apache.jasper.runtime.HttpJspBase) if this attribute has not been set and useDefault is TRUE
     */
    public String getExtends(boolean useDefault) {
        return (xtends == null && useDefault ? defaultExtends : xtends);
    }

    /**
     * Gets the value of the 'extends' page directive attribute.
     *
     * @return The value of the 'extends' page directive attribute, or the default
     *             (org.apache.jasper.runtime.HttpJspBase) if this attribute has not been set
     */
    public String getExtends() {
        return getExtends(true);
    }


    /*
     * contentType
     */
    /**
     * Sets the content type.
     *
     * @param value The content type value
     */
    public void setContentType(String value) {
        contentType = value;
    }

    /**
     * Returns the content type.
     *
     * @return The content type
     */
    public String getContentType() {
        return contentType;
    }


    /**
     * Sets the buffer value.
     *
     * @param value The buffer value
     * @param n The node
     * @param err The error dispatcher
     * @throws JasperException if the buffer value is invalid
     */
    public void setBufferValue(String value, Node n, ErrorDispatcher err) throws JasperException {

        if ("none".equalsIgnoreCase(value)) {
            buffer = 0;
        } else {
            if (value == null || !value.endsWith("kb")) {
                if (n == null) {
                    err.jspError("jsp.error.page.invalid.buffer");
                } else {
                    err.jspError(n, "jsp.error.page.invalid.buffer");
                }
            }
            try {
                @SuppressWarnings("null") // value can't be null here
                int k = Integer.parseInt(value.substring(0, value.length() - 2));
                buffer = k * 1024;
            } catch (NumberFormatException e) {
                if (n == null) {
                    err.jspError("jsp.error.page.invalid.buffer");
                } else {
                    err.jspError(n, "jsp.error.page.invalid.buffer");
                }
            }
        }

        bufferValue = value;
    }

    /**
     * Returns the buffer value.
     *
     * @return The buffer value
     */
    public String getBufferValue() {
        return bufferValue;
    }

    /**
     * Returns the buffer size in bytes.
     *
     * @return The buffer size
     */
    public int getBuffer() {
        return buffer;
    }


    /**
     * Sets the session attribute.
     *
     * @param value The session value
     * @param n The node
     * @param err The error dispatcher
     * @throws JasperException if the session value is invalid
     */
    public void setSession(String value, Node n, ErrorDispatcher err) throws JasperException {

        if ("true".equalsIgnoreCase(value)) {
            isSession = true;
        } else if ("false".equalsIgnoreCase(value)) {
            isSession = false;
        } else {
            err.jspError(n, "jsp.error.page.invalid.session");
        }

        session = value;
    }

    /**
     * Returns the session attribute value.
     *
     * @return The session value
     */
    public String getSession() {
        return session;
    }

    /**
     * Checks if session is enabled.
     *
     * @return true if session is enabled
     */
    public boolean isSession() {
        return isSession;
    }


    /**
     * Sets the autoFlush attribute.
     *
     * @param value The autoFlush value
     * @param n The node
     * @param err The error dispatcher
     * @throws JasperException if the autoFlush value is invalid
     */
    public void setAutoFlush(String value, Node n, ErrorDispatcher err) throws JasperException {

        if ("true".equalsIgnoreCase(value)) {
            isAutoFlush = true;
        } else if ("false".equalsIgnoreCase(value)) {
            isAutoFlush = false;
        } else {
            err.jspError(n, "jsp.error.autoFlush.invalid");
        }

        autoFlush = value;
    }

    /**
     * Returns the autoFlush attribute value.
     *
     * @return The autoFlush value
     */
    public String getAutoFlush() {
        return autoFlush;
    }

    /**
     * Checks if autoFlush is enabled.
     *
     * @return true if autoFlush is enabled
     */
    public boolean isAutoFlush() {
        return isAutoFlush;
    }


    /*
     * isThreadSafe
     */
    public void setIsThreadSafe(String value, Node n, ErrorDispatcher err) throws JasperException {

        if ("true".equalsIgnoreCase(value)) {
            isThreadSafe = true;
        } else if ("false".equalsIgnoreCase(value)) {
            isThreadSafe = false;
        } else {
            err.jspError(n, "jsp.error.page.invalid.isthreadsafe");
        }

        isThreadSafeValue = value;
    }

    public String getIsThreadSafe() {
        return isThreadSafeValue;
    }

    public boolean isThreadSafe() {
        return isThreadSafe;
    }


    /**
     * Sets the page info string.
     *
     * @param value The info string
     */
    public void setInfo(String value) {
        info = value;
    }

    /**
     * Returns the page info string.
     *
     * @return The info string
     */
    public String getInfo() {
        return info;
    }


    /**
     * Sets the error page URL.
     *
     * @param value The error page URL
     */
    public void setErrorPage(String value) {
        errorPage = value;
    }

    /**
     * Returns the error page URL.
     *
     * @return The error page URL
     */
    public String getErrorPage() {
        return errorPage;
    }


    /**
     * Sets the isErrorPage attribute.
     *
     * @param value The isErrorPage value
     * @param n The node
     * @param err The error dispatcher
     * @throws JasperException if the value is invalid
     */
    public void setIsErrorPage(String value, Node n, ErrorDispatcher err) throws JasperException {

        if ("true".equalsIgnoreCase(value)) {
            isErrorPage = true;
        } else if ("false".equalsIgnoreCase(value)) {
            isErrorPage = false;
        } else {
            err.jspError(n, "jsp.error.page.invalid.iserrorpage");
        }

        isErrorPageValue = value;
    }

    /**
     * Returns the isErrorPage attribute value.
     *
     * @return The isErrorPage value
     */
    public String getIsErrorPage() {
        return isErrorPageValue;
    }

    /**
     * Checks if this page is an error page.
     *
     * @return true if this is an error page
     */
    public boolean isErrorPage() {
        return isErrorPage;
    }


    /**
     * Sets the isELIgnored attribute.
     *
     * @param value The isELIgnored value
     * @param n The node
     * @param err The error dispatcher
     * @param pagedir Whether this is a page directive
     * @throws JasperException if the value is invalid
     */
    public void setIsELIgnored(String value, Node n, ErrorDispatcher err, boolean pagedir) throws JasperException {

        if ("true".equalsIgnoreCase(value)) {
            isELIgnored = true;
        } else if ("false".equalsIgnoreCase(value)) {
            isELIgnored = false;
        } else {
            if (pagedir) {
                err.jspError(n, "jsp.error.page.invalid.iselignored");
            } else {
                err.jspError(n, "jsp.error.tag.invalid.iselignored");
            }
        }

        isELIgnoredValue = value;
    }


    /**
     * Sets the errorOnELNotFound attribute.
     *
     * @param value The errorOnELNotFound value
     * @param n The node
     * @param err The error dispatcher
     * @param pagedir Whether this is a page directive
     * @throws JasperException if the value is invalid
     */
    public void setErrorOnELNotFound(String value, Node n, ErrorDispatcher err, boolean pagedir)
            throws JasperException {

        if ("true".equalsIgnoreCase(value)) {
            errorOnELNotFound = true;
        } else if ("false".equalsIgnoreCase(value)) {
            errorOnELNotFound = false;
        } else {
            if (pagedir) {
                err.jspError(n, "jsp.error.page.invalid.errorOnELNotFound");
            } else {
                err.jspError(n, "jsp.error.tag.invalid.errorOnELNotFound");
            }
        }

        errorOnELNotFoundValue = value;
    }


    /**
     * Sets the deferredSyntaxAllowedAsLiteral attribute.
     *
     * @param value The deferredSyntaxAllowedAsLiteral value
     * @param n The node
     * @param err The error dispatcher
     * @param pagedir Whether this is a page directive
     * @throws JasperException if the value is invalid
     */
    public void setDeferredSyntaxAllowedAsLiteral(String value, Node n, ErrorDispatcher err, boolean pagedir)
            throws JasperException {

        if ("true".equalsIgnoreCase(value)) {
            deferredSyntaxAllowedAsLiteral = true;
        } else if ("false".equalsIgnoreCase(value)) {
            deferredSyntaxAllowedAsLiteral = false;
        } else {
            if (pagedir) {
                err.jspError(n, "jsp.error.page.invalid.deferredsyntaxallowedasliteral");
            } else {
                err.jspError(n, "jsp.error.tag.invalid.deferredsyntaxallowedasliteral");
            }
        }

        deferredSyntaxAllowedAsLiteralValue = value;
    }

    /**
     * Sets the trimDirectiveWhitespaces attribute.
     *
     * @param value The trimDirectiveWhitespaces value
     * @param n The node
     * @param err The error dispatcher
     * @param pagedir Whether this is a page directive
     * @throws JasperException if the value is invalid
     */
    public void setTrimDirectiveWhitespaces(String value, Node n, ErrorDispatcher err, boolean pagedir)
            throws JasperException {

        if ("true".equalsIgnoreCase(value)) {
            trimDirectiveWhitespaces = true;
        } else if ("false".equalsIgnoreCase(value)) {
            trimDirectiveWhitespaces = false;
        } else {
            if (pagedir) {
                err.jspError(n, "jsp.error.page.invalid.trimdirectivewhitespaces");
            } else {
                err.jspError(n, "jsp.error.tag.invalid.trimdirectivewhitespaces");
            }
        }

        trimDirectiveWhitespacesValue = value;
    }

    /**
     * Sets the EL ignored flag.
     *
     * @param s The EL ignored flag
     */
    public void setELIgnored(boolean s) {
        isELIgnored = s;
    }

    /**
     * Returns the isELIgnored attribute value.
     *
     * @return The isELIgnored value
     */
    public String getIsELIgnored() {
        return isELIgnoredValue;
    }

    /**
     * Checks if EL is ignored.
     *
     * @return true if EL is ignored
     */
    public boolean isELIgnored() {
        return isELIgnored;
    }

    /**
     * Sets the error on EL not found flag.
     *
     * @param s The error on EL not found flag
     */
    public void setErrorOnELNotFound(boolean s) {
        errorOnELNotFound = s;
    }

    /**
     * Returns the errorOnELNotFound attribute value.
     *
     * @return The errorOnELNotFound value
     */
    public String getErrorOnELNotFound() {
        return errorOnELNotFoundValue;
    }

    /**
     * Checks if error on EL not found is enabled.
     *
     * @return true if error on EL not found is enabled
     */
    public boolean isErrorOnELNotFound() {
        return errorOnELNotFound;
    }

    /**
     * Puts a non-custom tag prefix in the map.
     *
     * @param prefix The prefix
     * @param where The mark location
     */
    public void putNonCustomTagPrefix(String prefix, Mark where) {
        nonCustomTagPrefixMap.put(prefix, where);
    }

    /**
     * Gets the mark for a non-custom tag prefix.
     *
     * @param prefix The prefix
     * @return The mark location
     */
    public Mark getNonCustomTagPrefix(String prefix) {
        return nonCustomTagPrefixMap.get(prefix);
    }

    /**
     * Returns the deferredSyntaxAllowedAsLiteral attribute value.
     *
     * @return The deferredSyntaxAllowedAsLiteral value
     */
    public String getDeferredSyntaxAllowedAsLiteral() {
        return deferredSyntaxAllowedAsLiteralValue;
    }

    /**
     * Checks if deferred syntax is allowed as literal.
     *
     * @return true if deferred syntax is allowed as literal
     */
    public boolean isDeferredSyntaxAllowedAsLiteral() {
        return deferredSyntaxAllowedAsLiteral;
    }

    /**
     * Sets whether deferred syntax is allowed as literal.
     *
     * @param isELDeferred true if deferred syntax is allowed
     */
    public void setDeferredSyntaxAllowedAsLiteral(boolean isELDeferred) {
        this.deferredSyntaxAllowedAsLiteral = isELDeferred;
    }

    /**
     * Returns the expression factory.
     *
     * @return The expression factory
     */
    public ExpressionFactory getExpressionFactory() {
        return expressionFactory;
    }

    /**
     * Returns the trimDirectiveWhitespaces attribute value.
     *
     * @return The trimDirectiveWhitespaces value
     */
    public String getTrimDirectiveWhitespaces() {
        return trimDirectiveWhitespacesValue;
    }

    /**
     * Checks if directive whitespaces are trimmed.
     *
     * @return true if directive whitespaces are trimmed
     */
    public boolean isTrimDirectiveWhitespaces() {
        return trimDirectiveWhitespaces;
    }

    /**
     * Sets whether directive whitespaces are trimmed.
     *
     * @param trimDirectiveWhitespaces true if directive whitespaces are trimmed
     */
    public void setTrimDirectiveWhitespaces(boolean trimDirectiveWhitespaces) {
        this.trimDirectiveWhitespaces = trimDirectiveWhitespaces;
    }

    /**
     * Returns the set of variable info names.
     *
     * @return The variable info names
     */
    public Set<String> getVarInfoNames() {
        return varInfoNames;
    }

    /**
     * Checks if error on undeclared namespace is enabled.
     *
     * @return true if error on undeclared namespace is enabled
     */
    public boolean isErrorOnUndeclaredNamespace() {
        return errorOnUndeclaredNamespace;
    }

    /**
     * Sets whether error on undeclared namespace is enabled.
     *
     * @param errorOnUndeclaredNamespace true if error on undeclared namespace is enabled
     */
    public void setErrorOnUndeclaredNamespace(boolean errorOnUndeclaredNamespace) {
        this.errorOnUndeclaredNamespace = errorOnUndeclaredNamespace;
    }
}
