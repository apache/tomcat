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
package org.apache.tomcat.util.buf;

import java.util.Locale;

import org.apache.tomcat.util.res.StringManager;

public enum EncodedSolidusHandling {
    DECODE("decode"),
    REJECT("reject"),
    PASS_THROUGH("passthrough");

    private static final StringManager sm = StringManager.getManager(EncodedSolidusHandling.class);

    private final String value;

    EncodedSolidusHandling(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EncodedSolidusHandling fromString(String from) {
        String trimmedLower = from.trim().toLowerCase(Locale.ENGLISH);

        for (EncodedSolidusHandling value : EncodedSolidusHandling.values()) {
            if (value.getValue().equals(trimmedLower)) {
                return value;
            }
        }

        throw new IllegalStateException(sm.getString("encodedSolidusHandling.invalid", from));
    }
}
