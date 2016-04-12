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
package org.apache.catalina.core;

import org.apache.catalina.mapper.MappingData;
import org.apache.catalina.servlet4preview.http.Mapping;
import org.apache.catalina.servlet4preview.http.MappingMatch;

public class ApplicationMapping {

    private final MappingData mappingData;

    private volatile Mapping mapping = null;

    public ApplicationMapping(MappingData mappingData) {
        this.mappingData = mappingData;
    }

    public Mapping getMapping() {
        if (mapping == null) {
            if (mappingData == null) {
                mapping = new MappingImpl("", "", MappingMatch.UNKNOWN, "");
            } else {
                String servletName;
                if (mappingData.wrapper == null) {
                    servletName = "";
                } else {
                    servletName = mappingData.wrapper.getName();
                }
                switch (mappingData.matchType) {
                    case CONTEXT_ROOT:
                        mapping = new MappingImpl("", "", mappingData.matchType, servletName);
                        break;
                    case DEFAULT:
                        mapping = new MappingImpl("/", "/", mappingData.matchType, servletName);
                        break;
                    case EXACT:
                        mapping = new MappingImpl(mappingData.wrapperPath.toString(),
                                mappingData.wrapperPath.toString(), mappingData.matchType, servletName);
                        break;
                    case EXTENSION:
                        String path = mappingData.wrapperPath.toString();
                        int extIndex = path.lastIndexOf('.');
                        mapping = new MappingImpl(path.substring(0, extIndex),
                                "*" + path.substring(extIndex), mappingData.matchType, servletName);
                        break;
                    case PATH:
                        mapping = new MappingImpl(mappingData.pathInfo.toString(),
                                mappingData.wrapperPath.toString() + "/*",
                                mappingData.matchType, servletName);
                        break;
                    case UNKNOWN:
                        mapping = new MappingImpl("", "", mappingData.matchType, servletName);
                        break;
                }
            }
        }

        return mapping;
    }

    public void recycle() {
        mapping = null;
    }

    private static class MappingImpl implements Mapping {

        private final String matchValue;
        private final String pattern;
        private final MappingMatch mappingType;
        private final String servletName;

        public MappingImpl(String matchValue, String pattern, MappingMatch mappingType,
                String servletName) {
            this.matchValue = matchValue;
            this.pattern = pattern;
            this.mappingType = mappingType;
            this.servletName = servletName;
        }

        @Override
        public String getMatchValue() {
            return matchValue;
        }

        @Override
        public String getPattern() {
            return pattern;
        }

        @Override
        public MappingMatch getMappingMatch() {
            return mappingType;
        }

        @Override
        public String getServletName() {
            return servletName;
        }
    }
}
