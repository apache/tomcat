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
package org.apache.tomcat.util.descriptor.web;


import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Representation of a message destination for a web application, as represented in a
 * <code>&lt;message-destination&gt;</code> element in the deployment descriptor.
 * </p>
 *
 * @since Tomcat 5.0
 */
public class MessageDestination extends ResourceBase {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     */
    public MessageDestination() {
    }

    // ------------------------------------------------------------- Properties


    /**
     * The display names of this destination. Multiple display names, each with an optional language, are supported as
     * per the deployment descriptor specification.
     */
    private final List<LocaleElement> displayNames = new ArrayList<>();

    /**
     * Get the display names.
     * @return the display names
     */
    public List<LocaleElement> getDisplayNames() {
        return displayNames;
    }

    /**
     * Add a display name to this destination.
     * @param displayName the display name to add
     */
    public void addDisplayName(LocaleElement displayName) {
        displayNames.add(displayName);
    }

    /**
     * Get the display name. The default display name (the one without a language) is returned if present, otherwise
     * the first display name is returned.
     * @return the display name
     */
    public String getDisplayName() {
        for (LocaleElement element : displayNames) {
            if (element.getLang() == null) {
                return element.getContent();
            }
        }
        return displayNames.isEmpty() ? null : displayNames.get(0).getContent();
    }

    /**
     * Set the display name. Any existing display names, including language specific ones, are replaced by a single
     * default display name.
     * @param displayName the display name
     */
    public void setDisplayName(String displayName) {
        displayNames.clear();
        if (displayName != null) {
            displayNames.add(new LocaleElement(displayName, null));
        }
    }


    /**
     * The large icon of this destination.
     */
    private String largeIcon = null;

    /**
     * Get the large icon.
     * @return the large icon
     */
    public String getLargeIcon() {
        return this.largeIcon;
    }

    /**
     * Set the large icon.
     * @param largeIcon the large icon
     */
    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }


    /**
     * The small icon of this destination.
     */
    private String smallIcon = null;

    /**
     * Get the small icon.
     * @return the small icon
     */
    public String getSmallIcon() {
        return this.smallIcon;
    }

    /**
     * Set the small icon.
     * @param smallIcon the small icon
     */
    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MessageDestination[");
        sb.append("name=");
        sb.append(getName());
        if (getDisplayName() != null) {
            sb.append(", displayName=");
            sb.append(getDisplayName());
        }
        if (largeIcon != null) {
            sb.append(", largeIcon=");
            sb.append(largeIcon);
        }
        if (smallIcon != null) {
            sb.append(", smallIcon=");
            sb.append(smallIcon);
        }
        if (getDescription() != null) {
            sb.append(", description=");
            sb.append(getDescription());
        }
        sb.append(']');
        return sb.toString();
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + displayNames.hashCode();
        result = prime * result + ((largeIcon == null) ? 0 : largeIcon.hashCode());
        result = prime * result + ((smallIcon == null) ? 0 : smallIcon.hashCode());
        return result;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MessageDestination other = (MessageDestination) obj;
        if (!displayNames.equals(other.displayNames)) {
            return false;
        }
        if (largeIcon == null) {
            if (other.largeIcon != null) {
                return false;
            }
        } else if (!largeIcon.equals(other.largeIcon)) {
            return false;
        }
        if (smallIcon == null) {
            return other.smallIcon == null;
        } else {
            return smallIcon.equals(other.smallIcon);
        }
    }
}
