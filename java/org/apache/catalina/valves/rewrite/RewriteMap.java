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

import org.apache.tomcat.util.res.StringManager;

/**
 * Interface for user defined lookup/replacement logic that can be defined in
 * a {@code rewrite.config} file by a {@code RewriteMap} directive. Such a map
 * can then be used by a {@code RewriteRule} defined in the same file.
 * <p>
 * An example {@code rewrite.config} file could look like:
 * <pre>
 * RewriteMap uc example.UpperCaseMap
 *
 * RewriteRule ^/(.*)$ ${uc:$1}
 * </pre>
 *
 * One parameter can be optionally appended to the {@code RewriteMap} directive.
 * This could be used &ndash; for example &ndash; to specify a name of a file, that
 * contains a lookup table used by the implementation of the map.
 */
public interface RewriteMap {

    /**
     * Optional parameter that can be defined through the {@code RewriteMap}
     * directive in the {@code rewrite.config} file.
     *
     * @param params the optional parameter
     * @return value is currently ignored
     */
    public String setParameters(String params);

    /**
     * Optional parameters that can be defined through the {@code RewriteMap}
     * directive in the {@code rewrite.config} file.
     * <p>
     * This method will be called, if there are more than one parameters defined.
     *
     * @param params the optional parameters
     */
    default void setParameters(String... params) {
        if (params == null) {
            return;
        }
        if (params.length > 1) {
            throw new IllegalArgumentException(
                    StringManager.getManager(RewriteMap.class).getString("rewriteMap.tooManyParameters"));
        }
        setParameters(params[0]);
    }

    /**
     * Maps a key to a replacement value.<br>
     * The method is free to return {@code null} to indicate, that the default
     * value from the {@code RewriteRule} directive should be used.
     *
     * @param key used by the actual implementation to generate a mapped value
     * @return mapped value or {@code null}
     */
    public String lookup(String key);
}
