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
package org.apache.tomcat.util.http.fileupload.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tomcat.util.http.fileupload.FileItemHeaders;

/**
 * Default implementation of the {@link FileItemHeaders} interface.
 *
 * @since FileUpload 1.2.1
 */
public class FileItemHeadersImpl implements FileItemHeaders, Serializable {

    /**
     * Serial version UID, being used, if serialized.
     */
    private static final long serialVersionUID = -4455695752627032559L;

    /**
     * Map of {@code String} keys to a {@code List} of
     * {@code String} instances.
     */
    private final Map<String, List<String>> headerNameToValueListMap = new LinkedHashMap<>();

    @Override
    public String getHeader(final String name) {
        final String nameLower = name.toLowerCase(Locale.ENGLISH);
        final List<String> headerValueList = headerNameToValueListMap.get(nameLower);
        if (null == headerValueList) {
            return null;
        }
        return headerValueList.get(0);
    }

    @Override
    public Iterator<String> getHeaderNames() {
        return headerNameToValueListMap.keySet().iterator();
    }

    @Override
    public Iterator<String> getHeaders(final String name) {
        final String nameLower = name.toLowerCase(Locale.ENGLISH);
        List<String> headerValueList = headerNameToValueListMap.get(nameLower);
        if (null == headerValueList) {
            headerValueList = Collections.emptyList();
        }
        return headerValueList.iterator();
    }

    /**
     * Method to add header values to this instance.
     *
     * @param name name of this header
     * @param value value of this header
     */
    public synchronized void addHeader(final String name, final String value) {
        final String nameLower = name.toLowerCase(Locale.ENGLISH);
        final List<String> headerValueList = headerNameToValueListMap.
                computeIfAbsent(nameLower, k -> new ArrayList<>());
        headerValueList.add(value);
    }

}
