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
package org.apache.tomcat.util.digester;

import java.security.Permission;
import java.util.PropertyPermission;

import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.security.PermissionCheck;

/**
 * A {@link org.apache.tomcat.util.IntrospectionUtils.SecurePropertySource}
 * that uses system properties to resolve expressions.
 * This property source is always active by default.
 *
 * @see Digester
 */
public class SystemPropertySource implements IntrospectionUtils.SecurePropertySource {

    @Override
    public String getProperty(String key) {
        // For backward compatibility
        return getProperty(key, null);
    }

    @Override
    public String getProperty(String key, ClassLoader classLoader) {
        if (classLoader instanceof PermissionCheck) {
            Permission p = new PropertyPermission(key, "read");
            if (!((PermissionCheck) classLoader).check(p)) {
                return null;
            }
        }
        return System.getProperty(key);
    }

}
