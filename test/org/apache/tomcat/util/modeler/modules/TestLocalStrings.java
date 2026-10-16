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
package org.apache.tomcat.util.modeler.modules;

import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.res.StringManager;

public class TestLocalStrings {

    private static final String PACKAGE_NAME = "org.apache.tomcat.util.modeler.modules";

    // Keys used by the classes in this package via the StringManager that is
    // bound to this package. A key that is missing from this package's
    // bundle is not resolved by the parent package and the message is lost.
    private static final String[] KEYS = { "modules.readDescriptorsError", "source.introspectionError" };

    @Test
    public void testKeysResolveInDefaultBundle() {
        assertKeysResolve(StringManager.getManager(PACKAGE_NAME));
    }

    @Test
    public void testKeysResolveInFrenchBundle() {
        assertKeysResolve(StringManager.getManager(PACKAGE_NAME, Locale.FRENCH));
    }

    @Test
    public void testKeysResolveInJapaneseBundle() {
        assertKeysResolve(StringManager.getManager(PACKAGE_NAME, Locale.JAPANESE));
    }

    private void assertKeysResolve(StringManager sm) {
        for (String key : KEYS) {
            String value = sm.getString(key);
            Assert.assertNotNull("Missing key: " + key, value);
            Assert.assertNotEquals("Unresolved key: " + key, key, value);
        }
    }
}
