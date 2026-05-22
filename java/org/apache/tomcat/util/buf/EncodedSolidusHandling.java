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

/**
 * Enumerates the possible handling strategies for encoded solidus characters (%2F) in URI paths.
 */
public enum EncodedSolidusHandling {
    /**
     * Decode the encoded solidus back to a forward slash character.
     */
    DECODE("decode"),
    /**
     * Reject the request containing an encoded solidus.
     */
    REJECT("reject"),
    /**
     * Pass the encoded solidus through without modification.
     */
    PASS_THROUGH("passthrough");

    private static final StringManager sm = StringManager.getManager(EncodedSolidusHandling.class);

    private final String value;

    /**
     * Creates a new handling strategy with the given string value.
     *
     * @param value the string representation of this handling strategy
     */
    EncodedSolidusHandling(String value) {
        this.value = value;
    }

    /**
     * Returns the string value for this handling strategy.
     *
     * @return the string value
     */
    public String getValue() {
        return value;
    }

    /**
     * Converts a string to the corresponding handling strategy.
     *
     * @param from the string to convert
     * @return the matching handling strategy
     * @throws IllegalStateException if the string does not match any known strategy
     */
    public static EncodedSolidusHandling fromString(String from) {
        String trimmedLower = from.trim().toLowerCase(Locale.ENGLISH);

        for (EncodedSolidusHandling value : values()) {
            if (value.getValue().equals(trimmedLower)) {
                return value;
            }
        }

        throw new IllegalStateException(sm.getString("encodedSolidusHandling.invalid", from));
    }
}
