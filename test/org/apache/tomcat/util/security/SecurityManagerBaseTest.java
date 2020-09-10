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
package org.apache.tomcat.util.security;

import java.security.Permission;

import org.apache.catalina.security.SecurityUtil;

/**
 * Base test class for unit tests which require the Java 2 {@link
 * SecurityManager} to be enabled. Tests that extend this class must be run in a
 * forked SecurityManager test batch since this class modifies global {@link
 * System} settings which may interfere with other tests. On static class
 * initialization, this class sets up the {@code "package.definition"} and
 * {@code "package.access"} system properties and adds a no-op SecurityManager
 * which does not check permissions. These settings are required in order to
 * make {@link org.apache.catalina.Globals#IS_SECURITY_ENABLED} and {@link
 * SecurityUtil#isPackageProtectionEnabled()} return true.
 */
public abstract class SecurityManagerBaseTest {
    static {
        System.setProperty("package.definition", "test");
        System.setProperty("package.access", "test");
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkPermission(final Permission permission) {
                // no-op
            }

            @Override
            public void checkPermission(final Permission permission, Object context) {
                // no-op
            }
        });
    }
}
