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
package org.apache.coyote;

import org.apache.tomcat.util.res.StringManager;

/**
 * Defines timing options for responding to requests that contain a
 * '100-continue' expectations.
 *
 *
 */
public enum ContinueResponseTiming {

    /**
     * Tomcat will automatically send the 100 intermediate response before
     * sending the request to the servlet.
     */
    IMMEDIATELY("immediately"),

    /**
     * Send the 100 intermediate response only when the servlet attempts to
     * read the request's body by either:
     * <ul>
     * <li>calling read on the InputStream returned by
     *     HttpServletRequest.getInputStream</li>
     * <li>calling read on the BufferedReader returned by
     *     HttpServletRequest.getReader</li>
     * </ul>
     * This allows the servlet to process the request headers and possibly
     * respond before reading the request body.
     */
    ON_REQUEST_BODY_READ("onRead"),


    /**
     * Internal use only. Used to indicate that the 100 intermediate response
     * should be sent if possible regardless of the current configuration.
     */
    ALWAYS("always");


    private static final StringManager sm = StringManager.getManager(ContinueResponseTiming.class);

    public static  ContinueResponseTiming fromString(String value) {
        /*
         * Do this for two reasons:
         * - Not all of the Enum values are intended to be used in configuration
         * - the naming convention for Enum constants and configuration values
         * - is not consistent
         */
        if (IMMEDIATELY.toString().equalsIgnoreCase(value)) {
            return IMMEDIATELY;
        } else if (ON_REQUEST_BODY_READ.toString().equalsIgnoreCase(value)) {
            return ContinueResponseTiming.ON_REQUEST_BODY_READ;
        } else {
            throw new IllegalArgumentException(sm.getString("continueResponseTiming.invalid", value));
        }
    }


    private final String configValue;


    private ContinueResponseTiming(String configValue) {
        this.configValue = configValue;
    }


    @Override
    public String toString() {
        return configValue;
    }
}
