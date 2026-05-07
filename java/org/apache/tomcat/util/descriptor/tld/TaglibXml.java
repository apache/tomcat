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
package org.apache.tomcat.util.descriptor.tld;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.jsp.tagext.FunctionInfo;

/**
 * Common representation of a Tag Library Descriptor (TLD) XML file.
 * <p>
 * This stores the raw result of parsing an TLD XML file, flattening different version of the descriptors to a common
 * format. This is different to a TagLibraryInfo instance that would be passed to a tag validator in that it does not
 * contain the uri and prefix values used by a JSP to reference this tag library.
 */
public class TaglibXml {
    /**
     * Constructs a new TaglibXml.
     */
    public TaglibXml() {
    }

    /**
     * The tag library version.
     */
    private String tlibVersion;

    /**
     * The JSP version requirement.
     */
    private String jspVersion;

    /**
     * The short name of the tag library.
     */
    private String shortName;

    /**
     * The URI of the tag library.
     */
    private String uri;

    /**
     * Description information about the tag library.
     */
    private String info;

    /**
     * The validator configuration.
     */
    private ValidatorXml validator;

    /**
     * The list of tag definitions.
     */
    private final List<TagXml> tags = new ArrayList<>();

    /**
     * The list of tag file definitions.
     */
    private final List<TagFileXml> tagFiles = new ArrayList<>();

    /**
     * The list of listener class names.
     */
    private final List<String> listeners = new ArrayList<>();

    /**
     * The list of function definitions.
     */
    private final List<FunctionInfo> functions = new ArrayList<>();

    /**
     * Returns the tag library version.
     * @return the library version
     */
    public String getTlibVersion() {
        return tlibVersion;
    }

    /**
     * Sets the tag library version.
     * @param tlibVersion the library version
     */
    public void setTlibVersion(String tlibVersion) {
        this.tlibVersion = tlibVersion;
    }

    /**
     * Returns the JSP version requirement.
     * @return the JSP version
     */
    public String getJspVersion() {
        return jspVersion;
    }

    /**
     * Sets the JSP version requirement.
     * @param jspVersion the JSP version
     */
    public void setJspVersion(String jspVersion) {
        this.jspVersion = jspVersion;
    }

    /**
     * Returns the short name of the tag library.
     * @return the short name
     */
    public String getShortName() {
        return shortName;
    }

    /**
     * Sets the short name of the tag library.
     * @param shortName the short name
     */
    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    /**
     * Returns the URI of the tag library.
     * @return the URI
     */
    public String getUri() {
        return uri;
    }

    /**
     * Sets the URI of the tag library.
     * @param uri the URI
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * Returns the description information.
     * @return the info
     */
    public String getInfo() {
        return info;
    }

    /**
     * Sets the description information.
     * @param info the info
     */
    public void setInfo(String info) {
        this.info = info;
    }

    /**
     * Returns the validator configuration.
     * @return the validator
     */
    public ValidatorXml getValidator() {
        return validator;
    }

    /**
     * Sets the validator configuration.
     * @param validator the validator
     */
    public void setValidator(ValidatorXml validator) {
        this.validator = validator;
    }

    /**
     * Adds a tag definition to this tag library.
     * @param tag the tag to add
     */
    public void addTag(TagXml tag) {
        tags.add(tag);
    }

    /**
     * Returns the list of tag definitions.
     * @return the tags
     */
    public List<TagXml> getTags() {
        return tags;
    }

    /**
     * Adds a tag file definition to this tag library.
     * @param tag the tag file to add
     */
    public void addTagFile(TagFileXml tag) {
        tagFiles.add(tag);
    }

    /**
     * Returns the list of tag file definitions.
     * @return the tag files
     */
    public List<TagFileXml> getTagFiles() {
        return tagFiles;
    }

    /**
     * Adds a listener class name to this tag library.
     * @param listener the listener class name
     */
    public void addListener(String listener) {
        listeners.add(listener);
    }

    /**
     * Returns the list of listener class names.
     * @return the listeners
     */
    public List<String> getListeners() {
        return listeners;
    }

    /**
     * Adds a function definition to this tag library.
     * @param name the function name
     * @param klass the function class
     * @param signature the function signature
     */
    public void addFunction(String name, String klass, String signature) {
        functions.add(new FunctionInfo(name, klass, signature));
    }

    /**
     * Returns the list of function definitions.
     * @return the functions
     */
    public List<FunctionInfo> getFunctions() {
        return functions;
    }
}
