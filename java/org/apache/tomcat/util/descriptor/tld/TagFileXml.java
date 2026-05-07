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

/**
 * Bare-bone model of a tag file loaded from a TLD. This does not contain the tag-specific attributes that requiring
 * parsing the actual tag file to derive.
 */
public class TagFileXml {
    /**
     * Constructs a new TagFileXml.
     */
    public TagFileXml() {
    }

    /**
     * The tag name.
     */
    private String name;

    /**
     * The tag file path.
     */
    private String path;

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
     * Returns the tag name.
     * @return the tag name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the tag name.
     * @param name the tag name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the tag file path.
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets the tag file path.
     * @param path the path
     */
    public void setPath(String path) {
        this.path = path;
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
}
