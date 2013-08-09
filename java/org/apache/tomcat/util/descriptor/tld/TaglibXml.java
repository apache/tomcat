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

import javax.servlet.jsp.tagext.FunctionInfo;

/**
 * Common representation of a Tag Library Descriptor (TLD) XML file.
 * <p/>
 * This stores the raw result of parsing an TLD XML file, flattening different
 * version of the descriptors to a common format. This is different to a
 * TagLibraryInfo instance that would be passed to a tag validator in that it
 * does not contain the uri and prefix values used by a JSP to reference this
 * tag library.
 */
public class TaglibXml {
    private String tlibVersion;
    private String jspVersion;
    private String shortName;
    private String uri;
    private String info;
    private Validator validator;
    private List<Tag> tags;
    private List<String> listeners;
    private List<FunctionInfo> functions;

    public String getTlibVersion() {
        return tlibVersion;
    }

    public void setTlibVersion(String tlibVersion) {
        this.tlibVersion = tlibVersion;
    }

    public String getJspVersion() {
        return jspVersion;
    }

    public void setJspVersion(String jspVersion) {
        this.jspVersion = jspVersion;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public Validator getValidator() {
        return validator;
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    public void addTag(Tag tag) {
        if (tags == null) {
            tags = new ArrayList<>();
        }
        tags.add(tag);
    }

    public List<Tag> getTags() {
        return tags;
    }

    public void addListener(String listener) {
        if (listeners == null) {
            listeners = new ArrayList<>();
        }
        listeners.add(listener);
    }

    public List<String> getListeners() {
        return listeners;
    }

    public void addFunction(FunctionInfo functionInfo) {
        if (functions == null) {
            functions = new ArrayList<>();
        }
        functions.add(functionInfo);
    }

    public List<FunctionInfo> getFunctions() {
        return functions;
    }
}
