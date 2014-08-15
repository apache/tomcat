/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.connector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TesterRequest extends Request {
    @Override
    public String getScheme() {
        return "http";
    }

    @Override
    public String getServerName() {
        return "localhost";
    }

    @Override
    public int getServerPort() {
        return 8080;
    }

    @Override
    public String getDecodedRequestURI() {
        return "/level1/level2/foo.html";
    }

    private String method;
    public void setMethod(String method) {
        this.method = method;
    }
    @Override
    public String getMethod() {
        return method;
    }

    private final Map<String,List<String>> headers = new HashMap<>();
    protected void addHeader(String name, String value) {
        List<String> values = headers.get(name);
        if (values == null) {
            values = new ArrayList<>();
            headers.put(name, values);
        }
        values.add(value);
    }
    @Override
    public String getHeader(String name) {
        List<String> values = headers.get(name);
        if (values == null || values.size() == 0) {
            return null;
        }
        return values.get(0);
    }
    @Override
    public Enumeration<String> getHeaders(String name) {
        List<String> values = headers.get(name);
        if (values == null || values.size() == 0) {
            return Collections.emptyEnumeration();
        }
        return Collections.enumeration(headers.get(name));
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }
}
