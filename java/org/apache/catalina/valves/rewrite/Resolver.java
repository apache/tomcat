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
    /**
     * Default constructor.
     */
    public Resolver() {
    }

    /**
     * Resolve a key to a string value.
     *
     * @param key The key to resolve
     * @return The resolved string value
     */
    public abstract String resolve(String key);

    /**
     * Resolve an environment variable key to a string value.
     *
     * @param key The key to resolve
     * @return The resolved string value
     */
    public String resolveEnv(String key) {
        return System.getProperty(key);
    }

    /**
     * Resolve an SSL variable key to a string value.
     *
     * @param key The key to resolve
     * @return The resolved string value
     */
    public abstract String resolveSsl(String key);

    /**
     * Resolve an HTTP header key to a string value.
     *
     * @param key The key to resolve
     * @return The resolved string value
     */
    public abstract String resolveHttp(String key);

    /**
     * Resolve a resource check.
     *
     * @param type The type of resource check (0=directory, 1=file, 2=non-empty file)
     * @param name The resource name
     * @return True if the resource matches the specified type
     */
    public abstract boolean resolveResource(int type, String name);

    /**
     * Return the URI character set.
     *
     * @return The URI character set
     */
    public abstract Charset getUriCharset();
}
