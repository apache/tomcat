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

public class InternalRewriteMap {

    public static RewriteMap toMap(String name) {
        if ("toupper".equals(name)) {
            return new UpperCase();
        } else if ("tolower".equals(name)) {
            return new LowerCase();
        } else if ("escape".equals(name)) {
            return new Escape();
        } else if ("unescape".equals(name)) {
            return new Unescape();
        } else {
            return null;
        }
    }

    public static class LowerCase implements RewriteMap {

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

    public static class UpperCase implements RewriteMap {

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

    public static class Escape implements RewriteMap {

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

    public static class Unescape implements RewriteMap {

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
