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
package org.apache.tomcat.util.net.openssl;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents an OpenSSL configuration command with a name-value pair.
 */
public class OpenSSLConfCmd implements Serializable {

    // Tomcat / Tomcat Native custom commands. Used internally by Tomcat. Not intended for direct use by users.
     /** Disables OCSP checking. */
    public static final String NO_OCSP_CHECK = "NO_OCSP_CHECK";
    /** Enables OCSP soft fail mode. */
    public static final String OCSP_SOFT_FAIL = "OCSP_SOFT_FAIL";
    /** Sets OCSP timeout. */
    public static final String OCSP_TIMEOUT = "OCSP_TIMEOUT";
    /** Sets OCSP verify flags. */
    public static final String OCSP_VERIFY_FLAGS = "OCSP_VERIFY_FLAGS";

    // Standard commands used internally by Tomcat. May also be used by users.
    /** Sets TLS groups. */
    public static final String GROUPS = "groups";

    @Serial
    private static final long serialVersionUID = 1L;

    /** The command name. */
    private String name = null;
    /** The command value. */
    private String value = null;

    /**
     * Constructs a new OpenSSLConfCmd with no name or value.
     */
    public OpenSSLConfCmd() {
    }

    /**
     * Constructs a new OpenSSLConfCmd with the given name and value.
     *
     * @param name The command name
     * @param value The command value
     */
    public OpenSSLConfCmd(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Returns the command name.
     *
     * @return The command name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the command name.
     *
     * @param name The command name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the command value.
     *
     * @return The command value
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the command value.
     *
     * @param value The command value
     */
    public void setValue(String value) {
        this.value = value;
    }
}
