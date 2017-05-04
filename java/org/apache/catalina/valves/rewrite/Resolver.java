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

/**
 * Resolver abstract class.
 */
public abstract class Resolver {

    public abstract String resolve(String key);

    public String resolveEnv(String key) {
        return System.getProperty(key);
    }

    public abstract String resolveSsl(String key);

    public abstract String resolveHttp(String key);

    public abstract boolean resolveResource(int type, String name);

    /**
     * @return The name of the encoding to use to %nn encode URIs
     *
     * @deprecated This will be removed in Tomcat 9.0.x
     */
    @Deprecated
    public abstract String getUriEncoding();

    public abstract Charset getUriCharset();
}
