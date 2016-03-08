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

import javax.servlet.http.Mapping;
import javax.servlet.http.MappingMatch;

import org.apache.catalina.mapper.MappingData;

public class ApplicationMapping implements Mapping {

    private final String matchValue;
    private final String pattern;
    private final MappingMatch mappingMatch;

    public ApplicationMapping(MappingData mappingData) {
        matchValue = mappingData.matchValue;
        pattern = mappingData.matchPattern;
        mappingMatch = mappingData.matchType;
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
    public MappingMatch getMatchType() {
        return mappingMatch;
    }
}
