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
package org.apache.catalina.valves.rewrite;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.catalina.util.URLEncoder;
import org.apache.tomcat.util.buf.UDecoder;

/**
 * Utility class providing built-in rewrite map implementations.
 */
public class InternalRewriteMap {

    /**
     * Constructs a new InternalRewriteMap.
     */
    private InternalRewriteMap() {
    }

    /**
     * Get a built-in RewriteMap by name.
     *
     * @param name the map name (toupper, tolower, escape, unescape)
     * @return the RewriteMap implementation, or {@code null} if not found
     */
    public static RewriteMap toMap(String name) {
        return switch (name) {
            case "toupper" -> new UpperCase();
            case "tolower" -> new LowerCase();
            case "escape" -> new Escape();
            case "unescape" -> new Unescape();
            case null, default -> null;
        };
    }

    /**
     * RewriteMap that converts strings to lower case.
     */
    public static class LowerCase implements RewriteMap {

        /**
         * Constructs a new LowerCase rewrite map.
         */
        public LowerCase() {
        }

        private Locale locale = Locale.getDefault();

        @Override
        public String setParameters(String params) {
            this.locale = Locale.forLanguageTag(params);
            return null;
        }

        @Override
        public String lookup(String key) {
            if (key != null) {
                return key.toLowerCase(locale);
            }
            return null;
        }

    }

    /**
     * RewriteMap that converts strings to upper case.
     */
    public static class UpperCase implements RewriteMap {

        /**
         * Constructs a new UpperCase rewrite map.
         */
        public UpperCase() {
        }

        private Locale locale = Locale.getDefault();

        @Override
        public String setParameters(String params) {
            this.locale = Locale.forLanguageTag(params);
            return null;
        }

        @Override
        public String lookup(String key) {
            if (key != null) {
                return key.toUpperCase(locale);
            }
            return null;
        }

    }

    /**
     * RewriteMap that URL-encodes strings.
     */
    public static class Escape implements RewriteMap {

        /**
         * Constructs a new Escape rewrite map.
         */
        public Escape() {
        }

        private Charset charset = StandardCharsets.UTF_8;

        @Override
        public String setParameters(String params) {
            this.charset = Charset.forName(params);
            return null;
        }

        @Override
        public String lookup(String key) {
            if (key != null) {
                return URLEncoder.DEFAULT.encode(key, charset);
            }
            return null;
        }

    }

    /**
     * RewriteMap that URL-decodes strings.
     */
    public static class Unescape implements RewriteMap {

        /**
         * Constructs a new Unescape rewrite map.
         */
        public Unescape() {
        }

        private Charset charset = StandardCharsets.UTF_8;

        @Override
        public String setParameters(String params) {
            this.charset = Charset.forName(params);
            return null;
        }

        @Override
        public String lookup(String key) {
            if (key != null) {
                return UDecoder.URLDecode(key, charset);
            }
            return null;
        }

    }

}
