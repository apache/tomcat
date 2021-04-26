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

import jakarta.servlet.jsp.tagext.TagAttributeInfo;
import jakarta.servlet.jsp.tagext.TagInfo;
import jakarta.servlet.jsp.tagext.TagVariableInfo;

/**
 * Model of a tag define in a tag library descriptor.
 * This represents the information as parsed from the XML but differs from
 * TagInfo in that is does not provide a link back to the tag library that
 * defined it.
 */
public class TagXml {
    private String name;
    private String tagClass;
    private String teiClass;
    private String bodyContent = TagInfo.BODY_CONTENT_JSP;
    private String displayName;
    private String smallIcon;
    private String largeIcon;
    private String info;
    private boolean dynamicAttributes;
    private final List<TagAttributeInfo> attributes = new ArrayList<>();
    private final List<TagVariableInfo> variables = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTagClass() {
        return tagClass;
    }

    public void setTagClass(String tagClass) {
        this.tagClass = tagClass;
    }

    public String getTeiClass() {
        return teiClass;
    }

    public void setTeiClass(String teiClass) {
        this.teiClass = teiClass;
    }

    public String getBodyContent() {
        return bodyContent;
    }

    public void setBodyContent(String bodyContent) {
        this.bodyContent = bodyContent;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSmallIcon() {
        return smallIcon;
    }

    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }

    public String getLargeIcon() {
        return largeIcon;
    }

    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public boolean hasDynamicAttributes() {
        return dynamicAttributes;
    }

    public void setDynamicAttributes(boolean dynamicAttributes) {
        this.dynamicAttributes = dynamicAttributes;
    }

    public List<TagAttributeInfo> getAttributes() {
        return attributes;
    }

    public List<TagVariableInfo> getVariables() {
        return variables;
    }
}
