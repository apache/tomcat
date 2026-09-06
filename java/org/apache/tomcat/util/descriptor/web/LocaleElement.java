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
import java.io.Serializable;

/**
 * Represents an element of a deployment descriptor that supports internationalization (i18n) via the optional
 * {@code xml:lang} attribute, for example {@code <description>} and {@code <display-name>}.
 * <p>
 * The {@code content} holds the element body text. The {@code lang} holds the value of the optional {@code xml:lang}
 * attribute, or {@code null} if the attribute is absent. An element without a {@code lang} is the default element
 * that applies when no language specific element matches.
 * </p>
 */
public class LocaleElement implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The content of the element.
     */
    private final String content;

    /**
     * The value of the {@code xml:lang} attribute, or {@code null} if not specified.
     */
    private final String lang;

    /**
     * Creates a new LocaleElement.
     *
     * @param content The element content
     * @param lang    The value of the {@code xml:lang} attribute, or {@code null} if not specified
     */
    public LocaleElement(String content, String lang) {
        this.content = content;
        this.lang = lang;
    }

    /**
     * Returns the content of the element.
     *
     * @return The element content
     */
    public String getContent() {
        return content;
    }

    /**
     * Returns the value of the {@code xml:lang} attribute, or {@code null} if not specified.
     *
     * @return The {@code xml:lang} attribute value
     */
    public String getLang() {
        return lang;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((content == null) ? 0 : content.hashCode());
        result = prime * result + ((lang == null) ? 0 : lang.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        LocaleElement other = (LocaleElement) obj;
        if (content == null) {
            if (other.content != null) {
                return false;
            }
        } else if (!content.equals(other.content)) {
            return false;
        }
        if (lang == null) {
            return other.lang == null;
        } else {
            return lang.equals(other.lang);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LocaleElement[");
        sb.append("content=");
        sb.append(content);
        if (lang != null) {
            sb.append(", lang=");
            sb.append(lang);
        }
        sb.append(']');
        return sb.toString();
    }
}
