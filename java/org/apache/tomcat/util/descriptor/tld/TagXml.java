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
 * Model of a tag define in a tag library descriptor. This represents the information as parsed from the XML but differs
 * from TagInfo in that it does not provide a link back to the tag library that defined it.
 */
public class TagXml {
    /**
     * Constructs a new TagXml.
     */
    public TagXml() {
    }

    /**
     * The tag name.
     */
    private String name;

    /**
     * The tag handler class.
     */
    private String tagClass;

    /**
     * The TagExtraInfo class.
     */
    private String teiClass;

    /**
     * The body content type.
     */
    private String bodyContent = TagInfo.BODY_CONTENT_JSP;

    /**
     * The display name.
     */
    private String displayName;

    /**
     * The small icon path.
     */
    private String smallIcon;

    /**
     * The large icon path.
     */
    private String largeIcon;

    /**
     * The description info.
     */
    private String info;

    /**
     * Whether the tag accepts dynamic attributes.
     */
    private boolean dynamicAttributes;

    /**
     * The list of tag attributes.
     */
    private final List<TagAttributeInfo> attributes = new ArrayList<>();

    /**
     * The list of tag variables.
     */
    private final List<TagVariableInfo> variables = new ArrayList<>();

    /**
     * Returns the tag name.
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the tag name.
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the tag handler class.
     * @return the tag class
     */
    public String getTagClass() {
        return tagClass;
    }

    /**
     * Sets the tag handler class.
     * @param tagClass the tag class
     */
    public void setTagClass(String tagClass) {
        this.tagClass = tagClass;
    }

    /**
     * Returns the TagExtraInfo class.
     * @return the TEI class
     */
    public String getTeiClass() {
        return teiClass;
    }

    /**
     * Sets the TagExtraInfo class.
     * @param teiClass the TEI class
     */
    public void setTeiClass(String teiClass) {
        this.teiClass = teiClass;
    }

    /**
     * Returns the body content type.
     * @return the body content type
     */
    public String getBodyContent() {
        return bodyContent;
    }

    /**
     * Sets the body content type.
     * @param bodyContent the body content type
     */
    public void setBodyContent(String bodyContent) {
        this.bodyContent = bodyContent;
    }

    /**
     * Returns the display name.
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the display name.
     * @param displayName the display name
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the small icon path.
     * @return the small icon path
     */
    public String getSmallIcon() {
        return smallIcon;
    }

    /**
     * Sets the small icon path.
     * @param smallIcon the small icon path
     */
    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }

    /**
     * Returns the large icon path.
     * @return the large icon path
     */
    public String getLargeIcon() {
        return largeIcon;
    }

    /**
     * Sets the large icon path.
     * @param largeIcon the large icon path
     */
    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }

    /**
     * Returns the description info.
     * @return the info
     */
    public String getInfo() {
        return info;
    }

    /**
     * Sets the description info.
     * @param info the info
     */
    public void setInfo(String info) {
        this.info = info;
    }

    /**
     * Returns whether the tag accepts dynamic attributes.
     * @return true if dynamic attributes are supported
     */
    public boolean hasDynamicAttributes() {
        return dynamicAttributes;
    }

    /**
     * Sets whether the tag accepts dynamic attributes.
     * @param dynamicAttributes true if dynamic attributes are supported
     */
    public void setDynamicAttributes(boolean dynamicAttributes) {
        this.dynamicAttributes = dynamicAttributes;
    }

    /**
     * Returns the list of tag attributes.
     * @return the attributes
     */
    public List<TagAttributeInfo> getAttributes() {
        return attributes;
    }

    /**
     * Returns the list of tag variables.
     * @return the variables
     */
    public List<TagVariableInfo> getVariables() {
        return variables;
    }
}
