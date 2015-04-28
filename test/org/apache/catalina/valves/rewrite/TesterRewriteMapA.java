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

import java.util.HashMap;
import java.util.Map;

public class TesterRewriteMapA implements RewriteMap {

    private static final Map<String,String> map = new HashMap<>();

    static {
        map.put("a", "aa");
        map.put("b", "bb");
    }

    @Override
    public String setParameters(String params) {
        // NO-OP
        return null;
    }

    @Override
    public String lookup(String key) {
        return map.get(key);
    }
}
