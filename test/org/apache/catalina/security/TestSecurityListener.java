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
package org.apache.catalina.security;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class TestSecurityListener {

    private final String originalUserName = System.getProperty("user.name");

    @After
    public void restoreUserName() {
        if (originalUserName != null) {
            System.setProperty("user.name", originalUserName);
        } else {
            System.clearProperty("user.name");
        }
    }

    @Test
    public void testCheckOsUserWithWhitespaceInList() {
        SecurityListener listener = new SecurityListener();
        listener.setCheckedOsUsers("root, admin");

        System.setProperty("user.name", "admin");
        try {
            listener.checkOsUser();
        } catch (Error e) {
            // Expected. Before the fix the whitespace was not trimmed so the
            // entry never matched and no Error was thrown.
            return;
        }
        Assert.fail("User [admin] was expected to be prohibited by the " +
                "configured list [root, admin]");
    }
}
